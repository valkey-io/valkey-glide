/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.SortOptionsBinary;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.ClusterBatch;
import glide.api.models.ClusterTransaction;
import glide.api.models.commands.batch.ClusterBatchOptions;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.commands.TransactionsClusterCommands;
import glide.api.commands.ClusterCommandExecutor;
import glide.api.commands.GenericBaseCommands;
import glide.api.commands.ServerManagementClusterCommands;
import glide.api.commands.ClusterServerManagement;
import glide.api.commands.GenericClusterCommands;
import glide.api.commands.HyperLogLogBaseCommands;
import glide.api.commands.ListBaseCommands;
import glide.api.commands.PubSubBaseCommands;
import glide.api.commands.ScriptingAndFunctionsClusterCommands;
import glide.api.commands.SetBaseCommands;
import glide.api.commands.SortedSetBaseCommands;
import glide.api.commands.StringBaseCommands;
import glide.api.commands.BitmapBaseCommands;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.commands.scan.SScanOptions;
import glide.api.models.commands.scan.SScanOptionsBinary;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.ScoreFilter;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.ScriptOptionsGlideString;
import glide.api.models.commands.ScriptArgOptions;
import glide.api.models.commands.ScriptArgOptionsGlideString;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeysBinary;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.ListDirection;
import glide.api.models.Response;
import glide.utils.ArrayTransformUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static glide.api.models.commands.RequestType.*;

/**
 * Glide cluster client for connecting to a Valkey/Redis cluster.
 * This implementation provides cluster-aware operations with proper routing support.
 * Key features include:
 * - ClusterValue return types for multi-node operations
 * - Route-based command targeting
 * - Automatic cluster topology handling
 * - Interface segregation for cluster-specific APIs
 */
public class GlideClusterClient extends BaseClient implements TransactionsClusterCommands, ClusterCommandExecutor,
        GenericBaseCommands, GenericClusterCommands, HyperLogLogBaseCommands, ListBaseCommands, PubSubBaseCommands,
        ServerManagementClusterCommands, ScriptingAndFunctionsClusterCommands, SetBaseCommands, SortedSetBaseCommands,
        StringBaseCommands, AutoCloseable {

    private GlideClusterClient(io.valkey.glide.core.client.GlideClient client) {
        super(client, createClusterServerManagement(client));
    }

    private static ClusterServerManagement createClusterServerManagement(io.valkey.glide.core.client.GlideClient client) {
        // Create a minimal wrapper that implements ServerManagementClusterCommands
        return new ClusterServerManagement(new ServerManagementClusterCommands() {
            public CompletableFuture<ClusterValue<String>> info() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Info))
                    .thenApply(result -> ClusterValue.ofSingleValue(result.toString()));
            }

            public CompletableFuture<ClusterValue<String>> info(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Info), route)
                    .thenApply(result -> {
                        if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                            return ClusterValue.ofSingleValue(result.toString());
                        } else {
                            // For multi-node routes, expect a map result
                            if (result instanceof Map) {
                                Map<String, String> mapResult = new java.util.HashMap<>();
                                    ((Map<?, ?>) result)
                                            .forEach((key, value) ->
                                    mapResult.put(key.toString(), value.toString()));
                                return ClusterValue.ofMultiValue(mapResult);
                            }
                            return ClusterValue.ofSingleValue(result.toString());
                        }
                    });
            }

            public CompletableFuture<ClusterValue<String>> info(Section[] sections) {
                String[] sectionNames = new String[sections.length];
                for (int i = 0; i < sections.length; i++) {
                    sectionNames[i] = sections[i].name().toLowerCase();
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Info, sectionNames))
                    .thenApply(result -> ClusterValue.ofSingleValue(result.toString()));
            }

            public CompletableFuture<ClusterValue<String>> info(Section[] sections, Route route) {
                String[] sectionNames = new String[sections.length];
                for (int i = 0; i < sections.length; i++) {
                    sectionNames[i] = sections[i].name().toLowerCase();
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Info, sectionNames), route)
                    .thenApply(result -> {
                        if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                            return ClusterValue.ofSingleValue(result.toString());
                        } else {
                            // For multi-node routes, expect a map result
                            if (result instanceof Map) {
                                Map<String, String> mapResult = new java.util.HashMap<>();
                                    ((Map<?, ?>) result)
                                            .forEach((key, value) ->
                                    mapResult.put(key.toString(), value.toString()));
                                return ClusterValue.ofMultiValue(mapResult);
                            }
                            return ClusterValue.ofSingleValue(result.toString());
                        }
                    });
            }

            public CompletableFuture<String> configRewrite() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigRewrite))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> configRewrite(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigRewrite), route)
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> configResetStat() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigResetStat))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> configResetStat(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigResetStat), route)
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<Map<String, String>> configGet(String[] parameters) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigGet, parameters))
                    .thenApply(result -> {
                        Map<String, String> config = new java.util.HashMap<>();
                        if (result instanceof Object[]) {
                            Object[] array = (Object[]) result;
                            for (int i = 0; i < array.length - 1; i += 2) {
                                config.put(array[i].toString(), array[i + 1].toString());
                            }
                        }
                        return config;
                    });
            }

            public CompletableFuture<ClusterValue<Map<String, String>>> configGet(String[] parameters, Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigGet, parameters), route)
                    .thenApply(result -> {
                        if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                            Map<String, String> config = new java.util.HashMap<>();
                            if (result instanceof Object[]) {
                                Object[] array = (Object[]) result;
                                for (int i = 0; i < array.length - 1; i += 2) {
                                    config.put(array[i].toString(), array[i + 1].toString());
                                }
                            }
                            return ClusterValue.ofSingleValue(config);
                        } else {
                            // For multi-node routes, expect a map of node -> config
                            if (result instanceof Map) {
                                Map<String, Map<String, String>> nodeConfigs = new java.util.HashMap<>();
                                ((Map<?, ?>) result).forEach((nodeKey, nodeValue) -> {
                                    Map<String, String> config = new java.util.HashMap<>();
                                    if (nodeValue instanceof Object[]) {
                                        Object[] array = (Object[]) nodeValue;
                                        for (int i = 0; i < array.length - 1; i += 2) {
                                            config.put(array[i].toString(), array[i + 1].toString());
                                        }
                                    }
                                    nodeConfigs.put(nodeKey.toString(), config);
                                });
                                return ClusterValue.ofMultiValue(nodeConfigs);
                            }
                            // Fallback to single value
                            Map<String, String> config = new java.util.HashMap<>();
                            if (result instanceof Object[]) {
                                Object[] array = (Object[]) result;
                                for (int i = 0; i < array.length - 1; i += 2) {
                                    config.put(array[i].toString(), array[i + 1].toString());
                                }
                            }
                            return ClusterValue.ofSingleValue(config);
                        }
                    });
            }

            public CompletableFuture<String> configSet(Map<String, String> parameters) {
                String[] args = new String[parameters.size() * 2];
                int i = 0;
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    args[i++] = entry.getKey();
                    args[i++] = entry.getValue();
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigSet, args))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> configSet(Map<String, String> parameters, Route route) {
                String[] args = new String[parameters.size() * 2];
                int i = 0;
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    args[i++] = entry.getKey();
                    args[i++] = entry.getValue();
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    ConfigSet, args), route)
                    .thenApply(result -> result.toString());
            }

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

            public CompletableFuture<ClusterValue<String[]>> time(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Time), route)
                    .thenApply(result -> {
                        if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                            if (result instanceof Object[]) {
                                Object[] objects = (Object[]) result;
                                String[] time = new String[objects.length];
                                for (int i = 0; i < objects.length; i++) {
                                    time[i] = objects[i].toString();
                                }
                                return ClusterValue.ofSingleValue(time);
                            }
                            return ClusterValue.ofSingleValue(new String[0]);
                        } else {
                            // For multi-node routes, expect a map of node -> time
                            if (result instanceof Map) {
                                Map<String, String[]> nodeResults = new java.util.HashMap<>();
                                ((Map<?, ?>) result).forEach((nodeKey, nodeValue) -> {
                                    if (nodeValue instanceof Object[]) {
                                        Object[] objects = (Object[]) nodeValue;
                                        String[] time = new String[objects.length];
                                        for (int i = 0; i < objects.length; i++) {
                                            time[i] = objects[i].toString();
                                        }
                                        nodeResults.put(nodeKey.toString(), time);
                                    }
                                });
                                return ClusterValue.ofMultiValue(nodeResults);
                            }
                            // Fallback to single value
                            if (result instanceof Object[]) {
                                Object[] objects = (Object[]) result;
                                String[] time = new String[objects.length];
                                for (int i = 0; i < objects.length; i++) {
                                    time[i] = objects[i].toString();
                                }
                                return ClusterValue.ofSingleValue(time);
                            }
                            return ClusterValue.ofSingleValue(new String[0]);
                        }
                    });
            }

            public CompletableFuture<Long> lastsave() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    LastSave))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            public CompletableFuture<ClusterValue<Long>> lastsave(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    LastSave), route)
                    .thenApply(result -> {
                        if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                            return ClusterValue.ofSingleValue(Long.parseLong(result.toString()));
                        } else {
                            // For multi-node routes, expect a map of node -> timestamp
                            if (result instanceof Map) {
                                Map<String, Long> nodeResults = new java.util.HashMap<>();
                                    ((Map<?, ?>) result).forEach((nodeKey,
                                            nodeValue) ->
                                    nodeResults.put(nodeKey.toString(), Long.parseLong(nodeValue.toString())));
                                return ClusterValue.ofMultiValue(nodeResults);
                            }
                            return ClusterValue.ofSingleValue(Long.parseLong(result.toString()));
                        }
                    });
            }

            public CompletableFuture<String> flushall() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushAll))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushall(FlushMode mode) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushAll, mode.name()))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushall(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushAll), route)
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushall(FlushMode mode, Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushAll, mode.name()), route)
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushdb() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushDB))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushdb(FlushMode mode) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushDB, mode.name()))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushdb(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushDB), route)
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> flushdb(FlushMode mode, Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    FlushDB, mode.name()), route)
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> lolwut() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> lolwut(int[] parameters) {
                String[] args = new String[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    args[i] = String.valueOf(parameters[i]);
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, args))
                    .thenApply(result -> result.toString());
            }

            public CompletableFuture<String> lolwut(int version) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, "VERSION", String.valueOf(version)))
                    .thenApply(result -> result.toString());
            }

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

            public CompletableFuture<ClusterValue<String>> lolwut(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut))
                    .thenApply(result -> ClusterValue.ofSingleValue(result.toString()));
            }

            public CompletableFuture<ClusterValue<String>> lolwut(int[] parameters, Route route) {
                String[] args = new String[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    args[i] = String.valueOf(parameters[i]);
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, args))
                    .thenApply(result -> ClusterValue.ofSingleValue(result.toString()));
            }

            public CompletableFuture<ClusterValue<String>> lolwut(int version, Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, "VERSION", String.valueOf(version)))
                    .thenApply(result -> ClusterValue.ofSingleValue(result.toString()));
            }

            public CompletableFuture<ClusterValue<String>> lolwut(int version, int[] parameters, Route route) {
                String[] args = new String[parameters.length + 2];
                args[0] = "VERSION";
                args[1] = String.valueOf(version);
                for (int i = 0; i < parameters.length; i++) {
                    args[i + 2] = String.valueOf(parameters[i]);
                }
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    Lolwut, args))
                    .thenApply(result -> ClusterValue.ofSingleValue(result.toString()));
            }

            public CompletableFuture<Long> dbsize() {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    DBSize))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }

            public CompletableFuture<Long> dbsize(Route route) {
                return client.executeCommand(new io.valkey.glide.core.commands.Command(
                    DBSize))
                    .thenApply(result -> Long.parseLong(result.toString()));
            }
        });
    }

    /**
     * Create a new GlideClusterClient with the given configuration.
     * Initializes a cluster-aware client with proper node discovery and routing.
     *
     * @param config The cluster client configuration
     * @return A CompletableFuture containing the new client
     */
    public static CompletableFuture<GlideClusterClient> createClient(GlideClusterClientConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Convert cluster configuration to core client configuration
                java.util.List<String> addresses = new java.util.ArrayList<>();

                if (config.getAddresses() != null) {
                    for (var address : config.getAddresses()) {
                        addresses.add(address.getHost() + ":" + address.getPort());
                    }
                }

                if (addresses.isEmpty()) {
                    addresses.add("localhost:6379");
                }

                // Create core client configuration with cluster-aware settings
                io.valkey.glide.core.client.GlideClient.Config coreConfig =
                    new io.valkey.glide.core.client.GlideClient.Config(addresses);

                // Initialize core client with cluster support
                io.valkey.glide.core.client.GlideClient coreClient =
                    new io.valkey.glide.core.client.GlideClient(coreConfig);

                return new GlideClusterClient(coreClient);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create GlideClusterClient", e);
            }
        });
    }


    // ServerManagementClusterCommands methods are implemented through composition pattern
    // via the ClusterServerManagement instance passed to BaseClient constructor

    /**
     * Execute a cluster batch of commands.
     *
     * @param batch The cluster batch of commands to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError) {
        return super.exec(batch, raiseOnError);
    }

    /**
     * Execute a cluster transaction (atomic batch).
     *
     * @param transaction The cluster transaction to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(ClusterTransaction transaction, boolean raiseOnError) {
        return super.exec(transaction, raiseOnError);
    }

    /**
     * @deprecated Use {@link #exec(ClusterBatch, boolean)} instead. This method is being replaced by
     *     a more flexible approach using {@link ClusterBatch}.
     */
    @Deprecated
    public CompletableFuture<Object[]> exec(ClusterTransaction transaction) {
        return exec(transaction, true);
    }

    public CompletableFuture<String> watch(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].getString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Watch, stringKeys))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> unwatch() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(UnWatch, new String[0]))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<ClusterValue<String>> unwatch(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(UnWatch, new String[0]), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<String>> info(InfoOptions.Section[] sections) {
        String[] args = new String[sections.length];
        for (int i = 0; i < sections.length; i++) {
            args[i] = sections[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Info, args))
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<String>> info(InfoOptions.Section[] sections, Object route) {
        String[] args = new String[sections.length];
        for (int i = 0; i < sections.length; i++) {
            args[i] = sections[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Info, args), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<Object[]> scan(ClusterScanCursor cursor, ScanOptions options) {
        // For cluster scan, use native JNI client implementation
        // The cursor and options are handled by the native implementation
        String[] args = options != null ? options.toArgs() : new String[0];
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Scan, args), cursor)
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor, ScanOptions options) {
        // For cluster scan binary, use native JNI client implementation
        // The cursor and options are handled by the native implementation
        String[] args = options != null ? options.toArgs() : new String[0];
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Scan, args), cursor)
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<Object[]> scan(ClusterScanCursor cursor) {
        // scan with default options
        return scan(cursor, ScanOptions.builder().build());
    }

    public CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor) {
        // scanBinary with default options
        return scanBinary(cursor, ScanOptions.builder().build());
    }

    public CompletableFuture<GlideString> randomKeyBinary() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(RandomKey, new String[0]))
            .thenApply(result -> GlideString.of(result));
    }

    public CompletableFuture<GlideString> randomKeyBinary(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(RandomKey, new String[0]), route)
            .thenApply(result -> GlideString.of(result));
    }

    public CompletableFuture<String> randomKey() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(RandomKey, new String[0]))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> randomKey(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(RandomKey, new String[0]), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<ClusterValue<Object>> customCommand(GlideString[] args, Route route) {
        // Convert GlideString[] to String[] for the native command
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].getString();
        }

        // Execute the custom command with route
        return client.executeCommand(new io.valkey.glide.core.commands.Command(stringArgs[0],
                    java.util.Arrays.copyOfRange(stringArgs, 1, stringArgs.length)), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> customCommand(String[] args, Route route) {
        // Execute the custom command with route
        return client.executeCommand(new io.valkey.glide.core.commands.Command(args[0],
                    java.util.Arrays.copyOfRange(args, 1, args.length)), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> customCommand(GlideString[] args) {
        // Convert GlideString[] to String[] for the native command
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].getString();
        }

        // Execute the custom command without route
        return client.executeCommand(new io.valkey.glide.core.commands.Command(stringArgs[0],
                    java.util.Arrays.copyOfRange(stringArgs, 1, stringArgs.length)))
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> customCommand(String[] args) {
        // Execute the custom command without route
        return client.executeCommand(new io.valkey.glide.core.commands.Command(args[0],
                    java.util.Arrays.copyOfRange(args, 1, args.length)))
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<String> pfmerge(GlideString destination, GlideString[] sourceKeys) {
        // Convert GlideString parameters to String for command execution
        String[] args = new String[sourceKeys.length + 1];
        args[0] = destination.getString();
        for (int i = 0; i < sourceKeys.length; i++) {
            args[i + 1] = sourceKeys[i].getString();
        }

        return client.executeCommand(new io.valkey.glide.core.commands.Command(PfMerge, args))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> pfmerge(String destination, String[] sourceKeys) {
        String[] args = new String[sourceKeys.length + 1];
        args[0] = destination;
        System.arraycopy(sourceKeys, 0, args, 1, sourceKeys.length);

        return client.executeCommand(new io.valkey.glide.core.commands.Command(PfMerge, args))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<Long> pfcount(GlideString[] keys) {
        // Convert GlideString[] to String[] for command execution
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].getString();
        }

        return client.executeCommand(new io.valkey.glide.core.commands.Command(PfCount, stringKeys))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> pfcount(String[] keys) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PfCount, keys))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Boolean> pfadd(GlideString key, GlideString[] elements) {
        // Convert GlideString parameters to String for command execution
        String[] args = new String[elements.length + 1];
        args[0] = key.getString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].getString();
        }

        return client.executeCommand(new io.valkey.glide.core.commands.Command(PfAdd, args))
            .thenApply(result -> result.equals(1L));
    }

    public CompletableFuture<Boolean> pfadd(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);

        return client.executeCommand(new io.valkey.glide.core.commands.Command(PfAdd, args))
            .thenApply(result -> result.equals(1L));
    }

    public CompletableFuture<Map<String, Long>> pubsubNumSub(String[] channels) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubNumSub, channels))
            .thenApply(result -> (Map<String, Long>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Long>> pubsubNumSub(GlideString[] channels) {
        // Convert GlideString[] to String[] for command execution
        String[] stringChannels = new String[channels.length];
        for (int i = 0; i < channels.length; i++) {
            stringChannels[i] = channels[i].getString();
        }

        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubNumSub, stringChannels))
            .thenApply(result -> {
                // Convert result map keys back to GlideString
                Map<String, Long> stringMap = (Map<String, Long>) result;
                Map<GlideString, Long> glideStringMap = new java.util.HashMap<>();
                for (Map.Entry<String, Long> entry : stringMap.entrySet()) {
                    glideStringMap.put(GlideString.of(entry.getKey()), entry.getValue());
                }
                return glideStringMap;
            });
    }

    public CompletableFuture<Long> pubsubNumPat() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubNumPat, new String[0]))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<GlideString[]> pubsubChannels(GlideString pattern) {
        String[] args = {pattern.getString()};
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubChannels, args))
            .thenApply(result -> {
                String[] stringResults = (String[]) result;
                GlideString[] glideResults = new GlideString[stringResults.length];
                for (int i = 0; i < stringResults.length; i++) {
                    glideResults[i] = GlideString.of(stringResults[i]);
                }
                return glideResults;
            });
    }

    public CompletableFuture<String[]> pubsubChannels(String pattern) {
        String[] args = {pattern};
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubChannels, args))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<GlideString[]> pubsubChannelsBinary() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubChannels, new String[0]))
            .thenApply(result -> {
                String[] stringResults = (String[]) result;
                GlideString[] glideResults = new GlideString[stringResults.length];
                for (int i = 0; i < stringResults.length; i++) {
                    glideResults[i] = GlideString.of(stringResults[i]);
                }
                return glideResults;
            });
    }

    public CompletableFuture<String[]> pubsubChannels() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PubSubChannels, new String[0]))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<Long> publish(GlideString message, GlideString channel, boolean sharded) {
        String command = sharded ? "SPUBLISH" : "PUBLISH";
        String[] args = {channel.getString(), message.getString()};
        return client.executeCommand(new io.valkey.glide.core.commands.Command(command, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> publish(String message, String channel, boolean sharded) {
        String command = sharded ? "SPUBLISH" : "PUBLISH";
        String[] args = {channel, message};
        return client.executeCommand(new io.valkey.glide.core.commands.Command(command, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<String> publish(GlideString message, GlideString channel) {
        String[] args = {channel.getString(), message.getString()};
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PUBLISH, args))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> publish(String message, String channel) {
        String[] args = {channel, message};
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PUBLISH, args))
            .thenApply(result -> result.toString());
    }

    /**
     * Execute a cluster batch with additional options.
     *
     * @param batch The cluster batch to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @param options Additional execution options
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError, ClusterBatchOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<io.valkey.glide.core.commands.Command> commands = batch.getCommands();

                if (batch.isAtomic()) {
                    // Execute as atomic transaction using MULTI/EXEC with options
                    return executeAtomicBatchWithOptions(commands, raiseOnError, options);
                } else {
                    // Execute as pipeline (non-atomic) with options
                    return executeNonAtomicBatchWithOptions(commands, raiseOnError, options);
                }
            } catch (Exception e) {
                if (raiseOnError) {
                    throw new RuntimeException("Failed to execute cluster batch with options", e);
                }
                return new Object[0];
            }
        });
    }

    /**
     * Execute a custom command that returns a cluster value.
     * This method implements the ClusterCommandExecutor interface to provide
     * cluster-specific return type without overriding the parent method.
     *
     * @param args The command arguments
     * @return A CompletableFuture containing the result wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<Object>> customClusterCommand(String[] args) {
        // Directly implement the logic to avoid override issues
        if (args.length == 0) {
            return CompletableFuture.completedFuture(ClusterValue.ofSingleValue(null));
        }

        // Execute as raw command since we no longer use CommandType enum
        String commandName = args[0];
        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, args.length - 1);

        // Execute using executeCustomCommand for cluster operations
        return executeCustomCommand(args)
            .thenApply(ClusterValue::ofSingleValue);
    }

    /**
     * Execute a custom command with GlideString arguments that returns a cluster value.
     * This method implements the ClusterCommandExecutor interface to provide
     * cluster-specific return type without overriding the parent method.
     *
     * @param args The command arguments as GlideString array
     * @return A CompletableFuture containing the result wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<Object>> customClusterCommand(GlideString[] args) {
        if (args.length == 0) {
            return CompletableFuture.completedFuture(ClusterValue.ofSingleValue(null));
        }

        // Convert GlideString[] to String[]
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }

        return customClusterCommand(stringArgs);
    }


    /**
     * Execute a custom command that returns a cluster value.
     * This method provides the expected API for integration tests by
     * using a List instead of array to avoid override conflicts.
     *
     * @param args The command arguments as a List
     * @return A CompletableFuture containing the result wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<Object>> customCommand(java.util.List<String> args) {
        return customClusterCommand(args.toArray(new String[0]));
    }

    // Missing ServerManagement methods for cluster client


    /**
     * Displays a piece of generative computer art and the Valkey version.
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @return A CompletableFuture containing a piece of generative computer art along with the current Valkey version.
     */
    public CompletableFuture<String> lolwut() {
        return executeCommand(Lolwut)
            .thenApply(result -> result.toString());
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output.
     * @return A CompletableFuture containing a piece of generative computer art along with the current Valkey version.
     */
    public CompletableFuture<String> lolwut(int[] parameters) {
        String[] args = Arrays.stream(parameters)
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        return executeCommand(Lolwut, args)
            .thenApply(result -> result.toString());
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @return A CompletableFuture containing a piece of generative computer art along with the current Valkey version.
     */
    public CompletableFuture<String> lolwut(int version) {
        return executeCommand(Lolwut, VERSION_VALKEY_API, String.valueOf(version))
            .thenApply(result -> result.toString());
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output.
     * @return A CompletableFuture containing a piece of generative computer art along with the current Valkey version.
     */
    public CompletableFuture<String> lolwut(int version, int[] parameters) {
        List<String> args = new ArrayList<>();
        args.add(VERSION_VALKEY_API);
        args.add(String.valueOf(version));
        for (int param : parameters) {
            args.add(String.valueOf(param));
        }
        return executeCommand(Lolwut, args.toArray(new String[0]))
            .thenApply(result -> result.toString());
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A CompletableFuture containing a ClusterValue with generative computer art.
     */
    public CompletableFuture<ClusterValue<String>> lolwut(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Lolwut), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(result.toString());
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, String> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result)
                                    .forEach((key, value) ->
                            mapResult.put(key.toString(), value.toString()));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(result.toString());
                }
            });
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output.
     * @param route Specifies the routing configuration for the command.
     * @return A CompletableFuture containing a ClusterValue with generative computer art.
     */
    public CompletableFuture<ClusterValue<String>> lolwut(int[] parameters, Route route) {
        String[] args = Arrays.stream(parameters)
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Lolwut, args), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(result.toString());
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, String> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result)
                                    .forEach((key, value) ->
                            mapResult.put(key.toString(), value.toString()));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(result.toString());
                }
            });
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param route Specifies the routing configuration for the command.
     * @return A CompletableFuture containing a ClusterValue with generative computer art.
     */
    public CompletableFuture<ClusterValue<String>> lolwut(int version, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Lolwut, VERSION_VALKEY_API, String.valueOf(version)), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(result.toString());
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, String> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result)
                                    .forEach((key, value) ->
                            mapResult.put(key.toString(), value.toString()));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(result.toString());
                }
            });
    }

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output.
     * @param route Specifies the routing configuration for the command.
     * @return A CompletableFuture containing a ClusterValue with generative computer art.
     */
    public CompletableFuture<ClusterValue<String>> lolwut(int version, int[] parameters, Route route) {
        List<String> args = new ArrayList<>();
        args.add(VERSION_VALKEY_API);
        args.add(String.valueOf(version));
        for (int param : parameters) {
            args.add(String.valueOf(param));
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Lolwut, args.toArray(new String[0])), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(result.toString());
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, String> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result)
                                    .forEach((key, value) ->
                            mapResult.put(key.toString(), value.toString()));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(result.toString());
                }
            });
    }


    // Missing ping method overloads that tests expect

    /**
     * Ping the server with a message and routing.
     *
     * @param message The message to ping with
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the ping response
     */
    public CompletableFuture<String> ping(String message, Route route) {
        // Use the route parameter to target specific nodes
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Ping, message), route)
            .thenApply(result -> result.toString());
    }

    /**
     * Ping the server with a GlideString message and routing.
     *
     * @param message The message to ping with
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the ping response
     */
    public CompletableFuture<GlideString> ping(GlideString message, Route route) {
        // Use the route parameter to target specific nodes
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Ping, message.toString()), route)
            .thenApply(result -> GlideString.of(result.toString()));
    }

    /**
     * Ping the server with routing (no message).
     *
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the ping response
     */
    public CompletableFuture<String> ping(Route route) {
        // Use the route parameter to target specific nodes
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Ping), route)
            .thenApply(result -> result.toString());
    }

    // Missing client management methods with routing

    /**
     * GET the client ID with routing.
     *
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the client ID wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<Long>> clientId(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            ClientId), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(Long.parseLong(result.toString()));
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, Long> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result).forEach(
                                    (key, value) ->
                            mapResult.put(key.toString(), Long.parseLong(value.toString())));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(Long.parseLong(result.toString()));
                }
            });
    }

    /**
     * GET the client name with routing.
     *
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the client name wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<String>> clientGetName(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            ClientGetName), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(result == null ? null : result.toString());
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, String> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result).forEach((key,
                                    value) ->
                            mapResult.put(key.toString(), value == null ? null : value.toString()));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(result == null ? null : result.toString());
                }
            });
    }

    /**
     * GET configuration values with routing.
     *
     * @param parameters The configuration parameters to get
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the configuration values wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<Map<String, String>>> configGet(String[] parameters, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            ConfigGet, parameters), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    Map<String, String> config = new java.util.HashMap<>();
                    if (result instanceof Object[]) {
                        Object[] array = (Object[]) result;
                        for (int i = 0; i < array.length - 1; i += 2) {
                            config.put(array[i].toString(), array[i + 1].toString());
                        }
                    }
                    return ClusterValue.ofSingleValue(config);
                } else {
                    // For multi-node routes, expect a map result where each node maps to config
                    if (result instanceof Map) {
                        Map<String, Map<String, String>> mapResult = new java.util.HashMap<>();
                        ((Map<?, ?>) result).forEach((key, value) -> {
                            Map<String, String> nodeConfig = new java.util.HashMap<>();
                            if (value instanceof Object[]) {
                                Object[] array = (Object[]) value;
                                for (int i = 0; i < array.length - 1; i += 2) {
                                    nodeConfig.put(array[i].toString(), array[i + 1].toString());
                                }
                            }
                            mapResult.put(key.toString(), nodeConfig);
                        });
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    // Fallback to single value
                    Map<String, String> config = new java.util.HashMap<>();
                    if (result instanceof Object[]) {
                        Object[] array = (Object[]) result;
                        for (int i = 0; i < array.length - 1; i += 2) {
                            config.put(array[i].toString(), array[i + 1].toString());
                        }
                    }
                    return ClusterValue.ofSingleValue(config);
                }
            });
    }

    /**
     * Echo a message with routing.
     *
     * @param message The message to echo
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the echoed message wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<String>> echo(String message, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Echo, message), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(result.toString());
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, String> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result)
                                    .forEach((key, value) ->
                            mapResult.put(key.toString(), value.toString()));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(result.toString());
                }
            });
    }

    /**
     * Echo a GlideString message with routing.
     *
     * @param message The message to echo
     * @param route The routing configuration for the command
     * @return A CompletableFuture containing the echoed message wrapped in ClusterValue
     */
    public CompletableFuture<ClusterValue<GlideString>> echo(GlideString message, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            Echo, message.toString()), route)
            .thenApply(result -> {
                if (route instanceof glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute) {
                    return ClusterValue.ofSingleValue(GlideString.of(result.toString()));
                } else {
                    // For multi-node routes, expect a map result
                    if (result instanceof Map) {
                        Map<String, GlideString> mapResult = new java.util.HashMap<>();
                            ((Map<?, ?>) result).forEach(
                                    (key, value) ->
                            mapResult.put(key.toString(), GlideString.of(value.toString())));
                        return ClusterValue.ofMultiValue(mapResult);
                    }
                    return ClusterValue.ofSingleValue(GlideString.of(result.toString()));
                }
            });
    }







    /**
     * Flush all functions with flush mode (no routing).
     *
     * @param flushMode The flush mode to use
     * @return A CompletableFuture containing the result
     */
    public CompletableFuture<String> functionFlush(FlushMode flushMode) {
        return super.functionFlush(flushMode.name());
    }


    // Function List methods - proper implementations using BaseClient
    public CompletableFuture<Map<String, Object>[]> functionList(boolean withCode) {
        // Use the existing BaseClient functionList method and adapt the result
        return super.functionList(null).thenApply(result -> {
            // Convert result to expected format
            if (result instanceof Object[]) {
                Object[] objects = (Object[]) result;
                Map<String, Object>[] maps = new Map[objects.length];
                for (int i = 0; i < objects.length; i++) {
                    if (objects[i] instanceof Map) {
                        maps[i] = (Map<String, Object>) objects[i];
                    } else {
                        maps[i] = new java.util.HashMap<>();
                    }
                }
                return maps;
            }
            return new Map[0];
        });
    }

    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode) {
        // Proper implementation converting String keys to GlideString
        return functionList(withCode).thenApply(result -> {
            Map<GlideString, Object>[] binaryMaps = new Map[result.length];
            for (int i = 0; i < result.length; i++) {
                Map<GlideString, Object> binaryMap = new java.util.HashMap<>();
                if (result[i] != null) {
                    for (Map.Entry<String, Object> entry : result[i].entrySet()) {
                        binaryMap.put(GlideString.of(entry.getKey()), entry.getValue());
                    }
                }
                binaryMaps[i] = binaryMap;
            }
            return binaryMaps;
        });
    }

    public CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode) {
        // Use the existing BaseClient functionList method with library name
        return super.functionList(libNamePattern).thenApply(result -> {
            // Convert result to expected format
            if (result instanceof Object[]) {
                Object[] objects = (Object[]) result;
                Map<String, Object>[] maps = new Map[objects.length];
                for (int i = 0; i < objects.length; i++) {
                    if (objects[i] instanceof Map) {
                        maps[i] = (Map<String, Object>) objects[i];
                    } else {
                        maps[i] = new java.util.HashMap<>();
                    }
                }
                return maps;
            }
            return new Map[0];
        });
    }

    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(GlideString libNamePattern, boolean withCode) {
        // Stub implementation - in real implementation would handle binary strings
        return functionList(libNamePattern.toString(), withCode).thenApply(result -> {
            // Convert String keys to GlideString - this is a stub implementation
            return new Map[0];
        });
    }

    public CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(boolean withCode, Route route) {
        // Delegate to non-route version and wrap in ClusterValue
        return functionList(withCode).thenApply(ClusterValue::ofSingleValue);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>[]>> functionListBinary(boolean withCode, Route route) {
        // Delegate to non-route version and wrap in ClusterValue
        return functionListBinary(withCode).thenApply(ClusterValue::ofSingleValue);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(String libNamePattern, boolean withCode, Route route) {
        // Delegate to non-route version and wrap in ClusterValue
        return functionList(libNamePattern, withCode).thenApply(ClusterValue::ofSingleValue);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>[]>> functionListBinary(GlideString libNamePattern, boolean withCode, Route route) {
        // Delegate to non-route version and wrap in ClusterValue
        return functionListBinary(libNamePattern, withCode).thenApply(ClusterValue::ofSingleValue);
    }

    // Function Kill methods - proper implementations using BaseClient
    public CompletableFuture<String> functionKill() {
        // Use the BaseClient functionKill method
        return super.functionKill();
    }

    public CompletableFuture<String> functionKill(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(
            FunctionKill), route)
            .thenApply(result -> result.toString());
    }


    // ===== SCRIPTING AND FUNCTIONS CLUSTER COMMANDS IMPLEMENTATION =====

    @Override
    public CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionStats, new String[0]))
                .thenApply(result -> ClusterValue.of((Map<String, Map<String, Object>>) result));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<GlideString, Map<GlideString, Object>>>> functionStatsBinary() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionStats, new String[0]))
                .thenApply(result -> {
                    // Convert String keys to GlideString keys for the nested maps
                    Map<String, Map<String, Object>> stringResult = (Map<String, Map<String, Object>>) result;
                    Map<GlideString, Map<GlideString, Object>> binaryResult = new java.util.HashMap<>();

                    for (Map.Entry<String, Map<String, Object>> nodeEntry : stringResult.entrySet()) {
                        Map<GlideString, Object> nodeData = new java.util.HashMap<>();
                        for (Map.Entry<String, Object> dataEntry : nodeEntry.getValue().entrySet()) {
                            nodeData.put(GlideString.of(dataEntry.getKey()), dataEntry.getValue());
                        }
                        binaryResult.put(GlideString.of(nodeEntry.getKey()), nodeData);
                    }
                    return ClusterValue.of(binaryResult);
                });
    }

    public CompletableFuture<String> functionRestore(byte[] payload) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionRestore, new String(payload)))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionRestore(byte[] payload, FunctionRestorePolicy policy) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionRestore, policy.toString(), new String(payload)))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionRestore(byte[] payload, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionRestore, new String(payload)), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionRestore(byte[] payload, FunctionRestorePolicy policy, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionRestore, policy.toString(), new String(payload)), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<byte[]> functionDump() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionDump, new String[0]))
            .thenApply(result -> result.toString().getBytes());
    }

    public CompletableFuture<ClusterValue<byte[]>> functionDump(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionDump, new String[0]), route)
            .thenApply(result -> ClusterValue.of(result.toString().getBytes()));
    }

    public CompletableFuture<String> functionDelete(String libName) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionDelete, libName))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionDelete(GlideString libName) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionDelete, libName.toString()))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionDelete(String libName, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionDelete, libName), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionDelete(GlideString libName, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionDelete, libName.toString()), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionFlush() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionFlush, new String[0]))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionFlush(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionFlush, new String[0]), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionFlush(FlushMode mode, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionFlush, mode.toString()), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> functionLoad(String libraryCode, boolean replace) {
        String replaceArg = replace ? "REPLACE" : "";
        if (replace) {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, replaceArg, libraryCode))
                .thenApply(result -> result.toString());
        } else {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, libraryCode))
                .thenApply(result -> result.toString());
        }
    }

    public CompletableFuture<GlideString> functionLoad(GlideString libraryCode, boolean replace) {
        String replaceArg = replace ? "REPLACE" : "";
        if (replace) {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, replaceArg, libraryCode.toString()))
                .thenApply(result -> GlideString.of(result.toString()));
        } else {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, libraryCode.toString()))
                .thenApply(result -> GlideString.of(result.toString()));
        }
    }

    public CompletableFuture<String> functionLoad(String libraryCode, boolean replace, Route route) {
        String replaceArg = replace ? "REPLACE" : "";
        if (replace) {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, replaceArg, libraryCode), route)
                .thenApply(result -> result.toString());
        } else {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, libraryCode), route)
                .thenApply(result -> result.toString());
        }
    }

    public CompletableFuture<GlideString> functionLoad(GlideString libraryCode, boolean replace, Route route) {
        String replaceArg = replace ? "REPLACE" : "";
        if (replace) {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, replaceArg, libraryCode.toString()), route)
                .thenApply(result -> GlideString.of(result.toString()));
        } else {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionLoad, libraryCode.toString()), route)
                .thenApply(result -> GlideString.of(result.toString()));
        }
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionStats, new String[0]), route)
                .thenApply(result -> ClusterValue.of(result));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<GlideString, Map<GlideString, Object>>>> functionStatsBinary(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FunctionStats, new String[0]), route)
                .thenApply(result -> {
                    // Convert String keys to GlideString keys for the nested maps
                    Map<String, Map<String, Object>> stringResult = (Map<String, Map<String, Object>>) result;
                    Map<GlideString, Map<GlideString, Object>> binaryResult = new java.util.HashMap<>();

                    for (Map.Entry<String, Map<String, Object>> nodeEntry : stringResult.entrySet()) {
                        Map<GlideString, Object> nodeData = new java.util.HashMap<>();
                        for (Map.Entry<String, Object> dataEntry : nodeEntry.getValue().entrySet()) {
                            nodeData.put(GlideString.of(dataEntry.getKey()), dataEntry.getValue());
                        }
                        binaryResult.put(GlideString.of(nodeEntry.getKey()), nodeData);
                    }
                    return ClusterValue.of(binaryResult);
                });
    }

    // FCALL methods
    public CompletableFuture<Object> fcall(String function) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, new String[]{function}));
    }

    public CompletableFuture<Object> fcall(GlideString function) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, new String[]{function.toString()}));
    }

    public CompletableFuture<ClusterValue<Object>> fcall(String function, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, new String[]{function}), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcall(GlideString function, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, new String[]{function.toString()}), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<Object> fcall(String function, String[] arguments) {
        String[] args = new String[arguments.length + 1];
        args[0] = function;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, args));
    }

    public CompletableFuture<Object> fcall(GlideString function, GlideString[] arguments) {
        String[] args = new String[arguments.length + 1];
        args[0] = function.toString();
        for (int i = 0; i < arguments.length; i++) {
            args[i + 1] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, args));
    }

    public CompletableFuture<ClusterValue<Object>> fcall(String function, String[] arguments, Route route) {
        String[] args = new String[arguments.length + 1];
        args[0] = function;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, args), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcall(GlideString function, GlideString[] arguments, Route route) {
        String[] args = new String[arguments.length + 1];
        args[0] = function.toString();
        for (int i = 0; i < arguments.length; i++) {
            args[i + 1] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, args), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    // FCALL_RO methods
    public CompletableFuture<Object> fcallReadOnly(String function) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, new String[]{function}));
    }

    public CompletableFuture<Object> fcallReadOnly(GlideString function) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, new String[]{function.toString()}));
    }

    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(String function, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, new String[]{function}), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(GlideString function, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, new String[]{function.toString()}), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<Object> fcallReadOnly(String function, String[] arguments) {
        String[] args = new String[arguments.length + 1];
        args[0] = function;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, args));
    }

    public CompletableFuture<Object> fcallReadOnly(GlideString function, GlideString[] arguments) {
        String[] args = new String[arguments.length + 1];
        args[0] = function.toString();
        for (int i = 0; i < arguments.length; i++) {
            args[i + 1] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, args));
    }

    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(String function, String[] arguments, Route route) {
        String[] args = new String[arguments.length + 1];
        args[0] = function;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, args), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(GlideString function, GlideString[] arguments, Route route) {
        String[] args = new String[arguments.length + 1];
        args[0] = function.toString();
        for (int i = 0; i < arguments.length; i++) {
            args[i + 1] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, args), route)
            .thenApply(result -> ClusterValue.of(result));
    }

    // FCALL methods with keys and arguments (from
    // ScriptingAndFunctionsBaseCommands)
    public CompletableFuture<Object> fcall(String function, String[] keys, String[] arguments) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function;
        commandArgs[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, commandArgs, 2, keys.length);
        System.arraycopy(arguments, 0, commandArgs, 2 + keys.length, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, commandArgs))
                .thenApply(result -> result);
    }

    public CompletableFuture<Object> fcall(GlideString function, GlideString[] keys, GlideString[] arguments) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function.toString();
        commandArgs[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            commandArgs[2 + i] = keys[i].toString();
        }
        for (int i = 0; i < arguments.length; i++) {
            commandArgs[2 + keys.length + i] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, commandArgs))
                .thenApply(result -> result);
    }

    public CompletableFuture<Object> fcallReadOnly(String function, String[] keys, String[] arguments) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function;
        commandArgs[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, commandArgs, 2, keys.length);
        System.arraycopy(arguments, 0, commandArgs, 2 + keys.length, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, commandArgs))
                .thenApply(result -> result);
    }

    public CompletableFuture<Object> fcallReadOnly(GlideString function, GlideString[] keys, GlideString[] arguments) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function.toString();
        commandArgs[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            commandArgs[2 + i] = keys[i].toString();
        }
        for (int i = 0; i < arguments.length; i++) {
            commandArgs[2 + keys.length + i] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, commandArgs))
                .thenApply(result -> result);
    }

    // FCALL methods with keys, arguments, and Route (cluster-specific)
    public CompletableFuture<ClusterValue<Object>> fcall(String function, String[] keys, String[] arguments,
            Route route) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function;
        commandArgs[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, commandArgs, 2, keys.length);
        System.arraycopy(arguments, 0, commandArgs, 2 + keys.length, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, commandArgs), route)
                .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcall(GlideString function, GlideString[] keys,
            GlideString[] arguments, Route route) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function.toString();
        commandArgs[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            commandArgs[2 + i] = keys[i].toString();
        }
        for (int i = 0; i < arguments.length; i++) {
            commandArgs[2 + keys.length + i] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCall, commandArgs), route)
                .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(String function, String[] keys, String[] arguments,
            Route route) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function;
        commandArgs[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, commandArgs, 2, keys.length);
        System.arraycopy(arguments, 0, commandArgs, 2 + keys.length, arguments.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, commandArgs), route)
                .thenApply(result -> ClusterValue.of(result));
    }

    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(GlideString function, GlideString[] keys,
            GlideString[] arguments, Route route) {
        String[] commandArgs = new String[2 + keys.length + arguments.length];
        commandArgs[0] = function.toString();
        commandArgs[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            commandArgs[2 + i] = keys[i].toString();
        }
        for (int i = 0; i < arguments.length; i++) {
            commandArgs[2 + keys.length + i] = arguments[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FCallReadOnly, commandArgs), route)
                .thenApply(result -> ClusterValue.of(result));
    }

    // Script methods
    public CompletableFuture<Boolean[]> scriptExists(String[] sha1s) {
        return super.scriptExists(sha1s);
    }

    public CompletableFuture<Boolean[]> scriptExists(GlideString[] sha1s) {
        // Convert GlideString array to String array
        String[] stringHashes = new String[sha1s.length];
        for (int i = 0; i < sha1s.length; i++) {
            stringHashes[i] = sha1s[i].toString();
        }
        return super.scriptExists(stringHashes);
    }

    public CompletableFuture<Boolean[]> scriptExists(String[] sha1s, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ScriptExists, sha1s), route)
            .thenApply(result -> {
                Boolean[] booleanResult = new Boolean[sha1s.length];
                if (result instanceof Object[]) {
                    Object[] array = (Object[]) result;
                    for (int i = 0; i < array.length && i < booleanResult.length; i++) {
                        booleanResult[i] = "1".equals(array[i].toString());
                    }
                } else {
                    for (int i = 0; i < booleanResult.length; i++) {
                        booleanResult[i] = false;
                    }
                }
                return booleanResult;
            });
    }

    public CompletableFuture<Boolean[]> scriptExists(GlideString[] sha1s, Route route) {
        // Convert GlideString array to String array
        String[] stringHashes = new String[sha1s.length];
        for (int i = 0; i < sha1s.length; i++) {
            stringHashes[i] = sha1s[i].toString();
        }
        return scriptExists(stringHashes, route);
    }

    public CompletableFuture<String> scriptFlush() {
        return super.scriptFlush();
    }

    public CompletableFuture<String> scriptFlush(FlushMode flushMode) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ScriptFlush, new String[]{flushMode.toString()}))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> scriptFlush(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ScriptFlush), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> scriptFlush(FlushMode flushMode, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ScriptFlush, new String[]{flushMode.toString()}), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<Object> invokeScript(Script script, Route route) {
        // Use EVALSHA if hash exists, otherwise use EVAL
        if (script.getHash() != null) {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(EvalSha, new String[]{script.getHash(), "0"}), route);
        } else {
            return client.executeCommand(new io.valkey.glide.core.commands.Command(Eval, new String[]{script.getCode(), "0"}), route);
        }
    }

    public CompletableFuture<Object> invokeScript(Script script, ScriptArgOptions options, Route route) {
        List<String> args = new ArrayList<>();
        if (script.getHash() != null) {
            args.add(script.getHash());
        } else {
            args.add(script.getCode());
        }
        args.add("0"); // No keys in ScriptArgOptions
        if (options.getArgs() != null) {
            args.addAll(options.getArgs());
        }

        String command = script.getHash() != null ? EvalSha : Eval;
        return client.executeCommand(new io.valkey.glide.core.commands.Command(command, args.toArray(new String[0])), route);
    }

    public CompletableFuture<Object> invokeScript(Script script, ScriptArgOptionsGlideString options, Route route) {
        List<String> args = new ArrayList<>();
        if (script.getHash() != null) {
            args.add(script.getHash());
        } else {
            args.add(script.getCode());
        }
        args.add("0"); // No keys in ScriptArgOptionsGlideString
        if (options.getArgs() != null) {
            for (GlideString arg : options.getArgs()) {
                args.add(arg.toString());
            }
        }

        String command = script.getHash() != null ? EvalSha : Eval;
        return client.executeCommand(new io.valkey.glide.core.commands.Command(command, args.toArray(new String[0])), route);
    }

    public CompletableFuture<String> scriptKill(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ScriptKill), route)
            .thenApply(result -> result.toString());
    }

    // ServerManagementClusterCommands implementation

    @Override
    public CompletableFuture<String[]> time() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Time))
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

    public CompletableFuture<ClusterValue<String[]>> time(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Time), route)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] time = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        time[i] = objects[i].toString();
                    }
                    return ClusterValue.of(time);
                }
                return ClusterValue.of(new String[0]);
            });
    }

    @Override
    public CompletableFuture<Long> lastsave() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LastSave))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    public CompletableFuture<ClusterValue<Long>> lastsave(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LastSave), route)
            .thenApply(result -> ClusterValue.of(Long.parseLong(result.toString())));
    }

    @Override
    public CompletableFuture<Long> dbsize() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(DBSize))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    public CompletableFuture<Long> dbsize(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(DBSize), route)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    @Override
    public CompletableFuture<String> configRewrite() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ConfigRewrite))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> configRewrite(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ConfigRewrite), route)
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> configResetStat() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ConfigResetStat))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> configResetStat(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ConfigResetStat), route)
            .thenApply(result -> result.toString());
    }


    @Override
    public CompletableFuture<String> configSet(Map<String, String> parameters) {
        String[] args = new String[parameters.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ConfigSet, args))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> configSet(Map<String, String> parameters, Route route) {
        String[] args = new String[parameters.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ConfigSet, args), route)
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> flushall() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushAll))
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> flushall(FlushMode mode) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushAll, mode.name()))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> flushall(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushAll), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> flushall(FlushMode mode, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushAll, mode.name()), route)
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> flushdb() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushDB))
            .thenApply(result -> result.toString());
    }

    @Override
    public CompletableFuture<String> flushdb(FlushMode mode) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushDB, mode.name()))
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> flushdb(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushDB), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<String> flushdb(FlushMode mode, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(FlushDB, mode.name()), route)
            .thenApply(result -> result.toString());
    }

    public CompletableFuture<ClusterValue<String>> info(Section[] sections, Route route) {
        String[] sectionNames = new String[sections.length];
        for (int i = 0; i < sections.length; i++) {
            sectionNames[i] = sections[i].name().toLowerCase();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Info, sectionNames), route)
            .thenApply(result -> ClusterValue.of(result.toString()));
    }

    public CompletableFuture<ClusterValue<String>> info(Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Info), route)
            .thenApply(result -> ClusterValue.of(result.toString()));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info() {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Info))
            .thenApply(result -> ClusterValue.of(result.toString()));
    }

    // ZDIFF family methods
    @Override
    public CompletableFuture<String[]> zdiff(String[] keys) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZDiff, keys))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<GlideString[]> zdiff(GlideString[] keys) {
        String[] stringKeys = Arrays.stream(keys).map(GlideString::toString).toArray(String[]::new);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZDiff, stringKeys))
            .thenApply(result -> {
                String[] stringResult = (String[]) result;
                return Arrays.stream(stringResult).map(GlideString::of).toArray(GlideString[]::new);
            });
    }

    @Override
    public CompletableFuture<Map<String, Double>> zdiffWithScores(String[] keys) {
        String[] args = Arrays.copyOf(keys, keys.length + 1);
        args[keys.length] = "WITHSCORES";
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZDiff, args))
            .thenApply(result -> (Map<String, Double>) result);
    }

    public CompletableFuture<Map<GlideString, Double>> zdiffWithScores(GlideString[] keys) {
        String[] stringKeys = Arrays.stream(keys).map(GlideString::toString).toArray(String[]::new);
        String[] args = Arrays.copyOf(stringKeys, stringKeys.length + 1);
        args[stringKeys.length] = "WITHSCORES";
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZDiff, args))
            .thenApply(result -> {
                Map<String, Double> stringResult = (Map<String, Double>) result;
                return stringResult.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        entry -> GlideString.of(entry.getKey()),
                        Map.Entry::getValue
                    ));
            });
    }

    @Override
    public CompletableFuture<Long> zdiffstore(String destination, String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZDiffStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zdiffstore(GlideString destination, GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination.toString();
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZDiffStore, args))
            .thenApply(result -> (Long) result);
    }

    // ZUNION family methods - basic implementations for compilation
    @Override
    public CompletableFuture<String[]> zunion(KeyArray keys) {
        String[] args = keys.toArgs();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnion, args))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<GlideString[]> zunion(KeyArrayBinary keys) {
        GlideString[] args = keys.toArgs();
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnion, stringArgs))
            .thenApply(result -> ArrayTransformUtils.toGlideStringArray((String[]) result));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zunionWithScores(KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, aggregateArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnion, args))
            .thenApply(result -> (Map<String, Double>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zunionWithScores(KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate) {
        GlideString[] rawKeysArgs = keysOrWeightedKeys.toArgs();
        String[] keysArgs = new String[rawKeysArgs.length];
        for (int i = 0; i < rawKeysArgs.length; i++) {
            keysArgs[i] = rawKeysArgs[i].toString();
        }
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, aggregateArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnion, args))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zunionWithScores(KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnion, args))
            .thenApply(result -> (Map<String, Double>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zunionWithScores(KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] rawKeysArgs = keysOrWeightedKeys.toArgs();
        String[] keysArgs = new String[rawKeysArgs.length];
        for (int i = 0; i < rawKeysArgs.length; i++) {
            keysArgs[i] = rawKeysArgs[i].toString();
        }
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnion, args))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Long> zunionstore(String destination, KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination}, keysArgs, aggregateArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zunionstore(GlideString destination, KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination.toString()}, keysArgs, aggregateArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zunionstore(String destination, KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination}, keysArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zunionstore(GlideString destination, KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination.toString()}, keysArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zunionstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 1];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, finalArgs))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zunionstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Route route) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 1];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, finalArgs), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zunionstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 3];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        finalArgs[args.length + 1] = "AGGREGATE";
        finalArgs[args.length + 2] = aggregate.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, finalArgs))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zunionstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate, Route route) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 3];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        finalArgs[args.length + 1] = "AGGREGATE";
        finalArgs[args.length + 2] = aggregate.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZUnionStore, finalArgs), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ZINTER family methods - basic implementations for compilation
    @Override
    public CompletableFuture<String[]> zinter(KeyArray keys) {
        String[] args = keys.toArgs();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInter, args))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<GlideString[]> zinter(KeyArrayBinary keys) {
        GlideString[] args = keys.toArgs();
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInter, stringArgs))
            .thenApply(result -> ArrayTransformUtils.toGlideStringArray((String[]) result));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zinterWithScores(KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInter, args))
            .thenApply(result -> (Map<String, Double>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zinterWithScores(KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] rawKeysArgs = keysOrWeightedKeys.toArgs();
        String[] keysArgs = new String[rawKeysArgs.length];
        for (int i = 0; i < rawKeysArgs.length; i++) {
            keysArgs[i] = rawKeysArgs[i].toString();
        }
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInter, args))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zinterWithScores(KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, aggregateArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInter, args))
            .thenApply(result -> (Map<String, Double>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zinterWithScores(KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate) {
        GlideString[] rawKeysArgs = keysOrWeightedKeys.toArgs();
        String[] keysArgs = new String[rawKeysArgs.length];
        for (int i = 0; i < rawKeysArgs.length; i++) {
            keysArgs[i] = rawKeysArgs[i].toString();
        }
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(keysArgs, aggregateArgs, new String[]{"WITHSCORES"});
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInter, args))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Long> zinterstore(String destination, KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination}, keysArgs, aggregateArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zinterstore(GlideString destination, KeysOrWeightedKeys keysOrWeightedKeys, Aggregate aggregate) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] aggregateArgs = aggregate.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination.toString()}, keysArgs, aggregateArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, args))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zinterstore(String destination, KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination}, keysArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zinterstore(GlideString destination, KeysOrWeightedKeys keysOrWeightedKeys) {
        String[] keysArgs = keysOrWeightedKeys.toArgs();
        String[] args = ArrayTransformUtils.concatenateArrays(new String[]{destination.toString()}, keysArgs);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zinterstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 1];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, finalArgs))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zinterstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Route route) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 1];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, finalArgs), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zinterstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 3];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        finalArgs[args.length + 1] = "AGGREGATE";
        finalArgs[args.length + 2] = aggregate.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, finalArgs))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zinterstore(GlideString destination, KeysOrWeightedKeysBinary keysOrWeightedKeys, Aggregate aggregate, Route route) {
        GlideString[] rawArgs = keysOrWeightedKeys.toArgs();
        String[] args = new String[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = rawArgs[i].toString();
        }
        String[] finalArgs = new String[args.length + 3];
        finalArgs[0] = destination.toString();
        System.arraycopy(args, 0, finalArgs, 1, args.length);
        finalArgs[args.length + 1] = "AGGREGATE";
        finalArgs[args.length + 2] = aggregate.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterStore, finalArgs), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zintercard(String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterCard, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zintercard(GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterCard, args))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zintercard(String[] keys, long limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[args.length - 2] = "LIMIT";
        args[args.length - 1] = String.valueOf(limit);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterCard, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> zintercard(GlideString[] keys, long limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[args.length - 2] = "LIMIT";
        args[args.length - 1] = String.valueOf(limit);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZInterCard, args))
            .thenApply(result -> (Long) result);
    }

    // ZRANGESTORE family methods - basic implementations for compilation
    @Override
    public CompletableFuture<Long> zrangestore(String destination, String source, RangeQuery rangeQuery, boolean reverse) {
        String[] args = new String[5];
        args[0] = destination;
        args[1] = source;
        args[2] = rangeQuery.getStart();
        args[3] = rangeQuery.getEnd();
        if (reverse) {
            args[4] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRangeStore, reverse ? args : Arrays.copyOf(args, 4)))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zrangestore(GlideString destination, GlideString source, RangeQuery rangeQuery, boolean reverse) {
        String[] args = new String[5];
        args[0] = destination.toString();
        args[1] = source.toString();
        args[2] = rangeQuery.getStart();
        args[3] = rangeQuery.getEnd();
        if (reverse) {
            args[4] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRangeStore, reverse ? args : Arrays.copyOf(args, 4)))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zrangestore(String destination, String source, RangeQuery rangeQuery) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRangeStore, destination, source, rangeQuery.getStart(), rangeQuery.getEnd()))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zrangestore(GlideString destination, GlideString source, RangeQuery rangeQuery) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRangeStore, destination.toString(), source.toString(), rangeQuery.getStart(), rangeQuery.getEnd()))
            .thenApply(result -> (Long) result);
    }

    // ZINCRBY methods
    @Override
    public CompletableFuture<Double> zincrby(String key, double increment, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZIncrBy, key, String.valueOf(increment), member))
            .thenApply(result -> Double.parseDouble(result.toString()));
    }

    @Override
    public CompletableFuture<Double> zincrby(GlideString key, double increment, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZIncrBy, key.toString(), String.valueOf(increment), member.toString()))
            .thenApply(result -> Double.parseDouble(result.toString()));
    }

    // Additional missing SortedSetBaseCommands methods - basic implementations for compilation
    @Override
    public CompletableFuture<String> zrandmember(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key))
            .thenApply(result -> result == null ? null : result.toString());
    }

    public CompletableFuture<ClusterValue<String>> zrandmember(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key), route)
            .thenApply(result -> ClusterValue.of(result == null ? null : result.toString()));
    }

    public CompletableFuture<GlideString> zrandmember(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key.toString()))
            .thenApply(result -> result == null ? null : GlideString.of((byte[]) result));
    }

    public CompletableFuture<ClusterValue<GlideString>> zrandmember(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key.toString()), route)
            .thenApply(result -> ClusterValue.of(result == null ? null : GlideString.of((byte[]) result)));
    }

    public CompletableFuture<String[]> zrandmemberWithCount(String key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key, String.valueOf(count)))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<ClusterValue<String[]>> zrandmemberWithCount(String key, long count, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key, String.valueOf(count)), route)
            .thenApply(result -> ClusterValue.of((String[]) result));
    }

    public CompletableFuture<GlideString[]> zrandmemberWithCount(GlideString key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key.toString(), String.valueOf(count)))
            .thenApply(result -> ArrayTransformUtils.toGlideStringArray((byte[][]) result));
    }

    public CompletableFuture<ClusterValue<GlideString[]>> zrandmemberWithCount(GlideString key, long count, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key.toString(), String.valueOf(count)), route)
            .thenApply(result -> ClusterValue.of(ArrayTransformUtils.toGlideStringArray((byte[][]) result)));
    }

    public CompletableFuture<Object[][]> zrandmemberWithCountWithScores(String key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key, String.valueOf(count), "WITHSCORES"))
            .thenApply(result -> (Object[][]) result);
    }

    public CompletableFuture<ClusterValue<Object[][]>> zrandmemberWithCountWithScores(String key, long count, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key, String.valueOf(count), "WITHSCORES"), route)
            .thenApply(result -> ClusterValue.of((Object[][]) result));
    }

    @Override
    public CompletableFuture<Object[][]> zrandmemberWithCountWithScores(GlideString key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key.toString(), String.valueOf(count), "WITHSCORES"))
            .thenApply(result -> (Object[][]) result);
    }

    public CompletableFuture<ClusterValue<Object[][]>> zrandmemberWithCountWithScores(GlideString key, long count, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRandMember, key.toString(), String.valueOf(count), "WITHSCORES"), route)
            .thenApply(result -> ClusterValue.of((Object[][]) result));
    }

    // ============= ZMPOP METHODS =============
    @Override
    public CompletableFuture<Map<String, Object>> zmpop(String[] keys, ScoreFilter modifier) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> zmpop(String[] keys, ScoreFilter modifier, Route route) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    public CompletableFuture<Map<GlideString, Object>> zmpop(GlideString[] keys, ScoreFilter modifier) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args))
            .thenApply(result -> (Map<GlideString, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>>> zmpop(GlideString[] keys, ScoreFilter modifier, Route route) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<GlideString, Object>) result));
    }

    public CompletableFuture<Map<String, Object>> zmpop(String[] keys, ScoreFilter modifier, long count) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> zmpop(String[] keys, ScoreFilter modifier, long count, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    public CompletableFuture<Map<GlideString, Object>> zmpop(GlideString[] keys, ScoreFilter modifier, long count) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args))
            .thenApply(result -> (Map<GlideString, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>>> zmpop(GlideString[] keys, ScoreFilter modifier, long count, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<GlideString, Object>) result));
    }

    // ============= BZMPOP METHODS =============
    @Override
    public CompletableFuture<Map<String, Object>> bzmpop(String[] keys, ScoreFilter modifier, double timeout) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> bzmpop(String[] keys, ScoreFilter modifier, double timeout, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    public CompletableFuture<Map<GlideString, Object>> bzmpop(GlideString[] keys, ScoreFilter modifier, double timeout) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args))
            .thenApply(result -> (Map<GlideString, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>>> bzmpop(GlideString[] keys, ScoreFilter modifier, double timeout, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<GlideString, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> bzmpop(String[] keys, ScoreFilter modifier, double timeout, long count) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        args[keys.length + 3] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> bzmpop(String[] keys, ScoreFilter modifier, double timeout, long count, Route route) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        args[keys.length + 3] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    public CompletableFuture<Map<GlideString, Object>> bzmpop(GlideString[] keys, ScoreFilter modifier, double timeout, long count) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        args[keys.length + 3] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args))
            .thenApply(result -> (Map<GlideString, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>>> bzmpop(GlideString[] keys, ScoreFilter modifier, double timeout, long count, Route route) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = modifier.toString();
        args[keys.length + 2] = String.valueOf(timeout);
        args[keys.length + 3] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZMPop, args), route)
            .thenApply(result -> ClusterValue.of((Map<GlideString, Object>) result));
    }

    // ============= ZLEXCOUNT METHODS =============
    @Override
    public CompletableFuture<Long> zlexcount(String key, LexRange minLex, LexRange maxLex) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZLexCount, key, minLex.toString(), maxLex.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zlexcount(String key, LexRange minLex, LexRange maxLex, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZLexCount, key, minLex.toString(), maxLex.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zlexcount(GlideString key, LexRange minLex, LexRange maxLex) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZLexCount, key.toString(), minLex.toString(), maxLex.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zlexcount(GlideString key, LexRange minLex, LexRange maxLex, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZLexCount, key.toString(), minLex.toString(), maxLex.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZREMRANGEBYSCORE METHODS =============
    @Override
    public CompletableFuture<Long> zremrangebyscore(String key, ScoreRange minScore, ScoreRange maxScore) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByScore, key, minScore.toArgs(), maxScore.toArgs()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zremrangebyscore(String key, ScoreRange minScore, ScoreRange maxScore, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByScore, key, minScore.toArgs(), maxScore.toArgs()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zremrangebyscore(GlideString key, ScoreRange minScore, ScoreRange maxScore) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByScore, key.toString(), minScore.toArgs(), maxScore.toArgs()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zremrangebyscore(GlideString key, ScoreRange minScore, ScoreRange maxScore, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByScore, key.toString(), minScore.toArgs(), maxScore.toArgs()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZREMRANGEBYLEX METHODS =============
    @Override
    public CompletableFuture<Long> zremrangebylex(String key, LexRange minLex, LexRange maxLex) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByLex, key, minLex.toString(), maxLex.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zremrangebylex(String key, LexRange minLex, LexRange maxLex, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByLex, key, minLex.toString(), maxLex.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zremrangebylex(GlideString key, LexRange minLex, LexRange maxLex) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByLex, key.toString(), minLex.toString(), maxLex.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zremrangebylex(GlideString key, LexRange minLex, LexRange maxLex, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByLex, key.toString(), minLex.toString(), maxLex.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZREMRANGEBYRANK METHODS =============
    @Override
    public CompletableFuture<Long> zremrangebyrank(String key, long start, long end) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByRank, key, String.valueOf(start), String.valueOf(end)))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zremrangebyrank(String key, long start, long end, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByRank, key, String.valueOf(start), String.valueOf(end)), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zremrangebyrank(GlideString key, long start, long end) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByRank, key.toString(), String.valueOf(start), String.valueOf(end)))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zremrangebyrank(GlideString key, long start, long end, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRemRangeByRank, key.toString(), String.valueOf(start), String.valueOf(end)), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZCOUNT METHODS =============
    @Override
    public CompletableFuture<Long> zcount(String key, ScoreRange minScore, ScoreRange maxScore) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZCount, key, minScore.toArgs(), maxScore.toArgs()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zcount(String key, ScoreRange minScore, ScoreRange maxScore, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZCount, key, minScore.toArgs(), maxScore.toArgs()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zcount(GlideString key, ScoreRange minScore, ScoreRange maxScore) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZCount, key.toString(), minScore.toArgs(), maxScore.toArgs()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zcount(GlideString key, ScoreRange minScore, ScoreRange maxScore, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZCount, key.toString(), minScore.toArgs(), maxScore.toArgs()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZMSCORE METHODS =============
    @Override
    public CompletableFuture<Double[]> zmscore(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMScore, args))
            .thenApply(result -> (Double[]) result);
    }

    public CompletableFuture<ClusterValue<Double[]>> zmscore(String key, String[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMScore, args), route)
            .thenApply(result -> ClusterValue.of((Double[]) result));
    }

    public CompletableFuture<Double[]> zmscore(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMScore, args))
            .thenApply(result -> (Double[]) result);
    }

    public CompletableFuture<ClusterValue<Double[]>> zmscore(GlideString key, GlideString[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZMScore, args), route)
            .thenApply(result -> ClusterValue.of((Double[]) result));
    }

    // ============= ZREVRANKWITHSCORE METHODS =============
    @Override
    public CompletableFuture<Object[]> zrevrankWithScore(String key, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key, member, "WITHSCORE"))
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<ClusterValue<Object[]>> zrevrankWithScore(String key, String member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key, member, "WITHSCORE"), route)
            .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    @Override
    public CompletableFuture<Object[]> zrevrankWithScore(GlideString key, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key.toString(), member.toString(), "WITHSCORE"))
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<ClusterValue<Object[]>> zrevrankWithScore(GlideString key, GlideString member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key.toString(), member.toString(), "WITHSCORE"), route)
            .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    // ============= ZREVRANK METHODS =============
    @Override
    public CompletableFuture<Long> zrevrank(String key, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key, member))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zrevrank(String key, String member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key, member), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zrevrank(GlideString key, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key.toString(), member.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zrevrank(GlideString key, GlideString member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRevRank, key.toString(), member.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZRANKWITHSCORE METHODS =============
    @Override
    public CompletableFuture<Object[]> zrankWithScore(String key, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key, member, "WITHSCORE"))
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<ClusterValue<Object[]>> zrankWithScore(String key, String member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key, member, "WITHSCORE"), route)
            .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    @Override
    public CompletableFuture<Object[]> zrankWithScore(GlideString key, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key.toString(), member.toString(), "WITHSCORE"))
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<ClusterValue<Object[]>> zrankWithScore(GlideString key, GlideString member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key.toString(), member.toString(), "WITHSCORE"), route)
            .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    // ============= ZRANK METHODS =============
    @Override
    public CompletableFuture<Long> zrank(String key, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key, member))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zrank(String key, String member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key, member), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> zrank(GlideString key, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key.toString(), member.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> zrank(GlideString key, GlideString member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRank, key.toString(), member.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= ZRANGEWITHSCORES METHODS =============
    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery, boolean reverse) {
        String[] args = new String[5];
        args[0] = key;
        args[1] = rangeQuery.getStart();
        args[2] = rangeQuery.getEnd();
        args[3] = "WITHSCORES";
        if (reverse) {
            args[4] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, reverse ? args : Arrays.copyOf(args, 4)))
            .thenApply(result -> (Map<String, Double>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Double>>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery, boolean reverse, Route route) {
        String[] args = new String[5];
        args[0] = key;
        args[1] = rangeQuery.getStart();
        args[2] = rangeQuery.getEnd();
        args[3] = "WITHSCORES";
        if (reverse) {
            args[4] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, reverse ? args : Arrays.copyOf(args, 4)), route)
            .thenApply(result -> ClusterValue.of((Map<String, Double>) result));
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zrangeWithScores(GlideString key, ScoredRangeQuery rangeQuery, boolean reverse) {
        String[] args = new String[5];
        args[0] = key.toString();
        args[1] = rangeQuery.getStart();
        args[2] = rangeQuery.getEnd();
        args[3] = "WITHSCORES";
        if (reverse) {
            args[4] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, reverse ? args : Arrays.copyOf(args, 4)))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Double>>> zrangeWithScores(GlideString key, ScoredRangeQuery rangeQuery, boolean reverse, Route route) {
        String[] args = new String[5];
        args[0] = key.toString();
        args[1] = rangeQuery.getStart();
        args[2] = rangeQuery.getEnd();
        args[3] = "WITHSCORES";
        if (reverse) {
            args[4] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, reverse ? args : Arrays.copyOf(args, 4)), route)
            .thenApply(result -> ClusterValue.of((Map<GlideString, Double>) result));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, key, rangeQuery.getStart(), rangeQuery.getEnd(), "WITHSCORES"))
            .thenApply(result -> (Map<String, Double>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Double>>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, key, rangeQuery.getStart(), rangeQuery.getEnd(), "WITHSCORES"), route)
            .thenApply(result -> ClusterValue.of((Map<String, Double>) result));
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zrangeWithScores(GlideString key, ScoredRangeQuery rangeQuery) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, key.toString(), rangeQuery.getStart(), rangeQuery.getEnd(), "WITHSCORES"))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Double>>> zrangeWithScores(GlideString key, ScoredRangeQuery rangeQuery, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, key.toString(), rangeQuery.getStart(), rangeQuery.getEnd(), "WITHSCORES"), route)
            .thenApply(result -> ClusterValue.of((Map<GlideString, Double>) result));
    }

    @Override
    public CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery, boolean reverse) {
        String[] args = new String[4];
        args[0] = key;
        args[1] = rangeQuery.getStart();
        args[2] = rangeQuery.getEnd();
        if (reverse) {
            args[3] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, reverse ? args : Arrays.copyOf(args, 3)))
            .thenApply(result -> (String[]) result);
    }

    @Override
    public CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, key, rangeQuery.getStart(), rangeQuery.getEnd()))
            .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<GlideString[]> zrange(GlideString key, RangeQuery rangeQuery, boolean reverse) {
        String[] args = new String[4];
        args[0] = key.toString();
        args[1] = rangeQuery.getStart();
        args[2] = rangeQuery.getEnd();
        if (reverse) {
            args[3] = "REV";
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, reverse ? args : Arrays.copyOf(args, 3)))
            .thenApply(result -> ArrayTransformUtils.toGlideStringArray((String[]) result));
    }

    public CompletableFuture<GlideString[]> zrange(GlideString key, RangeQuery rangeQuery) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZRange, key.toString(), rangeQuery.getStart(), rangeQuery.getEnd()))
            .thenApply(result -> ArrayTransformUtils.toGlideStringArray((String[]) result));
    }

    @Override
    public CompletableFuture<Object[]> bzpopmax(String[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[keys.length] = Double.toString(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZPopMax, args))
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<Object[]> bzpopmax(GlideString[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        for (int i = 0; i < keys.length; i++) {
            args[i] = keys[i].toString();
        }
        args[keys.length] = Double.toString(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZPopMax, args))
            .thenApply(result -> (Object[]) result);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(String key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMax, key, Long.toString(count)))
            .thenApply(result -> (Map<String, Double>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zpopmax(GlideString key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMax, key.toString(), Long.toString(count)))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMax, key))
            .thenApply(result -> (Map<String, Double>) result);
    }

    public CompletableFuture<Map<GlideString, Double>> zpopmax(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMax, key.toString()))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Object[]> bzpopmin(String[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[keys.length] = Double.toString(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZPopMin, args))
            .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<Object[]> bzpopmin(GlideString[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        for (int i = 0; i < keys.length; i++) {
            args[i] = keys[i].toString();
        }
        args[keys.length] = Double.toString(timeout);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BZPopMin, args))
            .thenApply(result -> (Object[]) result);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmin(String key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMin, key, Long.toString(count)))
            .thenApply(result -> (Map<String, Double>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, Double>> zpopmin(GlideString key, long count) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMin, key.toString(), Long.toString(count)))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmin(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMin, key))
            .thenApply(result -> (Map<String, Double>) result);
    }

    public CompletableFuture<Map<GlideString, Double>> zpopmin(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZPopMin, key.toString()))
            .thenApply(result -> (Map<GlideString, Double>) result);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(String key, String member, double increment, ZAddOptions options) {
        String[] optionArgs = options.toArgs();
        String[] args = new String[optionArgs.length + 5];
        args[0] = key;
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        args[optionArgs.length + 1] = "INCR";
        args[optionArgs.length + 2] = Double.toString(increment);
        args[optionArgs.length + 3] = member;
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args))
            .thenApply(result -> (Double) result);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(GlideString key, GlideString member, double increment, ZAddOptions options) {
        String[] optionArgs = options.toArgs();
        String[] args = new String[optionArgs.length + 5];
        args[0] = key.toString();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        args[optionArgs.length + 1] = "INCR";
        args[optionArgs.length + 2] = Double.toString(increment);
        args[optionArgs.length + 3] = member.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args))
            .thenApply(result -> (Double) result);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(String key, String member, double increment) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, key, "INCR", Double.toString(increment), member))
            .thenApply(result -> (Double) result);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(GlideString key, GlideString member, double increment) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, key.toString(), "INCR", Double.toString(increment), member.toString()))
            .thenApply(result -> (Double) result);
    }

    @Override
    public CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap, ZAddOptions options, boolean changed) {
        String[] optionArgs = options.toArgs();
        List<String> args = new ArrayList<>();
        args.add(key);
        args.addAll(Arrays.asList(optionArgs));
        if (changed) {
            args.add("CH");
        }
        for (Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(GlideString key, Map<GlideString, Double> membersScoresMap, ZAddOptions options, boolean changed) {
        String[] optionArgs = options.toArgs();
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.addAll(Arrays.asList(optionArgs));
        if (changed) {
            args.add("CH");
        }
        for (Map.Entry<GlideString, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey().toString());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap, ZAddOptions options) {
        String[] optionArgs = options.toArgs();
        List<String> args = new ArrayList<>();
        args.add(key);
        args.addAll(Arrays.asList(optionArgs));
        for (Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(GlideString key, Map<GlideString, Double> membersScoresMap, ZAddOptions options) {
        String[] optionArgs = options.toArgs();
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.addAll(Arrays.asList(optionArgs));
        for (Map.Entry<GlideString, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey().toString());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap, boolean changed) {
        List<String> args = new ArrayList<>();
        args.add(key);
        if (changed) {
            args.add("CH");
        }
        for (Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(GlideString key, Map<GlideString, Double> membersScoresMap, boolean changed) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        if (changed) {
            args.add("CH");
        }
        for (Map.Entry<GlideString, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey().toString());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap) {
        List<String> args = new ArrayList<>();
        args.add(key);
        for (Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> zadd(GlideString key, Map<GlideString, Double> membersScoresMap) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        for (Map.Entry<GlideString, Double> entry : membersScoresMap.entrySet()) {
            args.add(Double.toString(entry.getValue()));
            args.add(entry.getKey().toString());
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ZAdd, args.toArray(new String[0])))
            .thenApply(result -> (Long) result);
    }

    // StringBaseCommands increment/decrement methods

    @Override
    public CompletableFuture<Long> incr(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Incr, key))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> incr(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Incr, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> incr(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Incr, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> incr(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Incr, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> incrBy(String key, long amount) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(IncrBy, key, String.valueOf(amount)))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> incrBy(String key, long amount, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(IncrBy, key, String.valueOf(amount)), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> incrBy(GlideString key, long amount) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(IncrBy, key.toString(), String.valueOf(amount)))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> incrBy(GlideString key, long amount, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(IncrBy, key.toString(), String.valueOf(amount)), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Double> incrByFloat(String key, double amount) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(IncrByFloat, key, String.valueOf(amount)))
                .thenApply(result -> (Double) result);
    }

    public CompletableFuture<ClusterValue<Double>> incrByFloat(String key, double amount, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(IncrByFloat, key, String.valueOf(amount)),
                        route)
                .thenApply(result -> ClusterValue.of((Double) result));
    }

    @Override
    public CompletableFuture<Double> incrByFloat(GlideString key, double amount) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(IncrByFloat, key.toString(), String.valueOf(amount)))
                .thenApply(result -> (Double) result);
    }

    public CompletableFuture<ClusterValue<Double>> incrByFloat(GlideString key, double amount, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(IncrByFloat, key.toString(), String.valueOf(amount)), route)
                .thenApply(result -> ClusterValue.of((Double) result));
    }

    @Override
    public CompletableFuture<Long> decr(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Decr, key))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> decr(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Decr, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> decr(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Decr, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> decr(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Decr, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> decrBy(String key, long amount) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(DecrBy, key, String.valueOf(amount)))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> decrBy(String key, long amount, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(DecrBy, key, String.valueOf(amount)), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> decrBy(GlideString key, long amount) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(DecrBy, key.toString(), String.valueOf(amount)))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> decrBy(GlideString key, long amount, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(DecrBy, key.toString(), String.valueOf(amount)), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // String commands - StringBaseCommands implementation

    @Override
    public CompletableFuture<String> get(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GET, key))
                .thenApply(result -> (String) result);
    }

    @Override
    public CompletableFuture<GlideString> get(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GET, key.toString()))
                .thenApply(result -> ArrayTransformUtils.toGlideStringArray(new String[] { (String) result })[0]);
    }

    public CompletableFuture<ClusterValue<String>> get(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GET, key), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    public CompletableFuture<ClusterValue<GlideString>> get(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GET, key.toString()), route)
                .thenApply(result -> ClusterValue
                        .of(ArrayTransformUtils.toGlideStringArray(new String[] { (String) result })[0]));
    }

    @Override
    public CompletableFuture<String> set(String key, String value) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, key, value))
                .thenApply(result -> (String) result);
    }

    @Override
    public CompletableFuture<String> set(GlideString key, GlideString value) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, key.toString(), value.toString()))
                .thenApply(result -> (String) result);
    }

    public CompletableFuture<ClusterValue<String>> set(String key, String value, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, key, value), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    public CompletableFuture<ClusterValue<String>> set(GlideString key, GlideString value, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(SET, key.toString(), value.toString()), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    @Override
    public CompletableFuture<String> set(String key, String value, SetOptions options) {
        String[] optionArgs = options.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = value;
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, args))
                .thenApply(result -> (String) result);
    }

    @Override
    public CompletableFuture<String> set(GlideString key, GlideString value, SetOptions options) {
        String[] optionArgs = options.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = value.toString();
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, args))
                .thenApply(result -> (String) result);
    }

    public CompletableFuture<ClusterValue<String>> set(String key, String value, SetOptions options, Route route) {
        String[] optionArgs = options.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = value;
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, args), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    public CompletableFuture<ClusterValue<String>> set(GlideString key, GlideString value, SetOptions options,
            Route route) {
        String[] optionArgs = options.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = value.toString();
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SET, args), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    @Override
    public CompletableFuture<String[]> mget(String[] keys) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MGet, keys))
                .thenApply(result -> (String[]) result);
    }

    @Override
    public CompletableFuture<GlideString[]> mget(GlideString[] keys) {
        String[] keyStrings = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            keyStrings[i] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MGet, keyStrings))
                .thenApply(result -> ArrayTransformUtils.toGlideStringArray((String[]) result));
    }

    public CompletableFuture<ClusterValue<String[]>> mget(String[] keys, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MGet, keys), route)
                .thenApply(result -> ClusterValue.of((String[]) result));
    }

    public CompletableFuture<ClusterValue<GlideString[]>> mget(GlideString[] keys, Route route) {
        String[] keyStrings = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            keyStrings[i] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MGet, keyStrings), route)
                .thenApply(result -> ClusterValue.of(ArrayTransformUtils.toGlideStringArray((String[]) result)));
    }

    @Override
    public CompletableFuture<String> mset(Map<String, String> keyValueMap) {
        String[] args = ArrayTransformUtils.convertMapToKeyValueStringArray(keyValueMap);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MSet, args))
                .thenApply(result -> (String) result);
    }

    public CompletableFuture<ClusterValue<String>> mset(Map<String, String> keyValueMap, Route route) {
        String[] args = ArrayTransformUtils.convertMapToKeyValueStringArray(keyValueMap);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MSet, args), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    @Override
    public CompletableFuture<Boolean> msetnx(Map<String, String> keyValueMap) {
        String[] args = ArrayTransformUtils.convertMapToKeyValueStringArray(keyValueMap);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MSetNX, args))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> msetnx(Map<String, String> keyValueMap, Route route) {
        String[] args = ArrayTransformUtils.convertMapToKeyValueStringArray(keyValueMap);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(MSetNX, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    @Override
    public CompletableFuture<String> getdel(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETDEL, key))
                .thenApply(result -> (String) result);
    }

    @Override
    public CompletableFuture<GlideString> getdel(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETDEL, key.toString()))
                .thenApply(result -> ArrayTransformUtils.toGlideStringArray(new String[] { (String) result })[0]);
    }

    public CompletableFuture<ClusterValue<String>> getdel(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETDEL, key), route)
                .thenApply(result -> ClusterValue.of((String) result));
    }

    public CompletableFuture<ClusterValue<GlideString>> getdel(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETDEL, key.toString()), route)
                .thenApply(result -> ClusterValue
                        .of(ArrayTransformUtils.toGlideStringArray(new String[] { (String) result })[0]));
    }

    @Override
    public CompletableFuture<Long> strlen(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Strlen, key))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> strlen(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Strlen, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> strlen(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Strlen, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> strlen(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Strlen, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= KEY EXPIRATION MANAGEMENT METHODS =============

    // EXPIRE methods
    @Override
    public CompletableFuture<Boolean> expire(String key, long seconds) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIRE, key, Long.toString(seconds)))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> expire(GlideString key, long seconds) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(EXPIRE, key.toString(), Long.toString(seconds)))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> expire(String key, long seconds, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(EXPIRE, key, Long.toString(seconds)), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> expire(GlideString key, long seconds, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(EXPIRE, key.toString(), Long.toString(seconds)), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    @Override
    public CompletableFuture<Boolean> expire(String key, long seconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(seconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIRE, args))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> expire(GlideString key, long seconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(seconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIRE, args))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> expire(String key, long seconds, ExpireOptions expireOptions,
            Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(seconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIRE, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> expire(GlideString key, long seconds, ExpireOptions expireOptions,
            Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(seconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIRE, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // EXPIREAT methods
    @Override
    public CompletableFuture<Boolean> expireAt(String key, long timestamp) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIREAT, key, Long.toString(timestamp)))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(GlideString key, long timestamp) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(EXPIREAT, key.toString(), Long.toString(timestamp)))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> expireAt(String key, long timestamp, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(EXPIREAT, key, Long.toString(timestamp)),
                        route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> expireAt(GlideString key, long timestamp, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(EXPIREAT, key.toString(), Long.toString(timestamp)), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    @Override
    public CompletableFuture<Boolean> expireAt(String key, long unixSeconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(unixSeconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIREAT, args))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(GlideString key, long unixSeconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(unixSeconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIREAT, args))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> expireAt(String key, long unixSeconds, ExpireOptions expireOptions,
            Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(unixSeconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIREAT, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> expireAt(GlideString key, long unixSeconds,
            ExpireOptions expireOptions, Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(unixSeconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(EXPIREAT, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // PEXPIRE methods
    @Override
    public CompletableFuture<Boolean> pexpire(String key, long milliseconds) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(PEXPIRE, key, Long.toString(milliseconds)))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(GlideString key, long milliseconds) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(PEXPIRE, key.toString(), Long.toString(milliseconds)))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpire(String key, long milliseconds, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(PEXPIRE, key, Long.toString(milliseconds)),
                        route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpire(GlideString key, long milliseconds, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(PEXPIRE, key.toString(), Long.toString(milliseconds)), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    @Override
    public CompletableFuture<Boolean> pexpire(String key, long milliseconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(milliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIRE, args))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(GlideString key, long milliseconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(milliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIRE, args))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpire(String key, long milliseconds, ExpireOptions expireOptions,
            Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(milliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIRE, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpire(GlideString key, long milliseconds,
            ExpireOptions expireOptions, Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(milliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIRE, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // PEXPIREAT methods
    @Override
    public CompletableFuture<Boolean> pexpireAt(String key, long timestamp) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(PEXPIREAT, key, Long.toString(timestamp)))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(GlideString key, long timestamp) {
        return client
                .executeCommand(
                        new io.valkey.glide.core.commands.Command(PEXPIREAT, key.toString(), Long.toString(timestamp)))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpireAt(String key, long timestamp, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(PEXPIREAT, key, Long.toString(timestamp)),
                        route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpireAt(GlideString key, long timestamp, Route route) {
        return client.executeCommand(
                new io.valkey.glide.core.commands.Command(PEXPIREAT, key.toString(), Long.toString(timestamp)), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(String key, long unixMilliseconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(unixMilliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIREAT, args))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(GlideString key, long unixMilliseconds, ExpireOptions expireOptions) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(unixMilliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIREAT, args))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpireAt(String key, long unixMilliseconds,
            ExpireOptions expireOptions, Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = Long.toString(unixMilliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIREAT, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> pexpireAt(GlideString key, long unixMilliseconds,
            ExpireOptions expireOptions, Route route) {
        String[] optionArgs = expireOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = Long.toString(unixMilliseconds);
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PEXPIREAT, args), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // TTL methods
    @Override
    public CompletableFuture<Long> ttl(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(TTL, key))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> ttl(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(TTL, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> ttl(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(TTL, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> ttl(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(TTL, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // PTTL methods
    @Override
    public CompletableFuture<Long> pttl(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PTTL, key))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> pttl(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PTTL, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> pttl(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PTTL, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> pttl(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PTTL, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // EXPIRETIME methods
    @Override
    public CompletableFuture<Long> expiretime(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ExpireTime, key))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> expiretime(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ExpireTime, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> expiretime(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ExpireTime, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> expiretime(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(ExpireTime, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // PEXPIRETIME methods
    @Override
    public CompletableFuture<Long> pexpiretime(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PExpireTime, key))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> pexpiretime(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PExpireTime, key.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> pexpiretime(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PExpireTime, key), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> pexpiretime(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(PExpireTime, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // PERSIST methods
    @Override
    public CompletableFuture<Boolean> persist(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Persist, key))
                .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> persist(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Persist, key.toString()))
                .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> persist(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Persist, key), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> persist(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Persist, key.toString()), route)
                .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // ========== GenericBaseCommands Implementation ==========

    @Override
    public CompletableFuture<Long> exists(String[] keys) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Exists, keys))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> exists(GlideString[] keys) {
        // Convert GlideString array to String array
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Exists, stringKeys))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> exists(String[] keys, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Exists, keys), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> exists(GlideString[] keys, Route route) {
        // Convert GlideString array to String array
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Exists, stringKeys), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> unlink(String[] keys) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(UNLINK, keys))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> unlink(GlideString[] keys) {
        // Convert GlideString array to String array
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(UNLINK, stringKeys))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> unlink(String[] keys, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(UNLINK, keys), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> unlink(GlideString[] keys, Route route) {
        // Convert GlideString array to String array
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(UNLINK, stringKeys), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // === ListBaseCommands Implementation ===

    @Override
    public CompletableFuture<Map<String, String[]>> lmpop(String[] keys, ListDirection direction, long count) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = direction.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args))
                .thenApply(result -> (Map<String, String[]>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> lmpop(GlideString[] keys, ListDirection direction,
            long count) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = direction.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args))
                .thenApply(result -> (Map<GlideString, GlideString[]>) result);
    }

    @Override
    public CompletableFuture<Map<String, String[]>> lmpop(String[] keys, ListDirection direction) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = direction.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args))
                .thenApply(result -> (Map<String, String[]>) result);
    }

    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> lmpop(GlideString[] keys, ListDirection direction) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = direction.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args))
                .thenApply(result -> (Map<GlideString, GlideString[]>) result);
    }

    // === Cluster-specific LMPOP methods with Route support ===

    public CompletableFuture<ClusterValue<Map<String, String[]>>> lmpop(String[] keys, ListDirection direction,
            long count, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = direction.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args), route)
                .thenApply(result -> ClusterValue.of((Map<String, String[]>) result));
    }

    public CompletableFuture<ClusterValue<Map<GlideString, GlideString[]>>> lmpop(GlideString[] keys,
            ListDirection direction, long count, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = direction.toString();
        args[keys.length + 2] = String.valueOf(count);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args), route)
                .thenApply(result -> ClusterValue.of((Map<GlideString, GlideString[]>) result));
    }

    public CompletableFuture<ClusterValue<Map<String, String[]>>> lmpop(String[] keys, ListDirection direction,
            Route route) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = direction.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args), route)
                .thenApply(result -> ClusterValue.of((Map<String, String[]>) result));
    }

    public CompletableFuture<ClusterValue<Map<GlideString, GlideString[]>>> lmpop(GlideString[] keys,
            ListDirection direction, Route route) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = direction.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LMPop, args), route)
                .thenApply(result -> ClusterValue.of((Map<GlideString, GlideString[]>) result));
    }

    // ============= SET COMMAND IMPLEMENTATION =============

    // SADD methods
    @Override
    public CompletableFuture<Long> sadd(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SAdd, args))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> sadd(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SAdd, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sadd(String key, String[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SAdd, args), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> sadd(GlideString key, GlideString[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SAdd, args), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // SREM methods
    @Override
    public CompletableFuture<Long> srem(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SRem, args))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> srem(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SRem, args))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> srem(String key, String[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SRem, args), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> srem(GlideString key, GlideString[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SRem, args), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // SMEMBERS methods
    @Override
    public CompletableFuture<Set<String>> smembers(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMembers, key))
            .thenApply(result -> (Set<String>) result);
    }

    @Override
    public CompletableFuture<Set<GlideString>> smembers(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMembers, key.toString()))
            .thenApply(result -> (Set<GlideString>) result);
    }

    public CompletableFuture<ClusterValue<Set<String>>> smembers(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMembers, key), route)
            .thenApply(result -> ClusterValue.of((Set<String>) result));
    }

    public CompletableFuture<ClusterValue<Set<GlideString>>> smembers(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMembers, key.toString()), route)
            .thenApply(result -> ClusterValue.of((Set<GlideString>) result));
    }

    // SCARD methods
    @Override
    public CompletableFuture<Long> scard(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SCard, key))
            .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> scard(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SCard, key.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> scard(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SCard, key), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> scard(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SCard, key.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // SISMEMBER methods
    @Override
    public CompletableFuture<Boolean> sismember(String key, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SIsMember, key, member))
            .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> sismember(GlideString key, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SIsMember, key.toString(), member.toString()))
            .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> sismember(String key, String member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SIsMember, key, member), route)
            .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> sismember(GlideString key, GlideString member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SIsMember, key.toString(), member.toString()), route)
            .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // SMISMEMBER methods
    @Override
    public CompletableFuture<Boolean[]> smismember(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMIsMember, args))
            .thenApply(result -> (Boolean[]) result);
    }

    @Override
    public CompletableFuture<Boolean[]> smismember(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMIsMember, args))
            .thenApply(result -> (Boolean[]) result);
    }

    public CompletableFuture<ClusterValue<Boolean[]>> smismember(String key, String[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMIsMember, args), route)
            .thenApply(result -> ClusterValue.of((Boolean[]) result));
    }

    public CompletableFuture<ClusterValue<Boolean[]>> smismember(GlideString key, GlideString[] members, Route route) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMIsMember, args), route)
            .thenApply(result -> ClusterValue.of((Boolean[]) result));
    }

    // SMOVE methods
    @Override
    public CompletableFuture<Boolean> smove(String source, String destination, String member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMove, source, destination, member))
            .thenApply(result -> (Boolean) result);
    }

    @Override
    public CompletableFuture<Boolean> smove(GlideString source, GlideString destination, GlideString member) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMove, source.toString(), destination.toString(), member.toString()))
            .thenApply(result -> (Boolean) result);
    }

    public CompletableFuture<ClusterValue<Boolean>> smove(String source, String destination, String member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMove, source, destination, member), route)
            .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    public CompletableFuture<ClusterValue<Boolean>> smove(GlideString source, GlideString destination, GlideString member, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SMove, source.toString(), destination.toString(), member.toString()), route)
            .thenApply(result -> ClusterValue.of((Boolean) result));
    }

    // SINTERCARD methods
    @Override
    public CompletableFuture<Long> sintercard(String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> sintercard(GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sintercard(String[] keys, Route route) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> sintercard(GlideString[] keys, Route route) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> sintercard(String[] keys, long limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args))
                .thenApply(result -> (Long) result);
    }

    @Override
    public CompletableFuture<Long> sintercard(GlideString[] keys, long limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sintercard(String[] keys, long limit, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> sintercard(GlideString[] keys, long limit, Route route) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SInterCard, args), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    // SSCAN methods
    @Override
    public CompletableFuture<Object[]> sscan(String key, String cursor) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SScan, key, cursor))
                .thenApply(result -> (Object[]) result);
    }

    @Override
    public CompletableFuture<Object[]> sscan(GlideString key, GlideString cursor) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(SScan, key.toString(), cursor.toString()))
                .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<ClusterValue<Object[]>> sscan(String key, String cursor, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SScan, key, cursor), route)
                .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    public CompletableFuture<ClusterValue<Object[]>> sscan(GlideString key, GlideString cursor, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(SScan, key.toString(), cursor.toString()),
                        route)
                .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    @Override
    public CompletableFuture<Object[]> sscan(String key, String cursor, SScanOptions sScanOptions) {
        String[] optionArgs = sScanOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = cursor;
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SScan, args))
                .thenApply(result -> (Object[]) result);
    }

    @Override
    public CompletableFuture<Object[]> sscan(GlideString key, GlideString cursor, SScanOptionsBinary sScanOptions) {
        String[] optionArgs = sScanOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = cursor.toString();
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SScan, args))
                .thenApply(result -> (Object[]) result);
    }

    public CompletableFuture<ClusterValue<Object[]>> sscan(String key, String cursor, SScanOptions sScanOptions,
            Route route) {
        String[] optionArgs = sScanOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key;
        args[1] = cursor;
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SScan, args), route)
                .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    public CompletableFuture<ClusterValue<Object[]>> sscan(GlideString key, GlideString cursor,
            SScanOptionsBinary sScanOptions, Route route) {
        String[] optionArgs = sScanOptions.toArgs();
        String[] args = new String[optionArgs.length + 2];
        args[0] = key.toString();
        args[1] = cursor.toString();
        System.arraycopy(optionArgs, 0, args, 2, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SScan, args), route)
                .thenApply(result -> ClusterValue.of((Object[]) result));
    }

    // ============= BITMAP COMMAND IMPLEMENTATION =============

    // BITCOUNT methods
    public CompletableFuture<Long> bitcount(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> bitcount(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<Long> bitcount(String key, long start) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key, String.valueOf(start)))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> bitcount(GlideString key, long start) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString(), String.valueOf(start)))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(String key, long start, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key, String.valueOf(start)), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(GlideString key, long start, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString(), String.valueOf(start)), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<Long> bitcount(String key, long start, long end) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key, String.valueOf(start), String.valueOf(end)))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> bitcount(GlideString key, long start, long end) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString(), String.valueOf(start), String.valueOf(end)))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(String key, long start, long end, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key, String.valueOf(start), String.valueOf(end)), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(GlideString key, long start, long end, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString(), String.valueOf(start), String.valueOf(end)), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<Long> bitcount(String key, long start, long end, BitmapIndexType options) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key, String.valueOf(start), String.valueOf(end), options.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<Long> bitcount(GlideString key, long start, long end, BitmapIndexType options) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString(), String.valueOf(start), String.valueOf(end), options.toString()))
            .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(String key, long start, long end, BitmapIndexType options, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key, String.valueOf(start), String.valueOf(end), options.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    public CompletableFuture<ClusterValue<Long>> bitcount(GlideString key, long start, long end, BitmapIndexType options, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(BitCount, key.toString(), String.valueOf(start), String.valueOf(end), options.toString()), route)
            .thenApply(result -> ClusterValue.of((Long) result));
    }

    // ============= LCS COMMAND IMPLEMENTATION =============

    // LCSIDX methods
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(String key1, String key2) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX"))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdx(String key1, String key2, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX"), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(GlideString key1, GlideString key2) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX"))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdx(GlideString key1, GlideString key2, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX"), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(String key1, String key2, long minMatchLen) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX", "MINMATCHLEN", String.valueOf(minMatchLen)))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdx(String key1, String key2, long minMatchLen, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX", "MINMATCHLEN", String.valueOf(minMatchLen)), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(GlideString key1, GlideString key2, long minMatchLen) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX", "MINMATCHLEN", String.valueOf(minMatchLen)))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdx(GlideString key1, GlideString key2, long minMatchLen, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX", "MINMATCHLEN", String.valueOf(minMatchLen)), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(String key1, String key2) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX", "WITHMATCHLEN"))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdxWithMatchLen(String key1, String key2, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX", "WITHMATCHLEN"), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(GlideString key1, GlideString key2) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX", "WITHMATCHLEN"))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdxWithMatchLen(GlideString key1, GlideString key2, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX", "WITHMATCHLEN"), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(String key1, String key2, long minMatchLen) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX", "WITHMATCHLEN", "MINMATCHLEN", String.valueOf(minMatchLen)))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdxWithMatchLen(String key1, String key2, long minMatchLen, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1, key2, "IDX", "WITHMATCHLEN", "MINMATCHLEN", String.valueOf(minMatchLen)), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(GlideString key1, GlideString key2, long minMatchLen) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX", "WITHMATCHLEN", "MINMATCHLEN", String.valueOf(minMatchLen)))
            .thenApply(result -> (Map<String, Object>) result);
    }

    public CompletableFuture<ClusterValue<Map<String, Object>>> lcsIdxWithMatchLen(GlideString key1, GlideString key2, long minMatchLen, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(LCS, key1.toString(), key2.toString(), "IDX", "WITHMATCHLEN", "MINMATCHLEN", String.valueOf(minMatchLen)), route)
            .thenApply(result -> ClusterValue.of((Map<String, Object>) result));
    }

    // ============= GETEX COMMAND IMPLEMENTATION =============

    // GETEX methods
    @Override
    public CompletableFuture<String> getex(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, key))
                .thenApply(result -> result != null ? result.toString() : null);
    }

    public CompletableFuture<ClusterValue<String>> getex(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, key), route)
                .thenApply(result -> ClusterValue.of(result != null ? result.toString() : null));
    }

    @Override
    public CompletableFuture<GlideString> getex(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, key.toString()))
                .thenApply(result -> result != null ? GlideString.of(result.toString()) : null);
    }

    public CompletableFuture<ClusterValue<GlideString>> getex(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, key.toString()), route)
                .thenApply(result -> ClusterValue.of(result != null ? GlideString.of(result.toString()) : null));
    }

    @Override
    public CompletableFuture<String> getex(String key, GetExOptions options) {
        String[] args = new String[options.toArgs().length + 1];
        args[0] = key;
        String[] optionArgs = options.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, args))
                .thenApply(result -> result != null ? result.toString() : null);
    }

    public CompletableFuture<ClusterValue<String>> getex(String key, GetExOptions options, Route route) {
        String[] args = new String[options.toArgs().length + 1];
        args[0] = key;
        String[] optionArgs = options.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, args), route)
                .thenApply(result -> ClusterValue.of(result != null ? result.toString() : null));
    }

    @Override
    public CompletableFuture<GlideString> getex(GlideString key, GetExOptions options) {
        String[] args = new String[options.toArgs().length + 1];
        args[0] = key.toString();
        String[] optionArgs = options.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, args))
                .thenApply(result -> result != null ? GlideString.of(result.toString()) : null);
    }

    public CompletableFuture<ClusterValue<GlideString>> getex(GlideString key, GetExOptions options, Route route) {
        String[] args = new String[options.toArgs().length + 1];
        args[0] = key.toString();
        String[] optionArgs = options.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(GETEX, args), route)
                .thenApply(result -> ClusterValue.of(result != null ? GlideString.of(result.toString()) : null));
    }

    // ============= SORT COMMAND IMPLEMENTATION =============

    // SORT methods
    @Override
    public CompletableFuture<String[]> sort(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, key))
                .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<ClusterValue<String[]>> sort(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, key), route)
                .thenApply(result -> ClusterValue.of((String[]) result));
    }

    @Override
    public CompletableFuture<GlideString[]> sort(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, key.toString()))
                .thenApply(result -> (GlideString[]) result);
    }

    public CompletableFuture<ClusterValue<GlideString[]>> sort(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, key.toString()), route)
                .thenApply(result -> ClusterValue.of((GlideString[]) result));
    }

    @Override
    public CompletableFuture<String[]> sort(String key, SortOptions sortOptions) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key;
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args))
                .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<ClusterValue<String[]>> sort(String key, SortOptions sortOptions, Route route) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key;
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args), route)
                .thenApply(result -> ClusterValue.of((String[]) result));
    }

    @Override
    public CompletableFuture<GlideString[]> sort(GlideString key, SortOptionsBinary sortOptions) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key.toString();
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args))
                .thenApply(result -> (GlideString[]) result);
    }

    public CompletableFuture<ClusterValue<GlideString[]>> sort(GlideString key, SortOptionsBinary sortOptions,
            Route route) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key.toString();
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args), route)
                .thenApply(result -> ClusterValue.of((GlideString[]) result));
    }

    @Override
    public CompletableFuture<String[]> sortReadOnly(String key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, key))
                .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<ClusterValue<String[]>> sortReadOnly(String key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, key), route)
                .thenApply(result -> ClusterValue.of((String[]) result));
    }

    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(GlideString key) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, key.toString()))
                .thenApply(result -> (GlideString[]) result);
    }

    public CompletableFuture<ClusterValue<GlideString[]>> sortReadOnly(GlideString key, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, key.toString()), route)
                .thenApply(result -> ClusterValue.of((GlideString[]) result));
    }

    @Override
    public CompletableFuture<String[]> sortReadOnly(String key, SortOptions sortOptions) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key;
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, args))
                .thenApply(result -> (String[]) result);
    }

    public CompletableFuture<ClusterValue<String[]>> sortReadOnly(String key, SortOptions sortOptions, Route route) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key;
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, args), route)
                .thenApply(result -> ClusterValue.of((String[]) result));
    }

    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(GlideString key, SortOptionsBinary sortOptions) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key.toString();
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, args))
                .thenApply(result -> (GlideString[]) result);
    }

    public CompletableFuture<ClusterValue<GlideString[]>> sortReadOnly(GlideString key, SortOptionsBinary sortOptions,
            Route route) {
        String[] args = new String[sortOptions.toArgs().length + 1];
        args[0] = key.toString();
        String[] optionArgs = sortOptions.toArgs();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        return client.executeCommand(new io.valkey.glide.core.commands.Command(SortReadOnly, args), route)
                .thenApply(result -> ClusterValue.of((GlideString[]) result));
    }

    @Override
    public CompletableFuture<Long> sortStore(String key, String destination) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, key, "STORE", destination))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sortStore(String key, String destination, Route route) {
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, key, "STORE", destination), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> sortStore(GlideString key, GlideString destination) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(Sort, key.toString(), "STORE",
                        destination.toString()))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sortStore(GlideString key, GlideString destination, Route route) {
        return client
                .executeCommand(new io.valkey.glide.core.commands.Command(Sort, key.toString(), "STORE",
                        destination.toString()), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> sortStore(String key, String destination, SortOptions sortOptions) {
        String[] optionArgs = sortOptions.toArgs();
        String[] args = new String[optionArgs.length + 3];
        args[0] = key;
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        args[args.length - 2] = "STORE";
        args[args.length - 1] = destination;
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sortStore(String key, String destination, SortOptions sortOptions,
            Route route) {
        String[] optionArgs = sortOptions.toArgs();
        String[] args = new String[optionArgs.length + 3];
        args[0] = key;
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        args[args.length - 2] = "STORE";
        args[args.length - 1] = destination;
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

    @Override
    public CompletableFuture<Long> sortStore(GlideString key, GlideString destination, SortOptionsBinary sortOptions) {
        String[] optionArgs = sortOptions.toArgs();
        String[] args = new String[optionArgs.length + 3];
        args[0] = key.toString();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        args[args.length - 2] = "STORE";
        args[args.length - 1] = destination.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args))
                .thenApply(result -> (Long) result);
    }

    public CompletableFuture<ClusterValue<Long>> sortStore(GlideString key, GlideString destination,
            SortOptionsBinary sortOptions, Route route) {
        String[] optionArgs = sortOptions.toArgs();
        String[] args = new String[optionArgs.length + 3];
        args[0] = key.toString();
        System.arraycopy(optionArgs, 0, args, 1, optionArgs.length);
        args[args.length - 2] = "STORE";
        args[args.length - 1] = destination.toString();
        return client.executeCommand(new io.valkey.glide.core.commands.Command(Sort, args), route)
                .thenApply(result -> ClusterValue.of((Long) result));
    }

}
