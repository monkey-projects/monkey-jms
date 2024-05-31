(ns monkey.jms.core-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monkey.jms.core :as sut])
  (:import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
           org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
           org.apache.activemq.artemis.api.core.TransportConfiguration
           org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory))

(def broker-port 61617)
(def url (format "amqp://localhost:%d" broker-port))
(def topic "topic://test.local")

(defn- wait-for [f]
  (let [timeout 1000
        start (System/currentTimeMillis)]
    (loop [now start]
      (if (> now (+ start timeout))
        :timeout
        (if-let [v (f)]
          v
          (recur (System/currentTimeMillis)))))))

(def transport-config
  (TransportConfiguration.
   (.getName NettyAcceptorFactory)
   {"port" (str broker-port)
    "protocols" "AMQP"}))

(defn start-broker
  "Starts an embedded Artemis broker with AMQP connector"
  []
  (doto (EmbeddedActiveMQ.)
    (.setConfiguration
     (.. (ConfigurationImpl.)
         (setPersistenceEnabled false)
         (setJournalDirectory "target/data/journal")
         (setSecurityEnabled false)
         (addAcceptorConfiguration transport-config)))
    (.start)))

(defn stop-broker [b]
  (.stop b))

(defn with-broker [f]
  (let [b (start-broker)]
    (try
      (f)
      (finally 
        (stop-broker b)))))

(use-fixtures :once with-broker)

(deftest produce-consume
  (testing "can produce and consume messages"
    (let [conn (sut/connect {:url url
                             :username "artemis"
                             :password "artemis"})
          recv (atom [])
          handler (partial swap! recv conj)
          producer (sut/make-producer conn topic)
          consumer (sut/consume conn topic handler)
          msg "This is a test message"]
      (is (some? conn))
      (is (ifn? producer))
      (is (some? (producer msg)))
      (is (not= :timeout (wait-for #(not-empty @recv))))
      (is (= msg (first @recv)))
      (is (nil? (sut/disconnect conn)))))

  (testing "can produce and consume durable messages"
    (let [client-id "test-client"
          id "test-subscription"
          conn (sut/connect {:url url
                             :username "artemis"
                             :password "artemis"
                             :client-id client-id})
          recv (atom [])
          handler (partial swap! recv conj)
          producer (sut/make-producer conn topic)
          ;; Create consumer to register it
          consumer (sut/consume conn topic handler {:id id})
          msg "This is a test message"]
      (is (some? conn))
      ;; Shut down the consumer first
      (is (nil? (.close consumer)))
      (is (ifn? producer))
      ;; Produce a message and then start consuming.  We expect the message
      ;; to be received anyway.
      (is (some? (producer msg)))
      (let [consumer (sut/consume conn topic handler {:id id})]
        (is (not= :timeout (wait-for #(not-empty @recv))))
        (is (= msg (first @recv)))
        (is (nil? (sut/disconnect conn)))))))
