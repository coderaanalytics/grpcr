\name{grpc_server}
\alias{grpc_server}
\alias{start_server}
\alias{shutdown_server}
\title{
read_dataset
}
\description{
Instantiates Java and required objects, including an REngine instance, needed to start a gRPC server. Takes a list of services which in turn takes a list of methods (defined by the service). Each method references a function that is used to process incoming requests, the function must read the input (raw vector) into the associated protobuf request message and return a serialised protobuf response message (also a raw vector)
}
\usage{
grpc_server <- function(services, port = 35000, start_server = TRUE, block_process = TRUE)

\method{start_server}(server)
\method{shutdown_server}(server)
}
\arguments{
  \item{services}{}
  \item{port}{}
  \item{start_server}{}
  \item{block_process}{}
}
\details{
  TBC
}
\value{
  TBC
}
\seealso{
}
\examples{
\dontrun{
# Code goes here
}
}
\keyword{ grpc }
\keyword{ server }
