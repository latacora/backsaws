(ns build
  (:refer-clojure :exclude [test])
  (:require
    [clojure.tools.build.api :as b] ; for b/git-count-revs
    [org.corfield.build :as bb]))

(def lib 'com.latacora/backsaws)
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn test "Run the tests." [opts]
  ;; The :test alias has no :main-opts, on purpose (see deps.edn), and build-clj falls back
  ;; to Cognitect's test-runner when it finds none — which is not a dependency here, so
  ;; this task and `ci` below both died on a missing namespace. Name kaocha instead.
  (bb/run-tests (assoc opts :main-args ["-m" "kaocha.runner"])))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (test)
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
