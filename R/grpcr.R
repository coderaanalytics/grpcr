grpc_server <- function(services, port = 35000, start_server = TRUE, block_process = TRUE) {
  tryCatch({
    .jinit()
    .jengine(TRUE)
    for (service_name in names(services)) {
      for (method_name in names(services[[service_name]])) {
        services[[service_name]][[method_name]] <-
          toJava(services[[service_name]][[method_name]])
      }
    }
    .jaddClassPath(system.file("grpcr.jar", package = "gRPCr"))
    grpcr <- .jnew("software/codera/grpcr/Server")
    service_map <- .jnew("java/util/HashMap")
    for (service_name in names(services)) {
      method_map <- .jnew("java/util/HashMap")
      for (method_name in names(services[[service_name]])) {
        J(method_map, "put",
          method_name, services[[service_name]][[method_name]])
      }
      J(service_map, "put",
        service_name, method_map)
    }
    server <- .jcall(grpcr, "Lio/grpc/Server;", "addServices",
                     service_map, as.integer(port))
    if (start_server) {
      .jcall(server, "Lio/grpc/Server;", "start")
      message("gRPC server listening on port ", port, "\n")
    }
    if (block_process) Sys.sleep(Inf)
    return(server)
  }, error = function(e) {
    stop("Failed to start gRPC server: ", conditionMessage(e), "\n")
  }, interrupt = function(i) {
    message("\nShutting down gRPC server\n")
    .jcall(server, "Lio/grpc/Server;", "shutdown")
  })
}

start_server <- function(server) {
  .jcall(server, "Lio/grpc/Server;", "start")
}

shutdown_server <- function(server) {
  .jcall(server, "Lio/grpc/Server;", "shutdown")
}
