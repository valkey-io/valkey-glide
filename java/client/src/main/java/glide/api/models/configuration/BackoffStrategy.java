package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Represents the strategy used to determine how and when to reconnect, in case of connection
 * failures. The time between attempts grows exponentially, to the formula rand(0 ... factor *
 * (exponentBase ^ N)), where N is the number of failed attempts. Once the maximum value is reached,
 * that will remain the time between retry attempts until a reconnect attempt is successful. The
 * client will attempt to reconnect indefinitely.
 */
@Getter
@Builder
public class BackoffStrategy {
  /**
   * Number of retry attempts that the client should perform when disconnected from the server,
   * where the time between retries increases. Once the retries have reached the maximum value, the
   * time between
   */
  @NonNull private final Integer numOfRetries;

  /** The multiplier that will be applied to the waiting time between each retry. */
  @NonNull private final Integer factor;

  /** The exponent base configured for the strategy. */
  @NonNull private final Integer exponentBase;
}
