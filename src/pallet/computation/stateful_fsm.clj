(ns pallet.computation.stateful-fsm
  "A finite state machine with managed state"
  (:use
   [pallet.computation.fsm :only [fsm]]
   [pallet.computation.fsm-utils :only [swap!!]]
   [slingshot.slingshot :only [throw+]])
  (:require
   [clojure.tools.logging :as logging])
  (:import
   java.util.concurrent.Executors
   java.util.concurrent.TimeUnit))

(defonce timeout-sender (Executors/newScheduledThreadPool 1))

(def time-units
  {:days TimeUnit/DAYS
   :hours TimeUnit/HOURS
   :us TimeUnit/MICROSECONDS
   :ms TimeUnit/MILLISECONDS
   :mins TimeUnit/MINUTES
   :ns TimeUnit/NANOSECONDS
   :s TimeUnit/SECONDS})

;;; ## Implementation functions
(defn valid-state?-fn
  [{:keys [valid-state?] :as fsm}]
  valid-state?)

(defn valid-transition?-fn
  [fsm state]
  (let [v-t? (:valid-transition? fsm)]
    (fn [to-state]
      (v-t? (:state-kw @state) to-state))))

(defn schedule-timeout
  [state timeout transition-fn]
  (when timeout
    (logging/debugf "fsm timeout for %s in %s" (:state-kw state) timeout)
    (let [[unit value] (first timeout)]
      (.schedule
       timeout-sender
       (fn [] (transition-fn #(assoc % :state-kw :timed-out)))
       value
       (time-units unit)))))

;;; ## Transition functions
;; TODO add an :observable

(defmulti transition-fn
  "Return a function for making transitions, given a set of features to support.
   This multi-method allows other features to be added in an open fashion."
  (fn [features state-map state fsm] features))

(defmethod transition-fn #{}
  [_
   state-map
   state
   {:keys [transition valid-transition?] :as fsm}]
  (let [state-updater (:fsm/state-updater state-map identity)]
    (fn [new-state-fn]
      (let [[old-state new-state]
            (swap!!
             state
             (fn [state] (-> state new-state-fn state-updater)))]
        (transition (:state-kw old-state) (:state-kw new-state))
        new-state))))

(defmethod transition-fn #{:timeout}
  [features
   state-map
   state
   {:keys [transition valid-transition?] :as fsm}]
  (let [state-updater (:fsm/state-updater state-map identity)]
    (fn [new-state-fn]
      (let [new-timeout (atom nil)
            timeout-spec (atom nil)
            [old-state new-state]
            (swap!!
             state
             (fn [state]
               (let [{:keys [timeout] :as new-state}
                     (-> (dissoc state :timeout-f) new-state-fn state-updater)]
                 (when-let [old-timeout (and (:timeout-f state)
                                             @(:timeout-f state))]
                   (.cancel old-timeout))
                 (if timeout
                   (do
                     (reset! timeout-spec timeout)
                     (-> new-state
                         (assoc :timeout-f new-timeout)
                         (dissoc :timeout)))
                   new-state))))]
        (transition (:state-kw old-state) (:state-kw new-state))
        (schedule-timeout
         new-state @timeout-spec
         (transition-fn features state-map state fsm ))
        (dissoc new-state :timeout-f)))))

(defn stateful-fsm
  "Returns a Finite State Machine where the state of the machine is managed.

state-map
: a map from state key to a set of valid transition states, or a map with
  a :transitions key with a set of valid tranisition keys, and any keys required
  by the features in use.

features
: a set of features that the FSM should support. The sole provided feature
  is :on-enter-exit. Additional features and their combinations may be added by
  implementing methods on `transition-fn`.

Returns a map with :transition, :valid-state? and valid-transition? keys,
providing functions to change the state, test for a valid state and test for a
valid transition, respectively. The transition function takes a function of
state, that should return a new state.

On enter and on exit functions
------------------------------

On enter and on exit functions are enabled by the :on-enter-exit feature.

The :on-enter and :on-exit keys on the state-map values should map to functions
of a state argument. These functions can be used to manage external state.

Timeouts
--------

Timeouts are enabled by the :timeout feature.

If the state map returned by an event function contains a :timeout key, with a
value specified as a map from unit to duration, then a :timeout event will be
sent on elapse of the specified duration."
  [{:keys [state-kw state-data] :as state} state-map features]
  (let [fsm (fsm state-map (disj (set features) :timeout))
        state (atom state)]
    {:transition (transition-fn
                  (disj (set features) :on-enter-exit) state-map state fsm)
     :valid-state? (valid-state?-fn fsm)
     :valid-transition? (valid-transition?-fn fsm state)
     :state (fn [] (dissoc @state :timeout-f))
     :reset (fn [{:keys [status state-kw state-data]
                  :as new-state}]
              (swap! state merge new-state))}))
