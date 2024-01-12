package glide.managers;

import static glide.api.models.configuration.NodeAddress.DEFAULT_HOST;
import static glide.api.models.configuration.NodeAddress.DEFAULT_PORT;

import connection_request.ConnectionRequestOuterClass;
import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.TlsMode;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.ChannelHandler;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.Response;

/**
 * Service responsible for submitting connection requests to a socket channel handler and unpack
 * responses from the same socket channel handler.
 */
@RequiredArgsConstructor
public class ConnectionManager {

  // TODO: consider making connection manager static, and moving the ChannelHandler to the
  // RedisClient.

  /** UDS connection representation. */
  private final ChannelHandler channel;

  /**
   * Make a connection request to Redis Rust-core client.
   *
   * @param configuration Connection Request Configuration
   */
  public CompletableFuture<Void> connectToRedis(BaseClientConfiguration configuration) {
    ConnectionRequest request = createConnectionRequest(configuration);
    return channel.connect(request).thenApplyAsync(this::checkGlideRsResponse);
  }

  /**
   * Close the connection and the corresponding channel. Creates a ConnectionRequest protobuf
   * message based on the type of client Standalone/Cluster.
   *
   * @param configuration Connection Request Configuration
   * @return ConnectionRequest protobuf message
   */
  private ConnectionRequest createConnectionRequest(BaseClientConfiguration configuration) {
    if (configuration instanceof RedisClusterClientConfiguration) {
      return setupConnectionRequestBuilderRedisClusterClient(
              (RedisClusterClientConfiguration) configuration)
          .build();
    }

    return setupConnectionRequestBuilderRedisClient((RedisClientConfiguration) configuration)
        .build();
  }

  /**
   * Creates ConnectionRequestBuilder, so it has appropriate fields for the BaseClientConfiguration
   * where the Standalone/Cluster inherit from.
   *
   * @param configuration
   */
  private ConnectionRequest.Builder setupConnectionRequestBuilderBaseConfiguration(
      BaseClientConfiguration configuration) {
    ConnectionRequest.Builder connectionRequestBuilder = ConnectionRequest.newBuilder();
    if (!configuration.getAddresses().isEmpty()) {
      for (NodeAddress nodeAddress : configuration.getAddresses()) {
        connectionRequestBuilder.addAddresses(
            ConnectionRequestOuterClass.NodeAddress.newBuilder()
                .setHost(nodeAddress.getHost())
                .setPort(nodeAddress.getPort())
                .build());
      }
    } else {
      connectionRequestBuilder.addAddresses(
          ConnectionRequestOuterClass.NodeAddress.newBuilder()
              .setHost(DEFAULT_HOST)
              .setPort(DEFAULT_PORT)
              .build());
    }

    connectionRequestBuilder
        .setTlsMode(configuration.isUseTLS() ? TlsMode.SecureTls : TlsMode.NoTls)
        .setReadFrom(mapReadFromEnum(configuration.getReadFrom()));

    if (configuration.getCredentials() != null) {
      AuthenticationInfo.Builder authenticationInfoBuilder = AuthenticationInfo.newBuilder();
      if (configuration.getCredentials().getUsername() != null) {
        authenticationInfoBuilder.setUsername(configuration.getCredentials().getUsername());
      }
      authenticationInfoBuilder.setPassword(configuration.getCredentials().getPassword());

      connectionRequestBuilder.setAuthenticationInfo(authenticationInfoBuilder.build());
    }

    if (configuration.getRequestTimeout() != null) {
      connectionRequestBuilder.setRequestTimeout(configuration.getRequestTimeout());
    }

    return connectionRequestBuilder;
  }

  /**
   * Creates ConnectionRequestBuilder, so it has appropriate fields for the Redis Standalone Client.
   *
   * @param configuration Connection Request Configuration
   */
  private ConnectionRequest.Builder setupConnectionRequestBuilderRedisClient(
      RedisClientConfiguration configuration) {
    ConnectionRequest.Builder connectionRequestBuilder =
        setupConnectionRequestBuilderBaseConfiguration(configuration);
    connectionRequestBuilder.setClusterModeEnabled(false);
    if (configuration.getReconnectStrategy() != null) {
      connectionRequestBuilder.setConnectionRetryStrategy(
          ConnectionRequestOuterClass.ConnectionRetryStrategy.newBuilder()
              .setNumberOfRetries(configuration.getReconnectStrategy().getNumOfRetries())
              .setFactor(configuration.getReconnectStrategy().getFactor())
              .setExponentBase(configuration.getReconnectStrategy().getExponentBase())
              .build());
    }

    if (configuration.getDatabaseId() != null) {
      connectionRequestBuilder.setDatabaseId(configuration.getDatabaseId());
    }

    return connectionRequestBuilder;
  }

  /**
   * Creates ConnectionRequestBuilder, so it has appropriate fields for the Redis Cluster Client.
   *
   * @param configuration
   */
  private ConnectionRequestOuterClass.ConnectionRequest.Builder
      setupConnectionRequestBuilderRedisClusterClient(
          RedisClusterClientConfiguration configuration) {
    ConnectionRequest.Builder connectionRequestBuilder =
        setupConnectionRequestBuilderBaseConfiguration(configuration);
    connectionRequestBuilder.setClusterModeEnabled(true);

    return connectionRequestBuilder;
  }

  /**
   * Look up for java ReadFrom enum to protobuf defined ReadFrom enum.
   *
   * @param readFrom
   * @return Protobuf defined ReadFrom enum
   */
  private ConnectionRequestOuterClass.ReadFrom mapReadFromEnum(ReadFrom readFrom) {
    if (readFrom == ReadFrom.PREFER_REPLICA) {
      return ConnectionRequestOuterClass.ReadFrom.PreferReplica;
    }

    return ConnectionRequestOuterClass.ReadFrom.Primary;
  }

  /** Check a response received from Glide. */
  private Void checkGlideRsResponse(Response response) {
    if (response.hasRequestError()) {
      closeConnection();
      RequestError error = response.getRequestError();
      throw new ClosingException("Unexpected request error in response: " + error.getMessage());
    }
    if (response.hasClosingError()) {
      // A closing error is thrown when Rust-core is not connected to Redis
      // We want to close shop and throw a ClosingException
      closeConnection();
      throw new ClosingException(response.getClosingError());
    }
    if (response.hasRespPointer()) {
      closeConnection();
      throw new ClosingException("Unexpected data in response");
    }
    if (!response.hasConstantResponse()) {
      closeConnection();
      throw new ClosingException("Unexpected empty data in response");
    }
    // successful connection response has an "OK"
    return null;
  }

  /** Close the connection to the channel. */

  /**
   * Close the connection to the channel.
   *
   * @return a CompletableFuture to indicate the channel is closed
   */
  public CompletableFuture<Void> closeConnection() {
    CompletableFuture<Void> completableFuture = new CompletableFuture<>();
    channel
        .close()
        .syncUninterruptibly()
        .addListener(
            (GenericFutureListener<ChannelFuture>)
                future -> {
                  if (future.isCancelled()) {
                    completableFuture.cancel(false);
                  } else if (future.isDone()) {
                    completableFuture.complete(null);
                  }
                  completableFuture.completeExceptionally(
                      new RuntimeException("Channel failed to close"));
                });
    return completableFuture;
  }
}
