/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import io.valkey.glide.core.client.GlideClient;
import io.valkey.glide.jni.commands.CommandType;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Simple standalone client for connecting to a single Valkey instance.
 * This client extends SimpleBaseClient and provides additional standalone-specific commands.
 */
public class SimpleStandaloneClient extends SimpleBaseClient {

    private SimpleStandaloneClient(GlideClient client) {
        super(client);
    }

    /**
     * Create a new connection to a Valkey standalone instance.
     *
     * @param host The host to connect to
     * @param port The port to connect to
     * @return A new SimpleStandaloneClient instance
     */
    public static SimpleStandaloneClient create(String host, int port) {
        try {
            // Create client connection using the proper Config constructor
            GlideClient.Config config = new GlideClient.Config(Arrays.asList(host + ":" + port));
            GlideClient client = new GlideClient(config);
            return new SimpleStandaloneClient(client);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create standalone client", e);
        }
    }

    /**
     * Create a new connection to a Valkey standalone instance with default
     * localhost:6379.
     *
     * @return A new SimpleStandaloneClient instance
     */
    public static SimpleStandaloneClient create() {
        return create("localhost", 6379);
    }

    /**
     * Select the DB with the specified zero-based numeric index.
     *
     * @param index The database index
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> select(int index) {
        return executeCommand(CommandType.SELECT, String.valueOf(index))
            .thenApply(result -> result.toString());
    }

    /**
     * Returns the number of keys in the currently-selected database.
     *
     * @return A CompletableFuture containing the number of keys
     */
    public CompletableFuture<Long> dbsize() {
        return executeCommand(CommandType.DBSIZE)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete all the keys of the currently selected DB.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushdb() {
        return executeCommand(CommandType.FLUSHDB)
            .thenApply(result -> result.toString());
    }

    /**
     * Delete all the keys of all the existing databases.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushall() {
        return executeCommand(CommandType.FLUSHALL)
            .thenApply(result -> result.toString());
    }
}
