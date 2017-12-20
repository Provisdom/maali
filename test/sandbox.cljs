(ns sandbox.foo
  (:require [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defsession]]
            [provisdom.eala-dubh.todo.facts :as facts]
            [provisdom.eala-dubh.todo.rules :as rules]
            [provisdom.eala-dubh.todo.queries :as queries]
            [clara.rules :refer [fire-rules insert]]))

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