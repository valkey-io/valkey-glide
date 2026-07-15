/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.models.exceptions.ConfigurationError;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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
     * <p>When provided along with {@link #clientKey}, enables mutual TLS authentication so that the
     * client presents its certificate to the server. Use this when the server requires client
     * certificate authentication.
     *
     * <p>If set to an empty (non-null, length 0) byte array, a `ConfigurationError` will be raised.
     *
     * <p>If null (default), no client certificate will be presented.
     *
     * <p>Must be used together with {@link #clientKey}: providing one without the other results in a
     * `ConfigurationError`.
     *
     * <p>The certificate data should be in PEM format as a byte array.
     */
    @Builder.Default private final byte[] clientCertificate = null;

    /**
     * Client private key data for mutual TLS (mTLS) authentication.
     *
     * <p>When provided along with {@link #clientCertificate}, enables mutual TLS authentication. This
     * private key corresponds to the certificate provided in {@link #clientCertificate}.
     *
     * <p>If set to an empty (non-null, length 0) byte array, a `ConfigurationError` will be raised.
     *
     * <p>If null (default), no client key will be used.
     *
     * <p>Must be used together with {@link #clientCertificate}: providing one without the other
     * results in a `ConfigurationError`.
     *
     * <p>The key data should be in PEM format as a byte array.
     */
    @Builder.Default private final byte[] clientKey = null;

    /**
     * Filesystem path to the client certificate (PEM) for mutual TLS (mTLS) authentication.
     *
     * <p>Use this instead of {@link #clientCertificate} to have the GLIDE core read the certificate
     * from disk. When combined with {@link #certReloadEnabled}, the core periodically re-reads the
     * file so that a rotated certificate is adopted on the next reconnect, without recreating the
     * client.
     *
     * <p>Must be used together with {@link #clientKeyPath}: providing one without the other results
     * in a `ConfigurationError`. Path-based and byte-based ({@link #clientCertificate}) client
     * certificate configuration are mutually exclusive.
     *
     * <p>If null (default), no path-based client certificate is used.
     */
    @Builder.Default private final String clientCertPath = null;

    /**
     * Filesystem path to the client private key (PEM) for mutual TLS (mTLS) authentication.
     *
     * <p>See {@link #clientCertPath}. Must be provided together with {@link #clientCertPath}.
     *
     * <p>If null (default), no path-based client key is used.
     */
    @Builder.Default private final String clientKeyPath = null;

    /**
     * Whether to automatically reload the path-based client certificate and key.
     *
     * <p>When true (and {@link #clientCertPath}/{@link #clientKeyPath} are set), the GLIDE core
     * periodically re-reads the certificate and key files. On a successful reload (the material
     * parses and the private key matches the certificate), the new material is adopted on the next
     * reconnect; on any failure, the previously loaded material is kept (last-known-good).
     *
     * <p>Root/CA certificate reload is out of scope; only the client certificate and key are
     * reloaded.
     *
     * <p>Default: false (path-based material is loaded once at client creation).
     */
    @Builder.Default private final boolean certReloadEnabled = false;

    /**
     * Interval, in seconds, between certificate reload checks when {@link #certReloadEnabled} is
     * true.
     *
     * <p>If null (default), the core default of 300 seconds (5 minutes) is used. Ignored when {@link
     * #certReloadEnabled} is false.
     */
    @Builder.Default private final Integer certReloadIntervalSeconds = null;

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
            boolean certReloadEnabled,
            Integer certReloadIntervalSeconds) {
        this.useInsecureTLS = useInsecureTLS;
        this.rootCertificates = rootCertificates;
        this.clientCertificate = clientCertificate;
        this.clientKey = clientKey;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.certReloadEnabled = certReloadEnabled;
        this.certReloadIntervalSeconds = certReloadIntervalSeconds;
        validate();
    }

    /**
     * Validates self-contained TLS configuration invariants.
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

        if (certReloadEnabled && clientCertPath == null) {
            throw new ConfigurationError(
                    "`certReloadEnabled` requires `clientCertPath` and `clientKeyPath` to be"
                            + " provided.");
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
     * Load PEM-encoded root certificates from a file for TLS server verification.
     *
     * <p>This is a convenience loader for reading custom root certificates from disk to be used as
     * {@link #rootCertificates} in a {@code TlsAdvancedConfiguration}.
     *
     * @param path The file path to the PEM-encoded certificate file.
     * @return The certificate data in PEM format as a byte array.
     * @throws ConfigurationError If the file is missing, unreadable, or empty.
     */
    public static byte[] loadRootCertificatesFromFile(String path) {
        return loadPemFile(path, "Root certificate");
    }

    /**
     * Load a PEM-encoded client certificate from a file for mutual TLS (mTLS) authentication.
     *
     * <p>This is a convenience loader for reading a client certificate from disk to be used as {@link
     * #clientCertificate} in a {@code TlsAdvancedConfiguration}.
     *
     * <p>Unlike {@link #clientCertPath}, this loads the certificate once at configuration time. The
     * file does not need to remain on disk after the client is created. For automatic certificate
     * rotation, use {@link #clientCertPath} and {@link #clientKeyPath} instead.
     *
     * @param path The file path to the PEM-encoded client certificate file.
     * @return The client certificate data in PEM format as a byte array.
     * @throws ConfigurationError If the file is missing, unreadable, or empty.
     */
    public static byte[] loadClientCertificateFromFile(String path) {
        return loadPemFile(path, "Client certificate");
    }

    /**
     * Load a PEM-encoded client private key from a file for mutual TLS (mTLS) authentication.
     *
     * <p>This is a convenience loader for reading a client private key from disk to be used as {@link
     * #clientKey} in a {@code TlsAdvancedConfiguration}.
     *
     * <p>Unlike {@link #clientKeyPath}, this loads the key once at configuration time. The file does
     * not need to remain on disk after the client is created. For automatic certificate rotation,
     * use {@link #clientCertPath} and {@link #clientKeyPath} instead.
     *
     * @param path The file path to the PEM-encoded client private key file.
     * @return The client private key data in PEM format as a byte array.
     * @throws ConfigurationError If the file is missing, unreadable, or empty.
     */
    public static byte[] loadClientKeyFromFile(String path) {
        return loadPemFile(path, "Client key");
    }

    /**
     * Read a PEM file from disk, surfacing missing, unreadable, and empty files as {@link
     * ConfigurationError} with a descriptive, type-specific message.
     *
     * @param path The file path to read.
     * @param description Human-readable description of the PEM contents (e.g. "Client certificate").
     * @return The file contents as a byte array.
     * @throws ConfigurationError If the file is missing, unreadable, or empty.
     */
    private static byte[] loadPemFile(String path, String description) {
        byte[] data;
        try {
            data = Files.readAllBytes(Paths.get(path));
        } catch (NoSuchFileException e) {
            throw new ConfigurationError(description + " file not found: " + path);
        } catch (IOException e) {
            throw new ConfigurationError(
                    "Failed to read " + description.toLowerCase() + " file: " + e.getMessage());
        }

        if (data.length == 0) {
            throw new ConfigurationError(description + " file is empty: " + path);
        }

        return data;
    }
}
