(ns provisdom.eala-dubh.todo.commands
  (:require [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]))

(defmulti handle-state-command (fn [session command] (vec (take 2 command))))

(defmethod handle-state-command [:insert :todos]
  [session [_ _ todos]]
  (apply rules/insert session ::todo/Todo todos))

(defmethod handle-state-command [:retract :todos]
  [session [_ _ todos]]
  (apply rules/retract session todos))

(defn find-todo
  [id session]
  (-> (rules/query session ::todo/todo-by-id :?id id) first :?todo))

(defmethod handle-state-command [:set :todo-done]
  [session [_ _ id done]]
  (rules/update session ::todo/Todo (partial find-todo id) assoc ::todo/done done))

(defn find-visibility
  [session]
  (-> (rules/query session ::todo/visibility) first :?visibility))

(defmethod handle-state-command [:set :visibility]
  [session [_ _ visibility]]
  (rules/update session ::todo/Visibility find-visibility assoc ::todo/visibility visibility))

(defn update-state
  [session command]
  (-> session
      (listeners/without-listener listeners/query-listener)
      (listeners/with-listener listeners/query-listener)
      (handle-state-command command)
      (rules/fire-rules)))

(defsession session [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.rules/queries]
            {:fact-type-fn rules/spec-type})

(def update-state-xf (xforms/reductions update-state session))

(def query-bindings-xf (map listeners/updated-query-bindings))