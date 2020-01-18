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

(defn create-watcher-chan
  "Listens for events on pin 4, debounces them, and puts them onto a chan.
  Returns a vector; the first element is the channel, the second is an atom that can be set to true to shut donw this listener."
  []
  (let [gpio-chan (chan)
        off-switch (atom false)
        device  (gpio/device 0)
        watcher (gpio/watcher
                 device
                 {4 {::gpio/tag :switch
                     ::gpio/direction :input
                     ::gpio/edge-detection :rising}})
        last-event-timestamp (atom 0)
        debounce-wait-time-ns (* 1 1000 1000)
        ]
    (thread (while (not @off-switch)
              (if-some [event (gpio/event watcher 1000)]
                (do
                  (swap! last-event-timestamp
                         (fn [timestamp]
                           (if (> (:dvlopt.linux.gpio/nano-timestamp event)
                                  (+ timestamp debounce-wait-time-ns))
                             (do
                               (put! gpio-chan event)
                               (:dvlopt.linux.gpio/nano-timestamp event))
                             timestamp)
                           ))
                   )))
            (println "Closing watcher")
            (gpio/close watcher)
            (gpio/close device)
            (close! gpio-chan)
            (println "Closed everything allocated."))
    [gpio-chan off-switch]))

(defn event-printer
  "A sample listener that pulls events from a channel and prints them out."
  [gpio-chan]
  (thread (while (if-some [event (<!! gpio-chan)]
                   (do
                     (println "Event: " event)
                     true)
                   false))
          (println "Exiting event-printer")))
