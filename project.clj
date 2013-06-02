(defproject mss "0.1.0-SNAPSHOT"
  :description "multi socket server"
  :url "http://github.com/prozz/mss"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/timbre "2.0.0"]
                 [org.clojure/tools.cli "0.2.2"]]
  :global-vars {*warn-on-reflection* true}
  :main mss.core)
