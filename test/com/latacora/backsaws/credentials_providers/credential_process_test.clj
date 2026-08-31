(ns com.latacora.backsaws.credentials-providers.credential-process-test
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [cognitect.aws.credentials :as awscreds]
            [com.latacora.backsaws.credentials-providers :as cp]
            [meander.epsilon :as m])
  (:import [java.io File]
           [java.time Instant]
           [java.util.logging Handler Level LogRecord Logger]))


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
      (let [provider (cp/credential-process-provider profile config-file)]
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
          provider (cp/credential-process-provider profile config-file)]
      (is (nil? (awscreds/fetch provider))))))


(def ^:private provider-logger "com.latacora.backsaws.credentials-providers")


(defn ^:private captured-log
  "What `f` logs to `provider-logger` at `level` or above, as a handler would render
  it: the message, and the throwable, whose `toString` carries its `ex-data`."
  [level f]
  (let [logger (Logger/getLogger provider-logger)
        records (atom [])
        handler (proxy [Handler] []
                  (publish [^LogRecord record]
                    (swap! records conj (str (.getMessage record) " " (.getThrown record))))
                  (flush [])
                  (close []))
        prior-level (.getLevel logger)]
    (try
      (.setLevel logger level)
      (.addHandler logger handler)
      (f)
      (finally
        (.removeHandler logger handler)
        (.setLevel logger prior-level)))
    (str/join "\n" @records)))


(deftest debug-log-redacts-fetched-credentials-test
  (let [profile (str (gensym))
        config-file (write-config-file profile)
        ctx (atom [{:type :profile :value profile}])
        logged (with-redefs [sh/sh (partial fake-sh! ctx)]
                 (captured-log
                  Level/FINE
                  #(awscreds/fetch (cp/credential-process-provider profile config-file))))]
    (testing "which credentials were fetched"
      (is (str/includes? logged (:AccessKeyId fake-result))))
    (testing "and not the credentials"
      (is (not (str/includes? logged (:SecretAccessKey fake-result))))
      (is (not (str/includes? logged (:SessionToken fake-result)))))))


(def ^:private static-secret "SECRET-DO-NOT-LOG")


(deftest error-log-omits-profile-values-test
  (let [profile (str (gensym))
        config-file (doto (File/createTempFile "test" "awsconfig")
                      (spit (format (str "[profile %s]\n"
                                         "aws_access_key_id = ASIA\n"
                                         "aws_secret_access_key = %s\n")
                                    profile static-secret)))
        logged (captured-log
                Level/SEVERE
                #(is (nil? (awscreds/fetch (cp/credential-process-provider profile config-file)))))]
    (testing "a profile with no credential_process says what it did have"
      (is (str/includes? logged "credential_process"))
      (is (str/includes? logged "aws_secret_access_key")))
    (testing "without the values, which for such a profile are credentials"
      (is (not (str/includes? logged static-secret))))))
