(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defqueries defsession] :as rules]
            [provisdom.eala-dubh.todo.facts :as facts]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.queries :as queries]
            [clojure.spec.alpha :as s]
            [provisdom.eala-dubh.listeners :as listeners]
            [cljs.pprint :refer [pprint]]))

#_(enable-console-print!)

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
           [?f <- ::c]]]
  [::qrab [[]
           [?f <- ::d]]])


(defsession specky [sandbox.foo/ruuls sandbox.foo/quubs] {:fact-type-fn rules/spec-type})

(def q-listeners
  {::qoof #(println "qoof" %)
   ::qrab #(println "qrab" %)})

(-> (listeners/with-listener specky (listeners/query-listener q-listeners))
    (rules/insert ::c f1 f2)
    (rules/fire-rules))
