(ns rabbitmq-component.consumer
  (:require [clojure.tools.logging :as log]
            [clojure.tools.reader.edn :as edn]
            [integrant.core :as ig]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as chain]
            [langohr.channel :as lch]
            [langohr.consumers :as lc]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [schema.core :as s])
  (:import (clojure.lang IFn)))

(s/defschema Consumers
  {s/Str {:schema     s/Any
          :handler-fn IFn}})

(defn handler-fn->interceptor
  [handler-fn]
  (interceptor/interceptor
   {:name  ::consumer-handler-fn-interceptor
    :enter handler-fn}))

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
      (lq/declare channel title {:exclusive false :auto-delete false})
      (dotimes [_n (or parallel-consumers 4)]
        (lc/subscribe channel title (fn [_channel _meta payload]
                                      (try
                                        (s/validate schema (-> (String. payload "UTF-8") edn/read-string (dissoc :meta)))
                                        (chain/execute {:payload    (edn/read-string (String. payload "UTF-8"))
                                                        :components components}
                                                       (conj (or interceptors []) (handler-fn->interceptor handler-fn)))
                                        (catch Exception ex
                                          (log/error ::error-while-consuming-message :exception ex))))
                      {:auto-ack true})))
    {:channel    channel
     :connection connection}))

(defmethod ig/halt-key! ::rabbitmq-consumer
  [_ rabbitmq-consumer]
  (log/info :stopping ::rabbitmq-consumer)
  (rmq/close (:channel rabbitmq-consumer))
  (rmq/close (:connection rabbitmq-consumer)))
