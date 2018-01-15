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
(s/def ::fire-rules (s/cat :command #{:fire-rules} :atom ::specs/atom))
(s/def ::response (s/cat :command #{:response} :response ::specs/Response))
(s/def ::page (s/cat :command #{:page} :page ::specs/page))
(s/def ::hash (s/cat :command #{:hash} :hash ::specs/hash))
(s/def ::new-comment (s/cat :command #{:new-comment} :body ::specs/body))
(s/def ::delete-comment (s/cat :command #{:delete-comment} :id ::specs/id))
(s/def ::new-article (s/cat :command #{:new-article} :article ::specs/NewArticle))
(s/def ::update-article (s/cat :command #{:update-article} :article ::specs/UpdatedArticle))
(s/def ::delete-article (s/cat :command #{:delete-article} :article ::specs/DeletedArticle))
(s/def ::toggle-favorite (s/cat :command #{:toggle-favorite} :slug ::specs/slug :favorited ::specs/favorited))
(s/def ::toggle-following (s/cat :command #{:toggle-following} :username ::specs/User))
(s/def ::login (s/cat :command #{:login} :credentials ::specs/Login))
(s/def ::logout (s/cat :command #{:logout}))
(s/def ::register (s/cat :command #{:register} :credentials ::specs/NewUser))
(s/def ::update-user (s/cat :command #{:update-user} :user ::specs/UpdatedUser))
(s/def ::set-token (s/cat :command #{:set-token} :token ::specs/token))
(s/def ::command (s/or ::init ::init
                       ::fire-rules ::fire-rules
                       ::response ::response
                       ::page ::page
                       ::hash ::hash
                       ::new-comment ::new-comment
                       ::delete-comment ::delete-comment
                       ::new-article ::new-article
                       ::update-article ::update-article
                       ::delete-article ::delete-article
                       ::toggle-favorite ::toggle-favorite
                       ::toggle-following ::toggle-following
                       ::register ::register
                       ::update-user ::update-user
                       ::login ::login
                       ::logout ::logout
                       ::set-token ::set-token))

;;; Reduction function to update clara session state
(defn handle-state-command
  [session command]
  (case-of ::command command
           ::init {:keys [init-session]} init-session
           ::fire-rules {:keys [atom]} (rules/insert session ::specs/Time #::specs{:time (.getTime (js/Date.)) :atom atom})
           ::response {:keys [response]} (rules/insert session ::specs/Response response)
           ::new-comment {:keys [body]} (rules/insert session ::specs/NewComment {:body body})
           ::delete-comment {:keys [id]} (rules/insert session ::specs/DeletedComment {:id id})
           ::new-article {:keys [article]} (rules/insert session ::specs/NewArticle article)
           ::update-article {:keys [article]} (rules/insert session ::specs/UpdatedArticle article)
           ::delete-article {:keys [article]} (rules/insert session ::specs/DeletedArticle article)

           ::toggle-favorite {:keys [slug favorited]}
           (let [request (-> (rules/query session ::conduit/request :?request-data slug :?type :favorite :?target :user)
                             first
                             :?request)]
             (println "********" request)
             (rules/insert session ::specs/Response #::specs{:request  request
                                                             :response favorited
                                                             :time     (specs/now)}))

           ::toggle-following {:keys [user]}
           (rules/upsert-q session ::specs/ToggleFollowing
                           (rules/query-fn ::conduit/following-user :?following-user :?username (:username user))
                           update ::specs/following not)

           ::register {:keys [credentials]} (rules/insert session ::specs/NewUser credentials)
           ::update-user {:keys [user]} (rules/insert session ::specs/UpdatedUser user)
           ::login {:keys [credentials]} (rules/insert session ::specs/Login credentials)
           ::logout _ (handle-state-command session [:set-token nil])
           ::hash {:keys [hash]} (set! (.-hash js/location) hash)
           ::page {:keys [page]} (rules/upsert-q session ::specs/ActivePage
                                                 (rules/query-fn ::conduit/active-page :?active-page)
                                                 assoc ::specs/page (s/unform ::specs/page page))
           ::set-token {:keys [token]}
           (do
             (if token
               (.setItem js/localStorage conduit/token-key token)
               (.removeItem js/localStorage conduit/token-key))
             (-> session
                 (rules/upsert-q ::specs/Token (rules/query-fn ::conduit/token :?token) assoc ::specs/token token)
                 (rules/upsert-q ::specs/ActivePage (rules/query-fn ::conduit/active-page :?active-page)
                                 assoc ::specs/page #::specs{:feed (some? token) :offset 0 :limit 10})))))

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
