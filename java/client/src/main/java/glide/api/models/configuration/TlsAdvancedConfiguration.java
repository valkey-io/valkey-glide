/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.models.exceptions.ConfigurationError;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
 * <p>Mutual TLS (mTLS) is configured through the intent-revealing {@code useMutualTls} and {@code
 * useMutualTlsWithReload} builder methods rather than by setting individual certificate fields,
 * which keeps invalid combinations unrepresentable:
 *
 * <ul>
 *   <li>{@link TlsAdvancedConfigurationBuilder#useMutualTls(byte[], byte[])} - in-memory PEM
 *       certificate and key, loaded once (load file bytes via {@link
 *       TlsAdvancedConfigurationBuilder#loadClientCertificateFromFile}/{@link
 *       TlsAdvancedConfigurationBuilder#loadClientKeyFromFile}).
 *   <li>{@link TlsAdvancedConfigurationBuilder#useMutualTlsWithReload(String, String)} - path-based
 *       mTLS reloading at the core's default cadence (see {@link #certReloadIntervalSeconds}).
 *   <li>{@link TlsAdvancedConfigurationBuilder#useMutualTlsWithReload(String, String, int)} -
 *       path-based mTLS reloading every {@code intervalSecs} seconds.
 * </ul>
 *
 * <p>Using either {@code useMutualTlsWithReload} overload enables reloading; the explicit interval
 * only overrides the cadence. Reloading requires filesystem paths, so in-memory certificates are
 * inherently static.
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
     * PEM-encoded client certificate for in-memory mTLS, set via {@link
     * TlsAdvancedConfigurationBuilder#useMutualTls(byte[], byte[])} and paired with {@link
     * #clientKey}. If null (default), no in-memory client certificate is presented.
     */
    private final byte[] clientCertificate;

    /**
     * PEM-encoded client private key for in-memory mTLS, corresponding to {@link #clientCertificate}
     * and always paired with it. If null (default), no in-memory client key is used.
     */
    private final byte[] clientKey;

    /**
     * Filesystem path to the PEM client certificate, set via the {@code useMutualTlsWithReload}
     * overloads ({@link TlsAdvancedConfigurationBuilder#useMutualTlsWithReload(String, String)},
     * {@link TlsAdvancedConfigurationBuilder#useMutualTlsWithReload(String, String, int)}) so the
     * GLIDE core reads it from disk; see {@link #certReloadIntervalSeconds} for reload behavior.
     * Path- and byte-based ({@link #clientCertificate}) configuration are mutually exclusive, and
     * this is always paired with {@link #clientKeyPath}. If null (default), no path-based certificate
     * is used.
     */
    private final String clientCertPath;

    /**
     * Filesystem path to the PEM client private key; see {@link #clientCertPath}, with which it is
     * always paired. If null (default), no path-based client key is used.
     */
    private final String clientKeyPath;

    /**
     * Optional override, in seconds, for the automatic reload interval; only meaningful when
     * path-based mTLS reloading is enabled (that is, when {@link #clientCertPath} is set via a {@code
     * useMutualTlsWithReload} overload).
     *
     * <ul>
     *   <li>{@code null} (set by {@link
     *       TlsAdvancedConfigurationBuilder#useMutualTlsWithReload(String, String)}): the core uses
     *       its default cadence, currently 300 seconds (see <a
     *       href="https://github.com/valkey-io/valkey-glide/blob/06bd09e1549e1ec5c8fced77a85a417a8573236f/glide-core/src/tls_reload/mod.rs#L44">{@code
     *       DEFAULT_RELOAD_INTERVAL_SECONDS}</a> in glide-core).
     *   <li>A positive value (set by {@link
     *       TlsAdvancedConfigurationBuilder#useMutualTlsWithReload(String, String, int)}): the core
     *       re-reads the files at that interval. A successful reload (material parses and key matches
     *       the certificate) is adopted on the next reconnect; on failure the last-known-good
     *       material is kept. A non-positive value is rejected; use {@link
     *       TlsAdvancedConfigurationBuilder#useMutualTls(byte[], byte[])} for static (no-reload)
     *       mTLS.
     * </ul>
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
            Integer certReloadIntervalSeconds) {
        this.useInsecureTLS = useInsecureTLS;
        this.rootCertificates = rootCertificates;
        this.clientCertificate = clientCertificate;
        this.clientKey = clientKey;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
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

        // Enablement and interval are separate. When path-based reloading is enabled (a cert path is
        // set), a supplied interval must be positive; a non-positive value is rejected because static
        // (no-reload) mTLS is expressed by the byte-based useMutualTls overload, not by passing 0
        // here. A null interval is allowed and means the core chooses the cadence.
        if (clientCertPath != null
                && certReloadIntervalSeconds != null
                && certReloadIntervalSeconds <= 0) {
            throw new ConfigurationError(
                    "`certReloadIntervalSeconds` must be positive; omit it (null) to defer to the GLIDE"
                            + " core's default cadence.");
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
     * <p>Mutual TLS (mTLS) is configured exclusively through the {@code useMutualTls} and {@code
     * useMutualTlsWithReload} methods below. The individual client-certificate setters are
     * intentionally not part of the public API, so callers cannot assemble an invalid combination (a
     * certificate without its key, mixed byte/path sources, or reload without a path).
     */
    public static class TlsAdvancedConfigurationBuilder {

        /**
         * Enables mutual TLS (mTLS) using in-memory client certificate and key bytes, loaded once
         * (static, no reload). To load static material from files, pass the loader results here:
         *
         * <pre>{@code
         * builder.useMutualTls(
         *     TlsAdvancedConfigurationBuilder.loadClientCertificateFromFile(certPath),
         *     TlsAdvancedConfigurationBuilder.loadClientKeyFromFile(keyPath));
         * }</pre>
         *
         * <p>For automatic rotation of on-disk material, use {@link #useMutualTlsWithReload} instead.
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
         * Reads a PEM-encoded client certificate file into raw bytes. Convenience loader mirroring the
         * Go and Python clients; combine it with {@link #loadClientKeyFromFile(String)} and pass the
         * results to {@link #useMutualTls(byte[], byte[])} for static mTLS from files.
         *
         * @param path Filesystem path to the PEM-encoded client certificate.
         * @return the certificate bytes in PEM format
         * @throws IOException if the file cannot be read (for example, it does not exist)
         */
        public static byte[] loadClientCertificateFromFile(String path) throws IOException {
            return Files.readAllBytes(Paths.get(path));
        }

        /**
         * Reads a PEM-encoded client private key file into raw bytes; the key counterpart of {@link
         * #loadClientCertificateFromFile(String)}.
         *
         * @param path Filesystem path to the PEM-encoded client private key.
         * @return the private key bytes in PEM format
         * @throws IOException if the file cannot be read (for example, it does not exist)
         */
        public static byte[] loadClientKeyFromFile(String path) throws IOException {
            return Files.readAllBytes(Paths.get(path));
        }

        /**
         * Enables path-based mTLS with automatic reloading at the GLIDE core's default cadence: the
         * core reads the files from disk and periodically re-reads them to pick up rotated material.
         * The cadence is deferred to the core's default (see {@link #certReloadIntervalSeconds}); use
         * {@link #useMutualTlsWithReload(String, String, int)} to override it.
         *
         * @param clientCertPath Filesystem path to the PEM-encoded client certificate.
         * @param clientKeyPath Filesystem path to the PEM-encoded client private key.
         * @return this builder instance
         */
        public TlsAdvancedConfigurationBuilder useMutualTlsWithReload(
                String clientCertPath, String clientKeyPath) {
            this.clientCertPath = clientCertPath;
            this.clientKeyPath = clientKeyPath;
            this.certReloadIntervalSeconds = null;
            return this;
        }

        /**
         * Enables path-based mTLS with automatic reloading every {@code intervalSecs} seconds: the core
         * re-reads the files at that interval to pick up rotated material. See {@link
         * #certReloadIntervalSeconds} for the full reload semantics.
         *
         * @param clientCertPath Filesystem path to the PEM-encoded client certificate.
         * @param clientKeyPath Filesystem path to the PEM-encoded client private key.
         * @param intervalSecs Reload interval in seconds. Must be positive; a value {@code <= 0} is
         *     rejected with a {@link ConfigurationError}.
         * @return this builder instance
         */
        public TlsAdvancedConfigurationBuilder useMutualTlsWithReload(
                String clientCertPath, String clientKeyPath, int intervalSecs) {
            this.clientCertPath = clientCertPath;
            this.clientKeyPath = clientKeyPath;
            this.certReloadIntervalSeconds = intervalSecs;
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

        private TlsAdvancedConfigurationBuilder certReloadIntervalSeconds(
                Integer certReloadIntervalSeconds) {
            this.certReloadIntervalSeconds = certReloadIntervalSeconds;
            return this;
        }
    }
}
