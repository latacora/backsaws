(ns com.latacora.backsaws.ini-test
  (:require
   [com.latacora.backsaws.ini :as i]
   [clojure.test :as t]))

(def one-kv-outside-section
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

(def sectioned-and-unsectioned-kvs
  (i/ini-parser "z = 3\n[xyzzy]\nx = 1\n[iddqd]\ny=2"))

(def malformed-empty-key-no-header
  (i/ini-parser "="))

(def malformed-empty-key-with-header
  (i/ini-parser "[x]\n="))

(t/deftest parse-test
  (t/is
   (= [:ini
       [:body
        [:kv [:key "x"] [:wsp ""] "=" [:wsp ""] [:val "1"]] [:wsp ""]]]
      one-kv-outside-section))

  (t/is
   (= [:ini
       [:body]
       [:section
        [:header "[" [:wsp ""] [:name "x"] [:wsp ""] "]" [:wsp ""]]]]
      empty-section))

  (t/is
   (= [:ini
       [:body]
       [:section
        [:header "[" [:wsp ""] [:name "x"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body]]]
      empty-section-with-newline))

  (t/is
   (= [:ini
       [:body]
       [:section
        [:header "[" [:wsp ""] [:name "xyzzy"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body [:kv [:key "x"] [:wsp " "] "=" [:wsp " "] [:val "1"]] [:wsp ""]]]]
      one-section-with-one-kv))

  (t/is
   (= [:ini
       [:body]
       [:section
        [:header "[" [:wsp ""] [:name "xyzzy"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body
         [:kv [:key "x"] [:wsp " "] "=" [:wsp " "] [:val "1"]]
         [:wsp ""]
         [:eol "\n"]
         [:kv [:key "y"] [:wsp " "] "=" [:wsp " "] [:val "2"]]
         [:wsp ""]]]]
      one-section-with-two-kvs))

  (t/is
   (= [:ini
       [:body]
       [:section
        [:header "[" [:wsp ""] [:name "xyzzy"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body
         [:kv [:key "x"] [:wsp " "] "=" [:wsp " "] [:val "1"]]
         [:wsp ""]
         [:eol "\n"]]]
       [:section
        [:header "[" [:wsp ""] [:name "iddqd"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body [:kv [:key "y"] [:wsp ""] "=" [:wsp ""] [:val "2"]] [:wsp ""]]]]
      two-sections))

  (t/is
   (= [:ini
       [:body
        [:kv [:key "z"] [:wsp " "] "=" [:wsp " "] [:val "3"]]
        [:wsp ""]
        [:eol "\n"]]
       [:section
        [:header "[" [:wsp ""] [:name "xyzzy"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body
         [:kv [:key "x"] [:wsp " "] "=" [:wsp " "] [:val "1"]]
         [:wsp ""]
         [:eol "\n"]]]
       [:section
        [:header "[" [:wsp ""] [:name "iddqd"] [:wsp ""] "]" [:wsp ""]]
        [:eol "\n"]
        [:body [:kv [:key "y"] [:wsp ""] "=" [:wsp ""] [:val "2"]] [:wsp ""]]]]
      sectioned-and-unsectioned-kvs))

  (t/is
   (= [:ini
       [:body
        [:kv [:key ""] [:wsp ""] "=" [:wsp ""] [:val ""]] [:wsp ""]]]
      malformed-empty-key-no-header))

  (t/is
   (= [:ini
       [:body]
       [:section
        [:header "[" [:wsp ""] [:name "x"] [:wsp ""] "]" [:wsp ""]] [:eol "\n"]
        [:body [:kv [:key ""] [:wsp ""] "=" [:wsp ""] [:val ""]] [:wsp ""]]]]
      malformed-empty-key-with-header)))

(t/deftest get-kvs-test
  (t/is (= [[nil "x" "1"]]
           (i/get-kvs one-kv-outside-section)))

  (t/is (= []
           (i/get-kvs empty-section)
           (i/get-kvs empty-section-with-newline)))

  (t/is (= [["xyzzy" "x" "1"]]
           (i/get-kvs one-section-with-one-kv)))

  (t/is (= [["xyzzy" "x" "1"]
            ["xyzzy" "y" "2"]]
           (i/get-kvs one-section-with-two-kvs)))

  (t/is (= [["xyzzy" "x" "1"]
            ["iddqd" "y" "2"]]
           (i/get-kvs two-sections))))
