(ns provisdom.eala-dubh.listeners
  "Support for tracing state changes in a Clara session."
  (:require [clara.rules.listener :as l]
            [clara.rules.engine :as eng]))

(declare to-query-listener)

(deftype PersistentQueryListener [reactions]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-query-listener listener)))

(declare append-trace)

(deftype QueryListener [reactions]
  l/ITransientEventListener
  (left-activate! [listener node tokens]
    (when (instance? clara.rules.engine.QueryNode node)
      (let [f (-> node :query :name reactions)
            bindings (mapv :bindings tokens)]
        (when f (f bindings)))))

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
    (PersistentQueryListener. reactions)))

(defn- to-query-listener [^PersistentLeftActivateListener listener]
  (QueryListener. (.-reactions listener)))

(defn query-listener
  "Creates a persistent tracing event listener"
  [reactions]
  (PersistentQueryListener. reactions))

(defn is-listening?
  "Returns true if the given session has tracing enabled, false otherwise."
  [session listener]
  (let [{:keys [listeners]} (eng/components session)]
    (boolean (some #(= (type %) (type listener)) listeners))))

(defn with-listener
  "Returns a new session identical to the given one, but with tracing enabled.
   The given session is returned unmodified if tracing is already enabled."
  [session listener]
  (if (is-listening? session listener)
    session
    (let [{:keys [listeners] :as components} (eng/components session)]
      (eng/assemble (assoc components
                      :listeners
                      (conj listeners listener))))))

(defn without-listening
  "Returns a new session identical to the given one, but with tracing disabled
   The given session is returned unmodified if tracing is already disabled."
  [session listener]
  (if (is-listening? session listener)
    (let [{:keys [listeners] :as components} (eng/components session)]
      (eng/assemble (assoc components
                      :listeners
                      (remove #(= (type %) (type listener)) listeners))))
    session))