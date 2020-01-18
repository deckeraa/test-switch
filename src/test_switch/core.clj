(ns test-switch.core
  (:require [dvlopt.linux.gpio :as gpio]
            [clojure.core.async :refer :all]))

(defn test-switch []
  (with-open [device  (gpio/device 0) ; device 0 is the Pi's GPIO bank
              watcher (gpio/watcher
                       device
                       {4 {::gpio/tag :switch    ; :switch is just a name we give to the pin
                           ::gpio/direction :input
                           ::gpio/edge-detection :rising}})]
    (while true
      (if-some [event (gpio/event watcher -1)]
        (println "Event: " event)
          ))))

(defn create-watcher-chan []
  (let [gpio-chan (chan)
        off-switch (atom false)
        device  (gpio/device 0)
        watcher (gpio/watcher
                 device
                 {4 {::gpio/tag :switch
                     ::gpio/direction :input
                     ::gpio/edge-detection :rising}})
        last-event-timestamp (atom 0)
        debounce-wait-time-ns 500000
        ]
    (thread (while (not @off-switch)
              (if-some [event (gpio/event watcher 1000)]
                (do
                  (println "Event: " event)
                  (swap! last-event-timestamp
                         (fn [timestamp]
                           (if (> (:dvlopt.linux.gpio/nano-timestamp event)
                                  (+ timestamp debounce-wait-time-ns))
                             (do
                               (put! gpio-chan event)
                               (:dvlopt.linux.gpio/nano-timestamp event))
                             timestamp)
                           ))
                  (println "@last-event-timestamp is now " @last-event-timestamp)
                  )))
            (println "Closing watcher")
            (gpio/close watcher)
            (gpio/close device)
            (println "Closed everything allocated."))
    [gpio-chan off-switch]))
