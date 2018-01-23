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
  #_[::request!
   [?request <- ::specs/Request]
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
  {:method                :get
   :uri                   (if (::specs/feed filter) (endpoint "articles" "feed") (endpoint "articles"))
   :params                (cond-> filter
                                  (::specs/username filter) (assoc :author (::specs/username filter))
                                  true (dissoc ::specs/username)
                                  (::specs/favorited-user filter) (assoc :favorited (::specs/favorited-user filter))
                                  true (dissoc ::specs/favorited-user))
   :headers               (auth-header token)
   ::specs/ArticlesFilter filter
   ::specs/time           (specs/now)})

(def current (acc/max ::specs/time :returns-fact true))

;;; TODO - get rid of biz logic dependence on pages, make everything centered around article list fetched from server
(defrules articles-list-rules
  [::articles-request!
   [?filter <- ::specs/ArticlesFilter]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [tags-request {:method      :get
                       :uri         (endpoint "tags")
                       ::specs/time (specs/now)}
         articles-request (make-articles-request ?filter ?token)]
     (rules/insert! ::specs/TagsHttpRequest tags-request)
     (effects/http-effect ?command-ch tags-request)
     (rules/insert! ::specs/ArticlesHttpRequest articles-request)
     (effects/http-effect ?command-ch articles-request))]

  [::articles-response!
   [?request <- ::specs/ArticlesHttpRequest]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/ArticleCount {::specs/count (:articlesCount ?response)})
   (apply rules/insert! ::specs/Article (map #(assoc (specs/ns-keys %) ::specs/time (specs/now)) (:articles ?response)))]

  [::tags-response!
   [?request <- ::specs/TagsHttpRequest]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Tag (map (fn [tag] {::specs/tag tag}) (:tags ?response)))])

(defrules favorite-rules
  [::favorited-user-request!
   [?article <- current :from [::specs/Article (= ?slug slug)]]
   [::specs/LoggedIn (some? logged-in-user)]
   =>
   (rules/insert! ::specs/ToggleFavoriteUserRequest #::specs{:slug ?slug :time (specs/now)})]

  [::favorited-user-response!
   [?request <- ::specs/ToggleFavoriteUserRequest (= ?slug slug) ]
   [?article <- current :from [::specs/Article (= ?slug slug) (= ?favorited favorited)]]
   [::specs/Response (= ?request request)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method      (if ?favorited :post :delete)
                  :uri         (endpoint "articles" ?slug "favorite")
                  :headers     (auth-header ?token)
                  ::specs/time (specs/now)}]
     (rules/insert! ::specs/ToggleFavoriteHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::favorited-http-response!
   [?request <- ::specs/ToggleFavoriteHttpRequest]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Article (assoc (specs/ns-keys (:article ?response)) ::specs/time (specs/now)))])

(defn make-comments-request
  [slug token]
  {:method      :get
   :uri         (endpoint "articles" slug "comments")
   :headers     (auth-header token)
   ::specs/slug slug
   ::specs/time (specs/now)})

(defrules article-rules
  [::article-user-request!
   [?article <- current :from [::specs/Article (= ?slug slug)]]
   =>
   (rules/insert! ::specs/ViewArticleUserRequest #::specs{:slug ?slug :time (specs/now)})]

  [::article-user-response!
   [?request <- ::specs/ViewArticleUserRequest (= ?slug slug)]
   [::specs/Response (= ?request request)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [article-request {:method              :get
                          :uri                 (endpoint "articles" ?slug) ;; evaluates to "api/articles/:slug"
                          :headers             (auth-header ?token)
                          ::specs/time         (specs/now)}
         comments-request (make-comments-request ?slug ?token)]
     (rules/insert! ::specs/ViewArticleHttpRequest article-request)
     (effects/http-effect ?command-ch article-request)
     (rules/insert! ::specs/ViewCommentsHttpRequest comments-request)
     (effects/http-effect ?command-ch comments-request))]

  [::article-response!
   [?request <- ::specs/ViewArticleHttpRequest]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   =>
   (rules/insert! ::specs/Article (assoc (specs/ns-keys (:article ?response)) ::specs/time (specs/now)))]

  [::editable-article!
   [?article <- current :from [::specs/Article (= ?slug slug) (= ?author (::specs/username author))]]
   [::specs/LoggedIn (= ?logged-in-user logged-in-user)]
   [:test (and (some? ?logged-in-user) (= ?author ?logged-in-user))]
   =>
   (rules/insert! ::specs/EditableArticle ?article)]

  [::new-article-user-request
   [:or [:not [?request <- current :from [::specs/EditArticleUserRequest (= specs/new-article EditedArticle)]]]
    [:and [:and [?request <- current :from [::specs/EditArticleUserRequest (= specs/new-article EditedArticle)]]
           [::specs/EditArticleUserResponse (= ?request request)]]]]
   [::specs/LoggedIn (some? logged-in-user)]
   #_[:or [:test (nil? ?request)]
    [::specs/EditArticleUserResponse (= ?request request)]]
   =>
   (println "NUSHIT")
   (rules/insert! ::specs/EditArticleUserRequest #::specs{:EditedArticle specs/new-article :time (specs/now)})]

  [::edit-article-user-request!
   [?article <- ::specs/EditableArticle]
   =>
   (rules/insert! ::specs/EditArticleUserRequest #::specs{:EditedArticle ?article :time (specs/now)})]

  [::edit-article-user-response!
   [?request <- ::specs/EditArticleUserRequest (= ?article EditedArticle) (= ?slug (::specs/slug EditedArticle))]
   [::specs/Response (= ?request request)  ]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [new? (= specs/new-article ?article)
         request {:method        (if new? :put :post)
                  :uri           (if new? (endpoint "articles" ?slug) (endpoint "articles"))
                  :params        {:article ?article}
                  :headers       (auth-header ?token)
                  ::specs/EditedArticle ?article
                  ::specs/time   (specs/now)}]
     (rules/insert! ::specs/EditArticleHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::edit-article-response!
   [?request <- ::specs/EditArticleHttpRequest (= ?article EditedArticle)]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   [::specs/LoggedIn (= ?logged-in-user logged-in-user)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [new? (= specs/new-article ?article)
         article (assoc (specs/ns-keys (:article ?response)) ::specs/time (specs/now))
         slug (::specs/slug article)
         comments-request (make-comments-request slug ?token)]
     (assoc (specs/ns-keys article) ::specs/time (specs/now))
     (rules/insert! ::specs/Request comments-request)
     (effects/http-effect ?command-ch comments-request)
     (when (and new? (some? ?logged-in-user))
       (rules/insert! ::specs/EditArticleUserRequest #::specs{:EditedArticle specs/new-article
                                                              :time (specs/now)})))]

  [::delete-article-user-request!
   [?article <- ::specs/EditableArticle]
   =>
   (rules/insert! ::specs/DeleteArticleUserRequest #::specs{:EditableArticle ?article :time (specs/now)})]

  [::delete-article-user-response!
   [?request <- ::specs/DeleteArticleUserRequest (= ?slug (::specs/slug EditableArticle)) ]
   [::specs/Response (= ?request request)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method        :delete
                  :uri           (endpoint "articles" ?slug)
                  :headers       (auth-header ?token)
                  ::specs/type   :delete
                  ::specs/target :server
                  ::specs/time   (specs/now)}]
     (rules/insert! ::specs/DeleteArticleHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::delete-article-response!
   [?request <- ::specs/DeleteArticleHttpRequest]
   [?request <- ::specs/HttpResponse (= ?request request)]
   [?filter <- ::specs/ArticlesFilter]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (async/put! ?command-ch [:refresh-articles ?filter])])

(defrules comment-rules
  [::comments-response!
   [::specs/ViewCommentsHttpRequest (= ?slug slug)]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   [::specs/LoggedIn (= ?logged-in-user logged-in-user)]
   =>
   (apply rules/insert! ::specs/Comment (map #(assoc (specs/ns-keys %) ::specs/slug ?slug ::specs/time (specs/now))
                                             (:comments ?response)))
   (when (some? ?logged-in-user) (rules/insert! ::specs/NewCommentUserRequest #::specs{:slug ?slug :time (specs/now)}))]

  [::new-comment-user-response!
   [?request <- ::specs/NewCommentUserRequest (= ?slug slug)]
   [::specs/NewCommentUserResponse (= ?request request) (= ?body body)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :post
                  :uri     (endpoint "articles" ?slug "comments")
                  :params  {:comment {:body ?body}}
                  :headers (auth-header ?token)}]
     (rules/insert! ::specs/NewCommentHttpRequest #::specs{:time (specs/now)})
     (effects/http-effect ?command-ch request))]

  [::new-comment-response!
   [::specs/NewCommentHttpRequest (= ?slug slug)]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   [::specs/LoggedIn (= ?logged-in-user logged-in-user)]
   =>
   (rules/insert! ::specs/Comment #(assoc (specs/ns-keys (:comment ?response)) ::specs/slug ?slug ::specs/time (specs/now)))
   (when (some? ?logged-in-user) (rules/insert! ::specs/NewCommentUserRequest #::specs{:slug ?slug :time (specs/now)}))]

  [::delete-comment-user-request!
   [?comment <- current :from [::specs/Comment (= ?author (:username author))]]
   [::specs/LoggedIn (= ?logged-in-user logged-in-user)]
   [:test (and (some? ?logged-in-user) (= ?author ?logged-in-user))]
   =>
   (rules/insert! ::specs/DeleteCommentUserRequest #::specs{:Comment ?comment :time (specs/now)})]

  [::delete-comment-user-response!
   [?request <- ::specs/DeleteCommentUserRequest (= ?comment Comment) (= ?slug (::specs/slug Comment)) (= ?id (::specs/id Comment))]
   [::specs/Response (= ?request request)]
   [::specs/Token (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request {:method  :delete
                  :uri     (endpoint "articles" ?slug "comments" ?id)
                  :headers (auth-header ?token)
                  ::specs/Comment ?comment}]
     (rules/insert! ::specs/DeleteCommentHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::delete-comment-response!
   [?request <- ::specs/DeleteCommentHttpRequest (= ?comment Comment)]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   =>
   (rules/retract! ::specs/Comment ?comment)])

;;; TODO
(defrules profile-rules
  [::profile!
   [::specs/ActivePage (= :profile page) (= ?username (::specs/username page))]
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
    (= :profile page)
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

(defrules user-rules
  [::login-user-request!
   [::specs/LoggedIn (nil? token)]
   =>
   (rules/insert! ::specs/LoginUserRequest #::specs{:time (specs/now)})
   (rules/insert! ::specs/NewUserRequest #::specs{:time (specs/now)})]

  [::login-user-response!
   [?request <- ::specs/LoginUserRequest]
   [::specs/LoginUserResponse (= ?request request) (= ?credentials Credentials)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request #::specs{:method :post
                          :uri (endpoint "users" "login")
                          :params {:user ?credentials}}]
     (rules/insert! ::specs/UserHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::new-user-response!
   [?request <- ::specs/NewUserRequest]
   [::specs/NewUserResponse (= ?request request) (= ?user NewUser)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request #::specs{:method :post
                          :uri (endpoint "user")
                          :params {:user ?user}}]
     (rules/insert! ::specs/UserHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::token-request!
   [::specs/LoggedIn (nil? token)]
   =>
   (rules/insert! ::specs/TokenRequest #::specs{:time (specs/now)})]

  [::token-response!
   [?request <- ::specs/TokenRequest]
   [::specs/TokenResponse (= ?request request) (= ?token token)]
   =>
   (let [request {:method  :get
                  :uri     (endpoint "user")
                  :headers (auth-header ?token)
                  ::specs/time (specs/now)}]
     (rules/insert! ::specs/UserHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::update-user-request!
   [?user <- ::specs/User]
   =>
   (rules/insert! ::specs/UpdateUserRequest #::specs{:user ?user :time (specs/now)})]

  [::update-user-response!
   [?request <- ::specs/UpdateUserRequest]
   [::specs/UpdatedUserResponse (= ?request request) (= ?user User)]
   [::specs/LoggedIn (= ?token token)]
   [::specs/AppData (= ?command-ch command-ch)]
   =>
   (let [request #::specs{:method :put
                          :uri (endpoint "user")
                          :params {:user ?user}
                          :headers (auth-header ?token)}]
     (rules/insert! ::specs/UserHttpRequest request)
     (effects/http-effect ?command-ch request))]

  [::user-http-response!
   [?request <- ::specs/UserHttpRequest]
   [::specs/HttpResponse (= ?request request) (= ?response response)]
   [?logged-in <- ::specs/LoggedIn]
   =>
   (let [user (specs/ns-keys (:user ?response))
         token (::specs/token user)
         username (::specs/username user)]
     (rules/insert-unconditional! ::specs/User user)
     (rules/upsert! ::specs/LoggedIn ?logged-in #::specs{:logged-in-user username :token token}))]

  [::logout-request!
   [::specs/LoggedIn (some? token)]
   =>
   (rules/insert! ::specs/LogoutRequest #::specs{:time (specs/now)})]

  [::logout-user-response!
   [?request <- ::specs/LogoutRequest]
   [::specs/Response (= ?request request)]
   [?logged-in <- ::specs/LoggedIn]
   [?user <- ::specs/User]
   =>
   (rules/retract! ::specs/User ?user)
   (rules/upsert! ::specs/LoggedIn ?logged-in #::specs{:logged-in-user nil :token nil})])

(defrules view-update-rules
  [::render-active-page!
   {:salience -1000}
   [?active-page <- ::specs/ActivePage (= ?page page)]
   =>
   (view/render :page ?page)]

  [::render-loading!
   {:salience -1000}
   [?loading <- (acc/all) :from [::specs/Loading]]
   =>
   (view/render :loading (mapv ::specs/section ?loading))]

  [::current-article!
   [?article <- current :from [::specs/Article (= ?slug slug)]]
   =>
   (rules/insert! ::specs/CurrentArticle ?article)]

  [::render-articles!
   {:salience -1000}
   [::specs/ActivePage (= :home page)]
   [?articles <- (acc/all) :from [::specs/CurrentArticle]]
   [::specs/ArticleCount (= ?count count)]
   =>
   (view/render :articles ?articles)
   (view/render :article-count ?count)]

  [::render-tags!
   {:salience -1000}
   [::specs/ActivePage (= :home page)]
   [?tags <- (acc/all) :from [::specs/Tag]]
   =>
   (view/render :tags (mapv ::specs/tag ?tags))]

  [::current-comment!
   [?comment <- current :from [::specs/Comment (= ?id id)]]
   =>
   (rules/insert! ::specs/CurrentComment ?comment)]

  [::render-article!
   {:salience -1000}
   [::specs/ActivePage (= :article page) (= ?slug (::specs/slug page))]
   [?article <- ::specs/CurrentArticle (= ?slug slug)]
   [?comments <- (acc/all) :from [::specs/Comment]]
   =>
   (view/render :article ?article)
   (view/render :comments ?comments)]

  [::render-profile!
   {:salience -1000}
   [::specs/ActivePage (= :profile page)]
   [?articles <- (acc/all) :from [::specs/Article]]
   [?profile <- ::specs/Profile]
   =>
   (view/render :profile ?profile)
   (view/render :articles ?articles)])

#_(enable-console-print!)
#_(cljs.pprint/pprint rules)

(defqueries queries
  [::active-page [] [?active-page <- ::specs/ActivePage (= ?page page)]]
  [::active-article [] [?active-article <- ::specs/ActiveArticle]]
  [::token [] [?token <- ::specs/Token]]
  [::articles-filter [] [?articles-filter <- ::specs/ArticlesFilter]]
  [::request [:?request-data :?type :?target]
   [?request <- current :from [::specs/Request (= ?request-data request-data) (= ?type type) (= ?target target)]]])