/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents the credentials for connecting to a server.
 *
 * <p>Supports two authentication modes:
 *
 * <ul>
 *   <li><b>Password-based authentication:</b> Use {@code password} (and optionally {@code
 *       username})
 *   <li><b>IAM authentication:</b> Use {@code username} (required) and {@code iamConfig}
 * </ul>
 *
 * <p>These modes are mutually exclusive - you cannot use both {@code password} and {@code
 * iamConfig} at the same time.
 *
 * @example
 *     <pre>{@code
 * // Password-based credentials with username:
 * ServerCredentials credentials1 = ServerCredentials.builder()
 *     .username("GLIDE")
 *     .password("myPassword")
 *     .build();
 *
 * // IAM-based credentials:
 * ServerCredentials credentials2 = ServerCredentials.builder()
 *     .username("myUser")  // Required for IAM
 *     .iamConfig(IamAuthConfig.builder()
 *         .clusterName("my-cluster")
 *         .service(ServiceType.ELASTICACHE)
 *         .region("us-east-1")
 *         .build())
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class ServerCredentials {
    /**
     * The password that will be used for authenticating connections to the servers. Mutually
     * exclusive with {@code iamConfig}.
     */
    private final String password;

    /**
     * The username that will be used for authenticating connections to the servers. If not supplied
     * for password-based authentication, "default" will be used. Required for IAM authentication.
     */
    private final String username;

    /**
     * IAM authentication configuration. Mutually exclusive with {@code password}. The client will
     * automatically generate and refresh the authentication token based on the provided
     * configuration.
     */
    private final IamAuthConfig iamConfig;

    // Private constructor with validation - called by Lombok's builder
    // Parameter order MUST match field declaration order for Lombok
    private ServerCredentials(String password, String username, IamAuthConfig iamConfig) {
        // Validate mutual exclusivity
        if (password != null && iamConfig != null) {
            throw new IllegalArgumentException(
                    "password and iamConfig are mutually exclusive. Use either password-based or IAM"
                            + " authentication, not both.");
        }

        // Validate IAM requires username
        if (iamConfig != null && username == null) {
            throw new IllegalArgumentException("username is required for IAM authentication.");
        }

        this.password = password;
        this.username = username;
        this.iamConfig = iamConfig;
    }
}
