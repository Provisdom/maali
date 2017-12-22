(ns provisdom.eala-dubh.todo.commands.view
  (:require [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]
            [cljs.core.match :refer-macros [match]]))

(defn query-result->command
  [binding-map-entry]
  (match binding-map-entry
         [::todo/visible-todos todos] [::update :todo-list (-> todos first :?todos)]
         [::todo/visibility visibility] [::update :visibility (-> visibility first :?visibility ::todo/visibility)]
         [::todo/active-count count] [::update :active-count (-> count first :?count)]
         :else [::no-op]))

(def query-result-xf (map #(map query-result->command %)))