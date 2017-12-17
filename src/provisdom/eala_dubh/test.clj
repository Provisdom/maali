(ns provisdom.eala-dubh.test
  (:require [clara.rules :refer :all]
            [clara.rules.dsl :as dsl]
            [clara.rules.compiler :as com]
            [cljs.env :as env]))

(clear-ns-productions!)

(defrecord Foo [foo])

(defrule foob
         [Foo [{foo :foo}] (= ?foo foo)]
         =>
         (println "FOOOOO" ?foo))

(defrule baab
         [Foo [{foo :foo}] (= foo "bar")]
         =>
         (println "BAAAAA"))

