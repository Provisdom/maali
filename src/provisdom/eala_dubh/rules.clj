(ns provisdom.eala-dubh.rules
  (:require [clara.rules.compiler :as com]
            [clara.macros :as macros]))

(defn compiling-cljs?
  "Return true if we are currently generating cljs code.  Useful because cljx does not
         provide a hook for conditional macro expansion."
  []
  (boolean
    (when-let [n (find-ns 'cljs.analyzer)]
      (when-let [v (ns-resolve n '*cljs-file*)]

        ;; We perform this require only if we are compiling ClojureScript
        ;; so non-ClojureScript users do not need to pull in
        ;; that dependency.
        #_(require 'clara.macros)
        @v))))

(defn cljs-ns
  "Returns the ClojureScript namespace being compiled during Clojurescript compilation."
  []
  (if (compiling-cljs?)
    (-> 'cljs.analyzer (find-ns) (ns-resolve '*cljs-ns*) deref)
    nil))


(defmacro deffacttype
  [name fields & body]
  `(defrecord ~name
     ~fields
     ~'provisdom.eala-dubh.session/TypeInfo
     (~'gettype [_#] (symbol ~(str (cljs-ns)) ~(str name)))
     ~@body))

(def productions (atom {}))

(defn add-args-to-production
  [production]
  (let [lhs (:lhs production)]
    (let [elhs (eval lhs)
          lhs' (vec
                 (for [constraint elhs]
                   (let [{:keys [type constraints args] :as c} (or (:from constraint) constraint)]
                     (if args
                       constraint
                       (let [args [(com/field-name->accessors-used (eval type) constraints)]]
                         (assoc-in constraint (if (:from constraint) [:from :args] [:args]) args))))))]
      (assoc production :lhs (list 'quote lhs')))))

(defmacro defrules
  [rules-name & rules]
  (let [prods (into {} (map (fn [[rule-name rule]] [rule-name (add-args-to-production (apply macros/build-rule rule-name rule))])) rules)]
    (swap! productions assoc (symbol (name (ns-name *ns*)) (name rules-name)) (into {} (map (fn [[k v]] [k (eval v)])) prods))
    `(def ~rules-name ~prods)))

(defmacro defqueries
  [queries-name & queries]
  (let [prods (into {} (map (fn [[query-name query]] [query-name (add-args-to-production (apply macros/build-query query-name query))])) queries)]
    (swap! productions assoc (symbol (name (ns-name *ns*)) (name queries-name)) (into {} (map (fn [[k v]] [k (eval v)])) prods))
    `(def ~queries-name ~prods)))

(defn names-unique
  [defs]
  (let [non-unique (->> defs
                        (group-by first)
                        (filter (fn [[k v]] (not= 1 (count v))))
                        first
                        set)]
    (if (empty? non-unique)
      defs
      (throw (ex-info "Non-unique production names" {:names non-unique})))))

(defmacro defsession
  [name sources options]
  (let [prods (vec (vals (names-unique (apply concat (map @productions sources)))))]
    #_(binding [*out* *err*] (println "******" #_options #_prods @productions))
    `(def ~name ~(macros/productions->session-assembly-form prods options))))

(comment
  (defmacro defsession
    [name fields & body]
    `(let [session-name# (symbol ~(str (cljs-ns)) ~(str name))]
       (clara.rules/defsession ~name
         ~fields
         ~@body)
       (swap! 'provisdom.eala-dubh.session/sessions update session-name# ~name)))

  (defmacro defrule
    [name & body]
    (let [production (apply macros/build-rule name body)
          {:keys [lhs rhs]} production]
      (macros/defrule! name (add-args-to-production production))))

  (defmacro defqueery
    [name & body]
    (let [query (apply macros/build-query name body)
          lhs (:lhs query)]
      (macros/defquery! name (add-args-to-production query)))))

