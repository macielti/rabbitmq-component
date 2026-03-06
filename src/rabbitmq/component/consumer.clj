(ns rabbitmq.component.consumer
  (:require [clojure.tools.logging :as log]
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

(defmethod ig/init-key ::rabbitmq-consumer
  [_ {:keys [consumers components]}]
  (log/info :starting ::rabbitmq-consumer)
  (let [current-env (-> components :config :current-env)
        topics (-> components :config :topics)
        uri (case current-env
              :prod (-> components :config :rabbitmq-uri)
              :test (-> components :rabbitmq-container :url))
        connection (rmq/connect {:uri uri})
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

(defn wait-for-consumption!
  "Waits until all produced messages have been consumed in test environment.
   Takes a producer component and a consumer component.
   Should be called after all produce! calls have been made.
   Polls every 100ms until consumed count >= produced messages count.
   Times out after the specified timeout in milliseconds (default: 5000ms).
   Returns true if all messages were consumed, throws an exception on timeout."
  ([producer consumer]
   (wait-for-consumption! producer consumer 5000))
  ([producer consumer timeout-ms]
   (when-not (:consumed-count consumer)
     (throw (ex-info "wait-for-consumption! can only be used in test environment"
                     {:consumer consumer})))
   (let [start-time (System/currentTimeMillis)
         consumed (fn [] @(:consumed-count consumer))
         produced (fn [] (count @(:produced-messages producer)))]
     (loop []
       (if (>= (consumed) (produced))
         true
         (if (> (- (System/currentTimeMillis) start-time) timeout-ms)
           (throw (ex-info "Timeout waiting for message consumption"
                           {:produced-count (produced)
                            :consumed-count (consumed)
                            :timeout-ms     timeout-ms}))
           (do
             (Thread/sleep 100)
             (recur))))))))

(defmethod ig/halt-key! ::rabbitmq-consumer
  [_ rabbitmq-consumer]
  (log/info :stopping ::rabbitmq-consumer)
  (rmq/close (:channel rabbitmq-consumer))
  (rmq/close (:connection rabbitmq-consumer)))
