(ns foo2
  (:require [clara.rules :refer :all]
            [clara.rules.accumulators :as acc]))

(defrecord Num [x])
(defrecord Nums [nums])
(defrecord FireEvent [ts])
(defrecord TsNum [ts inner-num])

(defrule ts-num
         [FireEvent (= ?ts ts)]
         [?n <- Num]
         =>
         (insert! (->TsNum ?ts ?n)))

(defrule collect-nums
         [?nums <- (acc/all) :from [TsNum (= ?ts ts)]]
         =>
         (insert! (->Nums (mapv :inner-num ?nums))))


(defrule print-nums
         [?nums <- (acc/all) :from [TsNum (= ?ts ts)]]
         =>
         (println "FLOOB" ?nums))

(-> (mk-session [ts-num collect-nums print-nums])
    (insert (->Num 1))
    (insert (->Num 2))
    (insert (->FireEvent 1))
    (fire-rules)
    (insert (->Num 3))
    (insert (->FireEvent 2))
    (fire-rules)
    (retract (->Num 2))
    (insert (->FireEvent 3))
    (fire-rules))