(ns provisdom.eala-dubh.todo.queries
  #?(:cljs
     (:require-macros [provisdom.eala-dubh.rules :refer [defqueries]]))
  (:require [clara.rules :refer [query]]
            #?(:clj [provisdom.eala-dubh.rules :refer [defqueries]])
            [provisdom.eala-dubh.todo.facts :as todo]))

(defqueries queries
            [::todos [[:?id]
                      [?todo <- `todo/Todo [{id :id}] (= ?id id)]]]
            [::total [[]
                      [?total <- `todo/Total]]])

(defn find-todo
  [id session]
  (-> (query session ::todos :?id id) first :?todo))