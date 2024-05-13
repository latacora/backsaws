(ns com.latacora.backsaws.dynamodb-test
  (:require [com.latacora.backsaws.dynamodb :as ddb]
            [clojure.test :as t]))


(def test-val
  {:string "asd"
   :number 333
   :stringset #{"this" "is" "a" "string" "set"}
   :list [1 "string" 3]
   :numberset #{1 2 3 4}
   :bytes (.getBytes "foo")
   :bool-true true
   :bool-false false})

;; hand encoded to spec
(def encoded-val
  {:M {:string {:S "asd"}
       :number {:N "333"}
       :stringset {:SS ["this" "is" "a" "string" "set"]}
       :list {:L [{:N "1"} {:S "string"} {:N "3"}]}
       :numberset {:NS ["1" "2" "3" "4"]}
       :bytes {:B "Zm9v"}
       :bool-true {:BOOL true}
       :bool-false {:BOOL false}}})

(t/deftest test-decode
  (t/is (= #{1 2 3} (ddb/decode {:NS ["1" "2" "3"]})))
  (t/is (= "foo" (ddb/decode {:S "foo"})))
  (t/is (= 3 (ddb/decode {:N "3"})))
  (t/is (= 3.33 (ddb/decode {:N "3.33"})))
  (t/is (= #{"foo" "bar"} (ddb/decode {:SS ["foo" "bar"]})))
  (let [eb {:B "Zm9v"}]
    (t/is (= eb (ddb/encode (ddb/decode eb)))))
  (t/is
   (= (dissoc test-val :bytes)
      (dissoc (ddb/decode encoded-val) :bytes))
   "Encode and decode are identical, except bytes, for which equality doesn't work"))

(t/deftest test-encode
  (t/testing "booleans"
    (t/is (= {:BOOL true} (ddb/encode true)))
    (t/is (= {:BOOL false} (ddb/encode false))))
  (t/testing "lists"
    (t/is (= {:L [{:N "1"} {:N "2"} {:N "3"}]} (ddb/encode [1 2 3])))
    (t/is (= {:L [{:N "1"} {:S "string"} {:N "3"}]} (ddb/encode [1 "string" 3]))))
  (t/testing "map"
    (t/is (= {:M {:bar {:S "baz"} :foo {:N "2"} :nested {:M {:another {:S "value"}}}}}
             (ddb/encode {:bar "baz" :foo 2 :nested {:another "value"}}))))
  (t/testing "sets"
    (t/is (= {:NS ["1"]} (ddb/encode #{1})))
    (t/is (= {:SS ["foo"]} (ddb/encode #{"foo"})))))
