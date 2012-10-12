(defproject pallet-fsm "0.1.1"
  :description "Finite state machine library"
  :url "https://github.com/pallet/pallet-fsm"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:url "git@github.com:pallet/pallet-fsm.git"}
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/algo.monads "0.1.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [pallet-thread "0.1.0"]
                 [slingshot "0.10.2"]]
  :warn-on-reflection true
  :profiles {:dev {:dependencies [[ch.qos.logback/logback-classic "1.0.0"]]}})
