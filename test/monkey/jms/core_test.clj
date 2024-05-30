(ns monkey.jms.core-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [monkey.jms.core :as sut]))

(def url "amqp://localhost:61616")
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

(def broker-port 61617)

(defn start-broker []
)

(defn stop-broker [b]
)

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
      (is (nil? (sut/disconnect conn))))))
