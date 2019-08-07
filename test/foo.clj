(ns foo
  (:require [clara.rules :refer :all]
            [clara.rules.accumulators :as acc]
            [clojure.set :as set]))

(defrecord Num [x b])
(defrecord Req [num])
(defrecord Res [req num])

(defrule req!
         [?num <- Num (= ?x x)]
         =>
         (println "REQ" ?num)
         (insert! (->Req ?num)))

#_(defrule res!
         [?req <- Req (= ?num num)]
         [Res (= ?req req) (= ?num' num)]
         =>
         (println "RES" ?req ?num')
         (insert-unconditional! ?num')
         (retract! ?num))

(defrule res!
         [?req <- Req (= ?num num) (= ?x (:x num))]
         [Res (= ?req req) (= ?num' num)]
         [?num'' <- Num (= ?x x)]
         =>
         (println "RES" (:b ?num) (:b ?num''))
         (when (= (:b ?num) (:b ?num''))
           (retract! ?num)
           (insert-unconditional! (assoc ?num'' :b (:b ?num')))))

(defquery reqq [] [?req <- Req])
#_(let [s (mk-session 'foo)
        s' (-> s
             (insert (->Num 1 true))
             (insert (->Num 2 false))
             (fire-rules))
        rq (-> s' (query reqq) first :?req)
        _ (println rq)
        s'' (-> s'
                #_(retract (->Num 1 true))
                #_(insert (->Num 1 :foo))
                (insert (->Res rq (update (:num rq) :b not)))
                (fire-rules))]
    s'')

(defrecord Thing [status])

(defrule count-on
  [?n <- (acc/count) :from [Thing (= status :on)]]
  =>
  (println "On " ?n))

(def s (-> (mk-session 'foo)
           (insert (->Thing :on)
                   (->Thing :off))
           fire-rules))