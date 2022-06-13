(ns com.latacora.backsaws.ini
  (:require
   [clojure.java.io :as io]
   [instaparse.core :as insta]
   [babashka.fs :as fs]
   [meander.epsilon :as m]))

(def ini-parser (-> "ini.ebnf" io/resource insta/parser))
(defn parse-path! [path] (-> path fs/expand-home fs/file slurp ini-parser))
(def parse-aws-config! (partial parse-path! "~/.aws/config"))

(defn get-kvs
  [ini-parse]
  (m/search
   ini-parse
   [:ini
    [:body . _ ...]
    &
    (m/scan
     [:section
      [:header & (m/scan [:name ?h])]
      . _ ...
      [:body & (m/scan [:kv [:key ?k] [:wsp _] "=" [:wsp _] [:val ?v]])]
      . _ ...])]
   [?h ?k ?v]))
