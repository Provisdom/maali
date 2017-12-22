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

(def id1 (random-uuid))
(def id2 (random-uuid))
(def id3 (random-uuid))
(def cmds [[:init session]
           [:insert-many :todos [#::todo{:id id1 :title "Hi" :edit false :done false}
                                 #::todo{:id id2 :title "there!" :edit false :done false}]]
           [:update :visibility :all]
           [:update :todo id1 {::todo/done true}]
           [:update :visibility :active]
           [:retract :todo id1]
           [:insert-many :todos [#::todo{:id id1 :title "Hi" :edit false :done false}
                                 #::todo{:id id3 :title "FOO!" :edit false :done false}]]
           [:retract-many :todos [id1 id2 id3]]])

(pprint
  (into [] (map (comp (partial zipmap [:command :bindings]) vector)
                cmds
                (sequence (comp commands/update-state-xf commands/query-bindings-xf) cmds))))

(pprint
  (into [] (map (comp (partial zipmap [:command :view]) vector)
                cmds
                (sequence (comp commands/update-state-xf commands/query-bindings-xf commands/query-result-xf) cmds))))


