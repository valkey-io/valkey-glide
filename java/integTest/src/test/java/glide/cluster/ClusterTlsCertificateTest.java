/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestUtilities.getCaCertificate;
import static org.junit.jupiter.api.Assertions.*;

import glide.TestUtilities;
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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClusterTlsCertificateTest {

    private static List<NodeAddress> clusterNodes;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws Exception {
        String clusterHosts = System.getProperty("test.server.cluster.tls", "");
        String[] hosts = clusterHosts.split(",");

        clusterNodes = new ArrayList<>();
        for (String host : hosts) {
            String[] parts = host.trim().split(":");
            NodeAddress node =
                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build();
            clusterNodes.add(node);
        }

        caCert = getCaCertificate();
    }

    @Test
    void testClusterTlsWithoutCertificateFails() throws Exception {
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder().addresses(clusterNodes).useTLS(true).build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }

    @Test
    void testClusterTlsWithSelfSignedCertificateSucceeds() throws Exception {
        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(caCert, clusterNodes);

        TestUtilities.createAndTestClient(config);
    }

    @Test
    void testClusterTlsWithMultipleCertificatesSucceeds() throws Exception {
        String caCertStr = new String(caCert, StandardCharsets.UTF_8);
        String multipleCerts = caCertStr + "\n" + caCertStr;
        byte[] certBundle = multipleCerts.getBytes(StandardCharsets.UTF_8);
        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(certBundle, clusterNodes);

        TestUtilities.createAndTestClient(config);
    }

    @Test
    void testClusterTlsWithEmptyCertificateArrayFails() throws Exception {
        byte[] emptyCaCert = new byte[0];
        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(emptyCaCert, clusterNodes);

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
        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(invalidCert, clusterNodes);

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
                            .addresses(clusterNodes)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .build();

            TestUtilities.createAndTestClient(config);
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
