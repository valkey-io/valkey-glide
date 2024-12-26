/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

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
     * during initial client creation and any reconnections that may occur during request processing.
     * **Note**: A high connection timeout may lead to prolonged blocking of the entire command
     * pipeline. If not explicitly set, a default value of 250 milliseconds will be used.
     */
    private final Integer connectionTimeout;
}
