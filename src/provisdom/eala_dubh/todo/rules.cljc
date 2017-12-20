(ns provisdom.eala-dubh.todo.rules
  (:require [provisdom.eala-dubh.dom :as dom]
    #?(:clj
            [provisdom.eala-dubh.rules :refer [defrules] :as rules]
       :cljs [provisdom.eala-dubh.rules :refer-macros [defrules] :as rules])
            [clara.rules :refer [insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.todo.views :as views]
            [provisdom.eala-dubh.todo.facts :as facts]))

(defrules rules
  [::start [[::facts/Start [{session-key ::facts/session-key}] (= ?k session-key)]
            =>
            (println "START")
            (dom/patch (.getElementById js/document "app")
                       [views/app ?k])]]
  [::todo-list [[::facts/Start [{session-key ::facts/session-key}] (= ?k session-key)]
                [?todos <- (acc/all) :from [::facts/Todo]]
                =>
                #_(println ?todos)
                (dom/patch "task-list" (views/task-list ?k (sort-by ::facts/id ?todos) false))]]
  [::active-count [[?count <- (acc/count) :from [::facts/Todo [{::facts/keys [done]}] (= done false)]]
                   =>
                   (println "Active" ?count)
                   (insert! (rules/spec-type {::facts/count ?count} ::facts/Active))]]
  [::done-count [[?count <- (acc/count) :from [::facts/Todo [{::facts/keys [done]}] (= done true)]]
                 =>
                 (insert! (rules/spec-type {::facts/count ?count} ::facts/Done))]]
  [::total-count [[::facts/Active [{::facts/keys [count]}] (= ?a count)]
                  [::facts/Done [{::facts/keys [count]}] (= ?d count)]
                  =>
                  (println "Total" (+ ?a ?d))
                  (insert! (rules/spec-type {::facts/count (+ ?a ?d)} ::facts/Total))]]
  [::footer [[::facts/Start [{::facts/keys [session-key]}] (= ?k session-key)]
             [::facts/Active [{::facts/keys [count]}] (= ?active-count count)]
             [::facts/Done [{::facts/keys [count]}] (= ?done-count count)]
             [::facts/Visibility [{::facts/keys [visibility]}] (= ?visibility visibility)]
             =>
             (dom/patch "footer" (views/footer ?k ?active-count ?done-count ?visibility))]])
