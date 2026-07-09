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

    /**
     * Returns the client certificate file path for path-based mutual TLS, or {@code null} if not
     * configured.
     *
     * <p>Validates that the certificate path and key path are both provided together, and that
     * path-based and byte-based client certificate configuration are not mixed.
     *
     * @throws ConfigurationError if only one of the certificate/key path is provided, or if path-
     *     based and byte-based client certificate configuration are both provided.
     */
    public static String extractClientCertPath(BaseClientConfiguration configuration) {
        TlsAdvancedConfiguration tlsConfig = getTlsConfig(configuration);
        if (tlsConfig == null) {
            return null;
        }
        validateCertPathTls(tlsConfig);
        return tlsConfig.getClientCertPath();
    }

    /**
     * Returns the client key file path for path-based mutual TLS, or {@code null} if not configured.
     *
     * @throws ConfigurationError if only one of the certificate/key path is provided, or if path-
     *     based and byte-based client certificate configuration are both provided.
     */
    public static String extractClientKeyPath(BaseClientConfiguration configuration) {
        TlsAdvancedConfiguration tlsConfig = getTlsConfig(configuration);
        if (tlsConfig == null) {
            return null;
        }
        validateCertPathTls(tlsConfig);
        return tlsConfig.getClientKeyPath();
    }

    /**
     * Returns {@code true} if automatic certificate reload is requested for path-based mTLS.
     *
     * <p>Reload is only meaningful when a client certificate path is configured; enabling it without
     * a certificate path results in a {@link ConfigurationError}.
     *
     * @throws ConfigurationError if reload is enabled without a client certificate path.
     */
    public static boolean isCertReloadEnabled(BaseClientConfiguration configuration) {
        TlsAdvancedConfiguration tlsConfig = getTlsConfig(configuration);
        if (tlsConfig == null) {
            return false;
        }
        if (tlsConfig.isCertReloadEnabled() && tlsConfig.getClientCertPath() == null) {
            throw new ConfigurationError(
                    "`certReloadEnabled` requires `clientCertPath` and `clientKeyPath` to be provided.");
        }
        return tlsConfig.isCertReloadEnabled();
    }

    /**
     * Returns the certificate reload interval in seconds, or {@code null} to use the core default.
     */
    public static Integer extractCertReloadIntervalSeconds(BaseClientConfiguration configuration) {
        TlsAdvancedConfiguration tlsConfig = getTlsConfig(configuration);
        if (tlsConfig == null) {
            return null;
        }
        return tlsConfig.getCertReloadIntervalSeconds();
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

    /**
     * Ensures the path-based client certificate/key are provided together and are not mixed with the
     * byte-based client certificate configuration.
     */
    private static void validateCertPathTls(TlsAdvancedConfiguration tlsConfig) {
        boolean hasCertPath = tlsConfig.getClientCertPath() != null;
        boolean hasKeyPath = tlsConfig.getClientKeyPath() != null;
        if (hasCertPath && !hasKeyPath) {
            throw new ConfigurationError(
                    "`clientCertPath` is provided but `clientKeyPath` is not provided. mTLS requires"
                            + " both.");
        }
        if (hasKeyPath && !hasCertPath) {
            throw new ConfigurationError(
                    "`clientKeyPath` is provided but `clientCertPath` is not provided. mTLS requires"
                            + " both.");
        }
        if (hasCertPath && tlsConfig.getClientCertificate() != null) {
            throw new ConfigurationError(
                    "`clientCertPath` and `clientCertificate` cannot both be provided; use one or the"
                            + " other.");
        }
    }
}
