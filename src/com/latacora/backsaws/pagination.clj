(ns com.latacora.backsaws.pagination
  (:require
   [cognitect.aws.client.api :as aws]
   [meander.epsilon :as m]
   [clojure.set :as set]))

(def ^:private is-truncated-keys
  #{:IsTruncated})

(defn ^:private some-fn*
  [ks]
  (->
   (apply some-fn ks)
   (with-meta {::some-of (set ks)})))

(defn ^:private mapcat-ks*
  "Returns a fn that mapcats its argument over ks.

  Really only exists to make testing easier; see testing ns for details."
  [ks]
  (-> (fn [resp] (mapcat resp ks)) (with-meta {::mapcat-of ks})))

(defn ^:private constantly*
  "Like [[constantly]].

  Really only exists to make testing easier; see testing ns for details."
  [x]
  (-> (constantly x) (with-meta {::constantly x})))

(defn ^:private next-request-from-mapping
  [marker-mapping]
  (->
   (fn [request response]
     (->>
      (for [[resp-k req-k] marker-mapping]
        [req-k (resp-k response)])
      (into (or request {}))))
   (with-meta {::marker-mapping marker-mapping})))

(defn ^:private one-or-concat
  [[k :as ks]]
  (case (count ks)
    ;; [0, 1] cases optional, but benefit introspection
    0 ::not-paginated
    1 k
    (mapcat-ks* ks)))

(defn ^:private similarity
  "Find the length of the longest common substring length between two strings (or
  anything named, so also keywords and symbols)."
  [str1 str2]
  (loop [s1 (seq (name str1))
         s2 (seq (name str2))
         len 0
         maxlen 0]
    (cond
      (>= maxlen (count s1))
      maxlen

      (>= maxlen (+ (count s2) len))
      (recur (rest s1) (seq (name str2)) 0 maxlen)

      :else (let [a (nth s1 len "")
                  [b & s2] s2
                  len (inc len)]
              (if (= a b)
                (recur s1 s2 len (max len maxlen))
                (recur s1 s2 0 maxlen))))))

(defn ^:private infer-paging-opts*
  "Like infer-paging-opts, but not memoized."
  ([client op]
   (infer-paging-opts* client op {}))
  ([client op paging-opts]
   ;; Almost always, we'll find exactly one key (keyword, really) for a given
   ;; result, but a handful of functions have two, e.g. s3's
   ;; ListObjectVersions (which has both versions and deletions). When that
   ;; happens, we run down both and merge them; it's up to the caller to
   ;; distinguish (in all cases we've seen, this is reasonable). In the common
   ;; case where there's exactly 1 kw we find, we just return that; this
   ;; promotes introspection.
   (let [{:keys [request response]} (-> client aws/ops op)]
     (->>
      (reduce
       (fn [opts [key default-fn]]
         (if-not (key opts)
           (assoc opts key (default-fn opts))
           opts)) ;; already set, leave it alone
       paging-opts
       ;; we use a vec because the order in which we compute these matters; the
       ;; definition of :truncated? depends on that of :next-marker.
       [[:results
         (fn [_]
           (-> response (m/search {?k [:seq-of _]} ?k) sort one-or-concat))]
        [:next-request
         (fn [_]
           (let [resp-keys (-> response keys set)
                 matches #(for [c resp-keys :when (re-matches % (name c))] c)
                 next-markers (or
                               (seq (set/intersection resp-keys #{:nextToken}))
                               (seq (matches #"Next([A-Za-z]*)*?(Marker|Token)"))
                               (seq (matches #"([A-Za-z]*)*?(Marker|Token)")))]
             (if-not next-markers
               ::not-paginated
               (let [req-keys (-> request keys set)]
                 (->>
                  (for [next-marker next-markers
                        :let [similarity (partial similarity next-marker)]]
                    [next-marker (apply max-key similarity req-keys)])
                  (into {})
                  next-request-from-mapping)))))]
        [:truncated?
         (fn [{:keys [next-request]}]
           (if (identical? next-request ::not-paginated)
             (constantly* false)
             (or
              (->> is-truncated-keys (filter response) first)
              (->> next-request meta ::marker-mapping keys some-fn*))))]])))))

(def infer-paging-opts
  "For a given client + op, infer paging opts for [[paginated-invoke]]."
  (memoize infer-paging-opts*))

(defn paginated-invoke
  "Like [[aws/invoke]], but with pagination. Returns a lazy seq of results.

  You likely don't need to specify your own pagination behavior: if you don't,
  we'll try to infer some reasonable defaults. Pagination opts are as follows:

  `:results` is a fn from a response to the actual objects, e.g. `:Accounts` or
  `(comp :Instances :Reservation)`. We return a lazy seq of these elements.
  Most paginatied operations only contain one useful type of result (e.g
  `ListRepositories` returns only repositories). Some operations have
  more than one. For example, s3's ListObjectVersions has DeletionMarkers
   Versions, and CommonPrefixes. By default, these get intermingled via
   concatenation. It is the caller's job to disambiguate and filter if they don't
   need all results. This function does not attempt to recreate an equivalent
   single response object, because a) that would prevent streaming processing,
   and b) for some services, that would be incorrect (some of the results may be
   page-specific). Such page-specific results will still be useful for streaming
   purposes.

  `:truncated?` is a fn that given a response, tells you if there's another one
  or not. For some services, this is :IsTruncated (hence the name), but it is
  often based on the existence of next marker keys in the previous response.

  `:next-request` takes the last request (part of the op-map) and last response
  and returns the next request.

   If you specify paging opts explicitly, those will be merged with inferred
  opts. The actual paging-opts will be added to the returned object's metadata
  under `:paging-opts` so you can inspect them."
  ([client op-map]
   (paginated-invoke client op-map nil))
  ([client op-map paging-opts]
   (let [inferred (->> op-map :op (infer-paging-opts client))
         {:keys [results truncated? next-request]} (merge inferred paging-opts)
         response (aws/invoke client op-map)]
     (->
      (if (truncated? response)
        (lazy-cat
         (results response)
         (let [op-map (update op-map :request next-request response)]
           (paginated-invoke client op-map paging-opts)))
        (results response))
      (with-meta {:paging-opts paging-opts})))))
