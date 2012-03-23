(ns pallet.computation.fsm-test
  (:use
   clojure.test
   pallet.computation.fsm))

;; example from http://www.erlang.org/doc/design_principles/fsm.html

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
    :close (assoc state :state-kw :locked)))

(deftest fsm-test
  (testing "basic transitions"
    (let [{:keys [event state reset]}
          (fsm {:state-kw :locked :state-data {:code "123"}}
               {:locked locked :open open})]
      (is (= {:status :ok :state-kw :locked :state-data {:code "123"}}
             (state)))
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123" :so-far "1"}}
             (event :button 1)))
      (is (= {:status :ok :state-kw :locked
              :state-data {:code "123" :so-far "12"}}
             (event :button 2)))
      (is (= {:status :ok :state-kw :open
              :state-data {:code "123" :so-far nil}}
             (event :button 3)))
      (testing "timeout"
        (Thread/sleep 2000)
        (is (= {:status :ok :state-kw :locked
                :state-data {:code "123" :so-far nil}}
               (state))))))
  (testing "entry exit"
    (let [exit-locked (atom nil)
          enter-open (atom nil)
          {:keys [event state reset]}
          (fsm {:state-kw :locked :state-data {:code "1"}}
               {:locked {:fn locked :on-exit #(reset! exit-locked true)}
                :open {:fn open :on-enter #(reset! enter-open true)}})]
      (is (= {:status :ok :state-kw :locked :state-data {:code "1"}}
             (state)))
      (is (= {:status :ok :state-kw :open
              :state-data {:code "1" :so-far nil}}
             (event :button 1)))
      (is @exit-locked)
      (is @enter-open))))
