(ns rabbitmq-component.consumer
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as chain]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.consumers :as lc]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [schema.core :as s]
            [taoensso.nippy :as nippy])
  (:import (clojure.lang IFn)))

(s/defschema Consumers
  {s/Str {:schema       s/Any
          :interceptors [s/Any]
          :handler-fn   IFn}})

(defn handler-fn->interceptor
  [handler-fn]
  (interceptor/interceptor
    {:name  ::consumer-handler-fn-interceptor
     :enter (fn [context]
              (handler-fn context)
              context)}))

(defmethod ig/init-key ::rabbitmq-consumer
  [_ {:keys [consumers components]}]
  (log/info :starting ::rabbitmq-consumer)
  (let [topics (-> components :config :topics)
        uri (-> components :config :rabbitmq-uri)
        connection (rmq/connect {:uri uri})
        channel (lch/open connection)]

    (s/validate Consumers consumers)

    (doseq [{:keys [title parallel-consumers]} topics
            :let [schema (get-in consumers [title :schema])
                  interceptors (get-in consumers [title :interceptors])
                  handler-fn (get-in consumers [title :handler-fn])]]
      (lq/declare channel title {:exclusive   false
                                 :auto-delete false
                                 :durable     true})
      (dotimes [_n (or parallel-consumers 4)]
        (lc/subscribe channel title (fn [_channel meta payload]
                                      (try
                                        (s/validate schema (-> (nippy/thaw-from-string (String. payload "UTF-8")) (dissoc :meta)))
                                        (chain/execute {:payload    (nippy/thaw-from-string (String. payload "UTF-8"))
                                                        :components components}
                                                       (conj (or interceptors []) (handler-fn->interceptor handler-fn)))
                                        (catch Exception ex
                                          (log/error ::error-while-consuming-message :exception ex)
                                          (lb/reject channel (:delivery-tag meta) true)
                                          (throw ex)))
                                      (lb/ack channel (:delivery-tag meta)))
                      {:auto-ack false})))
    {:channel    channel
     :connection connection}))

(defmethod ig/halt-key! ::rabbitmq-consumer
  [_ rabbitmq-consumer]
  (log/info :stopping ::rabbitmq-consumer)
  (rmq/close (:channel rabbitmq-consumer))
  (rmq/close (:connection rabbitmq-consumer)))
