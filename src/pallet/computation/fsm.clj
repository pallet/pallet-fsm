(ns pallet.computation.fsm
  "A finite state machine lib"
  (:use
   [slingshot.slingshot :only [throw+]])
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

state     - a map of initial state
state-map - a map from state key to transition function

state-map transition functions should take the current state vector, and event,
and user data.  Functions should return the new state as a map with keys
:status :state-kw and :state-data."
  [{:keys [status state-kw state-data] :or {status :ok}} state-map]
  (assert state-kw "Must supply initial state keyword")
  (assert (state-map state-kw) "Initial state must be in state-map")
  (let [state (atom {:status status :state-kw state-kw :state-data state-data})]
    {:event (letfn [(fire [event data]
                      (let [{:keys [timeout] :as new-state}
                            (swap!
                             state
                             #((get state-map (:state-kw %) fsm-invalid-state)
                               % event data))]
                        (if timeout
                          (let [[unit value] (first timeout)]
                            (.schedule timeout-sender
                                       #(fire :timeout nil)
                                       value
                                       (time-units unit))
                            (swap! state dissoc :timeout))
                          new-state)))]
              fire)
     :state (fn []
              @state)
     :reset (fn [{:keys [status state-kw state-data] :as new-state}]
              (reset! state new-state))}))
