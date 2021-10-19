(ns com.latacora.backsaws.pagination-test
  (:require
   [com.latacora.backsaws.pagination :as p]
   [clojure.test :as t]
   [cognitect.aws.client.api :as aws]
   [clojure.spec.alpha :as s]
   [com.gfredericks.test.chuck.clojure-test :refer [checking]]))

;; Because paging-opts are often _but not always_ keywords (they're technically
;; just arbitrary functions), it's kind of annoying to test! You really just
;; want to test against end to end behavior, and that requires actual data.
;; Fortunately aws-api provides specs, and specs provide a way to generate
;; sample data. So you can just test that the given functions react the same way
;; to all sample data :-)

;; FWIW, I think this code mostly works, except it's hard to prove because
;; sampling the generators breaks a lot, see:
;; https://github.com/cognitect-labs/aws-api/issues/99

(def samples
  [[:organizations
    :ListAccountsForParent
    {:results :Accounts
     :truncated? (#'p/complement* :NextToken)
     :next-marker :NextToken
     :marker-key :NextToken}]

   [:s3
    :ListObjectVersions
    {:results :Versions
     :truncated? :IsTruncated
     :next-marker :KeyMarker
     :marker-key :KeyMarker}]

   [:s3
    :ListBuckets
    {:results :Buckets
     :truncated? @#'p/constantly-false
     :next-marker ::p/not-paginated
     :marker-key ::p/not-paginated}]])

(t/deftest inferred-paging-opts-tests
  (doseq [[api op expected] samples]
    (let [client (aws/client {:api api})
          inferred (p/infer-paging-opts client op)
          {keywords true fns false} (group-by (comp keyword? val) expected)]

      ;; Keywords are easy to check:
      (doseq [[k expected-fn] keywords]
        (let [actual-fn (inferred k)]
          (t/is (= expected-fn actual-fn)
                [api op k])))

      ;; For arbitrary fns, we generate some samples and try against those to
      ;; see if the fns behave the same:
      (checking
       ["inferred works same as reference" api op] 100
       [resp (s/gen (aws/response-spec-key client op))]
       (doseq [[k expected-fn] fns]
         (let [actual-fn (inferred k)]
           (t/is (fn? actual-fn))
           (t/is (= (expected-fn resp) (actual-fn resp))
                 [api op k])))))))

(def ^:private pagination-ns (comp #{(namespace ::p/x)} namespace))

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
            inferred (p/infer-paging-opts client op)]
        (t/is (= (comparable expected) (comparable inferred)))))))
