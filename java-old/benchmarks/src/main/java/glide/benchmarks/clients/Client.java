/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients;

import glide.benchmarks.utils.ConnectionSettings;

/** A Valkey client interface */
public interface Client {
    void connectToValkey(ConnectionSettings connectionSettings);

    default void closeConnection() {}

    String getName();
}
