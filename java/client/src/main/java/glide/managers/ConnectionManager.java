package glide.managers;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import glide.api.models.configuration.BaseClientConfiguration;
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
   * @param configuration Connection Request Configuration
   */
  public CompletableFuture<Void> connectToRedis(BaseClientConfiguration configuration) {
    connection_request.ConnectionRequestOuterClass.ConnectionRequest request = createConnectionRequest(configuration);
    return channel.connect(request).thenApplyAsync(this::checkGlideRsResponse);
  }

  /** Close the connection and the corresponding channel. */
  /**
   * Creates a ConnectionRequest protobuf message based on the type of client Standalone/Cluster.
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
   * Modifies ConnectionRequestBuilder, so it has appropriate fields for the BaseClientConfiguration
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
        .setTlsMode(
            configuration.isUseTLS()
                ? ConnectionRequestOuterClass.TlsMode.SecureTls
                : ConnectionRequestOuterClass.TlsMode.NoTls)
        .setReadFrom(mapReadFromEnum(configuration.getReadFrom()));

    if (configuration.getCredentials() != null) {
      ConnectionRequestOuterClass.AuthenticationInfo.Builder authenticationInfoBuilder =
          ConnectionRequestOuterClass.AuthenticationInfo.newBuilder();
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
   * Modifies ConnectionRequestBuilder, so it has appropriate fields for the Redis Standalone
   * Client.
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
   * Modifies ConnectionRequestBuilder, so it has appropriate fields for the Redis Cluster Client.
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
      return null;
    } else if (response.hasRespPointer()) {
      throw new RuntimeException("Unexpected data in response");
    }
    // TODO commented out due to #710 https://github.com/aws/babushka/issues/710
    //      empty response means a successful connection
    // throw new IllegalStateException("A malformed response received: " + response.toString());
    return null;
  }

  /** Close the connection and the corresponding channel. */
  public CompletableFuture<Void> closeConnection() {
    return CompletableFuture.runAsync(channel::close);
  }
}
