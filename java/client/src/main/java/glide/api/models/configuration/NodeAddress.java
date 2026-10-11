/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Represents the address of a node in the cluster or in standalone installation: a host and port,
 * or a Unix domain socket.
 *
 * @example
 *     <pre>{@code
 * NodeAddress address1 = NodeAddress.builder().build(); // default parameters: localhost:6379
 * NodeAddress address2 = NodeAddress.builder().port(6380).build(); // localhost:6380
 * NodeAddress address3 = NodeAddress.builder().host("my.cloud.com").port(12345).build(); // custom address
 * NodeAddress address4 = NodeAddress.builder().unixSocketPath("/run/valkey/valkey.sock").build();
 * }</pre>
 */
@Getter
@Builder
@ToString
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Builder enforces non-null/default invariants and throws early")
public class NodeAddress {
    public static final String DEFAULT_HOST = "localhost";
    public static final Integer DEFAULT_PORT = 6379;

    @NonNull @Builder.Default private final String host = DEFAULT_HOST;
    @NonNull @Builder.Default private final Integer port = DEFAULT_PORT;

    /**
     * Absolute path of the server's Unix domain socket. When set, {@code host} and {@code port} are
     * ignored. Standalone clients only; not supported on Windows, with TLS, with {@link
     * NodeDiscoveryMode#DISCOVER_ALL}, or mixed with TCP addresses.
     */
    private final String unixSocketPath;
}
