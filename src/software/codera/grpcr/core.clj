(ns software.codera.grpcr.core
  (:import [io.grpc BindableService CallOptions ManagedChannel ManagedChannelBuilder MethodDescriptor MethodDescriptor$Marshaller MethodDescriptor$MethodType ServerBuilder ServerServiceDefinition ServerServiceDefinition$Builder Status StatusRuntimeException]
           [io.grpc.stub ClientCalls ServerCalls ServerCalls$UnaryMethod StreamObserver]
           [org.rosuda.REngine REXP REXPReference REngineException REXPMismatchException REXPLanguage RList REXPRaw]
           [java.util HashMap]))

(gen-class
  :name software.codera.grpcr.Server
  :prefix "server-"
  :main false
  :methods [^{:static true} [addServices [java.util.HashMap int] io.grpc.Server]])

(set! *warn-on-reflection* true)

(defn byte-marshaller []
  (reify MethodDescriptor$Marshaller
    (parse [_ stream]
      (.readAllBytes stream))
    (stream [_ value]
      (java.io.ByteArrayInputStream. value))))

(defn create-methods [methods]
  (reduce-kv 
    (fn [methods method-name method-ref]
      (conj methods
            {:name method-name
             :method
             (fn [request response-observer]
               (try
                 (let [data (REXPRaw. request)
                       expression (->> (into-array REXP [method-ref data])
                                       (RList.)
                                       (REXPLanguage.))]
                   (.onNext ^StreamObserver response-observer
                            ^bytes (-> (.getEngine ^REXPReference method-ref)
                                       (.eval expression nil true)
                                       (.asBytes)))
                   (.onCompleted ^StreamObserver response-observer)
                   nil)
                 (catch REngineException e
                   (println ;;FIXME: log this
                            (ex-info "Rengine failed"
                                     {:type :rengine
                                      :cause "Error invoking 'call'."}
                                     e))
                   (.onError ^StreamObserver response-observer (.asRuntimeException Status/INTERNAL)))
                 (catch REXPMismatchException e
                   (println ;;FIXME: log this
                            (ex-info "R expression mismatch"
                                     {:type :rexp-mismatch
                                      :cause "Error invoking 'call'."}
                                     e))
                   (.onError ^StreamObserver response-observer (.asRuntimeException Status/INTERNAL)))))}))
    []
    methods))

(defn create-method-descriptor [service-name method-name]
  (.. (MethodDescriptor/newBuilder (byte-marshaller) (byte-marshaller))
      (setFullMethodName (MethodDescriptor/generateFullMethodName service-name method-name))
      (setType MethodDescriptor$MethodType/UNARY)
      (setSampledToLocalTracing true)
      build))

(defn create-services [services]
  (reduce-kv
    (fn [services service-name methods]
      (conj services
            (reify BindableService
              (bindService [_]
                (let [ssd (ServerServiceDefinition/builder ^String service-name)]
                  (doseq [method (create-methods methods)]
                    (.addMethod ^ServerServiceDefinition$Builder ssd
                                (create-method-descriptor service-name (:name method))
                                (ServerCalls/asyncUnaryCall 
                                  (reify ServerCalls$UnaryMethod
                                    (invoke [_ request response-observer]
                                      ((:method method) request ^StreamObserver response-observer))))))
                  (.build ssd))))))
  []
  services))

(defn server-addServices [^HashMap services port]
  (let [server (ServerBuilder/forPort port)]
    (doseq [service (create-services services)]
      (.addService server ^BindableService service))
    (.. server
        (maxInboundMessageSize Integer/MAX_VALUE)
        build)))

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
