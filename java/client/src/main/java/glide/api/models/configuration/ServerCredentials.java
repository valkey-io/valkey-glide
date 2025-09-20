/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.models.exceptions.ConfigurationError;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents the credentials for connecting to a server.
 *
 * @example
 *     <pre>{@code
 * // credentials with username:
 * ServerCredentials credentials1 = ServerCredentials.builder()
 *     .username("GLIDE")
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
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification =
                "Builder validates credential mode before customers observe the instance")
public class ServerCredentials {
    /** The password that will be used for authenticating connections to the servers. */
    private final String password;

    /**
     * The username that will be used for authenticating connections to the servers. If not supplied,
     * "default" will be used. Username is required when IAM authentication is configured.
     */
    private final String username;

    /** Optional IAM authentication configuration. Mutually exclusive with {@link #password}. */
    private final IamAuthConfig iamAuthConfig;

    /** Returns {@code true} if IAM authentication is configured. */
    public boolean isUsingIamAuth() {
        return iamAuthConfig != null;
    }

    public static class ServerCredentialsBuilder {
        public ServerCredentials build() {
            if (password != null && iamAuthConfig != null) {
                throw new ConfigurationError(
                        "Server credentials cannot define both password and IAM configuration.");
            }

            if (iamAuthConfig != null && (username == null || username.isBlank())) {
                throw new ConfigurationError(
                        "IAM authentication requires a non-empty username.");
            }

            if (password == null && iamAuthConfig == null) {
                throw new ConfigurationError(
                        "Server credentials must define either a password or an IAM configuration.");
            }

            return new ServerCredentials(password, username, iamAuthConfig);
        }
    }
}
