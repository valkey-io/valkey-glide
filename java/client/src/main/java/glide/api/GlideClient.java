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
import static command_request.CommandRequestOuterClass.RequestType.RandomKey;
import static command_request.CommandRequestOuterClass.RequestType.Scan;
import static command_request.CommandRequestOuterClass.RequestType.Select;
import static command_request.CommandRequestOuterClass.RequestType.Time;
import static command_request.CommandRequestOuterClass.RequestType.UnWatch;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_VALKEY_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_VALKEY_API;
import static glide.api.models.commands.function.FunctionLoadOptions.REPLACE;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;

import glide.api.commands.ConnectionManagementCommands;
import glide.api.commands.GenericCommands;
import glide.api.commands.ScriptingAndFunctionsCommands;
import glide.api.commands.ServerManagementCommands;
import glide.api.commands.TransactionsCommands;
import glide.api.models.Batch;
import glide.api.models.GlideString;
import glide.api.models.Transaction;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.batch.BatchOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Client used for connection to standalone servers.<br>
 * Use {@link #createClient} to request a client.
 *
 * @see For full documentation refer to <a
 *     href="https://github.com/valkey-io/valkey-glide/wiki/Java-Wrapper#standalone">Valkey Glide
 *     Wiki</a>.
 */
public class GlideClient extends BaseClient
        implements GenericCommands,
                ServerManagementCommands,
                ConnectionManagementCommands,
                ScriptingAndFunctionsCommands,
                TransactionsCommands {

    /** Constructor using ClientParams from BaseClient. */
    protected GlideClient(ClientBuilder builder) {
        super(builder);
    }

    /**
     * Creates a new {@link GlideClient} instance and establishes a connection to a standalone Valkey
     *
     * @param config The configuration options for the client, including server addresses,
     *     authentication credentials, TLS settings, database selection, reconnection strategy, and
     *     Pub/Sub subscriptions.
     * @return A Future that resolves to a connected {@link GlideClient} instance.
     * @remarks Use this static method to create and connect a {@link GlideClient} to a standalone
     *     Valkey server. The client will automatically handle connection establishment, including any
     *     authentication and TLS configurations.
     *     <ul>
     *       <li><b>Authentication</b>: If {@link ServerCredentials} are provided, the client will
     *           attempt to authenticate using the specified username and password.
     *       <li><b>TLS</b>: If {@link
     *           BaseClientConfiguration.BaseClientConfigurationBuilder#useTLS(boolean)} is set to
     *           <code>true</code>, the client will establish secure connections using TLS.
     *       <li><b>Reconnection Strategy</b>: The {@link BackoffStrategy} settings define how the
     *           client will attempt to reconnect in case of disconnections.
     *       <li><b>Pub/Sub Subscriptions</b>: Any channels or patterns specified in {@link
     *           StandaloneSubscriptionConfiguration} will be subscribed to upon connection.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * GlideClientConfiguration config =
     *     GlideClientConfiguration.builder()
     *         .address(node1address)
     *         .address(node2address)
     *         .useTLS(true)
     *         .readFrom(ReadFrom.PREFER_REPLICA)
     *         .credentials(credentialsConfiguration)
     *         .requestTimeout(2000)
     *         .clientName("GLIDE")
     *         .subscriptionConfiguration(
     *             StandaloneSubscriptionConfiguration.builder()
     *                 .subscription(EXACT, "notifications")
     *                 .subscription(EXACT, "news")
     *                 .callback(callback)
     *                 .build())
     *         .inflightRequestsLimit(1000)
     *         .build();
     * GlideClient client = GlideClient.createClient(config).get();
     * }</pre>
     */
    public static CompletableFuture<GlideClient> createClient(
            @NonNull GlideClientConfiguration config) {
        return BaseClient.createClient(config, GlideClient::new);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull String[] args) {
        return commandManager.submitNewCommand(CustomCommand, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull GlideString[] args) {
        return commandManager.submitNewCommand(
                CustomCommand, args, this::handleBinaryObjectOrNullResponse);
    }

    @Deprecated
    @Override
    public CompletableFuture<Object[]> exec(@NonNull Transaction transaction) {
        if (transaction.isBinaryOutput()) {
            return commandManager.submitNewBatch(
                    transaction, true, Optional.empty(), this::handleArrayOrNullResponseBinary);
        } else {
            return commandManager.submitNewBatch(
                    transaction, true, Optional.empty(), this::handleArrayOrNullResponse);
        }
    }

    @Override
    public CompletableFuture<Object[]> exec(@NonNull Batch batch, boolean raiseOnError) {
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
            @NonNull Batch batch, boolean raiseOnError, @NonNull BatchOptions options) {
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
    public CompletableFuture<String> info() {
        return commandManager.submitNewCommand(Info, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> info(@NonNull Section[] sections) {
        return commandManager.submitNewCommand(
                Info,
                Stream.of(sections).map(Enum::toString).toArray(String[]::new),
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> select(long index) {
        return commandManager.submitNewCommand(
                Select, new String[] {Long.toString(index)}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> clientId() {
        return commandManager.submitNewCommand(ClientId, new String[0], this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> clientGetName() {
        return commandManager.submitNewCommand(
                ClientGetName, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> configRewrite() {
        return commandManager.submitNewCommand(
                ConfigRewrite, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> configResetStat() {
        return commandManager.submitNewCommand(
                ConfigResetStat, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, String>> configGet(@NonNull String[] parameters) {
        return commandManager.submitNewCommand(ConfigGet, parameters, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<String> configSet(@NonNull Map<String, String> parameters) {
        return commandManager.submitNewCommand(
                ConfigSet, convertMapToKeyValueStringArray(parameters), this::handleStringResponse);
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
    public CompletableFuture<String[]> time() {
        return commandManager.submitNewCommand(
                Time, new String[0], response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Long> lastsave() {
        return commandManager.submitNewCommand(LastSave, new String[0], this::handleLongResponse);
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
    public CompletableFuture<String> flushdb() {
        return commandManager.submitNewCommand(FlushDB, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode) {
        return commandManager.submitNewCommand(
                FlushDB, new String[] {mode.toString()}, this::handleStringResponse);
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
    public CompletableFuture<Long> dbsize() {
        return commandManager.submitNewCommand(DBSize, new String[0], this::handleLongResponse);
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
    public CompletableFuture<byte[]> functionDump() {
        return commandManager.submitNewCommand(
                FunctionDump, BaseClient.EMPTY_GLIDE_STRING_ARRAY, this::handleBytesOrNullResponse);
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
    public CompletableFuture<Object> fcall(@NonNull String function) {
        return fcall(function, new String[0], new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull GlideString function) {
        return fcall(
                function, BaseClient.EMPTY_GLIDE_STRING_ARRAY, BaseClient.EMPTY_GLIDE_STRING_ARRAY);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull String function) {
        return fcallReadOnly(function, new String[0], new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull GlideString function) {
        return fcallReadOnly(
                function, BaseClient.EMPTY_GLIDE_STRING_ARRAY, BaseClient.EMPTY_GLIDE_STRING_ARRAY);
    }

    @Override
    public CompletableFuture<String> functionKill() {
        return commandManager.submitNewCommand(FunctionKill, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, Map<String, Object>>>> functionStats() {
        return commandManager.submitNewCommand(
                FunctionStats,
                new String[0],
                response -> handleFunctionStatsResponse(response, false).getMultiValue());
    }

    @Override
    public CompletableFuture<Map<String, Map<GlideString, Map<GlideString, Object>>>>
            functionStatsBinary() {
        return commandManager.submitNewCommand(
                FunctionStats,
                BaseClient.EMPTY_GLIDE_STRING_ARRAY,
                response -> handleFunctionStatsBinaryResponse(response, false).getMultiValue());
    }

    @Override
    public CompletableFuture<String> unwatch() {
        return commandManager.submitNewCommand(UnWatch, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> randomKey() {
        return commandManager.submitNewCommand(
                RandomKey, new String[0], this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<GlideString> randomKeyBinary() {
        return commandManager.submitNewCommand(
                RandomKey, BaseClient.EMPTY_GLIDE_STRING_ARRAY, this::handleGlideStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull String cursor) {
        return commandManager.submitNewCommand(Scan, new String[] {cursor}, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull GlideString cursor) {
        return commandManager.submitNewCommand(
                Scan, new GlideString[] {cursor}, this::handleArrayResponseBinary);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull String cursor, @NonNull ScanOptions options) {
        String[] arguments = ArrayUtils.addFirst(options.toArgs(), cursor);
        return commandManager.submitNewCommand(Scan, arguments, this::handleArrayResponse);
    }

    @Override
    public CompletableFuture<Object[]> scan(
            @NonNull GlideString cursor, @NonNull ScanOptions options) {
        GlideString[] arguments = new ArgsBuilder().add(cursor).add(options.toArgs()).toArray();
        return commandManager.submitNewCommand(Scan, arguments, this::handleArrayResponseBinary);
    }
}
