(ns provisdom.eala-dubh.todo.view
  (:require [reagent.core :as r]
            [cljs.core.match :refer-macros [match]]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.specs :as specs]
            [clojure.core.async :as async :refer [put!]]))

(defonce view-state (r/atom {}))
(defonce intent-ch (async/chan))

(defn update-view
  [commands]
  (doseq [command commands]
    (match command
           [:render k v] (swap! view-state assoc k v)
           :else nil)))

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
                     :on-click #(put! intent-ch [:update :visibility name])})]
    [:div
     [:span#todo-count
      [:strong active-count] " " (case active-count 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :completed) "Completed"]]]
     (when show-clear
       [:button#clear-completed {:on-click #(put! intent-ch [:retract-completed :todos])}
        "Clear completed " completed-count])]))

(defn todo-item []
  (fn [{::specs/keys [id done edit title]}]
    [:li {:class (str (if done "completed ")
                      (if edit "editing"))}
     [:div.view
      [:input.toggle {:type      "checkbox" :checked done
                      :on-change #(put! intent-ch [:update :todo id {::specs/done (not done)}])}]
      [:label {:on-double-click #(put! intent-ch [:update :todo id {::specs/edit true}])} title]
      [:button.destroy {:on-click #(put! intent-ch [:retract :todo id])}]]
     (when edit
       [todo-edit {:class   "edit" :title title
                   :on-save #(put! intent-ch [:update :todo id {::specs/title %}])
                   :on-stop #(put! intent-ch [:update :todo id {::specs/edit false}])}])]))

(defn todo-app [props]
  (fn []
    (let [{:keys [todo-list active-count completed-count all-completed show-clear] :as vs} @view-state]
      [:div
       [:section#todoapp
        [:header#header
         [:h1 "todos"]
         [todo-input {:id          "new-todo"
                      :placeholder "What needs to be done?"
                      :on-save     #(put! intent-ch [:insert :todo (todo/new-todo %)])}]]
        [:div
         (when (-> todo-list count pos?)
           [:section#main
            [:input#toggle-all {:type      "checkbox" :checked all-completed
                                :on-change #(put! intent-ch [:complete-all :todos (not all-completed)])}]
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