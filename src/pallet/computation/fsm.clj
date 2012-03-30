(ns pallet.computation.fsm
  "A finite state machine lib"
  (:use
   [slingshot.slingshot :only [throw+]])
  (:require
   [clojure.tools.logging :as logging]))

;;; ## Implementation functions
(defn valid-state?-fn
  [state-map]
  (fn valid-state? [state]
    (not= ::nil (get state-map state ::nil))))

(defn valid-transition?-fn
  [state-map]
  (fn [from-state to-state]
    (let [v? (get-in state-map [from-state :transitions])]
      (and v? (v? to-state)))))

(defn validate-state
  [state-map]
  (fn
    [state]
    (when (= ::nil (get state-map state ::nil))
      (throw+
       {:reason :invalid-state
        :state state
        :state-map state-map}
       "Invalid state %s" state state-map))))

(defn validate-transition
  [state-map]
  (fn [from-state to-state]
    (let [v? (get-in state-map [from-state :transitions])]
      (when (or (nil? v?)
                (not (v? to-state)))
        (throw+
         {:reason :invalid-transition
          :from-state from-state
          :to-state to-state
          :state-map state-map}
         "Invalid transition from %s to %s for %s"
         from-state to-state state-map)))))

(defn transition-to-state-fn
  "Return a function that transitions to a new state"
  [state-map]
  (let [validate-state (validate-state state-map)
        validate-transition (validate-transition state-map)]
    (fn transition-to-state [old-state new-state]
      (validate-state old-state)
      (validate-transition old-state new-state)
      new-state)))

(defn exit-enter-fn
  "A function that calls on-exit and on-enter if a state has changed."
  [state-map]
  (fn exit-enter
    [old-state new-state]
    (logging/debugf "state %s -> %s" old-state new-state)
    (when (not= old-state new-state)
      (when-let [on-exit (get-in state-map [old-state :on-exit])]
        (on-exit old-state))
      (when-let [on-enter (get-in state-map [new-state :on-enter])]
        (on-enter new-state)))))

;;; ## Transition functions

;; TODO add an :observable

(defmulti transition-fn
  "Return a function for making transitions, given a set of features to support.
   This multi-method allows other features to be added in an open fashion."
  (fn [features state-map] features))

(defmethod transition-fn #{}
  [_ state-map]
  (transition-to-state-fn state-map))

(defmethod transition-fn #{:on-enter-exit}
  [_ state-map]
  (let [transition-to-state (transition-to-state-fn state-map)
        exit-enter (exit-enter-fn state-map)]
    (fn transition [old-state new-state]
      (transition-to-state old-state new-state)
      (exit-enter old-state new-state)
      new-state)))

;;; ## FSM constructor

(defn fsm
  "Returns a Finite State Machine where the current state is managed by the
user.

state-map
: a map from state key to a set of valid transition states, or to a map with
  a :transitions key with a set of valid tranisition keys, and any keys required
  by the features in use.

features
: a set of features that the FSM should support. The sole provided feature
  is :on-enter-exit. Additional features and their combinations may be added by
  implementing methods on `transition-fn`.

Returns a map with :state!, :valid-state? and valid-transition? keys, providing
functions to change the state, test for a valid state and test for a valid
transition, respectively.

The :state! function takes an old state and a new state, and returns the new
state. An exception is thrown if the transition is invalid.

The :valid-state? predicate tests a state for validity.

The :valid-transition? predicate tests an old state, new state pair for
validity.

On enter and on exit functions
------------------------------

On enter and on exit functions are enabled by the :on-enter-exit feature.

The :on-enter and :on-exit keys on the state-map values should map to functions
of a state argument. These functions can be used to manage external state."
  [state-map features]
  (let [state-map (zipmap
                   (keys state-map)
                   (map
                    (fn normalise-map [v] (if (map? v) v {:transitions v}))
                    (vals state-map)))]
    {:transition (transition-fn (set features) state-map)
     :valid-state? (valid-state?-fn state-map)
     :valid-transition? (valid-transition?-fn state-map)
     ::features features}))
