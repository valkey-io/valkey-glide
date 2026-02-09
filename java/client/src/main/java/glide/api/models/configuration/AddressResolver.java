/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/**
 * A functional interface for resolving server addresses before connection.
 *
 * <p>This callback allows custom DNS resolution or address translation logic to be applied when the
 * client connects to Valkey/Redis servers. The resolver is invoked with the configured host and
 * port, and should return the actual host and port to use for the connection.
 *
 * <p>Example use cases:
 *
 * <ul>
 *   <li>Custom DNS resolution for service discovery
 *   <li>Address translation for proxy setups
 *   <li>Dynamic endpoint resolution for cloud environments
 * </ul>
 *
 * @example
 *     <pre>{@code
 * AddressResolver resolver = (host, port) -> {
 *     // Custom resolution logic
 *     String resolvedHost = myDnsResolver.resolve(host);
 *     return new ResolvedAddress(resolvedHost, port);
 * };
 *
 * GlideClientConfiguration config = GlideClientConfiguration.builder()
 *     .address(NodeAddress.builder().host("my-service").port(6379).build())
 *     .addressResolver(resolver)
 *     .build();
 * }</pre>
 */
@FunctionalInterface
public interface AddressResolver {

    /**
     * Resolves the given host and port to the actual connection address.
     *
     * @param host The configured host name or IP address
     * @param port The configured port number
     * @return A {@link ResolvedAddress} containing the resolved host and port to use for connection
     */
    ResolvedAddress resolve(String host, int port);
}
