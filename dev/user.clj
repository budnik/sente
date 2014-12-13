(ns user
  (:require [leiningen.core.main :as lein]
            [clojure.java.io :as io]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [ring.middleware.reload :as reload]
            [environ.core :refer [env]]
            [taoensso.sente :as sente]
            [ring.adapter.jetty :refer [run-jetty]]))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]} (sente/make-channel-socket! {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(defroutes routes
  (resources "/")
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (GET "/*" req (io/resource "index.html")))


(def my-ring-handler
  (let [ring-defaults-config
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
          {:read-token (fn [req] (-> req :params :csrf-token))})]
   (ring.middleware.defaults/wrap-defaults my-routes ring-defaults-config)))

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (logf "Event: %s" event)
  (event-msg-handler ev-msg))

#+clj
(do ; Server-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      (logf "Unhandled event: %s" event)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

  ;; Add your (defmethod event-msg-handler <event-id> [ev-msg] <body>)s here...
  )

(def http-handler (reload/wrap-reload (api #'routes)))

(defn run [& [port]]
  (defonce ^:private server
    (let [port (Integer. (or port (env :port) 10555))]
      (print "Starting web server on port" port ".\n")
      (run-jetty http-handler {:port port
                               :join? false})))
  server)

(defn start-figwheel []
  (future
    (print "Starting figwheel.\n")
    (lein/-main ["figwheel"])))

(defn -main [& [port]]
  (run port))
