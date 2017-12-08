(defproject automation-api-clj "0.1.10-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.async "0.2.395"]

                 ;; websocket
                 [org.clojure/data.json        "0.2.6"]
                 [clj-http                     "3.3.0"]
                 [stylefruits/gniazdo "1.0.1"]

                 ;; util
                 [mount "0.1.11"]
                 [environ "1.0.0"]
                 [cprop "0.1.11"]
                 [diehard "0.5.0"]
                 [com.rpl/specter "1.0.5"]

                 ;; logging
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [io.clj/logging "0.8.1"]]

  :plugins [[lein-environ "1.1.0"]
            [environ/environ.lein "0.3.1"]]

  :min-lein-version "2.6.1"

  :vcs :git

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" "releases"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [ring/ring-mock "0.3.0"]
                                  [environ "1.1.0"]
                                  [org.clojure/tools.nrepl      "0.2.12"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :source-paths   ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :repl-options {:init-ns user}}}

  :repositories [["releases" {:url      "https://sforzando.artifactoryonline.com/sforzando/libs-release-local"
                              :username [:gpg :env/artifactory_user]
                              :password [:gpg :env/artifactory_pwd]}]
                 ["plugins" {:url      "https://sforzando.artifactoryonline.com/sforzando/plugins-release"
                             :username [:gpg :env/artifactory_user]
                             :password [:gpg :env/artifactory_pwd]}]])
