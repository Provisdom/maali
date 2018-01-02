(ns provisdom.eala-dubh.todo.rules
  (:require [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.todo.specs :as specs]
    #?(:clj [provisdom.eala-dubh.rules :refer [defrules defqueries] :as rules]
       :cljs [provisdom.eala-dubh.rules :refer-macros [defrules defqueries] :as rules])
            [clara.rules.accumulators :as acc]))

;;; Convenience function to create new ::Todo facts
(def next-id (atom 0))

(defn new-todo
  [title]
  #::specs{:id (swap! next-id inc) :title title :done false :edit false})

;;; Rules
(defrules rules
  [::active-count!
   [?count <- (acc/count) :from [::specs/Todo (= done false)]]
   =>
   (rules/insert! ::specs/Active {::specs/count ?count})
   (rules/insert! ::specs/All-Completed {::specs/all-completed (= 0 ?count)})]

  [::completed-count!
   [?count <- (acc/count) :from [::specs/Todo (= done true)]]
   =>
   (rules/insert! ::specs/Completed {::specs/count ?count})
   (rules/insert! ::specs/Show-Clear {::specs/show-clear (not= 0 ?count)})]
  )

;;; Queries
(defqueries queries
  [::visible-todos []
   [::specs/Visibility (= ?visibility visibility)]
   [?todo <- ::specs/Todo (condp = ?visibility
                      :active (= done false)
                      :completed (= done true)
                      :all true)]]

  [::completed-todos [] [?todo <- ::specs/Todo (= done true)]]

  [::active-todos [] [?todo <- ::specs/Todo (= done false)]]

  [::todo-by-id [:?id] [?todo <- ::specs/Todo (= ?id id)]]

  [::active-count [] [::specs/Active (= ?count count)]]

  [::completed-count [] [::specs/Completed (= ?count count)]]

  [::visibility [] [?visibility <- ::specs/Visibility]]

  [::all-completed [] [::specs/All-Completed (= ?all-completed all-completed)]]

  [::show-clear [] [::specs/Show-Clear (= ?show-clear show-clear)]])