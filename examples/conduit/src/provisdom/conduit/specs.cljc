(ns provisdom.conduit.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::command-ch any?)                                   ;;
(s/def ::AppData (s/keys :req [::command-ch]))

(s/def ::count nat-int?)

(s/def ::token (s/nilable string?))
(s/def ::Token (s/keys :req [::token]))

(s/def ::username string?)
(s/def ::image (s/nilable string?))
(s/def ::bio (s/nilable string?))
(s/def ::email string?)
(s/def ::following boolean?)
(s/def ::Profile (s/keys :req-un [::username ::image ::bio] :opt-un [::following]))
(s/def ::User ::Profile)

(s/def ::password string?)
(s/def ::Login (s/keys :req [::email ::password]))
(s/def ::NewUser (s/merge ::Login (s/keys :req [::username])))
(s/def ::UpdatedUser (s/keys :req [::username ::email ::image ::bio] :opt [::password]))
(s/def ::UserReq (s/keys))
(derive ::Login ::UserReq)
(derive ::NewUser ::UserReq)
(derive ::UpdatedUser ::UserReq)

(s/def ::tag string?)
(s/def ::Tag (s/keys :req [::tag]))

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
(s/def ::Article (s/keys :req-un [::author ::body ::createdAt ::description ::favorited ::favoritesCount
                               ::slug ::tagList ::title ::updatedAt]
                         :opt [::can-edit]))
(s/def ::new boolean?)
(s/def ::NewArticle (s/keys :req-un [::new ::body ::description ::tagList ::title]))
(s/def ::UpdatedArticle ::Article)
(s/def ::DeletedArticle (s/keys :req-un [::slug]))
(s/def ::ArticleEdit (s/or ::NewArticle ::NewArticle
                           ::UpdatedArticle ::UpdatedArticle
                           ::DeletedArticle ::DeletedArticle))

(s/def ::can-edit boolean?)

(s/def ::id int?)
(s/def ::Comment (s/keys :req-un [::author ::body ::createdAt ::updatedAt ::id] :opt [::can-edit]))
(s/def ::NewComment (s/keys :req-un [::body]))
(s/def ::DeletedComment (s/keys :req-un [::id]))
(s/def ::CommentEdit (s/or ::NewComment ::NewComment ::DeletedComment ::DeletedComment))
(s/def ::ArticleCount (s/keys :req [::count]))

(s/def ::error (s/tuple keyword? string?))
(s/def ::Error (s/keys :req [::error]))

(s/def ::hash string?)

(s/def ::offset ::count)
(s/def ::feed boolean?)
(s/def ::limit pos-int?)
(s/def ::base-filter (s/keys :req [::offset ::limit]))
(s/def ::home-filter (s/merge ::base-filter (s/keys :opt [::feed ::tag])))
(s/def ::favorited-user ::username)
(s/def ::profile-filter (s/merge ::base-filter (s/keys :opt [::favorited-user ::username])))

(s/def ::page (s/or :home ::home-filter
                    :profile (s/keys :req [::username ::profile-filter])
                    :article (s/keys :req [::slug])
                    :editor (s/or ::NewArticle ::NewArticle ::UpdatedArticle ::UpdatedArticle)
                    :login #{:login}
                    :register #{:register}
                    :settings #{:settings}))

(defn page-name
  [page]
  (let [[page-name _] (s/conform ::page page)]
    page-name))

(s/def ::ActivePage (s/keys :req [::page]))

;;; Requests to server
(s/def ::request map?)
(s/def ::request-type #{:login :articles :article :editor :comments :profile
                        :tags :toggle-follow-user :toggle-favorite-article})
(s/def ::request-data any?)
(s/def ::Request (s/keys :req [::request ::request-type] :opt [::request-data]))
(s/def ::Loading (s/keys :req [::request-type]))

;;; Responses from server
(s/def ::response map?)
(s/def ::Response (s/keys :req [::response ::request]))
