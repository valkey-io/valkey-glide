/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.exceptions.ConfigurationError;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class TlsAdvancedConfigurationTest {

    @Test
    void testBuilderWithRootCertificates() {
        byte[] certBytes = "test-cert".getBytes(StandardCharsets.UTF_8);

        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder().rootCertificates(certBytes).build();

        assertNotNull(config);
        assertArrayEquals(certBytes, config.getRootCertificates());
    }

    @Test
    void testBuilderWithNullRootCertificates() {
        TlsAdvancedConfiguration config = TlsAdvancedConfiguration.builder().build();

        assertNotNull(config);
        assertNull(config.getRootCertificates());
    }

    @Test
    void testUseMutualTlsWithBytes() {
        byte[] certBytes = "client-cert".getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = "client-key".getBytes(StandardCharsets.UTF_8);

        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder().useMutualTls(certBytes, keyBytes).build();

        assertNotNull(config);
        assertArrayEquals(certBytes, config.getClientCertificate());
        assertArrayEquals(keyBytes, config.getClientKey());
        assertNull(config.getClientCertPath());
        assertNull(config.getClientKeyPath());
        assertNull(config.getCertReloadIntervalSeconds());
    }

    @Test
    void testLoadClientCertificateAndKeyFromFile() throws Exception {
        byte[] certBytes = "client-cert-from-file".getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = "client-key-from-file".getBytes(StandardCharsets.UTF_8);
        Path certPath = Files.createTempFile("client-cert", ".pem");
        Path keyPath = Files.createTempFile("client-key", ".pem");

        try {
            Files.write(certPath, certBytes);
            Files.write(keyPath, keyBytes);

            byte[] loadedCert =
                    TlsAdvancedConfiguration.TlsAdvancedConfigurationBuilder.loadClientCertificateFromFile(
                            certPath.toString());
            byte[] loadedKey =
                    TlsAdvancedConfiguration.TlsAdvancedConfigurationBuilder.loadClientKeyFromFile(
                            keyPath.toString());

            assertArrayEquals(certBytes, loadedCert);
            assertArrayEquals(keyBytes, loadedKey);

            // The loaders feed straight into the byte-based static overload.
            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.builder().useMutualTls(loadedCert, loadedKey).build();

            assertArrayEquals(certBytes, config.getClientCertificate());
            assertArrayEquals(keyBytes, config.getClientKey());
            // Static byte-based mTLS sets no cert path, so reload is not enabled.
            assertNull(config.getClientCertPath());
        } finally {
            Files.deleteIfExists(certPath);
            Files.deleteIfExists(keyPath);
        }
    }

    @Test
    void testLoadClientCertificateFromFileMissingThrows() {
        assertThrows(
                IOException.class,
                () ->
                        TlsAdvancedConfiguration.TlsAdvancedConfigurationBuilder.loadClientCertificateFromFile(
                                "/nonexistent/path/client-cert.pem"));
    }

    @Test
    void testLoadClientKeyFromFileMissingThrows() {
        assertThrows(
                IOException.class,
                () ->
                        TlsAdvancedConfiguration.TlsAdvancedConfigurationBuilder.loadClientKeyFromFile(
                                "/nonexistent/path/client-key.pem"));
    }

    @Test
    void testBuilderWithNullClientCertificateAndKey() {
        TlsAdvancedConfiguration config = TlsAdvancedConfiguration.builder().build();

        assertNotNull(config);
        assertNull(config.getClientCertificate());
        assertNull(config.getClientKey());
    }

    @Test
    void testFromKeyStoreWithInvalidPath() throws Exception {
        assertThrows(
                FileNotFoundException.class,
                () -> {
                    TlsAdvancedConfiguration.fromKeyStore(
                            "/nonexistent/path/keystore.jks", "password".toCharArray(), "JKS");
                });
    }

    @Test
    void testFromKeyStoreWithKeyStoreNotSupported() throws Exception {
        Path keyStorePath = Files.createTempFile("test-keystore", ".jks");
        char[] password = "testpass".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, password);

            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            assertThrows(
                    KeyStoreException.class,
                    () -> {
                        TlsAdvancedConfiguration.fromKeyStore(
                                keyStorePath.toString(), password, "NotSupported");
                    });
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testFromKeyStoreWithNullKeyStoreType() throws Exception {
        Path keyStorePath = Files.createTempFile("test-keystore", ".jks");
        char[] password = "testpass".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, password);

            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            assertThrows(
                    NullPointerException.class,
                    () -> {
                        TlsAdvancedConfiguration.fromKeyStore(keyStorePath.toString(), password, null);
                    });
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testFromKeyStoreWithInvalidPassword() throws Exception {
        Path keyStorePath = Files.createTempFile("test-keystore", ".jks");
        char[] password = "correctpass".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, password);

            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            assertThrows(
                    IOException.class,
                    () -> {
                        TlsAdvancedConfiguration.fromKeyStore(
                                keyStorePath.toString(), "wrongpass".toCharArray(), "JKS");
                    });
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testFromKeyStoreWithEmptyKeyStore() throws Exception {
        Path keyStorePath = Files.createTempFile("test-keystore", ".jks");
        char[] password = "testpass".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, password);

            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.fromKeyStore(keyStorePath.toString(), password, "JKS");

            assertNotNull(config);
            assertNotNull(config.getRootCertificates());
            assertEquals(0, config.getRootCertificates().length);
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsEmptyCertThrows() {
        byte[] keyBytes = "client-key".getBytes(StandardCharsets.UTF_8);

        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder().useMutualTls(new byte[0], keyBytes).build());
        assertTrue(error.getMessage().contains("`clientCertificate` cannot be an empty byte array"));
    }

    @Test
    void testUseMutualTlsEmptyKeyThrows() {
        byte[] certBytes = "client-cert".getBytes(StandardCharsets.UTF_8);

        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder().useMutualTls(certBytes, new byte[0]).build());
        assertTrue(error.getMessage().contains("`clientKey` cannot be an empty byte array"));
    }

    @Test
    void testBuilderWithNullClientCertAndKeyPaths() {
        TlsAdvancedConfiguration config = TlsAdvancedConfiguration.builder().build();

        assertNotNull(config);
        assertNull(config.getClientCertPath());
        assertNull(config.getClientKeyPath());
    }

    @Test
    void testBuilderCertReloadDefaultsDisabled() {
        TlsAdvancedConfiguration config = TlsAdvancedConfiguration.builder().build();

        // No cert path means reload is not enabled.
        assertNull(config.getClientCertPath());
        assertNull(config.getCertReloadIntervalSeconds());
    }

    // The two-argument reload overload requests reload but defers the cadence to the core.
    @Test
    void testUseMutualTlsWithReloadDefaultInterval() {
        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder()
                        .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key")
                        .build();

        assertEquals("/certs/client.pem", config.getClientCertPath());
        assertEquals("/certs/client.key", config.getClientKeyPath());
        assertNull(config.getClientCertificate());
        assertNull(config.getClientKey());
        assertNull(config.getCertReloadIntervalSeconds());
    }

    @Test
    void testUseMutualTlsWithReloadCustomInterval() {
        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder()
                        .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key", 120)
                        .build();

        assertEquals("/certs/client.pem", config.getClientCertPath());
        assertEquals("/certs/client.key", config.getClientKeyPath());
        assertEquals(120, config.getCertReloadIntervalSeconds());
    }

    // A zero interval is rejected: static (no-reload) mTLS is expressed by useMutualTls(bytes).
    @Test
    void testUseMutualTlsWithReloadZeroIntervalThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () ->
                                TlsAdvancedConfiguration.builder()
                                        .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key", 0)
                                        .build());
        assertTrue(error.getMessage().contains("`certReloadIntervalSeconds` must be positive"));
    }

    @Test
    void testUseMutualTlsWithReloadNegativeIntervalThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () ->
                                TlsAdvancedConfiguration.builder()
                                        .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key", -1)
                                        .build());
        assertTrue(error.getMessage().contains("`certReloadIntervalSeconds` must be positive"));
    }

    // An interval without cert paths has nothing to reload. The builder cannot express
    // that, so this calls the constructor directly.
    @Test
    void testIntervalWithoutCertPathsThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> new TlsAdvancedConfiguration(false, null, null, null, null, null, 60));
        assertTrue(
                error
                        .getMessage()
                        .contains("`certReloadIntervalSeconds` may only be set with path-based mTLS"));
    }

    // The largest int a caller can pass still sits below the uint32 bound, so it
    // round-trips through the builder.
    @Test
    void testMaxExpressibleIntervalAccepted() {
        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder()
                        .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key", Integer.MAX_VALUE)
                        .build();

        assertEquals(Integer.MAX_VALUE, config.getCertReloadIntervalSeconds());
    }

    @Test
    void testIntervalWithByteBasedMutualTlsThrows() {
        byte[] cert = "cert".getBytes(StandardCharsets.UTF_8);
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);

        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> new TlsAdvancedConfiguration(false, null, cert, key, null, null, 60));
        assertTrue(
                error
                        .getMessage()
                        .contains("`certReloadIntervalSeconds` may only be set with path-based mTLS"));
    }

    // Byte and path mTLS material together is unrepresentable through the builder,
    // so this exercises the constructor directly.
    @Test
    void testBothMTlsModesPopulatedThrows() {
        byte[] cert = "cert".getBytes(StandardCharsets.UTF_8);
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);

        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () ->
                                new TlsAdvancedConfiguration(
                                        false, null, cert, key, "/certs/client.pem", "/certs/client.key", null));
        assertTrue(error.getMessage().contains("cannot both be provided"));
    }

    // ---------------------------------------------------------------------------
    // useMutualTlsFromKeyStore
    // ---------------------------------------------------------------------------

    // A self-signed test certificate (CN=glide-test) and its matching PKCS#8 RSA private key, as
    // base64-encoded DER. Generated once with OpenSSL purely for these tests. Embedding static
    // material keeps the suite portable: the keystore is assembled in-process with public JCA only
    // (no JDK-internal cert generation, no keytool/openssl at runtime).
    private static final String TEST_CERT_DER_B64 =
            "MIIDCzCCAfOgAwIBAgIUFQK2et9Pudh5iYTRtZGhzw4sDD0wDQYJKoZIhvcNAQELBQAwFTETMBEG"
                    + "A1UEAwwKZ2xpZGUtdGVzdDAeFw0yNjEwMDEyMjI3MjdaFw0zNjA5MjgyMjI3MjdaMBUxEzARBgNV"
                    + "BAMMCmdsaWRlLXRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDgnb1t8Ehycjj5"
                    + "vJWgy+iMenZ2fyQsDLxueELNFei5Maf+R73aylg03nlbVXPy8e0l/Y+f7fidxqtKJ4L+FRak3Wy"
                    + "EitEZA1PTBpqytdBmKrWFODGkgbef8RJMSI+mr1YA6WFiltWRhyX/RNzzK7h0Q2bHrI20Ub+eZ4"
                    + "rV7FnxF8ATyaedjmXiGO+1m0SQ3+YeQwbF3siqYm6ZBsuCnmCPFO040iUNUjxsz82KQMLcDPmvx"
                    + "jzrqUjy/KPBACXnAVENeImxDQm0xDidGA6sfd4lKA3bb3xpcA6GGwQ0ojK22L3K6BoAQKCleIZR"
                    + "maDqovMBa6T+HK/cyJX7VPoq53vtAgMBAAGjUzBRMB0GA1UdDgQWBBRkKHAkdmNaG5uXO+T7B8Ru"
                    + "AP2UyDAfBgNVHSMEGDAWgBRkKHAkdmNaG5uXO+T7B8RuAP2UyDAPBgNVHRMBAf8EBTADAQH/MA0G"
                    + "CSqGSIb3DQEBCwUAA4IBAQB3FbtoxDMW+kmRSBYMKNNk98tEZcB3uXB2YULbDnYZbvRNC9jf2s68"
                    + "6HRwZa66ATNTZFguPjR1bDYvjkg2SadlmrLl9hajl0jbmgy3nv/ovE0HPAmaPvmmiuuj9u9Ryph"
                    + "oor0F+jKoVsCoiBb1hzy0OZzoie5JfExbYfMYe0XZQQJx6/qpNw6wDNETQyxbsU9JjgRW8ZBLVk"
                    + "EDAi7ri8qp8Dr66u/uwhUmrAMZ5pFf9BfDwkT/NvbvEtSsdPyKpmXYkK4QBs3HawTG7oKAop/iU"
                    + "rsQ2A6wQMRDd6aS60VI7bDK1T5bMPytLk3jLrFRHB0KEXnWzkRlpei1FmGI/hU9";

    private static final String TEST_KEY_PKCS8_DER_B64 =
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDgnb1t8Ehycjj5vJWgy+iMenZ2"
                    + "fyQsDLxueELNFei5Maf+R73aylg03nlbVXPy8e0l/Y+f7fidxqtKJ4L+FRak3WyEitEZA1PTBpq"
                    + "ytdBmKrWFODGkgbef8RJMSI+mr1YA6WFiltWRhyX/RNzzK7h0Q2bHrI20Ub+eZ4rV7FnxF8ATya"
                    + "edjmXiGO+1m0SQ3+YeQwbF3siqYm6ZBsuCnmCPFO040iUNUjxsz82KQMLcDPmvxjzrqUjy/KPBA"
                    + "CXnAVENeImxDQm0xDidGA6sfd4lKA3bb3xpcA6GGwQ0ojK22L3K6BoAQKCleIZRmaDqovMBa6T+"
                    + "HK/cyJX7VPoq53vtAgMBAAECggEAI2/PeH5Nt7ycj434n088R5l0ghpp9wclXVpc06VOu5UBd4U"
                    + "TB2cgBmtJAydWrTAM5Y7871Log9/Zm0/jgzmJgoYqfji2Z3dWbLcghexYTh4T2Eo2zsjmUvYCGI"
                    + "XkH/yOmYM4aYj5dcW4MW9IWpb9uV3+46auDpJNJG0agsiQog/87JUGYUYkIbyMsvariYkKEIfNA"
                    + "o2OEVlJCFZUVjOAFoBKovK/BcXFiE3dZ7YK3WSOb08+q5ep4ev9RtL2lB8NU442ZJ+7U0QEHSxw"
                    + "YEwPz77aMDn/Kfr1eILEDWMoe31xoXbaEF2F94pxbOfGDQEUHtQZtBztTtKutI67SsQr8QKBgQD7"
                    + "8XF+AkT40Bzr3as/a5UUPiRKpc0zSYw/crjBU2pMCpYU3IvqeWR4smmctp66KDa4pe0TFm7Gd0p"
                    + "CdUhem3dcpdwRAtgDYZHcAwKOoUJOV3z0X5985+UBJV1lNqpEN1rRAHc7xB1Rq7sDoV8Nu8YdGd"
                    + "hlzpxmNDXv+XKhmRVnhQKBgQDkO6ZXYR0yGKo8evgdzQw86KDVDJsuVPsESb0uNWW3MwPmG73ne"
                    + "O8IOSH/hDS+ebD+0p4zCPrXaxLzAnLmc+S8hSLHt3OhUapiz9Kvhs722C/izO6g0G+eupMLscp6"
                    + "AFRQU29oFwilke0fqrcEUHhohRB6qi5JV8Ii5nof+2VLSQKBgFsh+OWVuJEv5mZDJqCoL6LE36f"
                    + "I1bMJlZuVydLUc4zR/3vIUywbgQZPsvgm7r9zsGeWTW0sHiHYIJpthiICpmhy7mmQ18ZRUst8oz"
                    + "4ogq2H5AEZXb12vFVvyJrF7U0DoOwc+QQ7akeSkPE9O/7hv0XjhW0+EUC+/gux9Y8SqrVpAoGBA"
                    + "KyeDNYjpjBAhWjO3J+1eN8MVrAsI6YsMdnxZ3rueerQU8+TBdNvHOKMS5F0zWuOoHZql6ojzYxl"
                    + "+GQBYyO3XbXTwBVrQ7IsEQFBC6kj/Z6mrbkMpCLO4s0bcaGzq18QprRGFomUej63mq+Lr3Y84oS"
                    + "yt17/HZjtHfDFfnJ38gm5AoGBAPgtFB6z79AMrme6Dzs1dIZZExXnZ5p5lhFUblw01NxlZT4V1a"
                    + "dh71oD6reT9/ttKSPCeBOekejmYYsNfH5Xj4iewjNn1ZlCFBBH3yRzb3LMj729ddWOoqlMTivtv"
                    + "+zcqcoHvV9V4qBRsvYIq8VGsVNP7trFAhWIBMk+3qN+OMUa";

    /**
     * Builds a keystore of the given type containing a single {@code PrivateKeyEntry} assembled from
     * the embedded test cert + key, and persists it to {@code keyStorePath}. Uses only public JCA
     * ({@link CertificateFactory}, {@link KeyFactory}, {@link KeyStore#setKeyEntry}), so it is
     * portable across JDKs with no internal-API dependency.
     *
     * @param chainLength number of certificate entries in the chain (the same self-signed cert is
     *     repeated; sufficient for asserting PEM block count).
     */
    private static void writeKeyStoreWithPrivateKey(
            Path keyStorePath, char[] password, String keyStoreType, int chainLength)
            throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert =
                cf.generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(TEST_CERT_DER_B64)));

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey =
                kf.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(TEST_KEY_PKCS8_DER_B64)));

        Certificate[] chain = new Certificate[chainLength];
        Arrays.fill(chain, cert);

        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, password);
        keyStore.setKeyEntry("client", privateKey, password, chain);

        try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
            keyStore.store(fos, password);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    void testUseMutualTlsFromKeyStorePkcs12HappyPath() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "PKCS12", 1);

            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.builder()
                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "PKCS12")
                            .build();

            assertNotNull(config);
            assertNotNull(config.getClientCertificate());
            assertNotNull(config.getClientKey());

            String certPem = new String(config.getClientCertificate(), StandardCharsets.UTF_8);
            String keyPem = new String(config.getClientKey(), StandardCharsets.UTF_8);
            assertTrue(certPem.contains("-----BEGIN CERTIFICATE-----"));
            assertTrue(certPem.contains("-----END CERTIFICATE-----"));
            assertTrue(keyPem.contains("-----BEGIN PRIVATE KEY-----"));
            assertTrue(keyPem.contains("-----END PRIVATE KEY-----"));

            // Keystore loading maps to static (no-reload) mTLS: never sets the path fields.
            assertNull(config.getClientCertPath());
            assertNull(config.getClientKeyPath());
            assertNull(config.getCertReloadIntervalSeconds());
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreJksHappyPath() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".jks");
        char[] password = "testpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "JKS", 1);

            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.builder()
                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "JKS")
                            .build();

            assertNotNull(config.getClientCertificate());
            assertNotNull(config.getClientKey());
            assertNull(config.getClientCertPath());
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreMultiCertChainEmitsMultipleBlocks() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "PKCS12", 3);

            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.builder()
                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "PKCS12")
                            .build();

            String certPem = new String(config.getClientCertificate(), StandardCharsets.UTF_8);
            assertEquals(3, countOccurrences(certPem, "-----BEGIN CERTIFICATE-----"));
            assertEquals(3, countOccurrences(certPem, "-----END CERTIFICATE-----"));
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreNoPrivateKeyEntryThrows() throws Exception {
        // An empty keystore has no PrivateKeyEntry; the method must reject it with a clear error.
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            ConfigurationError error =
                    assertThrows(
                            ConfigurationError.class,
                            () ->
                                    TlsAdvancedConfiguration.builder()
                                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "PKCS12")
                                            .build());
            assertTrue(error.getMessage().contains("does not contain a private key entry"));
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreInvalidPathThrows() {
        assertThrows(
                FileNotFoundException.class,
                () ->
                        TlsAdvancedConfiguration.builder()
                                .useMutualTlsFromKeyStore(
                                        "/nonexistent/path/keystore.p12", "password".toCharArray(), "PKCS12"));
    }

    @Test
    void testUseMutualTlsFromKeyStoreWrongPasswordThrows() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "correctpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "PKCS12", 1);

            // A wrong keystore password surfaces as an IOException from KeyStore.load.
            assertThrows(
                    IOException.class,
                    () ->
                            TlsAdvancedConfiguration.builder()
                                    .useMutualTlsFromKeyStore(
                                            keyStorePath.toString(), "wrongpass".toCharArray(), "PKCS12"));
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreUnsupportedTypeThrows() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "PKCS12", 1);

            assertThrows(
                    KeyStoreException.class,
                    () ->
                            TlsAdvancedConfiguration.builder()
                                    .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "NotSupported"));
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreNullTypeThrows() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "PKCS12", 1);

            // Consistent with fromKeyStore: a null type reaches KeyStore.getInstance and NPEs.
            assertThrows(
                    NullPointerException.class,
                    () ->
                            TlsAdvancedConfiguration.builder()
                                    .useMutualTlsFromKeyStore(keyStorePath.toString(), password, null));
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    // Confirms the emitted PEM is genuinely valid, not merely marker-shaped: the serialized
    // certificate parses back through CertificateFactory and the serialized key parses back through
    // KeyFactory as PKCS#8, round-tripping to the same material that went into the keystore.
    @Test
    void testUseMutualTlsFromKeyStoreEmitsValidRoundTrippablePem() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-keystore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            writeKeyStoreWithPrivateKey(keyStorePath, password, "PKCS12", 1);

            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.builder()
                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "PKCS12")
                            .build();

            // The certificate PEM parses back into an X.509 certificate equal to the embedded one.
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate roundTrippedCert =
                    cf.generateCertificate(new ByteArrayInputStream(config.getClientCertificate()));
            Certificate expectedCert =
                    cf.generateCertificate(
                            new ByteArrayInputStream(Base64.getDecoder().decode(TEST_CERT_DER_B64)));
            assertEquals(expectedCert, roundTrippedCert);

            // The key PEM is valid PKCS#8: strip the markers, Base64-decode the body, and reconstruct
            // the private key, which must equal the embedded one.
            String keyPem = new String(config.getClientKey(), StandardCharsets.UTF_8);
            String keyBody =
                    keyPem
                            .replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey roundTrippedKey =
                    kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBody)));
            PrivateKey expectedKey =
                    kf.generatePrivate(
                            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(TEST_KEY_PKCS8_DER_B64)));
            assertEquals(expectedKey, roundTrippedKey);
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
