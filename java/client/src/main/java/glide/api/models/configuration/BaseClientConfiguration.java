package glide.api.models.configuration;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/** Represents the configuration settings for a Redis client. */
@Getter
@SuperBuilder
public abstract class BaseClientConfiguration {
  /**
   * DNS Addresses and ports of known nodes in the cluster. If the server is in cluster mode the
   * list can be partial, as the client will attempt to map out the cluster and find all nodes. If
   * the server is in standalone mode, only nodes whose addresses were provided will be used by the
   * client. For example: [ {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
   * {address: sample-address-0002.use2.cache.amazonaws.com, port:6379} ]. If none are set, a
   * default address localhost:6379 will be used.
   */
  @Singular private final List<NodeAddress> addresses;

  /** True if communication with the cluster should use Transport Level Security. */
  @Builder.Default private final boolean useTLS = false;

  /** If not set, `PRIMARY` will be used. */
  @Builder.Default private final ReadFrom readFrom = ReadFrom.PRIMARY;

  /**
   * Credentials for authentication process. If none are set, the client will not authenticate
   * itself with the server.
   */
  private final RedisCredentials credentials;

  /**
   * The duration in milliseconds that the client should wait for a request to complete. This
   * duration encompasses sending the request, awaiting for a response from the server, and any
   * required reconnections or retries. If the specified timeout is exceeded for a pending request,
   * it will result in a timeout error. If not set, a default value will be used.
   */
  private final Integer requestTimeout;
}
