/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;

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
}
