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
    void testBuilderWithClientCertificateAndKey() {
        byte[] certBytes = "client-cert".getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = "client-key".getBytes(StandardCharsets.UTF_8);

        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder().clientCertificate(certBytes).clientKey(keyBytes).build();

        assertNotNull(config);
        assertArrayEquals(certBytes, config.getClientCertificate());
        assertArrayEquals(keyBytes, config.getClientKey());
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
    void testLoadRootCertificatesFromFile() throws Exception {
        byte[] certBytes = "root-cert-data".getBytes(StandardCharsets.UTF_8);
        Path certPath = Files.createTempFile("root-cert", ".pem");

        try {
            Files.write(certPath, certBytes);
            String path = certPath.toString();

            byte[] loaded = TlsAdvancedConfiguration.loadRootCertificatesFromFile(path);

            assertArrayEquals(certBytes, loaded);
        } finally {
            Files.deleteIfExists(certPath);
        }
    }

    @Test
    void testLoadRootCertificatesFromFileNotFound() {
        String path = "/nonexistent/path/ca-cert.pem";
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.loadRootCertificatesFromFile(path));
        assertTrue(error.getMessage().contains("Root certificate file not found"));
    }

    @Test
    void testLoadRootCertificatesFromFileEmpty() throws Exception {
        Path certPath = Files.createTempFile("empty-root-cert", ".pem");

        try {
            String path = certPath.toString();
            ConfigurationError error =
                    assertThrows(
                            ConfigurationError.class,
                            () -> TlsAdvancedConfiguration.loadRootCertificatesFromFile(path));
            assertTrue(error.getMessage().contains("Root certificate file is empty"));
        } finally {
            Files.deleteIfExists(certPath);
        }
    }

    @Test
    void testLoadClientCertificateFromFile() throws Exception {
        byte[] certBytes = "client-cert-data".getBytes(StandardCharsets.UTF_8);
        Path certPath = Files.createTempFile("client-cert", ".pem");

        try {
            Files.write(certPath, certBytes);
            String path = certPath.toString();

            byte[] loaded = TlsAdvancedConfiguration.loadClientCertificateFromFile(path);

            assertArrayEquals(certBytes, loaded);
        } finally {
            Files.deleteIfExists(certPath);
        }
    }

    @Test
    void testLoadClientCertificateFromFileNotFound() {
        String path = "/nonexistent/path/client-cert.pem";
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.loadClientCertificateFromFile(path));
        assertTrue(error.getMessage().contains("Client certificate file not found"));
    }

    @Test
    void testLoadClientCertificateFromFileEmpty() throws Exception {
        Path certPath = Files.createTempFile("empty-client-cert", ".pem");

        try {
            String path = certPath.toString();
            ConfigurationError error =
                    assertThrows(
                            ConfigurationError.class,
                            () -> TlsAdvancedConfiguration.loadClientCertificateFromFile(path));
            assertTrue(error.getMessage().contains("Client certificate file is empty"));
        } finally {
            Files.deleteIfExists(certPath);
        }
    }

    @Test
    void testLoadClientKeyFromFile() throws Exception {
        byte[] keyBytes = "client-key-data".getBytes(StandardCharsets.UTF_8);
        Path keyPath = Files.createTempFile("client-key", ".pem");

        try {
            Files.write(keyPath, keyBytes);
            String path = keyPath.toString();

            byte[] loaded = TlsAdvancedConfiguration.loadClientKeyFromFile(path);

            assertArrayEquals(keyBytes, loaded);
        } finally {
            Files.deleteIfExists(keyPath);
        }
    }

    @Test
    void testLoadClientKeyFromFileNotFound() {
        String path = "/nonexistent/path/client-key.pem";
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class, () -> TlsAdvancedConfiguration.loadClientKeyFromFile(path));
        assertTrue(error.getMessage().contains("Client key file not found"));
    }

    @Test
    void testLoadClientKeyFromFileEmpty() throws Exception {
        Path keyPath = Files.createTempFile("empty-client-key", ".pem");

        try {
            String path = keyPath.toString();
            ConfigurationError error =
                    assertThrows(
                            ConfigurationError.class, () -> TlsAdvancedConfiguration.loadClientKeyFromFile(path));
            assertTrue(error.getMessage().contains("Client key file is empty"));
        } finally {
            Files.deleteIfExists(keyPath);
        }
    }

    @Test
    void testBuilderWithClientCertAndKeyPaths() {
        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder()
                        .clientCertPath("/certs/client.pem")
                        .clientKeyPath("/certs/client.key")
                        .build();

        assertNotNull(config);
        assertEquals("/certs/client.pem", config.getClientCertPath());
        assertEquals("/certs/client.key", config.getClientKeyPath());
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

        assertFalse(config.isCertReloadEnabled());
        assertNull(config.getCertReloadIntervalSeconds());
    }

    @Test
    void testBuilderWithCertReloadEnabledAndInterval() {
        TlsAdvancedConfiguration config =
                TlsAdvancedConfiguration.builder()
                        .clientCertPath("/certs/client.pem")
                        .clientKeyPath("/certs/client.key")
                        .certReloadEnabled(true)
                        .certReloadIntervalSeconds(120)
                        .build();

        assertTrue(config.isCertReloadEnabled());
        assertEquals(120, config.getCertReloadIntervalSeconds());
    }
}
