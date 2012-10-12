(defproject pallet-fsm "0.1.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/algo.monads "0.1.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [pallet-thread "0.1.0"]
                 [slingshot "0.10.2"]]
  :warn-on-reflection true
  :profiles {:dev {:dependencies [[ch.qos.logback/logback-classic "1.0.0"]]}})
