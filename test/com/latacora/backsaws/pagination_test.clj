(ns com.latacora.backsaws.pagination-test
  (:require
   [com.latacora.backsaws.pagination :as p]
   [clojure.test :as t]
   [cognitect.aws.client.api :as aws]
   [clojure.spec.alpha :as s]
   [clojure.test.check.generators :as gen]))

(defn ^:private complement*
  "Like [[clojure.core/complement]] but with a metadata hint. See [[is-complement?]]."
  [f]
  (-> f complement (with-meta {::complement-of f})))

(defn ^:private is-complement?
  "Checks that `actual` is a [[clojure.core/complement]] that behaves like a given
  `expected` [[complement*]]."
  [expected actual]
  (let [complement-of (-> expected meta ::complement-of)
        map-with-key {complement-of "a truthy value"}]
    (t/is (= true (expected {}) (actual {})))
    (t/is (= false (expected map-with-key) (actual map-with-key)))))

;; Because paging-opts are often _but not always_ keywords (they're technically
;; just arbitrary functions), it's kind of annoying to test! You really just
;; want to test against end to end behavior, and that requires actual data.
;; Fortunately aws-api provides specs, and specs provide a way to generate
;; sample data. So you can just test that the given functions react the same way
;; to all sample data :-)

(t/deftest inferred-paging-opts-tests
  (t/are [api op expected]
      (let [only-kws (fn [m] (select-keys
                              m [:results :next-marker :marker-key]))
            client (aws/client {:api api})
            inferred (p/infer-paging-opts client op)]
        (t/is (= (only-kws expected) (only-kws inferred)))
        (let [[expected inferred] (map :truncated? [expected inferred])]
          (if (keyword? expected)
            (t/is (= expected inferred))
            (is-complement? expected inferred))))

    :organizations
    :ListAccountsForParent
    {:results :Accounts
     :truncated? (complement* :NextToken)
     :next-marker :NextToken
     :marker-key :StartingToken}

    :s3
    :ListObjectVersions
    {:results :Versions
     :truncated? :IsTruncated
     :next-marker :KeyMarker
     :marker-key :KeyMarker}))

(t/deftest paginated-invoke-tests
  )

(let [client (aws/client {:api :s3})]
  (->>
   (aws/response-spec-key client :ListObjectVersions)
   (s/gen)
   (gen/sample)
   (group-by :NextVersionIdMarker)
   keys))
