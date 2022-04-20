(ns com.latacora.backsaws.credential-process-test
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as sh]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [cognitect.aws.credentials :as awscreds]
            [com.latacora.backsaws.credential-process :as cp]
            [meander.epsilon :as m])
  (:import [java.io File]
           [java.time Instant]
           [java.util.logging Level Logger]))


;; If you’re debugging these tests change this to e.g. Level/FINER
(def log-level Level/OFF)

(run! #(.setLevel (Logger/getLogger %) log-level)
      ["com.latacora.backsaws.credential-process" (str *ns*)])

(->> (Logger/getLogger "")
     (.getHandlers)
     (run! #(.setLevel % log-level)))


(defn write-config-file
  [profile]
  (let [tf (File/createTempFile "test" "awsconfig")
        contents (format (str "[default]\n"
                              "foo=bar\n"
                              "\n"
                              "[profile %s]\n"
                              "credential_process = aws-sso-util credential-process --profile %s\n")
                         profile profile)]
    (spit tf contents)
    (log/infof "Wrote config file to %s" tf)
    tf))


(def fake-result
  "Fake result of the invocation of the credential_process, as per
   https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sourcing-external.html"
  {:Version 1
   :AccessKeyId "ASIA"
   :SecretAccessKey "xyzzy"
   :SessionToken "iddqd"
   :Expiration (-> (Instant/now) (.plusSeconds 3600) str)})


(def fake-process-output
  (json/write-str fake-result))


(defn fake-sh!
  [ctx & args]
  (let [expected-profile (->> @ctx (filter #(-> % :type (= :profile))) last :value)
        profile (m/match args
                  ("aws-sso-util" "credential-process" "--profile" ?profile)
                  ?profile)]
    (is (= expected-profile profile))
    (swap! ctx conj {:type :call :fn 'fake-sh :args args})
    {:out fake-process-output
     :exit 0
     :err "we good"}))


(deftest e2e-happy-path-test
  (let [profile (str (gensym))
        config-file (write-config-file profile)
        ctx (atom [{:type :profile :value profile}])
        n-calls (fn [] (->> @ctx (filter #(-> % :type (= :call))) count))]
    (with-redefs [sh/sh (partial fake-sh! ctx)]
      (let [provider (cp/provider profile config-file)]
        (testing "instantiating does not fetch creds"
          (is (zero? (n-calls))))

        (testing "first fetch calls aws-sso-util"
          (let [result (awscreds/fetch provider)
                {:aws/keys [access-key-id secret-access-key session-token]} result]
            (is (= "ASIA" access-key-id))
            (is (= "xyzzy" secret-access-key))
            (is (= "iddqd" session-token))
            (is (<= 3000 (::awscreds/ttl result) 3600))
            (is (= 1 (n-calls)))))

        (testing "fetch results are cached"
          (awscreds/fetch provider)
          (is (= 1 (n-calls))))))))


(deftest e2e-sad-path-test
  (with-redefs [sh/sh (constantly {:out "" :exit 1 :err "ruh roh"})]
    (let [profile (str (gensym))
          config-file (write-config-file profile)
          provider (cp/provider profile config-file)]
      (is (nil? (awscreds/fetch provider))))))
