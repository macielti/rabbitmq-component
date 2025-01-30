(ns rabbitmq-component.producer
  (:require [clojure.tools.logging :as log]
            [common-clj.traceability.core :as traceability]
            [integrant.core :as ig]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.core :as rmq]
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

(defmethod ig/init-key ::rabbitmq-producer
  [_ {:keys [components]}]
  (log/info :starting ::rabbitmq-producer)
  (let [uri (-> components :config :rabbitmq-uri)
        connection (rmq/connect {:uri uri})
        channel (lch/open connection)]
    {:connection  connection
     :channel     channel
     :current-env (-> components :config :current-env)}))

(defmethod ig/halt-key! ::rabbitmq-producer
  [_ rabbitmq-producer]
  (log/info :stopping ::rabbitmq-producer)
  (rmq/close (:channel rabbitmq-producer))
  (rmq/close (:connection rabbitmq-producer)))
