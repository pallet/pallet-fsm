(defproject pallet-fsm "0.2.0-SNAPSHOT"
  :description "Finite state machine library"
  :url "https://github.com/pallet/pallet-fsm"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:url "git@github.com:pallet/pallet-fsm.git"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/algo.monads "0.1.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [pallet-thread "0.1.0"]]
  :warn-on-reflection true)
