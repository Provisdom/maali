(ns sandbox
  (:require [clojure.test :refer :all]
            #_[provisdom.eala-dubh.todo.facts :as facts]
            #_[provisdom.eala-dubh.todo.rules :as rules]
            #_[provisdom.eala-dubh.todo.queries :as queries]
            [provisdom.eala-dubh.rules :refer [deffacttype defrules defsession]]
            [clara.rules])
  #_(:import (provisdom.eala_dubh.test Foo)))

(deffacttype Foo [foo])

(defrules rools
          [::foob [[`Foo [{foo :foo}] (= ?foo foo)]
                   =>
                   (println "FOOOOO" ?foo)]]
          [::baab [[`Foo [{foo :foo}] (= foo "bar")]
                   =>
                   (println "BAAAAA")]])

(defsession poo [sandbox/rools] {:fact-type-fn provisdom.eala-dubh.rules/gettype})

(-> poo
    (insert (->Foo "foo") (->Foo "bar"))
    (fire-rules))