(ns provisdom.conduit.rules
  (:require [clojure.spec.alpha :as s]
            [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries] :as rules]
            [clara.rules.accumulators :as acc]
            [clojure.string :as str]))

(def api-url "https://conduit.productionready.io/api")

(defn endpoint [& params]
  "Concat any params to api-url separated by /"
  (str/join "/" (concat [api-url] params)))

(defn auth-header
  [token]
  "Get user token and format for API authorization"
  (when token
    [:Authorization (str "Token " token)]))

(defrules rules
  [::request!
   [::specs/Request (= ?section request-type) (= ?request request)]
   [::specs/Pending (= ?request request)]
   =>
   (rules/insert! ::specs/Loading {::specs/section ?section})]

  [::response!
   [::specs/Response (= ?request request)]
   [::specs/Pending (= ?request request)]
   =>
   (rules/retract! ::specs/Pending {::specs/request ?request})]

  [::cancelled-request!
   [?response <- ::specs/Response (= ?request request)]
   [?pending <- ::specs/Pending (= ?request request)]
   [:not [::specs/Request (= ?request request)]]
   =>
   (rules/retract! ::specs/Pending ?pending)
   (rules/retract! ::specs/Response ?response)]

 [::tags-request!
   [::specs/ActivePage (= :home page)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :tags
                                           :request      {:method :get
                                                          :uri    (endpoint "tags")}})]

  [::tags-response!
   [::specs/Request (= :tags request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Tag (map (fn [tag] {::specs/tag tag}) (:tags ?response)))]

  [::articles-request!
   [?filter <- ::specs/Filter (= ?feed feed)]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :articles
                                           :request      {:method  :get
                                                          :uri     (if ?feed (endpoint "articles" "feed") (endpoint "articles"))
                                                          :params  ?filter
                                                          :headers (auth-header ?token)}})]

  [::articles-response!
   [::specs/Request (= :articles request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/ArticleCount {::specs/count (:articlesCount ?response)})
   (apply rules/insert! ::specs/Article (:articles ?response))]

  [::active-article!
   [::specs/ActivePage (= ?page page) (= ?slug (::specs/slug page))]
   =>
   (when ?slug
     (rules/insert! ::specs/ActiveArticle {::specs/slug ?slug}))]

  [::comments-request!
   [::specs/ActiveArticle (= ?slug slug)]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :comments
                                           :request      {:method  :get
                                                          :uri     (endpoint "articles" ?slug "comments")
                                                          :headers (auth-header ?token)}})]

  [::comments-response!
   [::specs/Request (= :comments request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Comment (:comments ?response))])

#_(enable-console-print!)
#_(cljs.pprint/pprint rules)

(defqueries queries
  [::active-page [] [?active-page <- ::specs/ActivePage (= ?page page)]]
  [::request []
   [::specs/Request (= ?request request)]
   [:not [::specs/Pending (= ?request request)]]
   [:not [::specs/Response (= ?request request)]]]
  [::loading [] [::specs/Loading (= ?section section)]]
  [::articles [] [?article <- ::specs/Article]]
  [::article-count [] [::specs/ArticleCount (= ?count count)]]
  [::active-article [] [::specs/ActiveArticle (= ?slug slug)] [?article <- ::specs/Article (= ?slug slug)]]
  [::comments [] [?comment <- ::specs/Comment]]
  [::tags [] [::specs/Tag (= ?tag tag)]]
  #_[::profile [] [?profile <- ::specs/Profile]]
  #_[::filter [] [?filter <- ::specs/Filter]]
  #_[::errors [] [::specs/Error (= ?error error)]]
  #_[::user [] [?user <- ::specs/User]]
  #_[::token [] [::specs/User (= ?token token)]]
  #_[::hash [] [::specs/Hash (= ?hash hash)]]
  )

(s/def ::?token ::specs/token)
(s/def ::?filter ::specs/Filter)
(s/def ::?article ::specs/Article)
(s/def ::?comment ::specs/Comment)
(s/def ::?count ::specs/count)
(s/def ::?slug ::specs/slug)
(s/def ::?page ::specs/page)
(s/def ::?section ::specs/section)
(s/def ::?request ::specs/request)
(s/def ::?tag ::specs/tag)

(s/def ::active-page (s/cat :query #{::active-page} :result (s/coll-of (s/keys :req-un [::?page]))))

(s/def ::loading (s/cat :query #{::loading} :result (s/coll-of (s/keys :req-un [::?section]))))
(s/def ::request (s/cat :query #{::request} :result (s/coll-of (s/keys :req-un [::?request]))))

(s/def ::tags (s/cat :query #{::tags} :result (s/coll-of (s/keys :req-un [::?tag]))))

(s/def ::articles (s/cat :query #{::articles} :result (s/coll-of (s/keys :req-un [::?article]))))
(s/def ::article-count (s/cat :query #{::article-count} :result (s/coll-of (s/keys :req-un [::?count]))))
(s/def ::active-article (s/cat :query #{::active-article} :result (s/coll-of (s/keys :req-un [::?article]))))

(s/def ::comments (s/cat :query #{::comments} :result (s/coll-of (s/keys :req-un [::?comment]))))

(s/def ::query-result (s/or ::active-page ::active-page
                            ::loading ::loading
                            ::request ::request
                            ::tags ::tags
                            ::articles ::articles
                            ::article-count ::article-count
                            ::active-article ::active-article
                            ::comments ::comments))