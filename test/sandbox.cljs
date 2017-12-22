(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.commands :as commands]
            [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.tracing :as tracing]
            [net.cgrand.xforms :as xforms]
            [cljs.pprint :refer [pprint]]))

#_(enable-console-print!)

(defsession session [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def id1 (random-uuid))
(def cmds [[:insert :todos [#::todo{:id id1 :title "Hi" :edit false :done false}
                            #::todo{:id (random-uuid) :title "there!" :edit false :done false}]]
           [:set :visibility :all]
           [:set :todo-done id1 true]])

(pprint
  (into []  (map (comp (partial zipmap [:command :bindings]) vector)
                 cmds
                 (rest (sequence (comp commands/update-state-xf commands/query-bindings-xf) cmds)))))

(comment
  (s/def ::a int?)
  (s/def ::b string?)
  (s/def ::foo ::a)
  (s/def ::c (s/keys :req [::a ::b] :req-un [::foo]))
  (s/def ::d (s/keys :opt [::a]))

  (def f1 {::a 1 ::b "foo" :foo 2})
  (def f2 {::a 2 ::b "bar" :foo 3})

  (defrules ruuls
    [::foo
     [::c (= ?b b) (= ?a a)]
     =>
     (println "::foo" ?a ?b)])

  (defqueries quubs
    [::qoof
     []
     [::c (= ?a a)]]
    [::qrab
     []
     [?f <- ::d]])


  (defsession specky [sandbox.foo/ruuls sandbox.foo/quubs] {:fact-type-fn rules/spec-type})

  (def q-listeners
    {::qoof #(println "qoof" %)
     ::qrab #(println "qrab" %)})

  (pprint
    (listeners/updated-query-bindings
      (-> (listeners/with-listener specky (listeners/query-listener))
          (rules/insert ::c f1 f2)
          (rules/fire-rules))))
  )
