(ns foo.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as string]
            [cljs.core.async :as async :refer (<! >! put! chan timeout)]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(defonce app-state (atom {:text "Hello, what is your name?!"}))

(def oauth-url
  (apply str
    (string/split
      "https://accounts.google.com/o/oauth2/auth?
      scope=email%20profile&
      state=%2Fprofile&
      redirect_uri=http%3A%2F%2Flocalhost%3A10555%2Foauth2callback&
      response_type=token&
      client_id=722505593932-g5ha7dlvljr5e0bp4l8hqmmdc5s4pumu.apps.googleusercontent.com"
       #"\s+")))

(def token_from_url
  (peek 
    (re-find #"(?:access_token=)([^&]+)" js/document.location.hash)))

(defn page []
  [:div (@app-state :text) "Kotala la-la-la"
    (if token_from_url 
      [:p "Welcome!"]
      [:a {:href oauth-url} "Login"])])

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :ws ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(defn main []
  (reagent/render-component [page] (.getElementById js/document "app"))
  (when-let [token token_from_url]
    (chsk-send! [:login/happend token])
    ; (go-loop [seconds 1]
    ;           (<! (timeout 1000))
             
    ;           (print "waited" seconds "seconds")
    ;           (recur (inc seconds))
    ))