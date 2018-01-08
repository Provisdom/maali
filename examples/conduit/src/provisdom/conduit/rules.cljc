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

(defrules http-handling-rules
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

  ;;; Clean up responses so they don't leak memory
  [::retracted-request!
   [?response <- ::specs/Response (= ?request request)]
   [:not [::specs/Request (= ?request request)]]
   =>
   (rules/retract! ::specs/Response ?response)]

  [::cancelled-request!
   [?response <- ::specs/Response (= ?request request)]
   [?pending <- ::specs/Pending (= ?request request)]
   [:not [::specs/Request (= ?request request)]]
   =>
   (rules/retract! ::specs/Pending ?pending)
   (rules/retract! ::specs/Response ?response)])

(defn articles-request
  [filter token]
  {:method  :get
   :uri     (if (::feed filter) (endpoint "articles" "feed") (endpoint "articles"))
   :params  (-> filter
                (assoc :author (::specs/username filter))
                (dissoc ::specs/username))
   :headers (auth-header token)})

(defrules home-page-rules
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
   [::specs/ActivePage (= :home (specs/page-name page))]
   [?filter <- ::specs/Filter (= ?feed feed)]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :articles
                                           :request      (articles-request ?filter ?token)})]

  [::articles-response!
   [::specs/Request (= :articles request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/ArticleCount {::specs/count (:articlesCount ?response)})
   (apply rules/insert! ::specs/Article (:articles ?response))])

(defrules article-page-rules
  [::active-article!
   [::specs/ActivePage (= ?page page) (= ?slug (::specs/slug page))]
   =>
   (when ?slug
     (rules/insert! ::specs/ActiveArticle {::specs/slug ?slug}))]

  [::article-request!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :article
                                           :request      {:method  :get
                                                          :uri     (endpoint "articles" ?slug) ;; evaluates to "api/articles/:slug"
                                                          :headers (auth-header ?token)}})
   (rules/insert! ::specs/Request #::specs{:request-type :comments
                                           :request      {:method  :get
                                                          :uri     (endpoint "articles" ?slug "comments")
                                                          :headers (auth-header ?token)}})]

  [::article-response!
   [::specs/Request (= :article request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Article (:article ?response))]

  [::comments-response!
   [::specs/Request (= :comments request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Comment (:comments ?response))])

(defrules comment-edit-rules
  [::new-comment!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?new-comment <- ::specs/NewComment (= ?body body)]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :comments
                                           :request-data ?new-comment
                                           :request      {:method  :post
                                                          :uri     (endpoint "articles" ?slug "comments")
                                                          :params  {:comment {:body ?body}}
                                                          :headers (auth-header ?token)}})]

  [::deleted-comment!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?deleted-comment <- ::specs/DeletedComment (= ?id id)]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :comments
                                           :request-data ?deleted-comment
                                           :request      {:method  :delete
                                                          :uri     (endpoint "articles" ?slug "comments" ?id)
                                                          :headers (auth-header ?token)}})]
  [::new-comment-response!
   [::specs/Request (= :comments request-type) (= ?request request) (= ?new-comment request-data)]
   [::specs/Response (= ?request request) (= ?response response)]
   [?new-comment <- ::specs/NewComment]
   =>
   (rules/insert! ::specs/Comment (:comment ?response))]

  [::deleted-comment-response!
   [::specs/Request (= :comments request-type) (= ?request request) (= ?deleted-comment request-data)]
   [::specs/Response (= ?request request) (= ?response response)]
   [?deleted-comment <- ::specs/DeletedComment (= ?id id)]
   [?comment <- ::specs/Comment (= ?id id)]
   =>
   (rules/retract! ::specs/Comment ?comment)]

  [::remove-comment-edits
   [:not [::specs/ActivePage (= :article (specs/page-name page))]]
   [:or [?comment-edit <- ::specs/NewComment] [?comment-edit <- ::specs/DeletedComment]]
   =>
   (condp = (rules/spec-type ?comment-edit)
     ::specs/NewComment (rules/retract! ::specs/NewComment ?comment-edit)
     ::specs/DeletedComment (rules/retract! ::specs/DeletedComment ?comment-edit))])

(defrules profile-page-rules
  [::profile!
   [::specs/ActivePage (= :profile (specs/page-name page)) (= ?username (::specs/username page))]
   [::specs/Token (= ?token token)]
   =>
   (rules/insert! ::specs/Request #::specs{:request-type :articles
                                           :request      (articles-request {::specs/username ?username} ?token)})
   (rules/insert! ::specs/Request #::specs{:request-type :profile
                                           :request      {:method          :get
                                                          :uri             (endpoint "profiles" ?username)     ;; evaluates to "api/profiles/:profile"
                                                          :headers         (auth-header ?token)}})]

  [::profile-response!
   [::specs/Request (= :profile request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Profile (:profile ?response))])

#_(enable-console-print!)
#_(cljs.pprint/pprint rules)

(defqueries queries
  [::active-page [] [?active-page <- ::specs/ActivePage (= ?page page)]]
  [::request []
   [::specs/Request (= ?request request)]
   [:not [::specs/Pending (= ?request request)]]
   [:not [::specs/Response (= ?request request)]]]
  [::loading [] [::specs/Loading (= ?section section)]]
  [::articles [] [:or [::specs/ActivePage (= :home (specs/page-name page))] [::specs/ActivePage (= :profile (specs/page-name page))]] [?article <- ::specs/Article]]
  [::article-count [] [::specs/ArticleCount (= ?count count)]]
  [::active-article [] [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))] [?article <- ::specs/Article (= ?slug slug)]]
  [::comments [] [?comment <- ::specs/Comment]]
  [::tags [] [::specs/Tag (= ?tag tag)]]
  [::profile [] [?profile <- ::specs/Profile]]
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
(s/def ::?profile ::specs/Profile)

(s/def ::active-page (s/cat :query #{::active-page} :result (s/coll-of (s/keys :req-un [::?page]))))

(s/def ::loading (s/cat :query #{::loading} :result (s/coll-of (s/keys :req-un [::?section]))))
(s/def ::request (s/cat :query #{::request} :result (s/coll-of (s/keys :req-un [::?request]))))

(s/def ::tags (s/cat :query #{::tags} :result (s/coll-of (s/keys :req-un [::?tag]))))

(s/def ::articles (s/cat :query #{::articles} :result (s/coll-of (s/keys :req-un [::?article]))))
(s/def ::article-count (s/cat :query #{::article-count} :result (s/coll-of (s/keys :req-un [::?count]))))
(s/def ::active-article (s/cat :query #{::active-article} :result (s/coll-of (s/keys :req-un [::?article]))))

(s/def ::comments (s/cat :query #{::comments} :result (s/coll-of (s/keys :req-un [::?comment]))))

(s/def ::profile (s/cat :query #{::profile} :result (s/coll-of (s/keys :req-un [::?profile]))))

(s/def ::query-result (s/or ::active-page ::active-page
                            ::loading ::loading
                            ::request ::request
                            ::tags ::tags
                            ::articles ::articles
                            ::article-count ::article-count
                            ::active-article ::active-article
                            ::profile ::profile
                            ::comments ::comments))