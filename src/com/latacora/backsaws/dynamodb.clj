(ns com.latacora.backsaws.dynamodb
  (:require [clojure.data.json :as json])
  (:import java.util.Base64))

(defmulti decode
  "Given a DynamoDB map encoded data structure, returns a simplified clojure data structure."
  (fn [encoded]
    (-> encoded first key keyword)))

(defmethod decode :BOOL [v]
  (:BOOL v))

(defmethod decode :S [v]
  (:S v))

(defmethod decode :SS [v]
  (set (:SS v)))

(defmethod decode :N [v]
  (json/read-str (:N v)))

(defmethod decode :NS [v]
  (set (map json/read-str (:NS v))))

(defmethod decode :M [v]
  (reduce-kv (fn [m k v]
               (assoc m k (decode v)))
             {}
             (:M v)))

(defmethod decode :L [v]
  (mapv decode (:L v)))

(defmethod decode :NULL [v]
  nil)

(defmethod decode :B [v]
  (.decode (Base64/getDecoder)
           (:B v)))

(defn encode-bytes [^bytes input]
  (new String (.encode (Base64/getEncoder) input) "utf-8"))

(defprotocol IDynamoDBEncode
  (encode [this]))

(extend-protocol IDynamoDBEncode
  (Class/forName "[B")
  (encode [this]
    {:B (encode-bytes this)})

  Number
  (encode [this] {:N (json/json-str this)})

  String
  (encode [this] {:S this})

  Boolean
  (encode [this] {:BOOL (if this true false)})

  clojure.lang.IPersistentVector
  (encode [this]
    {:L (mapv encode this)})

  clojure.lang.IPersistentList
  (encode [this]
    {:L (mapv encode this)})

  clojure.lang.IPersistentSet
  (encode [this]
    (cond
      (every? number? this)
      {:NS  (mapv json/json-str this)}

      (every? string? this)
      {:SS (mapv identity this)}

      (every? bytes? this)
      {:BS (mapv encode-bytes this)}

      :else
      (throw
       (ex-info
        "Cannot encode set, is invalid."))))

  clojure.lang.IPersistentMap
  (encode [this]
    {:M (reduce-kv (fn [m k v]
                     (assoc m k (encode v)))
                   {}
                   this)}))

(comment
  (decode {:M {:foo {:S "asd"} :bar {:N "333"} :baz {:L [{:S "asd"}]}}})

  (decode {:N "2"})

  (decode {:M (:Item result)}))
