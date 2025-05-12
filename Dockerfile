FROM rocker/r-ver:4

LABEL maintainer="Byron Botha <byron@codera.co.za>"
LABEL description="Codera Dashboard"

RUN apt-get update
RUN apt-get -y --no-install-recommends \
    install \
    git-core \
    libdeflate-dev \
    libprotobuf-dev \
    libprotoc-dev \
    openjdk-21-jdk \
    protobuf-compiler \
    tzdata

RUN R CMD javareconf

RUN apt-get -y install r-cran-rjava \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN install2.r --error --skipinstalled \
    RProtoBuf \
    Rserve \
    forecast \
    rJava \
    remotes \
    zoo

RUN echo 'options(repos = c(CRAN = "https://cran.mirror.ac.za"))' >> "${R_HOME}/etc/Rprofile.site"

RUN Rscript -e "require(remotes); install_github('coderaanalytics/grpcr', INSTALL_opts = c('--no-help', '--no-html'))"

WORKDIR /app

COPY . .

ENV TZ=Africa/Johannesburg
ENV APP=/app

STOPSIGNAL SIGINT

CMD ["Rscript", "tests/forecast_server.R"]
