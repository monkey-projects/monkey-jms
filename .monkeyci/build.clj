(ns build
  (:require [monkey.ci.plugin
             [clj :as p]
             [github :as gh]]
            [monkey.ci.build.v2 :as m]))

(defn sub-lib [dir]
  (fn [ctx]
    (->> ((p/deps-library {:test-job-id (str "test-" dir)
                           :publish-job-id (str "publish-" dir)}) ctx)
         (map #(m/work-dir % dir)))))

[(sub-lib "core")
 (sub-lib "artemis")
 (gh/release-job {:dependencies ["publish-core" "publish-artemis"]})]
