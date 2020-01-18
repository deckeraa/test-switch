(defproject test-switch "0.1.0-SNAPSHOT"
  :description "A Clojure library that demonstrates de-bouncing a microswitch being read via the dvlopt.linux.gpio library."
  :url "https://stronganchortech.com/responding-to-gpio-input-events-in-clojure-on-a-rasp-pi-4"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.7.559"]
                 [dvlopt/linux.gpio "1.0.0"]]
  :repl-options {:init-ns test-switch.core})
