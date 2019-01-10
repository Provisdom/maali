(ns provisdom.maali.listeners
  "Support for tracing state changes in a Clara session."
  (:require [clara.rules.listener :as l]
            [clara.rules.engine :as eng]
            [provisdom.maali.rules :as rules]
            [provisdom.maali.tracing :as tracing]))

(declare to-query-listener)

(deftype PersistentQueryListener [activations retractions]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-query-listener listener)))

(declare append-bindings!)

(deftype QueryListener [activations retractions]
  l/ITransientEventListener
  (left-activate! [listener node tokens]
    (append-bindings! activations node tokens))

  (left-retract! [listener node tokens]
    (append-bindings! retractions node tokens))

  (right-activate! [listener node elements])

  (right-retract! [listener node elements])

  (insert-facts! [listener node token facts])

  (alpha-activate! [listener node facts])

  (insert-facts-logical! [listener node token facts])

  (retract-facts! [listener node token facts])

  (alpha-retract! [listener node facts])

  (retract-facts-logical! [listener node token facts])

  (add-accum-reduced! [listener node join-bindings result fact-bindings])

  (remove-accum-reduced! [listener node join-bindings fact-bindings])

  (add-activations! [listener node activations])

  (remove-activations! [listener node activations])

  (fire-rules! [listener node])

  (to-persistent! [listener]
    (PersistentQueryListener. activations retractions)))

(defn- append-bindings!
  [query-bindings node tokens]
  (when (instance? clara.rules.engine.QueryNode node)
    (let [name (-> node :query :name)
          bindings (mapv :bindings tokens)]
      (swap! query-bindings assoc name bindings))))

(defn- to-query-listener [^PersistentQueryListener listener]
  (QueryListener. (.-activations listener) (.-retractions listener)))

(defn query-listener
  "Creates a persistent tracing event listener"
  []
  (PersistentQueryListener. (atom {}) (atom {})))

(defn updated-query-results
  [session]
  (if-let [listener (->> (eng/components session)
                         :listeners
                         (filter #(instance? PersistentQueryListener %) )
                         (first))]
    ;;;Look at all of the activations and retractions for queries, and collect new query results for any
    ;;;queries that changed. Note that this is fast - queries have already been executed, we're just getting the
    ;;;results.
    (into {} (map (fn [[k _]] [k (rules/query session k)]))
          (concat @(.-activations ^PersistentQueryListener listener) @(.-retractions ^PersistentQueryListener listener)))
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

(defn session-reducer-with-query-listener
  "Updates a session by applying reducer-fn to the command, and handles
   query-listener so query bindings resulting from command can be tracked."
  [reducer-fn]
  (fn [session reducer-args]
    (if session
      (-> session
          (without-listener query-listener)
          (with-listener query-listener)
          (reducer-fn reducer-args)
          (rules/fire-rules))
      ;;; Allows for initialization command
      (-> (reducer-fn nil reducer-args)
          (with-listener query-listener)
          (rules/fire-rules)))))

(defn debug-session-reducer-with-query-listener
  [command-fn]
  (let [qfn (session-reducer-with-query-listener command-fn)]
    (fn [session command]
      (-> session
          tracing/trace
          (qfn command)
          tracing/print-trace))))

;;; Transducer for fetching query bindings from a reductions stream, used in conjunction with update-session-with-query-listener.
(def query-bindings-xf (map updated-query-results))