/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Advanced TLS configuration settings class for creating a client. Shared settings for standalone
 * and cluster clients.
 */
@Getter
@SuperBuilder
public class TlsAdvancedConfiguration {

    /**
     * True if communication with the cluster should check certificate validity.
     *
     * <p>If the server/cluster's certificate does not validate, not setting this will cause the
     * connection attempt to fail.
     *
     * <p>If the server/cluster's certificate does not validate, setting this will cause the
     * connection to ignore the certificate's validity and succeed.
     *
     * <p>This is useful for when CNAMEs are used to point to a server/cluster.
     */
    @Builder.Default private final boolean useInsecureTLS = false;
}
