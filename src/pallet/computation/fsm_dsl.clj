(ns pallet.computation.fsm-dsl
  "A DSL for generating fsm and event-machine state-map's"
  (:use
   [clojure.set :only [union]]
   [clojure.algo.monads :only [state-m domonad]]))

(def fsm-m state-m)

(defmacro let-s
  "A monadic comprehension using the fsm-m monad."
  [& body]
  `(domonad fsm-m
     ~@body))

(defmacro chain-s
  "Defines a monadic comprehension under the fsm-m monad, where return value
  bindings not specified. Any vector in the arguments is expected to be of the
  form [symbol expr] and becomes part of the generated monad comprehension."
  [& args]
  (letfn [(gen-step [f]
            (if (vector? f)
              f
              [(gensym "_") f]))]
    (let [bindings (mapcat gen-step args)]
      `(let-s
         [~@bindings]
         ~(last (drop-last bindings))))))

(defn initial-state
  [state-kw]
  (fn [c]
    [nil (assoc-in c [:fsm/inital-state :state-kw] state-kw)]))

(defn initial-state-data
  [state-data]
  (fn [c]
    [nil (assoc-in c [:fsm/inital-state :state-data] state-data)]))

(defn using-fsm-features
  [& features]
  (fn [c]
    [nil (update-in c [:fsm/fsm-features] union (set features))]))

(defn using-event-machine-features
  [& features]
  (fn [c]
    [nil (update-in c [:fsm/event-machine-features] union (set features))]))

(defmacro state
  {:indent 1}
  [state & body]
  `(fn [c#]
     [nil (assoc c# ~state (when-let [f# (chain-s ~@body)] (second (f# {}))))]))

(defn valid-transitions
  [& states]
  (fn [c]
    [nil (assoc-in c [:transitions] (set states))]))

(defn on-enter
  [f]
  (fn [c]
    [nil (assoc-in c [:on-enter] f)]))

(defmacro on-enter-fn
  "Define a function used for :on-enter"
  [[state] & body]
  `(fn [c#]
     [nil (assoc-in c# [:on-enter] (fn [~state] ~@body))]))

(defn on-exit
  [f]
  (fn [c]
    [nil (assoc-in c [:on-exit] f)]))

(defmacro on-exit-fn
  "Define a function used for :on-exit"
  [[state] & body]
  `(fn [c#]
     [nil (assoc-in c# [:on-exit] (fn [~state] ~@body))]))

(defn event-handler
  [f]
  (fn [c]
    [nil (assoc-in c [:event-fn] f)]))

(defmacro event-handler-fn
  "Define a function used for :event-fn"
  [[state event event-data] & body]
  `(fn [c#]
     [nil (assoc-in c# [:event-fn] (fn [~state ~event ~event-data] ~@body))]))

(defn state-driver
  [f]
  (fn [c]
    [nil (assoc-in c [:state-fn] f)]))

(defmacro state-driver-fn
  "Define a function used for :event-fn"
  [[state] & body]
  `(fn [c#]
     [nil (assoc-in c# [:state-fn] (fn [~state] ~@body))]))

(defn infer-features
  [c]
  (let [c (if (some
               (fn [[k v]] (when (map? v) (or (:on-enter v) (:on-exit v))))
               c)
            (update-in c [:fsm/fsm-features] union #{:on-enter-exit})
            c)
        c (if (:timed-out c)
            (update-in c [:fsm/fsm-features] union #{:timeout})
            c)]
    c))

(defn verify-event-machine-config
  [c]
  [nil (infer-features c)])

(defn verify-fsm-config
  [c]
  [nil (infer-features c)])

(defmacro event-machine-config
  [& body]
  `(second ((chain-s ~@body verify-event-machine-config) {})))

(defmacro fsm-config
  [& body]
  `(second ((chain-s ~@body verify-fsm-config) {})))

;;; ## FSM configuration inspection

(defn configured-states
  [fsm-config]
  (remove #(= "fsm" (namespace %)) (keys fsm-config)))
