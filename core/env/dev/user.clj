(ns user
  (:require [monkey.jms :as jms]))

(def ctx
  (jms/connect {:url "amqp://localhost:5672"
                :client-id (str (random-uuid)) ; Must be unique for all connections
                :username "artemis"
                :password "artemis"}))

(def topic "topic://test.topic")
(def queue "queue://test.queue")

(defn consume
  ([id]
   ;; Id must be unique within the same client
   (jms/consume ctx topic #(println "Got message on" id ":" %)
                {:id id}))
  ([]
   (jms/consume ctx queue #(println "Got message on queue:" %)
                {})))

(defn produce [msg]
  (let [p (jms/produce ctx topic)]
    (p msg)))
