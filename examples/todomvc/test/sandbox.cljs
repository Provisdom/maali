(ns sandbox.foo
  (:require [provisdom.todo.specs :as specs]
            [provisdom.maali.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint :refer-macros [pprint]]
            [provisdom.todo.commands :as commands]))

(defsession session [provisdom.todo.rules/rules provisdom.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def td1 (specs/new-todo "Hi"))
(def td2 (specs/new-todo "there"))
(def td3 (specs/new-todo "Hi!"))
(def td4 (specs/new-todo "FOO!"))
(def cmds [[:init session]
           [:update-visibility :all]
           [:insert-many [td1 td2]]
           [:update (::specs/id td1) {::specs/done true}]
           [:update (::specs/id td2) {::specs/done true}]
           [:retract-completed]
           [:insert-many [td3 td4]]])

(pprint
  (into [] (map (comp (partial zipmap [:command :bindings]) vector)
                cmds
                (sequence (comp commands/update-state-xf listeners/query-bindings-xf) cmds))))

(pprint
  (into [] (map (comp (partial zipmap [:command :view]) vector)
                cmds
                (sequence (comp commands/update-state-xf listeners/query-bindings-xf commands/query-result-xf) cmds))))


