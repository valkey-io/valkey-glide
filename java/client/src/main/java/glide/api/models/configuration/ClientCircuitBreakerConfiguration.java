/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration for the client-wide circuit breaker.
 *
 * <p>The circuit breaker detects when the GLIDE core is unhealthy (sustained error rate across all
 * nodes) and rejects requests synchronously at the FFI boundary before threads park. This prevents
 * thread explosion under degraded conditions.
 *
 * <p>Disabled by default. Pass an instance to the client builder to enable.
 *
 * @example
 *     <pre>{@code
 * ClientCircuitBreakerConfiguration.builder()
 *     .windowSizeMs(10000)
 *     .failureRateThreshold(0.5f)
 *     .minErrors(50)
 *     .openTimeoutMs(5000)
 *     .countTimeouts(false)
 *     .consecutiveSuccesses(3)
 *     .build();
 * }</pre>
 */
@Getter
@Builder
@ToString
public class ClientCircuitBreakerConfiguration {

    /** Sliding window duration in milliseconds for error rate calculation. Default: 10000 (10s). */
    @Builder.Default private final int windowSizeMs = 10000;

    /** Error rate threshold (0.0-1.0) within the window to trip the breaker. Default: 0.5 (50%). */
    @Builder.Default private final float failureRateThreshold = 0.5f;

    /**
     * Minimum number of errors within the window before the rate is evaluated. Prevents tripping on
     * small sample sizes. Default: 50.
     */
    @Builder.Default private final int minErrors = 50;

    /** Time in milliseconds in Open state before allowing a probe request. Default: 5000 (5s). */
    @Builder.Default private final int openTimeoutMs = 5000;

    /**
     * Whether command timeouts count toward tripping the breaker. Default: false. Set to true only if
     * timeouts reliably indicate server-side issues rather than client-side Tokio starvation.
     */
    @Builder.Default private final boolean countTimeouts = false;

    /**
     * Number of consecutive successful probe requests needed before closing the breaker. Provides a
     * grace period to prevent flapping. Default: 3.
     */
    @Builder.Default private final int consecutiveSuccesses = 3;
}
