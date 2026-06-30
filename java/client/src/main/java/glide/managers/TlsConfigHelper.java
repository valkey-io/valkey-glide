/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.AdvancedBaseClientConfiguration;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.api.models.exceptions.ConfigurationError;

/** TLS configuration helpers shared by connection builders. */
public final class TlsConfigHelper {

    private TlsConfigHelper() {}

    /** Returns {@code true} if insecure TLS is requested, throws if misconfigured. */
    public static boolean resolveInsecureTls(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced == null) {
            return false;
        }
        TlsAdvancedConfiguration tlsConfig = advanced.getTlsAdvancedConfiguration();
        if (tlsConfig != null && tlsConfig.isUseInsecureTLS()) {
            if (!configuration.isUseTLS()) {
                throw new ConfigurationError(
                        "`useInsecureTLS` cannot be enabled when `useTLS` is disabled.");
            }
            return true;
        }
        return false;
    }

    /** Returns the root certificates bytes, or {@code null} if not configured. */
    public static byte[] extractRootCertificates(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced == null) {
            return null;
        }
        TlsAdvancedConfiguration tlsConfig = advanced.getTlsAdvancedConfiguration();
        if (tlsConfig == null) {
            return null;
        }
        return tlsConfig.getRootCertificates();
    }

    /**
     * Returns the client certificate bytes for mutual TLS, or {@code null} if not configured.
     *
     * <p>Validates that the client certificate and client key are both provided together (mTLS
     * requires both), and that neither is an empty (non-null, length 0) byte array.
     *
     * @throws ConfigurationError if only one of certificate/key is provided, or if either value is
     *     empty.
     */
    public static byte[] extractClientCertificate(BaseClientConfiguration configuration) {
        TlsAdvancedConfiguration tlsConfig = getTlsConfig(configuration);
        if (tlsConfig == null) {
            return null;
        }
        validateClientAuthTls(tlsConfig);
        byte[] clientCert = tlsConfig.getClientCertificate();
        if (clientCert != null && clientCert.length == 0) {
            throw new ConfigurationError(
                    "`clientCertificate` cannot be an empty byte array; use null if not providing a client"
                            + " certificate.");
        }
        return clientCert;
    }

    /**
     * Returns the client private key bytes for mutual TLS, or {@code null} if not configured.
     *
     * <p>Validates that the client certificate and client key are both provided together (mTLS
     * requires both), and that neither is an empty (non-null, length 0) byte array.
     *
     * @throws ConfigurationError if only one of certificate/key is provided, or if either value is
     *     empty.
     */
    public static byte[] extractClientKey(BaseClientConfiguration configuration) {
        TlsAdvancedConfiguration tlsConfig = getTlsConfig(configuration);
        if (tlsConfig == null) {
            return null;
        }
        validateClientAuthTls(tlsConfig);
        byte[] clientKey = tlsConfig.getClientKey();
        if (clientKey != null && clientKey.length == 0) {
            throw new ConfigurationError(
                    "`clientKey` cannot be an empty byte array; use null if not providing a client key.");
        }
        return clientKey;
    }

    private static TlsAdvancedConfiguration getTlsConfig(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced == null) {
            return null;
        }
        return advanced.getTlsAdvancedConfiguration();
    }

    /** Ensures the client certificate and client key are both provided, or both omitted. */
    private static void validateClientAuthTls(TlsAdvancedConfiguration tlsConfig) {
        boolean hasCert = tlsConfig.getClientCertificate() != null;
        boolean hasKey = tlsConfig.getClientKey() != null;
        if (hasCert && !hasKey) {
            throw new ConfigurationError(
                    "`clientCertificate` is provided but `clientKey` is not provided. mTLS requires both.");
        }
        if (hasKey && !hasCert) {
            throw new ConfigurationError(
                    "`clientKey` is provided but `clientCertificate` is not provided. mTLS requires both.");
        }
    }
}
