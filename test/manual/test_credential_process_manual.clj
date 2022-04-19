#!/bin/sh
#_(
   set -eu
   
   #_ YO Make sure you run this from the root of the repo!
   
   echo "Using AWS profile $AWS_PROFILE"

   #_DEPS is same format as deps.edn. Multiline is okay.
   DEPS='
   {:deps {org.clojure/clojure         {:mvn/version "1.11.1"}
           com.cognitect.aws/api       {:mvn/version "0.8.539"}
           com.cognitect.aws/endpoints {:mvn/version "1.1.12.192"}
           com.cognitect.aws/s3        {:mvn/version "822.2.1109.0"}
           latacora/backsaws           {:local/root  "./"}}}
   '

   #_You can put other options here
   OPTS='
   -J-Xms256m -J-Xmx256m -J-client
   '

exec clojure $OPTS -Sdeps "$DEPS" -M "$0" "$@"
)

(require '[cognitect.aws.client.api :as aws])
(require '[com.latacora.backsaws.credential-process :as cp])

(-> (aws/client {:api :s3, :credentials-provider (cp/provider)})
    (aws/invoke {:op :ListBuckets})
    (:Buckets)
    (->> (map :Name))
    (println))
