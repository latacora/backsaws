;; This code was copied from the project pod-babashka-aws and then modified by Latacora.
;;
;; Original file:
;; https://github.com/babashka/pod-babashka-aws/blob/81e200692c8b637529cc48f7c51f2e5777c586c5/src/pod/babashka/aws/impl/aws/credentials.clj
;;
;; pod-babashka-aws is copyright © 2020 Michiel Borkent, Jeroen van Dijk, Rahul De and Valtteri
;; Harmainen and is distributed under the Apache License 2.0:
;; https://github.com/babashka/pod-babashka-aws/blob/81e200692c8b637529cc48f7c51f2e5777c586c5/LICENSE
;;
;; Modifications copyright © 2022 Latacora and distributed under the Eclipse Public License 1.0; see
;; LICENSE in the root of this code repository.

(ns com.latacora.backsaws.credential-process
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [cognitect.aws.config :as config]
            [cognitect.aws.credentials :as creds]
            [cognitect.aws.util :as u]))


(set! *warn-on-reflection* true)


(defn- parse-cmd
  "Split string to list of individual space separated arguments.
  If argument contains space you can wrap it with `'` or `\"`."
  [s]
  (loop [s (java.io.StringReader. s)
         in-double-quotes? false
         in-single-quotes? false
         buf (java.io.StringWriter.)
         parsed []]
    (let [c (.read s)]
      (cond
        (= -1 c) (if (pos? (count (str buf)))
                   (conj parsed (str buf))
                   parsed)
        (= 39 c) ;; single-quotes
        (if in-single-quotes?
        ;; exit single-quoted string
          (recur s in-double-quotes? false (java.io.StringWriter.) (conj parsed (str buf)))
        ;; enter single-quoted string
          (recur s in-double-quotes? true buf parsed))

        (= 92 c) ;; assume escaped quote
        (let [escaped (.read s)
              buf (doto buf (.write escaped))]
          (recur s in-double-quotes? in-single-quotes? buf parsed))

        (and (not in-single-quotes?) (= 34 c)) ;; double quote
        (if in-double-quotes?
        ;; exit double-quoted string
          (recur s false in-single-quotes? (java.io.StringWriter.) (conj parsed (str buf)))
        ;; enter double-quoted string
          (recur s true in-single-quotes? buf parsed))

        (and (not in-double-quotes?)
             (not in-single-quotes?)
             (Character/isWhitespace c))
        (recur s in-double-quotes? in-single-quotes? (java.io.StringWriter.)
               (let [bs (str buf)]
                 (cond-> parsed
                   (not (str/blank? bs)) (conj bs))))
        :else (do
                (.write buf c)
                (recur s in-double-quotes? in-single-quotes? buf parsed))))))


(def windows? (-> (System/getProperty "os.name")
                  (str/lower-case)
                  (str/includes? "win")))


(defn run-credential-process-cmd [cmd]
  (let [cmd (parse-cmd cmd)
        cmd (if windows?
              (mapv #(str/replace % "\"" "\\\"")
                    cmd)
              cmd)
        ;; _ (binding [*out* *err*] (prn :cmd cmd))
        {:keys [exit out err]} (apply shell/sh cmd)]
    (if (zero? exit)
      out
      (throw (ex-info (str "Non-zero exit: " (pr-str err)) {})))))


(defn get-credentials-via-cmd [cmd]
  (let [credential-map (json/read-str (run-credential-process-cmd cmd))
        {:strs [AccessKeyId SecretAccessKey SessionToken Expiration]} credential-map]
    (assert (and AccessKeyId SecretAccessKey))
    {"aws_access_key_id" AccessKeyId
     "aws_secret_access_key" SecretAccessKey
     "aws_session_token" SessionToken
     :Expiration Expiration}))


(defn provider
  "Like profile-credentials-provider but with support for credential_process

   See https://github.com/cognitect-labs/aws-api/issues/73"
  ([]
   (provider (or (u/getenv "AWS_PROFILE")
                 (u/getProperty "aws.profile")
                 "default")))

  ([profile-name]
   (provider profile-name (or (io/file (u/getenv "AWS_CREDENTIAL_PROFILES_FILE"))
                              (io/file (u/getProperty "user.home") ".aws" "credentials"))))

  ([profile-name ^java.io.File f]
   (creds/auto-refreshing-credentials
    (reify creds/CredentialsProvider
      (fetch [_]
        (when (.exists f)
          (try
            (let [profile (get (config/parse f) profile-name)
                  profile (if-let [cmd (get profile "credential_process")]
                            (merge profile (get-credentials-via-cmd cmd))
                            profile)]
              (creds/valid-credentials
               {:aws/access-key-id     (get profile "aws_access_key_id")
                :aws/secret-access-key (get profile "aws_secret_access_key")
                :aws/session-token     (get profile "aws_session_token")
                ::creds/ttl (creds/calculate-ttl profile)}
               "aws profiles file"))
            (catch Throwable t
              (log/error t "Error fetching credentials from aws profiles file")
              {}))))))))
