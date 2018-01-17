(ns provisdom.conduit.commands
  (:require [clojure.spec.alpha :as s]
            [lambdaisland.uniontypes #?(:clj :refer :cljs :refer-macros) [case-of]]
            [net.cgrand.xforms :as xforms]
            [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.maali.tracing :as tracing]
            [provisdom.conduit.rules :as conduit]))

;;; Model command specs
(s/def ::init (s/cat :command #{:init} :init-session rules/session?))
(s/def ::response (s/cat :command #{:response} :response ::specs/Response))
(s/def ::page (s/cat :command #{:page} :page ::specs/page))
(s/def ::hash (s/cat :command #{:hash} :hash ::specs/hash))
(s/def ::refresh-articles (s/cat :command #{:refresh-articles} :filter ::specs/ArticlesFilter))
(s/def ::toggle-favorite (s/cat :command #{:toggle-favorite} :slug ::specs/slug :favorited ::specs/favorited))
(s/def ::view-article (s/cat :command #{:view-article} :article ::specs/Article))
(s/def ::new-article (s/cat :command #{:new-article}))
(s/def ::edit-article (s/cat :command #{:edit-article} :slug ::specs/slug))
(s/def ::delete-article (s/cat :command #{:delete-article} :article ::specs/DeletedArticle))
(s/def ::new-comment (s/cat :command #{:new-comment} :body ::specs/body))
(s/def ::delete-comment (s/cat :command #{:delete-comment} :id ::specs/id))
(s/def ::toggle-following (s/cat :command #{:toggle-following} :username ::specs/User))
(s/def ::register (s/cat :command #{:register} :credentials ::specs/NewUser))
(s/def ::login (s/cat :command #{:login} :credentials ::specs/Login))
(s/def ::logout (s/cat :command #{:logout}))
(s/def ::update-user (s/cat :command #{:update-user} :user ::specs/UpdatedUser))
(s/def ::set-token (s/cat :command #{:set-token} :token ::specs/token))
(s/def ::command (s/or ::init ::init
                       ::response ::response
                       ::page ::page
                       ::hash ::hash
                       ::refresh-articles ::refresh-articles
                       ::toggle-favorite ::toggle-favorite
                       ::view-article ::view-article
                       ::new-article ::new-article
                       ::edit-article ::edit-article
                       ::delete-article ::delete-article
                       ::new-comment ::new-comment
                       ::delete-comment ::delete-comment
                       ::toggle-following ::toggle-following
                       ::register ::register
                       ::login ::login
                       ::logout ::logout
                       ::update-user ::update-user
                       ::set-token ::set-token))

(defmacro find-request
  ([session target type]
   (-> (rules/query session ::conduit/request :?type type :?target target)
       first
       :?request))
  ([session target type request-data]
   (-> (rules/query session ::conduit/request :?type type :?target target :?request-data request-data)
       first
       :?request)))

;;; Reduction function to update clara session state
(defn handle-state-command
  [session command]
  (case-of ::command command
           ::init {:keys [init-session]} init-session
           ::response {:keys [response]} (rules/insert session ::specs/HttpResponse response)
           ::page {:keys [page]} (rules/upsert-q session ::specs/ActivePage
                                                 (rules/query-fn ::conduit/active-page :?active-page)
                                                 assoc ::specs/page page)
           ::hash {:keys [hash]} (set! (.-hash js/location) hash)
           ::refresh-articles {:keys [filter]} (rules/upsert-q ::specs/ArticlesFilter
                                                               (rules/query-fn ::conduit/articles-filter :?articles-filter)
                                                               identity filter)
           ::toggle-favorite {:keys [slug favorited]}
           (rules/insert session ::specs/Response #::specs{:request  (find-request :user :favorite slug)
                                                           :response favorited
                                                           :time     (specs/now)})

           ::view-article {:keys [article]}
           (do
             (rules/upsert-q session ::specs/ActiveArticle
                            (rules/query-fn ::conduit/active-article :?active-article)
                            assoc ::specs/slug (::specs/slug article))
             (rules/upsert-q ::specs/ActivePage (rules/query-fn ::conduit/active-page :?active-page)
                             assoc ::specs/page :article))

           ::new-article _ (rules/insert session ::specs/EditedArticle specs/new-article)
           ::edit-article {:keys [slug]}
           (rules/insert session ::specs/Response #::specs{:request  (find-request :user :edit-article slug)
                                                           :response favorited
                                                           :time     (specs/now)})
           ::delete-article {:keys [article]} (rules/insert session ::specs/DeletedArticle article)
           ::new-comment {:keys [body]} (rules/insert session ::specs/NewComment {:body body})
           ::delete-comment {:keys [id]} (rules/insert session ::specs/DeletedComment {:id id})

           ::toggle-following {:keys [user]}
           (rules/upsert-q session ::specs/ToggleFollowing
                           (rules/query-fn ::conduit/following-user :?following-user :?username (:username user))
                           update ::specs/following not)

           ::register {:keys [credentials]} (rules/insert session ::specs/NewUser credentials)
           ::login {:keys [credentials]} (rules/insert session ::specs/Login credentials)
           ::logout _ (handle-state-command session [:set-token nil])
           ::update-user {:keys [user]} (rules/insert session ::specs/UpdatedUser user)

           ::set-token {:keys [token]}
           (do
             (if token
               (.setItem js/localStorage conduit/token-key token)
               (.removeItem js/localStorage conduit/token-key))
             (-> session
                 (rules/upsert-q ::specs/Token (rules/query-fn ::conduit/token :?token) assoc ::specs/token token)
                 (rules/upsert-q ::specs/ActivePage (rules/query-fn ::conduit/active-page :?active-page)
                                 assoc ::specs/page :home)
                 (rules/upsert-q ::specs/ArticlesFilter (rules/query-fn ::conduit/articles-filter :?articles-filter)
                                 merge #::specs{:feed (some? token) :offset 0 :limit 10})))))

(s/fdef handle-state-command
        :args (s/cat :session rules/session? :command ::command)
        :ret rules/session?)

(def update-state (fn [session command]
                    (let [session
                          (-> session
                              (handle-state-command command)
                              (rules/fire-rules))]
                      #_(println (rules/query session ::conduit/current-article))
                      session)))

(def debug-update-state (fn [session command]
                          (if session
                            (-> session
                                (tracing/with-tracing)
                                (handle-state-command command)
                                (rules/fire-rules)
                                (tracing/print-trace))
                            (-> session
                                (handle-state-command command)
                                (rules/fire-rules)))))
(def update-state-xf (comp (xforms/reductions update-state nil) (drop 1)))
(def debug-update-state-xf (comp (xforms/reductions debug-update-state nil) (drop 1)))
