(ns provisdom.eala-dubh.todo.queries
  #?(:cljs
     (:require-macros [provisdom.eala-dubh.rules :refer [defqueries]]))
  (:require [clara.rules :refer [query]]
            #?(:clj [provisdom.eala-dubh.rules :refer [defqueries]])
            [provisdom.eala-dubh.todo.facts :as facts]))

(defqueries queries
            [::todos [[:?id]
                      [?todo <- ::facts/Todo [{::facts/keys [id]}] (= ?id id)]]]
            [::total [[]
                      [?total <- ::facts/Total]]])

(defn find-todo
  [id session]
  (-> (query session ::todos :?id id) first :?todo))