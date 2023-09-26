package javababushka.benchmarks;

import javababushka.benchmarks.utils.ConnectionSettings;

public interface Client {
  void connectToRedis();

  void connectToRedis(ConnectionSettings connectionSettings);

  default void closeConnection() {}

  String getName();
}
