(ns git
  (:require [com.atomist.git.core :as git]
            [clojure.tools.logging :as log])
  (:import (java.io File)))

(defn delete-recursively [fname]
  (doseq [f (reverse (file-seq (clojure.java.io/file fname)))]
    (clojure.java.io/delete-file f)))

(defn get-tmp-dir []
  (File. "./whatever"))

(defn run-with-cloned-workspace
  "setup and teardown the cloned repo
    return the result of running f
    on the cloned dir"
  [{{:keys [owner repo sha token]} :commit} f]
  (let [dir (get-tmp-dir)]
    (try
      (log/infof "clone %s and checkout %s" repo sha)
      (git/perform dir
                   :git-clone {:org owner :repo-name repo :branch "master" :oauth-token token}
                   :git-checkout (if sha {:branch sha} {:branch "master"}))
      (log/infof "%s is cloned and %s is checked out in %s" repo sha (.getPath dir))

      (f dir)
      (catch Throwable t
        (log/error (str "error running operation on " repo " " (.getMessage t)))
        {:errors [{:could_not_clone true}]})
      (finally
        (delete-recursively dir)))))

(defn edit-in-a-cloned-workspace
  [{{:keys [owner repo sha token]} :commit} f message]
  (let [dir (get-tmp-dir)]
    (try
      (log/infof "edit %s and checkout %s" repo sha)
      (git/perform dir
                   :git-clone {:org owner :repo-name repo :branch "master" :oauth-token token}
                   :git-checkout (if sha {:branch sha} {:branch "master"}))
      (log/infof "%s is cloned and %s is checked out in %s" repo sha (.getPath dir))

      (f dir)

      (git/perform dir
                   :git-add {:file-pattern "**/**"}
                   :git-commit {:message message}
                   :git-push {:branch "master" :oauth-token token :remote "origin"})
      (catch Throwable t
        (log/error (str "error running operation on " repo " " (.getMessage t)))
        {:errors [{:could_not_clone true}]})
      (finally
        (delete-recursively dir)))))