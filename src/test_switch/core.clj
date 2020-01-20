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
  Returns a vector; the first element is the channel, the second is an atom that can be set to true to shut down this listener."
  []
  (let [gpio-chan (chan)
        off-switch (atom false)
        device  (gpio/device 0)
        watcher (gpio/watcher
                 device
                 {4 {::gpio/tag :switch
                     ::gpio/direction :input
                     ::gpio/edge-detection :rising
                     }})
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

(defn create-watcher-chan-both-directions
  "Listens for events on pin 4, debounces them, and puts them onto a chan.
  Returns a vector; the first element is the channel, the second is an atom that can be set to true to shut down this listener."
  []
  (let [gpio-chan (chan)
        gpio-multi-chan (mult gpio-chan)
        off-switch (atom false)
        device  (gpio/device 0)
        watcher (gpio/watcher
                 device
                 {4 {::gpio/tag :switch
                     ::gpio/direction :input
                     }})
        buff    (gpio/buffer watcher)
        last-event-timestamp (atom 0)
        debounce-wait-time-ns (* 2 1000 1000)
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
    {:gpio-multi-chan gpio-multi-chan
     :watcher watcher
     :buffer buff
     :off-switch off-switch}))

                                        ; state of the line can be read via (gpio/poll (:watcher foo) (:buffer foo) :switch)

(defn event-printer-multi-chan
  "A sample listener that pulls events from a channel and prints them out."
  [gpio-multi-chan]
  (let [my-chan (chan)]
    (tap gpio-multi-chan my-chan)
    (thread (while (if-some [event (<!! my-chan)]
                     (do
                       (println "Event: " event)
                       true)
                     false))
            (println "Exiting event-printer"))))

(defn create-logical-inputs-status-atom
  "Returns an atom which represents the logical status of board inputs and creates a thread
  that subscribes to an event multi-channel (intended to be a debounced signal) and updates
  the atom as board events come in."
  [gpio-multi-chan watcher buff]
  (let [inputs-status-atom (atom {:switch (gpio/poll watcher buff :switch)})
        my-chan (chan)]
    (tap gpio-multi-chan my-chan)
    (thread (while (if-some [event (<!! my-chan)]
                     (do
                       (when (= :switch (:dvlopt.linux.gpio/tag event))
                         (swap! inputs-status-atom
                                (fn [x] (assoc x
                                               :switch
                                               (if (= :rising (:dvlopt.linux.gpio/edge event))
                                                 true
                                                 false)))))
                       true)
                     false))
            (println "Exiting create-logical-inputs-status-atom"))
    inputs-status-atom))
