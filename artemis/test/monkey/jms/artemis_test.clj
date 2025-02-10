(ns monkey.jms.artemis-test
  (:require [clojure.test :refer [deftest testing is]]
            [monkey.jms.artemis :as sut]))

(deftest embedded-broker
  (testing "can start and stop"
    (let [b (sut/start-broker {})]
      (is (some? b))
      (is (= b (sut/stop-broker b))))))
