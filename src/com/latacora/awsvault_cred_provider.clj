(ns com.latacora.awsvault-cred-provider
  (:require
   [clojure.java.shell :as sh]
   [cognitect.aws.credentials :as awscreds]
   [clojure.string :as str]))

(defn ^:private aws-vault-exec!
  [profile]
  (let [raw-vars (sh/sh "aws-vault" "exec" profile "--" "env" "-0")
        xf (comp
            (filter #(str/starts-with? % "AWS_"))
            (keep (fn [line]
                    (let [[k v] (str/split line #"=" 2)
                          k (case k
                              "AWS_ACCESS_KEY_ID" :AccessKeyId
                              "AWS_SECRET_ACCESS_KEY" :SecretAccessKey
                              "AWS_SESSION_TOKEN" :SessionToken
                              "AWS_SESSION_EXPIRATION" :Expiration
                              nil)]
                      (when k [k v])))))]
    (into {} xf (-> raw-vars :out (str/split (re-pattern "\0"))))))

(defn aws-vault-provider
  [profile]
  (awscreds/cached-credentials-with-auto-refresh
   (reify awscreds/CredentialsProvider
     (fetch [_]
       (let [creds (aws-vault-exec! profile)]
         (awscreds/valid-credentials
          {:aws/access-key-id (:AccessKeyId creds)
           :aws/secret-access-key (:SecretAccessKey creds)
           :aws/session-token (:SessionToken creds)
           ::awscreds/ttl (awscreds/calculate-ttl creds)}
          (format "aws-vault with profile %s" profile)))))))
