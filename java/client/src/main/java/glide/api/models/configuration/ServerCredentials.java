/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents the credentials for connecting to a server.
 *
 * @example
 *     <pre>{@code
 * // credentials with password only:
 * ServerCredentials credentials1 = ServerCredentials.builder()
 *     .password(pwd)
 *     .build();
 * // credentials with username and password:
 * ServerCredentials credentials2 = ServerCredentials.builder()
 *     .username("GLIDE")
 *     .password(pwd)
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class ServerCredentials {
    /** The password that will be used for authenticating connections to the servers. */
    private final String password;

    /**
     * The username that will be used for authenticating connections to the servers. If not supplied,
     * "default" will be used.
     */
    private final String username;
}
