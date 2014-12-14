(ns user
  (:use [org.httpkit.server :only [run-server]])
  (:require [leiningen.core.main :as lein]
            [clojure.java.io :as io]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults]
            [environ.core :refer [env]]
            [taoensso.sente :as sente]))

(defonce sente-socket (sente/make-channel-socket! {}))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]} sente-socket]
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

(defn- event-msg-handler
  [{:as ev-msg :keys [ring-req event ?reply-fn]} _]
  (let [session (:session ring-req)
        uid     (:uid session)
        [id data :as ev] event]
    (printf "Event: %s (UID: %s)" ev uid)))

(defonce chsk-router
    (sente/start-chsk-router-loop! event-msg-handler ch-chsk))

(def http-handler
  (let [ring-defaults-config
        (assoc-in ring.middleware.defaults/site-defaults [:security :anti-forgery]
          {:read-token (fn [req] (-> req :params :csrf-token))})]

    ;; NB: Sente requires the Ring `wrap-params` + `wrap-keyword-params`
    ;; middleware to work. These are included with
    ;; `ring.middleware.defaults/wrap-defaults` - but you'll need to ensure
    ;; that they're included yourself if you're not using `wrap-defaults`.
    ;;
    (ring.middleware.defaults/wrap-defaults routes ring-defaults-config)))


; (defmulti event-msg-handler :id) ; Dispatch on event-id
; ;; Wrap for logging, catching, etc.:
; (defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
;   (print event)
;   (event-msg-handler ev-msg))
; (do ; Server-side methods
;   (defmethod event-msg-handler :default
;     [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
;     (let [session (:session ring-req)
;           uid     (:uid     session)]
;       (print (str "Unhandled event: " event))
;       (when ?reply-fn
;         (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

;   ;; Add your (defmethod event-msg-handler <event-id> [ev-msg] <body>)s here...
;   )


(defn run [& [port]]
  (defonce ^:private server
    (let [port (Integer. (or port (env :port) 10555))]
      (print "Starting web server on port " port)
      (run-server (reload/wrap-reload (var http-handler)) {:port port
                               :join? false})))
  server)

(defn start-figwheel []
  (future
    (print "Starting figwheel.\n")
    (lein/-main ["figwheel"])))

(defn -main [& [port]]
  (run port))
