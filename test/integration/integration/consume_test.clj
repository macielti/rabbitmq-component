(ns integration.consume-test
  (:require [clojure.test :refer :all]
            [hashp.core]
            [integration.aux.components :as aux.components]
            [java-time.api :as jt]
            [matcher-combinators.test :refer [match?]]
            [rabbitmq.component.consumer :as component.consumer]
            [rabbitmq.component.producer :as component.producer]
            [schema.core :as s]
            [schema.core :as schema]
            [schema.test :as st]))

(def test-context (atom nil))
(def as-of (jt/instant))

(def consumers
  {"test_topic" {:schema       schema/Any
                 :interceptors []
                 :handler-fn   (fn [context] (reset! test-context (:payload context)))}})

(def config
  {:topics      [{:title              "test_topic"
                  :parallel-consumers 1}]
   :current-env :test})

(st/deftest consumer-component-test
  (testing "We can consume messages from RabbitMQ"
    (let [system (aux.components/start-system! consumers config)
          producer (get system ::component.producer/rabbitmq-producer)
          consumer (get system ::component.consumer/rabbitmq-consumer)
          message {:topic   "test_topic"
                   :payload {:foo        "bar"
                             :created-at as-of}}]

      (component.producer/produce! message producer)

      (component.consumer/wait-for-consumption! producer consumer)

      (testing "The producer component respect the schema"
        (is (= producer
               (s/validate component.producer/RabbitMQProducerComponent producer))))

      (is (= [message]
             (component.producer/produced-messages producer)))

      (is (match? {:foo        "bar"
                   :created-at jt/instant?
                   :meta       {:correlation-id "DEFAULT"}}
                  @test-context)))))
