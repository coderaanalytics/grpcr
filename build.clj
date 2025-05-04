(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.github.coderaanalytics/grpcr)
(def version (format "0.0.%s" (b/git-count-revs nil)))
(def class-dir "inst/classes")
(def uber-file (format "inst/%s.jar" (name lib)))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "inst/classes"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis}))
