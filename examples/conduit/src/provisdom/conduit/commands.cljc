(ns provisdom.conduit.commands
  (:require [clojure.spec.alpha :as s]
            [lambdaisland.uniontypes #?(:clj :refer :cljs :refer-macros) [case-of]]
            [net.cgrand.xforms :as xforms]
            [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.conduit.rules :as todo]
            [provisdom.maali.listeners :as listeners]
            [clojure.string :as str]))

;;; Model command specs
(s/def ::init (s/cat :command #{:init} :init-session rules/session?))
(s/def ::insert (s/cat :command #{:insert} :spec ::specs/singletons :value ::specs/singleton-value))
(s/def ::retract (s/cat :command #{:retract} :spec ::specs/singletons :value ::specs/singleton-value))
(s/def ::command (s/or ::init ::init
                       ::insert ::insert
                       ::retract ::retract))
;;; Reduction function to update clara session state
#_(defn handle-state-command
  [session command]
  (case-of ::command command))

;;; Effects commands
(s/def ::get-articles (s/cat :command #{:get-articles} :filter (s/keys :opt [::specs/username ::specs/favorited])))
(s/def ::get-feed-articles (s/cat :command #{:get-feed-articles}))
(s/def ::get-tags (s/cat :command #{:get-tags}))
(s/def ::get-article-comments (s/cat :command #{:get-article-comments} :slug ::specs/slug))
(s/def ::get-user-profile (s/cat :command #{:get-user-profile} :username ::specs/username))

(def api-url "https://conduit.productionready.io/api")

(defn endpoint [& params]
  "Concat any params to api-url separated by /"
  (str/join "/" (concat [api-url] params)))

(defn auth-header [session]
  "Get user token and format for API authorization"
  (let [token (first (rules/query-fn ::rules/token :?token))]
    (if token
      [:Authorization (str "Token " token)]
      nil)))

