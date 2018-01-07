;;; Taken from https://github.com/Day8/re-frame-http-fx/blob/master/src/day8/re_frame/http_fx.cljs

(ns provisdom.conduit.effects
  (:require [cljs.core.async :as async]
            [goog.net.ErrorCode :as errors]
            [ajax.core :as ajax]
            [lambdaisland.uniontypes :refer-macros [case-of]]
            [cljs.spec.alpha :as s]
            [provisdom.conduit.specs :as specs]
            [provisdom.conduit.view :as view]
            [clojure.string :as str]))

(defn ajax-xhrio-handler
  "ajax-request only provides a single handler for success and errors"
  [on-success on-failure xhrio [success? response]]
  ; see http://docs.closure-library.googlecode.com/git/class_goog_net_XhrIo.html
  (if success?
    (on-success response)
    (let [details (merge
                    {:uri             (.getLastUri xhrio)
                     :last-method     (.-lastMethod_ xhrio)
                     :last-error      (.getLastError xhrio)
                     :last-error-code (.getLastErrorCode xhrio)
                     :debug-message   (-> xhrio .getLastErrorCode (errors/getDebugMessage))}
                    response)]
      (on-failure details))))


(defn request->xhrio-options
  [{:as   request
    :keys [on-success on-failure]
    :or   {on-success [:http-no-on-success]
           on-failure [:http-no-on-failure]}}]
  ; wrap events in cljs-ajax callback
  (let [api (new js/goog.net.XhrIo)]
    (-> request
        (assoc
          :api api
          :handler (partial ajax-xhrio-handler
                            on-success
                            on-failure
                            api))
        (dissoc :on-success :on-failure))))

(defn http-effect
  [request]
  (-> request
      (merge {})
      request->xhrio-options
      ajax/ajax-request))

;;; Effects commands
(s/def ::no-op (s/cat :command #{:no-op}))
(s/def ::request (s/cat :command #{:request} :request ::specs/request))
(s/def ::render (s/cat :command #{:render} :target ::view/target))

(s/def ::effect (s/or ::no-op ::no-op
                      ::request ::request
                      ::render ::render))

(s/def ::effects (s/or :single ::effect
                       :multiple (s/coll-of ::effects)))

(defn handle-effects*
  [command-ch effect]
  (case-of ::effects effect
           :multiple _ (vec (mapcat (partial handle-effects* command-ch) effect))
           :single _
           (case-of ::effect effect

                    ::no-op _ nil

                    ::request {:keys [request]}
                    (let [request' (assoc request :response-format (ajax/json-response-format {:keywords? true})
                                                  :on-success (fn [%] (async/put! command-ch [[:response #::specs{:response % :request request}]]))
                                                  :on-failure #(println %))]
                      (http-effect request')
                      [[:pending request]])

                    ::render {[_ {:keys [var val]}] :target} (view/render var val))))

(defn handle-effects
  [command-ch effects]
  (let [commands (handle-effects* command-ch effects)]
    commands
    #_(if (seq commands)
      commands
      #_(async/put! command-ch commands))))