(ns rabbitmq.component.consumer
  (:require [clojure.tools.logging :as log]
            [diehard.core :as dh]
            [integrant.core :as ig]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as chain]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.consumers :as lc]
            [langohr.core :as rmq]
            [langohr.queue :as lq]
            [medley.core :as misc]
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

(defn consumed-count
  "Returns the number of consumed messages in test environment.
   Returns nil in production environment."
  [{:keys [consumed-count]}]
  (when consumed-count
    @consumed-count))

(defn wait-for-consumption!
  "Waits until the number of consumed messages reaches the expected count.
   Useful in integration tests to ensure all produced messages have been consumed
   before proceeding with assertions.
   Options:
     :timeout-ms - Maximum time to wait in milliseconds (default: 10000)"
  [consumer expected-count & {:keys [timeout-ms] :or {timeout-ms 10000}}]
  (loop [start (System/currentTimeMillis)]
    (let [current (or (consumed-count consumer) 0)]
      (when (< current expected-count)
        (if (> (- (System/currentTimeMillis) start) timeout-ms)
          (throw (ex-info "Timeout waiting for messages to be consumed"
                          {:expected expected-count :consumed current :timeout-ms timeout-ms}))
          (do (Thread/sleep 100)
              (recur start)))))))

(defmethod ig/init-key ::rabbitmq-consumer
  [_ {:keys [consumers components]}]
  (log/info :starting ::rabbitmq-consumer)
  (let [current-env (-> components :config :current-env)
        topics (-> components :config :topics)
        uri (case current-env
              :prod (-> components :config :rabbitmq-uri)
              :test (-> components :rabbitmq-container :url))
        connection (dh/with-retry {:max-retries 5
                                   :backoff-ms  [1000 15000]
                                   :retry-on    Exception
                                   :on-retry    (fn [_ _]
                                                  (log/warn :retrying-rabbitmq-consumer-connection))}
                     (rmq/connect {:uri uri}))
        channel (lch/open connection)
        consumed-count (when (= current-env :test) (atom 0))]

    (s/validate Consumers consumers)

    (doseq [{:keys [title parallel-consumers]} topics
            :let [schema (get-in consumers [title :schema])
                  interceptors (get-in consumers [title :interceptors])
                  handler-fn (get-in consumers [title :handler-fn])]]
      (lq/declare channel title {:exclusive   false
                                 :auto-delete false
                                 :durable     true})
      (dotimes [_n (or parallel-consumers 4)]
        (let [consumer-channel (lch/open connection)]
          (lc/subscribe consumer-channel title (fn [handler-channel meta payload]
                                                 (try
                                                   (s/validate schema (-> (nippy/thaw-from-string (String. payload "UTF-8")) (dissoc :meta)))
                                                   (chain/execute {:payload    (nippy/thaw-from-string (String. payload "UTF-8"))
                                                                   :components components}
                                                                  (conj (or interceptors []) (handler-fn->interceptor handler-fn)))
                                                   (catch Exception ex
                                                     (log/error ::error-while-consuming-message :topic title :exception ex)
                                                     (lb/reject handler-channel (:delivery-tag meta) true)
                                                     (throw ex)))
                                                 (lb/ack handler-channel (:delivery-tag meta))
                                                 (when consumed-count
                                                   (swap! consumed-count inc)))
                        {:auto-ack false}))))
    (misc/assoc-some {:channel    channel
                      :connection connection}
                     :consumed-count consumed-count)))

(defmethod ig/halt-key! ::rabbitmq-consumer
  [_ rabbitmq-consumer]
  (log/info :stopping ::rabbitmq-consumer)
  (rmq/close (:channel rabbitmq-consumer))
  (rmq/close (:connection rabbitmq-consumer)))
