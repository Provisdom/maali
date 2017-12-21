(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.eala-dubh.todo.facts :as facts]
            [provisdom.eala-dubh.todo.rules :as todo]
            [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.tracing :as tracing]
            [cljs.pprint :refer [pprint]]))

#_(enable-console-print!)

(defsession session [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def q-listeners
  {::todo/todo-count #(println "Count" %)})

(pprint
  (tracing/get-trace
    (let [s'
          (-> session
              (listeners/with-listener (listeners/query-listener q-listeners))
              #_(tracing/with-tracing)
              (rules/insert ::facts/Todo
                            #::facts{:id (random-uuid) :title "Hi" :edit false :done false}
                            #::facts{:id (random-uuid) :title "there!" :edit false :done false})
              (rules/insert ::facts/Visibility {::facts/visibility :all})
              (rules/fire-rules))
          s''
          (-> s'
              (rules/insert ::facts/Todo
                            #::facts{:id (random-uuid) :title "Foo" :edit false :done false})
              (rules/fire-rules))]
      s'')))

(comment
  (s/def ::a int?)
  (s/def ::b string?)
  (s/def ::foo ::a)
  (s/def ::c (s/keys :req [::a ::b] :req-un [::foo]))
  (s/def ::d (s/keys :opt [::a]))

  (def f1 {::a 1 ::b "foo" :foo 2})
  (def f2 {::a 2 ::b "bar" :foo 3})

  (defrules ruuls
    [::foo [[::c (= ?b b) (= ?a a)]
            =>
            (println "::foo" ?a ?b)]])

  (defqueries quubs
    [::qoof [[]
             [::c (= ?a a)]]]
    [::qrab [[]
             [?f <- ::d]]])


  (defsession specky [sandbox.foo/ruuls sandbox.foo/quubs] {:fact-type-fn rules/spec-type})

  (def q-listeners
    {::qoof #(println "qoof" %)
     ::qrab #(println "qrab" %)})

  (-> (listeners/with-listener specky (listeners/query-listener q-listeners))
      (rules/insert ::c f1 f2)
      (rules/fire-rules))
  )
