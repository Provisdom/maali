(ns provisdom.conduit.rules
  (:require [clojure.spec.alpha :as s]
            [cljs.core.async :as async]
            [lambdaisland.uniontypes #?(:clj :refer :cljs :refer-macros) [case-of]]
            [provisdom.conduit.specs :as specs]
            [provisdom.conduit.effects :as effects]
            [provisdom.conduit.view :as view]
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
   [:not [::specs/Response (= ?request request)]]
   =>
   (rules/insert! ::specs/Loading #::specs{:section ?section})]

  ;;; Clean up responses so they don't leak memory.
  [::retracted-request!
   [?response <- ::specs/Response (= ?request request)]
   [:not [::specs/Request (= ?request request)]]
   =>
   (rules/retract! ::specs/Response ?response)])

(defn make-articles-request
  [filter token]
  {:method  :get
   :uri     (if (::specs/feed filter) (endpoint "articles" "feed") (endpoint "articles"))
   :params  (-> filter
                (assoc :author (::specs/username filter))
                (dissoc ::specs/username))
   :headers (auth-header token)})

(defrules home-page-rules
  [::tags-request!
   [::specs/ActivePage (= :home page)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method :get
                  :uri    (endpoint "tags")}]
     (rules/insert! ::specs/Request #::specs{:request-type :tags
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::tags-response!
   [::specs/Request (= :tags request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Tag (map (fn [tag] {::specs/tag tag}) (:tags ?response)))]

  [::articles-request!
   [::specs/ActivePage (= :home (specs/page-name page))]
   [?filter <- ::specs/Filter (= ?feed feed)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request (make-articles-request ?filter ?token)]
     (rules/insert! ::specs/Request #::specs{:request-type :articles
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::articles-response!
   [::specs/Request (= :articles request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/ArticleCount {::specs/count (:articlesCount ?response)})
   (apply rules/insert! ::specs/Article (:articles ?response))])

(defrules article-page-rules
  [::article-request!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [article-request {:method  :get
                          :uri     (endpoint "articles" ?slug) ;; evaluates to "api/articles/:slug"
                          :headers (auth-header ?token)}
         comments-request {:method  :get
                           :uri     (endpoint "articles" ?slug "comments")
                           :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :article
                                             :request      article-request})
     (effects/http-effect ?command-ch article-request)
     (rules/insert! ::specs/Request #::specs{:request-type :comments
                                             :request      comments-request})
     (effects/http-effect ?command-ch comments-request))]

  [::article-response!
   [::specs/Request (= :article request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Article (:article ?response))]

  [::comments-response!
   [::specs/Request (= :comments request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Comment (:comments ?response))]

  ;;; TODO - do we need ::specs/CanEdit?
  [::can-edit-article!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?article <- ::specs/Article (= ?slug slug) (= ?author (:username author))]
   [::specs/User (= ?username username)]
   =>
   (rules/upsert! ?article (assoc ?article ::specs/can-edit (= ?author ?username)))]

  [::can-edit-comment!
   [::specs/ActivePage (= :article (specs/page-name page))]
   [?comment <- ::specs/Comment (= ?author (:username author))]
   [::specs/User (= ?username username)]
   =>
   (rules/upsert! ?comment (assoc ?comment ::specs/can-edit (= ?author ?username)))]
  )

(defrules article-edit-rules
  [::new-article!
   [::specs/ActivePage (= :editor (specs/page-name page))]
   [?new-article <- ::specs/NewArticle]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :post
                  :uri     (endpoint "articles" )
                  :params  {:article ?new-article}
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :editor
                                             :request-data ?new-article
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::update-article!
   [::specs/ActivePage (= :editor (specs/page-name page))]
   [?updated-article <- ::specs/UpdatedArticle (= ?slug slug)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :put
                  :uri     (endpoint "articles" ?slug)
                  :params  {:article ?updated-article}
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :editor
                                             :request-data ?updated-article
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::delete-article!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?deleted-article <- ::specs/DeletedArticle (= ?slug slug)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :delete
                  :uri     (endpoint "articles" ?slug)
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :editor
                                             :request-data ?deleted-article
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::edit-article-response!
   [::specs/Request (= :editor request-type) (= ?request request) (= ?article-edit request-data)]
   [::specs/Response (= ?request request) (= ?response response)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (lambdaisland.uniontypes/case-of ::specs/ArticleEdit ?article-edit
            ::specs/NewArticle _
            (do
              (async/put! ?command-ch [:page {::specs/slug (-> ?response :article :slug)}])
              (rules/retract! ::specs/NewArticle ?article-edit))

            ::specs/UpdatedArticle _
            (do
              (async/put! ?command-ch [:page {::specs/slug (-> ?response :article :slug)}])
              (rules/retract! ::specs/UpdatedArticle ?article-edit))

            ::specs/DeletedArticle _
            (do
              (async/put! ?command-ch [:page :home])
              (rules/retract! ::specs/DeletedArticle ?article-edit)))])

(defrules comment-edit-rules
  [::new-comment!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?new-comment <- ::specs/NewComment]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :post
                  :uri     (endpoint "articles" ?slug "comments")
                  :params  {:comment ?new-comment}
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :comments
                                             :request-data ?new-comment
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::delete-comment!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?deleted-comment <- ::specs/DeletedComment (= ?id id)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :delete
                  :uri     (endpoint "articles" ?slug "comments" ?id)
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :comments
                                             :request-data ?deleted-comment
                                             :request      request})
     (effects/http-effect ?command-ch request))]

  [::new-comment-response!
   [::specs/Request (= :comments request-type) (= ?request request) (= ?new-comment request-data)]
   [::specs/Response (= ?request request) (= ?response response)]
   [?new-comment <- ::specs/NewComment]
   =>
   (rules/insert! ::specs/Comment (:comment ?response))
   (rules/retract! ::specs/NewComment ?new-comment)]

  [::delete-comment-response!
   [::specs/Request (= :comments request-type) (= ?request request) (= ?deleted-comment request-data)]
   [::specs/Response (= ?request request) (= ?response response)]
   [?deleted-comment <- ::specs/NewComment (= ?id id)]
   [?comment <- ::specs/Comment (= ?id id)]
   =>
   (rules/retract! ::specs/Comment ?comment)
   (rules/retract! ::specs/DeletedComment ?deleted-comment)])

(defrules profile-page-rules
  [::profile!
   [::specs/ActivePage (= :profile (specs/page-name page)) (= ?username (::specs/username page))]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [articles-request (make-articles-request {::specs/username ?username} ?token)
         profile-request {:method  :get
                          :uri     (endpoint "profiles" ?username) ;; evaluates to "api/profiles/:profile"
                          :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :articles
                                             :request      articles-request})
     (effects/http-effect ?command-ch articles-request)
     (rules/insert! ::specs/Request #::specs{:request-type :profile
                                             :request      profile-request})
     (effects/http-effect ?command-ch profile-request))]

  [::profile-response!
   [::specs/Request (= :profile request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Profile (:profile ?response))])

(defrules view-update-rules
  [::render-active-page!
   [?active-page <- ::specs/ActivePage (= ?page page)]
   =>
   (view/render :page ?page)]

  [::render-loading!
   [?loading <- (acc/all) :from [::specs/Loading]]
   =>
   (view/render :loading (mapv ::specs/section ?loading))]

  [::render-articles!
   [::specs/ActivePage (= :home (specs/page-name page))]
   [?articles <- (acc/all) :from [::specs/Article]]
   [?tags <- (acc/all) :from [::specs/Tag]]
   [::specs/ArticleCount (= ?count count)]
   =>
   (view/render :articles ?articles)
   (view/render :tags (mapv ::specs/tag ?tags))
   (view/render :article-count ?count)]

  [::render-article!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?article <- ::specs/Article (= ?slug slug)]
   [?comments <- (acc/all) :from [::specs/Comment]]
   =>
   (view/render :article ?article)
   (view/render :comments ?comments)]

  [::render-profile!
   [::specs/ActivePage (= :profile (specs/page-name page))]
   [?articles <- (acc/all) :from [::specs/Article]]
   [?profile <- ::specs/Profile]
   =>
   (view/render :profile ?profile)
   (view/render :articles ?articles)])

#_(enable-console-print!)
#_(cljs.pprint/pprint rules)

(defqueries queries
  [::active-page [] [?active-page <- ::specs/ActivePage (= ?page page)]])