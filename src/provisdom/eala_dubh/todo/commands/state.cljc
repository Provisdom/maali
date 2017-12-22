(ns provisdom.eala-dubh.todo.commands.state
  (:require [provisdom.eala-dubh.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]))

(defmulti handle-state-command (fn [session command] (vec (take 2 command))))

(defmethod handle-state-command [::insert :todo]
  [session [_ _ todo]]
  (rules/insert session ::todo/Todo todo))

(defmethod handle-state-command [::insert-many :todos]
  [session [_ _ todos]]
  (apply rules/insert session ::todo/Todo todos))

(defn find-todo
  [id session]
  (-> (rules/query session ::todo/todo-by-id :?id id) first :?todo))

(defmethod handle-state-command [::retract :todo]
  [session [_ _ id]]
  (let [todo (find-todo id session)]
    (rules/retract session todo)))

(defmethod handle-state-command [::retract-many :todos]
  [session [_ _ ids]]
  (let [todos (map #(find-todo % session) ids)]
    (apply rules/retract session todos)))

(defmethod handle-state-command [::update :todo]
  [session [_ _ id attrs]]
  (rules/update session ::todo/Todo (partial find-todo id) merge attrs))

(defn find-visibility
  [session]
  (-> (rules/query session ::todo/visibility) first :?visibility))

(defmethod handle-state-command [::update :visibility]
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

(def update-state-xf (comp (xforms/reductions update-state session) (drop 1)))

(def query-bindings-xf (map listeners/updated-query-bindings))