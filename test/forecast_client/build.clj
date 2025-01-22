(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.github.coderaanalytics/grpcr)
(def version (format "0.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def uber-file (format "target/%s-client.jar" (name lib)))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src/clj" "build/classes/java/main"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :src-dirs ["src/clj"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'software.codera.forecast.core}))
