(ns pallet.computation.fsm-utils)

;; this belongs in some util lib
(defn swap!!
  "Like swap!, but returns a vector of old and new value"
  [atom f & args]
  {:pre [(instance? clojure.lang.Atom atom)]}
  (let [old-val (clojure.core/atom nil)
        new-val (swap! atom (fn [s]
                           (reset! old-val s)
                           (apply f s args)))]
    [@old-val new-val]))
