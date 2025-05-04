library(RProtoBuf)
library(gRPCr)
library(zoo)

RProtoBuf::readProtoFiles(system.file("auto_arima.proto", package = "gRPCr"))

request_handler <- function(x) {
  input <- data.frame(period = as.character(as.yearmon(time(x))),
                      observation = x)
  request <- new(auto_arima.ForecastRequest, 
                 parameters = new(auto_arima.ArimaParameters,
                                  max_p = 5,
                                  max_d = 2,
                                  max_q = 5,
                                  max_seas_p = 2,
                                  max_seas_d = 1,
                                  max_seas_q = 2,
                                  stationary = FALSE,
                                  seasonal = TRUE,
                                  ic = "aicc",
                                  n_ahead = 10))
  .mapply(function(...) request$add("observations", auto_arima.ObservedData$new(...)),
          input,
          NULL)
  return(request$serialize(NULL))
}

response_handler <- function(x) {
  function(response) {
    message <- auto_arima.ForecastResponse$read(response)
    observations <- message$observations |>
      lapply(as.list) |>
      lapply(as.data.frame) |>
      do.call(rbind.data.frame, args = _)
    return(list("data.frame" = observations,
                "ts" = ts(observations$observation,
                          freq = frequency(x),
                          start = start(x))))
  }
}

client <- grpc_client("localhost:35000")

response <- grpc_request(client = client,
                         service = "auto_arima.ArimaForecast",
                         method = "Forecast",
                         request = request_handler(AirPassengers),
                         response_handler = response_handler(AirPassengers))

tail(response$data.frame, n = 24)
ts.plot(cbind(AirPassengers, response$ts), col = 1:2, lwd = 2:1, lty = 1:2,
        main = "AirPassengers (1yr-ahead forecast)")
