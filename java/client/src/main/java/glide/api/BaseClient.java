/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.ffi.resolvers.SocketListenerResolver.getSocket;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static redis_request.RedisRequestOuterClass.RequestType.Blpop;
import static redis_request.RedisRequestOuterClass.RequestType.Brpop;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.GetRange;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.HLen;
import static redis_request.RedisRequestOuterClass.RequestType.HSetNX;
import static redis_request.RedisRequestOuterClass.RequestType.HashDel;
import static redis_request.RedisRequestOuterClass.RequestType.HashExists;
import static redis_request.RedisRequestOuterClass.RequestType.HashGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HashMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashSet;
import static redis_request.RedisRequestOuterClass.RequestType.Hvals;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.LInsert;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LPushX;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.Lindex;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.PExpire;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.PTTL;
import static redis_request.RedisRequestOuterClass.RequestType.Persist;
import static redis_request.RedisRequestOuterClass.RequestType.PfAdd;
import static redis_request.RedisRequestOuterClass.RequestType.PfCount;
import static redis_request.RedisRequestOuterClass.RequestType.PfMerge;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.RPushX;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.SInter;
import static redis_request.RedisRequestOuterClass.RequestType.SInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.SIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SMove;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SUnionStore;
import static redis_request.RedisRequestOuterClass.RequestType.SetRange;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.Strlen;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.XAdd;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiff;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZLexCount;
import static redis_request.RedisRequestOuterClass.RequestType.ZMScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.ZRangeStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByLex;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZScore;
import static redis_request.RedisRequestOuterClass.RequestType.Zadd;
import static redis_request.RedisRequestOuterClass.RequestType.Zcard;
import static redis_request.RedisRequestOuterClass.RequestType.Zcount;
import static redis_request.RedisRequestOuterClass.RequestType.Zrange;
import static redis_request.RedisRequestOuterClass.RequestType.Zrank;
import static redis_request.RedisRequestOuterClass.RequestType.Zrem;

import glide.api.commands.GenericBaseCommands;
import glide.api.commands.HashBaseCommands;
import glide.api.commands.HyperLogLogBaseCommands;
import glide.api.commands.ListBaseCommands;
import glide.api.commands.SetBaseCommands;
import glide.api.commands.SortedSetBaseCommands;
import glide.api.commands.StreamBaseCommands;
import glide.api.commands.StringBaseCommands;
import glide.api.models.Script;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.LexRange;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoreRange;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.StreamAddOptions;
import glide.api.models.commands.ZaddOptions;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.exceptions.RedisException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.connectors.resources.Platform;
import glide.connectors.resources.ThreadPoolResource;
import glide.connectors.resources.ThreadPoolResourceAllocator;
import glide.ffi.resolvers.RedisValueResolver;
import glide.managers.BaseCommandResponseResolver;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

/** Base Client class for Redis */
@AllArgsConstructor
public abstract class BaseClient
        implements AutoCloseable,
                GenericBaseCommands,
                StringBaseCommands,
                HashBaseCommands,
                ListBaseCommands,
                SetBaseCommands,
                SortedSetBaseCommands,
                StreamBaseCommands,
                HyperLogLogBaseCommands {

    /** Redis simple string response with "OK" */
    public static final String OK = ConstantResponse.OK.toString();

    protected final ConnectionManager connectionManager;
    protected final CommandManager commandManager;

    /**
     * Async request for an async (non-blocking) Redis client.
     *
     * @param config Redis client Configuration.
     * @param constructor Redis client constructor reference.
     * @param <T> Client type.
     * @return a Future to connect and return a RedisClient.
     */
    protected static <T> CompletableFuture<T> CreateClient(
            BaseClientConfiguration config,
            BiFunction<ConnectionManager, CommandManager, T> constructor) {
        try {
            ThreadPoolResource threadPoolResource = config.getThreadPoolResource();
            if (threadPoolResource == null) {
                threadPoolResource =
                        ThreadPoolResourceAllocator.getOrCreate(Platform.getThreadPoolResourceSupplier());
            }
            ChannelHandler channelHandler = buildChannelHandler(threadPoolResource);
            ConnectionManager connectionManager = buildConnectionManager(channelHandler);
            CommandManager commandManager = buildCommandManager(channelHandler);
            // TODO: Support exception throwing, including interrupted exceptions
            return connectionManager
                    .connectToRedis(config)
                    .thenApply(ignore -> constructor.apply(connectionManager, commandManager));
        } catch (InterruptedException e) {
            // Something bad happened while we were establishing netty connection to UDS
            var future = new CompletableFuture<T>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Closes this resource, relinquishing any underlying resources. This method is invoked
     * automatically on objects managed by the try-with-resources statement.
     *
     * @see <a
     *     href="https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html#close--">AutoCloseable::close()</a>
     */
    @Override
    public void close() throws ExecutionException {
        try {
            connectionManager.closeConnection().get();
        } catch (InterruptedException e) {
            // suppressing the interrupted exception - it is already suppressed in the future
            throw new RuntimeException(e);
        }
    }

    protected static ChannelHandler buildChannelHandler(ThreadPoolResource threadPoolResource)
            throws InterruptedException {
        CallbackDispatcher callbackDispatcher = new CallbackDispatcher();
        return new ChannelHandler(callbackDispatcher, getSocket(), threadPoolResource);
    }

    protected static ConnectionManager buildConnectionManager(ChannelHandler channelHandler) {
        return new ConnectionManager(channelHandler);
    }

    protected static CommandManager buildCommandManager(ChannelHandler channelHandler) {
        return new CommandManager(channelHandler);
    }

    /**
     * Extracts the value from a <code>GLIDE core</code> response message and either throws an
     * exception or returns the value as an object of type <code>T</code>. If <code>isNullable</code>,
     * than also returns <code>null</code>.
     *
     * @param response Redis protobuf message.
     * @param classType Parameter <code>T</code> class type.
     * @param isNullable Accepts null values in the protobuf message.
     * @return Response as an object of type <code>T</code> or <code>null</code>.
     * @param <T> The return value type.
     * @throws RedisException On a type mismatch.
     */
    @SuppressWarnings("unchecked")
    protected <T> T handleRedisResponse(Class<T> classType, boolean isNullable, Response response)
            throws RedisException {
        Object value =
                new BaseCommandResponseResolver(RedisValueResolver::valueFromPointer).apply(response);
        if (isNullable && (value == null)) {
            return null;
        }
        if (classType.isInstance(value)) {
            return (T) value;
        }
        String className = value == null ? "null" : value.getClass().getSimpleName();
        throw new RedisException(
                "Unexpected return type from Redis: got "
                        + className
                        + " expected "
                        + classType.getSimpleName());
    }

    protected Object handleObjectOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Object.class, true, response);
    }

    protected String handleStringResponse(Response response) throws RedisException {
        return handleRedisResponse(String.class, false, response);
    }

    protected String handleStringOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(String.class, true, response);
    }

    protected Boolean handleBooleanResponse(Response response) throws RedisException {
        return handleRedisResponse(Boolean.class, false, response);
    }

    protected Long handleLongResponse(Response response) throws RedisException {
        return handleRedisResponse(Long.class, false, response);
    }

    protected Long handleLongOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Long.class, true, response);
    }

    protected Double handleDoubleResponse(Response response) throws RedisException {
        return handleRedisResponse(Double.class, false, response);
    }

    protected Double handleDoubleOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Double.class, true, response);
    }

    protected Object[] handleArrayResponse(Response response) throws RedisException {
        return handleRedisResponse(Object[].class, false, response);
    }

    protected Object[] handleArrayOrNullResponse(Response response) throws RedisException {
        return handleRedisResponse(Object[].class, true, response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code>.
     * @param <V> Value type.
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<String, V> handleMapResponse(Response response) throws RedisException {
        return handleRedisResponse(Map.class, false, response);
    }

    @SuppressWarnings("unchecked") // raw Set cast to Set<String>
    protected Set<String> handleSetResponse(Response response) throws RedisException {
        return handleRedisResponse(Set.class, false, response);
    }

    @Override
    public CompletableFuture<Long> del(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Del, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> get(@NonNull String key) {
        return commandManager.submitNewCommand(
                GetString, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> set(@NonNull String key, @NonNull String value) {
        return commandManager.submitNewCommand(
                SetString, new String[] {key, value}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> set(
            @NonNull String key, @NonNull String value, @NonNull SetOptions options) {
        String[] arguments = ArrayUtils.addAll(new String[] {key, value}, options.toArgs());
        return commandManager.submitNewCommand(SetString, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> mget(@NonNull String[] keys) {
        return commandManager.submitNewCommand(
                MGet, keys, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String> mset(@NonNull Map<String, String> keyValueMap) {
        String[] args = convertMapToKeyValueStringArray(keyValueMap);
        return commandManager.submitNewCommand(MSet, args, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> incr(@NonNull String key) {
        return commandManager.submitNewCommand(Incr, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> incrBy(@NonNull String key, long amount) {
        return commandManager.submitNewCommand(
                IncrBy, new String[] {key, Long.toString(amount)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Double> incrByFloat(@NonNull String key, double amount) {
        return commandManager.submitNewCommand(
                IncrByFloat, new String[] {key, Double.toString(amount)}, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Long> decr(@NonNull String key) {
        return commandManager.submitNewCommand(Decr, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> decrBy(@NonNull String key, long amount) {
        return commandManager.submitNewCommand(
                DecrBy, new String[] {key, Long.toString(amount)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> strlen(@NonNull String key) {
        return commandManager.submitNewCommand(Strlen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> setrange(@NonNull String key, int offset, @NonNull String value) {
        String[] arguments = new String[] {key, Integer.toString(offset), value};
        return commandManager.submitNewCommand(SetRange, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> getrange(@NonNull String key, int start, int end) {
        String[] arguments = new String[] {key, Integer.toString(start), Integer.toString(end)};
        return commandManager.submitNewCommand(GetRange, arguments, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> hget(@NonNull String key, @NonNull String field) {
        return commandManager.submitNewCommand(
                HashGet, new String[] {key, field}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> hset(
            @NonNull String key, @NonNull Map<String, String> fieldValueMap) {
        String[] args = ArrayUtils.addFirst(convertMapToKeyValueStringArray(fieldValueMap), key);
        return commandManager.submitNewCommand(HashSet, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> hsetnx(
            @NonNull String key, @NonNull String field, @NonNull String value) {
        return commandManager.submitNewCommand(
                HSetNX, new String[] {key, field, value}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> hdel(@NonNull String key, @NonNull String[] fields) {
        String[] args = ArrayUtils.addFirst(fields, key);
        return commandManager.submitNewCommand(HashDel, args, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> hlen(@NonNull String key) {
        return commandManager.submitNewCommand(HLen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> hvals(@NonNull String key) {
        return commandManager.submitNewCommand(
                Hvals,
                new String[] {key},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String[]> hmget(@NonNull String key, @NonNull String[] fields) {
        String[] arguments = ArrayUtils.addFirst(fields, key);
        return commandManager.submitNewCommand(
                HashMGet, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Boolean> hexists(@NonNull String key, @NonNull String field) {
        return commandManager.submitNewCommand(
                HashExists, new String[] {key, field}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Map<String, String>> hgetall(@NonNull String key) {
        return commandManager.submitNewCommand(HashGetAll, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Long> hincrBy(@NonNull String key, @NonNull String field, long amount) {
        return commandManager.submitNewCommand(
                HashIncrBy, new String[] {key, field, Long.toString(amount)}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Double> hincrByFloat(
            @NonNull String key, @NonNull String field, double amount) {
        return commandManager.submitNewCommand(
                HashIncrByFloat,
                new String[] {key, field, Double.toString(amount)},
                this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Long> lpush(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(LPush, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> lpop(@NonNull String key) {
        return commandManager.submitNewCommand(
                LPop, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> lpopCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                LPop,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String[]> lrange(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                LRange,
                new String[] {key, Long.toString(start), Long.toString(end)},
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String> lindex(@NonNull String key, long index) {
        return commandManager.submitNewCommand(
                Lindex, new String[] {key, Long.toString(index)}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String> ltrim(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                LTrim,
                new String[] {key, Long.toString(start), Long.toString(end)},
                this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> llen(@NonNull String key) {
        return commandManager.submitNewCommand(LLen, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lrem(@NonNull String key, long count, @NonNull String element) {
        return commandManager.submitNewCommand(
                LRem, new String[] {key, Long.toString(count), element}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> rpush(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(RPush, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> rpop(@NonNull String key) {
        return commandManager.submitNewCommand(
                RPop, new String[] {key}, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<String[]> rpopCount(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                RPop,
                new String[] {key, Long.toString(count)},
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Long> sadd(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> sismember(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                SIsMember, new String[] {key, member}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> srem(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SRem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Set<String>> smembers(@NonNull String key) {
        return commandManager.submitNewCommand(SMembers, new String[] {key}, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Long> scard(@NonNull String key) {
        return commandManager.submitNewCommand(SCard, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> sdiffstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SDiffStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> smove(
            @NonNull String source, @NonNull String destination, @NonNull String member) {
        return commandManager.submitNewCommand(
                SMove, new String[] {source, destination, member}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> sinterstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SInterStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Set<String>> sinter(@NonNull String[] keys) {
        return commandManager.submitNewCommand(SInter, keys, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Long> sunionstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, destination);
        return commandManager.submitNewCommand(SUnionStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> exists(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Exists, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> unlink(@NonNull String[] keys) {
        return commandManager.submitNewCommand(Unlink, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> expire(@NonNull String key, long seconds) {
        return commandManager.submitNewCommand(
                Expire, new String[] {key, Long.toString(seconds)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expire(
            @NonNull String key, long seconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {key, Long.toString(seconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(Expire, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(@NonNull String key, long unixSeconds) {
        return commandManager.submitNewCommand(
                ExpireAt, new String[] {key, Long.toString(unixSeconds)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> expireAt(
            @NonNull String key, long unixSeconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {key, Long.toString(unixSeconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(ExpireAt, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(@NonNull String key, long milliseconds) {
        return commandManager.submitNewCommand(
                PExpire, new String[] {key, Long.toString(milliseconds)}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpire(
            @NonNull String key, long milliseconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {key, Long.toString(milliseconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(PExpire, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(@NonNull String key, long unixMilliseconds) {
        return commandManager.submitNewCommand(
                PExpireAt,
                new String[] {key, Long.toString(unixMilliseconds)},
                this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Boolean> pexpireAt(
            @NonNull String key, long unixMilliseconds, @NonNull ExpireOptions expireOptions) {
        String[] arguments =
                ArrayUtils.addAll(
                        new String[] {key, Long.toString(unixMilliseconds)}, expireOptions.toArgs());
        return commandManager.submitNewCommand(PExpireAt, arguments, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<Long> ttl(@NonNull String key) {
        return commandManager.submitNewCommand(TTL, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Object> invokeScript(@NonNull Script script) {
        return commandManager.submitScript(
                script, List.of(), List.of(), this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object> invokeScript(
            @NonNull Script script, @NonNull ScriptOptions options) {
        return commandManager.submitScript(
                script, options.getKeys(), options.getArgs(), this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZaddOptions options,
            boolean changed) {
        String[] changedArg = changed ? new String[] {"CH"} : new String[] {};
        String[] membersScores = convertMapToValueKeyStringArray(membersScoresMap);

        String[] arguments =
                concatenateArrays(new String[] {key}, options.toArgs(), changedArg, membersScores);

        return commandManager.submitNewCommand(Zadd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key,
            @NonNull Map<String, Double> membersScoresMap,
            @NonNull ZaddOptions options) {
        return this.zadd(key, membersScoresMap, options, false);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap, boolean changed) {
        return this.zadd(key, membersScoresMap, ZaddOptions.builder().build(), changed);
    }

    @Override
    public CompletableFuture<Long> zadd(
            @NonNull String key, @NonNull Map<String, Double> membersScoresMap) {
        return this.zadd(key, membersScoresMap, ZaddOptions.builder().build(), false);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(
            @NonNull String key, @NonNull String member, double increment, @NonNull ZaddOptions options) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        options.toArgs(),
                        new String[] {"INCR", Double.toString(increment), member});

        return commandManager.submitNewCommand(Zadd, arguments, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Double> zaddIncr(
            @NonNull String key, @NonNull String member, double increment) {
        String[] arguments =
                concatenateArrays(
                        new String[] {key}, new String[] {"INCR", Double.toString(increment), member});

        return commandManager.submitNewCommand(Zadd, arguments, this::handleDoubleResponse);
    }

    @Override
    public CompletableFuture<Long> zrem(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(Zrem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zcard(@NonNull String key) {
        return commandManager.submitNewCommand(Zcard, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmin(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZPopMin, new String[] {key, Long.toString(count)}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmin(@NonNull String key) {
        return commandManager.submitNewCommand(ZPopMin, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(@NonNull String key, long count) {
        return commandManager.submitNewCommand(
                ZPopMax, new String[] {key, Long.toString(count)}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zpopmax(@NonNull String key) {
        return commandManager.submitNewCommand(ZPopMax, new String[] {key}, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Double> zscore(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                ZScore, new String[] {key, member}, this::handleDoubleOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> zrank(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                Zrank, new String[] {key, member}, this::handleLongOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> zrankWithScore(@NonNull String key, @NonNull String member) {
        return commandManager.submitNewCommand(
                Zrank, new String[] {key, member, WITH_SCORE_REDIS_API}, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<Double[]> zmscore(@NonNull String key, @NonNull String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(
                ZMScore,
                arguments,
                response -> castArray(handleArrayOrNullResponse(response), Double.class));
    }

    @Override
    public CompletableFuture<String[]> zdiff(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        return commandManager.submitNewCommand(
                ZDiff, arguments, response -> castArray(handleArrayResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Map<String, Double>> zdiffWithScores(@NonNull String[] keys) {
        String[] arguments = ArrayUtils.addFirst(keys, Long.toString(keys.length));
        arguments = ArrayUtils.add(arguments, WITH_SCORES_REDIS_API);
        return commandManager.submitNewCommand(ZDiff, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Long> zdiffstore(@NonNull String destination, @NonNull String[] keys) {
        String[] arguments =
                ArrayUtils.addAll(new String[] {destination, Long.toString(keys.length)}, keys);
        return commandManager.submitNewCommand(ZDiffStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zcount(
            @NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                Zcount, new String[] {key, minScore.toArgs(), maxScore.toArgs()}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebyrank(@NonNull String key, long start, long end) {
        return commandManager.submitNewCommand(
                ZRemRangeByRank,
                new String[] {key, Long.toString(start), Long.toString(end)},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebylex(
            @NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZRemRangeByLex,
                new String[] {key, minLex.toArgs(), maxLex.toArgs()},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zremrangebyscore(
            @NonNull String key, @NonNull ScoreRange minScore, @NonNull ScoreRange maxScore) {
        return commandManager.submitNewCommand(
                ZRemRangeByScore,
                new String[] {key, minScore.toArgs(), maxScore.toArgs()},
                this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zlexcount(
            @NonNull String key, @NonNull LexRange minLex, @NonNull LexRange maxLex) {
        return commandManager.submitNewCommand(
                ZLexCount, new String[] {key, minLex.toArgs(), maxLex.toArgs()}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zrangestore(
            @NonNull String destination,
            @NonNull String source,
            @NonNull RangeQuery rangeQuery,
            boolean reverse) {
        String[] arguments =
                RangeOptions.createZRangeStoreArgs(destination, source, rangeQuery, reverse);

        return commandManager.submitNewCommand(ZRangeStore, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> zrangestore(
            @NonNull String destination, @NonNull String source, @NonNull RangeQuery rangeQuery) {
        return this.zrangestore(destination, source, rangeQuery, false);
    }

    @Override
    public CompletableFuture<String> xadd(@NonNull String key, @NonNull Map<String, String> values) {
        return xadd(key, values, StreamAddOptions.builder().build());
    }

    @Override
    public CompletableFuture<String> xadd(
            @NonNull String key, @NonNull Map<String, String> values, @NonNull StreamAddOptions options) {
        String[] arguments =
                ArrayUtils.addAll(
                        ArrayUtils.addFirst(options.toArgs(), key), convertMapToKeyValueStringArray(values));
        return commandManager.submitNewCommand(XAdd, arguments, this::handleStringOrNullResponse);
    }

    @Override
    public CompletableFuture<Long> pttl(@NonNull String key) {
        return commandManager.submitNewCommand(PTTL, new String[] {key}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Boolean> persist(@NonNull String key) {
        return commandManager.submitNewCommand(
                Persist, new String[] {key}, this::handleBooleanResponse);
    }

    @Override
    public CompletableFuture<String> type(@NonNull String key) {
        return commandManager.submitNewCommand(Type, new String[] {key}, this::handleStringResponse);
    }

    @Override
    public CompletableFuture<Long> linsert(
            @NonNull String key,
            @NonNull InsertPosition position,
            @NonNull String pivot,
            @NonNull String element) {
        return commandManager.submitNewCommand(
                LInsert, new String[] {key, position.toString(), pivot, element}, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> blpop(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitNewCommand(
                Blpop, arguments, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String[]> brpop(@NonNull String[] keys, double timeout) {
        String[] arguments = ArrayUtils.add(keys, Double.toString(timeout));
        return commandManager.submitNewCommand(
                Brpop, arguments, response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<Long> rpushx(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(RPushX, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> lpushx(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(LPushX, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String[]> zrange(
            @NonNull String key, @NonNull RangeQuery rangeQuery, boolean reverse) {
        String[] arguments = RangeOptions.createZRangeArgs(key, rangeQuery, reverse, false);

        return commandManager.submitNewCommand(
                Zrange,
                arguments,
                response -> castArray(handleArrayOrNullResponse(response), String.class));
    }

    @Override
    public CompletableFuture<String[]> zrange(@NonNull String key, @NonNull RangeQuery rangeQuery) {
        return this.zrange(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery, boolean reverse) {
        String[] arguments = RangeOptions.createZRangeArgs(key, rangeQuery, reverse, true);

        return commandManager.submitNewCommand(Zrange, arguments, this::handleMapResponse);
    }

    @Override
    public CompletableFuture<Map<String, Double>> zrangeWithScores(
            @NonNull String key, @NonNull ScoredRangeQuery rangeQuery) {
        return this.zrangeWithScores(key, rangeQuery, false);
    }

    @Override
    public CompletableFuture<Long> pfadd(@NonNull String key, @NonNull String[] elements) {
        String[] arguments = ArrayUtils.addFirst(elements, key);
        return commandManager.submitNewCommand(PfAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> pfcount(@NonNull String[] keys) {
        return commandManager.submitNewCommand(PfCount, keys, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<String> pfmerge(
            @NonNull String destination, @NonNull String[] sourceKeys) {
        String[] arguments = ArrayUtils.addFirst(sourceKeys, destination);
        return commandManager.submitNewCommand(PfMerge, arguments, this::handleStringResponse);
    }
}
