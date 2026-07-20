/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.exceptions.ConfigurationError;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
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
}
