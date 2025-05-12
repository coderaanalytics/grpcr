# Build

Compile gRPC stubs

```bash
gradle build
```

Compile Clojure code

```bash
clj -T:build uber
```

Start the server by running

```bash
Rscript tests/forecast_server.R
```

# Run from tests/forecast_client

### 12-period-ahead forecast of AirPasseners data using auto.arima

```bash
java -cp target/grpcr-client.jar clojure.main \
  -m software.codera.forecast.core \
  --server localhost:35000 \
  --data-dir resources/AirPassengers.csv \
  --n-ahead 12
```

```
Input:
{:server "localhost:35000",
 :data-dir "resources/AirPassengers.csv",
 :n-ahead 12}

Output:
({:period "Jul 1960", :observation 622.0, :forecast false}
 {:period "Aug 1960", :observation 606.0, :forecast false}
 {:period "Sep 1960", :observation 508.0, :forecast false}
 {:period "Oct 1960", :observation 461.0, :forecast false}
 {:period "Nov 1960", :observation 390.0, :forecast false}
 {:period "Dec 1960", :observation 432.0, :forecast false}
 {:period "Jan 1961", :observation 450.42236, :forecast true}
 {:period "Feb 1961", :observation 425.7172, :forecast true}
 {:period "Mar 1961", :observation 479.00684, :forecast true}
 {:period "Apr 1961", :observation 492.40445, :forecast true}
 {:period "May 1961", :observation 509.05496, :forecast true}
 {:period "Jun 1961", :observation 583.345, :forecast true}
 {:period "Jul 1961", :observation 670.01074, :forecast true}
 {:period "Aug 1961", :observation 667.07764, :forecast true}
 {:period "Sep 1961", :observation 558.18933, :forecast true}
 {:period "Oct 1961", :observation 497.2078, :forecast true}
 {:period "Nov 1961", :observation 429.87198, :forecast true}
 {:period "Dec 1961", :observation 477.24255, :forecast true})
```

### 12 forecasts of AirPasseners data using auto.arima (blocking)

Estimate the model and forecast the data 12 times incrementing the forecast horizon by 1 period each round and return the terminal data point.

```bash
java -cp target/grpcr-client.jar clojure.main \
  -m software.codera.forecast.core \
  --server localhost:35000 \
  --data-dir resources/AirPassengers.csv \
  --repeat 12 \
  --blocking true
```

```
Input:
{:server "localhost:35000",
 :data-dir "resources/AirPassengers.csv",
 :repeat 12,
 :blocking true}

Output:
{:return
 ({:period "Jan 1961", :observation 450.42236, :forecast true}
  {:period "Feb 1961", :observation 425.7172, :forecast true}
  {:period "Mar 1961", :observation 479.00684, :forecast true}
  {:period "Apr 1961", :observation 492.40445, :forecast true}
  {:period "May 1961", :observation 509.05496, :forecast true}
  {:period "Jun 1961", :observation 583.345, :forecast true}
  {:period "Jul 1961", :observation 670.01074, :forecast true}
  {:period "Aug 1961", :observation 667.07764, :forecast true}
  {:period "Sep 1961", :observation 558.18933, :forecast true}
  {:period "Oct 1961", :observation 497.2078, :forecast true}
  {:period "Nov 1961", :observation 429.87198, :forecast true}
  {:period "Dec 1961", :observation 477.24255, :forecast true}),
 :time "15388.614902"}
```

### 12 forecasts of AirPasseners data using auto.arima (concurrent)

Estimate the model and forecast the data 12 times incrementing the forecast horizon by 1 period each round and return the terminal data point.

```bash
java -cp target/grpcr-client.jar clojure.main \
  -m software.codera.forecast.core \
  --server localhost:35000 \
  --data-dir resources/AirPassengers.csv \
  --repeat 12 \
  --blocking false
```

```
Input:
{:server "localhost:35000",
 :data-dir "resources/AirPassengers.csv",
 :repeat 12,
 :blocking false}

Output:
{:return
 ({:period "Jan 1961", :observation 450.42236, :forecast true}
  {:period "Feb 1961", :observation 425.7172, :forecast true}
  {:period "Mar 1961", :observation 479.00684, :forecast true}
  {:period "Apr 1961", :observation 492.40445, :forecast true}
  {:period "May 1961", :observation 509.05496, :forecast true}
  {:period "Jun 1961", :observation 583.345, :forecast true}
  {:period "Jul 1961", :observation 670.01074, :forecast true}
  {:period "Aug 1961", :observation 667.07764, :forecast true}
  {:period "Sep 1961", :observation 558.18933, :forecast true}
  {:period "Oct 1961", :observation 497.2078, :forecast true}
  {:period "Nov 1961", :observation 429.87198, :forecast true}
  {:period "Dec 1961", :observation 477.24255, :forecast true}),
 :time "2047.306895"}
```

Estimating the models and forecasting the series in parallel results in a 

```
15388.61 / 2047.31 ~ 8 times speed up (8 core AMD 3700X)
```
