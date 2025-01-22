(ns software.codera.forecast.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import [software.codera.forecast ObservedData ArimaParameters ForecastRequest ArimaForecastGrpc]
           [io.grpc ManagedChannelBuilder])
  (:gen-class))

(defn client [address]
  (let [[host port] (str/split address #":")]
    (ArimaForecastGrpc/newBlockingStub
      (.. (ManagedChannelBuilder/forAddress host (Integer. port))
          (maxInboundMessageSize Integer/MAX_VALUE)
          usePlaintext
          build))))

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

(s/def ::config (s/*
                  (s/cat :prop #{"--server"
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
                                           :seasonal)
                                         (Boolean. val)

                                         (:max-p
                                           :max-d
                                           :max-q
                                           :max-seas-p
                                           :max-seas-d
                                           :max-seas-q
                                           :n-ahead)
                                         (Integer. val)

                                         val)]
                               {prop val})))
                      (reduce merge))]
        (with-open [reader (io/reader (:data-dir args))]
          (let [data (csv/read-csv reader)
                request (->> (map zipmap (repeat (map keyword (first data))) (rest data))
                             (compile-message args))
                response (-> (client (:server args))
                             (.forecast request))] 
            (println "ARIMA forecast (last two years):\n")
            (pp/pprint
              (for [datum (take-last 24 (.getObservationsList response ))]
                {:period (.getPeriod datum)
                 :observation (.getObservation datum)
                 :forecast (.getForecast datum)}))))))))
