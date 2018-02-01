;;; CLJS version of pprint that preserves namespace-aliases for map prefixes.
;;; So if you have aliased my.really.long.namespace.name to my-ns, when pprint-ing
;;; maps, the map prefix will just be #::my-ns rather than the long form. This
;;; helps with readability, particularly by keeping sane the indentation level
;;; of nested maps.

(ns provisdom.maali.pprint
  #?(:cljs
     (:require-macros
       [cljs.pprint :as m :refer [pprint-logical-block print-length-loop]]))
  (:require [cljs.pprint :as pp]
    #?(:clj [cljs.analyzer :as ana])))

#?(:cljs (def ^:dynamic *ns-aliases* {}))

#?(:cljs
   (defn- use-method
     "Installs a function as a new method of multimethod associated with dispatch-value. "
     [multifn dispatch-val func]
     (-add-method multifn dispatch-val func)))

#?(:cljs
   (defn- pprint-map [amap]
     (let [[ns lift-map] (when (not (record? amap))
                           (#'cljs.core/lift-ns amap))
           amap (or lift-map amap)
           prefix (if ns (str "#:" (or (*ns-aliases* (symbol ns)) ns) "{") "{")]
       (pprint-logical-block :prefix prefix :suffix "}"
                                (print-length-loop [aseq (seq amap)]
                                                      (when aseq
                                                        ;;compiler gets confused with nested macro if it isn't namespaced
                                                        ;;it tries to use clojure.pprint/pprint-logical-block for some reason
                                                        (m/pprint-logical-block
                                                          (pp/write-out (ffirst aseq))
                                                          (-write *out* " ")
                                                          (pp/pprint-newline :linear)
                                                          (set! pp/*current-length* 0) ;always print both parts of the [k v] pair
                                                          (pp/write-out (fnext (first aseq))))
                                                        (when (next aseq)
                                                          (-write *out* ", ")
                                                          (pp/pprint-newline :linear)
                                                          (recur (next aseq)))))))))

#?(:cljs (use-method pp/simple-dispatch :map pprint-map))

#?(:clj
   (defn cljs-find-ns
     "Returns the cljs-namespace as a symbol for the given symbol or string,
     or nil if no cljs-namespace can be found."
     [n]
     (let [s (symbol n)] (when (@ana/namespaces s) s))))

#?(:clj
   (defn cljs-namespace?
     "Predicate that returns true if the given string or symbol refers
     to an existing cljs-namespace."
     [s]
     (if (cljs-find-ns s) true false)))

#?(:clj
   (defn cljs-ns-aliases
     "Returns a map of the namespace-aliases defined in the cljs-namespace."
     ([] (cljs-ns-aliases ana/*cljs-ns*))
     ([a-ns]
      (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
        (into (sorted-map)
              ;; filter out the trivial aliases-entries where the key equals the val
              (filter (fn [e] (not= (key e) (val e)))
                      (get-in @ana/namespaces [a-ns :requires])))))))

#?(:clj
   (defmacro pprint
     [x]
     `(binding [*ns-aliases* (quote ~(clojure.set/map-invert (cljs-ns-aliases)))]
        (cljs.pprint/pprint ~x))))