(ns automation.core
  (:require [gniazdo.core :as ws]
            [mount.core :as mount]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [config-service.core :as cs]
            [clojure.tools.logging :as log]
            [automation.registry :as registry]
            [clojure.core.async :as async]
            [automation.restart :refer [with-restart]])
  (:import (clojure.lang ExceptionInfo)
           (java.util UUID)))

(def staging-url "https://automation-staging.atomist.services/registration")
(def prod-url "https://automation.atomist.com/registration")

(defn get-token []
  (let [gt (cs/get-config-value [:github-token])]
    (str "token " (or (:value gt) gt))))

(defn select-url []
  (condp = (cs/get-config-value [:domain])
    "staging.atomist.services." staging-url
    "prod.atomist.services." prod-url
    staging-url))

(defn get-registration []
  (->> (client/get (select-url) {:headers {:authorization (get-token)}
                                 :as      :json})
       :body))

(defn delete-registration [session-id]
  (client/delete (format (str (select-url) "/%s") session-id)
                 {:headers {:authorization (get-token)}}))

(defn register
  "Register for events and listen on websocket"
  []
  (let [auth-header (get-token)
        url (select-url)]
    (log/info (registry/registration))
    (log/info url)
    (-> (client/post url
                     {:body           (json/write-str (registry/registration))
                      :content-type   :json
                      :headers        {:authorization auth-header}
                      :socket-timeout 10000
                      :conn-timeout   5000
                      :accept         :json
                      })
        :body
        (json/read-str :key-fn keyword))))

(defn get-parameter-value [request parameter-name]
  (some->> (get-in request [:parameters])
           (filter #(= parameter-name (:name %)))
           first
           :value))

(defn mapped-parameter-value [request parameter-name]
  (some->> (get-in request [:mapped_parameters])
           (filter #(= parameter-name (:name %)))
           first
           :value))

(defn get-secret-value [request secret-name]
  (some->> (get-in request [:secrets])
           (filter #(= secret-name (:name %)))
           first
           :value))

(declare simple-message failed-status success-status)

(def ^:private reg-socket nil)

(defn- connect-automation-api
  [channel-closed]
  (let [response (register)]
    (log/info "response " response)

    (ws/connect
      (:url response)
      :on-receive (fn [msg]
                    (let [o (json/read-str msg :key-fn keyword)]
                      (if (:ping o)
                        (ws/send-msg reg-socket (json/write-str {:pong (:ping o)}))
                        (if (:data o)
                          (do
                            (log/info "Received events" (with-out-str (clojure.pprint/pprint o)))
                            (registry/event-handler o))
                          (do
                            (log/info "Received commands:\n" (with-out-str (clojure.pprint/pprint (dissoc o :secrets))))
                            (try
                              (registry/command-handler o)
                              (success-status o)
                              (catch ExceptionInfo ex
                                (simple-message o (.getMessage ex))
                                (simple-message o (str "```" (with-out-str (clojure.pprint/pprint (ex-data ex))) "```"))
                                (failed-status o))
                              (catch Throwable t
                                (log/error t (str "problem in processing the command loop" (.getMessage t)))
                                (failed-status o))))))))
      :on-error (fn [e] (log/error e "error processing websocket"))
      :on-close (fn [code message]
                  (log/warnf "websocket closing (%d):  %s" code message)
                  (async/go (async/>! channel-closed :channel-closed))))))

(defn- close-automation-api [conn]
  (try
    (ws/close conn)
    (catch Throwable t (log/error t (.getMessage t)))))

(defn- send-new-socket [socket]
  (log/info "updating current api websocket")
  (alter-var-root #'reg-socket (constantly socket)))

(declare api-connection)
(mount/defstate api-connection
                :start (with-restart #'connect-automation-api #'close-automation-api #'send-new-socket)
                :stop (async/>!! api-connection :stop))

(defn- send-on-socket [x]
  (log/info "send-on-socket " x)
  (ws/send-msg reg-socket (json/json-str x)))

(defn success-status [command]
  (-> (select-keys command [:corrid :correlation_context :users :channels])
      (assoc :content_type "application/x-atomist-status+json")
      (assoc :message (json/json-str {:status "success"}))
      (send-on-socket)))

(defn failed-status [command]
  (-> (select-keys command [:corrid :correlation_context :users :channels])
      (assoc :content_type "application/x-atomist-status+json")
      (assoc :message (json/json-str {:status "failure"}))
      (send-on-socket)))

(defn simple-message [command s]
  (-> (select-keys command [:corrid :correlation_context :users :channels])
      (assoc :content_type "text/plain")
      (assoc :message s)
      (send-on-socket)))

(defn pprint-data-message [command data]
  (let [message (str "```"
                     (-> data
                         (clojure.pprint/pprint)
                         (with-out-str))
                     "```")]
    (simple-message command message)))

(defn guid []
  (UUID/randomUUID))

(defn update-when-seq [a-map k fn]
  (if (seq (get a-map k))
    (update a-map k fn)
    a-map))

(defn channel [o channel]
  (-> o
      (assoc :channels [channel])))

(defn user [o user]
  (assoc o :users [user]))

(defn get-team-id
  [o]
  ;; we also have (-> o :correlation_context :team :id
  (or (-> o :extensions :team_id)
      (-> o :team :id)))

(defn actionable-message
  "  params
       command - incoming Command Request data
       slack   - slack Message data where all actions may refer to
                 other CommandHandlers"
  [command slack]
  (let [num (atom 0)
        slack-with-action-ids
        (update-when-seq
          slack :attachments
          (fn [attachments]
            (mapv
              (fn [attachment]
                (update-when-seq
                  attachment :actions
                  (fn [actions]
                    (mapv (fn [action]
                            (if (:rug action)
                              (assoc-in action [:rug :id]
                                        (str (get-in action [:rug :rug :name])
                                             "-"
                                             (swap! num inc)))
                              action)) actions))))
              attachments)))]

    (-> (select-keys command [:corrid :correlation_context :users :channels])
        (assoc :content_type "application/x-atomist-slack+json")
        (assoc :message
               (-> slack-with-action-ids
                   (update-when-seq
                     :attachments
                     (fn [attachments]
                       (mapv
                         (fn [attachment]
                           (update-when-seq
                             attachment :actions
                             (fn [actions]
                               (mapv
                                 (fn [action]
                                   (if (:rug action)
                                     (let [action-id (get-in action [:rug :id])]
                                       (case (:type action)
                                         "button"
                                         (-> action
                                             (dissoc :rug)
                                             (assoc :name "rug")
                                             (assoc :value action-id))
                                         "select"
                                         (-> action
                                             (dissoc :rug)
                                             (assoc :name (str "rug::" action-id)))
                                         action))
                                     action))
                                 actions))))
                         attachments)))
                   (json/json-str)))
        (assoc :actions (->> (:attachments slack-with-action-ids)
                             (mapcat :actions)
                             (filter :rug)
                             (mapv :rug)))
        (send-on-socket))))
