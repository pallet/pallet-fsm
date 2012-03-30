(ns pallet.computation.fsm-test
  (:use
   clojure.test
   pallet.computation.fsm))

(deftest fsm-test
  (testing "no features"
    (let [{:keys [transition valid-state? valid-transition?] :as sm}
          (fsm {:locked #{:open} :open #{:locked}} nil)]
      (is (valid-state? :locked) "recognises valid states")
      (is (valid-state? :open) "recognises valid states")
      (is (not (valid-state? :broken)) "recognises invalid states")

      (is (valid-transition? :locked :open) "recognises valid transitions")
      (is (valid-transition? :open :locked) "recognises valid transitions")
      (is (not (valid-transition? :open :open))
          "recognises invalid transitions")

      (is (= :open (transition :locked :open)))
      (is (= :locked (transition :open :locked)))))

  (testing "on-entry on-exit"
    (let [exit-locked (atom nil)
          enter-open (atom nil)
          {:keys [transition valid-state? valid-transition?] :as sm}
          (fsm {:locked {:transitions #{:open}
                         :on-exit (fn [_] (reset! exit-locked true))}
                :open {:transitions #{:locked}
                       :on-enter (fn [_] (reset! enter-open true))}}
               #{:on-enter-exit})]

      (is (valid-state? :locked) "recognises valid states")
      (is (valid-state? :open) "recognises valid states")
      (is (not (valid-state? :broken)) "recognises invalid states")

      (is (valid-transition? :locked :open) "recognises valid transitions")
      (is (valid-transition? :open :locked) "recognises valid transitions")
      (is (not (valid-transition? :open :open))
          "recognises invalid transitions")

      (is (= :open (transition :locked :open)))
      (is @exit-locked ":on-exit called")
      (is @enter-open ":on-enter called")
      (is (= :locked (transition :open :locked))))))
