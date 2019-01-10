(ns provisdom.maali.rules
  (:require [cljs.spec.alpha]
            [datascript.core :as d]
            [datascript.parser :as dp]
            [clojure.set :as set]
            #?(:clj  [clojure.pprint :refer [pprint]]
               :cljs [cljs.pprint :refer [pprint]])
            #?(:clj [clojure.spec.alpha :as s])
            #?(:cljs [clojure.spec.alpha :as s])))

(defn throw-when-not-valid
  [x spec]
  (when-let [e (s/explain-data spec x)]
    #?(:cljs
       (do
         (enable-console-print!)
         (.error js/console (str "Data failed spec " (pr-str spec)))
         (pprint e)))
    (throw (ex-info (str "Data failed spec " (pr-str spec)) {:fact x :explanation (s/explain-str spec x)})))
  x)

(defn spec-type
  ([x] (-> x meta ::spec-type))
  ([x spec]
   (throw-when-not-valid x spec)
   (vary-meta x assoc ::spec-type spec)))

(s/def ::lhs (s/+ vector?))
(s/def ::rhs (s/+ list?))
(s/def ::params (s/coll-of keyword? :type vector?))
(s/def ::rule (s/cat :name keyword? :doc (s/? string?) :opts (s/? map?) :lhs ::lhs :sep #{'=>} :rhs ::rhs))

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

(defn- check-and-spec
  "Checks that facts conform to the specified spec, decorates fact
   maps with metadata containing"
  [spec facts]
  (let [form (@cljs.spec.alpha/registry-ref spec)]
    (when (= ::s/unknown form) (throw (ex-info (str "Unknown spec " (pr-str spec)) {:spec spec}))))
  (mapv #(spec-type % spec) facts))

#_(defn upsert
    "For session, retracts old-fact (if not nil)
   and unconditionally inserts a fact created by applying the supplied
   function and arguments to old-fact."
    [session spec old-fact f & args]
    (let [s (if old-fact
              (retract session spec old-fact)
              session)]
      (when-let [new-fact (apply f old-fact args)]
        (insert s spec new-fact))))

#?(:clj
   (defmacro def-derive
     "Macros to wrap useful pattern of defining a spec and calling
      derive on the spec and a \"parent\" spec to create a hierarchy."
     ([child-name parent-name]
      `(def-derive ~child-name ~parent-name ~parent-name))
     ([child-name parent-name spec]
      `(do
         (#?(:clj clojure.spec.alpha/def :cljs cljs.spec.alpha/def)
           ~child-name (#?(:clj clojure.spec.alpha/merge :cljs cljs.spec.alpha/merge) ~parent-name ~spec))
         (derive ~child-name ~parent-name)))))

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
