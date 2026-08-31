(ns com.latacora.backsaws.credentials-providers.default-chain-test
  (:require [clojure.test :refer [deftest is testing]]
            [cognitect.aws.client.api :as aws]
            [cognitect.aws.credentials :as awscreds]
            [com.latacora.backsaws.credentials-providers :as cp]))


(defn ^:private record-constructor
  "A stand-in for a provider constructor that appends its arguments to `built`, tagged."
  [built tag]
  (fn [& args] (swap! built conj (into [tag] args)) nil))

(defn ^:private chain-built-by
  "The providers `default-credentials-provider` constructs for `args`, in order.

  Which order is the whole of what this function decides, and a constructed provider is
  opaque, so we watch it build them rather than take one apart afterwards."
  [& args]
  (let [built (atom [])]
    (with-redefs [cp/credential-process-provider (record-constructor built :credential-process)
                  awscreds/default-credentials-provider (record-constructor built :aws-default)
                  aws/default-http-client (constantly ::default-http-client)]
      (apply cp/default-credentials-provider args))
    @built))

(deftest default-credentials-provider-test
  (testing "a named profile comes first, because the rest of the chain ignores the name"
    (is (= [[:credential-process "p"] [:aws-default ::http]]
           (chain-built-by :profile-name "p" :http-client ::http))))

  (testing "with no profile named there is no such conflict, so the environment comes first"
    (is (= [[:aws-default ::http] [:credential-process]]
           (chain-built-by :http-client ::http))))

  (testing "an http client is built when none is given"
    (is (= [[:aws-default ::default-http-client] [:credential-process]]
           (chain-built-by))))

  (testing "a trailing map, which is how a caller holding an options map calls this"
    (is (= [[:credential-process "p"] [:aws-default ::http]]
           (chain-built-by {:profile-name "p" :http-client ::http}))))

  (testing "including one it has nothing to put in"
    (is (= [[:aws-default ::default-http-client] [:credential-process]]
           (chain-built-by nil)))))
