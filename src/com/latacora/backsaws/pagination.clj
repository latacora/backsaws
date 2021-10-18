(ns com.latacora.backsaws.pagination
  (:require
   [cognitect.aws.client.api :as aws]
   [meander.epsilon :as m]
   [clojure.set :as set]))

(def ^:private next-markers
  [:NextToken :NextMarker])

(def ^:private marker-keys
  (into [:StartingToken] next-markers))

(def ^:private is-truncated-keys
  [:IsTruncated])

(defn ^:private word-parts
  [k]
  (->> k name (re-seq #"[A-Z][a-z]*")))

(defn ^:private complement*
  "Returns a fn that is the complement of f.

  Really only exists to make testing easier; see testing ns for details."
  [f]
  (-> (complement f) (with-meta {::complement-of f})))

(defn ^:private mapcat-ks*
  "Returns a fn that mapcats its argument over ks.

  Really only exists to make testing easier; see testing ns for details."
  [ks]
  (-> (fn [resp] (mapcat resp ks)) (with-meta {::mapcat-of ks})))

(def ^:private constantly-false
  "`(constantly false)`. Only exists to aid testing."
  (constantly false))

(defn infer-paging-opts
  "For a given client + op, infer paging opts for [[paginated-invoke]]."
  ([client op]
   (infer-paging-opts client op {}))
  ([client op paging-opts]
   ;; Almost always, we'll find exactly one key (keyword, really) for a given
   ;; result, but a handful of functions have two, e.g. s3's
   ;; ListObjectVersions (which has both versions and deletions). When that
   ;; happens, we run down both and merge them; it's up to the caller to
   ;; distinguish (in all cases we've seen, this is reasonable). In the common
   ;; case where there's exactly 1 kw we find, we just return that; this
   ;; promotes introspectability.
   (let [{:keys [request response]} (-> client aws/ops op)
         shared-parts (->> op word-parts set (partial set/intersection))
         similarity #(->> % word-parts set shared-parts count -)
         one-or-concat (comp
                        (fn [[k :as ks]]
                          (case (count ks)
                            ;; [0, 1] cases optional, but benefit introspection
                            0 ::not-paginated
                            1 k
                            (mapcat-ks* ks)))
                        (partial sort-by similarity))]
     (reduce
      (fn [opts [key default-fn]]
        (if-not (key opts)
          (assoc opts key (default-fn opts))
          opts)) ;; already set, leave it alone
      paging-opts
      ;; we use a vec because the order in which we compute these matters; the
      ;; definition of :truncated? depends on that of :next-marker.
      [[:results
        (fn [_] (-> response (m/search {?k [:seq-of _]} ?k) one-or-concat))]
       [:next-marker
        (fn [_] (->> next-markers (filter response) one-or-concat))]
       [:marker-key
        (fn [_]
          (if request ;; does this fn take args at all?
            (->> marker-keys (filter request) first)
            ::not-paginated))]
       [:truncated?
        (fn [{:keys [next-marker marker-key]}]
          (if (identical? marker-key ::not-paginated)
            constantly-false
            (or
             (->> is-truncated-keys (filter response) first)
             (complement* next-marker))))]]))))

(defn paginated-invoke
  "Like [[aws/invoke]], but with pagination. Returns a lazy seq of results.

  You likely don't need to specify your own pagination behavior: if you don't,
  we'll try to infer some reasonable defaults. Pagination opts are as follows:

  `:results` is a fn from a response to the actual objects, e.g. :Accounts
  or (comp :Instances :Reservation). Note that because we're returning a lazy
  seq of results, we can only collect one thing at a time. Some operations, e.g.
  s3's ListObjectVersions, have two: DeletionMarkers and Versions. By default,
  these get intermingled via concatenation.

  `:truncated?` is a fn that given a response, tells you if there's another one
  or not. For some services, this is :IsTruncated (hence the name), but it is
  often `(complement next-marker)` (see below).

  `:next-marker` is a fn that given a response, returns the token/marker for the
  next page. This is often `:NextMarker`.

  `:marker-key` is the key in the next request that holds the next marker from
  the previous response. This is often `:Marker` or `:StartingMarker`."
  ([client op-map]
   (let [paging-opts (infer-paging-opts client (:op op-map))]
     (paginated-invoke client op-map paging-opts)))
  ([client op-map paging-opts]
   (let [{:keys [results truncated? next-marker marker-key]} paging-opts
         response (aws/invoke client op-map)]
     (if (truncated? response)
       (lazy-cat
        (results response)
        (let [op-map (assoc op-map marker-key (next-marker response))]
          (paginated-invoke client op-map paging-opts)))
       (results response)))))
