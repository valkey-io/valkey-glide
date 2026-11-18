/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.TestUtilities;
import glide.api.GlideClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.cluster.ValkeyCluster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that verify mutual TLS behaviour end to end.
 *
 * <p>The tests share a single-node TLS standalone server started with {@code --tls-auth-clients},
 * so the server rejects any TLS connection that does not present a valid client certificate. The
 * accepting case and the rejecting case are both required. Without the rejecting case, the
 * accepting case could pass against a server that quietly ignored client certificates.
 */
public class MutualTlsIntegrationTest {

    private static ValkeyCluster mtlsCluster;
    private static NodeAddress nodeAddr;
    private static byte[] clientCert;
    private static byte[] clientKey;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        mtlsCluster =
                new ValkeyCluster(
                        /* tls */ true,
                        /* clusterMode */ false,
                        /* shardCount */ 1,
                        /* replicaCount */ 0,
                        /* loadModule */ null,
                        /* addresses */ null,
                        /* tlsAuthClients */ true);
        List<NodeAddress> nodes = mtlsCluster.getNodesAddr();
        nodeAddr = nodes.get(0);

        Path tlsDir =
                Paths.get(System.getProperty("user.dir"))
                        .getParent()
                        .getParent()
                        .resolve("utils")
                        .resolve("tls_crts");
        caCert = Files.readAllBytes(tlsDir.resolve("ca.crt"));
        clientCert = Files.readAllBytes(tlsDir.resolve("server.crt"));
        clientKey = Files.readAllBytes(tlsDir.resolve("server.key"));
    }

    @AfterAll
    static void teardown() throws IOException {
        if (mtlsCluster != null) {
            mtlsCluster.close();
        }
    }

    @Test
    void testMTlsClientCertAcceptedByServerRequiringOne()
            throws ExecutionException, InterruptedException {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder()
                        .rootCertificates(caCert)
                        .useMutualTls(clientCert, clientKey)
                        .build();
        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(nodeAddr)
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        try (GlideClient client = GlideClient.createClient(config).get()) {
            assertEquals("PONG", client.ping().get());
        }
    }

    @Test
    void testMTlsMissingClientCertRejectedByServerRequiringOne() {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();
        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(nodeAddr)
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClient.createClient(config).get();
                });
    }

    /**
     * Verifies that mTLS configured from a keystore (via {@link
     * TlsAdvancedConfiguration.TlsAdvancedConfigurationBuilder#useMutualTlsFromKeyStore}) is accepted
     * by a server that requires a client certificate. This is the keystore-based counterpart of
     * {@link #testMTlsClientCertAcceptedByServerRequiringOne()}: the same client cert/key are loaded
     * from a PKCS12 keystore instead of PEM bytes.
     */
    @Test
    void testMTlsFromKeyStoreAcceptedByServerRequiringOne() throws Exception {
        Path keyStorePath = Files.createTempFile("mtls-client-identity", ".p12");
        char[] password = "testpass".toCharArray();

        try {
            TestUtilities.buildClientIdentityKeyStore(
                    keyStorePath, password, "PKCS12", clientCert, clientKey);

            TlsAdvancedConfiguration tlsConfig =
                    TlsAdvancedConfiguration.builder()
                            .rootCertificates(caCert)
                            .useMutualTlsFromKeyStore(keyStorePath.toString(), password, "PKCS12")
                            .build();
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(nodeAddr)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .build();

            try (GlideClient client = GlideClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        } finally {
            Files.deleteIfExists(keyStorePath);
        }
    }
}
