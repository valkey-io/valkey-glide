/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestUtilities.getCaCertificate;
import static org.junit.jupiter.api.Assertions.*;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClusterTlsCertificateTest {

    private static NodeAddress[] clusterNodes;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws Exception {
        String clusterHosts = System.getProperty("test.server.cluster.tls", "");
        String[] hosts = clusterHosts.split(",");

        List<NodeAddress> nodes = new ArrayList<>();
        for (String host : hosts) {
            String[] parts = host.trim().split(":");
            NodeAddress node =
                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build();
            nodes.add(node);
        }

        clusterNodes = nodes.toArray(new NodeAddress[0]);
        caCert = getCaCertificate();
    }

    @Test
    void testClusterTlsWithoutCertificateFails() throws Exception {
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(Arrays.asList(clusterNodes))
                        .useTLS(true)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }

    @Test
    void testClusterTlsWithSelfSignedCertificateSucceeds() throws Exception {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();

        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(Arrays.asList(clusterNodes))
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
            String result = client.ping().get();
            assertEquals("PONG", result);
        }
    }

    @Test
    void testClusterTlsWithMultipleCertificatesSucceeds() throws Exception {
        String caCertStr = new String(caCert, StandardCharsets.UTF_8);
        String multipleCerts = caCertStr + "\n" + caCertStr;
        byte[] certBundle = multipleCerts.getBytes(StandardCharsets.UTF_8);

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(certBundle).build();

        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(Arrays.asList(clusterNodes))
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
            String result = client.ping().get();
            assertEquals("PONG", result);
        }
    }

    @Test
    void testClusterTlsWithEmptyCertificateArrayFails() throws Exception {
        byte[] emptyCaCert = new byte[0];

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(emptyCaCert).build();

        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(Arrays.asList(clusterNodes))
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }

    @Test
    void testClusterTlsWithInvalidCertificateFails() throws Exception {
        byte[] invalidCert =
                "-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----".getBytes();

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(invalidCert).build();

        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();

        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(Arrays.asList(clusterNodes))
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }

    @Test
    void testClusterTlsWithKeyStoreSucceeds() throws Exception {
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

            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();

            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .addresses(Arrays.asList(clusterNodes))
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .build();

            try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
                String result = client.ping().get();
                assertEquals("PONG", result);
            }
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
