(ns provisdom.todo.rules
  (:require [clojure.spec.alpha :as s]
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries] :as rules]
            [provisdom.todo.view :as view]
            [clara.rules.accumulators :as acc]))

;;; Rules
(defrules rules
  [::active-count!
   [?count <- (acc/count) :from [::specs/Todo (= done false)]]
   =>
   (view/update-view :active-count ?count)
   (view/update-view :all-completed (= 0 ?count))]

  [::completed-count!
   [?count <- (acc/count) :from [::specs/Todo (= done true)]]
   =>
   (view/update-view :completed-count ?count)
   (view/update-view :show-clear (not= 0 ?count))]

  [::visible-todos!
   [::specs/Visibility (= ?visibility visibility)]
   [?todos <- (acc/all) :from [::specs/Todo (condp = ?visibility
                                              :active (= done false)
                                              :completed (= done true)
                                              :all true)]]
   =>
   (view/update-view :todo-list (sort-by ::specs/id ?todos))]

  [::visibility!
   [::specs/Visibility (= ?visibility visibility)]
   =>
   (view/update-view :visibility ?visibility)])

;;; Queries
(defqueries queries
  [::completed-todos [] [?todo <- ::specs/Todo (= done true)]]

  [::todo-by-id [:?id] [?todo <- ::specs/Todo (= ?id id)]]

  [::visibility [] [?visibility <- ::specs/Visibility]])
