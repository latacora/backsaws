(ns com.latacora.backsaws.pagination
  (:require
   [cognitect.aws.client.api :as aws]
   [meander.epsilon :as m]))

(def ^:private next-markers
  [:NextToken :NextMarker :KeyMarker])

(def ^:private marker-keys
  (into [:StartingToken] next-markers))

(defn infer-paging-opts
  "For a given client + op, infer paging opts for [[paginated-invoke]]."
  ([client op]
   (infer-paging-opts client op {}))
  ([client op paging-opts]
   (let [{:keys [request response]} (-> client aws/ops op)]
     (reduce
      (fn [opts [key default-fn]]
        (if-not (key opts)
          (assoc opts key (default-fn opts))
          opts)) ;; already set, leave it alone
      paging-opts
      ;; we use a vec because the order in which we compute these matters:
      [[:results (fn [_] (m/find response {?k [:seq-of _]} ?k))]
       [:next-marker (fn [_] (->> next-markers (filter response) first))]
       [:marker-key (fn [_] (->> marker-keys (filter request) first))]
       [:truncated (fn [{:keys [next-marker]}]
                     (if (:IsTruncated response)
                       :IsTruncated
                       (complement next-marker)))]]))))

(defn paginated-invoke
  "Like [[aws/invoke]], but with pagination.

  You likely don't need to specify your own pagination behavior: if you don't,
  we'll try to infer some reasonable defaults. Pagination opts are as follows:

  `results` is a fn from a response to the actual objects, e.g. :Accounts
  or (comp :Instances :Reservation).

  `truncated?` is a fn that given a response, tells you if there's another one
  or not. For some services, this is :IsTruncated (hence the name), but it is
  often `(complement next-marker)` (see below).

  `next-marker` is a fn that given a response, returns the token/marker for the
  next page. This is often `:NextMarker`.

  `marker-key` is the key in the next request that holds the next marker from
  the previous response. This is often `:Marker` or `:StartingMarker`."
  ([client op-map]
   (let [paging-opts (infer-paging-opts client (:op op-map))]
     (paginated-invoke client op-map paging-opts)))
  ([client op-map {:keys [results truncated? next-marker marker-key] :as paging-opts}]
   (let [response (aws/invoke client op-map)]
     (if (truncated? response)
       (lazy-cat
        (results response)
        (let [op-map (assoc op-map marker-key (next-marker response))]
          (paginated-invoke client op-map paging-opts)))
       (results response)))))
