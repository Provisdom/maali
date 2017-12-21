(ns provisdom.eala-dubh.session
  (:require [clara.rules :refer [insert insert-all retract fire-rules insert! retract!]]
            [clara.tools.tracing :as tracing]
            [clara.tools.inspect :as inspect]
            [clara.tools.fact-graph :as fact-graph]
            [cljs.pprint :refer [pprint]]))

(def tx-logs (atom {}))
(def sessions (atom {}))
(defn insert-unconditional
  [key & facts]
  (swap! tx-logs update key conj [true (vec facts)])
  (swap! sessions update key #(insert-all (tracing/with-tracing %) facts))
  key)

(defn insert-unconditional-all
  [key facts]
  (apply insert-unconditional key facts))

(defn retract-unconditional
  [key fact]
  (swap! tx-logs update key conj [false fact])
  (swap! sessions update key #(retract (tracing/with-tracing %) fact))
  key)

(defn reload-session
  [key]
  (doseq [[added? tx] (@tx-logs key)]
    (swap! sessions update key
           #(-> %
                ((if added? insert-all retract) tx)
                (fire-rules))))
  key)

(defn register
  [session key]
  (swap! sessions assoc key session)
  (swap! tx-logs #(assoc % key (or (% key) [])))
  (reload-session key))

(defn fire-rules!
  [key]
  (let [session (@sessions key)
        s' (fire-rules session)]
    (swap! sessions assoc key (tracing/without-tracing s'))
    (pprint (tracing/get-trace s'))
    #_(inspect/explain-activations s')
    #_(pprint (fact-graph/session->fact-graph s')))
  key)

(defn query
  [session q & args]
  (apply clara.rules/query (@sessions session) q args))

(defn upsert
  [key query-fn f & args]
  (let [item (query-fn (@sessions key))
        new-item (when f (apply f item args))]
    (cond-> key
        item (retract-unconditional item)
        new-item (insert-unconditional new-item))))

