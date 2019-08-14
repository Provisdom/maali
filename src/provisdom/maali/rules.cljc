(ns provisdom.maali.rules
  (:require [cljs.spec.alpha]
            [clara.rules :as rules]
            [clara.rules.engine :as eng]
            [clara.rules.memory :as mem]
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
    #?(:clj [clara.macros :as macros])
    #?(:clj [clara.rules.compiler :as com])
    #?(:clj [clojure.spec.alpha :as s])
    #?(:cljs [clojure.spec.alpha :as s])
    #?(:clj [clara.rules.dsl :as dsl])
      [cljs.analyzer :as ana])
  #?(:clj
     (:import [clara.rules.engine LocalSession])))

#?(:clj
   (defn compiling-cljs?
     []
     (boolean
       (when-let [n (find-ns 'cljs.analyzer)]
         (when-let [v (ns-resolve n '*cljs-file*)]
           ;; We perform this require only if we are compiling ClojureScript
           ;; so non-ClojureScript users do not need to pull in
           ;; that dependency.
           #_(require 'clara.macros)
           @v)))))

(defn session?
  [x]
  (instance? clara.rules.engine.LocalSession x))

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
(s/def ::query (s/cat :name keyword? :doc (s/? string?) :params ::params :lhs ::lhs))
(s/def ::rule (s/cat :name keyword? :doc (s/? string?) :opts (s/? map?) :lhs ::lhs :sep #{'=>} :rhs ::rhs))

#?(:clj
   (defonce productions (atom {})))

#?(:clj
   (defn- resolve-spec-form
     [spec-name]
     (loop [s spec-name]
       (let [form (if (compiling-cljs?) (@cljs.spec.alpha/registry-ref s) (s/form spec-name))]
         (if (keyword? form) (recur form) form)))))

(defn- flatten-keys
  [keys]
  (mapcat #(cond
             (keyword? %) [%]
             (list? %) (flatten-keys (rest %)))
          keys))
#?(:clj
   (defn- spec->keys
     "Resolve a map spec and walk the defining forms to assemble the set of
      keys used in the spec. Throws if it cannot determine that the spec is
      defined by s/keys at via the forms."
     [spec-name]
     (let [composite-forms (if (compiling-cljs?)
                             #{'cljs.spec.alpha/or 'cljs.spec.alpha/merge}
                             #{'clojure.spec.alpha/or 'clojure.spec.alpha/merge})
           form (if (keyword? spec-name) (resolve-spec-form spec-name) spec-name)]
       (cond
         (composite-forms (first form))
         (reduce (fn [keys key] (clojure.set/union keys (spec->keys key))) #{} (rest form))

         (= (first form) (if (compiling-cljs?) 'cljs.spec.alpha/keys 'clojure.spec.alpha/keys))
         (set (mapcat (fn [[keys-type keys]]
                        (if (#{:req-un :opt-un} keys-type)
                          (map (comp keyword name)
                               (flatten-keys keys))
                          (flatten-keys keys)))
                      (->> form (drop 1) (partition 2))))

         :else
         (throw (ex-info
                  (str "Fact types must be spec'ed with s/keys: (s/def " spec-name " " (pr-str form) ")")
                  {:type (pr-str spec-name) :form (pr-str form)}))))))

#?(:clj
   (defn- add-args-to-constraint
     "Since fact types are map specs, use the keys of the associated spec in the
      constraint fact type to destructure the attributes for use as symbols in
      the constraint."
     [constraint]
     (if (vector? constraint)
       (let [[op & cs] constraint]
         (if (#{:and :or :not :exists} op)
           (into [op] (map add-args-to-constraint cs))))
       (let [{:keys [type constraints args] :as c} (or (:from constraint) constraint)]
        (if (or args (not (contains? c :type)))
          constraint
          (let [args [{:keys (vec (spec->keys type))}
                      #_(com/field-name->accessors-used (eval type) constraints)]]
            (assoc-in constraint (if (:from constraint) [:from :args] [:args]) args)))))))

#?(:clj
     (defn- add-args-to-lhs
       [lhs]
       (let [elhs (eval lhs)
             lhs' (vec
                    (for [constraint elhs]
                      (add-args-to-constraint constraint)))]
         lhs')))

#?(:clj
   (defn- add-args-to-production
     [production]
     (let [lhs (add-args-to-lhs (:lhs production))]
       (assoc production :lhs (list 'quote lhs)))))


#?(:clj
   (defn- update-name->productions
     [defs-name prods]
     (swap! productions assoc (symbol (name (ns-name *ns*)) (name defs-name)) prods)))

#?(:clj
   (defn- build-prods
     "Build productions data form from DSL."
     [defs-name defs opts build-fn]
     ; If opts exist, apply to all rule definitions in the group.
     (let [prods (into {} (map (fn [[name & def]]
                                 (try
                                   [name (add-args-to-production (build-fn name (if opts (cons opts def) def)))]
                                   (catch Exception e
                                     (throw (ex-info "Exception building production"
                                                     {:name name
                                                      :opts opts
                                                      :def def
                                                      :ex e})))))
                               defs))]
       (update-name->productions defs-name prods)
       prods)))

;;; TODO - spec rules/queries and validate to avoid obscure exceptions

#?(:clj
   (defn- names-unique
     "Verifies that names are unique within a group of definitions."
     [defs]
     (let [non-unique (->> defs
                           (group-by first)
                           (filter (fn [[k v]] (not= 1 (count v))))
                           (map first)
                           set)]
       (if (empty? non-unique)
         defs
         (throw (ex-info (str "Non-unique production names: " non-unique) {:names non-unique}))))))

#?(:clj
   (defmacro defrules
     "Define a group of rules. Each rule is defined in a vector where the first element
      is a keyword defining the rule name, followed by normal clara-rules syntax, using
      the spec name as the fact type in constraints. Example:
        (defrules my-rules
          [::first-rule
            [?fact1 <- ::fact-1-spec (= ?attr attr)]
            =>
            (println \"Fact 1 attr: \" attr)]
          [::second-rule
            ...])

      To use the rules when defining a session, use the full ns-qualified name of the
      group as a source."
     [rules-name & rules]
     (let [[opts rules] (if (map? (first rules))
                          [(first rules) (rest rules)]
                          [nil rules])]
       (doseq [rule (names-unique rules)]
         (if-let [e (s/explain-data ::rule rule)]
           (binding [*out* *err*]
             ;;; Pretty print error info here so it isn't mangled by the browser.
             (println (str "Rule in " rules-name " failed spec"))
             (pprint e)
             (throw (ex-info (str "Rule in " rules-name " failed spec") {:explanation (s/explain-str ::rule rule)})))))
       (let [prods (build-prods rules-name rules opts dsl/build-rule)]
         `(def ~rules-name ~prods)))))

#?(:clj
   (defmacro defqueries
     "Define a group of queries. Each query is defined in a vector where the first element
      is a keyword defining the rule name, followed by normal clara-rules syntax, using
      the spec name as the fact type in constraints. Example:
        (defqueries my-queries
          [::first-query
            [:?attr]
            [?fact1 <- ::fact-1-spec (= ?attr attr)]]
          [::second-query
            ...])

      To use the queries when defining a session, use the full ns-qualified name of the
      group as a source."
     [queries-name & queries]
     (doseq [query (names-unique queries)]
       (if-let [e (s/explain-data ::query query)]
         (binding [*out* *err*]
           ;;; Pretty print error info here so it isn't mangled by the browser.
           (println (str "Query in " queries-name " failed spec"))
           (pprint e)
           (throw (ex-info (str "Query in " queries-name " failed spec") {:explanation (s/explain-str ::query query)})))))
     (let [prods (build-prods queries-name queries nil dsl/build-query)]
       `(def ~queries-name ~prods))))

#?(:clj
   (defmacro defsession
     "Define a rules session, use defrules/defqueries groups as sources. Specify sources
      as a vector. Sources must be fully-qualified symbols which refer to productions grouped
      by defrules, defqueries, or defsession.

      Accepts any clara-rules session options as map, except for :fact-type-fn,
      which will always be set to provisdom.maali.rules/spec-type."
     ([name sources] `(defsession ~name ~sources {}))
     ([name sources options]
      (try
        (let [qualified-sources (map (fn [x]
                                       (if (compiling-cljs?)
                                         (ana/resolve-symbol x)
                                         (let [v (resolve x)
                                               m (meta v)
                                               n (clojure.core/name (:name m))
                                               ns (clojure.core/name (ns-name (:ns m)))]
                                           (symbol ns n))))
                                     sources)
              prods (set (apply concat (map @productions qualified-sources)))]
          (update-name->productions name prods)
          (if (compiling-cljs?)
            (let [prods (set (map eval (vals prods)))]
              `(def ~name ~(macros/productions->session-assembly-form prods (merge options {:fact-type-fn `spec-type}))))
            `(def ~name (com/mk-session* (com/add-production-load-order ~(mapv second prods)) ~(merge options {:fact-type-fn `spec-type})))))
        (catch Exception e
          ; Dump exception to *err* so we get all of the info when using figwheel or boot-cljs
          (binding [*out* *err*]
            (pprint e)
            (throw e)))))))

(defn- check-and-spec
  "Checks that facts conform to the specified spec, decorates fact
   maps with metadata containing"
  [spec facts]
  (let [form (@cljs.spec.alpha/registry-ref spec)]
    (when (= ::s/unknown form) (throw (ex-info (str "Unknown spec " (pr-str spec)) {:spec spec}))))
  (mapv #(spec-type % spec) facts))

(defn insert
  "Unconditionally insert facts with type of spec into session.
   \"spec\" should name a fact spec defined as some combination of
   s/keys specs."
  [session spec & facts]
  (rules/insert-all session (check-and-spec spec facts)))

(defn insert!
  "Conditionally insert facts with type of spec into current session context.
   \"spec\" should name a fact spec defined as some combination of
   s/keys specs."
  [spec & facts]
  (rules/insert-all! (check-and-spec spec facts)))

(defn insert-unconditional!
  "Unconditionally insert facts with type of spec into current session context.
   \"spec\" should name a fact spec defined as some combination of
   s/keys specs."
  [spec & facts]
  (rules/insert-all-unconditional! (check-and-spec spec facts)))

(defn retract
  "Retract facts with type of spec into session.
   \"spec\" should name a fact spec defined as some combination of
   s/keys specs."
  [session spec f & facts]
  (let [facts (check-and-spec spec (if (fn? f) (f session) (cons f facts)))]
    (apply rules/retract session facts)))

(defn retract!
  "Retract facts with type of spec into current session context.
   \"spec\" should name a fact spec defined as some combination of
   s/keys specs."
  [spec & facts]
  (apply rules/retract! (check-and-spec spec facts)))

(defn upsert
  "For session, retracts old-fact (if not nil)
   and unconditionally inserts a fact created by applying the supplied
   function and arguments to old-fact."
  [session spec old-fact f & args]
  (let [s (if old-fact
            (retract session spec old-fact)
            session)]
    (when-let [new-fact (apply f old-fact args)]
      (insert s spec new-fact))))

(defn upsert!
  "Within the current session context, retracts old-fact (if not nil)
   and unconditionally inserts a fact created by applying the supplied
   function and arguments to old-fact."
  [spec old-fact f & args]
  (when old-fact
    (retract! spec old-fact))
  (when-let [new-fact (apply f old-fact args)]
    (insert-unconditional! spec new-fact)))

(defn upsert-seq!
  "Within the current session context, calls upsert! for each fact
   in old-fact-seg."
  [spec old-fact-seq f & args]
  (doseq [old-fact old-fact-seq]
    (apply upsert! spec old-fact f args)))

(defn fire-rules
  "Fires rules for the session."
  [session]
  (rules/fire-rules session))

(defn query
  "Retrieves results for the specified query from session."
  [session query & params]
  (apply rules/query session query params))

(defn query-partial
  "Retrieves results for a query that where args can be only a partial match.
   args should be a subset of parameters defined in query."
  [session query & args]
  (let [{:keys [memory rulebase]} (eng/components session)
        query-node (get-in rulebase [:query-nodes query])
        tokens (mem/get-tokens-all memory query-node)
        args-map (apply hash-map args)
        matching-tokens (filter #(= args-map (select-keys % (keys args-map))) (map :bindings tokens))]
    matching-tokens))

;;; Some query boilerplate functions
(defn query-many
  [map-fn session query & args]
  (mapv map-fn (apply rules/query session query args)))

(defn query-one
  [map-fn session query & args]
  (-> (apply rules/query session query args) first map-fn))

(defn query-fn
  "Returns a function that will apply a query and map the results via map-fn."
  [query map-fn & args]
  (fn [session]
    (mapv map-fn (apply rules/query session query args))))

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
