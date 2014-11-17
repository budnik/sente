(ns foo.core
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string]))

(defonce app-state (atom {:text "Hello, what is your name? "}))

(def oauth-url
  (apply str
    (string/split
        "https://accounts.google.com/o/oauth2/auth?
        scope=email%20profile&
        state=%2Fprofile&
        redirect_uri=http%3A%2F%2Flocalhost%3A10555%2Foauth2callback&
        response_type=token&
        client_id=722505593932-g5ha7dlvljr5e0bp4l8hqmmdc5s4pumu.apps.googleusercontent.com"
         #"\s+")
))

(defn page []
  [:div (@app-state :text) "Dmytro Budnyk"
    [:a {:target  "_blank" :href oauth-url} "Login"]])

(defn main []
  (reagent/render-component [page] (.getElementById js/document "app")))
