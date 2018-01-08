(ns provisdom.todo.commands
  (:require [clojure.spec.alpha :as s]
            [lambdaisland.uniontypes #?(:clj :refer :cljs :refer-macros) [case-of]]
            [net.cgrand.xforms :as xforms]
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.listeners :as listeners]))

;;; Model command specs
(s/def ::init (s/cat :command #{:init} :init-session rules/session?))
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
           ::update {:keys [id attrs]} (rules/upsert-q session ::specs/Todo (rules/query-fn ::todo/todo-by-id :?todo :?id id)
                                                     merge attrs)
           ::complete-all {:keys [done]} (rules/upsert-q session ::specs/Todo
                                                       (rules/query-fn (if done ::todo/active-todos ::todo/completed-todos) :?todo)
                                                       assoc ::specs/done done)
           ::retract {:keys [id]} (rules/retract session ::specs/Todo (rules/query-fn ::todo/todo-by-id :?todo :?id id))
           ::retract-completed _ (rules/retract session ::specs/Todo (rules/query-fn ::todo/completed-todos :?todo))
           ::update-visibility {:keys [visibility]} (rules/upsert-q session ::specs/Visibility
                                                                  (rules/query-fn ::todo/visibility :?visibility)
                                                                  assoc ::specs/visibility visibility)))

(s/fdef handle-state-command
        :args (s/cat :session rules/session? :command ::command)
        :ret rules/session?)

(def update-state (fn [session command]
                    (-> session
                        (handle-state-command command)
                        (rules/fire-rules))))
;;; TODO - fix debuggery
(def debug-update-state (fn [session command]
                          (-> session
                              (handle-state-command command)
                              (rules/fire-rules))))
(def update-state-xf (comp (xforms/reductions update-state nil) (drop 1)))
(def debug-update-state-xf (comp (xforms/reductions debug-update-state nil) (drop 1)))