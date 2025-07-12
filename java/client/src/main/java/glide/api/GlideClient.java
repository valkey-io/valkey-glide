/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static command_request.CommandRequestOuterClass.RequestType.ClientGetName;
import static command_request.CommandRequestOuterClass.RequestType.ClientId;
import static command_request.CommandRequestOuterClass.RequestType.ConfigGet;
import static command_request.CommandRequestOuterClass.RequestType.ConfigResetStat;
import static command_request.CommandRequestOuterClass.RequestType.ConfigRewrite;
import static command_request.CommandRequestOuterClass.RequestType.ConfigSet;
import static command_request.CommandRequestOuterClass.RequestType.Copy;
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
import static command_request.CommandRequestOuterClass.RequestType.Move;
import static command_request.CommandRequestOuterClass.RequestType.Ping;
import static command_request.CommandRequestOuterClass.RequestType.RandomKey;
import static command_request.CommandRequestOuterClass.RequestType.Scan;
import static command_request.CommandRequestOuterClass.RequestType.ScriptExists;
import static command_request.CommandRequestOuterClass.RequestType.ScriptFlush;
import static command_request.CommandRequestOuterClass.RequestType.ScriptKill;
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

    /**
     * A constructor. Use {@link #createClient} to get a client. Made protected to simplify testing.
     */
    protected GlideClient(ClientBuilder builder) {
        super(builder);
    }

    /**
     * Creates a new {@link GlideClient} instance and establishes a connection to a standalone Valkey
     * server.
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
        return createClient(config, GlideClient::new);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull String[] args) {
        return commandManager.executeObjectCommand(CustomCommand, args);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull GlideString[] args) {
        return commandManager.executeObjectCommand(
                CustomCommand, args);
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
        return commandManager.executeStringCommand(Ping, new String[0]);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String message) {
        return commandManager.executeStringCommand(
                Ping, new String[] {message});
    }

    @Override
    public CompletableFuture<GlideString> ping(@NonNull GlideString message) {
        return commandManager.executeStringCommand(
                Ping, new String[] {message.toString()})
                .thenApply(result -> result != null ? GlideString.of(result) : null);
    }

    @Override
    public CompletableFuture<String> info() {
        return commandManager.executeStringCommand(Info, new String[0]);
    }

    @Override
    public CompletableFuture<String> info(@NonNull Section[] sections) {
        return commandManager.executeStringCommand(
                Info,
                Stream.of(sections).map(Enum::toString).toArray(String[]::new));
    }

    @Override
    public CompletableFuture<String> select(long index) {
        return commandManager.executeStringCommand(
                Select, new String[] {Long.toString(index)});
    }

    @Override
    public CompletableFuture<Long> clientId() {
        return commandManager.executeLongCommand(ClientId, new String[0]);
    }

    @Override
    public CompletableFuture<String> clientGetName() {
        return commandManager.executeStringCommand(
                ClientGetName, new String[0]);
    }

    @Override
    public CompletableFuture<String> configRewrite() {
        return commandManager.executeStringCommand(
                ConfigRewrite, new String[0]);
    }

    @Override
    public CompletableFuture<String> configResetStat() {
        return commandManager.executeStringCommand(
                ConfigResetStat, new String[0]);
    }

    @Override
    public CompletableFuture<Map<String, String>> configGet(@NonNull String[] parameters) {
        return commandManager.executeObjectCommand(ConfigGet, parameters)
                .thenApply(result -> result != null ? castToMapOfStringToString(result) : null);
    }

    @Override
    public CompletableFuture<String> configSet(@NonNull Map<String, String> parameters) {
        return commandManager.executeStringCommand(
                ConfigSet, convertMapToKeyValueStringArray(parameters));
    }

    @Override
    public CompletableFuture<String> echo(@NonNull String message) {
        return commandManager.executeStringCommand(
                Echo, new String[] {message});
    }

    @Override
    public CompletableFuture<GlideString> echo(@NonNull GlideString message) {
        return commandManager.executeStringCommand(
                Echo, new String[] {message.toString()})
                .thenApply(result -> result != null ? GlideString.of(result) : null);
    }

    @Override
    public CompletableFuture<String[]> time() {
        return commandManager.executeArrayCommand(
                Time, new String[0])
                .thenApply(result -> result != null ? castToStringArray(result) : null);
    }

    @Override
    public CompletableFuture<Long> lastsave() {
        return commandManager.executeLongCommand(LastSave, new String[0]);
    }

    @Override
    public CompletableFuture<String> flushall() {
        return commandManager.executeStringCommand(FlushAll, new String[0]);
    }

    @Override
    public CompletableFuture<String> flushall(@NonNull FlushMode mode) {
        return commandManager.executeStringCommand(
                FlushAll, new String[] {mode.toString()});
    }

    @Override
    public CompletableFuture<String> flushdb() {
        return commandManager.executeStringCommand(FlushDB, new String[0]);
    }

    @Override
    public CompletableFuture<String> flushdb(@NonNull FlushMode mode) {
        return commandManager.executeStringCommand(
                FlushDB, new String[] {mode.toString()});
    }

    @Override
    public CompletableFuture<String> lolwut() {
        return commandManager.executeStringCommand(Lolwut, new String[0]);
    }

    @Override
    public CompletableFuture<String> lolwut(int @NonNull [] parameters) {
        String[] arguments =
                Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new);
        return commandManager.executeStringCommand(Lolwut, arguments);
    }

    @Override
    public CompletableFuture<String> lolwut(int version) {
        return commandManager.executeStringCommand(
                Lolwut,
                new String[] {VERSION_VALKEY_API, Integer.toString(version)});
    }

    @Override
    public CompletableFuture<String> lolwut(int version, int @NonNull [] parameters) {
        String[] arguments =
                concatenateArrays(
                        new String[] {VERSION_VALKEY_API, Integer.toString(version)},
                        Arrays.stream(parameters).mapToObj(Integer::toString).toArray(String[]::new));
        return commandManager.executeStringCommand(Lolwut, arguments);
    }

    @Override
    public CompletableFuture<Long> dbsize() {
        return commandManager.executeLongCommand(DBSize, new String[0]);
    }

    @Override
    public CompletableFuture<String> functionLoad(@NonNull String libraryCode, boolean replace) {
        String[] arguments =
                replace ? new String[] {REPLACE.toString(), libraryCode} : new String[] {libraryCode};
        return commandManager.executeStringCommand(FunctionLoad, arguments);
    }

    @Override
    public CompletableFuture<GlideString> functionLoad(
            @NonNull GlideString libraryCode, boolean replace) {
        GlideString[] arguments =
                replace
                        ? new GlideString[] {gs(REPLACE.toString()), libraryCode}
                        : new GlideString[] {libraryCode};
        return commandManager.executeStringCommand(
                FunctionLoad, Arrays.stream(arguments).map(GlideString::toString).toArray(String[]::new))
                .thenApply(result -> result != null ? GlideString.of(result) : null);
    }

    @Override
    public CompletableFuture<Boolean> move(@NonNull String key, long dbIndex) {
        return commandManager.executeBooleanCommand(
                Move, new String[] {key, Long.toString(dbIndex)});
    }

    @Override
    public CompletableFuture<Boolean> move(@NonNull GlideString key, long dbIndex) {
        return commandManager.executeBooleanCommand(
                Move, new String[] {key.toString(), Long.toString(dbIndex)});
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(boolean withCode) {
        return commandManager.executeArrayCommand(
                FunctionList,
                withCode ? new String[] {WITH_CODE_VALKEY_API} : new String[0])
                .thenApply(result -> result != null ? handleFunctionListResponse(result) : null);
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode) {
        return commandManager.executeArrayCommand(
                FunctionList,
                Arrays.stream(new ArgsBuilder().addIf(WITH_CODE_VALKEY_API, withCode).toArray())
                        .map(GlideString::toString).toArray(String[]::new))
                .thenApply(result -> result != null ? handleFunctionListResponseBinary(result) : null);
    }

    @Override
    public CompletableFuture<Map<String, Object>[]> functionList(
            @NonNull String libNamePattern, boolean withCode) {
        return commandManager.executeArrayCommand(
                FunctionList,
                withCode
                        ? new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern, WITH_CODE_VALKEY_API}
                        : new String[] {LIBRARY_NAME_VALKEY_API, libNamePattern})
                .thenApply(result -> result != null ? handleFunctionListResponse(result) : null);
    }

    @Override
    public CompletableFuture<Map<GlideString, Object>[]> functionListBinary(
            @NonNull GlideString libNamePattern, boolean withCode) {
        return commandManager.executeArrayCommand(
                FunctionList,
                Arrays.stream(new ArgsBuilder()
                        .add(LIBRARY_NAME_VALKEY_API)
                        .add(libNamePattern)
                        .addIf(WITH_CODE_VALKEY_API, withCode)
                        .toArray())
                        .map(GlideString::toString).toArray(String[]::new))
                .thenApply(result -> result != null ? handleFunctionListResponseBinary(result) : null);
    }

    @Override
    public CompletableFuture<String> functionFlush() {
        return commandManager.executeStringCommand(
                FunctionFlush, new String[0]);
    }

    @Override
    public CompletableFuture<String> functionFlush(@NonNull FlushMode mode) {
        return commandManager.executeStringCommand(
                FunctionFlush, new String[] {mode.toString()});
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull String libName) {
        return commandManager.executeStringCommand(
                FunctionDelete, new String[] {libName});
    }

    @Override
    public CompletableFuture<String> functionDelete(@NonNull GlideString libName) {
        return commandManager.executeStringCommand(
                FunctionDelete, new String[] {libName.toString()});
    }

    @Override
    public CompletableFuture<byte[]> functionDump() {
        return commandManager.executeObjectCommand(
                FunctionDump, new String[0])
                .thenApply(result -> result != null ? (byte[]) result : null);
    }

    @Override
    public CompletableFuture<String> functionRestore(byte @NonNull [] payload) {
        return commandManager.executeStringCommand(
                FunctionRestore, new String[] {gs(payload).toString()});
    }

    @Override
    public CompletableFuture<String> functionRestore(
            byte @NonNull [] payload, @NonNull FunctionRestorePolicy policy) {
        return commandManager.executeStringCommand(
                FunctionRestore,
                new String[] {gs(payload).toString(), policy.toString()});
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull String function) {
        return fcall(function, new String[0], new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcall(@NonNull GlideString function) {
        return fcall(function, new GlideString[0], new GlideString[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull String function) {
        return fcallReadOnly(function, new String[0], new String[0]);
    }

    @Override
    public CompletableFuture<Object> fcallReadOnly(@NonNull GlideString function) {
        return fcallReadOnly(function, new GlideString[0], new GlideString[0]);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB) {
        String[] arguments =
                new String[] {source, destination, DB_VALKEY_API, Long.toString(destinationDB)};
        return commandManager.executeBooleanCommand(Copy, arguments);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source, @NonNull GlideString destination, long destinationDB) {
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(DB_VALKEY_API), gs(Long.toString(destinationDB))
                };
        return commandManager.executeBooleanCommand(Copy, arguments);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull String source, @NonNull String destination, long destinationDB, boolean replace) {
        String[] arguments =
                new String[] {source, destination, DB_VALKEY_API, Long.toString(destinationDB)};
        if (replace) {
            arguments = ArrayUtils.add(arguments, REPLACE_VALKEY_API);
        }
        return commandManager.executeBooleanCommand(Copy, arguments);
    }

    @Override
    public CompletableFuture<Boolean> copy(
            @NonNull GlideString source,
            @NonNull GlideString destination,
            long destinationDB,
            boolean replace) {
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(DB_VALKEY_API), gs(Long.toString(destinationDB))
                };
        if (replace) {
            arguments = ArrayUtils.add(arguments, gs(REPLACE_VALKEY_API));
        }
        return commandManager.executeBooleanCommand(Copy, arguments);
    }

    @Override
    public CompletableFuture<String> functionKill() {
        return commandManager.executeStringCommand(FunctionKill, new String[0]);
    }

    @Override
    public CompletableFuture<Map<String, Map<String, Map<String, Object>>>> functionStats() {
        return commandManager.executeObjectCommand(
                FunctionStats,
                new String[0])
                .thenApply(result -> result != null ? handleFunctionStatsResponse(result, false).getMultiValue() : null);
    }

    @Override
    public CompletableFuture<Map<String, Map<GlideString, Map<GlideString, Object>>>>
            functionStatsBinary() {
        return commandManager.executeObjectCommand(
                FunctionStats,
                new String[0])
                .thenApply(result -> result != null ? handleFunctionStatsBinaryResponse(result, false).getMultiValue() : null);
    }

    @Override
    public CompletableFuture<String> unwatch() {
        return commandManager.executeStringCommand(UnWatch, new String[0]);
    }

    @Override
    public CompletableFuture<String> randomKey() {
        return commandManager.executeStringCommand(
                RandomKey, new String[0]);
    }

    @Override
    public CompletableFuture<GlideString> randomKeyBinary() {
        return commandManager.executeStringCommand(
                RandomKey, new String[0])
                .thenApply(result -> result != null ? GlideString.of(result) : null);
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull String cursor) {
        return commandManager.executeArrayCommand(Scan, new String[] {cursor});
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull GlideString cursor) {
        return commandManager.executeArrayCommand(
                Scan, new String[] {cursor.toString()});
    }

    @Override
    public CompletableFuture<Object[]> scan(@NonNull String cursor, @NonNull ScanOptions options) {
        String[] arguments = ArrayUtils.addFirst(options.toArgs(), cursor);
        return commandManager.executeArrayCommand(Scan, arguments);
    }

    @Override
    public CompletableFuture<Object[]> scan(
            @NonNull GlideString cursor, @NonNull ScanOptions options) {
        GlideString[] arguments = new ArgsBuilder().add(cursor).add(options.toArgs()).toArray();
        return commandManager.executeArrayCommand(Scan, 
                Arrays.stream(arguments).map(GlideString::toString).toArray(String[]::new));
    }

    @Override
    public CompletableFuture<Boolean[]> scriptExists(@NonNull String[] sha1s) {
        return commandManager.executeArrayCommand(
                ScriptExists, sha1s)
                .thenApply(result -> result != null ? castToBooleanArray(result) : null);
    }

    @Override
    public CompletableFuture<Boolean[]> scriptExists(@NonNull GlideString[] sha1s) {
        return commandManager.executeArrayCommand(
                ScriptExists, sha1s)
                .thenApply(result -> result != null ? castToBooleanArray(result) : null);
    }

    @Override
    public CompletableFuture<String> scriptFlush() {
        return commandManager.executeStringCommand(ScriptFlush, new String[0]);
    }

    @Override
    public CompletableFuture<String> scriptFlush(@NonNull FlushMode flushMode) {
        return commandManager.executeStringCommand(
                ScriptFlush, new String[] {flushMode.toString()});
    }

    @Override
    public CompletableFuture<String> scriptKill() {
        return commandManager.executeStringCommand(ScriptKill, new String[0]);
    }
}
