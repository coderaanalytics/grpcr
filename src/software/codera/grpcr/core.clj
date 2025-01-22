(ns software.codera.grpcr.core
  (:import [io.grpc HandlerRegistry Metadata MethodDescriptor MethodDescriptor$Marshaller MethodDescriptor$MethodType Server ServerBuilder ServerCall ServerCall$Listener ServerCallHandler ServerMethodDefinition Status]
           [org.rosuda.REngine REXP REXPReference REngineException REXPMismatchException REXPLanguage RList REXPRaw])
  (:gen-class
    :name software.codera.grpcr.Server
    :prefix "-"
    :main false
    :methods [^{:static true} [defMethod [java.lang.String org.rosuda.REngine.REXPReference] void]
              ^{:static true} [addServices [int] io.grpc.Server]]))

(set! *warn-on-reflection* true)

(defmulti call-listener #(.. ^ServerCall % getMethodDescriptor getFullMethodName))

(defn call-handler []
  (reify ServerCallHandler
    (startCall [_ server-call headers]
      (let [call-listener (call-listener server-call)]
        (.request server-call 1)
        call-listener))))

(defn byte-marshaller []
  (reify MethodDescriptor$Marshaller
    (parse [_ stream]
      (.readAllBytes stream))
    (stream [_ value]
      (java.io.ByteArrayInputStream. value))))

(defn registry [call-handler]
  (proxy [HandlerRegistry] []
    (lookupMethod [method-name authority]
      (let [method (.. (MethodDescriptor/newBuilder (byte-marshaller) (byte-marshaller))
                       (setFullMethodName method-name)
                       (setType MethodDescriptor$MethodType/UNARY)
                       (setSampledToLocalTracing true)
                       build)]
        (ServerMethodDefinition/create method call-handler)))))

(defn -defMethod [^String method-name ^REXPReference method-ref]
  (defmethod call-listener method-name [server-call]
    (proxy [ServerCall$Listener] []

      (onCancel [])

      (onComplete [])

      (onHalfClose [])

      (onMessage [request]
        (try
          (let [data (REXPRaw. request)
                expression (->> (into-array REXP [method-ref data])
                                (RList.)
                                (REXPLanguage.))
                response (-> (.getEngine method-ref)
                             (.eval expression nil true)
                             (.asBytes))]
            (.sendHeaders ^ServerCall server-call (Metadata.))
            (.sendMessage ^ServerCall server-call response)
            (.close server-call Status/OK (Metadata.)))
          (catch REngineException e
            (throw
              (ex-info "Rengine failed"
                       {:cause "Error invoking 'call'."}
                       e)))
          (catch REXPMismatchException e
            (throw
              (ex-info "Mismatch exception"
                       {:cause "Error invoking 'call'."}
                       e)))))

      (onReady []))))

(defn -addServices [port]
  (let [server (.. ServerBuilder
                   (forPort port)
                   (fallbackHandlerRegistry (registry (call-handler)))
                   (maxInboundMessageSize Integer/MAX_VALUE)
                   build)]
    server))
