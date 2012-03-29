(ns pallet.computation.fsm-test
  (:use
   clojure.test
   pallet.computation.fsm))

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

(deftest fsm-test
  (testing "no features"
    (let [{:keys [event state reset] :as sm}
          (fsm {:state-kw :locked :state-data {:code "123"}}
               {:locked locked-no-timeout :open open}
               nil)]
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123"}
              :fsm sm}
             (state)))
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123" :so-far "1"}
              :fsm sm}
             (event :button 1)))
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123" :so-far "12"}
              :fsm sm}
             (event :button 2)))
      (is (= {:status :ok :state-kw :open
              :state-data {:code "123" :so-far nil}
              :fsm sm}
             (event :button 3)))))

  (testing "timeout"
    (let [{:keys [event state reset] :as sm}
          (fsm {:state-kw :locked :state-data {:code "123"}}
               {:locked locked :open open}
               #{:timeout})]
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123"}
              :fsm sm}
             (state)))
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123" :so-far "1"}
              :fsm sm}
             (event :button 1)))
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123" :so-far "12"}
              :fsm sm}
             (event :button 2)))
      (is (= {:status :ok :state-kw :open
              :state-data {:code "123" :so-far nil}
              :fsm sm}
             (event :button 3)))
      (testing "timeout"
        (Thread/sleep 2000)
        (is (= {:status :ok :state-kw :locked
                :state-data {:code "123" :so-far nil}
                :fsm sm}
               (state))))))

  (testing "on-entry on-exit"
    (let [exit-locked (atom nil)
          enter-open (atom nil)
          {:keys [event state reset] :as sm}
          (fsm {:state-kw :locked :state-data {:code "1"}}
               {:locked {:event-fn locked
                         :on-exit (fn [_] (reset! exit-locked true))}
                :open {:event-fn open
                       :on-enter (fn [_] (reset! enter-open true))}}
               #{:timeout :on-enter-exit})]
      (is (= {:status :ok :state-kw :locked :state-data {:code "1"} :fsm sm}
             (state)))
      (is (= {:status :ok :state-kw :open
              :state-data {:code "1" :so-far nil} :fsm sm}
             (event :button 1)))
      (is @exit-locked)
      (is @enter-open))))

(deftest fsm-exec-test
  (testing "door"
    (let [locked-counter (atom 0)
          open-counter (atom 0)
          state-map {:locked {:event-fn locked-no-timeout
                              :state-fn (fn [_] (swap! locked-counter inc))}
                     :open {:event-fn open
                            :state-fn (fn [_] (swap! open-counter inc))}
                     :relock {:event-fn (fn [state _ _] state)
                              :state-fn (fn [_])}}
          {:keys [event state reset] :as sm}
          (fsm {:state-kw :locked :state-data {:code "123"}}
               state-map
               nil)
          fsm-exec (fsm-exec-fn state-map #{:re-locked})
          thread (Thread. #(fsm-exec sm))]
      (.start thread)
      (event :button 1)
      (event :button 2)
      (event :button 3)
      (event :re-lock nil)
      (.join thread)
      (is (= :re-locked (:state-kw (state))))
      (is (pos? @locked-counter))
      (is (pos? @open-counter)))))
