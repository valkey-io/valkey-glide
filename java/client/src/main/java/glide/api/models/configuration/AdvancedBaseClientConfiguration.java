/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class AdvancedBaseClientConfiguration {

    /**
     * The duration in milliseconds that the client will wait for a connection to be established. If
     * the connection attempt does not complete within this time frame, a connection timeout error
     * will occur. If not set, a default value will be used.
     */
    private final Integer connectionTimeout;
}
