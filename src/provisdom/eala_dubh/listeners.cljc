(ns provisdom.eala-dubh.listeners
  "Support for tracing state changes in a Clara session."
  (:require [clara.rules.listener :as l]
            [clara.rules.engine :as eng]))

(declare to-query-listener)

(deftype PersistentQueryListener [query-bindings]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-query-listener listener)))

(declare append-trace)

(deftype QueryListener [query-bindings]
  l/ITransientEventListener
  (left-activate! [listener node tokens]
    #_(println "NODE" node)
    (when (instance? clara.rules.engine.QueryNode node)
      (let [name (-> node :query :name)
            bindings (mapv :bindings tokens)]
        (swap! query-bindings assoc name bindings))))

  (left-retract! [listener node tokens])

  (right-activate! [listener node elements])

  (right-retract! [listener node elements])

  (insert-facts! [listener facts])

  (alpha-activate! [listener node facts])

  (insert-facts-logical! [listener node token facts])

  (retract-facts! [listener facts])

  (alpha-retract! [listener node facts])

  (retract-facts-logical! [listener node token facts])

  (add-accum-reduced! [listener node join-bindings result fact-bindings])

  (remove-accum-reduced! [listener node join-bindings fact-bindings])

  (add-activations! [listener node activations])

  (remove-activations! [listener node activations])

  (fire-rules! [listener node])

  (to-persistent! [listener]
    (PersistentQueryListener. query-bindings)))

(defn- to-query-listener [^PersistentQueryListener listener]
  (QueryListener. (.-query_bindings listener)))

(defn query-listener
  "Creates a persistent tracing event listener"
  []
  (PersistentQueryListener. (atom {})))

(defn updated-query-bindings
  [session]
  (if-let [listener (->> (eng/components session)
                         :listeners
                         (filter #(instance? PersistentQueryListener %) )
                         (first))]
    @(.-query_bindings ^PersistentQueryListener listener)
    nil #_(throw (ex-info "No tracing listener attached to session." {:session session})))
  )

(defn is-listening?
  "Returns true if the given session has tracing enabled, false otherwise."
  [session listener-type]
  (let [{:keys [listeners]} (eng/components session)]
    (boolean (some #(= (type %) listener-type) listeners))))

(defn with-listener
  "Returns a new session identical to the given one, but with tracing enabled.
   The given session is returned unmodified if tracing is already enabled."
  [session listener-fn]
  (let [listener (listener-fn)]
    (if (is-listening? session (type listener))
      session
      (let [{:keys [listeners] :as components} (eng/components session)]
        (eng/assemble (assoc components
                        :listeners
                        (conj listeners listener)))))))

(defn without-listener
  "Returns a new session identical to the given one, but with tracing disabled
   The given session is returned unmodified if tracing is already disabled."
  [session listener-fn]
  (let [listener (listener-fn)]
    (if (is-listening? session (type listener))
      (let [{:keys [listeners] :as components} (eng/components session)]
        (eng/assemble (assoc components
                        :listeners
                        (remove #(= (type %) (type listener)) listeners))))
      session)))