/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

// Note: Using fully qualified name to avoid collision with this class
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.commands.InfoOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Glide client for connecting to a single Valkey/Redis instance.
 * This class provides the integration test API while using the refactored core client underneath.
 */
public class GlideClient extends BaseClient {

    private GlideClient(io.valkey.glide.core.client.GlideClient client) {
        super(client);
    }

    /**
     * Create a new GlideClient with the given configuration.
     *
     * @param config The client configuration
     * @return A CompletableFuture containing the new client
     */
    public static CompletableFuture<GlideClient> createClient(GlideClientConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Convert configuration to core client config
                io.valkey.glide.core.client.GlideClient.Config coreConfig =
                    convertToConfig(config);

                io.valkey.glide.core.client.GlideClient coreClient =
                    new io.valkey.glide.core.client.GlideClient(coreConfig);

                return new GlideClient(coreClient);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create GlideClient", e);
            }
        });
    }

    /**
     * Convert from integration test configuration to core client configuration.
     */
    private static io.valkey.glide.core.client.GlideClient.Config convertToConfig(
            GlideClientConfiguration config) {
        // Extract addresses from config
        java.util.List<String> addresses = new java.util.ArrayList<>();

        if (config.getAddresses() != null) {
            for (var address : config.getAddresses()) {
                addresses.add(address.getHost() + ":" + address.getPort());
            }
        }

        // Default to localhost:6379 if no addresses specified
        if (addresses.isEmpty()) {
            addresses.add("localhost:6379");
        }

        return new io.valkey.glide.core.client.GlideClient.Config(addresses);
    }

    /**
     * Get information about the server.
     *
     * @param sections The sections to retrieve
     * @return A CompletableFuture containing the info response
     */
    public CompletableFuture<String> info(InfoOptions.Section[] sections) {
        return executeCommand(io.valkey.glide.api.commands.CommandType.INFO)
            .thenApply(result -> result.toString());
    }

    /**
     * Select the DB with the specified zero-based numeric index.
     *
     * @param index The database index
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> select(int index) {
        return executeCommand(io.valkey.glide.api.commands.CommandType.SELECT, String.valueOf(index))
            .thenApply(result -> result.toString());
    }

    /**
     * Returns the number of keys in the currently-selected database.
     *
     * @return A CompletableFuture containing the number of keys
     */
    public CompletableFuture<Long> dbsize() {
        return executeCommand(io.valkey.glide.api.commands.CommandType.DBSIZE)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete all the keys of the currently selected DB.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushdb() {
        return executeCommand(io.valkey.glide.api.commands.CommandType.FLUSHDB)
            .thenApply(result -> result.toString());
    }

    /**
     * Delete all the keys of all the existing databases.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushall() {
        return executeCommand(io.valkey.glide.api.commands.CommandType.FLUSHALL)
            .thenApply(result -> result.toString());
    }
}
