(ns com.latacora.backsaws.ini-test
  (:require
   [com.latacora.backsaws.ini :as i]
   [clojure.test :as t]))

(def no-section
  (i/ini-parser "x=1"))

(def empty-section
  (i/ini-parser "[x]"))

(def empty-section-with-newline
  (i/ini-parser "[x]\n"))

(def one-section-with-one-kv
  (i/ini-parser "[xyzzy]\nx = 1"))

(def one-section-with-two-kvs
  (i/ini-parser "[xyzzy]\nx = 1\ny = 2"))

(def two-sections
  (i/ini-parser "[xyzzy]\nx = 1\n[iddqd]\ny=2"))

(t/deftest get-kvs-test
  (t/is (= [["xyzzy" "x" "1"]]
           (i/get-kvs one-section-with-one-kv)))

  (t/is (= [["xyzzy" "x" "1"]
            ["xyzzy" "y" "2"]]
           (i/get-kvs one-section-with-two-kvs)))

  (t/is (= [["xyzzy" "x" "1"]
            ["iddqd" "y" "2"]]
           (i/get-kvs two-sections))))
