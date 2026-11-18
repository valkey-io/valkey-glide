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
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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

    // A test leaf certificate (CN=glide-test) with its matching PKCS#8 RSA private key, plus the
    // intermediate and root certificates that signed it — a real chain (leaf -> intermediate ->
    // root), as base64-encoded DER. Generated once with OpenSSL purely for these tests, e.g.:
    //   openssl req -x509 -newkey rsa:2048 -keyout root.key -out root.crt -days 3650 -nodes \
    //       -subj "/CN=glide-test-root"
    //   openssl req -newkey rsa:2048 -keyout int.key -out int.csr -nodes \
    //       -subj "/CN=glide-test-intermediate"
    //   openssl x509 -req -in int.csr -CA root.crt -CAkey root.key -CAcreateserial -days 3650 \
    //       -extfile <(printf "basicConstraints=CA:TRUE") -out int.crt
    //   openssl req -newkey rsa:2048 -keyout leaf.key -out leaf.csr -nodes -subj "/CN=glide-test"
    //   openssl x509 -req -in leaf.csr -CA int.crt -CAkey int.key -CAcreateserial -days 3650 \
    //       -out leaf.crt
    //   openssl x509 -in leaf.crt -outform DER | base64  # -> TEST_CERT_DER_B64
    //   openssl pkcs8 -topk8 -nocrypt -in leaf.key -outform DER | base64  # -> TEST_KEY_PKCS8_DER_B64
    //   openssl x509 -in int.crt -outform DER | base64  # -> TEST_INTERMEDIATE_DER_B64
    //   openssl x509 -in root.crt -outform DER | base64  # -> TEST_ROOT_DER_B64
    // Embedding static material keeps the suite self-contained: the keystore is assembled in-process
    // with public JCA only (no JDK-internal cert generation, no keytool/openssl at runtime).
    private static final String TEST_CERT_DER_B64 =
            "MIIDBzCCAe+gAwIBAgIUDSY1/ZOxH2ikJrej2FQFYHUyJU8wDQYJKoZIhvcNAQELBQAwIjEgMB4G"
                    + "A1UEAwwXZ2xpZGUtdGVzdC1pbnRlcm1lZGlhdGUwHhcNMjYxMDAyMDExODU1WhcNMzYwOTI5MDEx"
                    + "ODU1WjAVMRMwEQYDVQQDDApnbGlkZS10ZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC"
                    + "AQEAtpehMS+YJRgFSapZjNKFtdUfK+iCPv6/hd54ojPHXjidjIUZRCwEHh19jsaQuGjaIBsReRXs"
                    + "6JOKYHsAHvaR4dUzof4hx5Rnt6H4fZ6W4WNQub4K8sa+b5juh92UZBPxhlnQ/OZ5gmde8mOcxc/T"
                    + "OR9QWqJ92sSYpShmsGAxntvf59+eqgBFpGyepq/+cE0RHK36i4MUuTGzxQS6649HsKbHnd9/lqRG"
                    + "sZvSj6vd7dLvzZ+mjZv+BOZrZrfQtayWGuZ3K4AqgS0rPB63vlHShc7bSgaujwobXy3oy1AdM9zT"
                    + "K5w5RPwrY1foqSoV1UnYmw/aWGI+GSy7rrl889i5xwIDAQABo0IwQDAdBgNVHQ4EFgQUePUv9+kd"
                    + "UQKhN8Sjvmg8T/IPZzgwHwYDVR0jBBgwFoAUOonVgArccMaCkkogof94/9swgjUwDQYJKoZIhvcN"
                    + "AQELBQADggEBAFXheqLWkq9WcWy3vmTO22j8mSIy5LjrG/G88+AfVpqGpX+/onczaWxG8pGDq2d1"
                    + "OfdHCBjmXKvKEm7cvpnJSg2AO/ou69Uv4ZG1GyEHBny9qG61nvRobUe2KKHII6MebLQUaypr1VX8"
                    + "3PBTbEcgspQ8KNakB8snWlRowkz1tVdCQc0ukAFA5cPe07ljNsJXHWfqkeVF6oFdNKYhYHhRP5tm"
                    + "o9z3W/NFLoCN82F0ishen5wKVZOtTiMZNBxQRNoaIaiNwbf2hOhb0ANfc1stfy4CNbVbVfLmX8fq"
                    + "UtEb3svPrAVJIRSug4OLlCGJQyFfGdslnP0CYVjrwWDrn3+6X3c=";

    private static final String TEST_KEY_PKCS8_DER_B64 =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC2l6ExL5glGAVJqlmM0oW11R8r"
                    + "6II+/r+F3niiM8deOJ2MhRlELAQeHX2OxpC4aNogGxF5Fezok4pgewAe9pHh1TOh/iHHlGe3ofh9"
                    + "npbhY1C5vgryxr5vmO6H3ZRkE/GGWdD85nmCZ17yY5zFz9M5H1Baon3axJilKGawYDGe29/n356q"
                    + "AEWkbJ6mr/5wTREcrfqLgxS5MbPFBLrrj0ewpsed33+WpEaxm9KPq93t0u/Nn6aNm/4E5mtmt9C1"
                    + "rJYa5ncrgCqBLSs8Hre+UdKFzttKBq6PChtfLejLUB0z3NMrnDlE/CtjV+ipKhXVSdibD9pYYj4Z"
                    + "LLuuuXzz2LnHAgMBAAECggEASRLd28VkalP2qciXFhiakm68juH6XiOtmnGybZezTi3yP2508id7"
                    + "bmH3AdDN0j+ELB0pHQB9U4bYdkxDfCDJuUuN4mLGOg1WhNM5k2yIjaMlh3BbCVYomJjnvVAcNwEU"
                    + "Q+RmExBZyKp+ARuEflXx/oZdrighng/X1yEYF7YnpZ9D0nTs7y2sVOgj8qvvjfZOTv7wy0WgxSiL"
                    + "flpnuCwzbAyRIiTIFGksrlkBkOzNvTNHBIsZsKa6FJBurNLlPLXmKN1i+idF132H6q8YI+z6G1Kz"
                    + "3qABag1I4v8Fqcl4qRqBedWH34dayvxam6ehCLzdbFuSTojnqN51K6CBjdzMwQKBgQDmVZdbzMjr"
                    + "i/kvMibkCgIMrpRXkod6PpUU0FD9l9Bz5H3wk4G8NyVC86NU8dsWTsSiATkdQ1iRx+YAckLTkEwv"
                    + "+ljT55gQ7meVDlbPCbQTWwIEfQlhtkmJy4efGYg9Dkqz+u5DAWIxObiut98XGEokw30kkFWDpowC"
                    + "IYxm20mXDwKBgQDK8CvsJnkalm+F1ohg7i5RkJnudW0FT3UQlF3Jj60RK2I4dqaYJEG/JkZre2Vt"
                    + "OA1xXh/jFvUxzIkStQl8RXpLgy2sZ9ezw2Kyy3FxIPKQ0jSVCmd0plKE2KNab4jWZm2Xl9a+lFku"
                    + "9sIdUxJqh8QGXY5tsFvYdDU4uxLMomTxyQKBgGXSEl3fcjZGIzqM1gparjtC9Yqc2MzeW3Le/96K"
                    + "vPhuWon9+wzj59Hn+Bz16V68JUpkdgYMnlubXX53BDmYAUX4Skoqh9t8OEf5FcDiTjt8MLEhQQNz"
                    + "3KBQW7ymQcaTycw0Mh1mwCx4kr6Rw8nmz+fejzSZpWPUPPI4OGPDro1bAoGAG5itYF+a+FKct8aE"
                    + "pSm+grj3NcYiHSbA9JA4cMBo+Hy9zo/T97x2dFfwG42cLU4CBfiWvXrRvQPjX/feYlfQWZRtEZTN"
                    + "cFSRh17C/m9MjQUIwXu4tdQoRIhxLkscgItNO+AaA7CIsCo+G17AklwD/Bmc1K22z6h91EkcNVeg"
                    + "AoECgYB5w/hthWK210nTASiVjeuCbAQDb8nUs5i/9ulET9jLYUBRcEighDG49WdT3mcw0NoTlC9W"
                    + "AE+a3EzloPxGVnO2SFPzTjwzP/8As50klDwB27D0oqOjXv52CvHttS9i7DkZWeEiG2M/wN/7D7Ch"
                    + "lsV8ak7dfShJkzniN7JjB0LldQ==";

    private static final String TEST_INTERMEDIATE_DER_B64 =
            "MIIDGjCCAgKgAwIBAgIUZNgGa2iKBcPLmPNBEbKvYmcG9howDQYJKoZIhvcNAQELBQAwGjEYMBYG"
                    + "A1UEAwwPZ2xpZGUtdGVzdC1yb290MB4XDTI2MTAwMjAxMTg1NVoXDTM2MDkyOTAxMTg1NVowIjEg"
                    + "MB4GA1UEAwwXZ2xpZGUtdGVzdC1pbnRlcm1lZGlhdGUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw"
                    + "ggEKAoIBAQCeyyqphnbk2W49rQaAtb2pAnhcA+dJ0wdAS8oPNmE7WzM3suJ3bdPU1U6Nm3yn2pT8"
                    + "xMf/cSQf/qyE9bAsqGx/h7YO/OFBA9wifhb1bFm4d0VTb6iXrwFHpen2rjU6uqdXCOj+8K55yNou"
                    + "EqSnfGUGdKdpD5UGjQCypNaDP0tgYSyZJjqCEbw4thbPqO3fvunesrVUrquh9MBTMnckDxj5THh3"
                    + "SpEjo3MND2X598s5JN0p7sPD7EtJvWaLP0A4fNVWeCF/pu7yeAXEIG7uEpgJPNT0dL+sCtM2GWww"
                    + "UR7E6wVB0VLZZTKqJ/EChLLR4eoNkX5hUtqzi8oQISebbTMrAgMBAAGjUDBOMAwGA1UdEwQFMAMB"
                    + "Af8wHQYDVR0OBBYEFDqJ1YAK3HDGgpJKIKH/eP/bMII1MB8GA1UdIwQYMBaAFFXl4F1lVQ3HH+l9"
                    + "wzkh0Ix1YqA+MA0GCSqGSIb3DQEBCwUAA4IBAQBnfsghGHFD0RaX0Yt1XWY3K8edTLdP0PmlB6dZ"
                    + "fPX1RDLy7FNid50+m1VRoVAC6eE3bvc22Vq8RD+RBh5IIsFXnuqv0G3cLlpxEIYdcPDW070qOe4K"
                    + "VrZS9/RoEgxt5ddMWGBB7QZ2UuBFk4egF28AmfEX0kA8tzWdhoqLbHK7NSRLMkJJM9UuUZdyH37h"
                    + "NLLQZlOAElqczua73rnkN/gPChtaOZvbjY9G9b7iOfDwafTRF/T8mUqGZGPKF7qa76ICyDYDeWDN"
                    + "NVSAZ8EJrh7bmc7v5O1Xg34jVxWNYDQKSm2Soz+RtC7mVLrr/uRrcJHFEz2zEb7VsZofI5tOUQiX";

    private static final String TEST_ROOT_DER_B64 =
            "MIIDFTCCAf2gAwIBAgIUJcSg7X9WnspaFLi91nKYDijnnbAwDQYJKoZIhvcNAQELBQAwGjEYMBYG"
                    + "A1UEAwwPZ2xpZGUtdGVzdC1yb290MB4XDTI2MTAwMjAxMTg1NVoXDTM2MDkyOTAxMTg1NVowGjEY"
                    + "MBYGA1UEAwwPZ2xpZGUtdGVzdC1yb290MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA"
                    + "ohsN1bM84YLr9DabGgPks1thMO6XJx8bWwXN7uZ/Az2fRNfDB/7hsQbY1j01+1wu7OUsUWEi+tcp"
                    + "hsa733Ruo8mA5h9r2NP7ty8QaPg4uttKKMaJgoXTBjlerQIJYdGLD2ExIivrFKFT6Nfs8LDF28XC"
                    + "/nS8i2ale15xyEMZrIJGBDSxauLW3CQSvJ+J2GqxFfjrDU/5JDfxwYAvo/hIe4pC0OqK8bS5nHFf"
                    + "xx2q70m9SvO1H+05lCE+dooWjKbuSSWL8hiFiNvIwJUqw7ihsZHtUG+pVgY6q/K91rsh2tR0PasA"
                    + "qJvQv38/lhR+EELKIDPC4gO4g4F0+f+blhcqXQIDAQABo1MwUTAdBgNVHQ4EFgQUVeXgXWVVDccf"
                    + "6X3DOSHQjHVioD4wHwYDVR0jBBgwFoAUVeXgXWVVDccf6X3DOSHQjHVioD4wDwYDVR0TAQH/BAUw"
                    + "AwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAHZ0oOpObMDoyyvJ9dhTgrQeGq8rHlI2iifv14zUdr8JG"
                    + "jDCGUCPpHVjXNkVxriLj1a+DCiCcpNPNzoeUpRzvaA7DuNPmZsBX9pnQZmSmg9fPwq5e7Vd2DtTI"
                    + "bZUSIqOsMLBoGA8qgtu3rJTyYm8vsX31kvUDCFwiSDxt0TFlwgYE5116iWa3ynQ64NOosKMBzdN1"
                    + "jiQEtFeMakMydgj4nH5MBBhZodn3eQTbhotbjPwo8ojFVJPUjqeZY+HoZxmzMmV9SokF2G2xVsPu"
                    + "3ncuA2xqFJ6IYam3IHDmzdmZ+cHBR/a+J6N//7zayj5Oz6qnuLLXgnZ6VhgV+v5a7VQR1A==";

    /**
     * Builds a keystore of the given type containing a single {@code PrivateKeyEntry} assembled from
     * the embedded test cert + key, and persists it to {@code keyStorePath}. Uses only public JCA
     * ({@link CertificateFactory}, {@link KeyFactory}, {@link KeyStore#setKeyEntry}), so it is
     * portable across JDKs with no internal-API dependency.
     *
     * @param chainLength number of certificate entries in the chain, taken in order from the real
     *     leaf -> intermediate -> root chain (1 = leaf only, 2 = leaf+intermediate, 3 = full chain).
     *     A valid chain (each cert signed by the next) is required: the PKCS12 provider rejects a
     *     chain of unrelated/duplicate certificates.
     */
    private static void writeKeyStoreWithPrivateKey(
            Path keyStorePath, char[] password, String keyStoreType, int chainLength) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate leaf =
                cf.generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(TEST_CERT_DER_B64)));
        Certificate intermediate =
                cf.generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(TEST_INTERMEDIATE_DER_B64)));
        Certificate root =
                cf.generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(TEST_ROOT_DER_B64)));

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey =
                kf.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(TEST_KEY_PKCS8_DER_B64)));

        // Ordered leaf -> intermediate -> root; take the requested prefix so each cert is signed by
        // the next, which the PKCS12 provider requires.
        Certificate[] fullChain = {leaf, intermediate, root};
        if (chainLength < 1 || chainLength > fullChain.length) {
            throw new IllegalArgumentException("chainLength must be 1..3, got " + chainLength);
        }
        Certificate[] chain = Arrays.copyOf(fullChain, chainLength);

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

    /** Loads the embedded leaf certificate. */
    private static Certificate loadLeafCertificate() throws Exception {
        return CertificateFactory.getInstance("X.509")
                .generateCertificate(
                        new ByteArrayInputStream(Base64.getDecoder().decode(TEST_CERT_DER_B64)));
    }

    /** Loads the embedded leaf private key. */
    private static PrivateKey loadLeafPrivateKey() throws Exception {
        return KeyFactory.getInstance("RSA")
                .generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(TEST_KEY_PKCS8_DER_B64)));
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
    void testUseMutualTlsFromKeyStoreTrustStoreOnlyThrows() throws Exception {
        // A trust store holds only trusted certificates (no PrivateKeyEntry); it must be rejected.
        // This is the realistic way to hit the "no private key entry" error.
        Path keyStorePath = Files.createTempFile("mtls-truststore", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, password);
            trustStore.setCertificateEntry("ca-root", loadLeafCertificate());
            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                trustStore.store(fos, password);
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
    void testUseMutualTlsFromKeyStoreSecretKeyBeforePrivateKeyIsSkipped() throws Exception {
        // A PKCS12 store holding a SecretKeyEntry under an alias that sorts/lists before the
        // PrivateKeyEntry. isKeyEntry() is true for both, so a naive scan would select the secret key
        // and fail; entryInstanceOf(PrivateKeyEntry) must skip it and return the client identity.
        Path keyStorePath = Files.createTempFile("mtls-mixed", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            // AES secret key under an alias that lists before the client identity.
            SecretKey secret = new SecretKeySpec(new byte[16], "AES");
            keyStore.setEntry(
                    "aaa-secret",
                    new KeyStore.SecretKeyEntry(secret),
                    new KeyStore.PasswordProtection(password));
            keyStore.setKeyEntry(
                    "zzz-client", loadLeafPrivateKey(), password, new Certificate[] {loadLeafCertificate()});
            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            TlsAdvancedConfiguration config =
                    TlsAdvancedConfiguration.builder()
                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "PKCS12")
                            .build();

            // The client (leaf) certificate is returned, not an error about the secret key.
            String certPem = new String(config.getClientCertificate(), StandardCharsets.UTF_8);
            Certificate roundTrippedCert =
                    CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(config.getClientCertificate()));
            assertEquals(loadLeafCertificate(), roundTrippedCert);
            assertTrue(certPem.contains("-----BEGIN CERTIFICATE-----"));
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }

    @Test
    void testUseMutualTlsFromKeyStoreMultiplePrivateKeyEntriesThrows() throws Exception {
        // Two private key entries make the presented identity ambiguous; the keystore must be
        // rejected with a ConfigurationError naming the aliases rather than silently picking one.
        Path keyStorePath = Files.createTempFile("mtls-two-keys", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            PrivateKey key = loadLeafPrivateKey();
            Certificate[] chain = {loadLeafCertificate()};
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            keyStore.setKeyEntry("identity-a", key, password, chain);
            keyStore.setKeyEntry("identity-b", key, password, chain);
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
            assertTrue(error.getMessage().contains("multiple private key entries"));
            assertTrue(error.getMessage().contains("identity-a"));
            assertTrue(error.getMessage().contains("identity-b"));
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
