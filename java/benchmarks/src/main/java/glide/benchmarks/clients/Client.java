package glide.benchmarks.clients;

import glide.benchmarks.utils.ConnectionSettings;

/** A Redis client interface */
public interface Client {
    void connectToRedis(ConnectionSettings connectionSettings);

    default void closeConnection() {}

    String getName();
}
