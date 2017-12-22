(ns provisdom.eala-dubh.rules
  (:require [clojure.spec.alpha :as s]
            [clara.rules :as rules]
    #?(:clj [clara.macros :as macros])
    #?(:clj [clara.rules.compiler :as com])
    #?(:cljs [cljs.spec.alpha])))

#?(:clj
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
           @v)))))

#?(:clj
   (defn cljs-ns
     "Returns the ClojureScript namespace being compiled during Clojurescript compilation."
     []
     (if (compiling-cljs?)
       (-> 'cljs.analyzer (find-ns) (ns-resolve '*cljs-ns*) deref)
       nil)))

#?(:cljs
   (defprotocol TypeInfo
     (gettype [this])))

(defn spec-type
  ([x] (-> x meta ::spec-type))
  ([x s] (with-meta x {::spec-type s})))

#?(:clj
   (defmacro deffacttype
     [name fields & body]
     `(defrecord ~name
        ~fields
        ~'provisdom.eala-dubh.rules/TypeInfo
        (~'gettype [_#] (symbol ~(str (cljs-ns)) ~(str name)))
        ~@body)))

#?(:clj
   (def productions (atom {})))

#?(:clj
   (defn resolve-spec-form
     [spec-name]
     (loop [s spec-name]
       (let [form (@cljs.spec.alpha/registry-ref s)]
         (if (keyword? form) (recur form) form)))))

#?(:clj
   (defn add-args-to-production
     [production]
     (let [lhs (:lhs production)]
       (let [elhs (eval lhs)
             lhs' (vec
                    (for [constraint elhs]
                      (let [{:keys [type constraints args] :as c} (or (:from constraint) constraint)]
                        (if args
                          constraint
                          (let [form (let [f (resolve-spec-form type)]
                                       (if (and (list? f) (= 'cljs.spec.alpha/keys (first f)))
                                         f
                                         (throw (ex-info
                                                  (str "Fact types must be spec'ed with s/keys: (s/def " type " " (pr-str f) ")")
                                                  {:type type :form f}))))
                                args [{:keys (vec (mapcat (fn [[keys-type keys]]
                                                            (if (#{:req-un :opt-un} keys-type)
                                                              (map (comp keyword name) keys)
                                                              keys))
                                                          (->> form (drop 1) (partition 2))))}
                                      #_(com/field-name->accessors-used (eval type) constraints)]]
                            (assoc-in constraint (if (:from constraint) [:from :args] [:args]) args))))))]
         (assoc production :lhs (list 'quote lhs'))))))

#?(:clj
   (defmacro defrules
     [rules-name & rules]
     (let [prods (into {} (map (fn [[rule-name & rule]] [rule-name (add-args-to-production (apply macros/build-rule rule-name rule))])) rules)]
       (swap! productions assoc (symbol (name (ns-name *ns*)) (name rules-name)) (into {} (map (fn [[k v]] [k (eval v)])) prods))
       `(def ~rules-name ~prods))))

#?(:clj
   (defmacro defqueries
     [queries-name & queries]
     (let [prods (into {} (map (fn [[query-name & query]] [query-name (add-args-to-production (apply macros/build-query query-name query))])) queries)]
       (swap! productions assoc (symbol (name (ns-name *ns*)) (name queries-name)) (into {} (map (fn [[k v]] [k (eval v)])) prods))
       `(def ~queries-name ~prods))))

#?(:clj
   (defn names-unique
     [defs]
     (let [non-unique (->> defs
                           (group-by first)
                           (filter (fn [[k v]] (not= 1 (count v))))
                           first
                           set)]
       (if (empty? non-unique)
         defs
         (throw (ex-info "Non-unique production names" {:names non-unique}))))))

#?(:clj
   (defmacro defsession
     [name sources options]
     (let [prods (vec (vals (names-unique (apply concat (map @productions sources)))))]
       #_(binding [*out* *err*] (println "******" #_options #_prods @productions))
       `(def ~name ~(macros/productions->session-assembly-form prods options)))))

(defn check-and-spec
  [spec facts]
  (mapv #(if-let [e (s/explain-data spec %)]
          (throw (ex-info "Fact failed spec" {:fact % :explanation e}))
          (spec-type % spec))
       facts))

(defn insert
  [session spec & facts]
  (rules/insert-all session (check-and-spec spec facts)))

(defn insert!
  [spec & facts]
  (rules/insert-all! (check-and-spec spec facts)))

(defn retract
  [session & facts]
  (apply rules/retract session facts))

(defn retract!
  [& facts]
  (apply rules/retract! facts))

(defn update
  [session spec query-fn f & args]
  (let [item (query-fn session)
        new-item (when f (apply f item args))]
    (cond-> session
            item (retract item)
            new-item (insert spec new-item))))

(defn fire-rules
  [session]
  (rules/fire-rules session))

(defn query
  [session query & params]
  (apply rules/query session query params))