(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defsession] :as rules]
            [provisdom.eala-dubh.todo.facts :as facts]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.queries :as queries]
            [clojure.spec.alpha :as s]))

(enable-console-print!)

(s/def ::a int?)
(s/def ::b string?)
(s/def ::foo ::a)
(s/def ::c (s/keys :req [::a ::b] :req-un [::foo]))

(def f1 {::a 1 ::b "foo" :foo 2})
(def f2 {::a 2 ::b "bar" :foo 3})

(defrules ruuls
  [::foo [[::c (= ?b b) (= ?a a)]
          =>
          (println "::foo" ?a ?b)]])

(println ruuls)

(defsession specky [sandbox.foo/ruuls] {:fact-type-fn rules/spec-type})

(-> specky
    (rules/insert ::c f1 f2)
    (rules/fire-rules))
