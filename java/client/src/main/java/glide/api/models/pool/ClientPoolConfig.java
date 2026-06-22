/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.pool;

import glide.api.models.configuration.BaseClientConfiguration;
import java.time.Duration;
import lombok.Builder;
import lombok.Getter;

/** Configuration for a client-instance pool. */
@Getter
@Builder
public class ClientPoolConfig {

    /** Maximum clients in the pool. Default: 10. */
    @Builder.Default private final int maxSize = 10;

    /** Minimum idle clients to pre-warm. Default: 1. */
    @Builder.Default private final int minIdle = 1;

    /** Acquire timeout. Default: 5 seconds. */
    @Builder.Default private final Duration acquireTimeout = Duration.ofSeconds(5);

    /** Idle eviction timeout. Default: 5 minutes. */
    @Builder.Default private final Duration idleTimeout = Duration.ofSeconds(300);

    /** Request timeout (for cleanup operations). Default: 5 seconds. */
    @Builder.Default private final Duration requestTimeout = Duration.ofSeconds(5);

    /** Send PING on borrow to verify connection health. Default: false. */
    @Builder.Default private final boolean testOnBorrow = false;

    /** The client configuration (addresses, TLS, auth, etc.). */
    private final BaseClientConfiguration clientConfig;

    public void validate() {
        if (maxSize < 1) throw new IllegalArgumentException("maxSize must be >= 1");
        if (minIdle > maxSize) throw new IllegalArgumentException("minIdle must be <= maxSize");
        if (clientConfig == null) throw new IllegalArgumentException("clientConfig is required");
    }
}
