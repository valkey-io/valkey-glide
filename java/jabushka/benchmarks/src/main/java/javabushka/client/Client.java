package javabushka.client;

import javabushka.client.utils.ConnectionSettings;

public interface Client {
  void connectToRedis();

  void connectToRedis(ConnectionSettings connectionSettings);

  default void closeConnection() {}

  String getName();
}
