(ns pallet.algo.fsm.fsm
  "A finite state machine with externally maintained state."
  (:use
   [slingshot.slingshot :only [throw+]])
  (:require
   [clojure.tools.logging :as logging]))

;;; ## State identity function
(defn- state-identity
  "Returns a function to extract the state identity from the state.
  By default the state is just an identity."
  [state-map]
  (:fsm/fsm-state-identity state-map identity))

;;; ## State and transition validation
(defn- valid-state?-fn
  [state-map]
  (let [state-identity (state-identity state-map)]
    (fn valid-state? [state]
      (not= ::nil (get state-map (state-identity state) ::nil)))))

(defn- valid-transition?-fn
  [state-map]
  (let [state-identity (state-identity state-map)]
    (fn [from-state to-state]
      (let [v? (get-in state-map [(state-identity from-state) :transitions])]
        (and v? (v? (state-identity to-state)))))))

(defn- validate-state
  [state-map]
  (fn
    [state]
    (when (= ::nil (get state-map state ::nil))
      (throw+
       {:reason :invalid-state
        :state state
        :state-map state-map}
       "Invalid state %s" state state-map))))

(defn- validate-transition
  [state-map]
  (let [fsm-name (:fsm/name state-map)]
    (fn [from-state to-state]
      (let [v? (get-in state-map [from-state :transitions])]
        (when (or (nil? v?)
                  (not (v? to-state)))
          (throw+
           {:reason :invalid-transition
            :from-state from-state
            :to-state to-state
            :state-map state-map
            :fsm-name fsm-name}
           "Invalid transition%s: from %s to %s for %s"
           (if fsm-name (str " " fsm-name) "")
           from-state to-state state-map))))))

;;; ## Basic transition
(defn- transition-to-state-fn
  "Return a function that transitions to a new state"
  [state-map]
  (let [validate-state (validate-state state-map)
        validate-transition (validate-transition state-map)
        state-identity (state-identity state-map)]
    (fn transition-to-state [old-state new-state]
      (validate-state (state-identity old-state))
      (validate-transition
       (state-identity old-state) (state-identity new-state)))))

;;; ## On-enter and on-exit functions
(defn- exit-enter-fn
  "A function that calls on-exit and on-enter if a state has changed."
  [state-map]
  (let [state-identity (state-identity state-map)
        fsm-name (if-let [n (:fsm/name state-map)] (str n " - ") "")]
    (fn exit-enter
      [old-state new-state]
      (let [old-state-id (state-identity old-state)
            new-state-id (state-identity new-state)]
        (when (not= old-state-id new-state-id)
          (when-let [on-exit (get-in state-map [old-state-id :on-exit])]
            (on-exit old-state))
          (when-let [on-enter (get-in state-map [new-state-id :on-enter])]
            (on-enter new-state)))))))

(defn with-enter-exit
  [state-map]
  (let [exit-enter (exit-enter-fn state-map)]
    (fn transition [old-state new-state]
      (exit-enter old-state new-state))))

;;; ## Observers and loggers
(defn with-transition-observer
  "Middleware for adding a transition `observer` function.  The `observer` must
  be a function of two arguments, the old and new states. The return value of
  the observer is ignored."
  [observer]
  (fn [state-map]
    observer))

(defn with-transition-logger
  "Middleware to log transitions."
  [log-level]
  (fn [state-map]
    (let [fsm-name (if-let [n (:fsm/name state-map)] (str n " - ") "")]
      (fn [old-state new-state]
        (logging/logf
         log-level "%stransition from %s to %s"
         fsm-name old-state new-state)))))

(defn with-transition-identity-logger
  "Middleware to log transition identities."
  [log-level]
  (fn [state-map]
    (let [fsm-name (if-let [n (:fsm/name state-map)] (str n " - ") "")
          state-identity (state-identity state-map)]
      (fn [old-state new-state]
        (logging/logf
         log-level "%s%s -> %s"
         fsm-name (state-identity old-state) (state-identity new-state))))))

;;; ## Feature plugin processing
(def ^{:private true}
  builtin-middleware
  {:on-enter-exit with-enter-exit})

(defn- transition-fn [features state-map]
  (let [lock-transition-feature? #(= :lock-transition %)
        lock? (some lock-transition-feature? features)
        fs (->> (concat [transition-to-state-fn]
                        (remove lock-transition-feature? features))
                (map #(builtin-middleware %1 %1))
                (map #(% state-map)))]
    (if lock?
      (fn [old-state new-state]
        (locking (::lock state-map)
          (doseq [f fs]
            (f old-state new-state)))
        new-state)
      (fn [old-state new-state]
        (doseq [f fs]
          (f old-state new-state))
        new-state))))

;;; ## FSM

(defn fsm
  "Returns a Finite State Machine where the current state is managed by the
user.

state-map
: a map from state key to a set of valid transition states, or to a map with
  a :transitions key with a set of valid tranisition keys, and any keys required
  by the features in use.

features
: a set of features that the FSM should support. Features are either named by
  keyword for the builtin feature, :on-enter-exit, or are plugin functions that
  should take a state-map and return a function taking the old and new states
  as arguments. The return value of the plugin function is ignored. There are
  three plugin constructor functions provided, `with-transition-observer`
  `with-transition-logger`, and `with-transition-identity-logger`.

Returns a map with :transition, :valid-state? and valid-transition? keys,
providing functions to change the state, test for a valid state and test for a
valid transition, respectively.

The :transition function takes an old state and a new state, and returns the new
state. An exception is thrown if the transition is invalid.

The :valid-state? predicate tests a state for validity.

The :valid-transition? predicate tests an old state, new state pair for
validity.

On enter and on exit functions
------------------------------

On enter and on exit functions are enabled by the :on-enter-exit feature.

The :on-enter and :on-exit keys on the state-map values should map to functions
of a state argument. These functions can be used to manage external state, and
will be called as appropriate by the :transition function."
  ([state-map features]
     (let [state-map
           (into {} (map
                     (fn [[k v :as entry]]
                       (cond
                        (and (keyword? k) (= "fsm" (namespace k))) entry
                        (map? v) entry
                        :else [k {:transitions v}]))
                     state-map))
           lock-object (when (some #(= :lock-transition %) features)
                         (Object.))]
       {:transition (transition-fn
                     (set features)
                     (assoc state-map ::lock lock-object))
        :valid-state? (valid-state?-fn state-map)
        :valid-transition? (valid-transition?-fn state-map)
        ::features features
        :state-identity (state-identity state-map)
        ::lock lock-object}))
  ([config]
     (fsm
      (dissoc config
              :fsm/fsm-features :fsm/event-machine-features :fsm/inital-state)
      (:fsm/fsm-features config))))

(defn with-fsm-feature
  "Returns the `fsm-spec` with specified `feature` added to it."
  [fsm-spec feature]
  (update-in fsm-spec [:fsm/fsm-features] concat [feature]))
