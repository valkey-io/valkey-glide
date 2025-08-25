/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static command_request.CommandRequestOuterClass.RequestType.ClientGetName;
import static command_request.CommandRequestOuterClass.RequestType.ClientId;
import static command_request.CommandRequestOuterClass.RequestType.ConfigGet;
import static command_request.CommandRequestOuterClass.RequestType.ConfigResetStat;
import static command_request.CommandRequestOuterClass.RequestType.ConfigRewrite;
import static command_request.CommandRequestOuterClass.RequestType.ConfigSet;
import static command_request.CommandRequestOuterClass.RequestType.CustomCommand;
import static command_request.CommandRequestOuterClass.RequestType.DBSize;
import static command_request.CommandRequestOuterClass.RequestType.Echo;
import static command_request.CommandRequestOuterClass.RequestType.FCall;
import static command_request.CommandRequestOuterClass.RequestType.FCallReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.FlushAll;
import static command_request.CommandRequestOuterClass.RequestType.FlushDB;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDelete;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDump;
import static command_request.CommandRequestOuterClass.RequestType.FunctionFlush;
import static command_request.CommandRequestOuterClass.RequestType.FunctionKill;
import static command_request.CommandRequestOuterClass.RequestType.FunctionList;
import static command_request.CommandRequestOuterClass.RequestType.FunctionLoad;
import static command_request.CommandRequestOuterClass.RequestType.FunctionRestore;
import static command_request.CommandRequestOuterClass.RequestType.FunctionStats;
import static command_request.CommandRequestOuterClass.RequestType.Info;
import static command_request.CommandRequestOuterClass.RequestType.LastSave;
import static command_request.CommandRequestOuterClass.RequestType.Lolwut;
import static command_request.CommandRequestOuterClass.RequestType.Ping;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardNumSub;
import static command_request.CommandRequestOuterClass.RequestType.RandomKey;
import static command_request.CommandRequestOuterClass.RequestType.SPublish;
import static command_request.CommandRequestOuterClass.RequestType.ScriptExists;
import static command_request.CommandRequestOuterClass.RequestType.ScriptFlush;
import static command_request.CommandRequestOuterClass.RequestType.ScriptKill;
import static command_request.CommandRequestOuterClass.RequestType.Select;
import static command_request.CommandRequestOuterClass.RequestType.Time;
import static command_request.CommandRequestOuterClass.RequestType.UnWatch;
import static glide.api.commands.ServerManagementCommands.VERSION_VALKEY_API;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_VALKEY_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_VALKEY_API;
import static glide.api.models.commands.function.FunctionLoadOptions.REPLACE;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.castMapOfArrays;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;

import glide.api.commands.ConnectionManagementClusterCommands;
import glide.api.commands.GenericClusterCommands;
import glide.api.commands.PubSubClusterCommands;
import glide.api.commands.ScriptingAndFunctionsClusterCommands;
import glide.api.commands.ServerManagementClusterCommands;
import glide.api.commands.TransactionsClusterCommands;
import glide.api.logging.Logger;
import glide.api.models.ClusterBatch;
import glide.api.models.ClusterTransaction;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.ScriptArgOptions;
import glide.api.models.commands.ScriptArgOptionsGlideString;
import glide.api.models.commands.batch.ClusterBatchOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.api.models.configuration.ServerCredentials;
import glide.ffi.resolvers.ClusterScanCursorResolver;
import glide.managers.CommandManager;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import response.ResponseOuterClass.Response;

/**
 * Client used for connection to cluster servers.<br>
 * Use {@link #createClient} to request a client.
 *
 * @see For full documentation refer to <a
 *     href="https://github.com/valkey-io/valkey-glide/wiki/Java-Wrapper#cluster">Valkey Glide
 *     Wiki</a>.
 */
public class GlideClusterClient extends BaseClient
        implements ConnectionManagementClusterCommands,
                GenericClusterCommands,
                ServerManagementClusterCommands,
                ScriptingAndFunctionsClusterCommands,
                TransactionsClusterCommands,
                PubSubClusterCommands {

    /** A private constructor. Use {@link #createClient} to get a client. */
    GlideClusterClient(ClientBuilder builder) {
        super(builder);
    }

    /**
     * Creates a new {@link GlideClusterClient} instance and establishes connections to a Valkey
     * Cluster.
     *
     * @param config The configuration options for the client, including cluster addresses,
     *     authentication credentials, TLS settings, periodic checks, and Pub/Sub subscriptions.
     * @return A Future that resolves to a connected {@link GlideClusterClient} instance.
     * @remarks Use this static method to create and connect a {@link GlideClusterClient} to a Valkey
     *     Cluster. The client will automatically handle connection establishment, including cluster
     *     topology discovery and handling of authentication and TLS configurations.
     *     <ul>
     *       <li><b>Cluster Topology Discovery</b>: The client will automatically discover the cluster
     *           topology based on the seed addresses provided.
     *       <li><b>Authentication</b>: If {@link ServerCredentials} are provided, the client will
     *           attempt to authenticate using the specified username and password.
     *       <li><b>TLS</b>: If {@link
     *           BaseClientConfiguration.BaseClientConfigurationBuilder#useTLS(boolean)} is set to
     *           <code>true</code>, the client will establish secure connections using TLS.
     *       <li><b>Pub/Sub Subscriptions</b>: Any channels or patterns specified in {@link
     *           ClusterSubscriptionConfiguration} will be subscribed to upon connection.
     *       <li><b>Reconnection Strategy</b>: The {@link BackoffStrategy} settings define how the
     *           client will attempt to reconnect in case of disconnections.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * GlideClusterClientConfiguration config =
     *     GlideClusterClientConfiguration.builder()
     *         .address(node1address)
     *         .address(node2address)
     *         .useTLS(true)
     *         .readFrom(ReadFrom.PREFER_REPLICA)
     *         .credentials(credentialsConfiguration)
     *         .requestTimeout(2000)
     *         .clientName("GLIDE")
     *         .subscriptionConfiguration(
     *             ClusterSubscriptionConfiguration.builder()
     *                 .subscription(EXACT, "notifications")
     *                 .subscription(EXACT, "news")
     *                 .subscription(SHARDED, "data")
     *                 .callback(callback)
     *                 .build())
     *         .inflightRequestsLimit(1000)
     *         .build();
     * GlideClusterClient client = GlideClusterClient.createClient(config).get();
     * }</pre>
     */
    public static CompletableFuture<GlideClusterClient> createClient(
            @NonNull GlideClusterClientConfiguration config) {
        return createClient(config, GlideClusterClient::new);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(@NonNull String[] args) {
        // TODO if a command returns a map as a single value, ClusterValue misleads user
        return commandManager.submitNewCommand(
                CustomCommand, args, response -> ClusterValue.of(handleObjectOrNullResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(@NonNull GlideString[] args) {
        // TODO if a command returns a map as a single value, ClusterValue misleads user
        return commandManager.submitNewCommand(
                CustomCommand,
                args,
                response -> ClusterValue.of(handleBinaryObjectOrNullResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(
            @NonNull String[] args, @NonNull Route route) {
        return commandManager.submitNewCommand(
                CustomCommand, args, route, response -> handleCustomCommandResponse(route, response));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(
            @NonNull GlideString[] args, @NonNull Route route) {
        return commandManager.submitNewCommand(
                CustomCommand, args, route, response -> handleCustomCommandBinaryResponse(route, response));
    }

    @SuppressWarnings("unchecked")
    protected ClusterValue<Object> handleCustomCommandResponse(Route route, Response response) {
        if (route instanceof SingleNodeRoute) {
            return ClusterValue.ofSingleValue(handleObjectOrNullResponse(response));
        }
        if (response.hasConstantResponse()) {
            return ClusterValue.ofSingleValue(handleStringResponse(response));
        }
        var data =
                handleValkeyResponse(Object.class, EnumSet.of(ResponseFlags.ENCODING_UTF8), response);
        if (data instanceof Map) {
            return ClusterValue.ofMultiValue((Map<String, Object>) data);
        }
        return ClusterValue.ofSingleValue(data);
    }

    @SuppressWarnings("unchecked")
    protected ClusterValue<Object> handleCustomCommandBinaryResponse(Route route, Response response) {
        if (route instanceof SingleNodeRoute) {
            return ClusterValue.ofSingleValue(handleBinaryObjectOrNullResponse(response));
        }
        if (response.hasConstantResponse()) {
            return ClusterValue.ofSingleValue(handleStringResponse(response));
        }
        var data = handleValkeyResponse(Object.class, EnumSet.noneOf(ResponseFlags.class), response);
        if (data instanceof Map) {
            return ClusterValue.ofMultiValueBinary((Map<GlideString, Object>) data);
        }
        return ClusterValue.ofSingleValue(data);
    }

    @Deprecated
    @Override
    public CompletableFuture<Object[]> exec(@NonNull ClusterTransaction transaction) {
        if (transaction.isBinaryOutput()) {
            return commandManager.submitNewBatch(
                    transaction, true, Optional.empty(), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewBatch(
                    transaction, true, Optional.empty(), this::handleArrayOrNullResponse);
        }
    }

    @Deprecated
    @Override
    public CompletableFuture<Object[]> exec(
            @NonNull ClusterTransaction transaction, @NonNull SingleNodeRoute route) {
        ClusterBatchOptions options = ClusterBatchOptions.builder().route(route).build();
        if (transaction.isBinaryOutput()) {
            return commandManager.submitNewBatch(
                    transaction, true, Optional.of(options), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewBatch(
                    transaction, true, Optional.of(options), this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object[]> exec(@NonNull ClusterBatch batch, boolean raiseOnError) {
        if (batch.isBinaryOutput()) {
            return commandManager.submitNewBatch(
                    batch, raiseOnError, Optional.empty(), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewBatch(
                    batch, raiseOnError, Optional.empty(), this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object[]> exec(
            @NonNull ClusterBatch batch, boolean raiseOnError, @NonNull ClusterBatchOptions options) {
        if (batch.isBinaryOutput()) {
            return commandManager.submitNewBatch(
                    batch, raiseOnError, Optional.of(options), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewBatch(
                    batch, raiseOnError, Optional.of(options), this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<String> ping() {
        return commandManager.submitNewCommand(Ping, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message) {
        return commandManager.submitNewCommand(
                Ping, new String[] {message}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> ping(@NonNull GlideString message) {
        return commandManager.submitNewCommand(
                Ping, new GlideString[] {message}, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull Route route) {
        return commandManager.submitNewCommand(Ping, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Ping, new String[] {message}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> ping(@NonNull GlideString message, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Ping, new GlideString[] {message}, route, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info() {
        return commandManager.submitNewCommand(
                Info, new String[0], response -> ClusterValue.of(handleMapResponse(response)));
    }

    public CompletableFuture<ClusterValue<String>> info(@NonNull Route route) {
        return commandManager.submitNewCommand(
                Info,
                new String[0],
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.of(handleStringResponse(response))
                                : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(@NonNull Section[] sections) {
        return commandManager.submitNewCommand(
                Info,
                Stream.of(sections).map(Enum::toString).toArray(String[]::new),
                response -> ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(
            @NonNull Section[] sections, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Info,
                Stream.of(sections).map(Enum::toString).toArray(String[]::new),
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.of(handleStringResponse(response))
                                : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Long> clientId() {
        return commandManager.submitNewCommand(ClientId, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Long>> clientId(@NonNull Route route) {
        return commandManager.submitNewCommand(
                ClientId,
                new String[0],
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.of(handleLongResponse(response))
                                : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> clientGetName() {
        return commandManager.submitNewCommand(
                ClientGetName, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> clientGetName(@NonNull Route route) {
        return commandManager.submitNewCommand(
                ClientGetName,
                new String[0],
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.of(handleStringOrNullResponse(response))
                                : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> configRewrite() {
        return commandManager.submitNewCommand(
                ConfigRewrite, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configRewrite(@NonNull Route route) {
        return commandManager.submitNewCommand(
                ConfigRewrite, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configResetStat() {
        return commandManager.submitNewCommand(
                ConfigResetStat, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configResetStat(@NonNull Route route) {
        return commandManager.submitNewCommand(
                ConfigResetStat, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, String>> configGet(@NonNull String[] parameters) {
        return commandManager.submitNewCommand(ConfigGet, parameters, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, String>>> configGet(
            @NonNull String[] parameters, @NonNull Route route) {
        return commandManager.submitNewCommand(
                ConfigGet,
                parameters,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleMapResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> configSet(@NonNull Map<String, String> parameters) {
        return commandManager.submitNewCommand(
                ConfigSet, convertMapToKeyValueStringArray(parameters), this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configSet(
            @NonNull Map<String, String> parameters, @NonNull Route route) {
        return commandManager.submitNewCommand(
                ConfigSet, convertMapToKeyValueStringArray(parameters), route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> echo(@NonNull String message) {
        return commandManager.submitNewCommand(
                Echo, new String[] {message}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> echo(@NonNull GlideString message) {
        return commandManager.submitNewCommand(
                Echo, new GlideString[] {message}, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> echo(
            @NonNull String message, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Echo,
                new String[] {message},
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleStringResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<GlideString>> echo(
            @NonNull GlideString message, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Echo,
                new GlideString[] {message},
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleGlideStringResponse(response))
                                : ClusterValue.ofMultiValueBinary(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> select(long index) {
        return commandManager.submitNewCommand(
                Select, new String[] {Long.toString(index)}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> select(long index, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Select,
                new String[] {Long.toString(index)},
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleStringResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String[]> time() {
        return commandManager.submitNewCommand(
                Time, new String[0], response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<ClusterValue<String[]>> time(@NonNull Route route) {
        return commandManager.submitNewCommand(
                Time,
                new String[0],
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(castArray(handleArrayResponse(response), String.class))
                                : ClusterValue.ofMultiValue(
                                        castMapOfArrays(handleMapResponse(response), String.class)));
    }

    @Override
    public CompletableFuture<Long> lastsave() {
        return commandManager.submitNewCommand(LastSave, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Long>> lastsave(@NonNull Route route) {
        return commandManager.submitNewCommand(
                LastSave,
                new String[0],
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.of(handleLongResponse(response))
                                : ClusterValue.of(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> flushall() {
        return commandManager.submitNewCommand(FlushAll, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FlushAll, new String[] {mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull Route route) {
        return commandManager.submitNewCommand(
                FlushAll, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull FlushMode mode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FlushAll, new String[] {mode.toString()}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb() {
        return commandManager.submitNewCommand(FlushDB, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FlushDB, new String[] {mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull Route route) {
        return commandManager.submitNewCommand(
                FlushDB, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FlushDB, new String[] {mode.toString()}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut() {
        return commandManager.submitNewCommand(Lolwut, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int @NonNull [] parameters) {
        String[] arguments =
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        return commandManager.submitNewCommand(Lolwut, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int version) {
        return commandManager.submitNewCommand(
                Lolwut,
                new String[] {VERSION_VALKEY_API, Integer.toString(version)},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_VALKEY_API, Integer.toString(version)},
                        Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        return commandManager.submitNewCommand(Lolwut, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(@NonNull Route route) {
        return commandManager.submitNewCommand(
                Lolwut,
                new String[0],
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleStringResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(
            int @NonNull [] parameters, @NonNull Route route) {
        String[] arguments =
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        return commandManager.submitNewCommand(
                Lolwut,
                arguments,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleStringResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(int version, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Lolwut,
                new String[] {VERSION_VALKEY_API, Integer.toString(version)},
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleStringResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> lolwut(
            int version, int @NonNull [] parameters, @NonNull Route route) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_VALKEY_API, Integer.toString(version)},
                        Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        return commandManager.submitNewCommand(
                Lolwut,
                arguments,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleStringResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<Long> dbsize() {
        return commandManager.submitNewCommand(DBSize, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> dbsize(@NonNull Route route) {
        return commandManager.submitNewCommand(DBSize, new String[0], route, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> functionLoad(@NonNull String libraryCode, boolean replace) {
        String[] arguments =
                replace ? new String[] {REPLACE.toString(), libraryCode} : new String[] {libraryCode};
        return commandManager.submitNewCommand(FunctionLoad, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> functionLoad(
            @NonNull GlideString libraryCode, boolean replace) {
        GlideString[] arguments =
                replace
                        ? new GlideString[] {gs(REPLACE.toString()), libraryCode}
                        : new GlideString[] {libraryCode};
        return commandManager.submitNewCommand(
                FunctionLoad, arguments, this::handleGlideStringResponse);
    }

    @Override
    public CompletableFuture<String> functionLoad(
            @NonNull String libraryCode, boolean replace, @NonNull Route route) {
        String[] arguments =
                replace ? new String[] {REPLACE.toString(), libraryCode} : new String[] {libraryCode};
        return commandManager.submitNewCommand(
                FunctionLoad, arguments, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<GlideString> functionLoad(
            @NonNull GlideString libraryCode, boolean replace, @NonNull Route route) {
        GlideString[] arguments =
                replace
                        ? new GlideString[] {gs(REPLACE.toString()), libraryCode}
                        : new GlideString[] {libraryCode};
        return commandManager.submitNewCommand(
                FunctionLoad, arguments, route, this::handleGlideStringResponse);
    }

    /** Process a <code>FUNCTION LIST</code> cluster response. */
    protected ClusterValue<Map<String, Object>[]> handleFunctionListResponse(
            Response response, Route route) {
        if (route instanceof SingleNodeRoute) {
            Map<String, Object>[] data = handleFunctionListResponse(handleArrayResponse(response));
            return ClusterValue.ofSingleValue(data);
        } else {
            // each `Object` is a `Map<String, Object>[]` actually
            Map<String, Object> info = handleMapResponse(response);
            Map<String, Map<String, Object>[]> data = new LinkedHashMap<>();
            for (var nodeInfo : info.entrySet()) {
                data.put(nodeInfo.getKey(), handleFunctionListResponse((Object[]) nodeInfo.getValue()));
            }
            return ClusterValue.ofMultiValue(data);
        }
    }

    /** Process a <code>FUNCTION LIST</code> cluster response. */
    protected ClusterValue<Map<GlideString, Object>[]> handleFunctionListResponseBinary(
            Response response, Route route) {
        if (route instanceof SingleNodeRoute) {
            Map<GlideString, Object>[] data =
                    handleFunctionListResponseBinary(handleArrayResponseBinary(response));
            return ClusterValue.ofSingleValue(data);
        } else {
            // each `Object` is a `Map<GlideString, Object>[]` actually
            Map<GlideString, Object> info = handleBinaryStringMapResponse(response);
            Map<GlideString, Map<GlideString, Object>[]> data = new LinkedHashMap<>();
            for (var nodeInfo : info.entrySet()) {
                data.put(
                        nodeInfo.getKey(), handleFunctionListResponseBinary((Object[]) nodeInfo.getValue()));
            }
            return ClusterValue.ofMultiValueBinary(data);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode ? new String[] {WITH_CODE_VALKEY_API} : new String[0],
                response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                new ArgsBuilder().addIf(WITH_CODE_VALKEY_API, withCode).toArray(),
                response -> handleFunctionListResponseBinary(handleArrayResponseBinary(response)));
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(
            @NonNull String libNamePattern, boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode
                        ? new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern, WITH_CODE_VALKEY_API}
                        : new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern},
                response -> handleFunctionListResponse(handleArrayResponse(response)));
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(
            @NonNull GlideString libNamePattern, boolean withCode) {
        return commandManager.submitNewCommand(
                FunctionList,
                new ArgsBuilder()
                        .add(LIBRARY_NAME_VALKEY_API)
                        .add(libNamePattern)
                        .addIf(WITH_CODE_VALKEY_API, withCode)
                        .toArray(),
                response -> handleFunctionListResponseBinary(handleArrayResponseBinary(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            boolean withCode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode ? new String[] {WITH_CODE_VALKEY_API} : new String[0],
                route,
                response -> handleFunctionListResponse(response, route));
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>[]>> functionListBinary(
            boolean withCode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionList,
                new ArgsBuilder().addIf(WITH_CODE_VALKEY_API, withCode).toArray(),
                route,
                response -> handleFunctionListResponseBinary(response, route));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            @NonNull String libNamePattern, boolean withCode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionList,
                withCode
                        ? new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern, WITH_CODE_VALKEY_API}
                        : new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern},
                route,
                response -> handleFunctionListResponse(response, route));
    }

    public CompletableFuture<ClusterValue<Map<GlideString, Object>[]>> functionListBinary(
            @NonNull GlideString libNamePattern, boolean withCode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionList,
                new ArgsBuilder()
                        .add(LIBRARY_NAME_VALKEY_API)
                        .add(libNamePattern)
                        .addIf(WITH_CODE_VALKEY_API, withCode)
                        .toArray(),
                route,
                response -> handleFunctionListResponseBinary(response, route));
    }

    @Override
    public CompletableFuture<String> functionFlush() {
        return commandManager.submitNewCommand(
                FunctionFlush, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FunctionFlush, new String[] {mode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionFlush, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull FlushMode mode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionFlush, new String[] {mode.toString()}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull String libName) {
        return commandManager.submitNewCommand(
                FunctionDelete, new String[] {libName}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull GlideString libName) {
        return commandManager.submitNewCommand(
                FunctionDelete, new GlideString[] {libName}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull String libName, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionDelete, new String[] {libName}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionDelete(
            @NonNull GlideString libName, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionDelete, new GlideString[] {libName}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<byte[]> functionDump() {
        return commandManager.submitNewCommand(
                FunctionDump, new GlideString[] {}, this::handleBytesOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<byte[]>> functionDump(@NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionDump,
                new GlideString[] {},
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleBytesOrNullResponse(response))
                                : ClusterValue.ofMultiValueBinary(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> functionRestore(byte @NonNull [] payload) {
        return commandManager.submitNewCommand(
                FunctionRestore, new GlideString[] {gs(payload)}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(
            byte @NonNull [] payload, @NonNull FunctionRestorePolicy policy) {
        return commandManager.submitNewCommand(
                FunctionRestore,
                new GlideString[] {gs(payload), gs(policy.toString())},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(byte @NonNull [] payload, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionRestore, new GlideString[] {gs(payload)}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionRestore(
            byte @NonNull [] payload, @NonNull FunctionRestorePolicy policy, @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionRestore,
                new GlideString[] {gs(payload), gs(policy.toString())},
                route,
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull String function) {
        return fcall(function, new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull GlideString function) {
        return fcall(function, new GlideString[0]);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
            @NonNull String function, @NonNull Route route) {
        return fcall(function, new String[0], route);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
            @NonNull GlideString function, @NonNull Route route) {
        return fcall(function, new GlideString[0], route);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull String function, @NonNull String[] arguments) {
        String[] args = concatenateArrays(new String[] {function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCall, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcall(
            @NonNull GlideString function, @NonNull GlideString[] arguments) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCall, args, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
            @NonNull String function, @NonNull String[] arguments, @NonNull Route route) {
        String[] args = concatenateArrays(new String[] {function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCall,
                args,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcall(
            @NonNull GlideString function, @NonNull GlideString[] arguments, @NonNull Route route) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCall,
                args,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleBinaryObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValueBinary(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull String function) {
        return fcallReadOnly(function, new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull GlideString function) {
        return fcallReadOnly(function, new GlideString[0]);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            @NonNull String function, @NonNull Route route) {
        return fcallReadOnly(function, new String[0], route);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            @NonNull GlideString function, @NonNull Route route) {
        return fcallReadOnly(function, new GlideString[0], route);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(
            @NonNull String function, @NonNull String[] arguments) {
        String[] args = concatenateArrays(new String[] {function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(FCallReadOnly, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(
            @NonNull GlideString function, @NonNull GlideString[] arguments) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCallReadOnly, args, this::handleBinaryObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            @NonNull String function, @NonNull String[] arguments, @NonNull Route route) {
        String[] args = concatenateArrays(new String[] {function, "0"}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCallReadOnly,
                args,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValue(handleMapResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            @NonNull GlideString function, @NonNull GlideString[] arguments, @NonNull Route route) {
        GlideString[] args =
                concatenateArrays(new GlideString[] {function, gs("0")}, arguments); // 0 - key count
        return commandManager.submitNewCommand(
                FCallReadOnly,
                args,
                route,
                response ->
                        route instanceof SingleNodeRoute
                                ? ClusterValue.ofSingleValue(handleBinaryObjectOrNullResponse(response))
                                : ClusterValue.ofMultiValueBinary(handleBinaryStringMapResponse(response)));
    }

    @Override
    public CompletableFuture<String> functionKill() {
        return commandManager.submitNewCommand(FunctionKill, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> functionKill(@NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionKill, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Object> invokeScript(@NonNull Script script, @NonNull Route route) {
        if (script.getBinaryOutput()) {
            return commandManager.submitScript(
                    script, List.of(), route, this::handleBinaryObjectOrNullResponse);
        } else {
            return commandManager.submitScript(
                    script, List.of(), route, this::handleObjectOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object> invokeScript(
            @NonNull Script script, @NonNull ScriptArgOptions options, @NonNull Route route) {
        return commandManager.submitScript(
                script,
                options.getArgs().stream().map(GlideString::gs).collect(Collectors.toList()),
                route,
                script.getBinaryOutput()
                        ? this::handleBinaryObjectOrNullResponse
                        : this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> invokeScript(
            @NonNull Script script, @NonNull ScriptArgOptionsGlideString options, @NonNull Route route) {
        return commandManager.submitScript(
                script,
                options.getArgs(),
                route,
                script.getBinaryOutput()
                        ? this::handleBinaryObjectOrNullResponse
                        : this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Boolean[]> scriptExists(@NonNull String[] sha1s) {
        return commandManager.submitNewCommand(
                ScriptExists, sha1s, response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    @Override
    public CompletableFuture<Boolean[]> scriptExists(@NonNull GlideString[] sha1s) {
        return commandManager.submitNewCommand(
                ScriptExists, sha1s, response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    @Override
    public CompletableFuture<Boolean[]> scriptExists(@NonNull String[] sha1s, @NonNull Route route) {
        return commandManager.submitNewCommand(
                ScriptExists,
                sha1s,
                route,
                response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    @Override
    public CompletableFuture<Boolean[]> scriptExists(
            @NonNull GlideString[] sha1s, @NonNull Route route) {
        return commandManager.submitNewCommand(
                ScriptExists,
                sha1s,
                route,
                response -> castArray(handleArrayResponse(response), Boolean.class));
    }

    @Override
    public CompletableFuture<String> scriptFlush() {
        return commandManager.submitNewCommand(ScriptFlush, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> scriptFlush(@NonNull FlushMode flushMode) {
        return commandManager.submitNewCommand(
                ScriptFlush, new String[] {flushMode.toString()}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> scriptFlush(@NonNull Route route) {
        return commandManager.submitNewCommand(
                ScriptFlush, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> scriptFlush(@NonNull FlushMode flushMode, @NonNull Route route) {
        return commandManager.submitNewCommand(
                ScriptFlush, new String[] {flushMode.toString()}, route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> scriptKill() {
        return commandManager.submitNewCommand(ScriptKill, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> scriptKill(@NonNull Route route) {
        return commandManager.submitNewCommand(
                ScriptKill, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats() {
        return commandManager.submitNewCommand(
                FunctionStats, new String[0], response -> handleFunctionStatsResponse(response, false));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<GlideString, Map<GlideString, Object>>>>
            functionStatsBinary() {
        return commandManager.submitNewCommand(
                FunctionStats,
                new GlideString[0],
                response -> handleFunctionStatsBinaryResponse(response, false));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats(
            @NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionStats,
                new String[0],
                route,
                response -> handleFunctionStatsResponse(response, route instanceof SingleNodeRoute));
    }

    @Override
    public CompletableFuture<ClusterValue<Map<GlideString, Map<GlideString, Object>>>>
            functionStatsBinary(@NonNull Route route) {
        return commandManager.submitNewCommand(
                FunctionStats,
                new GlideString[0],
                route,
                response -> handleFunctionStatsBinaryResponse(response, route instanceof SingleNodeRoute));
    }

    public CompletableFuture<String> publish(
            @NonNull String message, @NonNull String channel, boolean sharded) {
        if (!sharded) {
            return publish(message, channel);
        }

        return commandManager.submitNewCommand(
                SPublish,
                new String[] {channel, message},
                response -> {
                    // Check, but ignore the number - it is never valid. A GLIDE bug/limitation TODO
                    handleLongResponse(response);
                    return OK;
                });
    }

    @Override
    public CompletableFuture<String> publish(
            @NonNull GlideString message, @NonNull GlideString channel, boolean sharded) {
        if (!sharded) {
            return publish(message, channel);
        }

        return commandManager.submitNewCommand(
                SPublish,
                new GlideString[] {channel, message},
                response -> {
                    // Check, but ignore the number - it is never valid. A GLIDE bug/limitation TODO
                    handleLongResponse(response);
                    return OK;
                });
    }

    @Override
    public CompletableFuture<String[]> pubsubShardChannels() {
        return commandManager.submitNewCommand(
                PubSubShardChannels,
                new String[0],
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubShardChannelsBinary() {
        return commandManager.submitNewCommand(
                PubSubShardChannels,
                new GlideString[0],
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<String[]> pubsubShardChannels(@NonNull String pattern) {
        return commandManager.submitNewCommand(
                PubSubShardChannels,
                new String[] {pattern},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<GlideString[]> pubsubShardChannels(@NonNull GlideString pattern) {
        return commandManager.submitNewCommand(
                PubSubShardChannels,
                new GlideString[] {pattern},
                response -> castArray(handleArrayResponseBinary(response), GlideString.class));
    }

    @Override
    public CompletableFuture<Map<String, Long>> pubsubShardNumSub(@NonNull String[] channels) {
        return commandManager.submitNewCommand(PubSubShardNumSub, channels, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<GlideString, Long>> pubsubShardNumSub(
            @NonNull GlideString[] channels) {
        return commandManager.submitNewCommand(
                PubSubShardNumSub, channels, this::handleBinaryStringMapResponse);
    }

    @Override
    public CompletableFuture<String> unwatch(@NonNull Route route) {
        return commandManager.submitNewCommand(
                UnWatch, new String[0], route, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> unwatch() {
        return commandManager.submitNewCommand(UnWatch, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> randomKey(@NonNull Route route) {
        return commandManager.submitNewCommand(
                RandomKey, new String[0], route, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> randomKeyBinary(@NonNull Route route) {
        return commandManager.submitNewCommand(
                RandomKey, new GlideString[0], route, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> randomKey() {
        return commandManager.submitNewCommand(
                RandomKey, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> randomKeyBinary() {
        return commandManager.submitNewCommand(
                RandomKey, new GlideString[0], this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(ClusterScanCursor cursor) {
        return commandManager
                .submitClusterScan(cursor, ScanOptions.builder().build(), this::handleArrayResponse)
                .thenApply(
                        result -> new Object[] {new NativeClusterScanCursor(result[0].toString()), result[1]});
    }

    @Override
    public CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor) {
        return commandManager
                .submitClusterScan(cursor, ScanOptions.builder().build(), this::handleArrayResponseBinary)
                .thenApply(
                        result -> new Object[] {new NativeClusterScanCursor(result[0].toString()), result[1]});
    }

    @Override
    public CompletableFuture<Object[]> scan(ClusterScanCursor cursor, ScanOptions options) {
        return commandManager
                .submitClusterScan(cursor, options, this::handleArrayResponse)
                .thenApply(
                        result -> new Object[] {new NativeClusterScanCursor(result[0].toString()), result[1]});
    }

    @Override
    public CompletableFuture<Object[]> scanBinary(ClusterScanCursor cursor, ScanOptions options) {
        return commandManager
                .submitClusterScan(cursor, options, this::handleArrayResponseBinary)
                .thenApply(
                        result -> new Object[] {new NativeClusterScanCursor(result[0].toString()), result[1]});
    }

    /** A {@link ClusterScanCursor} implementation for interacting with the Rust layer. */
    private static final class NativeClusterScanCursor
            implements CommandManager.ClusterScanCursorDetail {

        private final String cursorHandle;
        private final boolean isFinished;
        private boolean isClosed = false;

        // This is for internal use only.
        public NativeClusterScanCursor(@NonNull String cursorHandle) {
            this.cursorHandle = cursorHandle;
            this.isFinished = ClusterScanCursorResolver.FINISHED_CURSOR_HANDLE.equals(cursorHandle);
        }

        @Override
        public String getCursorHandle() {
            return cursorHandle;
        }

        @Override
        public boolean isFinished() {
            return isFinished;
        }

        @Override
        public void releaseCursorHandle() {
            internalClose();
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                // Release the native cursor
                this.internalClose();
            } finally {
                super.finalize();
            }
        }

        private void internalClose() {
            if (!isClosed) {
                try {
                    ClusterScanCursorResolver.releaseNativeCursor(cursorHandle);
                } catch (Exception ex) {
                    Logger.log(
                            Logger.Level.ERROR,
                            "ClusterScanCursor",
                            () -> "Error releasing cursor " + cursorHandle,
                            ex);
                } finally {
                    // Mark the cursor as closed to avoid double-free (if close() gets called more than once).
                    isClosed = true;
                }
            }
        }
    }
}
