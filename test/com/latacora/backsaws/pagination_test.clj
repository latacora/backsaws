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
     :truncated? (#'p/some-fn* #{:NextToken})
     :next-request (#'p/next-request-from-mapping
                    {:NextToken :NextToken})}]

   [:s3
    :ListObjectVersions
    {:results (#'p/mapcat-ks* [:DeleteMarkers :Versions])
     :truncated? :IsTruncated
     :next-request (#'p/next-request-from-mapping
                    {:NextVersionIdMarker :VersionIdMarker
                     :NextKeyMarker :KeyMarker})}]

   [:s3
    :ListBuckets
    {:results :Buckets
     :truncated? (#'p/constantly* false)
     :next-request ::p/not-paginated}]

   [:codecommit
    :ListRepositories
    {:results :repositories
     :truncated? (#'p/some-fn* #{:nextToken})
     :next-request (#'p/next-request-from-mapping
                    {:nextToken :nextToken})}]])

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

(t/deftest manual-paginated-invoke-tests
  (let [orgs (aws/client {:api :organizations})]
    (with-redefs
      [aws/invoke
       (fn [client {:keys [op request]}]
         (t/is (identical? orgs client))
         (t/is (= op :ListAccountsForParent))
         (condp = request
           {:ParentId "xyzzy"}
           {:Accounts [{:AccountId 1}]
            :NextToken "iddqd"}

           {:ParentId "xyzzy" :NextToken "iddqd"}
           {:Accounts [{:AccountId 2}]}))]
      (t/is
       (=
        [{:AccountId 1} {:AccountId 2}]
        (p/paginated-invoke
         orgs
         {:op :ListAccountsForParent
          :request {:ParentId "xyzzy"}})))))

  (let [s3 (aws/client {:api :s3})]
    (with-redefs
      [aws/invoke
       (fn [client {:keys [op request]}]
         (t/is (identical? s3 client))
         (t/is (= op :ListObjectVersions))
         (case [(:VersionIdMarker request) (:KeyMarker request)]
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
      (p/paginated-invoke s3 {:op :ListObjectVersions}))))

(t/deftest next-request-tests
  (t/is
   (= {:VersionIdMarker :x
       :KeyMarker :y}
      (let [request nil
            response {:NextVersionIdMarker :x
                      :NextKeyMarker :y}
            mapping {:NextVersionIdMarker :VersionIdMarker
                     :NextKeyMarker :KeyMarker}
            next-request (#'p/next-request-from-mapping mapping)]
        (next-request request response))))

  (t/is
   (= {:VersionIdMarker :x
       :KeyMarker :y
       :SomeOtherValue :z}
      (let [request {:SomeOtherValue :z}
            response {:NextVersionIdMarker :x
                      :NextKeyMarker :y}
            mapping {:NextVersionIdMarker :VersionIdMarker
                     :NextKeyMarker :KeyMarker}
            next-request (#'p/next-request-from-mapping mapping)]
        (next-request request response)))))

(t/deftest some-fn*-tests
  (let [pred (#'p/some-fn* [:a :b])]
    ;; This works as a truncated? predicate, so absence of keys should be false,
    ;; presence should be true (because it means that there's a next token or
    ;; whatever).
    (t/is (not (pred {})))
    (t/is (not (pred nil)))
    (t/is (pred {:a 1}))
    (t/is (pred {:b 1}))
    (t/is (pred {:a 1 :b 1}))))
