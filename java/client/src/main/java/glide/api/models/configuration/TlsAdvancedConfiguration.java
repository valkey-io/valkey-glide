/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

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
import lombok.experimental.SuperBuilder;

/**
 * Advanced TLS configuration settings class for creating a client. Shared settings for standalone
 * and cluster clients.
 */
@Getter
@SuperBuilder
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
}
