/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.ffi.resolvers.SocketListenerResolver.getSocket;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;

import glide.api.commands.ConnectionManagementCommands;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import response.ResponseOuterClass;
import response.ResponseOuterClass.Response;

/** Base Client class for Redis */
@AllArgsConstructor
public abstract class BaseClient implements AutoCloseable, ConnectionManagementCommands {

    /** Redis simple string response with "OK" */
    public static final String OK = ResponseOuterClass.ConstantResponse.OK.toString();

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
     * Extracts the response from the Protobuf response and either throws an exception or returns the
     * appropriate response as an <code>Object</code>.
     *
     * @param response Redis protobuf message
     * @return Response <code>Object</code>
     */
    protected Object handleObjectResponse(Response response) {
        // convert protobuf response into Object
        return new BaseCommandResponseResolver(RedisValueResolver::valueFromPointer).apply(response);
    }

    /**
     * Extracts the response value from the Redis response and either throws an exception or returns
     * the value as a <code>String</code>.
     *
     * @param response Redis protobuf message
     * @return Response as a <code>String</code>
     * @throws RedisException if there's a type mismatch
     */
    protected String handleStringResponse(Response response) {
        Object value = handleObjectResponse(response);
        if (value instanceof String || value == null) {
            return (String) value;
        }
        throw new RedisException(
                "Unexpected return type from Redis: got "
                        + value.getClass().getSimpleName()
                        + " expected String");
    }

    @Override
    public CompletableFuture<String> ping() {
        return commandManager.submitNewCommand(Ping, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> ping(String str) {
        return commandManager.submitNewCommand(Ping, new String[] {str}, this::handleStringResponse);
    }
}
