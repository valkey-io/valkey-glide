/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** Represents the configuration settings for a Standalone Redis client. */
@Getter
@SuperBuilder
public class RedisClientConfiguration extends BaseClientConfiguration {
    /** Strategy used to determine how and when to reconnect, in case of connection failures. */
    private final BackoffStrategy reconnectStrategy;

    /** Index of the logical database to connect to. */
    private final Integer databaseId;
}
