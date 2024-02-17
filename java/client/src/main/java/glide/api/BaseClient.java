/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.ffi.resolvers.SocketListenerResolver.getSocket;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;

import glide.api.commands.ConnectionManagementCommands;
import glide.api.commands.SetCommands;
import glide.api.commands.StringCommands;
import glide.api.models.commands.SetOptions;
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
        implements AutoCloseable, ConnectionManagementCommands, StringCommands, SetCommands {
    /** Redis simple string response with "OK" */
    public static final String OK = ConstantResponse.OK.toString();

    protected final ConnectionManager connectionManager;
    protected final CommandManager commandManager;

    /**
     * Async request for an async (non-blocking) Redis client.
     *
     * @param config Redis client Configuration
     * @param constructor Redis client constructor reference
     * @param <T> Client type
     * @return a Future to connect and return a RedisClient
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
     * Extracts the value from a Redis response message and either throws an exception or returns the
     * value as an object of type {@link T}. If <code>isNullable</code>, than also returns <code>null
     * </code>.
     *
     * @param response Redis protobuf message
     * @param classType Parameter {@link T} class type
     * @param isNullable Accepts null values in the protobuf message
     * @return Response as an object of type {@link T} or <code>null</code>
     * @param <T> return type
     * @throws RedisException on a type mismatch
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

    protected Object handleObjectResponse(Response response) throws RedisException {
        return handleRedisResponse(Object.class, false, response);
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

    protected Long handleLongResponse(Response response) throws RedisException {
        return handleRedisResponse(Long.class, false, response);
    }

    protected Object[] handleArrayResponse(Response response) {
        return handleRedisResponse(Object[].class, true, response);
    }

    protected Set<String> handleSetResponse(Response response) {
        return handleRedisResponse(Set.class, false, response);
    }

    /**
     * @param response A Protobuf response
     * @return A map of <code>String</code> to <code>V</code>
     * @param <V> Value type, could be even map too
     */
    @SuppressWarnings("unchecked") // raw Map cast to Map<String, V>
    protected <V> Map<String, V> handleMapResponse(Response response) throws RedisException {
        return handleRedisResponse(Map.class, false, response);
    }

    @Override
    public CompletableFuture<String> ping() {
        return commandManager.submitNewCommand(Ping, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(@NonNull String str) {
        return commandManager.submitNewCommand(Ping, new String[] {str}, this::handleStringResponse);
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
    public CompletableFuture<Long> sadd(String key, String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SAdd, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Long> srem(String key, String[] members) {
        String[] arguments = ArrayUtils.addFirst(members, key);
        return commandManager.submitNewCommand(SRem, arguments, this::handleLongResponse);
    }

    @Override
    public CompletableFuture<Set<String>> smembers(String key) {
        return commandManager.submitNewCommand(SMembers, new String[] {key}, this::handleSetResponse);
    }

    @Override
    public CompletableFuture<Long> scard(String key) {
        return commandManager.submitNewCommand(SCard, new String[] {key}, this::handleLongResponse);
    }
}
