# Run from test folder

```bash
java -cp forecast_client/target/grpcr-client.jar clojure.main -m software.codera.forecast.core --server localhost:35000 --data-dir resources/AirPassengers.csv --n-ahead 12
```
