/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.models.protobuf.command_request.CommandRequestOuterClass.RequestType;
import glide.api.models.Batch;
import glide.api.models.ClusterBatch;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.batch.BatchOptions;
import glide.api.models.commands.batch.ClusterBatchOptions;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.exceptions.GlideException;
import io.valkey.glide.jni.client.GlideJniClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Service responsible for executing commands via direct JNI integration with glide-core.
 * This is a complete replacement of the UDS-based CommandManager with protobuf elimination.
 * 
 * REVOLUTIONARY CHANGE: No more protobuf Response objects or response handlers!
 * Commands return native Java objects directly via JNI.
 */
@RequiredArgsConstructor
public class CommandManager {

    /** JNI client for direct integration with glide-core. */
    private final GlideJniClient jniClient;

    /** Command specifications with expected return types. */
    private static final Map<RequestType, CommandSpec> COMMAND_SPECS;

    static {
        Map<RequestType, CommandSpec> specs = new HashMap<>();
        
        // String commands
        specs.put(RequestType.Get, new CommandSpec("GET", ReturnType.STRING));
        specs.put(RequestType.Set, new CommandSpec("SET", ReturnType.STRING));
        specs.put(RequestType.Append, new CommandSpec("APPEND", ReturnType.LONG));
        specs.put(RequestType.Decr, new CommandSpec("DECR", ReturnType.LONG));
        specs.put(RequestType.DecrBy, new CommandSpec("DECRBY", ReturnType.LONG));
        specs.put(RequestType.GetDel, new CommandSpec("GETDEL", ReturnType.STRING));
        specs.put(RequestType.GetEx, new CommandSpec("GETEX", ReturnType.STRING));
        specs.put(RequestType.GetRange, new CommandSpec("GETRANGE", ReturnType.STRING));
        specs.put(RequestType.Incr, new CommandSpec("INCR", ReturnType.LONG));
        specs.put(RequestType.IncrBy, new CommandSpec("INCRBY", ReturnType.LONG));
        specs.put(RequestType.IncrByFloat, new CommandSpec("INCRBYFLOAT", ReturnType.DOUBLE));
        specs.put(RequestType.LCS, new CommandSpec("LCS", ReturnType.OBJECT));
        specs.put(RequestType.MGet, new CommandSpec("MGET", ReturnType.ARRAY));
        specs.put(RequestType.MSet, new CommandSpec("MSET", ReturnType.STRING));
        specs.put(RequestType.MSetNX, new CommandSpec("MSETNX", ReturnType.BOOLEAN));
        specs.put(RequestType.SetRange, new CommandSpec("SETRANGE", ReturnType.LONG));
        specs.put(RequestType.Strlen, new CommandSpec("STRLEN", ReturnType.LONG));

        // Hash commands
        specs.put(RequestType.HDel, new CommandSpec("HDEL", ReturnType.LONG));
        specs.put(RequestType.HExists, new CommandSpec("HEXISTS", ReturnType.BOOLEAN));
        specs.put(RequestType.HGet, new CommandSpec("HGET", ReturnType.STRING));
        specs.put(RequestType.HGetAll, new CommandSpec("HGETALL", ReturnType.ARRAY));
        specs.put(RequestType.HIncrBy, new CommandSpec("HINCRBY", ReturnType.LONG));
        specs.put(RequestType.HIncrByFloat, new CommandSpec("HINCRBYFLOAT", ReturnType.DOUBLE));
        specs.put(RequestType.HKeys, new CommandSpec("HKEYS", ReturnType.ARRAY));
        specs.put(RequestType.HLen, new CommandSpec("HLEN", ReturnType.LONG));
        specs.put(RequestType.HMGet, new CommandSpec("HMGET", ReturnType.ARRAY));
        specs.put(RequestType.HRandField, new CommandSpec("HRANDFIELD", ReturnType.OBJECT));
        specs.put(RequestType.HScan, new CommandSpec("HSCAN", ReturnType.ARRAY));
        specs.put(RequestType.HSet, new CommandSpec("HSET", ReturnType.LONG));
        specs.put(RequestType.HSetNX, new CommandSpec("HSETNX", ReturnType.BOOLEAN));
        specs.put(RequestType.HStrlen, new CommandSpec("HSTRLEN", ReturnType.LONG));
        specs.put(RequestType.HVals, new CommandSpec("HVALS", ReturnType.ARRAY));

        // List commands
        specs.put(RequestType.BLMove, new CommandSpec("BLMOVE", ReturnType.STRING));
        specs.put(RequestType.BLPop, new CommandSpec("BLPOP", ReturnType.ARRAY));
        specs.put(RequestType.BRPop, new CommandSpec("BRPOP", ReturnType.ARRAY));
        specs.put(RequestType.LIndex, new CommandSpec("LINDEX", ReturnType.STRING));
        specs.put(RequestType.LInsert, new CommandSpec("LINSERT", ReturnType.LONG));
        specs.put(RequestType.LLen, new CommandSpec("LLEN", ReturnType.LONG));
        specs.put(RequestType.LMove, new CommandSpec("LMOVE", ReturnType.STRING));
        specs.put(RequestType.LPop, new CommandSpec("LPOP", ReturnType.STRING));
        specs.put(RequestType.LPos, new CommandSpec("LPOS", ReturnType.LONG));
        specs.put(RequestType.LPush, new CommandSpec("LPUSH", ReturnType.LONG));
        specs.put(RequestType.LPushX, new CommandSpec("LPUSHX", ReturnType.LONG));
        specs.put(RequestType.LRange, new CommandSpec("LRANGE", ReturnType.ARRAY));
        specs.put(RequestType.LRem, new CommandSpec("LREM", ReturnType.LONG));
        specs.put(RequestType.LSet, new CommandSpec("LSET", ReturnType.STRING));
        specs.put(RequestType.LTrim, new CommandSpec("LTRIM", ReturnType.STRING));
        specs.put(RequestType.RPop, new CommandSpec("RPOP", ReturnType.STRING));
        specs.put(RequestType.RPush, new CommandSpec("RPUSH", ReturnType.LONG));
        specs.put(RequestType.RPushX, new CommandSpec("RPUSHX", ReturnType.LONG));

        // Set commands
        specs.put(RequestType.SAdd, new CommandSpec("SADD", ReturnType.LONG));
        specs.put(RequestType.SCard, new CommandSpec("SCARD", ReturnType.LONG));
        specs.put(RequestType.SDiff, new CommandSpec("SDIFF", ReturnType.ARRAY));
        specs.put(RequestType.SDiffStore, new CommandSpec("SDIFFSTORE", ReturnType.LONG));
        specs.put(RequestType.SInter, new CommandSpec("SINTER", ReturnType.ARRAY));
        specs.put(RequestType.SInterStore, new CommandSpec("SINTERSTORE", ReturnType.LONG));
        specs.put(RequestType.SIsMember, new CommandSpec("SISMEMBER", ReturnType.BOOLEAN));
        specs.put(RequestType.SMembers, new CommandSpec("SMEMBERS", ReturnType.ARRAY));
        specs.put(RequestType.SMove, new CommandSpec("SMOVE", ReturnType.BOOLEAN));
        specs.put(RequestType.SPop, new CommandSpec("SPOP", ReturnType.STRING));
        specs.put(RequestType.SRandMember, new CommandSpec("SRANDMEMBER", ReturnType.STRING));
        specs.put(RequestType.SRem, new CommandSpec("SREM", ReturnType.LONG));
        specs.put(RequestType.SUnion, new CommandSpec("SUNION", ReturnType.ARRAY));
        specs.put(RequestType.SUnionStore, new CommandSpec("SUNIONSTORE", ReturnType.LONG));

        // Sorted Set commands
        specs.put(RequestType.BZPopMax, new CommandSpec("BZPOPMAX", ReturnType.ARRAY));
        specs.put(RequestType.BZPopMin, new CommandSpec("BZPOPMIN", ReturnType.ARRAY));
        specs.put(RequestType.ZAdd, new CommandSpec("ZADD", ReturnType.LONG));
        specs.put(RequestType.ZCard, new CommandSpec("ZCARD", ReturnType.LONG));
        specs.put(RequestType.ZCount, new CommandSpec("ZCOUNT", ReturnType.LONG));
        specs.put(RequestType.ZDiff, new CommandSpec("ZDIFF", ReturnType.ARRAY));
        specs.put(RequestType.ZDiffStore, new CommandSpec("ZDIFFSTORE", ReturnType.LONG));
        specs.put(RequestType.ZIncrBy, new CommandSpec("ZINCRBY", ReturnType.DOUBLE));
        specs.put(RequestType.ZInter, new CommandSpec("ZINTER", ReturnType.ARRAY));
        specs.put(RequestType.ZInterStore, new CommandSpec("ZINTERSTORE", ReturnType.LONG));
        specs.put(RequestType.ZLexCount, new CommandSpec("ZLEXCOUNT", ReturnType.LONG));
        specs.put(RequestType.ZMScore, new CommandSpec("ZMSCORE", ReturnType.ARRAY));
        specs.put(RequestType.ZPopMax, new CommandSpec("ZPOPMAX", ReturnType.ARRAY));
        specs.put(RequestType.ZPopMin, new CommandSpec("ZPOPMIN", ReturnType.ARRAY));
        specs.put(RequestType.ZRandMember, new CommandSpec("ZRANDMEMBER", ReturnType.STRING));
        specs.put(RequestType.ZRange, new CommandSpec("ZRANGE", ReturnType.ARRAY));
        specs.put(RequestType.ZRangeByLex, new CommandSpec("ZRANGEBYLEX", ReturnType.ARRAY));
        specs.put(RequestType.ZRangeByScore, new CommandSpec("ZRANGEBYSCORE", ReturnType.ARRAY));
        specs.put(RequestType.ZRank, new CommandSpec("ZRANK", ReturnType.LONG));
        specs.put(RequestType.ZRem, new CommandSpec("ZREM", ReturnType.LONG));
        specs.put(RequestType.ZRemRangeByLex, new CommandSpec("ZREMRANGEBYLEX", ReturnType.LONG));
        specs.put(RequestType.ZRemRangeByRank, new CommandSpec("ZREMRANGEBYRANK", ReturnType.LONG));
        specs.put(RequestType.ZRemRangeByScore, new CommandSpec("ZREMRANGEBYSCORE", ReturnType.LONG));
        specs.put(RequestType.ZRevRange, new CommandSpec("ZREVRANGE", ReturnType.ARRAY));
        specs.put(RequestType.ZRevRangeByLex, new CommandSpec("ZREVRANGEBYLEX", ReturnType.ARRAY));
        specs.put(RequestType.ZRevRangeByScore, new CommandSpec("ZREVRANGEBYSCORE", ReturnType.ARRAY));
        specs.put(RequestType.ZRevRank, new CommandSpec("ZREVRANK", ReturnType.LONG));
        specs.put(RequestType.ZScore, new CommandSpec("ZSCORE", ReturnType.DOUBLE));
        specs.put(RequestType.ZUnion, new CommandSpec("ZUNION", ReturnType.ARRAY));
        specs.put(RequestType.ZUnionStore, new CommandSpec("ZUNIONSTORE", ReturnType.LONG));

        // Generic commands
        specs.put(RequestType.Del, new CommandSpec("DEL", ReturnType.LONG));
        specs.put(RequestType.Exists, new CommandSpec("EXISTS", ReturnType.LONG));
        specs.put(RequestType.Expire, new CommandSpec("EXPIRE", ReturnType.BOOLEAN));
        specs.put(RequestType.ExpireAt, new CommandSpec("EXPIREAT", ReturnType.BOOLEAN));
        specs.put(RequestType.ExpireTime, new CommandSpec("EXPIRETIME", ReturnType.LONG));
        specs.put(RequestType.Keys, new CommandSpec("KEYS", ReturnType.ARRAY));
        specs.put(RequestType.Move, new CommandSpec("MOVE", ReturnType.BOOLEAN));
        specs.put(RequestType.Persist, new CommandSpec("PERSIST", ReturnType.BOOLEAN));
        specs.put(RequestType.PExpire, new CommandSpec("PEXPIRE", ReturnType.BOOLEAN));
        specs.put(RequestType.PExpireAt, new CommandSpec("PEXPIREAT", ReturnType.BOOLEAN));
        specs.put(RequestType.PExpireTime, new CommandSpec("PEXPIRETIME", ReturnType.LONG));
        specs.put(RequestType.PTTL, new CommandSpec("PTTL", ReturnType.LONG));
        specs.put(RequestType.RandomKey, new CommandSpec("RANDOMKEY", ReturnType.STRING));
        specs.put(RequestType.Rename, new CommandSpec("RENAME", ReturnType.STRING));
        specs.put(RequestType.RenameNX, new CommandSpec("RENAMENX", ReturnType.BOOLEAN));
        specs.put(RequestType.Touch, new CommandSpec("TOUCH", ReturnType.LONG));
        specs.put(RequestType.TTL, new CommandSpec("TTL", ReturnType.LONG));
        specs.put(RequestType.Type, new CommandSpec("TYPE", ReturnType.STRING));
        specs.put(RequestType.Unlink, new CommandSpec("UNLINK", ReturnType.LONG));

        // Connection commands
        specs.put(RequestType.Echo, new CommandSpec("ECHO", ReturnType.STRING));
        specs.put(RequestType.Ping, new CommandSpec("PING", ReturnType.STRING));
        specs.put(RequestType.Select, new CommandSpec("SELECT", ReturnType.STRING));

        // TODO: Add remaining 100+ commands for complete coverage
        // This covers the most commonly used commands to get basic functionality working
        
        COMMAND_SPECS = Collections.unmodifiableMap(specs);
    }

    /**
     * Command specification with return type information
     */
    private static class CommandSpec {
        final String command;
        final ReturnType returnType;
        
        CommandSpec(String command, ReturnType returnType) {
            this.command = command;
            this.returnType = returnType;
        }
    }

    /**
     * Expected return types from commands
     */
    enum ReturnType { 
        STRING, LONG, DOUBLE, BOOLEAN, ARRAY, OBJECT 
    }

    /**
     * Internal interface for exposing implementation details about a ClusterScanCursor. This is an
     * interface so that it can be mocked in tests.
     */
    public interface ClusterScanCursorDetail extends ClusterScanCursor {
        /**
         * Returns the handle String representing the cursor.
         *
         * @return the handle String representing the cursor.
         */
        String getCursorHandle();
    }

    // ==================== NEW PROTOBUF-FREE API ====================
    // Direct typed execution methods - no response handlers needed!

    /**
     * Execute a command expecting a String result.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with String result
     */
    public CompletableFuture<String> executeStringCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        return jniClient.executeStringCommand(spec.command, arguments);
    }

    /**
     * Execute a command expecting a Long result.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with Long result
     */
    public CompletableFuture<Long> executeLongCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        return jniClient.executeLongCommand(spec.command, arguments);
    }

    /**
     * Execute a command expecting a Double result.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with Double result
     */
    public CompletableFuture<Double> executeDoubleCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        return jniClient.executeDoubleCommand(spec.command, arguments);
    }

    /**
     * Execute a command expecting a Boolean result.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with Boolean result
     */
    public CompletableFuture<Boolean> executeBooleanCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        return jniClient.executeBooleanCommand(spec.command, arguments);
    }

    /**
     * Execute a command expecting an Object[] result.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with Object[] result
     */
    public CompletableFuture<Object[]> executeArrayCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        return jniClient.executeArrayCommand(spec.command, arguments);
    }

    /**
     * Execute a command expecting any Object result.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with Object result
     */
    public CompletableFuture<Object> executeObjectCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        return jniClient.executeObjectCommand(spec.command, arguments);
    }

    /**
     * Execute a command with automatic type detection based on RequestType.
     * This provides backward compatibility with existing code patterns.
     *
     * @param requestType Valkey command type
     * @param arguments Command arguments
     * @return CompletableFuture with correctly typed result
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> executeCommand(
            RequestType requestType, String[] arguments) {
        CommandSpec spec = getCommandSpec(requestType);
        
        switch (spec.returnType) {
            case STRING:
                return (CompletableFuture<T>) executeStringCommand(requestType, arguments);
            case LONG:
                return (CompletableFuture<T>) executeLongCommand(requestType, arguments);
            case DOUBLE:
                return (CompletableFuture<T>) executeDoubleCommand(requestType, arguments);
            case BOOLEAN:
                return (CompletableFuture<T>) executeBooleanCommand(requestType, arguments);
            case ARRAY:
                return (CompletableFuture<T>) executeArrayCommand(requestType, arguments);
            case OBJECT:
            default:
                return (CompletableFuture<T>) executeObjectCommand(requestType, arguments);
        }
    }

    // ==================== LEGACY API COMPATIBILITY ====================
    // These methods maintain compatibility with existing BaseClient code
    // but internally use the new protobuf-free implementation

    /**
     * Build a command and send - LEGACY COMPATIBILITY METHOD
     * This method maintains API compatibility but eliminates protobuf usage.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param responseHandler The handler for the response object - IGNORED in JNI implementation
     * @return A result promise of type T
     * @deprecated Use direct typed methods instead
     */
    @Deprecated
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        // Execute command directly via JNI, ignore the response handler
        return executeCommand(requestType, arguments);
    }

    /**
     * Build a command and send - LEGACY COMPATIBILITY METHOD
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param responseHandler The handler for the response object - IGNORED in JNI implementation
     * @return A result promise of type T
     * @deprecated Use direct typed methods instead
     */
    @Deprecated
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        String[] stringArgs = convertGlideStringsToStrings(arguments);
        return executeCommand(requestType, stringArgs);
    }

    /**
     * Build a command and send with routing - LEGACY COMPATIBILITY METHOD
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param route Command routing parameters
     * @param responseHandler The handler for the response object - IGNORED in JNI implementation
     * @return A result promise of type T
     * @deprecated Use direct typed methods instead
     */
    @Deprecated
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            Route route,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        // TODO: Implement routing support in JNI client
        // For now, execute without routing
        return executeCommand(requestType, arguments);
    }

    /**
     * Build a command and send with routing - LEGACY COMPATIBILITY METHOD
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param route Command routing parameters
     * @param responseHandler The handler for the response object - IGNORED in JNI implementation
     * @return A result promise of type T
     * @deprecated Use direct typed methods instead
     */
    @Deprecated
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        String[] stringArgs = convertGlideStringsToStrings(arguments);
        return executeCommand(requestType, stringArgs);
    }

    // ==================== UNSUPPORTED OPERATIONS (TODO) ====================
    // These operations are not yet implemented in the JNI client

    /**
     * Build a Batch and send via JNI - NOT YET IMPLEMENTED
     */
    public <T> CompletableFuture<T> submitNewBatch(
            @NonNull Batch batch,
            boolean raiseOnError,
            @NonNull Optional<BatchOptions> options,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Batch operations not yet implemented in JNI"));
        return future;
    }

    /**
     * Execute a script via JNI - NOT YET IMPLEMENTED
     */
    public <T> CompletableFuture<T> submitScript(
            @NonNull Script script,
            @NonNull String scriptHash,
            String[] keys,
            String[] args,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Script execution not yet implemented in JNI"));
        return future;
    }

    /**
     * Execute a script via JNI - NOT YET IMPLEMENTED
     */
    public <T> CompletableFuture<T> submitScript(
            @NonNull Script script,
            @NonNull String scriptHash,
            GlideString[] keys,
            GlideString[] args,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Script execution not yet implemented in JNI"));
        return future;
    }

    /**
     * Build a cluster batch and send via JNI - NOT YET IMPLEMENTED
     */
    public <T> CompletableFuture<T> submitNewBatch(
            @NonNull ClusterBatch batch,
            boolean raiseOnError,
            @NonNull Optional<ClusterBatchOptions> options,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Cluster batch operations not yet implemented in JNI"));
        return future;
    }

    /**
     * Submit cluster scan via JNI - NOT YET IMPLEMENTED
     */
    public <T> CompletableFuture<T> submitClusterScan(
            @NonNull ClusterScanCursor cursor,
            @NonNull ScanOptions options,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Cluster scan not yet implemented in JNI"));
        return future;
    }

    /**
     * Submit password update via JNI - NOT YET IMPLEMENTED
     */
    public <T> CompletableFuture<T> submitPasswordUpdate(
            @NonNull String password,
            GlideExceptionCheckedFunction<?, T> responseHandler) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Password update not yet implemented in JNI"));
        return future;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get command specification for a RequestType.
     */
    private CommandSpec getCommandSpec(RequestType requestType) {
        CommandSpec spec = COMMAND_SPECS.get(requestType);
        if (spec == null) {
            throw new UnsupportedOperationException("Command not yet mapped: " + requestType);
        }
        return spec;
    }

    /**
     * Convert GlideString array to String array.
     */
    private String[] convertGlideStringsToStrings(GlideString[] glideStrings) {
        String[] strings = new String[glideStrings.length];
        for (int i = 0; i < glideStrings.length; i++) {
            strings[i] = glideStrings[i].toString();
        }
        return strings;
    }
}