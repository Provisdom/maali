(ns provisdom.maali.rules
  (:require [clojure.spec.alpha :as s]
            [datascript.core :as d]
            [datascript.parser :as dp]
            [clojure.set :as set]
            #?(:clj  [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])))

(defrecord Session [rules db]
  #?@(
      :cljs
      [Fn
       IFn
       (-invoke [_ q]
                (d/q q db))
       (-invoke [_ q b]
                (d/q q db b))
       (-invoke [_ q b c]
                (d/q q db b c))
       (-invoke [_ q b c d]
                (d/q q db b c d))
       (-invoke [_ q b c d e]
                (d/q q db b c d e))
       (-invoke [_ q b c d e f]
                (d/q q db b c d e f))
       (-invoke [_ q b c d e f g]
                (d/q q db b c d e f g))
       (-invoke [_ q b c d e f g h]
                (d/q q db b c d e f g h))
       (-invoke [_ q b c d e f g h i]
                (d/q q db b c d e f g h i))
       (-invoke [_ q b c d e f g h i j]
                (d/q q db b c d e f g h i j))
       (-invoke [_ q b c d e f g h i j k]
                (d/q q db b c d e f g h i j k))
       (-invoke [_ q b c d e f g h i j k l]
                (d/q q db b c d e f g h i j k l))
       (-invoke [_ q b c d e f g h i j k l m]
                (d/q q db b c d e f g h i j k l m))
       (-invoke [_ q b c d e f g h i j k l m n]
                (d/q q db b c d e f g h i j k l m n))
       (-invoke [_ q b c d e f g h i j k l m n o]
                (d/q q db b c d e f g h i j k l m n o))
       (-invoke [_ q b c d e f g h i j k l m n o p]
                (d/q q db b c d e f g h i j k l m n o p))
       (-invoke [_ q b c d e f g h i j k l m n o p q]
                (d/q q db b c d e f g h i j k l m n o p q))
       (-invoke [_ q b c d e f g h i j k l m n o p q r]
                (d/q q db b c d e f g h i j k l m n o p q r))
       (-invoke [_ q b c d e f g h i j k l m n o p q r s]
                (d/q q db b c d e f g h i j k l m n o p q r s))
       (-invoke [_ q b c d e f g h i j k l m n o p q r s t]
                (d/q q db b c d e f g h i j k l m n o p q r s t))
       (-invoke [_ q b c d e f g h i j k l m n o p q r s t rest]
                (apply d/q q db b c d e f g h i j k l m n o p q r s t rest))]
      :clj
      [clojure.lang.Fn
       clojure.lang.IFn
       (invoke [_ q]
         (d/q q db))
       (invoke [_ q b]
         (d/q q db b))
       (invoke [_ q b c]
         (d/q q db b c))
       (invoke [_ q b c d]
         (d/q q db b c d))
       (invoke [_ q b c d e]
         (d/q q db b c d e))
       (invoke [_ q b c d e f]
         (d/q q db b c d e f))
       (invoke [_ q b c d e f g]
         (d/q q db b c d e f g))
       (invoke [_ q b c d e f g h]
         (d/q q db b c d e f g h))
       (invoke [_ q b c d e f g h i]
         (d/q q db b c d e f g h i))
       (invoke [_ q b c d e f g h i j]
         (d/q q db b c d e f g h i j))
       (invoke [_ q b c d e f g h i j k]
         (d/q q db b c d e f g h i j k))
       (invoke [_ q b c d e f g h i j k l]
         (d/q q db b c d e f g h i j k l))
       (invoke [_ q b c d e f g h i j k l m]
         (d/q q db b c d e f g h i j k l m))
       (invoke [_ q b c d e f g h i j k l m n]
         (d/q q db b c d e f g h i j k l m n))
       (invoke [_ q b c d e f g h i j k l m n o]
         (d/q q db b c d e f g h i j k l m n o))
       (invoke [_ q b c d e f g h i j k l m n o p]
         (d/q q db b c d e f g h i j k l m n o p))
       (invoke [_ q b c d e f g h i j k l m n o p q]
         (d/q q db b c d e f g h i j k l m n o p q))
       (invoke [_ q b c d e f g h i j k l m n o p q r]
         (d/q q db b c d e f g h i j k l m n o p q r))
       (invoke [_ q b c d e f g h i j k l m n o p q r s]
         (d/q q db b c d e f g h i j k l m n o p q r s))
       (invoke [_ q b c d e f g h i j k l m n o p q r s t]
         (d/q q db b c d e f g h i j k l m n o p q r s t))
       (invoke [_ q b c d e f g h i j k l m n o p q r s t rest]
         (apply d/q q db b c d e f g h i j k l m n o p q r s t rest))]))


(defn create-session
  [schema & ruleses]
  (map->Session
    {:rules (->> ruleses
                 (apply merge)
                 (map (fn [[name rule]] [name (assoc rule :pending-bindings {} :retracted-bindings {})]))
                 (into {}))
     :db    (d/empty-db schema)}))

(defn update-bindings
  [db rules]
  (->> rules
       (map (fn [[name {:keys [query rhs-fn bindings] :as rule}]]
              (let [current-results (set (d/q query db))
                    old-results (-> bindings keys set)
                    added-results (set/difference current-results old-results)
                    retracted-results (set/difference old-results current-results)
                    added-bindings (->> added-results
                                        (map (fn [b]
                                               (->> b
                                                    (apply rhs-fn)
                                                    set
                                                    (vector b))))
                                        (into {}))
                    retracted-bindings (select-keys bindings retracted-results)]
                [name (assoc rule :pending-bindings added-bindings
                                  :retracted-bindings retracted-bindings)])))

       (into {})))

(defn transact
  [{:keys [rules db] :as session} unconditional-insert-tx-data]
  (loop [db (d/db-with db unconditional-insert-tx-data)
         rules (update-bindings db rules)]
    (if (every? (fn [[_ rule]] (and (-> rule :pending-bindings empty?)
                                    (-> rule :retracted-bindings empty?)))
                rules)
      (assoc session :rules rules
                     :db db)
      (let [[rules' db'] (reduce (fn [[rules db] [name rule]]
                                   (let [tx-data (->> rule
                                                      :retracted-bindings
                                                      vals
                                                      (mapcat identity)
                                                      (map (fn [[e a v]] [:db/retract e a v]))
                                                      set)]
                                     [(assoc rules name (-> rule
                                                            (assoc :retracted-bindings {})
                                                            (update :bindings #(apply dissoc % (keys (:retracted-bindings rule))))))
                                      (d/db-with db (vec tx-data))]))
                                 [{} db] rules)
            [rules'' db''] (reduce (fn [[rules db] [name rule]]
                                     (let [[bindings db'] (reduce (fn [[bindings db] [binding tx-data]]
                                                                    (try
                                                                      (let [g (group-by #(or (:unconditional? %) (= :db/add! (first %))) tx-data)
                                                                            cond-tx-data (vec (g false))
                                                                            uncond-tx-data (mapv (fn [d]
                                                                                                   (cond
                                                                                                     (map? d) (dissoc d :unconditional?)
                                                                                                     (vector? d) (assoc d 0 :db/add)))
                                                                                                 (g true))
                                                                            {:keys [tx-data db-after]} (d/with (d/db-with db uncond-tx-data) cond-tx-data)]
                                                                        [(assoc bindings binding tx-data) db-after])
                                                                      (catch Exception ex
                                                                        (throw (ex-info "TX FOO" {:name name :bindings bindings :tx-data tx-data :ex ex})))))
                                                                  [{} db] (:pending-bindings rule))]
                                       [(assoc rules name (-> rule
                                                              (update :bindings merge bindings)
                                                              (assoc :pending-bindings {})))
                                        db']))
                                   [{} db'] rules')]
        (recur db'' (update-bindings db'' rules''))))))

#?(:clj
   (defmacro check-invariant
     ([request result invariant-form] `(check-invariant ~request ~result ~invariant-form nil))
     ([request result invariant-form debug-info]
      `(if ~invariant-form
         true
         (do
           (println "Invariant violation for request type " (provisdom.maali.rules/spec-type ~request))
           (println "Invariant: " (quote ~invariant-form))
           (cljs.pprint/pprint ~request)
           (cljs.pprint/pprint ~result)
           (when ~debug-info (cljs.pprint/pprint ~debug-info))
           (println "********")
           false)))))
