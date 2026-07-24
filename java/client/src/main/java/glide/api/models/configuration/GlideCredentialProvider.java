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
 * generated. It must return the current AWS credentials as a {@code String[]} with at least 2
 * elements:
 *
 * <ol>
 *   <li>AWS Access Key ID (required, must not be {@code null})
 *   <li>AWS Secret Access Key (required, must not be {@code null})
 *   <li>AWS Session Token (optional; pass {@code null} or omit for long-term credentials)
 * </ol>
 *
 * @see IamAuthConfig#getCredentialsProvider()
 * @example
 *     <pre>{@code
 * GlideCredentialProvider vaultCallback = () -> new String[]{
 *     vaultClient.getAccessKeyId(),
 *     vaultClient.getSecretAccessKey(),
 *     vaultClient.getSessionToken() // may be null
 * };
 * }</pre>
 */
@FunctionalInterface
public interface GlideCredentialProvider {

    /**
     * Retrieve the current AWS credentials.
     *
     * @return a {@code String[]} with at least 2 elements: {@code [accessKeyId, secretAccessKey,
     *     sessionToken?]}. The session token element (index 2) may be {@code null} or omitted for
     *     non-session credentials.
     * @throws Exception if credentials cannot be retrieved
     */
    String[] getCredentials() throws Exception;
}
