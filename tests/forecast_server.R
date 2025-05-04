library(gRPCr)

grpc_server(path = system.file("auto_arima.R", package = "gRPCr"),
            backend = "rserve")
