package glide.managers;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import glide.connectors.handlers.ChannelHandler;
import glide.ffi.resolvers.RedisValueResolver;
import glide.models.RequestBuilder;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/**
 * Service responsible for submitting connection requests to a socket channel handler and unpack
 * responses from the same socket channel handler.
 */
@RequiredArgsConstructor
public class ConnectionManager {

  /** UDS connection representation. */
  private final ChannelHandler channel;

  /**
   * Connect to Redis using a ProtoBuf connection request.
   *
   * @param host Server address
   * @param port Server port
   * @param useTls true if communication with the server or cluster should use Transport Level
   *     Security
   * @param clusterMode true if the client is used for connecting to a Redis Cluster
   */
  // TODO support more parameters and/or configuration object
  public CompletableFuture<Void> connectToRedis(
      String host, int port, boolean useTls, boolean clusterMode) {
    ConnectionRequest request =
        RequestBuilder.createConnectionRequest(host, port, useTls, clusterMode);
    return channel.connect(request).thenApply(this::checkGlideRsResponse);
  }

  /** Check a response received from Glide. */
  private Void checkGlideRsResponse(Response response) {
    if (response.hasRequestError()) {
      // TODO unexpected when establishing a connection
      throw new RuntimeException(
          String.format(
              "%s: %s",
              response.getRequestError().getType(), response.getRequestError().getMessage()));
    }
    if (response.hasClosingError()) {
      throw new RuntimeException("Connection closed: " + response.getClosingError());
    }
    if (response.hasRespPointer()) {
      throw new RuntimeException(
          "Unexpected response data: "
              + RedisValueResolver.valueFromPointer(response.getRespPointer()));
    }
    return null;
  }

  /** Close the connection and the corresponding channel. */
  public CompletableFuture<Void> closeConnection() {
    return CompletableFuture.runAsync(channel::close);
  }
}
