(ns pallet.algo.fsm.stateful-fsm
  "A finite state machine with managed state"
  (:require
   [pallet.algo.fsm.fsm :refer [fsm]]
   [pallet.algo.fsm.fsm-utils :refer [swap!!]]
   [pallet.thread.executor :refer [executor execute-after]]
   [clojure.tools.logging :as logging]))

;;; ## Base FSM transitions
(defn- valid-state?-fn
  [{:keys [valid-state?] :as fsm}]
  valid-state?)

(defn- valid-transition?-fn
  [fsm state]
  (let [v-t? (:valid-transition? fsm)]
    (fn [to-state]
      (v-t? @state to-state))))

(defn- base-transition
  "Final handler for transitions."
  [{:keys [transition] :as fsm}]
  (if-let [lock-object (-> fsm :fsm :pallet.algo.fsm.fsm/lock)]
    (fn base-transition [state update-middleware]
      {:pre [(instance? clojure.lang.Atom state)]}
      (let [update-fn (reduce #(%2 %1) update-middleware)]
        (locking lock-object
          (let [[old-state new-state] (swap!! state update-fn)]
            (transition old-state new-state)
            new-state))))
    (fn base-transition [state update-middleware]
      {:pre [(instance? clojure.lang.Atom state)]}
      (let [update-fn (reduce #(%2 %1) update-middleware)
            [old-state new-state] (swap!! state update-fn)]
        (transition old-state new-state)
        new-state))))

;;; ## Transitions with timeouts
(defonce
  ^{:defonce true :private true
    :doc "A scheduled executor service for implementing notification of
         timeouts."}
  timeout-sender
  (executor
   {:scheduled true :pool-size 3 :prefix "fsm" :thread-group-name "fsm"}))

(defn- schedule-timeout
  [state timeout transition-fn]
  (when timeout
    (logging/debugf "fsm timeout for %s in %s" (:state-kw state) timeout)
    (let [[unit value] (first timeout)]
      (execute-after
       timeout-sender
       (partial transition-fn state [#(assoc % :state-kw :timed-out)])
       value
       unit))))

(defn with-transition-timeout
  "Middleware for adding timeout's across every transition."
  [state-map fsm]
  (let [transition (base-transition fsm)]
    (fn [handler]
      (fn with-transition-timeout [state update-middleware]
        {:pre [(instance? clojure.lang.Atom state)]}
        (let [new-timeout (atom nil)
              timeout-spec (atom nil)

              update-fn
              (fn [handler]
                (fn timeout-update-fn
                  [state]
                  (let [{:keys [timeout] :as new-state} (dissoc
                                                         (handler state)
                                                         :timeout-f)]
                    (when-let [old-timeout (and (:timeout-f state)
                                                @(:timeout-f state))]
                      (logging/debugf
                       "%scanceling timeout"
                       (if-let [n (:fsm/name state-map)] (str n " - ") ""))
                      (future-cancel old-timeout))
                    (if timeout
                      (do
                        (reset! timeout-spec timeout)
                        (-> new-state
                            (assoc :timeout-f new-timeout)
                            (dissoc :timeout)))
                      new-state))))]
          (let [new-state (handler state (conj update-middleware update-fn))]
            (reset! new-timeout
                    (schedule-timeout state @timeout-spec transition))
            (dissoc new-state :timeout-f)))))))

;;; ## History
(defn with-history
  "Middleware for adding transition history to the state."
  [state-map fsm]
  (let [fsm-name (:fsm/name state-map)]
    (fn [handler]
      (fn with-histroy [state update-middleware]
        (let [history-update-fn
              (fn [handler]
                (fn history-update-fn
                  [state]
                  (let [new-state (handler state)]
                    (update-in
                     new-state [:history]
                     conj (-> state
                              (dissoc :history :fsm :em)
                              ((fn [state]
                                 (if fsm-name
                                   (assoc state :fsm/name fsm-name)
                                   state))))))))]
          (handler state (conj update-middleware history-update-fn)))))))

;;; ## Feature middleware processing
(def ^{:private true
       :doc "Provides look-up from feature keyword to implementation function."}
  builtin-middleware
  {:timeout with-transition-timeout
   :history with-history})

(defn- transition-fn
  "Returns a transition function that implements the specified features."
  [features state-map state fsm]
  (let [transition-fn (reduce
                       #(%2 %1)
                       (base-transition fsm)
                       (->> features
                            (map #(builtin-middleware %1 %1))
                            (map #(% state-map fsm))))]
    (fn [update-fn]
      (transition-fn state [update-fn]))))

;;; ## Stateful FSM
(defn stateful-fsm
  "Returns a Finite State Machine where the state of the machine is managed.

state-map
: a map from state key to a set of valid transition states, or a map with
  a :transitions key with a set of valid tranisition keys, and any keys required
  by the features in use.

features
: a set of features that the FSM should support. The provided features
  are :timeout and :history. Additional features may be added by passing
  middleware functions. middleware functions are functions of the underling fsm,
  that return a function taking a handler, which in return takes a function of
  the state atom and a list of state update middleware functions. The function
  returned by a state middleware function is called within the context of a
  `swap!` operation on the old and new values of the fsm state.

Returns a map with :transition, :valid-state? and valid-transition? keys,
providing functions to change the state, test for a valid state and test for a
valid transition, respectively. The transition function takes a function of
state, that should return a new state.

Timeouts
--------

Timeouts are enabled by the :timeout feature.

If the state map returned by an event function contains a :timeout key, with a
value specified as a map from unit to duration, then a :timeout event will be
sent on elapse of the specified duration.

History
-------

History is enabled by the :history feature, which records states on the
state's :history key."
  ([{:keys [state-kw state-data] :as state} state-map fsm-features features]
     (let [fsm (fsm
                (assoc state-map :fsm/fsm-state-identity :state-kw)
                fsm-features)
           state (atom state)]
       {:transition (transition-fn features state-map state fsm)
        :valid-state? (valid-state?-fn fsm)
        :valid-transition? (valid-transition?-fn fsm state)
        :state (fn [] (dissoc @state :timeout-f))
        :reset (fn [{:keys [status state-kw state-data]
                     :as new-state}]
                 (swap! state merge new-state))
        :fsm fsm}))
  ([config]
     (stateful-fsm
      (:fsm/inital-state config)
      (dissoc config
              :fsm/fsm-features :fsm/event-machine-features :fsm/inital-state)
      (:fsm/fsm-features config)
      (:fsm/stateful-fsm-features config))))
