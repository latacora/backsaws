(ns com.latacora.backsaws.ini
  (:require
   [clojure.java.io :as io]
   [instaparse.core :as insta]
   [babashka.fs :as fs]
   [meander.epsilon :as m]))

(def ini-parser
  "An instaparse parser for ini files.

  The grammar takes the strategy of maintaining all tokens in the parse tree and
  as such does not remove things like whitespace, or parts of syntax like [, ]
  and = that are implicit in the parse tree. This is intentional. By capturing
  all strings, the resulting parse tree can be reserialized while maximally
  maintaining formatting: just concat all the strings."
  (-> "ini.ebnf" io/resource insta/parser))

(defn parse-path! [path] (-> path fs/expand-home fs/file slurp ini-parser))
(def parse-aws-config! (partial parse-path! "~/.aws/config"))

(defn ^:private get-sectionless-kvs
  [parsed-ini]
  (m/search
   parsed-ini
   [:ini
    [:body & (m/scan [:kv [:key ?k] [:wsp _] "=" [:wsp _] [:val ?v]])]]
   [nil ?k ?v]))

(defn ^:private get-sectioned-kvs
  [parsed-ini]
  (m/search
   parsed-ini
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
  [parsed-ini]
  ;; These two meander queries can probably be refactored or even combined but I
  ;; don't know how. I've asked in #meander on the Clojurians Slack but have not
  ;; yet received an answer.
  (mapcat #(% parsed-ini) [get-sectionless-kvs get-sectioned-kvs]))
