(ns com.latacora.backsaws.pagination-test
  (:require
   [com.latacora.backsaws.pagination :as p]
   [clojure.test :as t]
   [cognitect.aws.client.api :as aws]))

;; Because paging-opts are often _but not always_ keywords (they're technically
;; just arbitrary functions), it's kind of annoying to test! You'd expect to
;; just want to test against end to end behavior, and that requires actual data.
;; Fortunately aws-api provides specs, and specs provide a way to generate
;; sample data. So you can just test that the given functions react the same way
;; to all sample data :-) Unfortunately this doesn't work as well as you'd like
;; because sampling the generators breaks a lot, see:
;; https://github.com/cognitect-labs/aws-api/issues/99

(def samples
  [[:organizations
    :ListAccountsForParent
    {:results :Accounts
     :truncated? (#'p/none-of?* #{:NextToken})
     :next-op-map (#'p/next-op-map-from-mapping
                   {:NextToken :NextToken})}]

   [:s3
    :ListObjectVersions
    {:results (#'p/mapcat-ks* [:DeleteMarkers :Versions])
     :truncated? :IsTruncated
     :next-op-map (#'p/next-op-map-from-mapping
                   {:NextVersionIdMarker :VersionIdMarker
                    :NextKeyMarker :KeyMarker})}]

   [:s3
    :ListBuckets
    {:results :Buckets
     :truncated? (#'p/constantly* false)
     :next-op-map ::p/not-paginated}]])

(def ^:private pagination-ns
  (comp #{(namespace ::p/x)} namespace))

(defn ^:private comparable
  [m]
  (->>
   (for [[k v :as item] m]
     (if-some [meta-items (->> v meta (filter (comp pagination-ns key)) seq)]
       [k (into {} meta-items)]
       item))
   (into {})))

(t/deftest inferred-paging-opts-tests
  (doseq [[api op expected] samples]
    (t/testing [api op]
      (let [client (aws/client {:api api})
            ;; we don't use the memoized public variant to aid in development
            inferred (#'p/infer-paging-opts* client op)]
        (t/is (= (comparable expected) (comparable inferred)))))))

(comment
  (#'p/infer-paging-opts* (aws/client {:api :organizations} ) :ListAccountsForParent)
  (#'p/infer-paging-opts* (aws/client {:api :s3} ) :ListObjectVersions))

(t/deftest manual-paginated-invoke-tests
  (let [s3 (aws/client {:api :s3})]
    (with-redefs
      [aws/invoke
       (fn [client {:keys [VersionIdMarker KeyMarker]}]
         (t/is (identical? s3 client))
         (case [VersionIdMarker KeyMarker]
           [nil nil]
           {:Versions [{:Key "a" :VersionId 1}
                       {:Key "a" :VersionId 2}]
            :DeleteMarkers [{:Key "a" :VersionId 3}]
            :NextKeyMarker "b"
            :IsTruncated true}

           [nil "b"]
           {:Versions [{:Key "b" :VersionId 1}
                       {:Key "b" :VersionId 2}]
            :DeleteMarkers []
            :NextKeyMarker "b"
            :NextVersionIdMarker 3
            :IsTruncated true}

           [3 "b"]
           {:Versions [{:Key "b" :VersionId 3}]
            :DeleteMarkers []
            :IsTruncated false}))]
      (p/paginated-invoke s3 {:op :ListObjectVersions})))

  (let [s3 (aws/client {:api :s3})]
    (with-redefs
      [aws/invoke
       (fn [client {:keys [VersionIdMarker KeyMarker]}]
         (t/is (identical? s3 client))
         (case [VersionIdMarker KeyMarker]
           [nil nil]
           {:Versions [{:Key "a" :VersionId 1}
                       {:Key "a" :VersionId 2}]
            :DeleteMarkers [{:Key "a" :VersionId 3}]
            :NextKeyMarker "b"
            :IsTruncated true}

           [nil "b"]
           {:Versions [{:Key "b" :VersionId 1}
                       {:Key "b" :VersionId 2}]
            :DeleteMarkers []
            :NextKeyMarker "b"
            :NextVersionIdMarker 3
            :IsTruncated true}

           [3 "b"]
           {:Versions [{:Key "b" :VersionId 3}]
            :DeleteMarkers []}))]
      (p/paginated-invoke s3 {:op :ListObjectVersions}))))

(t/deftest next-op-map-tests
  (t/is
   (= {:op :ListObjectVersions
       :VersionIdMarker :x
       :KeyMarker :y}
      (let [op-map {:op :ListObjectVersions}
            resp {:NextVersionIdMarker :x
                  :NextKeyMarker :y}
            mapping {:NextVersionIdMarker :VersionIdMarker
                     :NextKeyMarker :KeyMarker}
            next-op-map (#'p/next-op-map-from-mapping mapping)]
        (next-op-map op-map resp)))))
