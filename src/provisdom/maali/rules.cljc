(ns provisdom.maali.rules
  (:require [clojure.spec.alpha :as s]
            [datascript.core :as d]
            [datascript.parser :as dp]
            [clojure.set :as set]
            #?(:clj  [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])))

(defn create-session
  [schema & ruleses]
  {:rules (->> ruleses
               (apply merge)
               (map (fn [[name rule]] [name (assoc rule :pending-bindings {} :retracted-bindings {})]))
               (into {}))
   :db    (d/empty-db schema)})

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
      {:rules rules
       :db    db}
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
                                                                    (let [{:keys [tx-data db-after]} (d/with db (vec tx-data))]
                                                                      [(assoc bindings binding tx-data) db-after]))
                                                                  [{} db] (:pending-bindings rule))]
                                       [(assoc rules name (-> rule
                                                              (update :bindings merge bindings)
                                                              (assoc :pending-bindings {})))
                                        db']))
                                   [{} db'] rules')]
        (recur db'' (update-bindings db'' rules''))))))

(defn query
  [session q]
  (d/q q (:db session)))

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
