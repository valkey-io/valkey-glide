package glide.managers;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import glide.connectors.handlers.ChannelHandler;
import glide.ffi.resolvers.RedisValueResolver;
import glide.models.RequestBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass.RequestType;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.Response;

/**
 * Service responsible for submitting command requests to a socket channel handler and unpack
 * responses from the same socket channel handler.
 */
@RequiredArgsConstructor
public class CommandManager {

    /** UDS connection representation. */
    private final ChannelHandler channel;

    /**
     * Async (non-blocking) get.<br>
     * See <a href="https://redis.io/commands/get/">REDIS docs for GET</a>.
     *
     * @param key The key name
     */
    public CompletableFuture<String> get(String key) {
        return submitNewRequest(RequestType.GetString, List.of(key));
    }

    /**
     * Async (non-blocking) set.<br>
     * See <a href="https://redis.io/commands/set/">REDIS docs for SET</a>.
     *
     * @param key The key name
     * @param value The value to set
     */
    public CompletableFuture<String> set(String key, String value) {
        return submitNewRequest(RequestType.SetString, List.of(key, value));
    }

    /**
     * Build a command and submit it Netty to send.
     *
     * @param command Command type
     * @param args Command arguments
     * @return A result promise
     */
    private CompletableFuture<String> submitNewRequest(RequestType command, List<String> args) {
        return channel
                .write(RequestBuilder.prepareRedisRequest(command, args), true)
                .thenApplyAsync(this::extractValueFromGlideRsResponse);
    }

    /**
     * Check response and extract data from it.
     *
     * @param response A response received from rust core lib
     * @return A String from the Redis response, or Ok. Otherwise, returns null
     */
    private String extractValueFromGlideRsResponse(Response response) {
        if (response.hasRequestError()) {
            RequestError error = response.getRequestError();
            String msg = error.getMessage();
            switch (error.getType()) {
                case Unspecified:
                    // Unspecified error on Redis service-side
                    throw new RequestException(msg);
                case ExecAbort:
                    // Transactional error on Redis service-side
                    throw new ExecAbortException(msg);
                case Timeout:
                    // Timeout from Glide to Redis service
                    throw new TimeoutException(msg);
                case Disconnect:
                    // Connection problem between Glide and Redis
                    throw new ConnectionException(msg);
                default:
                    // Request or command error from Redis
                    throw new RedisException(msg);
            }
        }
        if (response.hasClosingError()) {
            // A closing error is thrown when Rust-core is not connected to Redis
            // We want to close shop and throw a ClosingException
            channel.close();
            throw new ClosingException(response.getClosingError());
        }
        if (response.hasConstantResponse()) {
            // Return "OK"
            return response.getConstantResponse().toString();
        }
        if (response.hasRespPointer()) {
            // Return the shared value - which may be a null value
            return RedisValueResolver.valueFromPointer(response.getRespPointer()).toString();
        }
        // if no response payload is provided, assume null
        return null;
    }
}
