(ns pallet.computation.fsm
  "A finite state machine lib"
  (:use
   [slingshot.slingshot :only [throw+]])
  (:require
   [clojure.tools.logging :as logging])
  (:import
   java.util.concurrent.Executors
   java.util.concurrent.TimeUnit))

(defonce timeout-sender (Executors/newSingleThreadScheduledExecutor))

(def time-units
  {:days TimeUnit/DAYS
   :hours TimeUnit/HOURS
   :us TimeUnit/MICROSECONDS
   :ms TimeUnit/MILLISECONDS
   :mins TimeUnit/MINUTES
   :ns TimeUnit/NANOSECONDS
   :s TimeUnit/SECONDS})

(defn- fsm-invalid-state
  [state event event-data]
  (throw+
   {:state state
    :event event
    :event-data event-data}
   "Invalid state"))

;;; ## utilities

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


;;; ## Implementation functions
(defn exit-enter
  "A function that calls on-exit and on-enter if a state has changed."
  [old-state-kw {:keys [state-kw] :as state} state-map]
  (logging/debugf "state %s -> %s" old-state-kw state-kw)
  (when (not= old-state-kw state-kw)
    (when-let [on-exit (get-in state-map [old-state-kw :on-exit])]
      (on-exit state))
    (when-let [on-enter (get-in state-map [state-kw :on-enter])]
      (on-enter state))))

(defn call-event-fn
  "A function that calls the event function for the state"
  [state-map]
  (fn [state event data]
    ((get-in state-map [(:state-kw state) :event-fn] fsm-invalid-state)
     state event data)))

(defn schedule-timeout
  [state timeout event-fn]
  (when timeout
    (logging/debugf "fsm timeout for %s in %s" (:state-kw @state) timeout)
    (let [[unit value] (first timeout)]
      (.schedule
       timeout-sender #(event-fn state :timeout nil) value (time-units unit)))))

;;; ## Event functions
(defmulti fire-event-fn
  "Return a function for firing events, given a set of features to support.
   This multi-method allows other features to be added in an open fashion."
  (fn [features state-map] features))

(defmethod fire-event-fn #{}
  [_ state-map]
  (let [call-event (call-event-fn state-map)]
    (fn fire-event [state event data]
      (swap! state (fn [state] (call-event state event data))))))

(defmethod fire-event-fn #{:on-enter-exit}
  [_ state-map]
  (let [call-event (call-event-fn state-map)]
    (fn fire-event [state event data]
      (let [[old-state new-state]
            (swap!! state (fn [state] (call-event state event data)))]
        (exit-enter (:state-kw old-state) new-state state-map)
        new-state))))

(defmethod fire-event-fn #{:timeout}
  [_ state-map]
  (let [call-event (call-event-fn state-map)]
    (fn fire-event [state event data]
      (let [timeout-atom (atom nil)
            new-state (swap!
                       state
                       (fn [state]
                         (let [new-state (call-event state event data)]
                           (reset! timeout-atom (:timeout new-state))
                           (dissoc new-state :timeout))))]
        (schedule-timeout state @timeout-atom fire-event)
        new-state))))

(defmethod fire-event-fn #{:on-enter-exit :timeout}
  [_ state-map]
  (let [call-event (call-event-fn state-map)]
    (fn fire-event [state event data]
      (let [timeout-atom (atom nil)
            [old-state {:keys [state-kw] :as new-state}]
            (swap!! state (fn [state]
                            (let [new-state (call-event state event data)]
                              (reset! timeout-atom (:timeout new-state))
                              (dissoc new-state :timeout))))]
        (exit-enter (:state-kw old-state) new-state state-map)
        (schedule-timeout state @timeout-atom fire-event)
        new-state))))

;;; ## FSM constructor
(defn fsm
  "Returns a Finite State Machine.

state
: a map of initial state

state-map
: a map from state key to an event transition function or to a map with
  an :event-fn key. Other keys on the state-map values are used by features.

features
: a set of features that the FSM should support. Provided features are :timeout
  and :on-enter-exit. Additional features and their combinations may be
  added by implementing methods on `fire-event-fn`.

Returns a map with :event, :state and :reset keys, providing functions to send
an event, query the state and reset the state, respectively.

state-map transition functions should take the current state vector, and event,
and user data.  Functions should return the new state as a map with keys
:status :state-kw and :state-data.

The :state-data value should be nil or a map. The final fsm map (as returned by
this function) will be set on the :fsm key of the the state, so that state
functions can refer to the fsm itself.

Timeouts
--------

Timeouts are enabled by the :timeout feature.

If the state map returned by an event function contains a :timeout key, with a
value specified as a map from unit to duration, then a :timeout event will be
sent on elapse of the specified duration.

On enter and on exit functions
------------------------------

On enter and on exit functions are enabled by the :on-enter-exit feature.

The :on-enter and :on-exit keys on the state-map values should map to functions
of a state argument. These functions can be used to manage external state. We
have to call them outside of a swap!  so that they are guaranteed to be called
exactly once, and therefore they can not (functionally) update the fsm's
state-data."
  [{:keys [status state-kw state-data] :or {status :ok}} state-map features]
  (assert state-kw "Must supply initial state keyword")
  (assert (state-map state-kw) "Initial state must be in state-map")
  (let [state-map (zipmap
                   (keys state-map)
                   (map
                    (fn [v] (if (map? v) v {:event-fn v}))
                    (vals state-map)))
        state (atom {:status status :state-kw state-kw :state-data state-data})]
    (let [fire-event (fire-event-fn (set features) state-map)]
      (letfn [(fire [event data]
                (logging/debugf
                 "in state %s fire %s %s"
                 (:state-kw @state) event data)
                (fire-event state event data))]
        (let [machine {:event fire
                       :state (fn [] @state)
                       :reset (fn [{:keys [status state-kw state-data]
                                    :as new-state}]
                                (swap! state merge new-state))}]
          (swap! state assoc :fsm machine)
          machine)))))
