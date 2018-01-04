(ns provisdom.eala-dubh.todo.rules
  (:require [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.todo.specs :as specs]
            [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries] :as rules]
            [clara.rules.accumulators :as acc]))

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

;;; Query result specs
(s/def ::?id ::specs/id)
(s/def ::?todo ::specs/Todo)
(s/def ::?visibility ::specs/Visibility)
(s/def ::?count ::specs/count)
(s/def ::?all-completed ::specs/all-completed)
(s/def ::?show-clear ::specs/show-clear)
(s/def ::todo-by-id (s/cat :query #{::todo-by-id} :result (s/coll-of (s/keys :req-un [::?id ::?todo]))))
(s/def ::completed-todos (s/cat :query #{::completed-todos} :result (s/coll-of (s/keys :req-un [::?todo]))))
(s/def ::active-todos (s/cat :query #{::active-todos} :result (s/coll-of (s/keys :req-un [::?todo]))))
(s/def ::visible-todos (s/cat :query #{::visible-todos} :result (s/coll-of (s/keys :req-un [::?todo]))))
(s/def ::active-count (s/cat :query #{::active-count} :result (s/coll-of (s/keys :req-un [::?count]))))
(s/def ::completed-count (s/cat :query #{::completed-count} :result (s/coll-of (s/keys :req-un [::?count]))))
(s/def ::visibility (s/cat :query #{::visibility} :result (s/coll-of (s/keys :req-un [::?visibility]))))
(s/def ::all-completed (s/cat :query #{::all-completed} :result (s/coll-of (s/keys :req-un [::?all-completed]))))
(s/def ::show-clear (s/cat :query #{::show-clear} :result (s/coll-of (s/keys :req-un [::?show-clear]))))

(s/def ::query-result (s/or ::visible-todos ::visible-todos
                            ::completed-todos ::completed-todos
                            ::active-todos ::active-todos
                            ::todo-by-id ::todo-by-id
                            ::active-count ::active-count
                            ::completed-count ::completed-count
                            ::visibility ::visibility
                            ::all-completed ::all-completed
                            ::show-clear ::show-clear))