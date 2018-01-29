(ns provisdom.todo.rules
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries defsession] :as rules]
            [clara.rules.accumulators :as acc]
            [provisdom.maali.listeners :as listeners]
            [net.cgrand.xforms :as xforms]))

;;; Input channel for responses
(def response-ch (async/chan 10))

;;; Used to fill in the ::specs/response-fn field in requests. The code which responds
;;; to a request can use ::specs/response-fn to provide the response.
(defn response
  [spec response]
  (if (s/valid? spec response)
    (async/put! response-ch [spec response])
    (throw (ex-info (str "Invalid response - must conform to spec " spec)
                    {:response response :spec spec :explanation (s/explain-data spec response)}))))

;;; Reducing function to produce the new session from a supplied response.
(defn handle-response
  [session [spec response]]
  (if session
    (-> session
        (rules/insert spec response)
        (rules/fire-rules))
    response))

;;; Transducer which takes in responses and provides reductions over the handle-response function,
;;; i.e. updated sessions.
(def handle-response-xf
  (comp
    (xforms/reductions (listeners/session-reducer-with-query-listener handle-response) nil)
    (drop 1)))                                              ;;; drop the initial nil value

;;; Convenience function to create new ::Todo facts
(defn now [] #?(:clj (System/currentTimeMillis) :cljs (.getTime (js/Date.))))

(defn new-todo
  ([title] (new-todo title (now)))
  ([title time]
   #::specs{:id (random-uuid) :title title :done false :created-at time}))

;;; Rules
(defrules rules
  [::retract-orphan-response!
   "Responses are inserted unconditionally from outside the rule engine, so
    explicitly retract any responses without a corresponding request."
   [?response <- ::specs/Response (= ?request Request)]
   [:not [?request <- ::specs/Request]]
   =>
   (rules/retract! ::specs/Response ?response)]

  [::anchor!
   "Initialize visibility and the request for a new todo. Both are singletons
    so insert unconditionally here."
   [::specs/Anchor (= ?time time)]
   =>
   (rules/insert-unconditional! ::specs/Visibility {::specs/visibility :all})
   (rules/insert-unconditional! ::specs/NewTodoRequest {::specs/response-fn (partial response ::specs/NewTodoResponse)})]

  [::new-todo-response!
   "Handle a new todo."
   [?request <- ::specs/NewTodoRequest]
   [::specs/NewTodoResponse (= ?request Request) (= ?todo Todo)]
   =>
   (rules/insert-unconditional! ::specs/Todo ?todo)
   (rules/upsert! ::specs/NewTodoRequest ?request assoc ::specs/time (now))]

  [::update-request!
   "When visibility changes or a new todo is inserted, conditionally insert
    requests to update todos."
   [?todo <- ::specs/Todo]
   =>
   (rules/insert! ::specs/UpdateTitleRequest #::specs{:Todo ?todo :response-fn (partial response ::specs/UpdateTitleResponse)})
   (rules/insert! ::specs/UpdateDoneRequest #::specs{:Todo ?todo :response-fn (partial response ::specs/UpdateDoneResponse)})
   (rules/insert! ::specs/RetractTodoRequest #::specs{:Todo ?todo :response-fn (partial response ::specs/Response)})]

  [::update-title-response!
   "Handle response to a title update request."
   [?request <- ::specs/UpdateTitleRequest (= ?todo Todo)]
   [::specs/UpdateTitleResponse (= ?request Request) (= ?title title)]
   =>
   (rules/upsert! ::specs/Todo ?todo assoc ::specs/title ?title)]

  [::update-done-response!
   "Handle response to a one update request."
   [?request <- ::specs/UpdateDoneRequest (= ?todo Todo)]
   [::specs/UpdateDoneResponse (= ?request Request) (= ?done done)]
   =>
   (rules/upsert! ::specs/Todo ?todo assoc ::specs/done ?done)]

  [::retract-todo-response!
   "Handle response to retract todo request."
   [?request <- ::specs/RetractTodoRequest (= ?todo Todo)]
   [::specs/Response (= ?request Request)]
   =>
   (rules/retract! ::specs/Todo ?todo)]

  [::complete-all-request!
   "Toggles the done attribute of all todos. If all todos are not done,
    then the request implies we set them all to done. If all todos are
    done, then the request means we will set them all to not done."
   [?todos <- (acc/grouping-by ::specs/done) :from [::specs/Todo]]
   =>
   (rules/insert! ::specs/CompleteAllRequest #::specs{:done        (not= 0 (count (?todos false)))
                                                      :response-fn (partial response ::specs/Response)})]

  [::complete-all-response!
   "Handle response to complete all request."
   [?request <- ::specs/CompleteAllRequest (= ?done done)]
   [::specs/Response (= ?request Request)]
   [?todos <- (acc/all) :from [::specs/Todo (= (not ?done) done)]]
   =>
   (rules/upsert-seq! ::specs/Todo ?todos update ::specs/done not)]

  [::retract-completed-request!
   "Request to retract all todo's marked done."
   [?todos <- (acc/all) :from [::specs/Todo (= true done)]]
   [:test (not-empty ?todos)]
   =>
   (rules/insert! ::specs/RetractCompletedRequest #::specs{:response-fn (partial response ::specs/Response)})]

  [::retract-completed-response!
   "Handle response to retract completed request."
   [?request <- ::specs/RetractCompletedRequest]
   [::specs/Response (= ?request Request)]
   [?todos <- (acc/all) :from [::specs/Todo (= true done)]]
   =>
   (apply rules/retract! ::specs/Todo ?todos)]

  [::visibility-request!
   "Request to update the visibility. Includes the valid choices for
    visibility given the current set of todos, e.g. if no todos are
    marked done, don't include completed as a valid option."
   [::specs/Visibility (= ?visibility visibility)]
   [?todos <- (acc/grouping-by ::specs/done) :from [::specs/Todo]]
   =>
   (let [visibilities (cond-> #{:all}
                              (not= 0 (count (?todos true))) (conj :completed)
                              (not= 0 (count (?todos false))) (conj :active))]
     (rules/insert! ::specs/VisibilityRequest #::specs{:visibility   ?visibility
                                                       :visibilities visibilities
                                                       :response-fn  (partial response ::specs/VisibilityResponse)}))]

  [::visibility-response!
   "Handle response to visibility request."
   [?request <- ::specs/VisibilityRequest (= ?visibilities visibilities)]
   [::specs/VisibilityResponse (= ?request Request) (= ?visibility visibility) (contains? ?visibilities visibility)]
   [?Visibility <- ::specs/Visibility]
   =>
   (rules/upsert! ::specs/Visibility ?Visibility assoc ::specs/visibility ?visibility)])

;;; Queries
(defqueries view-queries
  [::visible-todos []
   [::specs/Visibility (= ?visibility visibility)]
   [?todo <- ::specs/Todo (condp = ?visibility
                            :all true
                            :active (= false done)
                            :completed (= true done))]]
  [::active-count [] [?count <- (acc/count) :from [::specs/Todo (= false done)]]]
  [::completed-count [] [?count <- (acc/count) :from [::specs/Todo (= true done)]]])

(defqueries request-queries
  [::new-todo-request [] [?request <- ::specs/NewTodoRequest]]
  [::update-title-request [:?todo] [?request <- ::specs/UpdateTitleRequest (= ?todo Todo)]]
  [::update-done-request [:?todo] [?request <- ::specs/UpdateDoneRequest (= ?todo Todo)]]
  [::retract-todo-request [:?todo] [?request <- ::specs/RetractTodoRequest (= ?todo Todo)]]
  [::complete-all-request [] [?request <- ::specs/CompleteAllRequest]]
  [::retract-complete-request [] [?request <- ::specs/RetractCompletedRequest]]
  [::visibility-request [] [?request <- ::specs/VisibilityRequest]])

(defsession init-session [provisdom.todo.rules/rules provisdom.todo.rules/view-queries provisdom.todo.rules/request-queries]
  {:fact-type-fn rules/spec-type})

(def session (-> init-session
                 (listeners/with-listener listeners/query-listener)
                 (rules/insert ::specs/Anchor {::specs/time (now)})
                 (rules/fire-rules)))
