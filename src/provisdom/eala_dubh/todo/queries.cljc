(ns provisdom.eala-dubh.todo.queries
  (:require [provisdom.eala-dubh.rules :as rules]
            #?(:clj [provisdom.eala-dubh.rules :refer [defqueries]]
               :cljs [provisdom.eala-dubh.rules :refer-macros [defqueries]])
            [provisdom.eala-dubh.todo.facts :as facts]))

(defqueries queries
            [::todos [[:?id]
                      [?todo <- ::facts/Todo [{::facts/keys [id]}] (= ?id id)]]]
            [::total [[]
                      [?total <- ::facts/Total]]])

(defn find-todo
  [id session]
  (-> (rules/query session ::todos :?id id) first :?todo))