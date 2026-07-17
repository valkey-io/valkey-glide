/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.models.exceptions.ConfigurationError;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Enumeration;
import lombok.Builder;
import lombok.Getter;

/**
 * Advanced TLS configuration settings class for creating a client. Shared settings for standalone
 * and cluster clients.
 *
 * <p>Mutual TLS (mTLS) is configured through the single intent-revealing {@code useMutualTls}
 * builder method rather than by setting individual certificate fields, which keeps invalid
 * combinations unrepresentable:
 *
 * <ul>
 *   <li>{@link TlsAdvancedConfigurationBuilder#useMutualTls(byte[], byte[])} - mTLS from an
 *       in-memory PEM certificate and key, loaded once (cannot reload).
 *   <li>{@link TlsAdvancedConfigurationBuilder#useMutualTls(String, String)} - mTLS from a
 *       certificate and key read by the GLIDE core from disk, loaded once (no reload).
 *   <li>{@link TlsAdvancedConfigurationBuilder#useMutualTls(String, String, Integer)} - path-based
 *       mTLS with automatic reloading. Reload is enabled by using this overload; the interval is
 *       optional and, when omitted ({@code null}), the GLIDE core chooses the reload cadence (see
 *       {@link #certReloadIntervalSeconds}).
 * </ul>
 *
 * <p>Enablement and interval are separate: using the three-argument overload turns reloading on,
 * and the interval only overrides the cadence the core would otherwise pick. Automatic reloading
 * requires filesystem paths, so it is only offered for the path-based overload; in-memory
 * certificates are inherently static.
 */
@Getter
@Builder
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification =
                "Builder validates TLS invariants at construction time and throws before exposing"
                        + " instance")
public class TlsAdvancedConfiguration {

    /**
     * Whether to bypass TLS certificate verification.
     *
     * <p>When set to True, the client skips certificate validation. This is useful when connecting to
     * servers or clusters using self-signed certificates, or when DNS entries (e.g., CNAMEs) don't
     * match certificate hostnames.
     *
     * <p>This setting is typically used in development or testing environments. <b>It is strongly
     * discouraged in production</b>, as it introduces security risks such as man-in-the-middle
     * attacks.
     *
     * <p>Only valid if TLS is already enabled in the base client configuration. Enabling it without
     * TLS will result in a `ConfigurationError`.
     *
     * <p>Default: False (verification is enforced).
     */
    @Builder.Default private final boolean useInsecureTLS = false;

    /**
     * Custom root certificate data for TLS connections.
     *
     * <p>When provided, these certificates will be used instead of the system's default trust store.
     * If null, the system's default certificate trust store will be used.
     *
     * <p>The certificate data should be in PEM format as a byte array.
     */
    @Builder.Default private final byte[] rootCertificates = null;

    /**
     * Client certificate data for mutual TLS (mTLS) authentication.
     *
     * <p>Configured via {@link TlsAdvancedConfigurationBuilder#useMutualTls(byte[], byte[])}; the
     * client presents this certificate to the server when the server requires client certificate
     * authentication. Always paired with {@link #clientKey}.
     *
     * <p>If null (default), no in-memory client certificate is presented.
     *
     * <p>The certificate data should be in PEM format as a byte array.
     */
    private final byte[] clientCertificate;

    /**
     * Client private key data for mutual TLS (mTLS) authentication.
     *
     * <p>Configured via {@link TlsAdvancedConfigurationBuilder#useMutualTls(byte[], byte[])}. This
     * private key corresponds to {@link #clientCertificate} and is always paired with it.
     *
     * <p>If null (default), no in-memory client key is used.
     *
     * <p>The key data should be in PEM format as a byte array.
     */
    private final byte[] clientKey;

    /**
     * Filesystem path to the client certificate (PEM) for mutual TLS (mTLS) authentication.
     *
     * <p>Configured via the path-based {@code useMutualTls} overloads ({@link
     * TlsAdvancedConfigurationBuilder#useMutualTls(String, String)} or {@link
     * TlsAdvancedConfigurationBuilder#useMutualTls(String, String, Integer)}), which have the GLIDE
     * core read the certificate from disk. See {@link #certReloadIntervalSeconds} for reload
     * behavior.
     *
     * <p>Path-based and byte-based ({@link #clientCertificate}) client certificate configuration are
     * mutually exclusive; always paired with {@link #clientKeyPath}.
     *
     * <p>If null (default), no path-based client certificate is used.
     */
    private final String clientCertPath;

    /**
     * Filesystem path to the client private key (PEM) for mutual TLS (mTLS) authentication.
     *
     * <p>See {@link #clientCertPath}. Always paired with {@link #clientCertPath}.
     *
     * <p>If null (default), no path-based client key is used.
     */
    private final String clientKeyPath;

    /**
     * Whether automatic reload of the path-based client certificate and key is requested.
     *
     * <p>Set to {@code true} by {@link TlsAdvancedConfigurationBuilder#useMutualTls(String, String,
     * Integer)} regardless of whether an interval was supplied, and {@code false} otherwise. This is
     * what enables reloading; {@link #certReloadIntervalSeconds} only carries the optional cadence
     * override. Keeping enablement separate from the interval lets the "reload on, cadence deferred
     * to the core" state be represented (reload requested with a {@code null} interval).
     *
     * <p>Reloading requires path-based mTLS, so requesting it without {@link #clientCertPath}/{@link
     * #clientKeyPath} is rejected.
     */
    private final boolean certReloadRequested;

    /**
     * Optional override, in seconds, for the interval between automatic reload checks of the
     * path-based client certificate and key.
     *
     * <p>Only meaningful when {@link #certReloadRequested} is {@code true}:
     *
     * <ul>
     *   <li>{@code null}: no override; the GLIDE core chooses the reload cadence. When no override is
     *       supplied the core uses its default cadence (currently 300 seconds; see <a
     *       href="https://github.com/valkey-io/valkey-glide/blob/06bd09e1549e1ec5c8fced77a85a417a8573236f/glide-core/src/tls_reload/mod.rs#L44">{@code
     *       DEFAULT_RELOAD_INTERVAL_SECONDS}</a> in glide-core). The three-argument {@code
     *       useMutualTls} overload leaves this {@code null} when its interval argument is {@code
     *       null}.
     *   <li>A positive value: overrides the cadence, so the core re-reads the certificate and key
     *       files at that interval. On a successful reload (the material parses and the private key
     *       matches the certificate), the new material is adopted on the next reconnect; on any
     *       failure, the previously loaded material is kept (last-known-good).
     * </ul>
     *
     * <p>Set via {@link TlsAdvancedConfigurationBuilder#useMutualTls(String, String, Integer)}. A
     * non-positive value passed to that overload is rejected; "no reload" is expressed by the
     * two-argument path overload instead.
     *
     * <p>Root/CA certificate reload is out of scope; only the client certificate and key are
     * reloaded.
     */
    private final Integer certReloadIntervalSeconds;

    /**
     * Creates a new TlsAdvancedConfiguration. Validates self-contained TLS invariants on
     * construction.
     *
     * <p>Use {@link #builder()} to create instances.
     */
    TlsAdvancedConfiguration(
            boolean useInsecureTLS,
            byte[] rootCertificates,
            byte[] clientCertificate,
            byte[] clientKey,
            String clientCertPath,
            String clientKeyPath,
            boolean certReloadRequested,
            Integer certReloadIntervalSeconds) {
        this.useInsecureTLS = useInsecureTLS;
        this.rootCertificates = rootCertificates;
        this.clientCertificate = clientCertificate;
        this.clientKey = clientKey;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.certReloadRequested = certReloadRequested;
        this.certReloadIntervalSeconds = certReloadIntervalSeconds;
        validate();
    }

    /**
     * Validates self-contained TLS configuration invariants.
     *
     * <p>The public builder API can only produce valid mutual-TLS combinations; these checks are a
     * backstop and also guard the empty-byte-array and reload-interval cases that the intent methods
     * accept as arguments.
     *
     * @throws ConfigurationError if any invariant is violated.
     */
    private void validate() {
        boolean hasCert = clientCertificate != null;
        boolean hasKey = clientKey != null;
        boolean hasCertPath = clientCertPath != null;
        boolean hasKeyPath = clientKeyPath != null;

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

        if (hasCertPath && hasCert) {
            throw new ConfigurationError(
                    "`clientCertPath` and `clientCertificate` cannot both be provided; use one or"
                            + " the other.");
        }

        if (hasCert && !hasKey) {
            throw new ConfigurationError(
                    "`clientCertificate` is provided but `clientKey` is not provided. mTLS requires"
                            + " both.");
        }
        if (hasKey && !hasCert) {
            throw new ConfigurationError(
                    "`clientKey` is provided but `clientCertificate` is not provided. mTLS requires"
                            + " both.");
        }

        if (hasCert && clientCertificate.length == 0) {
            throw new ConfigurationError(
                    "`clientCertificate` cannot be an empty byte array; use null if not providing a"
                            + " client certificate.");
        }
        if (hasKey && clientKey.length == 0) {
            throw new ConfigurationError(
                    "`clientKey` cannot be an empty byte array; use null if not providing a client"
                            + " key.");
        }

        // Enablement and interval are separate. When reload is requested, a supplied interval must
        // be positive; a non-positive value is rejected because "no reload" is expressed by the
        // two-argument path overload, not by passing 0 here. A null interval is allowed and means
        // the core chooses the cadence.
        if (certReloadRequested
                && certReloadIntervalSeconds != null
                && certReloadIntervalSeconds <= 0) {
            throw new ConfigurationError(
                    "`certReloadIntervalSeconds` must be positive; omit it (null) to defer to the GLIDE"
                            + " core's default cadence.");
        }

        // Reloading requires path-based mTLS; key the check off the reload-requested flag, since a
        // deferred (null) interval still enables reloading.
        if (certReloadRequested && clientCertPath == null) {
            throw new ConfigurationError(
                    "certificate reload requires `clientCertPath` and `clientKeyPath` to be provided.");
        }
    }

    /**
     * Create TlsAdvancedConfiguration from a Java KeyStore file.
     *
     * @param keyStorePath Path to the KeyStore file
     * @param keyStorePassword Password for the KeyStore
     * @param keyStoreType KeyStore type (e.g., "JKS", "PKCS12")
     * @return TlsAdvancedConfiguration with certificates from KeyStore
     * @throws KeyStoreException if KeyStore type is not supported or KeyStore cannot be accessed
     * @throws IOException if KeyStore file cannot be read
     * @throws NoSuchAlgorithmException if integrity check algorithm is not available
     * @throws CertificateException if certificates cannot be loaded or encoded
     */
    public static TlsAdvancedConfiguration fromKeyStore(
            String keyStorePath, char[] keyStorePassword, String keyStoreType)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {

        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, keyStorePassword);
        }

        StringBuilder pemBuilder = new StringBuilder();
        Enumeration<String> aliases = keyStore.aliases();
        Encoder base64Encoder = Base64.getEncoder();
        final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----\n";
        final String END_CERT = "\n-----END CERTIFICATE-----\n";

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isCertificateEntry(alias)) {
                Certificate cert = keyStore.getCertificate(alias);
                pemBuilder.append(BEGIN_CERT);
                pemBuilder.append(base64Encoder.encodeToString(cert.getEncoded()));
                pemBuilder.append(END_CERT);
            }
        }

        return TlsAdvancedConfiguration.builder()
                .useInsecureTLS(false)
                .rootCertificates(pemBuilder.toString().getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Builder for {@link TlsAdvancedConfiguration}.
     *
     * <p>Mutual TLS (mTLS) is configured exclusively through the {@code useMutualTls} overloads
     * below. The individual client-certificate setters are intentionally not part of the public API,
     * so callers cannot assemble an invalid combination (a certificate without its key, mixed
     * byte/path sources, or reload without a path).
     */
    public static class TlsAdvancedConfigurationBuilder {

        /**
         * Enables mutual TLS (mTLS) using an in-memory client certificate and private key.
         *
         * <p>The certificate and key are loaded once from the supplied bytes; the material is static
         * for the lifetime of the client and cannot reload. Use {@link #useMutualTls(String, String,
         * Integer)} for automatic rotation.
         *
         * @param clientCert PEM-encoded client certificate bytes. Must be non-null and non-empty.
         * @param clientKey PEM-encoded client private key bytes corresponding to {@code clientCert}.
         *     Must be non-null and non-empty.
         * @return this builder instance
         */
        public TlsAdvancedConfigurationBuilder useMutualTls(byte[] clientCert, byte[] clientKey) {
            this.clientCertificate = clientCert;
            this.clientKey = clientKey;
            return this;
        }

        /**
         * Enables mutual TLS (mTLS) using client certificate and key files read from disk by the GLIDE
         * core.
         *
         * <p>The certificate and key are read once when the client connects; the files are not re-read
         * afterwards. Use {@link #useMutualTls(String, String, Integer)} to automatically pick up
         * rotated certificates.
         *
         * @param clientCertPath Filesystem path to the PEM-encoded client certificate.
         * @param clientKeyPath Filesystem path to the PEM-encoded client private key.
         * @return this builder instance
         */
        public TlsAdvancedConfigurationBuilder useMutualTls(
                String clientCertPath, String clientKeyPath) {
            this.clientCertPath = clientCertPath;
            this.clientKeyPath = clientKeyPath;
            return this;
        }

        /**
         * Enables mutual TLS (mTLS) from certificate and key files, with automatic reloading. Using
         * this overload turns reloading on; {@code reloadIntervalSeconds} is an optional cadence
         * override. See {@link #certReloadIntervalSeconds} for the full reload semantics.
         *
         * @param clientCertPath Filesystem path to the PEM-encoded client certificate.
         * @param clientKeyPath Filesystem path to the PEM-encoded client private key.
         * @param reloadIntervalSeconds Optional override for the reload interval, in seconds. Pass
         *     {@code null} to defer the cadence to the GLIDE core's default (the core owns the default
         *     value; see {@link #certReloadIntervalSeconds}). A positive value overrides that cadence.
         *     A value {@code <= 0} is rejected; to load the certificate once without reloading, use
         *     {@link #useMutualTls(String, String)} instead.
         * @return this builder instance
         */
        public TlsAdvancedConfigurationBuilder useMutualTls(
                String clientCertPath, String clientKeyPath, Integer reloadIntervalSeconds) {
            this.clientCertPath = clientCertPath;
            this.clientKeyPath = clientKeyPath;
            this.certReloadRequested = true;
            this.certReloadIntervalSeconds = reloadIntervalSeconds;
            return this;
        }

        // The individual mutual-TLS setters below are hidden from the public API so that mTLS can only
        // be configured through the intent-revealing methods above, which always produce a valid
        // combination. Declaring them here suppresses Lombok's public setter generation for these
        // fields.

        private TlsAdvancedConfigurationBuilder clientCertificate(byte[] clientCertificate) {
            this.clientCertificate = clientCertificate;
            return this;
        }

        private TlsAdvancedConfigurationBuilder clientKey(byte[] clientKey) {
            this.clientKey = clientKey;
            return this;
        }

        private TlsAdvancedConfigurationBuilder clientCertPath(String clientCertPath) {
            this.clientCertPath = clientCertPath;
            return this;
        }

        private TlsAdvancedConfigurationBuilder clientKeyPath(String clientKeyPath) {
            this.clientKeyPath = clientKeyPath;
            return this;
        }

        private TlsAdvancedConfigurationBuilder certReloadRequested(boolean certReloadRequested) {
            this.certReloadRequested = certReloadRequested;
            return this;
        }

        private TlsAdvancedConfigurationBuilder certReloadIntervalSeconds(
                Integer certReloadIntervalSeconds) {
            this.certReloadIntervalSeconds = certReloadIntervalSeconds;
            return this;
        }
    }
}
