(ns pallet.computation.fsm-dsl-test
  (:use
   clojure.test
   pallet.computation.fsm-dsl))

(defn a-fn [])

(deftest event-machine-config-test
  (testing "general usage"
    (is (= {:idle {:transitions #{:idle :refreshing}},
            :refreshing
            {:state-fn pallet.computation.fsm-dsl-test/a-fn,
             :event-fn pallet.computation.fsm-dsl-test/a-fn,
             :on-exit pallet.computation.fsm-dsl-test/a-fn,
             :on-enter pallet.computation.fsm-dsl-test/a-fn,
             :transitions #{:idle :refreshing}},
            :fsm/inital-state {:state-kw :fred},
            :fsm/fsm-features #{:on-enter-exit}}
           (event-machine-config
             (using-fsm-features :on-enter-exit)
             (initial-state :fred)
             (state :refreshing
               (valid-transitions :refreshing :idle)
               (on-enter a-fn)
               (on-exit a-fn)
               (event-handler a-fn)
               (state-driver a-fn))
             (state :idle
               (valid-transitions :refreshing :idle)))))
    (testing "inline definition"
      (let [c (event-machine-config
                (state :refreshing
                  (valid-transitions :refreshing :idle)
                  (on-enter-fn [state] [1 state])
                  (on-exit-fn [state] [2 state])
                  (event-handler-fn [state event data] [state event data])
                  (state-driver-fn [state] [3 state])))]
        (is (= [1 :a] ((-> c :refreshing :on-enter) :a)))
        (is (= [2 :b] ((-> c :refreshing :on-exit) :b)))
        (is (= [:c :d :e] ((-> c :refreshing :event-fn) :c :d :e)))
        (is (= [3 :d] ((-> c :refreshing :state-fn) :d)))))
    (testing "infers on-enter-exit"
      (is (= (event-machine-config
               (using-fsm-features :on-enter-exit)
               (state :refreshing
                 (valid-transitions :refreshing :idle)
                 (on-enter a-fn)))
             (event-machine-config
               (state :refreshing
                 (valid-transitions :refreshing :idle)
                 (on-enter a-fn)))))
      (is (= (event-machine-config
               (using-fsm-features :on-enter-exit)
               (state :refreshing
                 (valid-transitions :refreshing :idle)
                 (on-exit a-fn)))
             (event-machine-config
               (state :refreshing
                 (valid-transitions :refreshing :idle)
                 (on-exit a-fn))))))
    (testing "infers timeout"
      (is (= (event-machine-config
               (using-fsm-features :timeout)
               (state :timed-out
                 (valid-transitions :timed-out)))
             (event-machine-config
               (state :timed-out
                 (valid-transitions :timed-out))))))))
