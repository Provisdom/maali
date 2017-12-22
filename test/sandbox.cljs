(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.tracing :as tracing]
            [net.cgrand.xforms :as xforms]
            [provisdom.eala-dubh.pprint :refer-macros [pprint]]
            [provisdom.eala-dubh.todo.commands.state :as st]))

#_(enable-console-print!)

(defsession session [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def id1 (random-uuid))
(def id2 (random-uuid))
(def id3 (random-uuid))
(def cmds [[::st/insert-many :todos [#::todo{:id id1 :title "Hi" :edit false :done false}
                                 #::todo{:id id2 :title "there!" :edit false :done false}]]
           [::st/update :visibility :all]
           [::st/update :todo id1 {::todo/done true}]
           [::st/update :visibility :active]
           [::st/retract :todo id1]
           [::st/insert-many :todos [#::todo{:id id1 :title "Hi" :edit false :done false}
                                 #::todo{:id id3 :title "FOO!" :edit false :done false}]]
           [::st/retract-many :todos [id1 id2 id3]]])

(pprint
  (into [] (map (comp (partial zipmap [:command :bindings]) vector)
                cmds
                (sequence (comp st/update-state-xf st/query-bindings-xf) cmds))))


