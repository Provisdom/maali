(ns provisdom.eala-dubh.todo.rules
  (:require [clojure.spec.alpha :as s]
    #?(:clj [provisdom.eala-dubh.rules :refer [defrules defqueries] :as rules]
       :cljs [provisdom.eala-dubh.rules :refer-macros [defrules defqueries] :as rules])
            [clara.rules.accumulators :as acc]))

;;; Fact specs. Use convention that specs for fact "types" are camel-cased.
(s/def ::id int?)
(s/def ::title string?)
(s/def ::edit boolean?)
(s/def ::done boolean?)
(s/def ::Todo (s/keys :req [::id ::title ::edit ::done]))

(s/def ::visibility #{:all :active :completed})
(s/def ::Visibility (s/keys :req [::visibility]))

(s/def ::count (s/or :zero zero? :pos pos-int?))
(s/def ::Active (s/keys :req [::count]))
(s/def ::Completed (s/keys :req [::count]))

(s/def ::all-completed boolean?)
(s/def ::All-Completed (s/keys :req [::all-completed]))

(s/def ::show-clear boolean?)
(s/def ::Show-Clear (s/keys :req [::show-clear]))

;;; Convenience function to create new ::Todo facts
(def next-id (atom 0))

(defn new-todo
  [title]
  #::{:id (swap! next-id inc) :title title :done false :edit false})

;;; Rules
(defrules rules
  [::active-count!
   [?count <- (acc/count) :from [::Todo (= done false)]]
   =>
   (rules/insert! ::Active {::count ?count})
   (rules/insert! ::All-Completed {::all-completed (= 0 ?count)})]

  [::completed-count!
   [?count <- (acc/count) :from [::Todo (= done true)]]
   =>
   (rules/insert! ::Completed {::count ?count})
   (rules/insert! ::Show-Clear {::show-clear (not= 0 ?count)})]
  )

;;; Queries
(defqueries queries
  [::visible-todos []
   [::Visibility (= ?visibility visibility)]
   [?todo <- ::Todo (condp = ?visibility
                      :active (= done false)
                      :completed (= done true)
                      :all true)]]

  [::completed-todos []
   [?todo <- ::Todo (= done true)]]

  [::active-todos []
   [?todo <- ::Todo (= done false)]]

  [::todo-by-id [:?id]
   [?todo <- ::Todo (= ?id id)]]

  [::active-count []
   [::Active (= ?count count)]]

  [::completed-count []
   [::Completed (= ?count count)]]

  [::visibility []
   [?visibility <- ::Visibility]]

  [::all-completed []
   [::All-Completed (= ?all-completed all-completed)]]

  [::show-clear []
   [::Show-Clear (= ?show-clear show-clear)]])