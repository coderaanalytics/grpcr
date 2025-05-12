(ns software.codera.forecast.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import [com.google.common.util.concurrent FutureCallback Futures MoreExecutors]
           [io.grpc ManagedChannelBuilder]
           [software.codera.forecast ObservedData ArimaParameters ForecastRequest ArimaForecastGrpc ArimaForecastGrpc$ArimaForecastFutureStub ArimaForecastGrpc$ArimaForecastFutureStub ArimaForecastGrpc$ArimaForecastBlockingStub])
  (:gen-class))

(defn client [address blocking?] 
  (let [[host port] (str/split address #":")
        channel (.. (ManagedChannelBuilder/forAddress host (Integer. port))
                    (maxInboundMessageSize Integer/MAX_VALUE)
                    usePlaintext
                    build)]
    (if blocking?
      (ArimaForecastGrpc/newBlockingStub channel)
      (ArimaForecastGrpc/newFutureStub channel))))

(defn compile-message [args observations]
  (let [data (ForecastRequest/newBuilder)] 
    (.setParameters data
                    (.. (ArimaParameters/newBuilder)
                        (setMaxP (or (:max-p args) 5))
                        (setMaxD (or (:max-d args) 2))
                        (setMaxQ (or (:max-q args) 5))
                        (setMaxSeasP (or (:max-seas-p args) 2))
                        (setMaxSeasD (or (:max-seas-d args) 1))
                        (setMaxSeasQ (or (:max-seas-q args) 2))
                        (setStationary (or (:stationary args) false))
                        (setSeasonal (or (:seasonal args) true))
                        (setIc (or (:ic args) "aicc"))
                        (setNAhead (or (:n-ahead args) 1))))
    (doseq [obs observations]
      (.addObservations data
                        (.. (ObservedData/newBuilder)
                            (setPeriod (or (:period obs) ""))
                            (setObservation (if (seq (:observation obs))
                                              (Double. (:observation obs))
                                              ##NaN))
                            build)))
    (.build data)))

(defmulti do-forecast (fn [stub _ _] (type stub)))

(defmethod do-forecast ArimaForecastGrpc$ArimaForecastBlockingStub
  [stub data p]
  (try
    (let [response (.forecast stub data)]
      (deliver p response))
    (catch Exception throwable
      (deliver p throwable))))

(defmethod do-forecast ArimaForecastGrpc$ArimaForecastFutureStub
  [stub data p]
  (let [response (.forecast stub data)]
    (Futures/addCallback response
                         (reify FutureCallback
                           (onSuccess [_ result]
                             (deliver p result))
                           (onFailure [_ throwable]
                             (deliver p throwable)))
                         (MoreExecutors/directExecutor))))

(defmacro time-execution
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*out* s#]
       (hash-map :return (time ~@body)
                 :time   (.replaceAll (str s#) "[^0-9\\.]" "")))))

(s/def ::config (s/*
                  (s/cat :prop #{"--server"
                                 "--blocking"
                                 "--repeat"
                                 "--data-dir"
                                 "--max-p"
                                 "--max-d"
                                 "--max-q"
                                 "--max-seas-p"
                                 "--max-seas-d"
                                 "--max-seas-q"
                                 "--stationary"
                                 "--seasonal"
                                 "--ic"
                                 "--n-ahead"}
                         :val string?)))

(defn -main [& args]
  (let [parsed (s/conform ::config args)]
    (if (s/invalid? parsed)
      (throw (ex-info "Invalid input" (s/explain-data ::config args)))
      (let [args (->> parsed
                      (map (fn [{prop :prop val :val}]
                             (let [prop (keyword (subs prop 2))
                                   val (case prop
                                         (:stationary
                                           :seasonal
                                           :blocking)
                                         (Boolean. val)

                                         (:max-p
                                           :max-d
                                           :max-q
                                           :max-seas-p
                                           :max-seas-d
                                           :max-seas-q
                                           :n-ahead
                                           :repeat)
                                         (Integer. val)

                                         val)]
                               {prop val})))
                      (reduce merge))]
        (println "Input:")
        (pp/pprint args)
        (println)
        (with-open [reader (io/reader (:data-dir args))]
          (let [data (as-> (csv/read-csv reader) $
                       (map zipmap (repeat (map keyword (first $))) (rest $)))] 
            (if (:repeat args)
              (let [stub (client (:server args) (= (:blocking args) true))
                    results (repeatedly (:repeat args) promise)]
                (doseq [n (range (:repeat args))
                        :let [args (assoc args :n-ahead (inc n))
                              request (compile-message args data)
                              result (nth results n)]]
                  (do-forecast stub request result))
                (println "Output:")
                (pp/pprint
                  (time-execution
                    (doall
                      (for [result results
                            :let [time-limit 10000
                                  response (deref result time-limit :timed-out)]]
                        (cond
                          (instance? Throwable response)
                          (throw response)

                          (= response :timed-out)
                          (throw (ex-info "Request timed out."
                                          {:type :timed-out
                                           :time-limit time-limit}))

                          :else
                          (let [datum (last (.getObservationsList response))]
                            {:period (.getPeriod datum)
                             :observation (.getObservation datum)
                             :forecast (.getForecast datum)})))))))
              (let [request (compile-message args data)
                    response (-> (client (:server args) true)
                                 (.forecast request))]
                (println "Output:")
                (pp/pprint
                  (for [datum (take-last 18 (.getObservationsList response))]
                    {:period (.getPeriod datum)
                     :observation (.getObservation datum)
                     :forecast (.getForecast datum)}))))))))))
