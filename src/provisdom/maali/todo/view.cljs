(ns provisdom.maali.todo.view
  (:require [cljs.core.async :as async :refer [put!]]
            [cljs.spec.alpha :as s]
            [reagent.core :as r]
            [lambdaisland.uniontypes :refer-macros [case-of]]
            [provisdom.maali.todo.rules :as todo]
            [provisdom.maali.todo.specs :as specs]
            [provisdom.maali.todo.commands :as command]))

(defonce view-state (r/atom {}))
(defonce intent-ch (async/chan))

(defn update-view
  [commands]
  (doseq [command commands]
    (case-of ::command/view-command command
             ::command/no-op _ nil
             ::command/todo-list {:keys [key value]} (swap! view-state assoc key value)
             ::command/visibility {:keys [key value]} (swap! view-state assoc key value)
             ::command/active-count {:keys [key value]} (swap! view-state assoc key value)
             ::command/completed-count {:keys [key value]} (swap! view-state assoc key value)
             ::command/all-completed {:keys [key value]} (swap! view-state assoc key value)
             ::command/show-clear {:keys [key value]} (swap! view-state assoc key value))))

(s/fdef update-view
        :args (s/cat :commands (s/coll-of ::command/view-command))
        :ret any?)

(defn do!
  [command]
  (put! intent-ch command))

(s/fdef do!
        :args (s/cat :command ::command/command)
        :ret nil?)

(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (r/atom title)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input {:type        "text" :value @val
               :id          id :class class :placeholder placeholder
               :on-blur     save
               :on-change   #(reset! val (-> % .-target .-value))
               :on-key-down #(case (.-which %)
                               13 (save)
                               27 (stop)
                               nil)}])))

(def todo-edit (with-meta todo-input
                          {:component-did-mount #(.focus (r/dom-node %))}))

(defn todo-stats [{:keys [visibility active-count completed-count show-clear]}]
  (let [props-for (fn [name]
                    {:class    (if (= name visibility) "selected")
                     :on-click #(do! [:update-visibility name])})]
    [:div
     [:span#todo-count
      [:strong active-count] " " (case active-count 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :completed) "Completed"]]]
     (when show-clear
       [:button#clear-completed {:on-click #(do! [:retract-completed])}
        "Clear completed " completed-count])]))

(defn todo-item []
  (fn [{::specs/keys [id done edit title]}]
    [:li {:class (str (if done "completed ")
                      (if edit "editing"))}
     [:div.view
      [:input.toggle {:type      "checkbox" :checked done
                      :on-change #(do! [:update id {::specs/done (not done)}])}]
      [:label {:on-double-click #(do! [:update id {::specs/edit true}])} title]
      [:button.destroy {:on-click #(do! [:retract id])}]]
     (when edit
       [todo-edit {:class   "edit" :title title
                   :on-save #(do! [:update id {::specs/title %}])
                   :on-stop #(do! [:update id {::specs/edit false}])}])]))

(defn todo-app [props]
  (fn []
    (let [{:keys [todo-list all-completed] :as vs} @view-state]
      [:div
       [:section#todoapp
        [:header#header
         [:h1 "todos"]
         [todo-input {:id          "new-todo"
                      :placeholder "What needs to be done?"
                      :on-save     #(do! [:insert (specs/new-todo %)])}]]
        [:div
         (when (-> todo-list count pos?)
           [:section#main
            [:input#toggle-all {:type      "checkbox" :checked all-completed
                                :on-change #(do! [:complete-all (not all-completed)])}]
            [:label {:for "toggle-all"} "Mark all as complete"]
            [:ul#todo-list
             (for [todo todo-list]
               ^{:key (::specs/id todo)} [todo-item todo])]])
         [:footer#footer
          [todo-stats vs]]]]
       [:footer#info
        [:p "Double-click to edit a todo"]]])))

(defn ^:export run []
  (r/render [todo-app]
            (js/document.getElementById "app")))