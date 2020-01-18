(ns test-switch.core
  (:require [dvlopt.linux.gpio :as gpio]
            [clojure.core.async :refer [go go-loop <! timeout]]))

(defn test-switch []
  (with-open [device  (gpio/device 0)
              watcher (gpio/watcher
                       device
                       {4 {::gpio/tag :switch
                           ::gpio/direction :input
                           ::gpio/edge-detection :rising}})]
    (while true
      (if-some [event (gpio/event watcher 1000)]
        (do
          (println "Event: " event)
          )))))
