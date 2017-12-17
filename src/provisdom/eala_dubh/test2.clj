(ns provisdom.eala-dubh.test2
  (:require [clara.rules :refer :all]
            [clara.rules.dsl :as dsl]
            [clara.rules.compiler :as com]
            [cljs.env :as env]
            [provisdom.eala-dubh.test :as t]))


(defsession poo 'provisdom.eala-dubh.test)

(-> poo
    (insert (t/->Foo "foo") (t/->Foo "bar"))
    (fire-rules))
