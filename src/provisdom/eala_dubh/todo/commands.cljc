(ns provisdom.eala-dubh.todo.commands
  (:require [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]
            [cljs.core.match :refer-macros [match]]))

(defn find-todo
  [id session]
  (-> (rules/query session ::todo/todo-by-id :?id id) first :?todo))

(defn find-visibility
  [session]
  (-> (rules/query session ::todo/visibility) first :?visibility))

(defn handle-state-command
  [session command]
  (match command
         [:init init-session] init-session
         [:insert :todo todo] (rules/insert session ::todo/Todo todo)
         [:insert-many :todos todos] (apply rules/insert session ::todo/Todo todos)
         [:retract :todo id] (rules/retract session (find-todo id session))
         [:retract-many :todos todos] (apply rules/retract session (map #(find-todo % session) todos))
         [:update :todo id attrs] (rules/update session ::todo/Todo (partial find-todo id) merge attrs)
         [:update :visibility visibility] (rules/update session ::todo/Visibility find-visibility assoc ::todo/visibility visibility)))

(defn update-state
  [session command]
  (if session
    (-> session
        (listeners/without-listener listeners/query-listener)
        (listeners/with-listener listeners/query-listener)
        (handle-state-command command)
        (rules/fire-rules))
    (handle-state-command nil command)))

(def update-state-xf (comp (xforms/reductions update-state nil) (drop 1)))

(def query-bindings-xf (map listeners/updated-query-bindings))

(defn query-result->command
  [binding-map-entry]
  (match binding-map-entry
         [::todo/visible-todos todos] [:render :todo-list (->> todos first :?todos (sort-by ::todo/timestamp))]
         [::todo/visibility visibility] [:render :visibility (-> visibility first :?visibility ::todo/visibility)]
         [::todo/active-count count] [:render :active-count (-> count first :?count)]
         [::todo/completed-count count] [:render :completed-count (-> count first :?count)]
         :else [::no-op]))

(def query-result-xf (map #(map query-result->command %)))