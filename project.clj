(defproject net.clojars.macielti/rabbitmq "0.3.0"

  :description "RabbitMQ Integrant Components"

  :url "https://github.com/macielti/rabbitmq-component"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.4"]
                 [prismatic/schema "1.4.1"]
                 [integrant "1.0.1"]
                 [io.pedestal/pedestal.interceptor "0.8.1"]
                 [org.clojure/tools.logging "1.3.1"]
                 [com.novemberain/langohr "5.6.0"]
                 [diehard "0.12.0"]
                 [net.clojars.macielti/common-clj "46.1.4"]
                 [com.taoensso/nippy "3.6.0"]
                 [org.testcontainers/rabbitmq "1.21.4"]]

  :profiles {:dev {:test-paths   ^:replace ["test/unit" "test/integration" "test/helpers"]

                   :plugins      [[lein-cloverage "1.2.4"]
                                  [com.github.clojure-lsp/lein-clojure-lsp "2.0.14"]
                                  [com.github.liquidz/antq "RELEASE"]]

                   :dependencies [[com.taoensso/timbre "6.8.0"]
                                  [com.taoensso/encore "3.159.0"]
                                  [net.clojars.macielti/common-test-clj "7.1.0"]
                                  [nubank/matcher-combinators "3.10.0"]
                                  [hashp "0.2.2"]]

                   :injections   [(require 'hashp.core)]

                   :aliases      {"clean-ns"     ["clojure-lsp" "clean-ns" "--dry"] ;; check if namespaces are clean
                                  "format"       ["clojure-lsp" "format" "--dry"] ;; check if namespaces are formatted
                                  "diagnostics"  ["clojure-lsp" "diagnostics"]
                                  "lint"         ["do" ["clean-ns"] ["format"] ["diagnostics"]]
                                  "clean-ns-fix" ["clojure-lsp" "clean-ns"]
                                  "format-fix"   ["clojure-lsp" "format"]
                                  "lint-fix"     ["do" ["clean-ns-fix"] ["format-fix"]]}}})
