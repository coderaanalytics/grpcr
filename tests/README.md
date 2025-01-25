# Run from tests folder

```bash
java -cp forecast_client/target/grpcr-client.jar clojure.main -m software.codera.forecast.core --server localhost:35000 --data-dir forecast_client/resources/AirPassengers.csv --n-ahead 12
```
