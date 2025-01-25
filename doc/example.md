# Minimal Setup for RabbitMQ Integrant Component

First of all you need to add to your `project.clj` the dependency for the RabbitMQ Integrant Component:

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.macielti/rabbitmq-component.svg)](https://clojars.org/net.clojars.macielti/rabbitmq-component)

Second, you need to start a RabbitMQ server. You can use the official RabbitMQ Docker image.

The following code is a minimal setup for the RabbitMQ Integrant Component, setting up a simple consumer and producing a
messga to it.

```clojure
(require '[rabbitmq-component.consumer :as component.consumer]
         '[rabbitmq-component.producer :as component.producer]
         '[clojure.tools.logging :as log]
         '[schema.core :as s]
         '[integrant.core :as ig]
         '[taoensso.timbre :as timbre]
         '[taoensso.timbre.tools.logging]
         '[java-time.api :as jt])

(taoensso.timbre.tools.logging/use-timbre)
(timbre/set-min-level! :debug)

(def config {:rabbitmq-uri "amqp://guest:guest@0.0.0.0:5672"
             :topics       [{:title              "test_topic"
                             :parallel-consumers 1}]
             :current-env  :prod})

(def consumers {"test_topic" {:interceptors []
                              :schema       s/Any
                              :handler-fn   (fn [{:keys [_components payload]}]
                                              (log/info ::consuming-message :payload payload
                                                                            :as-of-local-date-time? (-> payload 
                                                                                                        :as-of 
                                                                                                        jt/local-date-time?)))}})

(def system-setup
  {::component.producer/rabbitmq-producer {:components {:config config}}
   ::component.consumer/rabbitmq-consumer {:consumers  consumers
                                           :components {:config config}}})

(def started-system (ig/init system-setup))

(component.producer/produce! {:topic   "test_topic"
                              :payload {:hello :world
                                        :as-of (jt/local-date-time)}}
                             (::component.producer/rabbitmq-producer started-system))
```
