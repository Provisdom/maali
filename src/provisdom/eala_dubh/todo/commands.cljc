(ns provisdom.eala-dubh.todo.commands
  (:require [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]
            [cljs.core.match :refer-macros [match]]
            [clara.tools.inspect :as inspect]))

(defn query-result-fn
  [query map-fn & args]
  (fn [session]
    (mapv map-fn (apply rules/query session query args))))

(defn handle-state-command
  [session command]
  (match command
         [:init init-session] (-> init-session
                                  (rules/insert ::todo/Visibility {::todo/visibility :all})
                                  (rules/fire-rules))
         [:insert :todo todo] (rules/insert session ::todo/Todo todo)
         [:insert-many :todos todos] (apply rules/insert session ::todo/Todo todos)
         [:retract :todo id] (rules/retract session (query-result-fn ::todo/todo-by-id :?todo :?id id))
         [:retract-completed :todos] (rules/retract session (query-result-fn ::todo/completed-todos :?todo))
         [:update :todo id attrs] (rules/upsert session ::todo/Todo (query-result-fn ::todo/todo-by-id :?todo :?id id)
                                                merge attrs)
         [:update :visibility visibility] (rules/upsert session ::todo/Visibility
                                                        (query-result-fn ::todo/visibility :?visibility)
                                                        assoc ::todo/visibility visibility)
         [:complete-all :todos done] (rules/upsert session ::todo/Todo
                                                   (query-result-fn (if done ::todo/active-todos ::todo/completed-todos) :?todo)
                                                   assoc ::todo/done done)))

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
         [::todo/visible-todos todos] [:render :todo-list (->> todos first :?todos (sort-by ::todo/id))]
         [::todo/visibility visibility] [:render :visibility (-> visibility first :?visibility ::todo/visibility)]
         [::todo/active-count count] [:render :active-count (-> count first :?count)]
         [::todo/completed-count count] [:render :completed-count (-> count first :?count)]
         [::todo/all-completed all-completed] [:render :all-completed (-> all-completed first :?all-completed ::todo/all-completed)]
         [::todo/show-clear show-clear] [:render :show-clear (-> show-clear first :?show-clear ::todo/show-clear)]
         :else [::no-op]))

(def query-result-xf (map #(map query-result->command %)))