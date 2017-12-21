(ns provisdom.eala-dubh.todo.rules
  (:require [provisdom.eala-dubh.dom :as dom]
    #?(:clj
            [provisdom.eala-dubh.rules :refer [defrules defqueries] :as rules]
       :cljs [provisdom.eala-dubh.rules :refer-macros [defrules defqueries] :as rules])
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.todo.views :as views]
            [provisdom.eala-dubh.todo.facts :as facts]))

(defrules rules)

(defqueries queries
  [::all-todos [[]
                [?todos <- (acc/all) :from [::facts/Todo]]]]
  [::active-todos [[]
                   [?todos <- (acc/all) :from [::facts/Todo (= done false)]]]]
  [::completed-todos [[]
                   [?todos <- (acc/all) :from [::facts/Todo (= done true)]]]]
  [::todo-by-id [[:?id]
                 [?todo <- ::facts/Todo [{::facts/keys [id]}] (= ?id id)]]]
  [::todo-count [[]
                 [?count <- (acc/count) :from [::facts/Todo (= done false)]]]]
  [::visibility [[]
                 [::facts/Visibility (= ?visibility visibility)]]])