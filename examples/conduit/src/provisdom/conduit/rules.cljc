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

(def token-key "provisdom.conduit/jwtToken")

(defn endpoint [& params]
  "Concat any params to api-url separated by /"
  (str/join "/" (concat [api-url] params)))

(defn auth-header
  [token]
  "Get user token and format for API authorization"
  (when token
    [:Authorization (str "Token " token)]))

(defrules request-response-rules
  [::request!
   [?request <- ::specs/Request (= ?type type) (= :server target)]
   [:test (specs/section ?type)]
   [:not [::specs/Response (= ?request request)]]
   =>
   (rules/insert! ::specs/Loading #::specs{:section ?type})]

  ;;; Clean up responses so they don't leak memory.
  [::retracted-request!
   [?response <- ::specs/Response (= ?request request)]
   [:not [?request <- ::specs/Request]]
   =>
   (println ::retracted-request!)
   (rules/retract! ::specs/Response ?response)]

  ;;; If we get multiple responses to the same request, only keep the newest
  [::retract-older-response!
   [?old <- ::specs/Response (= ?request request) (= ?old-time time)]
   [?new <- ::specs/Response (= ?request request) (= ?new-time time)]
   [:test (and (> ?new-time ?old-time))]
   =>
   (println ::retract-older-response!)
   (rules/retract! ::specs/Response ?old)])

(defn make-articles-request
  [filter token]
  {:method              :get
   :uri                 (if (::specs/feed filter) (endpoint "articles" "feed") (endpoint "articles"))
   :params              (cond-> filter
                                (::specs/username filter) (assoc :author (::specs/username filter))
                                true (dissoc ::specs/username)
                                (::specs/favorited-user filter) (assoc :favorited (::specs/favorited-user filter))
                                true (dissoc ::specs/favorited-user))
   :headers             (auth-header token)
   ::specs/type :articles
   ::specs/target :server
   ::specs/time         (specs/now)})

(defrules home-page-rules
  [::articles-request!
   [::specs/ActivePage (= :home (specs/page-name page)) (= ?filter page)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [tags-request {:method              :get
                       :uri                 (endpoint "tags")
                       ::specs/type :tags
                       ::specs/target :server
                       ::specs/time         (specs/now)}
         articles-request (make-articles-request ?filter ?token)]
     (rules/insert! ::specs/Request tags-request)
     (effects/http-effect ?command-ch tags-request)
     (rules/insert! ::specs/Request articles-request)
     (effects/http-effect ?command-ch articles-request))]

  [::articles-response!
   [?request <- ::specs/Request (= :articles type) (= :server target)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/ArticleCount {::specs/count (:articlesCount ?response)})
   (apply rules/insert! ::specs/Article (map #(assoc % ::specs/time (specs/now)) (:articles ?response)))]

  [::tags-response!
   [?request <- ::specs/Request (= :tags type) (= :server target)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Tag (map (fn [tag] {::specs/tag tag}) (:tags ?response)))])

(defrules favorite-rules
  [::favorited-user-request!
   [::specs/Article (= ?slug slug)]
   =>
   (println "ONE")
   (rules/insert! ::specs/Request #::specs{:request-data ?slug
                                           ::specs/type :favorite
                                           ::specs/target :user
                                           :time (specs/now)})]

  [::favorited-user-response!
   [?request <- ::specs/Request (= :favorite type) (= :user target) (= ?slug request-data) (= ?time time)]
   [::specs/Response (= ?request request) (= ?favorited response)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (println "TWO" ?request)
   (let [request {:method              (if ?favorited :post :delete)
                  :uri                 (endpoint "articles" ?slug "favorite")
                  :headers             (auth-header ?token)
                  ::specs/type :favorite
                  ::specs/target :server
                  ::specs/request-data ?slug
                  ::specs/time (specs/now)}]
     (rules/insert! ::specs/Request request)
     (effects/http-effect ?command-ch request))]

  [::favorited-http-response!
   [?request <- ::specs/Request (= ?slug request-data) (= :favorite type) (= :server target)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (println "THREE")
   (rules/insert! ::specs/Article (assoc (:article ?response) ::specs/time (specs/now)))])

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

  [::can-edit-article!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?article <- ::specs/Article (= ?slug slug) (= ?author (:username author))]
   [::specs/User (= ?username username)]
   =>
   (rules/upsert! ::specs/Article ?article (assoc ?article ::specs/can-edit (= ?author ?username)))]

  [::can-edit-comment!
   [::specs/ActivePage (= :article (specs/page-name page))]
   [?comment <- ::specs/Comment (= ?author (:username author))]
   [::specs/User (= ?username username)]
   =>
   (rules/upsert! ::specs/Comment ?comment (assoc ?comment ::specs/can-edit (= ?author ?username)))]

  [::cannot-edit-article!
   [::specs/ActivePage (= :article (specs/page-name page)) (= ?slug (::specs/slug page))]
   [?article <- ::specs/Article (= ?slug slug)]
   [:not [::specs/User]]
   =>
   (rules/upsert! ::specs/Article ?article (assoc ?article ::specs/can-edit false))]

  [::cannot-edit-comment!
   [::specs/ActivePage (= :article (specs/page-name page))]
   [?comment <- ::specs/Comment]
   [:not [::specs/User]]
   =>
   (rules/upsert! ::specs/Comment ?comment (assoc ?comment ::specs/can-edit false))]
  )

(defrules article-edit-rules
  [::new-article!
   [::specs/ActivePage (= :editor (specs/page-name page))]
   [?new-article <- ::specs/NewArticle]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :post
                  :uri     (endpoint "articles")
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
                                    (async/put! ?command-ch [:page {::specs/slug (-> ?response :article :slug)}])

                                    ::specs/UpdatedArticle _
                                    (async/put! ?command-ch [:page {::specs/slug (-> ?response :article :slug)}])

                                    ::specs/DeletedArticle _
                                    (async/put! ?command-ch [:page :home]))]

  [::remove-article-edits
   [:not [::specs/ActivePage (= :article (specs/page-name page))]]
   [?article-edit <- ::specs/ArticleEdit]
   =>
   (rules/retract! (rules/spec-type ?article-edit) ?article-edit)])

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
   ;;; TODO - this will not work, forces retraction of the above
   (rules/retract! ::specs/NewComment ?new-comment)]

  [::delete-comment-response!
   [::specs/Request (= :comments request-type) (= ?request request) (= ?deleted-comment request-data)]
   [::specs/Response (= ?request request) (= ?response response)]
   [?deleted-comment <- ::specs/DeletedComment (= ?id id)]
   [?comment <- ::specs/Comment (= ?id id)]
   =>
   (rules/retract! ::specs/Comment ?comment)
   (rules/retract! ::specs/DeletedComment ?deleted-comment)]

  [::remove-comment-edits
   [:not [::specs/ActivePage (= :article (specs/page-name page))]]
   [?comment-edit <- ::specs/CommentEdit]
   =>
   (rules/retract! (rules/spec-type ?comment-edit) ?comment-edit)])

(defrules profile-page-rules
  [::profile!
   [::specs/ActivePage (= :profile (specs/page-name page)) (= ?username (::specs/username page))]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [profile-request {:method  :get
                          :uri     (endpoint "profiles" ?username) ;; evaluates to "api/profiles/:profile"
                          :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :profile
                                             :request      profile-request})
     (effects/http-effect ?command-ch profile-request))]

  [::profile-articles
   [?page <- ::specs/ActivePage
    (= :profile (specs/page-name page))
    (= ?username (::specs/username page))
    (= ?filter (::specs/profile-filter page))]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [articles-request (make-articles-request ?filter ?token)]
     (rules/insert! ::specs/Request #::specs{:request-type :articles
                                             :request      articles-request})
     (effects/http-effect ?command-ch articles-request))]

  [::profile-response!
   [::specs/Request (= :profile request-type) (= ?request request)]
   [::specs/Response (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Profile (:profile ?response))])

(defn user-request
  [req-fact token command-ch]
  (let [req-type (rules/spec-type req-fact)
        method (condp = req-type
                 ::specs/NewUser :post
                 ::specs/UpdatedUser :put
                 ::specs/Login :post)
        uri (condp = req-type
              ::specs/NewUser (endpoint "user")
              ::specs/UpdatedUser (endpoint "user")
              ::specs/Login (endpoint "users" "login"))
        request {:method  method
                 :uri     uri
                 :params  {:user req-fact}
                 :headers (auth-header token)}]
    (rules/insert! ::specs/Request #::specs{:request-type :login
                                            :request-data req-fact
                                            :request      request})
    (effects/http-effect command-ch request)))

(defrules user-rules
  [::user-request!
   [?user-req <- ::specs/UserReq]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (user-request ?user-req ?token ?command-ch)]

  [::set-token!
   [:not [::specs/ActivePage]]
   [::specs/Token (= ?token token)]
   [:test (some? ?token)]
   =>
   (let [request {:method  :get
                  :uri     (endpoint "user")                ;; evaluates to "api/articles/"
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/Request #::specs{:request-type :login
                                             :request      request}))]

  [::user-response!
   [?token <- ::specs/Token (= ?token token)]
   [?Request <- ::specs/Request (= :login request-type) (= ?request request) (= ?user-req request-data)]
   [?Response <- ::specs/Response (= ?request request) (= ?response response)]
   [?user-req <- ::specs/UserReq]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [token (-> ?response :user :token)]
     (rules/insert-unconditional! ::specs/User (:user ?response))
     (rules/retract! (rules/spec-type ?user-req) ?user-req)
     (rules/retract! ::specs/Request ?Request)
     (rules/retract! ::specs/Response ?Response)
     (async/put! ?command-ch [:set-token token]))])

;;; TODO - put this back as queries or use salience to avoid thrashing the view with intermediate states
(defrules view-update-rules
  [::render-active-page!
   [?active-page <- ::specs/ActivePage (= ?page page)]
   =>
   (view/render :page (specs/page-name ?page))]

  [::render-loading!
   [?loading <- (acc/all) :from [::specs/Loading]]
   =>
   (view/render :loading (mapv ::specs/section ?loading))]

  [::current-article!
   [?article <- (acc/max ::specs/time :returns-fact true) :from [::specs/Article (= ?slug slug)]]
   =>
   (rules/insert! ::specs/CurrentArticle ?article)]

  [::render-articles!
   [::specs/ActivePage (= :home (specs/page-name page))]
   [?articles <- (acc/all) :from [::specs/CurrentArticle]]
   [::specs/ArticleCount (= ?count count)]
   =>
   (view/render :articles ?articles)
   (view/render :article-count ?count)]

  [::render-tags!
   [::specs/ActivePage (= :home (specs/page-name page))]
   [?tags <- (acc/all) :from [::specs/Tag]]
   =>
   (view/render :tags (mapv ::specs/tag ?tags))]

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
  [::active-page [] [?active-page <- ::specs/ActivePage (= ?page page)]]
  [::token [] [?token <- ::specs/Token]]
  #_[::current-article [] [?article <- (acc/max ::specs/time :returns-fact true) :from [::specs/Article (= ?slug slug)]]]
  [::request [:?request-data :?type :?target] [?request <- (acc/max ::specs/time :returns-fact true)
                                              :from [::specs/Request (= ?request-data request-data) (= ?type type) (= ?target target)]]])