(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.tracing :as tracing]
            [net.cgrand.xforms :as xforms]
            [provisdom.eala-dubh.pprint :refer-macros [pprint]]
            [provisdom.eala-dubh.todo.commands :as commands]))

#_(enable-console-print!)

(defsession session [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def td1 (todo/new-todo "Hi"))
(def td2 (todo/new-todo "there"))
(def td3 (todo/new-todo "Hi!"))
(def td4 (todo/new-todo "FOO!"))
(def cmds [[:init session]
           [:insert-many :todos [td1 td2]]
           [:update :visibility :all]
           [:update :todo (::todo/id td1) {::todo/done true}]
           [:update :visibility :active]
           [:retract :todo (::todo/id td1)]
           [:insert-many :todos [td3 td4]]
           [:retract-many :todos [(::todo/id td2) (::todo/id td3) (::todo/id td4)]]
           [:update :visibility :all]])

(pprint
  (into [] (map (comp (partial zipmap [:command :bindings]) vector)
                cmds
                (sequence (comp commands/update-state-xf commands/query-bindings-xf) cmds))))

(pprint
  (into [] (map (comp (partial zipmap [:command :view]) vector)
                cmds
                (sequence (comp commands/update-state-xf commands/query-bindings-xf commands/query-result-xf) cmds))))


