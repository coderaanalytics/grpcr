#install.packages("~/Documents/software/grpcr", repos = NULL, type = "source")

library(RProtoBuf)
library(gRPCr)

RProtoBuf::readProtoFiles("resources/auto_arima.proto")

extract_fn <- function(request) {
  message <- auto_arima.ForecastRequest$read(request)
  observations <- message$observations |>
    lapply(as.list) |>
    lapply(as.data.frame) |>
    do.call(rbind.data.frame, args = _)
  params <- message$parameters
  input <- list(auto.arima = list(), predict = list())
  input$predict$n.ahead <- params$n_ahead
  input$auto.arima$max.p <- params$max_p
  input$auto.arima$max.d <- params$max_d
  input$auto.arima$max.q <- params$max_q
  input$auto.arima$max.P <- params$max_seas_p
  input$auto.arima$max.D <- params$max_seas_d
  input$auto.arima$max.Q <- params$max_seas_q
  input$auto.arima$stationary <- params$stationary
  input$auto.arima$seasonal <- params$seasonal
  input$auto.arima$ic <- params$ic
  start_date <- paste("01", head(observations, n = 1)$period) |>
    as.Date(format="%d %b %Y") |>
    format("%Y %m") |>
    strsplit(" ") |>
    unlist() |>
    as.numeric()
  input$auto.arima$y <- ts(log(observations$observation), freq = 12, start = start_date)
  return(input)
}

transform_fn <- function(input){
  stopifnot(require(forecast))
  stopifnot(require(zoo))
  model <- do.call(auto.arima, input$auto.arima)
  cat("Estimated model:\n\n")
  print(model)
  fcast <- predict(model, n.ahead = input$predict$n.ahead)$pred
  result <- ts(c(input$auto.arima$y, fcast),
               freq = frequency(input$auto.arima$y),
               start = start(input$auto.arima$y))
  output <- data.frame(period = as.character(as.yearmon(time(result))),
                       observation = exp(result),
                       forecast = time(result) > tsp(input$auto.arima$y)[2])
  return(output)
}

load_fn <- function(output) {
  response <- new(auto_arima.ForecastResponse)
  lapply(.mapply(data.frame, output, NULL), function(record) {
    response$add("observations", do.call(new, c(auto_arima.ForecastData, record)))
  })
  return(response)
}

services <- list(auto_arima.ArimaForecast =
                 list(Forecast = function(request) {
                          tryCatch({
                            input <- extract_fn(request)
                          }, error = function(e) {
                            stop("R failed to marshall gRPC request: ",
                                 conditionMessage(e))
                          })
                          tryCatch({
                            output <- transform_fn(input)
                          }, error = function(e) {
                            stop("R failed to transform gRPC data: ",
                                  conditionMessage(e))
                          })
                          tryCatch({
                            response <- load_fn(output)
                            stopifnot(class(response) == "Message")
                          }, error = function(e) {
                            stop("R failed to unmarshall gRPC response: ",
                                 conditionMessage(e))
                          })
                          return(serialize(response, NULL))}))

server <- grpc_server(services)
