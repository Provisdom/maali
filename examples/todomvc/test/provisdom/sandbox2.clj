(ns provisdom.sandbox2
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules :refer [defrules defqueries defsession] :as rules]
            [clara.rules.accumulators :as acc]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.tracing :as tracing]
            [net.cgrand.xforms :as xforms]))

(s/def ::foo int?)
(s/def ::fact (s/keys :req [::foo]))

(defrules roolz
          [::foo!
           [?foo <- ::fact]
           =>
           (println "FOO" ?foo)])

(defsession session [provisdom.sandbox2/roolz] {:fact-type-fn rules/spec-type})

(-> session
    (rules/insert ::fact {::foo 1})
    (rules/fire-rules))

(clojure.pprint/pprint (-> (clara.rules.engine/components session) :rulebase :alpha-roots))
(clojure.pprint/pprint (-> (clara.rules.engine/components session) :rulebase :beta-roots))
(clojure.pprint/pprint (-> (clara.rules.engine/components session) :rulebase :productions))

(clojure.pprint/pprint roolz)

(clara.rules/defrule foo!
                     [?foo <- ::fact]
                     =>
                     (println "FOO" ?foo))

(clara.rules/defsession session2 'provisdom.sandbox2 :fact-type-fn rules/spec-type)

(-> session2
    (rules/insert ::fact {::foo 1})
    (clara.rules/fire-rules))

(clojure.pprint/pprint (-> (clara.rules.engine/components session2) :rulebase :alpha-roots))
(clojure.pprint/pprint (-> (clara.rules.engine/components session2) :rulebase :beta-roots))

(def session3 (clara.rules.compiler/mk-session [[(::foo! roolz)] :fact-type-fn rules/spec-type]))

(-> session2
    (rules/insert ::fact {::foo 1})
    (clara.rules/fire-rules))

(clojure.pprint/pprint (:rulebase (clara.rules.engine/components session3)))