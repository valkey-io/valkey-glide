/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Advanced configuration settings class for creating a client. Shared settings for standalone and
 * cluster clients.
 */
@Getter
@SuperBuilder
public abstract class AdvancedBaseClientConfiguration {

    /**
     * The duration in milliseconds to wait for a TCP/TLS connection to complete. This applies both
     * during initial client creation and any reconnection that may occur during request processing.
     *
     * <p>If not explicitly set, a default value of 2000 milliseconds will be used by the Rust core.
     *
     * <p>**Note**: A high connection timeout may lead to prolonged blocking of the entire command
     * pipeline.
     */
    private final Integer connectionTimeout;

    /**
     * The advanced TLS configuration settings.
     *
     * <p>This allows for more granular control of TLS behavior, such as enabling an insecure mode
     * that bypasses certificate validation.
     */
    @Builder.Default
    private final TlsAdvancedConfiguration tlsAdvancedConfiguration =
            TlsAdvancedConfiguration.builder().build();
}
