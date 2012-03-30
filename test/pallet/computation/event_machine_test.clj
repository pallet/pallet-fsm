(ns pallet.computation.event-machine-test
  (:use
   clojure.test
   pallet.computation.event-machine
   [pallet.computation.stateful-fsm :only [stateful-fsm]]))

;; example from http://www.erlang.org/doc/design_principles/fsm.html

(defn locked-no-timeout [state event event-data]
  (case event
    :button
    (let [state (update-in state [:state-data :so-far]
                           str event-data)
          {:keys [code so-far]} (:state-data state)]
      (cond
        (= code so-far)
        (->
         state
         (assoc-in [:state-data :so-far] nil)
         (assoc :state-kw :open))

        (< (count so-far) (count code))
        state

        :else
        (assoc-in state [:state-data :so-far] nil)))))

(defn locked [state event event-data]
  (case event
    :button
    (let [state (update-in state [:state-data :so-far]
                           str event-data)
          {:keys [code so-far]} (:state-data state)]
      (cond
        (= code so-far)
        (->
         state
         (assoc-in [:state-data :so-far] nil)
         (assoc :state-kw :open)
         (assoc :timeout {:s 1}))

        (< (count so-far) (count code))
        state

        :else
        (assoc-in state [:state-data :so-far] nil)))))

(defn open [state event event-data]
  (case event
    :timeout (assoc state :state-kw :locked)
    :close (assoc state :state-kw :locked)
    :re-lock (assoc state :state-kw :re-locked)))

(deftest event-machine-test
  (testing "no features"
    (let [fsm (stateful-fsm {:state-kw :locked :state-data {:code "123"}}
                            {:locked #{:locked :open} :open #{:open :timed-out}}
                            nil)
          {:keys [event state reset] :as em}
          (event-machine fsm {:locked locked-no-timeout :open open} nil)]
      (is (= {:state-kw :locked
              :state-data {:code "123"}
              :em em :fsm fsm}
             (state)))
      (is (= {:state-kw :locked
              :state-data {:code "123" :so-far "1"}
              :em em :fsm fsm}
             (event :button 1)))
      (is (= {:state-kw :locked
              :state-data {:code "123" :so-far "12"}
              :em em :fsm fsm}
             (event :button 2)))
      (is (= {:state-kw :open
              :state-data {:code "123" :so-far nil}
              :em em :fsm fsm}
             (event :button 3)))))

  (testing "timeout"
    (let [fsm (stateful-fsm {:state-kw :locked :state-data {:code "123"}}
                            {:locked #{:locked :open} :open #{:open :timed-out}}
                            #{:timeout})
          {:keys [event state reset] :as em}
          (event-machine fsm {:locked locked :open open} nil)]
      (is (= {:state-kw :locked
              :state-data {:code "123"}
              :em em :fsm fsm}
             (state)))
      (is (= {:state-kw :locked
              :state-data {:code "123" :so-far "1"}
              :em em :fsm fsm}
             (event :button 1)))
      (is (= {:state-kw :locked
              :state-data {:code "123" :so-far "12"}
              :em em :fsm fsm}
             (event :button 2)))
      (is (= {:state-kw :open
              :state-data {:code "123" :so-far nil}
              :em em :fsm fsm}
             (event :button 3)))
      (testing "timeout"
        (Thread/sleep 2000)
        (is (= {:state-kw :timed-out
                :state-data {:code "123" :so-far nil}
                :em em :fsm fsm}
               (state))))))

  (testing "on-entry on-exit"
    (let [exit-locked (atom nil)
          enter-open (atom nil)
          fsm (stateful-fsm {:state-kw :locked :state-data {:code "1"}}
                            {:locked
                             {:transitions #{:locked :open}
                              :on-exit (fn [_] (reset! exit-locked true))}
                             :open
                             {:transitions #{:open :timed-out}
                              :on-enter (fn [_] (reset! enter-open true))}}
                            #{:timeout :on-enter-exit})
          {:keys [event state reset] :as em}
          (event-machine
           fsm
           {:locked {:event-fn locked}
            :open open}
           nil)]
      (is (= {:state-kw :locked :state-data {:code "1"} :em em :fsm fsm}
             (state)))
      (is (= {:state-kw :open
              :state-data {:code "1" :so-far nil} :em em :fsm fsm}
             (event :button 1)))
      (is @exit-locked)
      (is @enter-open))))


(deftest poll-event-machine-test
  (testing "door"
    (let [locked-counter (atom 0)
          open-counter (atom 0)
          fsm (stateful-fsm
               {:state-kw :locked :state-data {:code "123"}}
               {:locked {:transitions #{:locked :open}}
                :open {:transitions #{:open :timed-out :re-locked}}
                :re-locked nil}
               #{:timeout})
          state-map {:locked {:event-fn locked-no-timeout
                              :state-fn (fn [_] (swap! locked-counter inc))}
                     :open {:event-fn open
                            :state-fn (fn [_] (swap! open-counter inc))}
                     :relock {:event-fn (fn [state _ _] state)
                              :state-fn (fn [_])}}
          {:keys [event state reset] :as em} (event-machine fsm state-map nil)
          pem (poll-event-machine-fn state-map #{:re-locked})]
      (event :button 1)
      (pem em)
      (is (= 1 @locked-counter))
      (is (zero? @open-counter))
      (event :button 2)
      (pem em)
      (is (= 2 @locked-counter))
      (is (zero? @open-counter))
      (event :button 3)
      (pem em)
      (is (= 2 @locked-counter))
      (is (= 1 @open-counter))
      (event :re-lock nil)
      (is (= :re-locked (:state-kw (state))))
      (is (= 2 @locked-counter))
      (is (= 1 @open-counter)))))


(deftest event-machine-loop-test
  (testing "door"
    (let [locked-counter (atom 0)
          open-counter (atom 0)
          fsm (stateful-fsm
               {:state-kw :locked :state-data {:code "123"}}
               {:locked {:transitions #{:locked :open}}
                :open {:transitions #{:open :timed-out :re-locked}}}
               #{:timeout})
          state-map {:locked {:event-fn locked-no-timeout
                              :state-fn (fn [_] (swap! locked-counter inc))}
                     :open {:event-fn open
                            :state-fn (fn [_] (swap! open-counter inc))}
                     :relock {:event-fn (fn [state _ _] state)
                              :state-fn (fn [_])}}
          {:keys [event state reset] :as em} (event-machine fsm state-map nil)
          eml (event-machine-loop-fn state-map #{:re-locked})
          thread (Thread. #(eml em))]
      (.start thread)
      (event :button 1)
      (event :button 2)
      (event :button 3)
      (event :re-lock nil)
      (.join thread)
      (is (= :re-locked (:state-kw (state))))
      (is (pos? @locked-counter))
      (is (pos? @open-counter)))))
