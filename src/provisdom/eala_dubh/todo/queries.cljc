(ns provisdom.eala-dubh.todo.queries
  (:require [provisdom.eala-dubh.rules :as rules]
            #?(:clj [provisdom.eala-dubh.rules :refer [defqueries]]
               :cljs [provisdom.eala-dubh.rules :refer-macros [defqueries]])
            [provisdom.eala-dubh.todo.facts :as facts]))



(defn find-todo
  [id session]
  (-> (rules/query session ::todos :?id id) first :?todo))