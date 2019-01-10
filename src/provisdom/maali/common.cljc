(ns provisdom.maali.common
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators]
            [provisdom.maali.rules :as rules]
            [#?(:clj clojure.pprint :cljs cljs.pprint) :refer [pprint]]))

;;; Common rules for request/response logic.
(def rules
  {::cancel-request! {:doc "Cancellation is a special response that always causes the corresponding
                            request to be retracted. Note that the ::retract-orphan-response! rule
                            below will then cause the cancellation fact to also be retracted."
                      :query '[:find ?request
                               :where
                               [?request ::cancellable true]
                               [?response ::cancellation true]
                               [?response ::request ?request]]
                      :rhs-fn (fn [?request]
                                [[:db/retractEntity ?request]])}
   ::retract-orphan-response! {:doc "Responses are inserted unconditionally from outside the rule engine, so
                                     explicitly retract any responses without a corresponding request."
                               :query '[:find ?response
                                        :where
                                        [?response ::request ?request]
                                        (not [?request _ _])]
                               :rhs-fn (fn [?response]
                                         [[:db/retractEntity ?response]])}})

