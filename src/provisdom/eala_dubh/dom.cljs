(ns provisdom.eala-dubh.dom
  (:require-macros [clara.rules :refer [defrule defquery defsession]])
  (:require [incremental-dom :as inc]
            [clara.rules :refer [insert retract fire-rules query insert! retract!]]
            [provisdom.eala-dubh.session :as session]
            [clojure.string :as str]))

(def ^{:doc     "Regular expression that parses a CSS-style id and class from an element name."
       :private true}
 re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn as-str [input]
  (cond
    (keyword? input) (name input)
    (symbol? input) (str input)
    (string? input) input
    :else nil))                                             ;; TODO: throw exception

(defn- merge-attributes [{:keys [id class]} map-attrs]
  (->> map-attrs
       (merge (if id {:id id}))
       (merge-with #(if %1 (str %1 " " %2) %2)
                   (if class {:class class}))))

(defn normalize-attributes [attrs]
  (into {} (->> attrs
                (filter second)
                (map (juxt (comp as-str first) second)))))

(defn normalize-elem
  [[tag & content]]
  (let [[tag id class] (if (fn? tag)
                         [tag nil nil]
                         (rest (re-matches re-tag (as-str tag))))
        tag-attrs {:id    id
                   :class (if class (.replace ^String class "." " "))}
        map-attrs (first content)]
    (if (map? map-attrs)
      [tag
       (normalize-attributes (merge-attributes tag-attrs (first content)))
       (rest content)]
      [tag (normalize-attributes tag-attrs) content])))

(defn normalize-hiccup [hiccup]
  (cond
    (seq? hiccup)
    (mapcat normalize-hiccup hiccup)

    (and (vector? hiccup) (fn? (first hiccup)))
    (let [[fn & args] hiccup]
        (normalize-hiccup (apply fn args)))

    (vector? hiccup)
    (let [[tag attrs content] (normalize-elem hiccup)]
      (list [tag attrs (mapcat normalize-hiccup content)]))

    :else (list hiccup)))

(defn- element-open
  [tag {key "key" :as attrs}]
  (apply inc/elementOpen (concat [tag
                                  (or key "")
                                  []]
                                 (mapcat identity attrs))))

(defn- element-void
  [tag {key "key" :as attrs}]
  (apply inc/elementVoid (concat [tag
                                  (or key "")
                                  []]
                                 (mapcat identity attrs))))

(defn- element-close
  [tag]
  (inc/elementClose tag))

(defn- text
  [text]
  (inc/text text))

(defn render-element
  [elem]
  (cond
    (string? elem)
    (text elem)

    (seq? elem)
    (doseq [item elem]
      (render-element item))

    (and (vector? elem) (fn? (first elem)))
    (let [[fn & args] elem]
      (render-element (normalize-hiccup (apply fn args))))

    (vector? elem)
    (let [[tag attrs content] elem
          content (if (map? attrs) content attrs)]
      (element-open tag attrs)
      (render-element content)
      (element-close tag))

    :else (text elem)))

(defn patch
  [node hiccup]
  #_(println (normalize-hiccup hiccup))
  (cond
    (string? node) (inc/patch (.getElementById js/document node) #(-> hiccup normalize-hiccup render-element) nil)
    :else (inc/patch node #(-> hiccup normalize-hiccup render-element) nil)))
