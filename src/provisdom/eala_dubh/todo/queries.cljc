(ns provisdom.eala-dubh.todo.queries
  #?(:cljs
     (:require-macros [provisdom.eala-dubh.rules :refer [defqueries]]))
  (:require [clara.rules :refer [query]]
            #?(:clj [provisdom.eala-dubh.rules :refer [defqueries]])
            [provisdom.eala-dubh.todo.facts :as todo])
  #?(:clj (:import [provisdom.eala-dubh.todo.facts Todo])))

(defqueries queries
            [::todos [[:?id]
                      [?todo <- #?(:clj Todo :cljs`todo/Todo) [{id :id}] (= ?id id)]]]
            [::total [[]
                      [?total <- #?(:clj Todo :cljs `todo/Total)]]])

(defn find-todo
  [id session]
  (-> (query session ::todos :?id id) first :?todo))