/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/**
 * A callback interface used to supply AWS credentials for IAM authentication token signing.
 *
 * <p>This interface is used when a custom credentials source (e.g., HashiCorp Vault, a custom STS
 * assume-role flow, or any other non-standard credential provider) must be used instead of the
 * default AWS credential chain.
 *
 * <p>The callback is invoked by the native Rust layer each time a fresh IAM token needs to be
 * generated. Implement this interface and return an {@link AwsCredentials} instance built with the
 * {@link AwsCredentials#builder()} — the builder ensures that required fields are present and
 * non-blank, and makes each credential field self-documenting at the call site.
 *
 * <pre>{@code
 * // Lambda with session credentials (e.g., from HashiCorp Vault):
 * GlideCredentialProvider provider = () -> AwsCredentials.builder()
 *     .accessKeyId(vaultClient.getAccessKeyId())
 *     .secretAccessKey(vaultClient.getSecretAccessKey())
 *     .sessionToken(vaultClient.getSessionToken()) // optional
 *     .build();
 *
 * // Lambda with long-term credentials (no session token):
 * GlideCredentialProvider staticProvider = () -> AwsCredentials.builder()
 *     .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
 *     .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
 *     .build();
 * }</pre>
 *
 * @see AwsCredentials
 * @see IamAuthConfig#getCredentialsProvider()
 */
@FunctionalInterface
public interface GlideCredentialProvider {

    /**
     * Retrieve the current AWS credentials.
     *
     * @return an {@link AwsCredentials} instance containing the AWS Access Key ID, Secret Access Key,
     *     and an optional Session Token.
     * @throws Exception if credentials cannot be retrieved
     */
    AwsCredentials getCredentials() throws Exception;
}
