(ns rabbitmq-component.producer
  (:require [clojure.tools.logging :as log]
            [common-clj.traceability.core :as common-traceability]
            [integrant.core :as ig]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.core :as rmq]
            [schema.core :as s]))

(defmulti produce!
  (fn [_ {:keys [current-env]}]
    current-env))

(s/defmethod produce! :prod
  [{:keys [topic payload]}
   {:keys [channel]}]
  (let [payload' (assoc payload :meta {:correlation-id (-> (common-traceability/current-correlation-id)
                                                           common-traceability/correlation-id-appended)})]
    (lb/publish channel "" topic (prn-str payload') {:persistent true})))

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
