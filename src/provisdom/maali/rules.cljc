(ns provisdom.maali.rules
  (:require [clara.rules :as rules]
            [clara.rules.engine]
            [#?(:clj clojure.pprint :cljs cljs.pprint) :refer [pprint]]
    #?(:clj
            [clara.macros :as macros])
    #?(:clj
            [clara.rules.compiler :as com])
    #?(:clj
            [clojure.spec.alpha :as s])
    #?(:cljs [cljs.spec.alpha :as s]))
  #?(:clj
     (:import [clara.rules.engine LocalSession])))

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
   (defmacro rel-ns [k]
     `(alias ~k (create-ns (symbol (str ns "." (str ~k)))))))

(defn session?
  [x]
  (instance? clara.rules.engine.LocalSession x))

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
        ~'provisdom.maali.rules/TypeInfo
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
   (defn add-args-to-constraint
     [constraint]
     (let [{:keys [type constraints args] :as c} (or (:from constraint) constraint)]
       (if (or args (not (contains? c :type)))
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
           (assoc-in constraint (if (:from constraint) [:from :args] [:args]) args))))
     ))

#?(:clj
   (defn add-args-to-production
     [production]
     (let [lhs (:lhs production)]
       (let [elhs (eval lhs)
             lhs' (vec
                    (for [constraint elhs]
                      (if (vector? constraint)
                        (let [[op & cs] constraint]
                          (if (#{:and :or :not} op)
                            (into [op] (map add-args-to-constraint cs))))
                        (add-args-to-constraint constraint))))]
         (assoc production :lhs (list 'quote lhs'))))))

#?(:clj
   (defn build-prods
     [defs-name defs build-fn]
     (let [prods (into {} (map (fn [[name & def]] [name (add-args-to-production (apply build-fn name def))])) defs)]
       (swap! productions assoc (symbol (name (ns-name *ns*)) (name defs-name)) (into {} (map (fn [[k v]] [k (eval v)])) prods))
       prods)))

#?(:clj
   (defmacro defrules
     [rules-name & rules]
     (let [prods (build-prods rules-name rules macros/build-rule)]
       `(def ~rules-name ~prods))))

#?(:clj
   (defmacro defqueries
     [queries-name & queries]
     (let [prods (build-prods queries-name queries macros/build-query)]
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
       `(def ~name ~(macros/productions->session-assembly-form prods options)))))

(defn check-and-spec
  [spec facts]
  (let [form (@cljs.spec.alpha/registry-ref spec)]
    (when (= ::s/unknown form) (throw (ex-info (str "Unknown spec " spec) {:spec spec}))))
  (mapv #(if-let [e (s/explain-data spec %)]
           (do
             (pprint e)
             (throw (ex-info (str "Fact failed spec " spec) {:fact % :explanation e})))
           (spec-type % spec))
        facts))

(defn insert
  [session spec & facts]
  (rules/insert-all session (check-and-spec spec facts)))

(defn insert!
  [spec & facts]
  (rules/insert-all! (check-and-spec spec facts)))

(defn insert-unconditional!
  [spec & facts]
  (rules/insert-all-unconditional! (check-and-spec spec facts)))

(defn retract
  [session spec f & facts]
  (let [facts (check-and-spec spec (if (fn? f) (f session) (cons f facts)))]
    (apply rules/retract session facts)))

(defn retract!
  [spec & facts]
  (apply rules/retract! (check-and-spec spec facts)))

(defn upsert
  [session spec old-fact new-fact]
  (cond-> session
          old-fact (retract spec old-fact)
          new-fact (insert spec new-fact)))

(defn upsert-q
  [session spec query-fn f & args]
  (let [items (query-fn session)
        new-items (when f (map #(apply f % args) items))
        s (if (not-empty items) (apply retract session spec items) session)
        s' (if (not-empty new-items) (apply insert s spec new-items) (apply insert s spec [(apply f nil args)]))]
    s'))

(defn upsert!
  ([spec old-fact new-fact]
   (when old-fact
     (retract! spec old-fact))
   (when new-fact
     (insert! spec new-fact))))

(defn upsert-f!
  [spec fact f & args]
  (retract! spec fact)
  (insert! spec (apply f fact args)))

(defn upsert-unconditional!
  ([spec fact]
   (insert! spec fact))
  ([spec fact f & args]
   (retract! fact)
   (insert-unconditional! spec (apply f fact args))))

(defn fire-rules
  [session]
  (rules/fire-rules session))

(defn query
  [session query & params]
  (apply rules/query session query params))

(defn query-fn
  "Returns a function that will apply a query and map the results via map-fn."
  [query map-fn & args]
  (fn [session]
    (mapv map-fn (apply rules/query session query args))))