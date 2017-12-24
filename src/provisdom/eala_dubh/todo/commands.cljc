(ns provisdom.eala-dubh.todo.commands
  (:require [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]
            [cljs.core.match :refer-macros [match]]
            [clara.tools.inspect :as inspect]
            [provisdom.eala-dubh.listeners :as listeners]))

;;; Reduction function to update clara session state
(defn handle-state-command
  [session command]
  (match command
         [:init init-session] (-> init-session
                                  (rules/insert ::todo/Visibility {::todo/visibility :all})
                                  (rules/fire-rules))
         [:insert :todo todo] (rules/insert session ::todo/Todo todo)
         [:insert-many :todos todos] (apply rules/insert session ::todo/Todo todos)
         [:retract :todo id] (rules/retract session (rules/query-fn ::todo/todo-by-id :?todo :?id id))
         [:retract-completed :todos] (rules/retract session (rules/query-fn ::todo/completed-todos :?todo))
         [:update :todo id attrs] (rules/upsert session ::todo/Todo (rules/query-fn ::todo/todo-by-id :?todo :?id id)
                                                merge attrs)
         [:update :visibility visibility] (rules/upsert session ::todo/Visibility
                                                        (rules/query-fn ::todo/visibility :?visibility)
                                                        assoc ::todo/visibility visibility)
         [:complete-all :todos done] (rules/upsert session ::todo/Todo
                                                   (rules/query-fn (if done ::todo/active-todos ::todo/completed-todos) :?todo)
                                                   assoc ::todo/done done)))

(def update-state (listeners/update-with-query-listener-fn handle-state-command))
(def debug-update-state (listeners/debug-update-with-query-listener-fn handle-state-command))
(def update-state-xf (comp (xforms/reductions update-state nil) (drop 1)))
(def debug-update-state-xf (comp (xforms/reductions debug-update-state nil) (drop 1)))

(defn query-result->command
  [binding-map-entry]
  (match binding-map-entry
         [::todo/visible-todos todos] [:render :todo-list (->> todos (map :?todo) (sort-by ::todo/id))]
         [::todo/visibility visibility] [:render :visibility (-> visibility first :?visibility ::todo/visibility)]
         [::todo/active-count count] [:render :active-count (-> count first :?count)]
         [::todo/completed-count count] [:render :completed-count (-> count first :?count)]
         [::todo/all-completed all-completed] [:render :all-completed (-> all-completed first :?all-completed)]
         [::todo/show-clear show-clear] [:render :show-clear (-> show-clear first :?show-clear)]
         :else [::no-op]))

(def query-result-xf (map #(map query-result->command %)))