package javababushka.benchmarks.clients;

import javababushka.benchmarks.utils.ConnectionSettings;

/** A Redis client interface */
public interface Client {
  void connectToRedis();

  void connectToRedis(ConnectionSettings connectionSettings);

  default void closeConnection() {}

  String getName();
}
