/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

// Note: Using fully qualified name to avoid collision with this class
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.commands.InfoOptions;
import glide.api.models.Batch;
import glide.api.models.Transaction;
import glide.api.models.commands.batch.BatchOptions;
import glide.api.models.GlideString;
import glide.internal.ResponseNormalizer;
import glide.api.commands.TransactionsCommands;
import glide.api.commands.GenericCommands;
import glide.api.commands.ServerManagementCommands;
import glide.api.commands.StandaloneServerManagement;
import glide.api.commands.PubSubBaseCommands;
import glide.api.commands.ScriptingAndFunctionsCommands;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.utils.ArrayTransformUtils;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static glide.api.models.commands.RequestType.*;

/**
 * Glide client for connecting to a single Valkey/Redis instance.
 * This class provides the integration test API while using the refactored core client underneath.
 */
public class GlideClient extends BaseClient implements TransactionsCommands, GenericCommands, ServerManagementCommands,
        PubSubBaseCommands, ScriptingAndFunctionsCommands {

    private GlideClient(glide.internal.GlideCoreClient client) {
        super(client, createStandaloneServerManagement(client));
    }

    private static StandaloneServerManagement createStandaloneServerManagement(glide.internal.GlideCoreClient client) {
        // Create a minimal wrapper that implements ServerManagementCommands
        // This is a temporary implementation that will delegate to the BaseClient methods
        return new StandaloneServerManagement(new ServerManagementCommands() {
            @Override
            public CompletableFuture<String> info() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    Info))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> info(glide.api.models.commands.InfoOptions.Section[] sections) {
                String[] sectionNames = new String[sections.length];
                for (int i = 0; i < sections.length; i++) {
                    sectionNames[i] = sections[i].name().toLowerCase();
                }
                return client.executeCommand(new glide.internal.protocol.Command(
                    Info, sectionNames))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String[]> time() {
                return client.executeCommand(new glide.internal.protocol.Command(
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
                return client.executeCommand(new glide.internal.protocol.Command(
                    LastSave))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            @Override
            public CompletableFuture<Long> dbsize() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    DBSize))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            @Override
            public CompletableFuture<String> flushall() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    FlushAll))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushall(glide.api.models.commands.FlushMode mode) {
                return client.executeCommand(new glide.internal.protocol.Command(
                    FlushAll, mode.name()))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushdb() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    FlushDB))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> flushdb(glide.api.models.commands.FlushMode mode) {
                return client.executeCommand(new glide.internal.protocol.Command(
                    FlushDB, mode.name()))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> configRewrite() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    ConfigRewrite))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> configResetStat() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    ConfigResetStat))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<java.util.Map<String, String>> configGet(String[] parameters) {
                return client.executeCommand(new glide.internal.protocol.Command(
                    ConfigGet, parameters))
                    .thenApply(glide.internal.ResponseNormalizer::flatPairsToStringMap);
            }

            @Override
            public CompletableFuture<String> configSet(java.util.Map<String, String> parameters) {
                String[] args = new String[parameters.size() * 2];
                int i = 0;
                for (java.util.Map.Entry<String, String> entry : parameters.entrySet()) {
                    args[i++] = entry.getKey();
                    args[i++] = entry.getValue();
                }
                return client.executeCommand(new glide.internal.protocol.Command(
                    ConfigSet, args))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut() {
                return client.executeCommand(new glide.internal.protocol.Command(
                    Lolwut))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut(int[] parameters) {
                String[] args = new String[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    args[i] = String.valueOf(parameters[i]);
                }
                return client.executeCommand(new glide.internal.protocol.Command(
                    Lolwut, args))
                    .thenApply(result -> result.toString());
            }

            @Override
            public CompletableFuture<String> lolwut(int version) {
                return client.executeCommand(new glide.internal.protocol.Command(
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
                return client.executeCommand(new glide.internal.protocol.Command(
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
        // Validate RESP synchronously so tests can assert throws immediately
        var sub = config.getSubscriptionConfiguration();
        if (sub != null && config.getProtocol() == glide.api.models.configuration.ProtocolVersion.RESP2) {
            throw new glide.api.models.exceptions.ConfigurationError("PubSub subscriptions require RESP3 protocol");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                glide.internal.GlideCoreClient.Config coreConfig =
                    convertToConfig(config);

                glide.internal.GlideCoreClient coreClient =
                    new glide.internal.GlideCoreClient(coreConfig);
                BaseClient.__debugLifecycle("create-standalone", coreClient.getNativeHandle());

                GlideClient wrapper = new GlideClient(coreClient);
                if (config.getSubscriptionConfiguration() != null) {
                    wrapper.__enablePubSub();
                    var subCfg = config.getSubscriptionConfiguration();
                    if (subCfg != null) {
                        subCfg.getCallback().ifPresent(cb -> wrapper.__setPubSubCallback(cb, subCfg.getContext().orElse(null)));
                    }
                    glide.internal.GlideCoreClient.registerClient(coreClient.getNativeHandle(), wrapper);
                }
                return wrapper;
            } catch (Exception e) {
                // Propagate the full error message including "Connection refused" if present
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Client creation failed";
                // Check if this is a connection error and ensure the message is clear
                if (errorMsg.contains("Connection refused") || errorMsg.contains("Failed to create client")) {
                    throw new glide.api.models.exceptions.ClosingException(errorMsg);
                }
                throw new glide.api.models.exceptions.ClosingException(errorMsg);
            }
        });
    }

    /**
     * Convert from integration test configuration to core client configuration.
     */
    private static glide.internal.GlideCoreClient.Config convertToConfig(
            GlideClientConfiguration config) {
        return ConfigurationConverter.convertStandaloneConfig(config);
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
                        Object k = array[i];
                        Object v = array[i + 1];
                        if (k != null) {
                            config.put(k.toString(), v == null ? null : v.toString());
                        }
                    }
                } else if (result instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<Object,Object> raw = (java.util.Map<Object,Object>) result;
                    for (java.util.Map.Entry<Object,Object> e : raw.entrySet()) {
                        if (e.getKey() != null) {
                            config.put(e.getKey().toString(), e.getValue() == null ? null : e.getValue().toString());
                        }
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
        // Use the base implementation that now properly handles timeout
        return super.exec(batch, raiseOnError, options);
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
        return executeCustomCommand(args)
                .thenApply(result -> normalizeStandaloneCustom(args, result));
    }

    /**
     * Execute a custom command with GlideString arguments (GenericCommands interface implementation).
     *
     * @param args The command arguments as GlideString array
     * @return A CompletableFuture containing the command result
     */
    @Override
    public CompletableFuture<Object> customCommand(GlideString[] args) {
        return executeCustomCommand(args)
                .thenApply(result -> {
                    String[] stringArgs = new String[args.length];
                    for (int i = 0; i < args.length; i++) {
                        stringArgs[i] = args[i].getString();
                    }
                    Object norm = normalizeStandaloneCustom(stringArgs, result);
                    // For GlideString[] input, convert only string results to GlideString format
                    if (norm == null) return null;
                    if (norm instanceof String) return GlideString.of(norm);
                    return norm;
                });
    }

    private Object normalizeStandaloneCustom(String[] args, Object result) {
        // Handle commands that may return format/text structure
        boolean isInfo = args.length == 1 && "INFO".equalsIgnoreCase(args[0]);
        boolean isClientList = args.length == 2 && "CLIENT".equalsIgnoreCase(args[0]) && "LIST".equalsIgnoreCase(args[1]);
        
        // Unwrap single-entry map for non-INFO commands (legacy behavior convenience)
        if (!isInfo && result instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) result;
            
            // Check for format/text structure (like INFO commands)
            if (isClientList && m.containsKey("text")) {
                return convertInfoLikeToText(m.get("text"));
            }
            
            if (m.size() == 1) {
                Object only = m.values().iterator().next();
                if (!(only instanceof java.util.Map)) result = only;
            }
        }
        if ( args.length == 1 && "INFO".equalsIgnoreCase(args[0]) ) {
            // Debug raw shape before normalization when enabled via system property or env var
            boolean debugInfo = Boolean.getBoolean("glide.debugInfoRaw") || System.getenv("GLIDE_DEBUG_INFO") != null;
            if (debugInfo) {
                try {
                    if (result instanceof CharSequence) {
                        String rawText = result.toString();
                        glide.api.logging.Logger.debug("info.raw.len", Integer.toString(rawText.length()));
                        glide.api.logging.Logger.debug("info.raw.containsStats", Boolean.toString(rawText.contains("# Stats")));
                    }
                } catch (Throwable t) {
                    glide.api.logging.Logger.debug("info.raw.inspectError", t.toString());
                }
            }
            String legacy = glide.internal.ResponseNormalizer.formatInfo(result);
            return legacy;
        }
        if (args.length > 1 && "CLIENT".equalsIgnoreCase(args[0]) && "INFO".equalsIgnoreCase(args[1])) {
            return result != null ? result.toString() : null;
        }
        if (args.length > 1 && "CLIENT".equalsIgnoreCase(args[0]) && "LIST".equalsIgnoreCase(args[1])) {
            if (result instanceof java.util.Map) {
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) result;
                if (!m.isEmpty()) {
                    Object first = m.values().iterator().next();
                    return first != null ? first.toString() : null;
                }
                return "";
            }
            return result != null ? result.toString() : null;
        }
        if (args.length > 1 && "CLUSTER".equalsIgnoreCase(args[0]) && "NODES".equalsIgnoreCase(args[1])) {
            if (result instanceof java.util.Map) {
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) result;
                if (!m.isEmpty()) {
                    Object first = m.values().iterator().next();
                    return first != null ? first.toString() : null;
                }
                return "";
            }
            return result != null ? result.toString() : null;
        }
        return result;
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
        return executeCommand(Scan, cursor.toString())
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] scanResult = (Object[]) result;
                    if (scanResult.length >= 2) {
                        Object newCursorObj = scanResult[0];
                        GlideString newCursor = newCursorObj instanceof GlideString ? (GlideString) newCursorObj : GlideString.of(String.valueOf(newCursorObj));
                        Object keysObj = scanResult[1];
                        Object[] keysArray;
                        if (keysObj instanceof Object[]) {
                            keysArray = (Object[]) keysObj;
                        } else if (keysObj instanceof java.util.Collection) {
                            keysArray = ((java.util.Collection<?>) keysObj).toArray();
                        } else {
                            keysArray = new Object[0];
                        }
                        GlideString[] binaryKeys = new GlideString[keysArray.length];
                        for (int i=0;i<keysArray.length;i++) {
                            Object k = keysArray[i];
                            binaryKeys[i] = k instanceof GlideString ? (GlideString) k : GlideString.of(String.valueOf(k));
                        }
                        return new Object[]{ newCursor, binaryKeys };
                    }
                }
                return new Object[]{ cursor, new GlideString[0] };
            });
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
        // Build args similar to string variant but keep binary semantics in the result
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(cursor.toString());
        if (options != null) {
            args.addAll(java.util.Arrays.asList(options.toArgs()));
        }
        return executeCommand(Scan, args.toArray(new String[0]))
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] scanResult = (Object[]) result;
                    if (scanResult.length >= 2) {
                        Object newCursorObj = scanResult[0];
                        GlideString newCursor = newCursorObj instanceof GlideString ? (GlideString) newCursorObj : GlideString.of(String.valueOf(newCursorObj));
                        Object keysObj = scanResult[1];
                        Object[] keysArray;
                        if (keysObj instanceof Object[]) {
                            keysArray = (Object[]) keysObj;
                        } else if (keysObj instanceof java.util.Collection) {
                            keysArray = ((java.util.Collection<?>) keysObj).toArray();
                        } else {
                            keysArray = new Object[0];
                        }
                        GlideString[] binaryKeys = new GlideString[keysArray.length];
                        for (int i=0;i<keysArray.length;i++) {
                            Object k = keysArray[i];
                            binaryKeys[i] = k instanceof GlideString ? (GlideString) k : GlideString.of(String.valueOf(k));
                        }
                        return new Object[]{ newCursor, binaryKeys };
                    }
                }
                return new Object[]{ cursor, new GlideString[0] };
            });
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
        // Redis PUBLISH expects (channel, message)
        return executeCommand(PUBLISH, channel, message).thenApply(response -> "OK");
    }

    @Override
    public CompletableFuture<String> publish(GlideString message, GlideString channel) {
        // Redis PUBLISH expects (channel, message)
        // Use binary-safe native path to avoid UTF-8 loss
        CompletableFuture<Object> fut = new CompletableFuture<>();
        long cid = glide.internal.AsyncRegistry.register(fut);
        glide.internal.GlideNativeBridge.executePublishBinaryAsync(client.getNativeHandle(), false, channel.getBytes(), message.getBytes(), cid);
        return fut.thenApply(r -> "OK");
    }

    @Override
    public CompletableFuture<Long> publish(String message, String channel, boolean sharded) {
        String commandType = sharded ? SPublish : PUBLISH;
        // Redis PUBLISH/ SPUBLISH expect (channel, message)
        return executeCommand(commandType, channel, message).thenApply(response -> Long.parseLong(response.toString()));
    }

    @Override
    public CompletableFuture<Long> publish(GlideString message, GlideString channel, boolean sharded) {
        // Redis PUBLISH/SPUBLISH expect (channel, message)
        CompletableFuture<Object> fut = new CompletableFuture<>();
        long cid = glide.internal.AsyncRegistry.register(fut);
        glide.internal.GlideNativeBridge.executePublishBinaryAsync(client.getNativeHandle(), sharded, channel.getBytes(), message.getBytes(), cid);
        return fut.thenApply(resp -> Long.parseLong(resp.toString()));
    }

    @Override
    public CompletableFuture<String[]> pubsubChannels() {
        return executeCommand(PubSubChannels).thenApply(response -> {
            if (response instanceof String[]) {
                return (String[]) response;
            }
            if (response instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) response;
                String[] arr = new String[list.size()];
                for (int i = 0; i < list.size(); i++) arr[i] = String.valueOf(list.get(i));
                return arr;
            }
            return new String[0];
        });
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubChannelsBinary() {
        return executeCommand(PubSubChannels).thenApply(response -> {
            String[] stringArray;
            if (response instanceof String[]) {
                stringArray = (String[]) response;
            } else if (response instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) response;
                stringArray = new String[list.size()];
                for (int i = 0; i < list.size(); i++) stringArray[i] = String.valueOf(list.get(i));
            } else if (response == null) {
                stringArray = new String[0];
            } else {
                stringArray = new String[] { String.valueOf(response) };
            }
            GlideString[] result = new GlideString[stringArray.length];
            for (int i = 0; i < stringArray.length; i++) {
                result[i] = GlideString.of(stringArray[i]);
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<String[]> pubsubChannels(String pattern) {
        return executeCommand(PubSubChannels, pattern).thenApply(response -> {
            if (response instanceof String[]) {
                return (String[]) response;
            }
            if (response instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) response;
                String[] arr = new String[list.size()];
                for (int i = 0; i < list.size(); i++) arr[i] = String.valueOf(list.get(i));
                return arr;
            }
            return new String[0];
        });
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubChannels(GlideString pattern) {
        return executeCommand(PubSubChannels, pattern.toString()).thenApply(response -> {
            String[] stringArray;
            if (response instanceof String[]) {
                stringArray = (String[]) response;
            } else if (response instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) response;
                stringArray = new String[list.size()];
                for (int i = 0; i < list.size(); i++) stringArray[i] = String.valueOf(list.get(i));
            } else if (response == null) {
                stringArray = new String[0];
            } else {
                stringArray = new String[] { String.valueOf(response) };
            }
            GlideString[] result = new GlideString[stringArray.length];
            for (int i = 0; i < stringArray.length; i++) {
                result[i] = GlideString.of(stringArray[i]);
            }
            return result;
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

    // ScriptingAndFunctionsCommands implementation
    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Map<String, Map<String, Object>>>> functionStats() {
        return executeCommand(FunctionStats)
                .thenApply(result -> (Map<String, Map<String, Map<String, Object>>>) result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Map<GlideString, Map<GlideString, Object>>>> functionStatsBinary() {
        return executeCommand(FunctionStats)
                .thenApply(result -> {
                    Map<String, Map<String, Map<String, Object>>> stringResult = (Map<String, Map<String, Map<String, Object>>>) result;
                    Map<String, Map<GlideString, Map<GlideString, Object>>> binaryResult = new java.util.HashMap<>();
                    for (Map.Entry<String, Map<String, Map<String, Object>>> nodeEntry : stringResult.entrySet()) {
                        Map<GlideString, Map<GlideString, Object>> nodeData = new java.util.HashMap<>();
                        if (nodeEntry.getValue() != null) {
                            for (Map.Entry<String, Map<String, Object>> dataEntry : nodeEntry.getValue().entrySet()) {
                                // Filter out running_script without valid command info
                                if ("running_script".equalsIgnoreCase(dataEntry.getKey())) {
                                    Map<String,Object> rsMap = dataEntry.getValue();
                                    Object commandVal = rsMap == null ? null : rsMap.get("command");
                                    if (commandVal == null) {
                                        continue; // skip this entry
                                    }
                                }
                                Map<String, Object> innerMap = dataEntry.getValue();
                                // Canonicalize engines stats ordering & numeric types
                                if ("engines".equalsIgnoreCase(dataEntry.getKey()) && innerMap != null) {
                                    Map<GlideString, Object> canonicalOuter = new java.util.HashMap<>();
                                    for (Map.Entry<String,Object> engineEntry : innerMap.entrySet()) {
                                        Map<GlideString,Object> engineStatsCanonical = new java.util.LinkedHashMap<>();
                                        Object engineStatsObj = engineEntry.getValue();
                                        if (engineStatsObj instanceof java.util.Map) {
                                            @SuppressWarnings("rawtypes") java.util.Map statsMap = (java.util.Map) engineStatsObj;
                                            Object libs = statsMap.get("libraries_count");
                                            Object funcs = statsMap.get("functions_count");
                                            Long libsL = libs == null ? 0L : Long.parseLong(String.valueOf(libs));
                                            Long funcsL = funcs == null ? 0L : Long.parseLong(String.valueOf(funcs));
                                            engineStatsCanonical.put(GlideString.of("libraries_count"), libsL);
                                            engineStatsCanonical.put(GlideString.of("functions_count"), funcsL);
                                        }
                                        canonicalOuter.put(GlideString.of(engineEntry.getKey()), engineStatsCanonical);
                                    }
                                    nodeData.put(GlideString.of(dataEntry.getKey()), canonicalOuter);
                                } else {
                                    Map<GlideString, Object> innerData = new java.util.HashMap<>();
                                    if (innerMap != null) {
                                        for (Map.Entry<String, Object> innerEntry : innerMap.entrySet()) {
                                            innerData.put(GlideString.of(innerEntry.getKey()), innerEntry.getValue());
                                        }
                                    }
                                    nodeData.put(GlideString.of(dataEntry.getKey()), innerData);
                                }
                            }
                        }
                        binaryResult.put(nodeEntry.getKey(), nodeData);
                    }
                    return binaryResult;
                });
    }

    @Override
    public CompletableFuture<String> scriptFlush() {
        return executeCommand(ScriptFlush)
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> scriptFlush(glide.api.models.commands.FlushMode flushMode) {
        return executeCommand(ScriptFlush, flushMode.toString())
            .thenApply(result -> result.toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean[]> scriptExists(String[] sha1s) {
        return executeCommand(ScriptExists, sha1s)
            .thenApply(ResponseNormalizer.BOOLEAN_ARRAY::apply);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean[]> scriptExists(GlideString[] sha1s) {
        String[] stringArgs = new String[sha1s.length];
        for (int i = 0; i < sha1s.length; i++) {
            stringArgs[i] = sha1s[i].toString();
        }
        return executeCommand(ScriptExists, stringArgs)
            .thenApply(ResponseNormalizer.BOOLEAN_ARRAY::apply);
    }

    // Function-related methods
    @Override
    public CompletableFuture<String> functionLoad(String libraryCode, boolean replace) {
        if (replace) {
            return executeCommand(FunctionLoad, "REPLACE", libraryCode)
                .thenApply(result -> result.toString());
        } else {
            return executeCommand(FunctionLoad, libraryCode)
                .thenApply(result -> result.toString());
        }
    }

    @Override
    public CompletableFuture<GlideString> functionLoad(GlideString libraryCode, boolean replace) {
        if (replace) {
            return executeCommand(FunctionLoad, "REPLACE", libraryCode.toString())
                .thenApply(result -> GlideString.of(result));
        } else {
            return executeCommand(FunctionLoad, libraryCode.toString())
                .thenApply(result -> GlideString.of(result));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>[]> functionList(boolean withCode) {
        if (withCode) {
            return executeCommand(FunctionList, "WITHCODE")
                .thenApply(ArrayTransformUtils::toMapStringObjectArray);
        } else {
            return executeCommand(FunctionList)
                .thenApply(ArrayTransformUtils::toMapStringObjectArray);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode) {
        return executeCommand(FunctionList, withCode ? new String[]{"WITHCODE"} : new String[0])
            .thenApply(glide.utils.ArrayTransformUtils::deepConvertFunctionList);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode) {
        if (withCode) {
            return executeCommand(FunctionList, "LIBRARYNAME", libNamePattern, "WITHCODE")
                .thenApply(ArrayTransformUtils::toMapStringObjectArray);
        } else {
            return executeCommand(FunctionList, "LIBRARYNAME", libNamePattern)
                .thenApply(ArrayTransformUtils::toMapStringObjectArray);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(GlideString libNamePattern, boolean withCode) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("LIBRARYNAME");
        args.add(libNamePattern.toString());
        if (withCode) args.add("WITHCODE");
        return executeCommand(FunctionList, args.toArray(new String[0]))
            .thenApply(glide.utils.ArrayTransformUtils::deepConvertFunctionList);
    }

    @Override
    public CompletableFuture<String> functionFlush() {
        return executeCommand(FunctionFlush)
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> functionFlush(FlushMode mode) {
        return executeCommand(FunctionFlush, mode.toString())
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> functionDelete(String libName) {
        return executeCommand(FunctionDelete, libName)
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> functionDelete(GlideString libName) {
        return executeCommand(FunctionDelete, libName.toString())
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<byte[]> functionDump() {
        // Use binary command to avoid charset transcoding issues for arbitrary payload bytes
        glide.internal.protocol.BinaryCommand command = new glide.internal.protocol.BinaryCommand(FunctionDump.toString());
        return executeBinaryCommand(command, result -> {
            if (result == null) return null;
            if (result instanceof byte[]) return (byte[]) result;
            if (result instanceof GlideString) return ((GlideString) result).getBytes();
            return result.toString().getBytes();
        });
    }

    @Override
    public CompletableFuture<String> functionRestore(byte[] payload) {
        // Order: FUNCTION RESTORE <payload>
        glide.internal.protocol.BinaryCommand command = new glide.internal.protocol.BinaryCommand(FunctionRestore.toString())
            .addArgument(payload);
        return executeBinaryCommand(command, result -> result == null ? null : result.toString());
    }

    @Override
    public CompletableFuture<String> functionRestore(byte[] payload, glide.api.models.commands.function.FunctionRestorePolicy policy) {
        // Order: FUNCTION RESTORE <payload> <policy>
        glide.internal.protocol.BinaryCommand command = new glide.internal.protocol.BinaryCommand(FunctionRestore.toString())
            .addArgument(payload)
            .addArgument(policy.toString());
        return executeBinaryCommand(command, result -> result == null ? null : result.toString());
    }


    @Override
    public CompletableFuture<Object> fcall(String function) {
        // Always send numkeys=0 for FCALL simple overload
        return executeCommand(FCall, function, "0");
    }

    public CompletableFuture<Object> fcall(GlideString function) {
        // Always send numkeys=0 for FCALL simple overload
        return executeCommand(FCall, function.toString(), "0");
    }

    public CompletableFuture<Object> fcallReadOnly(String function) {
        // Always send numkeys=0 for FCALL_RO simple overload
        return executeCommand(FCallReadOnly, function, "0");
    }

    public CompletableFuture<Object> fcallReadOnly(GlideString function) {
        // Always send numkeys=0 for FCALL_RO simple overload
        return executeCommand(FCallReadOnly, function.toString(), "0");
    }

    @Override
    public CompletableFuture<String> functionKill() {
        return executeCommand(FunctionKill)
            .thenApply(result -> result.toString());
    }

    private String convertInfoLikeToText(Object nodeResult) {
        if (nodeResult == null)
            return null;
        if (nodeResult instanceof String)
            return (String) nodeResult;
        if (nodeResult instanceof java.util.Map) {
            StringBuilder sb = new StringBuilder();
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) nodeResult;
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object val = e.getValue();
                String valText;
                if (val instanceof java.util.Map || val instanceof java.util.List) {
                    valText = String.valueOf(val);
                } else if (val != null && val.getClass().isArray()) {
                    valText = java.util.Arrays.deepToString(new Object[] { val });
                } else {
                    valText = String.valueOf(val);
                }
                sb.append(key).append(":").append(valText).append('\n');
            }
            return sb.toString();
        }
        return String.valueOf(nodeResult);
    }
}
