(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defsession]]
            [provisdom.eala-dubh.todo.facts :as facts]
            [provisdom.eala-dubh.todo.rules :as rules]
            [provisdom.eala-dubh.todo.queries :as queries]
            [clara.rules :refer [fire-rules insert]]
            [clojure.spec.alpha :as s]))

(deffacttype Foo [foo])

(defrules rools
          [::foob [[`Foo [{foo :foo}] (= ?foo foo)]
                   =>
                   (println "FOOOOO" ?foo)]]
          [::baab [[`Foo [{foo :foo}] (= foo "bar")]
                   =>
                   (println "BAAAAA")]])

(defsession poo [sandbox.foo/rools] {:fact-type-fn provisdom.eala-dubh.rules/gettype})

(-> poo
    (insert (->Foo "foo") (->Foo "bar"))
    (fire-rules))

(s/def ::a int?)
(s/def ::b string?)
(s/def ::c (s/keys :req [::a ::b]))

(defn spec-type
  ([x] (-> x meta ::spec-type))
  ([x s] (with-meta x {::spec-type s})))

(def f1 (spec-type {::a 1 ::b "foo"} ::c))
(def f2 (spec-type {::a 2 ::b "bar"} ::c))

(defrules ruuls
  [::foo [[::c [{a ::a b ::b}] (= ?b b) (= ?a a)]
          =>
          (println "::foo" ?a ?b)]])

(defsession specky [sandbox.foo/ruuls] {:fact-type-fn spec-type})

(-> specky
    (insert f1 f2)
    (fire-rules))