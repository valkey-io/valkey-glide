/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.exceptions.ConfigurationError;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    // Classpath location of a prebuilt PKCS12 keystore fixture holding a single client identity
    // (self-signed cert CN=glide-test + its RSA private key). Generated once with OpenSSL and
    // checked in under src/test/resources/tls. Loading a fixture keeps the material inspectable and
    // out of source, while the tests still assemble the specific keystore type/chain they need
    // in-process from it.
    private static final String FIXTURE_KEYSTORE = "/tls/client-identity.p12";
    private static final char[] FIXTURE_PASSWORD = "testpass".toCharArray();

    /**
     * Loads the client identity (private key + leaf certificate) from the fixture keystore. Finds
     * the first key entry rather than assuming an alias, avoiding any JVM alias-normalization
     * dependency.
     */
    private static KeyStore.PrivateKeyEntry loadFixtureIdentity() throws Exception {
        KeyStore fixture = KeyStore.getInstance("PKCS12");
        try (InputStream is = TlsAdvancedConfigurationTest.class.getResourceAsStream(FIXTURE_KEYSTORE)) {
            assertNotNull(is, "Missing test fixture on classpath: " + FIXTURE_KEYSTORE);
            fixture.load(is, FIXTURE_PASSWORD);
        }
        String alias = null;
        for (java.util.Enumeration<String> e = fixture.aliases(); e.hasMoreElements(); ) {
            String a = e.nextElement();
            if (fixture.isKeyEntry(a)) {
                alias = a;
                break;
            }
        }
        assertNotNull(alias, "Fixture keystore has no private key entry: " + FIXTURE_KEYSTORE);
        return (KeyStore.PrivateKeyEntry)
                fixture.getEntry(alias, new KeyStore.PasswordProtection(FIXTURE_PASSWORD));
    }

    /**
     * Builds a keystore of the given type containing a single {@code PrivateKeyEntry} sourced from
     * the checked-in fixture, and persists it to {@code keyStorePath}. Uses only public JCA, so it
     * is portable across JDKs with no internal-API dependency.
     *
     * @param chainLength number of certificate entries in the chain (the fixture's leaf cert is
     *     repeated; sufficient for asserting PEM block count).
     */
    private static void writeKeyStoreWithPrivateKey(
            Path keyStorePath, char[] password, String keyStoreType, int chainLength)
            throws Exception {
        KeyStore.PrivateKeyEntry identity = loadFixtureIdentity();
        PrivateKey privateKey = identity.getPrivateKey();
        Certificate leaf = identity.getCertificate();

        Certificate[] chain = new Certificate[chainLength];
        Arrays.fill(chain, leaf);

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

            KeyStore.PrivateKeyEntry fixture = loadFixtureIdentity();

            // The certificate PEM parses back into an X.509 certificate equal to the fixture's.
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate roundTrippedCert =
                    cf.generateCertificate(new ByteArrayInputStream(config.getClientCertificate()));
            assertEquals(fixture.getCertificate(), roundTrippedCert);

            // The key PEM is valid PKCS#8: strip the markers, Base64-decode the body, and reconstruct
            // the private key, which must equal the fixture's.
            String keyPem = new String(config.getClientKey(), StandardCharsets.UTF_8);
            String keyBody =
                    keyPem
                            .replace("-----BEGIN PRIVATE KEY-----", "")
                            .replace("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey roundTrippedKey =
                    kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBody)));
            assertEquals(fixture.getPrivateKey(), roundTrippedKey);
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
