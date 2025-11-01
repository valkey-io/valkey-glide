/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestUtilities.getCaCertificate;
import static org.junit.jupiter.api.Assertions.*;

import glide.TestUtilities;
import glide.api.GlideClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StandaloneTlsCertificateTest {

    public static NodeAddress nodeAddr;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws IOException {
        String standaloneHosts = System.getProperty("test.server.standalone.tls", "");
        String firstHost = standaloneHosts.split(",")[0].trim();
        String[] hostPort = firstHost.split(":");
        nodeAddr = NodeAddress.builder().host(hostPort[0]).port(Integer.parseInt(hostPort[1])).build();
        caCert = getCaCertificate();
    }

    @Test
    void testStandaloneTlsWithoutCertificateFails() {
        GlideClientConfiguration config =
                GlideClientConfiguration.builder().address(nodeAddr).useTLS(true).build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    @Test
    void testStandaloneTlsWithSelfSignedCertificateSucceeds()
            throws ExecutionException, InterruptedException {
        GlideClientConfiguration config =
                TestUtilities.createStandaloneConfigWithRootCert(caCert, nodeAddr);

        try (GlideClient client = GlideClient.createClient(config).get()) {
            String result = client.ping().get();
            assertEquals("PONG", result);
        }
    }

    @Test
    void testStandaloneTlsWithMultipleCertificatesSucceeds()
            throws ExecutionException, InterruptedException {
        String caCertStr = new String(caCert, StandardCharsets.UTF_8);
        String multipleCerts = caCertStr + "\n" + caCertStr;
        byte[] certBundle = multipleCerts.getBytes(StandardCharsets.UTF_8);
        GlideClientConfiguration config =
                TestUtilities.createStandaloneConfigWithRootCert(certBundle, nodeAddr);

        try (GlideClient client = GlideClient.createClient(config).get()) {
            String result = client.ping().get();
            assertEquals("PONG", result);
        }
    }

    @Test
    void testStandaloneTlsWithEmptyCertificateFails() {
        byte[] emptyCaCert = new byte[0];
        GlideClientConfiguration config =
                TestUtilities.createStandaloneConfigWithRootCert(emptyCaCert, nodeAddr);

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    @Test
    void testStandaloneTlsWithInvalidCertificateFails() {
        byte[] invalidCert =
                "-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----".getBytes();
        GlideClientConfiguration config =
                TestUtilities.createStandaloneConfigWithRootCert(invalidCert, nodeAddr);

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    @Test
    void testStandaloneTlsWithKeyStoreSucceeds()
            throws IOException,
                    KeyStoreException,
                    NoSuchAlgorithmException,
                    CertificateException,
                    ExecutionException,
                    InterruptedException {
        Path keyStorePath = Files.createTempFile("test-keystore", ".jks");
        char[] password = "password".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, password);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(caCert));
            keyStore.setCertificateEntry("ca-cert", cert);

            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }

            TlsAdvancedConfiguration tlsConfig =
                    TlsAdvancedConfiguration.fromKeyStore(keyStorePath.toString(), password, "JKS");

            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();

            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(nodeAddr)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .build();
            ;

            try (GlideClient client = GlideClient.createClient(config).get()) {
                String result = client.ping().get();
                assertEquals("PONG", result);
            }
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
