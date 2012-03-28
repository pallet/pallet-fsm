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

(defn fsm
  "Returns a Finite State Machine.

state
: a map of initial state

state-map
: a map from state key to transition function or to a map
  with :fn, :on-enter, and :on-exit keys.

state-map transition functions should take the current state vector, and event,
and user data.  Functions should return the new state as a map with keys
:status :state-kw and :state-data.

If the returned state map contains a :timeout key, with a value specified as a
map from unit to duration, then a :timeout event will be sent on elapse of the
specified duration.

:on-enter and :on-exit should map to functions of a state argument. These
functions can be used to manage external state. We have to call them outside of
a swap!  so that they are guaranteed to be called exactly once, and therefore
they can not (functionally) update the fsm's state-data."
  [{:keys [status state-kw state-data] :or {status :ok}} state-map]
  (assert state-kw "Must supply initial state keyword")
  (assert (state-map state-kw) "Initial state must be in state-map")
  (let [state-map (zipmap
                   (keys state-map)
                   (map (fn [v] (if (map? v) v {:fn v})) (vals state-map)))
        state (atom {:status status :state-kw state-kw :state-data state-data})]
    (letfn [(exit-enter [old-state-kw state-kw state]
              (logging/debugf "state %s -> %s" old-state-kw state-kw)
              (when (not= old-state-kw state-kw)
                (when-let [on-exit (get-in state-map [old-state-kw :on-exit])]
                  (on-exit state))
                (when-let [on-enter (get-in state-map [state-kw :on-enter])]
                  (on-enter state))))
            (fire [event data]
              (logging/debugf
               "in state %s fire %s %s"
               (:state-kw @state) event data)
              (let [old-state (atom nil)
                    timeout-atom (atom nil)

                    {:keys [timeout state-kw] :as new-state}
                    (swap!
                     state
                     (fn [state]
                       (reset! old-state state)
                       (let [new-state
                             ((get-in state-map [(:state-kw state) :fn]
                                      fsm-invalid-state)
                              state event data)]
                         (reset! timeout-atom (:timeout new-state))
                         (dissoc new-state :timeout))))

                    old-state-kw (:state-kw @old-state)]
                (exit-enter old-state-kw state-kw new-state)
                (when-let [timeout @timeout-atom]
                  (logging/debugf "fsm timeout for %s in %s" new-state timeout)
                  (let [[unit value] (first timeout)]
                    (.schedule timeout-sender
                               #(fire :timeout nil)
                               value
                               (time-units unit))))
                new-state))]
      {:event fire
       :state (fn []
                @state)
       :reset (fn [{:keys [status state-kw state-data] :as new-state}]
                (reset! state new-state))})))
