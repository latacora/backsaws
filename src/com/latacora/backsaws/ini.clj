(ns com.latacora.backsaws.ini
  (:require
   [clojure.java.io :as io]
   [instaparse.core :as insta]
   [babashka.fs :as fs]
   [meander.epsilon :as m]))

(def ini-parser (-> "ini.ebnf" io/resource insta/parser))
(defn parse-path! [path] (-> path fs/expand-home fs/file slurp ini-parser))
(def parse-aws-config! (partial parse-path! "~/.aws/config"))

(defn ^:private get-sectionless-kvs
  [ini-parse]
  (m/search
   ini-parse
   [:ini
    [:body & (m/scan [:kv [:key ?k] [:wsp _] "=" [:wsp _] [:val ?v]])]]
   [nil ?k ?v]))

(defn ^:private get-sectioned-kvs
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

(defn get-kvs
  [ini-parse]
  ;; These two meander queries can probably be refactored or even combined but I
  ;; don't know how. I've asked in #meander on the Clojurians Slack but have not
  ;; yet received an answer.
  (mapcat #(% ini-parse) [get-sectionless-kvs get-sectioned-kvs]))
