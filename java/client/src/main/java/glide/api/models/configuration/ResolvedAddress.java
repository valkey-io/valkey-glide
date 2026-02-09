/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a resolved server address returned by an {@link AddressResolver}.
 *
 * <p>This class holds the actual host and port that should be used for connecting to a Valkey/Redis
 * server after address resolution has been performed.
 *
 * @example
 *     <pre>{@code
 * // Create a resolved address
 * ResolvedAddress resolved = new ResolvedAddress("192.168.1.100", 6379);
 *
 * // Use in an AddressResolver
 * AddressResolver resolver = (host, port) -> {
 *     String actualHost = lookupHost(host);
 *     return new ResolvedAddress(actualHost, port);
 * };
 * }</pre>
 */
@Getter
@RequiredArgsConstructor
public class ResolvedAddress {

    /** The resolved host name or IP address to use for connection. */
    private final String host;

    /** The resolved port number to use for connection. */
    private final int port;
}
