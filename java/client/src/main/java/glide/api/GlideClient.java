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
import glide.api.commands.PubSubBaseCommands;
import glide.api.commands.StandaloneServerManagement;
import glide.api.models.commands.scan.ScanOptions;
import java.util.concurrent.CompletableFuture;

import static glide.api.models.commands.RequestType.*;

/**
 * Glide client for connecting to a single Valkey/Redis instance.
 * This class provides the integration test API while using the refactored core client underneath.
 */
public class GlideClient extends BaseClient implements TransactionsCommands, GenericCommands, ServerManagementCommands, PubSubBaseCommands {

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
                    Info))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> info(glide.api.models.commands.InfoOptions.Section[] sections) {
                String[] sectionNames = new String[sections.length];
                for (int i = 0; i < sections.length; i++) {
                    sectionNames[i] = sections[i].name().toLowerCase();
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Info, sectionNames))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String[]> time() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Time))
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
                    LastSave))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            @Override
            public CompletableFuture<Long> dbsize() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    DBSize))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            @Override
            public CompletableFuture<String> flushall() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushAll))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushall(glide.api.models.commands.FlushMode mode) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushAll, mode.name()))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushdb() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushDB))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushdb(glide.api.models.commands.FlushMode mode) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushDB, mode.name()))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> configRewrite() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigRewrite))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> configResetStat() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigResetStat))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<java.util.Map<String, String>> configGet(String[] parameters) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigGet, parameters))
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
                    ConfigSet, args))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut(int[] parameters) {
                String[] args = new String[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    args[i] = String.valueOf(parameters[i]);
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, args))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut(int version) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, "VERSION", String.valueOf(version)))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut(int version, int[] parameters) {
                String[] args = new String[parameters.length + 2];
                args[0] = "VERSION";
                args[1] = String.valueOf(version);
                for (int i = 0; i < parameters.length; i++) {
                    args[i + 2] = String.valueOf(parameters[i]);
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, args))
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
        return executeCommand(Select, String.valueOf(index))
            .thenApply(result -> result.toString());
    }

    /**
     * Returns the number of keys in the currently-selected database.
     *
     * @return A CompletableFuture containing the number of keys
     */
    public CompletableFuture<Long> dbsize() {
        return executeCommand(DBSize)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete all the keys of the currently selected DB.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushdb() {
        return executeCommand(FlushDB)
            .thenApply(result -> result.toString());
    }

    /**
     * Delete all the keys of the currently selected DB with flush mode.
     *
     * @param mode The flush mode to use
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushdb(glide.api.models.commands.FlushMode mode) {
        return executeCommand(FlushDB, mode.name())
            .thenApply(result -> result.toString());
    }

    /**
     * Delete all the keys of all the existing databases.
     *
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushall() {
        return executeCommand(FlushAll)
            .thenApply(result -> result.toString());
    }

    /**
     * Delete all the keys of all the existing databases with flush mode.
     *
     * @param mode The flush mode to use
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> flushall(glide.api.models.commands.FlushMode mode) {
        return executeCommand(FlushAll, mode.name())
            .thenApply(result -> result.toString());
    }

    /**
     * Returns UNIX TIME of the last DB save timestamp.
     *
     * @return A CompletableFuture containing the UNIX TIME
     */
    public CompletableFuture<Long> lastsave() {
        return executeCommand(LastSave)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * GET the current server time.
     *
     * @return A CompletableFuture containing an array with seconds and microseconds since Unix epoch
     */
    public CompletableFuture<String[]> time() {
        return executeCommand(Time)
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

    /**
     * GET configuration values for multiple parameters.
     *
     * @param parameters The configuration parameters to get
     * @return A CompletableFuture containing the configuration values
     */
    public CompletableFuture<java.util.Map<String, String>> configGet(String[] parameters) {
        return executeCommand(ConfigGet, parameters)
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

    /**
     * Set configuration parameters to the specified values.
     *
     * @param parameters A map consisting of configuration parameters and their respective values to set
     * @return A CompletableFuture containing "OK" if all configurations have been successfully set
     */
    public CompletableFuture<String> configSet(java.util.Map<String, String> parameters) {
        String[] args = new String[parameters.size() * 2];
        int i = 0;
        for (java.util.Map.Entry<String, String> entry : parameters.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return executeCommand(ConfigSet, args)
            .thenApply(result -> result.toString());
    }

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @return A CompletableFuture containing "OK" when the configuration was rewritten properly
     */
    public CompletableFuture<String> configRewrite() {
        return executeCommand(ConfigRewrite)
            .thenApply(result -> result.toString());
    }

    /**
     * Resets the statistics reported by the server.
     *
     * @return A CompletableFuture containing "OK" to confirm that the statistics were successfully reset
     */
    public CompletableFuture<String> configResetStat() {
        return executeCommand(ConfigResetStat)
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
     * GET server information.
     *
     * @param sections Array of info sections to retrieve
     * @return A CompletableFuture containing the info response
     */
    public CompletableFuture<String> info(InfoOptions.Section[] sections) {
        String[] sectionStrings = new String[sections.length];
        for (int i = 0; i < sections.length; i++) {
            sectionStrings[i] = sections[i].name().toLowerCase();
        }
        return info(sectionStrings);
    }

    /**
     * Display a piece of generative computer art along with the current Valkey version.
     *
     * @param version Version of computer art to generate
     * @return A CompletableFuture containing the generative art output
     */
    public CompletableFuture<String> lolwut(int version) {
        return executeCommand(Lolwut, "VERSION", String.valueOf(version))
            .thenApply(result -> result.toString());
    }

    /**
     * Display a piece of generative computer art along with the current Valkey version.
     *
     * @param version Version of computer art to generate
     * @param parameters Additional parameters for output customization
     * @return A CompletableFuture containing the generative art output
     */
    public CompletableFuture<String> lolwut(int version, int[] parameters) {
        String[] args = new String[parameters.length + 2];
        args[0] = "VERSION";
        args[1] = String.valueOf(version);
        for (int i = 0; i < parameters.length; i++) {
            args[i + 2] = String.valueOf(parameters[i]);
        }
        return executeCommand(Lolwut, args)
            .thenApply(result -> result.toString());
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
        return executeCommand(Scan, cursor)
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
            args.addAll(java.util.Arrays.asList(options.toArgs()));
        }
        
        return executeCommand(Scan, args.toArray(new String[0]))
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
        return executeCommand(Copy, source, destination)
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
            return executeCommand(Copy, source, destination, "REPLACE")
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
        return executeCommand(Copy, source, destination, "DB", String.valueOf(destinationDB))
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
            return executeCommand(Copy, source, destination, "DB", String.valueOf(destinationDB), "REPLACE")
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
        return executeCommand(Move, key, String.valueOf(dbIndex))
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

    /**
     * Flushes all the previously watched keys for a transaction.
     * 
     * @see <a href="https://valkey.io/commands/unwatch/">valkey.io</a> for details.
     * @return <code>OK</code>.
     */
    @Override
    public CompletableFuture<String> unwatch() {
        return executeCommand(UnWatch).thenApply(response -> response.toString());
    }

    /**
     * Marks the given keys to be watched for conditional execution of a transaction.
     * 
     * @see <a href="https://valkey.io/commands/watch/">valkey.io</a> for details.
     * @param keys The keys to watch.
     * @return <code>OK</code>.
     */
    @Override
    public CompletableFuture<String> watch(String[] keys) {
        return executeCommand(Watch, keys).thenApply(response -> response.toString());
    }

    /**
     * Marks the given keys to be watched for conditional execution of a transaction.
     * 
     * @see <a href="https://valkey.io/commands/watch/">valkey.io</a> for details.
     * @param keys The keys to watch.
     * @return <code>OK</code>.
     */
    @Override
    public CompletableFuture<String> watch(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(Watch, stringKeys).thenApply(response -> response.toString());
    }

    // PubSubBaseCommands implementation
    @Override
    public CompletableFuture<String> publish(String message, String channel) {
        return executeCommand(PUBLISH, message, channel).thenApply(response -> "OK");
    }

    @Override
    public CompletableFuture<String> publish(GlideString message, GlideString channel) {
        return executeCommand(PUBLISH, message.toString(), channel.toString()).thenApply(response -> "OK");
    }

    @Override
    public CompletableFuture<Long> publish(String message, String channel, boolean sharded) {
        String commandType = sharded ? SPublish : PUBLISH;
        return executeCommand(commandType, message, channel).thenApply(response -> Long.parseLong(response.toString()));
    }

    @Override
    public CompletableFuture<Long> publish(GlideString message, GlideString channel, boolean sharded) {
        String commandType = sharded ? SPublish : PUBLISH;
        return executeCommand(commandType, message.toString(), channel.toString()).thenApply(response -> Long.parseLong(response.toString()));
    }

    @Override
    public CompletableFuture<String[]> pubsubChannels() {
        return executeCommand(PubSubChannels).thenApply(response -> {
            if (response instanceof String[]) {
                return (String[]) response;
            }
            return new String[0];
        });
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubChannelsBinary() {
        return executeCommand(PubSubChannels).thenApply(response -> {
            if (response instanceof String[]) {
                String[] stringArray = (String[]) response;
                GlideString[] result = new GlideString[stringArray.length];
                for (int i = 0; i < stringArray.length; i++) {
                    result[i] = GlideString.of(stringArray[i]);
                }
                return result;
            }
            return new GlideString[0];
        });
    }

    @Override
    public CompletableFuture<String[]> pubsubChannels(String pattern) {
        return executeCommand(PubSubChannels, pattern).thenApply(response -> {
            if (response instanceof String[]) {
                return (String[]) response;
            }
            return new String[0];
        });
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubChannels(GlideString pattern) {
        return executeCommand(PubSubChannels, pattern.toString()).thenApply(response -> {
            if (response instanceof String[]) {
                String[] stringArray = (String[]) response;
                GlideString[] result = new GlideString[stringArray.length];
                for (int i = 0; i < stringArray.length; i++) {
                    result[i] = GlideString.of(stringArray[i]);
                }
                return result;
            }
            return new GlideString[0];
        });
    }

    @Override
    public CompletableFuture<Long> pubsubNumPat() {
        return executeCommand(PubSubNumPat).thenApply(response -> Long.parseLong(response.toString()));
    }

    @Override
    public CompletableFuture<java.util.Map<String, Long>> pubsubNumSub(String[] channels) {
        return executeCommand(PubSubNumSub, channels).thenApply(response -> {
            java.util.Map<String, Long> result = new java.util.HashMap<>();
            if (response instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) response;
                for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                    result.put(entry.getKey(), Long.parseLong(entry.getValue().toString()));
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<java.util.Map<GlideString, Long>> pubsubNumSub(GlideString[] channels) {
        String[] stringChannels = new String[channels.length];
        for (int i = 0; i < channels.length; i++) {
            stringChannels[i] = channels[i].toString();
        }
        return executeCommand(PubSubNumSub, stringChannels).thenApply(response -> {
            java.util.Map<GlideString, Long> result = new java.util.HashMap<>();
            if (response instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) response;
                for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                    result.put(GlideString.of(entry.getKey()), Long.parseLong(entry.getValue().toString()));
                }
            }
            return result;
        });
    }

    // ServerManagementCommands implementation - delegate to composition layer for standalone mode
    @SuppressWarnings("unchecked")
    public CompletableFuture<String> info() {
        return serverManagement.getInfo()
            .thenApply(result -> (String) result);
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<String> info(String... sections) {
        return serverManagement.getInfo(sections)
            .thenApply(result -> (String) result);
    }
}
