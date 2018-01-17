;;; Taken from https://github.com/Day8/re-frame-http-fx/blob/master/src/day8/re_frame/http_fx.cljs

(ns provisdom.conduit.effects
  (:require [cljs.core.async :as async]
            [goog.net.ErrorCode :as errors]
            [ajax.core :as ajax]
            [lambdaisland.uniontypes :refer-macros [case-of]]
            [cljs.spec.alpha :as s]
            [provisdom.conduit.specs :as specs]
            [provisdom.conduit.view :as view]
            [cljs.core.async :as async]))

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

#_(def ^:dynamic command-ch (async/chan 1))

(defn http-effect
  [command-ch {:keys [on-success on-failure] :as request}]
  (let [request' (cond-> request
                         (#{:post :put :delete} (:method request)) (assoc :format (ajax/json-request-format))
                         true (assoc :response-format (ajax/json-response-format {:keywords? true})
                                     :on-success (or
                                                   on-success
                                                   (fn [%] (async/put! command-ch [:response #::specs{:response % :request request :time (specs/now)}])))
                                     :on-failure (or on-failure #(println %))))]
    (-> request'
        (merge {})
        request->xhrio-options
        ajax/ajax-request)))

