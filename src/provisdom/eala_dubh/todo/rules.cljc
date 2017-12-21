(ns provisdom.eala-dubh.todo.rules
  (:require [provisdom.eala-dubh.dom :as dom]
    #?(:clj [provisdom.eala-dubh.rules :refer [defrules] :as rules]
       :cljs [provisdom.eala-dubh.rules :refer-macros [defrules] :as rules])
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.todo.views :as views]
            [provisdom.eala-dubh.todo.facts :as facts]))

(defrules rules
  [::start [[::facts/Start (= ?k session)]
            =>
            (println "START")
            (dom/patch (.getElementById js/document "app")
                       [views/app ?k])]]
  [::todo-list [[::facts/Start (= ?session session)]
                [?todos <- (acc/all) :from [::facts/Todo]]
                =>
                #_(println ?todos)
                (dom/patch "task-list" (views/task-list ?session (sort-by ::facts/id ?todos) false))]]
  [::active-count [[?count <- (acc/count) :from [::facts/Todo (= done false)]]
                   =>
                   (println "Active" ?count)
                   (rules/insert! ::facts/Active {::facts/count ?count})]]
  [::done-count [[?count <- (acc/count) :from [::facts/Todo (= done true)]]
                 =>
                 (rules/insert! ::facts/Done {::facts/count ?count})]]
  [::total-count [[::facts/Active (= ?a count)]
                  [::facts/Done (= ?d count)]
                  =>
                  (println "Total" (+ ?a ?d))
                  (rules/insert! ::facts/Total {::facts/count (+ ?a ?d)})]]
  [::footer [[::facts/Start  (= ?session session)]
             [::facts/Active  (= ?active-count count)]
             [::facts/Done  (= ?done-count count)]
             [::facts/Visibility  (= ?visibility visibility)]
             =>
             (dom/patch "footer" (views/footer ?session ?active-count ?done-count ?visibility))]])
