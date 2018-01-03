(ns provisdom.eala-dubh.todo.commands
  (:require [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.todo.specs :as specs]
            [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]
            [lambdaisland.uniontypes #?(:clj :refer :cljs :refer-macros) [case-of]]
            [provisdom.eala-dubh.listeners :as listeners]))

;;; Model command specs
(s/def ::init (s/cat :command #{:init} :init-session rules/session?)) ; TODO - spec session?
(s/def ::insert (s/cat :command #{:insert} :todo ::specs/Todo))
(s/def ::insert-many (s/cat :command #{:insert-many} :todos (s/coll-of ::specs/Todo)))
(s/def ::update (s/cat :command #{:update} :id ::specs/id :attrs ::specs/todo-attrs))
(s/def ::complete-all (s/cat :command #{:complete-all} :done ::specs/done))
(s/def ::retract (s/cat :command #{:retract} :id ::specs/id))
(s/def ::retract-completed (s/cat :command #{:retract-completed}))
(s/def ::update-visibility (s/cat :command #{:update-visibility} :visibility ::specs/visibility))

(s/def ::command (s/or ::init ::init
                       ::insert ::insert
                       ::insert-many ::insert-many
                       ::update ::update
                       ::complete-all ::complete-all
                       ::retract ::retract
                       ::retract-completed ::retract-completed
                       ::update-visibility ::update-visibility))

;;; Reduction function to update clara session state
(defn handle-state-command
  [session command]
  (case-of ::command command
           ::init {:keys [init-session]} init-session
           ::insert {:keys [todo]} (rules/insert session ::specs/Todo todo)
           ::insert-many {:keys [todos]} (apply rules/insert session ::specs/Todo todos)
           ::update {:keys [id attrs]} (rules/upsert session ::specs/Todo (rules/query-fn ::todo/todo-by-id :?todo :?id id)
                                                     merge attrs)
           ::complete-all {:keys [done]} (rules/upsert session ::specs/Todo
                                                       (rules/query-fn (if done ::todo/active-todos ::todo/completed-todos) :?todo)
                                                       assoc ::specs/done done)
           ::retract {:keys [id]} (rules/retract session (rules/query-fn ::todo/todo-by-id :?todo :?id id))
           ::retract-completed _ (rules/retract session (rules/query-fn ::todo/completed-todos :?todo))
           ::update-visibility {:keys [visibility]} (rules/upsert session ::specs/Visibility
                                                                  (rules/query-fn ::todo/visibility :?visibility)
                                                                  assoc ::specs/visibility visibility)))

(s/fdef handle-state-command
        :args (s/cat :session rules/session? :command ::specs/command)
        :ret rules/session?)

(def update-state (listeners/update-with-query-listener-fn handle-state-command))
(def debug-update-state (listeners/debug-update-with-query-listener-fn handle-state-command))
(def update-state-xf (comp (xforms/reductions update-state nil) (drop 1)))
(def debug-update-state-xf (comp (xforms/reductions debug-update-state nil) (drop 1)))

;;; View command specs
(s/def ::todo-list (s/cat :key #{:todo-list} :value (s/coll-of ::specs/Todo)))
(s/def ::visibility (s/cat :key #{:visibility} :value ::specs/visibility))
(s/def ::active-count (s/cat :key #{:active-count} :value ::specs/count))
(s/def ::completed-count (s/cat :key #{:completed-count} :value ::specs/count))
(s/def ::all-completed (s/cat :key #{:all-completed} :value ::specs/all-completed))
(s/def ::show-clear (s/cat :key #{:show-clear} :value ::specs/show-clear))
(s/def ::no-op (s/cat :no-op #{:no-op}))

(s/def ::view-command (s/or ::todo-list ::todo-list
                            ::visibility ::visibility
                            ::active-count ::active-count
                            ::completed-count ::completed-count
                            ::all-completed ::all-completed
                            ::show-clear ::show-clear
                            ::no-op ::no-op))

(defn query-result->command
  [binding-map-entry]
  (case-of ::todo/query-result binding-map-entry
           ::todo/visible-todos {:keys [result]} [:todo-list (->> result (map :?todo) (sort-by ::specs/id))]
           ::todo/visibility {:keys [result]} [:visibility (-> result first :?visibility ::specs/visibility)]
           ::todo/active-count {:keys [result]} [:active-count (-> result first :?count)]
           ::todo/completed-count {:keys [result]} [:completed-count (-> result first :?count)]
           ::todo/all-completed {:keys [result]} [:all-completed (-> result first :?all-completed)]
           ::todo/show-clear {:keys [result]} [:show-clear (-> result first :?show-clear)]
           ::todo/todo-by-id [] [:no-op]
           ::todo/completed-todos [] [:no-op]
           ::todo/active-todos [] [:no-op]))

(s/fdef query-result->command
        :args (s/cat :binding-map-entry ::todo/query-result)
        :ret ::view-command)

(def query-result-xf (map #(map query-result->command %)))