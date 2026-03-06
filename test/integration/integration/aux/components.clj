(ns integration.aux.components
  (:require [common-test-clj.component.rabbitmq.container :as component.rabbitmq-container]
            [integrant.core :as ig]
            [rabbitmq.component.consumer :as component.consumer]
            [rabbitmq.component.producer :as component.producer]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.tools.logging]))

(taoensso.timbre.tools.logging/use-timbre)

(defn ^:private system-setup
  [consumers config]
  {::component.rabbitmq-container/rabbitmq-container {:components {}}
   ::component.consumer/rabbitmq-consumer            {:consumers  consumers
                                                      :components {:rabbitmq-container (ig/ref ::component.rabbitmq-container/rabbitmq-container)
                                                                   :config             (merge config {:current-env :test})}}
   ::component.producer/rabbitmq-producer            {:components {:rabbitmq-container (ig/ref ::component.rabbitmq-container/rabbitmq-container)
                                                                   :config             (merge config {:current-env :test})}}})

(defn start-system! [consumers config]
  (timbre/set-min-level! :debug)
  (ig/init (system-setup consumers config)))