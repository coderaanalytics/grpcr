(ns software.codera.grpcr.core
  (:require [clojure.core.async :refer [>!! <!! chan close!]])
  (:import [io.grpc BindableService CallOptions ManagedChannel ManagedChannelBuilder MethodDescriptor MethodDescriptor$Marshaller MethodDescriptor$MethodType ServerBuilder ServerServiceDefinition ServerServiceDefinition$Builder Status StatusRuntimeException]
           [io.grpc.stub ClientCalls ServerCalls ServerCalls$UnaryMethod StreamObserver]
           [org.rosuda.REngine REXP REXPReference REngineException REXPMismatchException REXPLanguage RList REXPRaw REXPString]
           [org.rosuda.REngine.Rserve RConnection RserveException StartRserve]
           [java.util HashMap]))

(defn call-ocap
  ([con ocap]
   (.callOCAP con
              (->> (into-array REXP [ocap])
                   (RList.)
                   (REXPLanguage.))))
  ([con ocap & args]
   (.callOCAP con
              (->> (cons ocap args)
                   (into-array REXP)
                   (RList.)
                   (REXPLanguage.)))))

(defn eval-expr
  ([rengine ref]
   (.eval rengine
          (->> (into-array REXP [ref])
               (RList.)
               (REXPLanguage.))
          nil
          true))
  ([rengine ref & args]
   (.eval rengine 
          (->> (cons ref args)
               (into-array REXP)
               (RList.)
               (REXPLanguage.))
          nil
          true)))

(defn ocap->services [con]
  (let [ocap (->> (.capabilities con)
                  (call-ocap con)
                  (.asList))]
    (->> (.keys ocap)
         (map #(re-find #"(.+)\.(\w+)$" %))
         (map (fn [[x y z]] {y {z (.at ocap x)}}))
         (reduce (partial merge-with merge)))))

(gen-class
  :name software.codera.grpcr.Server
  :prefix "server-"
  :main false
  :methods [^{:static true} [jriServer [java.util.HashMap int] io.grpc.Server]
            ^{:static true} [rserveServer [java.lang.String int] io.grpc.Server]
            ^{:static true} [shutdownRserve [] void]])

(set! *warn-on-reflection* true)

(defn byte-marshaller []
  (reify MethodDescriptor$Marshaller
    (parse [_ stream]
      (.readAllBytes stream))
    (stream [_ value]
      (java.io.ByteArrayInputStream. value))))

(defmulti create-method (fn [method _] ((comp type :ref) method)))

(defmethod create-method REXPReference
  [method _]
  {:name (:method method)
   :method (fn [request response-observer]
             (try
               (let [jri (.getEngine ^REXPReference (:ref method))
                     method (:ref method)
                     data (REXPRaw. request)]
                 (.onNext ^StreamObserver response-observer
                          ^bytes (.asBytes ^REXP (eval-expr jri method data)))
                 (.onCompleted ^StreamObserver response-observer)
                 nil)
               (catch REngineException e
                 (println ;;FIXME: log this
                          (ex-info "Rengine failed"
                                   {:type :rengine
                                    :cause "Error invoking 'call'."}
                                   e))
                 (.onError ^StreamObserver response-observer
                           (.asRuntimeException Status/INTERNAL)))
               (catch REXPMismatchException e
                 (println ;;FIXME: log this
                          (ex-info "R expression mismatch"
                                   {:type :rexp-mismatch
                                    :cause "Error invoking 'call'."}
                                   e))
                 (.onError ^StreamObserver response-observer
                           (.asRuntimeException Status/INTERNAL)))))})

(defmethod create-method REXPString
  [method connection-pool]
  {:name (:method method)
   :method (fn [request response-observer]
             (try
               (let [{services :services
                      rserve :connection
                      :as connection} (<!! connection-pool)
                     method (-> services
                                (get (:service method))
                                (get (:method method)))
                     data (REXPRaw. request)]
                 (.onNext ^StreamObserver response-observer
                          ^bytes (.asBytes ^REXP (call-ocap rserve method data)))
                 (.onCompleted ^StreamObserver response-observer)
                 (>!! connection-pool connection)
                 nil)
               (catch RserveException e
                 (println ;;FIXME: log this
                          (ex-info "Rserve connection failed"
                                   {:type :rserve
                                    :cause "Error invoking 'call'."}
                                   e))
                 (.onError ^StreamObserver response-observer
                           (.asRuntimeException Status/INTERNAL)))
               (catch REXPMismatchException e
                 (println ;;FIXME: log this
                          (ex-info "R expression mismatch"
                                   {:type :rexp-mismatch
                                    :cause "Error invoking 'call'."}
                                   e))
                 (.onError ^StreamObserver response-observer
                           (.asRuntimeException Status/INTERNAL)))))})

(defn create-methods [service methods connection-pool]
  (reduce-kv #(conj %1 (create-method {:service service :method %2 :ref %3} connection-pool)) [] methods))

(defn create-method-descriptor [service-name method-name]
  (.. (MethodDescriptor/newBuilder (byte-marshaller) (byte-marshaller))
      (setFullMethodName (MethodDescriptor/generateFullMethodName service-name method-name))
      (setType MethodDescriptor$MethodType/UNARY)
      (setSampledToLocalTracing true)
      build))

(defn create-services 
  ([services]
   (create-services services nil))
  ([services connection-pool]
   (reduce-kv
     (fn [services service-name methods]
       (conj services
             (reify BindableService
               (bindService [_]
                 (let [ssd (ServerServiceDefinition/builder ^String service-name)]
                   (doseq [method (create-methods service-name methods connection-pool)]
                     (.addMethod ^ServerServiceDefinition$Builder ssd
                                 (create-method-descriptor service-name (:name method))
                                 (ServerCalls/asyncUnaryCall 
                                   (reify ServerCalls$UnaryMethod
                                     (invoke [_ request response-observer]
                                       ((:method method) request ^StreamObserver response-observer))))))
                   (.build ssd))))))
     []
     services)))

(defn server-jriServer [^HashMap services port]
   (let [server (ServerBuilder/forPort port)]
     (doseq [service (create-services services)]
       (.addService server ^BindableService service))
     (.. server
         (maxInboundMessageSize Integer/MAX_VALUE)
         build)))

(defn server-rserveServer [^String services port]
  (try
    (StartRserve/launchRserve
      "R"
      "--no-save --slave"
      (str "--no-save --slave "
           "--RS-set shutdown=1 "
           "--RS-set qap.oc=1 "
           "--RS-set source=" services)
      false)
    (catch Exception e
      (println ;;FIXME: log this
               (ex-info "Failed to start Rserve."
                        {:type :rserve-launch
                         :cause "Exception launching Rserve."}
                        e))))
  (with-open [rserve (RConnection.)]
    (let [services (ocap->services rserve)
          server (ServerBuilder/forPort port)
          connection-pool (chan 4)]
      (dotimes [_ 4]
        (let [connection (RConnection.)]
          (->> {:connection connection
                :services (ocap->services connection)}
               (>!! connection-pool ))))
      (doseq [service (create-services services connection-pool)]
        (.addService server ^BindableService service))
      (.. server
          (maxInboundMessageSize Integer/MAX_VALUE)
          build))))

(defn server-shutdownRserve []
  (try
    (with-open [rserve (RConnection.)]
      (.shutdown rserve)
      nil)
    (catch Exception _
      (println "Rserve failed to shutdown gracefully, shutting down forcefully")
      (.exec (Runtime/getRuntime) "pkill Rserve")
      nil)))

(gen-class
  :name software.codera.grpcr.Client
  :prefix "client-"
  :main false
  :methods [^{:static true} [buildChannel [java.lang.String] io.grpc.ManagedChannel]
            ^{:static true} [doCall [io.grpc.ManagedChannel java.lang.String java.lang.String bytes] bytes]])

(defn client-buildChannel [^String host]
  (.. ManagedChannelBuilder
      (forTarget host)
      usePlaintext
      build))

(defn client-doCall [chan service-name method-name request]
  (try
    (let [method-desc (create-method-descriptor service-name method-name) 
          call (.newCall ^ManagedChannel chan method-desc CallOptions/DEFAULT)
          response (ClientCalls/blockingUnaryCall call request)]
      response)
    (catch StatusRuntimeException e
      (throw e))))
