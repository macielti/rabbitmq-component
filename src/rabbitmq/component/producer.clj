(ns rabbitmq.component.producer
  (:require [clojure.tools.logging :as log]
            [common-clj.traceability.core :as traceability]
            [integrant.core :as ig]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.core :as rmq]
            [medley.core :as misc]
            [schema.core :as s]
            [taoensso.nippy :as nippy]))

(defmulti produce!
  (fn [_ {:keys [current-env]}]
    current-env))

(s/defmethod produce! :prod
  [{:keys [topic payload]}
   {:keys [channel]}]
  (let [payload' (assoc payload :meta {:correlation-id (traceability/current-correlation-id!)})]
    (lb/publish channel "" topic (nippy/freeze-to-string payload') {:persistent true})))

(s/defmethod produce! :test
  [{:keys [topic payload] :as message}
   {:keys [channel produced-messages]}]
  (let [payload' (assoc payload :meta {:correlation-id (traceability/current-correlation-id!)})]
    (lb/publish channel "" topic (nippy/freeze-to-string payload') {:persistent true})
    (swap! produced-messages conj message)))

(defn produced-messages
  "Returns the list of produced messages in test environment.
   Returns nil in production environment."
  [{:keys [current-env produced-messages]}]
  (when (= current-env :test)
    @produced-messages))

(defmethod ig/init-key ::rabbitmq-producer
  [_ {:keys [components]}]
  (log/info :starting ::rabbitmq-producer)
  (let [current-env (-> components :config :current-env)
        uri (case current-env
              :prod (-> components :config :rabbitmq-uri)
              :test (-> components :rabbitmq-container :url))
        connection (rmq/connect {:uri uri})
        channel (lch/open connection)]
    (misc/assoc-some {:connection  connection
                      :channel     channel
                      :current-env (-> components :config :current-env)}
                     :produced-messages (when (= current-env :test) (atom [])))))

(defmethod ig/halt-key! ::rabbitmq-producer
  [_ rabbitmq-producer]
  (log/info :stopping ::rabbitmq-producer)
  (rmq/close (:channel rabbitmq-producer))
  (rmq/close (:connection rabbitmq-producer)))
