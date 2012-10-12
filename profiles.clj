{:codox {:codox {:writer codox-md.writer/write-docs
                 :output-dir "doc/0.1"}
         :dependencies [[codox-md "0.1.0"]
                        [codox/codox.core "0.6.1"]]
         :pedantic :warn}
 :marginalia {:pedantic :warn
              :dir "doc/0.1/annotated"}
 :release
 {:plugins [[lein-set-version "0.2.1"]]
  :set-version
  {:updates [{:path "README.md"
              :no-snapshot true
              :search-regex #"pallet-fsm \"\d+\.\d+\.\d+\""}]}}}
