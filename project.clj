(defproject net.clojars.macielti/rabbitmq-component "0.1.0"

  :description "RabbitMQ Integrant Components"

  :url "https://github.com/macielti/rabbitmq-component"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [prismatic/schema "1.4.1"]
                 [integrant "0.13.1"]
                 [io.pedestal/pedestal.interceptor "0.7.2"]
                 [org.clojure/tools.logging "1.3.0"]
                 [com.novemberain/langohr "5.5.0"]
                 [net.clojars.macielti/common-clj "42.74.74"]]

  :profiles {:dev {:test-paths   ^:replace ["test/unit" "test/integration" "test/helpers"]

                   :plugins      [[lein-cloverage "1.2.4"]
                                  [com.github.clojure-lsp/lein-clojure-lsp "1.4.16"]
                                  [com.github.liquidz/antq "RELEASE"]]

                   :dependencies [[hashp "0.2.2"]]

                   :injections   [(require 'hashp.core)]

                   :aliases      {"clean-ns"     ["clojure-lsp" "clean-ns" "--dry"] ;; check if namespaces are clean
                                  "format"       ["clojure-lsp" "format" "--dry"] ;; check if namespaces are formatted
                                  "diagnostics"  ["clojure-lsp" "diagnostics"]
                                  "lint"         ["do" ["clean-ns"] ["format"] ["diagnostics"]]
                                  "clean-ns-fix" ["clojure-lsp" "clean-ns"]
                                  "format-fix"   ["clojure-lsp" "format"]
                                  "lint-fix"     ["do" ["clean-ns-fix"] ["format-fix"]]}}})
