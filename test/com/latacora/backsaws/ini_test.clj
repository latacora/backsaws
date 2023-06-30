(ns com.latacora.backsaws.ini-test
  (:require
   [com.latacora.backsaws.ini :as i]
   [clojure.test :as t]))

(t/deftest parser-alias-tests
  (t/is (identical? i/parser i/parse)))

(def one-kv-outside-section
  (i/parse "x=1"))

(def empty-section
  (i/parse "[x]"))

(def empty-section-with-newline
  (i/parse "[x]\n"))

(def one-section-with-one-kv
  (i/parse "[xyzzy]\nx = 1"))

(def one-section-with-two-kvs
  (i/parse "[xyzzy]\nx = 1\ny = 2"))

(def two-sections
  (i/parse "[xyzzy]\nx = 1\n[iddqd]\ny=2"))

(def sectioned-and-unsectioned-kvs
  (i/parse "z = 3\n[xyzzy]\nx = 1\n[iddqd]\ny=2"))

(def malformed-empty-key-no-header
  (i/parse "="))

(def malformed-empty-key-with-header
  (i/parse "[x]\n="))

(def header-more-chars
  (i/parse "[profile myprofile.sso.admin-prod]"))

(def kv-cred-proc
  (i/parse "credential_process = /bin/bash -c \"echo mycredentials\""))

(def kv-role-arn
  (i/parse "role_arn=arn:aws:iam::123456789012:role/resource-tagger"))

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
      malformed-empty-key-with-header))

  (t/is
    (=
       [:ini
        [:body]
        [:section
         [:header "[" [:wsp ""] [:name "profile myprofile.sso.admin-prod"] [:wsp ""] "]" [:wsp ""]]]]
       header-more-chars))

  (t/is
    (= [:ini
        [:body
         [:kv
          [:key "credential_process"] [:wsp " "] "=" [:wsp " "]
          [:val "/bin/bash -c \"echo mycredentials\""]] [:wsp ""]]]
     kv-cred-proc))

  (t/is
    (=
     [:ini
      [:body
       [:kv
        [:key "role_arn"] [:wsp ""] "=" [:wsp ""]
        [:val "arn:aws:iam::123456789012:role/resource-tagger"]] [:wsp ""]]]
     kv-role-arn)))

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
