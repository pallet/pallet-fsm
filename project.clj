(defproject pallet-fsm "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/algo.monads "0.1.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [pallet-thread "0.1.0-SNAPSHOT"]
                 [slingshot "0.10.2"]]
  :profiles {:dev {:dependencies [[codox-md "0.1.0"] [codox "0.6.0"]]}}
  :codox {:writer codox-md.writer/write-docs})
