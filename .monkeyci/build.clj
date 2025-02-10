(ns build
  (:require [monkey.ci.plugin
             [clj :as p]
             [github :as gh]]
            [monkey.ci.build.v2 :as m]))

[(->> (p/deps-library {:test-job-id "test-core"
                       :publish-job-id "publish-core"})
      (map #(m/work-dir % "core")))
 (->> (p/deps-library {:test-job-id "test-artemis"
                       :publish-job-id "publish-artemis"})
      (map #(m/work-dir % "artemis")))
 (gh/release-job {:dependencies ["publish-core" "publish-artemis"]})]
