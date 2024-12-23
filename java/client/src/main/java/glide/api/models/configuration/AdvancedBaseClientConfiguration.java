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
     * The duration in milliseconds that the client will wait for a connection to be established.
     *
     * <p>This timeout applies both during initial client creation and any reconnections that may
     * occur during request processing. A high connection timeout may lead to prolonged blocking of
     * the entire command pipeline. If the client cannot establish a connection within the specified
     * duration, a timeout error will occur.
     *
     * <p>If not explicitly set, a default value of 250 milliseconds will be used.
     */
    private final Integer connectionTimeout;
}
