(ns monkey.jms-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monkey.jms :as sut]
            [monkey.jms.artemis :as artemis]))

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

(defn with-broker [f]
  (artemis/with-broker {:broker-port broker-port} f))

(use-fixtures :once with-broker)

(deftest produce-consume
  (testing "can produce and consume messages async"
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
        (is (nil? (sut/disconnect conn))))))

  (testing "can poll for messages"
    (let [client-id "test-client"
          id "sync-subscription"
          conn (sut/connect {:url url
                             :username "artemis"
                             :password "artemis"
                             :client-id client-id})
          producer (sut/make-producer conn topic)
          consumer (sut/consume conn topic {:id id})
          msg "This is another test message"]
      (is (some? (producer msg)))
      (is (= msg (consumer 1000)))))

  (testing "can handle `BytesMessage`"
    (let [client-id (str (random-uuid))
          id "bytes-subscription"
          conn (sut/connect {:url url
                             :username "artemis"
                             :password "artemis"
                             :client-id client-id})
          make-bytes-message (fn [ctx s]
                               (let [msg (.createBytesMessage ctx)]
                                 (.writeBytes msg (.getBytes s))
                                 msg))
          producer (sut/make-producer conn topic
                                      {:serializer make-bytes-message})
          consumer (sut/consume conn topic {:id id})
          msg "This is another test message"]
      (is (some? (producer msg)))
      (is (= msg (consumer 1000))))))
