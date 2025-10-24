/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.TestUtilities.getCaCertificate;
import static org.junit.jupiter.api.Assertions.*;

import glide.api.GlideClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class StandaloneTlsCertificateTest {

    private static String valkeyHost;
    private static int valkeyPort;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws Exception {
        String standaloneHosts = System.getProperty("test.server.standalone.tls", "");
        String firstHost = standaloneHosts.split(",")[0].trim();
        String[] hostPort = firstHost.split(":");
        valkeyHost = hostPort[0];
        valkeyPort = Integer.parseInt(hostPort[1]);
        caCert = getCaCertificate();
    }

    @Test
    void testStandaloneTlsWithoutCertificateFails() throws Exception {
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                        .useTLS(true)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    @Test
    void testStandaloneTlsWithSelfSignedCertificateSucceeds() throws Exception {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();

        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        try (GlideClient client = GlideClient.createClient(config).get()) {
            String result = client.ping().get();
            assertEquals("PONG", result);
        }
    }

    @Test
    void testStandaloneTlsWithMultipleCertificatesSucceeds() throws Exception {
        String caCertStr = new String(caCert, StandardCharsets.UTF_8);
        String multipleCerts = caCertStr + "\n" + caCertStr;
        byte[] certBundle = multipleCerts.getBytes(StandardCharsets.UTF_8);

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(certBundle).build();

        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        try (GlideClient client = GlideClient.createClient(config).get()) {
            String result = client.ping().get();
            assertEquals("PONG", result);
        }
    }

    @Test
    void testStandaloneTlsWithEmptyCertificateFails() throws Exception {
        byte[] emptyCaCert = new byte[0];

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(emptyCaCert).build();

        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    @Test
    void testStandaloneTlsWithInvalidCertificateFails() throws Exception {
        byte[] invalidCert =
                "-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----".getBytes();

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(invalidCert).build();

        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    @Test
    void testStandaloneTlsWithKeyStoreSucceeds() throws Exception {
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
                            .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .build();

            try (GlideClient client = GlideClient.createClient(config).get()) {
                String result = client.ping().get();
                assertEquals("PONG", result);
            }
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
