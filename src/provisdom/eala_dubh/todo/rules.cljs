(ns provisdom.eala-dubh.todo.rules
  (:require-macros [provisdom.eala-dubh.rules :refer [defrules]])
  (:require [provisdom.eala-dubh.dom :as dom]
            [clara.rules :refer [insert insert-all retract fire-rules query insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.session :as session]
            [provisdom.eala-dubh.todo.views :as views]
            [provisdom.eala-dubh.todo.facts :as facts]
            [clojure.string :as string]))

(defrules rules
          [::start [[`facts/Start (= ?k session-key)]
                   =>
                   (println "START")
                   (dom/patch (.getElementById js/document "app")
                              [views/app ?k])]]
          [::todo-list [[`facts/Start (= ?k session-key)]
                       [?todos <- (acc/all) :from [`facts/Todo]]
                       =>
                       #_(println ?todos)
                       (dom/patch "task-list" (views/task-list ?k (sort-by :id ?todos) false))]]
          [::active-count [[?count <- (acc/count) :from [`facts/Todo (= done false)]]
                          =>
                          (insert! (facts/->Active ?count))]]
          [::total-count [[`facts/Active (= ?a count)]
                         [`facts/Done (= ?d count)]
                         =>
                         (insert! (facts/->Total (+ ?a ?d)))]]
          [::footer [[`facts/Start (= ?k session-key)]
                    [`facts/Active (= ?active-count count)]
                    [`facts/Done (= ?done-count count)]
                    [`facts/Visibility (= ?visibility visibility)]
                    =>
                    (dom/patch "footer" (views/footer ?k ?active-count ?done-count ?visibility))]])
