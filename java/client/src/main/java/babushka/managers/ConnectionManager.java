package babushka.managers;

import babushka.connectors.handlers.ChannelHandler;
import babushka.ffi.resolvers.RedisValueResolver;
import babushka.models.RequestBuilder;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

@RequiredArgsConstructor
public class ConnectionManager {

  /** UDS connection representation. */
  private final ChannelHandler channel;

  /** Client connection status to update when connection established. */
  private final AtomicBoolean connectionStatus;

  /**
   * Connect to Redis using a ProtoBuf connection request.
   *
   * @param host Server address
   * @param port Server port
   * @param useSsl true if communication with the server or cluster should use Transport Level
   *     Security
   * @param clusterMode true if REDIS instance runs in the cluster mode
   */
  // TODO support more parameters and/or configuration object
  public CompletableFuture<Boolean> connectToRedis(
      String host, int port, boolean useSsl, boolean clusterMode) {
    ConnectionRequest request =
        RequestBuilder.createConnectionRequest(host, port, useSsl, clusterMode);
    return channel.connect(request).thenApplyAsync(this::checkBabushkaResponse);
  }

  /** Check a response received from Babushka. */
  private boolean checkBabushkaResponse(Response response) {
    // TODO do we need to check callback value? It could be -1 or 0
    if (response.hasRequestError()) {
      // TODO do we need to support different types of exceptions and distinguish them by type?
      throw new RuntimeException(
          String.format(
              "%s: %s",
              response.getRequestError().getType(), response.getRequestError().getMessage()));
    } else if (response.hasClosingError()) {
      throw new RuntimeException("Connection closed: " + response.getClosingError());
    } else if (response.hasConstantResponse()) {
      return connectionStatus.compareAndSet(
          false, response.getConstantResponse() == ConstantResponse.OK);
    } else if (response.hasRespPointer()) {
      throw new RuntimeException(
          "Unexpected response data: "
              + RedisValueResolver.valueFromPointer(response.getRespPointer()));
    }
    // TODO commented out due to #710 https://github.com/aws/babushka/issues/710
    //      empty response means a successful connection
    // throw new IllegalStateException("A malformed response received: " + response.toString());
    return connectionStatus.compareAndSet(false, true);
  }

  /** Close the connection and the corresponding channel. */
  public CompletableFuture<Void> closeConnection() {
    return CompletableFuture.runAsync(channel::close);
  }
}
