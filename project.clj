(defproject com.yetanalytics/testomic "0.1.0"
  :description "Testing utilities and macros for use with datomic."
  :url "http://github.com/yetanalytics/testomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.datomic/datomic-free "0.9.5385"
                  :exclusions [commons-codec]]
                 [io.rkn/conformity "0.4.0"]
                 [crypto-random "1.2.0"]])
