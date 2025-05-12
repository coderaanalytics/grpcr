rserve_init <- function(services) {
  ocaps <- list()
  for (service in names(services)) {
    for (method in names(services[[service]])) {
      ocaps[[paste(service, method, sep = ".")]] <-
        ocap(services[[service]][[method]])
    }
  }
  return(function() ocap(function() ocaps))
}

grpc_server <- function(path, port = 35000, backend = "jri", ...) {
  if (backend == "jri") {
    tryCatch({
      .jinit()
      .jengine(TRUE)
      source(path)
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
          J(method_map,
            "put",
            method_name,
            services[[service_name]][[method_name]])
        }
        J(service_map,
          "put",
          service_name,
          method_map)
      }
      server <- .jcall(grpcr,
                       "Lio/grpc/Server;",
                       "jriServer",
                       service_map,
                       as.integer(port))
      .jcall(server, "Lio/grpc/Server;", "start")
      message("gRPC server listening on port ", port, "\n")
      Sys.sleep(Inf)
    }, error = function(e) {
      stop("Failed to start gRPC server: ", conditionMessage(e), "\n")
    }, interrupt = function(i) {
      message("\nShutting down gRPC server\n")
      .jcall(server, "Lio/grpc/Server;", "shutdown")
    })
  } else if (backend == "rserve")  {
    tryCatch({
      dots <- list(...)
      max_connections <- if (!is.null(dots$max_connections)) {
        dots$max_connections
      } else {
        10
      }
      Rserve(debug = FALSE,
             port = 6311L,
             args = paste0("--no-save ",
                           "--slave ",
                           "--RS-set shutdown=1 ",
                           "--RS-set qap.oc=1 ",
                           "--RS-set source=", path),
             wait = TRUE)
      .jinit()
      .jaddClassPath(system.file("grpcr.jar", package = "gRPCr"))
      grpcr <- .jnew("software/codera/grpcr/Server")
      server <- .jcall(grpcr,
                       "Lio/grpc/Server;",
                       "rserveServer",
                       as.integer(port),
                       as.integer(max_connections))
      .jcall(server, "Lio/grpc/Server;", "start")
      message("gRPC server listening on port ", port, "\n")
      Sys.sleep(Inf)
    }, error = function(e) {
      stop("Failed to start gRPC server: ", conditionMessage(e), "\n")
    }, interrupt = function(i) {
      if (backend == "rserve") {
        message("\nShutting down Rserve\n")
        system("pkill Rserve")
      }
      message("\nShutting down gRPC server\n")
      .jcall(server, "Lio/grpc/Server;", "shutdown")
      return(invisible(NULL))
    })
  } else {
    stop("Backend '", backend, "', not supported.")
  }
}

grpc_client <- function(host = "localhost:35000") {
  tryCatch({
    .jinit()
    .jaddClassPath(system.file("grpcr.jar", package = "gRPCr"))
    grpcr <- .jnew("software/codera/grpcr/Client")
    client <- .jcall(grpcr, "Lio/grpc/ManagedChannel;", "buildChannel", host)
    return(client)
  }, error = function(e) {
    stop("Failed to create gRPC client: ", conditionMessage(e), "\n")
  })
}

grpc_request <- function(client, service, method, request, response_handler = NULL) {
  tryCatch({
    grpcr <- .jnew("software/codera/grpcr/Client")
    response <- .jcall(grpcr,
                       "[B",
                       "doCall",
                       client,
                       service,
                       method,
                       request)
    if (is.null(response_handler)) {
      return(response)
    } else {
      return(response_handler(response))
    }
  }, error = function(e) {
    stop("Request to gRPC server failed to produce a response: ",
         conditionMessage(e), "\n")
  })
}
