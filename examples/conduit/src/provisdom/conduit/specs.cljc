(ns provisdom.conduit.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.set :as set])
  #?(:cljs (:require-macros [provisdom.conduit.specs :refer [def-derive]])))

;;; TODO - deduce parent-name from spec
#?(:clj
  (defmacro def-derive
    ([child-name parent-name]
     `(def-derive ~child-name ~parent-name ~parent-name))
    ([child-name parent-name spec]
     `(do
        (#?(:clj clojure.spec.alpha/def :cljs cljs.spec.alpha/def) ~child-name ~spec)
        (derive ~child-name ~parent-name)))))

(defn now [] #?(:clj (System/currentTimeMillis) :cljs (.getTime (js/Date.))))
(defn ns-keys
  [x]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (set/rename-keys x
                                     (into {}
                                           (map (fn [k]
                                                  [k (keyword "provisdom.conduit.specs" (name k))])
                                                (keys x))))
                    x))
                x))

(s/def ::command-ch any?)                                   ;;; TODO Spec channel?
(s/def ::AppData (s/keys :req [::command-ch]))
(s/def ::time int?)(s/def ::count nat-int?)
(s/def ::edit? boolean?)

(s/def ::token (s/nilable string?))
(s/def ::Token (s/keys :req [::token]))

;;; Requests
(s/def ::Request (s/keys :req [::time]))
(def-derive ::HttpRequest ::Request) ;;; TODO - spec HTTP request?
(def section #{:login :articles :article :editor :comments :profile :tags})
(s/def ::section section)
(s/def ::Loading (s/keys :req [::section]))

;;; Responses
(s/def ::response map?)                                     ;;; TODO - spec HTTP response?
(s/def ::Response (s/keys :req [::request ::time]))         ;;; TODO - how do we validate the request/response pair by type?
(def-derive ::HttpResponse ::Response (s/merge ::Response (s/keys :req [::response])))

;;; User profile
(s/def ::username string?)
(s/def ::image (s/nilable string?))
(s/def ::bio (s/nilable string?))
(s/def ::email string?)
(s/def ::following boolean?)
(s/def ::password string?)
(s/def ::Profile (s/keys :req [::username ::image ::bio] :opt [::following]))
(s/def ::User ::Profile)
(s/def ::Credentials (s/keys :req [::email ::password]))
(s/def ::NewUser (s/keys :req [::username ::email ::password]))
(s/def ::logged-in-user (s/nilable ::username))
(s/def ::LoggedIn (s/keys :req [::logged-in-user ::token]))

(def-derive ::LoginUserRequest ::Request)
(def-derive ::LoginUserResponse ::Response (s/merge ::Response (s/keys :req [::Credentials])))
(def-derive ::NewUserRequest ::Request)
(def-derive ::NewUserResponse ::Response (s/merge ::Response (s/keys :req [::NewUser])))
(def-derive ::UpdateUserRequest ::Request)
(def-derive ::UpdateUserResponse ::Response (s/merge ::Response (s/keys :req [::User])))
(def-derive ::TokenRequest ::Request)
(def-derive ::TokenResponse ::Resposne (s/merge ::Response (s/keys :req [::token])))
(def-derive ::LogoutRequest ::Request)
(def-derive ::UserHttpRequest ::HttpRequest)

(s/def ::tag string?)
(s/def ::Tag (s/keys :req [::tag]))
(s/def ::TagsHttpRequest ::HttpRequest)
(derive ::TagsHttpRequest ::HttpRequest)

(s/def ::author ::Profile)
(s/def ::body string?)
(s/def ::createdAt string?)
(s/def ::description string?)
(s/def ::favorited boolean?)
(s/def ::favoritesCount ::count)
(s/def ::slug string?)
(s/def ::tagList (s/coll-of ::tag))
(s/def ::title string?)
(s/def ::updatedAt string?)
(s/def ::Article (s/keys :req [::author ::body ::createdAt ::description ::favorited ::favoritesCount
                               ::slug ::tagList ::title ::updatedAt]))
(def-derive ::EditableArticle ::Article)
(s/def ::EditedArticle (s/keys :req [::body ::description ::tagList ::title] :opt [::slug]))
(def new-article {::body "" ::description "" ::tagList [] ::title ""})

(def-derive ::ViewArticleUserRequest ::Request (s/merge ::Request (s/keys :req [::slug])))
(def-derive ::ViewArticleHttpRequest ::HttpRequest)

(def-derive ::EditArticleUserRequest ::Request (s/merge ::Request (s/keys :req [::EditedArticle])))
(def-derive ::EditArticleUserResponse ::Response (s/merge ::Response ::EditedArticle))
(def-derive ::EditArticleHttpRequest ::HttpRequest (s/merge ::HttpRequest (s/keys :req [::EditedArticle])))

(def-derive ::DeleteArticleUserRequest ::Request (s/merge ::Request (s/keys :req [::EditableArticle])))
(def-derive ::DeleteArticleHttpRequest ::HttpRequest)

(def-derive ::ToggleFavoriteUserRequest ::Request (s/merge ::Request (s/keys :req [::slug])))
(def-derive ::ToggleFavoriteHttpRequest ::HttpRequest)

(s/def ::ActiveArticle (s/keys :req [::slug]))
(s/def ::CurrentArticle ::Article)
(s/def ::ArticleCount (s/keys :req [::count]))

(s/def ::id int?)
(s/def ::Comment (s/keys :req [::author ::body ::createdAt ::updatedAt ::id] :opt [::edit?]))
(def-derive ::ViewCommentsHttpRequest ::HttpRequest (s/merge ::HttpRequest (s/keys :req [::slug])))
(def-derive ::NewCommentUserRequest ::Request (s/merge ::Request (s/keys :req [::slug])))
(def-derive ::NewCommentUserResponse ::Response (s/merge ::Response (s/keys :req [::body])))
(def-derive ::NewCommentHttpRequest ::Request (s/merge ::Request (s/keys :req [::slug])))
(def-derive ::DeleteCommentUserRequest ::Request (s/merge ::Request (s/keys :req [::Comment])))
(def-derive ::DeleteCommentHttpRequest ::HttpRequest (s/merge ::HttpRequest (s/keys :req [::Comment])))
(s/def ::CurrentComment ::Comment)

(s/def ::error (s/tuple keyword? string?))
(s/def ::Error (s/keys :req [::error]))

(s/def ::hash string?)

(s/def ::offset ::count)
(s/def ::feed boolean?)
(s/def ::limit pos-int?)
(s/def ::ArticlesFilter (s/keys :req [::offset ::limit] :opt [::feed ::tag ::favorited-user ::username]))
(def-derive ::ArticlesHttpRequest ::HttpRequest)

(s/def ::page #{:home :profile :article :editor :login :register :settings})

(s/def ::ActivePage (s/keys :req [::page]))


