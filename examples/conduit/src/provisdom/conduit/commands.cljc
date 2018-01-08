(ns provisdom.conduit.commands
  (:require [clojure.spec.alpha :as s]
            [lambdaisland.uniontypes #?(:clj :refer :cljs :refer-macros) [case-of]]
            [net.cgrand.xforms :as xforms]
            [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defsession] :as rules]
            [provisdom.conduit.rules :as conduit]
            [provisdom.maali.listeners :as listeners]
            [clojure.string :as str]))

;;; Model command specs
(s/def ::init (s/cat :command #{:init} :init-session rules/session?))
(s/def ::upsert (s/cat :command #{:upsert} :old-value (s/nilable ::specs/Entity) :new-value (s/nilable ::specs/Entity)))
(s/def ::pending (s/cat :command #{:pending} :request ::specs/request))
(s/def ::response (s/cat :command #{:response} :response ::specs/Response))
(s/def ::page (s/cat :command #{:page} :page ::specs/page))
(s/def ::new-comment (s/cat :command #{:new-comment} :body ::specs/body))
(s/def ::delete-comment (s/cat :command #{:delete-comment} :id ::specs/id))
(s/def ::command (s/or ::init ::init
                       ::upsert ::upsert
                       ::pending ::pending
                       ::response ::response
                       ::page ::page
                       ::new-comment ::new-comment
                       ::delete-comment ::delete-comment))

;;; Reduction function to update clara session state
(defn handle-state-command
  [session command]
  (case-of ::command command
           ::init {:keys [init-session]} init-session
           ::upsert {:keys [spec old-value new-value]} (let [[old-spec old-value] old-value
                                                             [new-spec new-value] new-value]
                                                         (when (and old-spec new-spec (not= old-spec new-spec))
                                                           (throw (ex-info (str "Upsert specs must match: " old-spec new-spec)
                                                                           {:command command})))
                                                         (rules/upsert session (or old-spec new-spec) old-value new-value))
           ::pending {:keys [request]} (rules/insert session ::specs/Pending {::specs/request request})
           ::response {:keys [response]} (rules/insert session ::specs/Response response)
           ::page {[_ page] :page} (rules/upsert-q session ::specs/ActivePage
                                                   (rules/query-fn ::conduit/active-page :?active-page)
                                                   merge {::specs/page page})
           ::new-comment {:keys [body]} (rules/insert session ::specs/NewComment {::specs/body body})
           ::delete-comment {:keys [id]} (rules/insert session ::specs/DeletedComment {::specs/id id})))

(s/fdef handle-state-command
        :args (s/cat :session rules/session? :command ::command)
        :ret rules/session?)

(defn handle-state-commands
  [session commands]
  (reduce handle-state-command session commands))

(s/fdef handle-state-commands
        :args (s/cat :session rules/session? :commands (s/coll-of ::command))
        :ret rules/session?)

(def update-state (listeners/update-with-query-listener-fn handle-state-commands))
(def debug-update-state (listeners/debug-update-with-query-listener-fn handle-state-commands))
(def update-state-xf (comp (xforms/reductions update-state nil) (drop 1)))
(def debug-update-state-xf (comp (xforms/reductions debug-update-state nil) (drop 1)))

(defn query-result->effect
  [query-result]
  (case-of ::conduit/query-result query-result

           ::conduit/request
           {:keys [result]}
           (mapv (fn [{:keys [?request]}] [:request ?request]) result)

           ::conduit/loading
           {:keys [result]}
           [:render :loading (mapv :?section result)]

           ::conduit/active-page
           {[{[?page _] :?page} & _] :result}
           [:render :page ?page]

           ::conduit/tags
           {:keys [result]}
           [:render :tags (mapv :?tag result)]

           ::conduit/articles
           {:keys [result]}
           [:render :articles (mapv :?article result)]

           ::conduit/article-count
           {[{:keys [?count]} & _] :result}
           [:render :article-count (or ?count 0)]

           ::conduit/active-article
           {[{:keys [?article]} & _] :result}
           [:render :article ?article]

           ::conduit/comments
           {:keys [result]}
           [:render :comments (mapv :?comment result)]

           ::conduit/profile
           {[{:keys [?profile]} & _] :result}
           [:render :profile ?profile]))

(def query-result-xf (map #(sequence (comp (map query-result->effect) (filter seq)) %)))