(ns build
  (:refer-clojure :exclude [test])
  (:require
    [clojure.tools.build.api :as b] ; for b/git-count-revs
    [org.corfield.build :as bb]
    [clojure.edn :as edn]))

(def lib 'com.latacora/backsaws)
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(def ^:private aws-api-releases-file
  "https://raw.githubusercontent.com/cognitect-labs/aws-api/master/latest-releases.edn")

(-> aws-api-releases-file
    slurp
    edn/read-string
    )

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
