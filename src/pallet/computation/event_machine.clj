(ns pallet.computation.event-machine
  "An event machine dispatches events to updates in an underlying finite state
  machine."
  (:use
   [pallet.computation.fsm :only [fsm]]
   [slingshot.slingshot :only [throw+]])
  (:require
   [clojure.tools.logging :as logging]))

;;; ## Implementation functions
(defn- fsm-invalid-state
  [state event event-data]
  (throw+
   {:state state
    :event event
    :event-data event-data}
   "No event function for state"))

(defn call-event-fn
  "A function that calls the event function for the state."
  [state-map {:keys [transition] :as fsm}]
  (fn [event data]
    (transition
     #(do
        (logging/debugf "in state %s fire %s %s" (:state-kw %) event data)
        ((get-in state-map [(:state-kw %) :event-fn] fsm-invalid-state)
         % event data)))))

;;; ## Event functions
(defmulti fire-event-fn
  "Return a function for firing events, given a set of features to support.
   This multi-method allows other features to be added in an open fashion."
  (fn [features state-map fsm] features))

(defmethod fire-event-fn #{}
  [_ state-map fsm]
  (call-event-fn state-map fsm))

(defn event-machine
  "Returns an Event Machine, which dispatches events to state transitions
in a FSM. This adds event dispatch based on arbitrary functions.

fsm
: a fsm to dispatch to

state-map
: a map from state key to an event transition function or to a map with
  an :event-fn key. Other keys on the state-map values are used by features.

features
: a set of features that the event machine should support. None yet implemented.

Returns a map with :event, :state and :reset keys, providing functions to send
an event, query the state and reset the state, respectively.

state-map transition functions should take the current state vector, and event,
and user data.  Functions should return the new state as a map with keys
:status :state-kw and :state-data."
  [fsm state-map features]
  (let [state-map (zipmap
                   (keys state-map)
                   (map
                    (fn [v] (if (map? v) v {:event-fn v}))
                    (vals state-map)))]
    (let [fire (fire-event-fn (set features) state-map fsm)]
      (let [machine (merge
                     (select-keys
                      fsm [:state :reset :valid-state? :valid-transition?])
                     {:event fire})]
        ((:reset fsm) (assoc ((:state fsm)) :fsm fsm :em machine))
        machine))))

(defn poll-event-machine-fn
  "Returns a function to run the state-map's :state-fn's once against the
current state of an event-machine."
  [state-map terminal-state?]
  (fn poll-event-machine [{:keys [state] :as event-machine}]
    (let [{:keys [state-kw state-data] :as in-state} (state)]
      (if (terminal-state? state-kw)
        in-state
        (let [state-fn (get-in state-map [state-kw :state-fn])]
          (when-not state-fn
            (throw+
             {:state in-state
              :state-map state-map
              :reason :no-state-fn}
             "EM %sin state %s, but no :state-fn available"
             (when-let [em-name (::name state-map)] (str em-name " "))
             state-kw))
          (state-fn in-state)
          nil)))))

(defn event-machine-loop-fn
  "Returns a function to continuously run the state-map's :state-fn's against
the current state of an event-machine, until a terminal-state is reached."
  [state-map terminal-state?]
  (fn event-machine-loop [{:keys [state] :as event-machine}]
    (loop [in-state (state)]
      (let [{:keys [state-kw state-data]} in-state]
        (if (terminal-state? state-kw)
          in-state
          (let [state-fn (get-in state-map [state-kw :state-fn])]
            (when-not state-fn
              (throw+
               {:state in-state
                :state-map state-map
                :reason :no-state-fn}
               "EM %sin state %s, but no :state-fn available"
               (when-let [em-name (::name state-map)] (str em-name " "))
               state-kw))
            (state-fn in-state)
            (recur (state))))))))
