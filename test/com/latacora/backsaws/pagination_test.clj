(ns com.latacora.backsaws.pagination-test
  (:require
   [com.latacora.backsaws.pagination :as p]
   [clojure.test :as t]))

(t/deftest inferred-paging-opts-tests
  (t/are [api op expected]
      (let [client (aws/client {:api api})]
        (= expected (p/infer-paging-opts client op)))

    :organizations
    :ListAccountsForParent
    {:results :Accounts
     :truncated? (complement :NextToken)
     :next-marker :NextToken
     :marker-key :StartingToken}))


(complement :NextToken)
