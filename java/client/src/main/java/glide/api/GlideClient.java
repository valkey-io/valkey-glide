/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

// Note: Using fully qualified name to avoid collision with this class
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.commands.InfoOptions;
import glide.api.models.Batch;
import glide.api.models.Transaction;
import glide.api.models.commands.batch.BatchOptions;
import glide.api.models.GlideString;
import glide.api.commands.TransactionsCommands;
import glide.api.commands.GenericCommands;
import glide.api.commands.ServerManagementCommands;
import glide.api.commands.StandaloneServerManagement;
import glide.api.models.commands.scan.ScanOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Glide client for connecting to a single Valkey/Redis instance.
 * This class provides the integration test API while using the refactored core client underneath.
 */
public class GlideClient extends BaseClient implements TransactionsCommands, GenericCommands, ServerManagementCommands {

    private GlideClient(io.valkey.glide.core.client.GlideClient client) {
        super(client, createStandaloneServerManagement(client));
    }

    private static StandaloneServerManagement createStandaloneServerManagement(io.valkey.glide.core.client.GlideClient client) {
        // Create a minimal wrapper that implements ServerManagementCommands
        // This is a temporary implementation that will delegate to the BaseClient methods
        return new StandaloneServerManagement(new ServerManagementCommands() {
            @Override
            public CompletableFuture<String> info() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.INFO))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> info(String[] sections) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.INFO, sections))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String[]> time() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.TIME))
                    .thenApply(result -> {
                        if (result instanceof Object[]) {
                            Object[] objects = (Object[]) result;
                            String[] time = new String[objects.length];
                            for (int i = 0; i < objects.length; i++) {
                                time[i] = objects[i].toString();
                            }
                            return time;
                        }
                        return new String[0];
                    });
            }

            @Override
            public CompletableFuture<Long> lastsave() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.LASTSAVE))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            @Override
            public CompletableFuture<Long> dbsize() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.DBSIZE))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            @Override
            public CompletableFuture<String> flushall() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.FLUSHALL))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushall(glide.api.models.commands.FlushMode mode) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.FLUSHALL, mode.name()))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushdb() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.FLUSHDB))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushdb(glide.api.models.commands.FlushMode mode) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.FLUSHDB, mode.name()))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> configRewrite() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.CONFIG_REWRITE))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> configResetStat() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.CONFIG_RESETSTAT))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<java.util.Map<String, String>> configGet(String[] parameters) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.CONFIG_GET, parameters))
                    .thenApply(result -> {
                        java.util.Map<String, String> config = new java.util.HashMap<>();
                        if (result instanceof Object[]) {
                            Object[] array = (Object[]) result;
                            for (int i = 0; i < array.length - 1; i += 2) {
                                config.put(array[i].toString(), array[i + 1].toString());
                            }
                        }
                        return config;
                    });
            }

            @Override
            public CompletableFuture<String> configSet(java.util.Map<String, String> parameters) {
                String[] args = new String[parameters.size() * 2];
                int i = 0;
                for (java.util.Map.Entry<String, String> entry : parameters.entrySet()) {
                    args[i++] = entry.getKey();
                    args[i++] = entry.getValue();
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    io.valkey.glide.core.commands.CommandType.CONFIG_SET, args))
                    .thenApply(result -> result.toString());
            }
        });
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
     * Select the DB with the specified zero-based numeric index.
     *
     * @param index The database index
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> select(int index) {
        return executeCommand(io.valkey.glide.core.commands.CommandType.SELECT, String.valueOf(index))
            .thenApply(result -> result.toString());
    }

    /**
     * Returns the number of keys in the currently-selected database.
     *
     * @return A CompletableFuture containing the number of keys
     */
    public CompletableFuture<Long> dbsize() {
        return executeCommand(io.valkey.glide.core.commands.CommandType.DBSIZE)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete all the keys of the currently selected DB.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushdb() {
        return executeCommand(io.valkey.glide.core.commands.CommandType.FLUSHDB)
            .thenApply(result -> result.toString());
    }

    /**
     * Delete all the keys of all the existing databases.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushall() {
        return executeCommand(io.valkey.glide.core.commands.CommandType.FLUSHALL)
            .thenApply(result -> result.toString());
    }

    /**
     * Execute a batch of commands.
     *
     * @param batch The batch of commands to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError) {
        return super.exec(batch, raiseOnError);
    }

    /**
     * Execute a transaction (atomic batch).
     *
     * @param transaction The transaction to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(Transaction transaction, boolean raiseOnError) {
        return super.exec(transaction, raiseOnError);
    }

    /**
     * @deprecated Use {@link #exec(Batch, boolean)} instead. This method is being replaced by
     *     a more flexible approach using {@link Batch}.
     */
    @Deprecated
    @Override
    public CompletableFuture<Object[]> exec(Transaction transaction) {
        return exec(transaction, true);
    }

    /**
     * Execute a batch with additional options.
     *
     * @param batch The batch to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @param options Additional execution options (currently not supported)
     * @return A CompletableFuture containing an array of results
     */
    @Override
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError, BatchOptions options) {
        // Currently delegates to basic exec implementation
        // Future enhancement would process options for timeout, retry logic, etc.
        return exec(batch, raiseOnError);
    }


    /**
     * Get server information.
     *
     * @param sections Array of info sections to retrieve
     * @return A CompletableFuture containing the info response
     */
    public CompletableFuture<String> info(InfoOptions.Section[] sections) {
        String[] sectionStrings = new String[sections.length];
        for (int i = 0; i < sections.length; i++) {
            sectionStrings[i] = sections[i].name().toLowerCase();
        }
        return super.info(sectionStrings);
    }

    /**
     * Execute a custom command (GenericCommands interface implementation).
     *
     * @param args The command arguments
     * @return A CompletableFuture containing the command result
     */
    @Override
    public CompletableFuture<Object> customCommand(String[] args) {
        return executeCustomCommand(args);
    }

    /**
     * Execute a custom command with GlideString arguments (GenericCommands interface implementation).
     *
     * @param args The command arguments as GlideString array
     * @return A CompletableFuture containing the command result
     */
    @Override
    public CompletableFuture<Object> customCommand(GlideString[] args) {
        return executeCustomCommand(args);
    }

    /**
     * Scan for keys in the database.
     *
     * @param cursor The cursor to start scanning from
     * @return A CompletableFuture containing an array with [cursor, keys]
     */
    @Override
    public CompletableFuture<Object[]> scan(String cursor) {
        return executeCommand(io.valkey.glide.core.commands.CommandType.SCAN, cursor)
            .thenApply(result -> {
                // Parse scan response: [new_cursor, [key1, key2, ...]]
                if (result instanceof Object[]) {
                    Object[] scanResult = (Object[]) result;
                    if (scanResult.length >= 2) {
                        return new Object[]{scanResult[0], scanResult[1]};
                    }
                }
                // Fallback for unexpected response format
                return new Object[]{cursor, new String[0]};
            });
    }

    /**
     * Scan for keys in the database with GlideString cursor.
     *
     * @param cursor The cursor to start scanning from
     * @return A CompletableFuture containing an array with [cursor, keys]
     */
    @Override
    public CompletableFuture<Object[]> scan(GlideString cursor) {
        return scan(cursor.toString());
    }

    /**
     * Scan for keys in the database with options.
     *
     * @param cursor The cursor to start scanning from
     * @param options The scan options
     * @return A CompletableFuture containing an array with [cursor, keys]
     */
    @Override
    public CompletableFuture<Object[]> scan(String cursor, ScanOptions options) {
        // Build command arguments with options
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(cursor);
        
        if (options != null) {
            // Add options support - for now we delegate to basic scan
            // Future enhancement would parse options and add appropriate arguments
        }
        
        return executeCommand(io.valkey.glide.core.commands.CommandType.SCAN, args.toArray(new String[0]))
            .thenApply(result -> {
                // Parse scan response: [new_cursor, [key1, key2, ...]]
                if (result instanceof Object[]) {
                    Object[] scanResult = (Object[]) result;
                    if (scanResult.length >= 2) {
                        return new Object[]{scanResult[0], scanResult[1]};
                    }
                }
                // Fallback for unexpected response format
                return new Object[]{cursor, new String[0]};
            });
    }

    /**
     * Scan for keys in the database with GlideString cursor and options.
     *
     * @param cursor The cursor to start scanning from
     * @param options The scan options
     * @return A CompletableFuture containing an array with [cursor, keys]
     */
    @Override
    public CompletableFuture<Object[]> scan(GlideString cursor, ScanOptions options) {
        return scan(cursor.toString(), options);
    }

    /**
     * Returns a random key from the database.
     *
     * @return A CompletableFuture containing a random key, or null if no keys exist
     */
    @Override
    public CompletableFuture<String> randomKey() {
        return super.randomkey();
    }

    /**
     * Returns a random key from the database as a GlideString.
     *
     * @return A CompletableFuture containing a random key, or null if no keys exist
     */
    @Override
    public CompletableFuture<GlideString> randomKeyBinary() {
        return super.randomkeyBinary();
    }

    /**
     * Copy a key to a new destination.
     *
     * @param source The source key
     * @param destination The destination key
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(String source, String destination) {
        return executeCommand(io.valkey.glide.core.commands.CommandType.COPY, source, destination)
            .thenApply(result -> Boolean.parseBoolean(result.toString()));
    }

    /**
     * Copy a key to a new destination with GlideString arguments.
     *
     * @param source The source key
     * @param destination The destination key
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(GlideString source, GlideString destination) {
        return copy(source.toString(), destination.toString());
    }

    /**
     * Copy a key to a new destination with replace option.
     *
     * @param source The source key
     * @param destination The destination key
     * @param replace Whether to replace existing key
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(String source, String destination, boolean replace) {
        if (replace) {
            return executeCommand(io.valkey.glide.core.commands.CommandType.COPY, source, destination, "REPLACE")
                .thenApply(result -> Boolean.parseBoolean(result.toString()));
        } else {
            return copy(source, destination);
        }
    }

    /**
     * Copy a key to a new destination with GlideString arguments and replace option.
     *
     * @param source The source key
     * @param destination The destination key
     * @param replace Whether to replace existing key
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(GlideString source, GlideString destination, boolean replace) {
        return copy(source.toString(), destination.toString(), replace);
    }

    /**
     * Copy a key to a new destination in a different database.
     *
     * @param source The source key
     * @param destination The destination key
     * @param destinationDB The destination database
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(String source, String destination, long destinationDB) {
        return executeCommand(io.valkey.glide.core.commands.CommandType.COPY, source, destination, "DB", String.valueOf(destinationDB))
            .thenApply(result -> Boolean.parseBoolean(result.toString()));
    }

    /**
     * Copy a key to a new destination in a different database with GlideString arguments.
     *
     * @param source The source key
     * @param destination The destination key
     * @param destinationDB The destination database
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(GlideString source, GlideString destination, long destinationDB) {
        return copy(source.toString(), destination.toString(), destinationDB);
    }

    /**
     * Copy a key to a new destination in a different database with replace option.
     *
     * @param source The source key
     * @param destination The destination key
     * @param destinationDB The destination database
     * @param replace Whether to replace existing key
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(String source, String destination, long destinationDB, boolean replace) {
        if (replace) {
            return executeCommand(io.valkey.glide.core.commands.CommandType.COPY, source, destination, "DB", String.valueOf(destinationDB), "REPLACE")
                .thenApply(result -> Boolean.parseBoolean(result.toString()));
        } else {
            return copy(source, destination, destinationDB);
        }
    }

    /**
     * Copy a key to a new destination in a different database with GlideString arguments and replace option.
     *
     * @param source The source key
     * @param destination The destination key
     * @param destinationDB The destination database
     * @param replace Whether to replace existing key
     * @return A CompletableFuture containing true if copy was successful
     */
    @Override
    public CompletableFuture<Boolean> copy(GlideString source, GlideString destination, long destinationDB, boolean replace) {
        return copy(source.toString(), destination.toString(), destinationDB, replace);
    }

    /**
     * Move a key to a different database.
     *
     * @param key The key to move
     * @param dbIndex The database index to move the key to
     * @return A CompletableFuture containing true if move was successful
     */
    @Override
    public CompletableFuture<Boolean> move(String key, long dbIndex) {
        return executeCommand(io.valkey.glide.core.commands.CommandType.MOVE, key, String.valueOf(dbIndex))
            .thenApply(result -> Boolean.parseBoolean(result.toString()));
    }

    /**
     * Move a key to a different database with GlideString argument.
     *
     * @param key The key to move
     * @param dbIndex The database index to move the key to
     * @return A CompletableFuture containing true if move was successful
     */
    @Override
    public CompletableFuture<Boolean> move(GlideString key, long dbIndex) {
        return move(key.toString(), dbIndex);
    }
}
