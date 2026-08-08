/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
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
 * Integration tests that verify mutual TLS behaviour end to end against a cluster.
 *
 * <p>The tests share a TLS cluster (3 shards, 1 replica each) started with {@code
 * --tls-auth-clients}, so the cluster bus and clients both use TLS with client certificates and the
 * server rejects any TLS connection that does not present a valid client certificate. This pair of
 * accepting/rejecting tests mirrors the Python shape and the standalone class in {@code
 * MutualTlsIntegrationTest}: without the rejecting case, the accepting case could pass against a
 * server that quietly ignored client certificates.
 */
public class MutualTlsClusterIntegrationTest {

    private static ValkeyCluster mtlsCluster;
    private static List<NodeAddress> nodeAddrs;
    private static byte[] clientCert;
    private static byte[] clientKey;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        mtlsCluster =
                new ValkeyCluster(
                        /* tls */ true,
                        /* clusterMode */ true,
                        /* shardCount */ 3,
                        /* replicaCount */ 1,
                        /* loadModule */ null,
                        /* addresses */ null,
                        /* tlsAuthClients */ true);
        nodeAddrs = mtlsCluster.getNodesAddr();

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
        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(nodeAddrs)
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
            assertEquals("PONG", client.ping().get());
        }
    }

    @Test
    void testMTlsMissingClientCertRejectedByServerRequiringOne() {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();
        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(nodeAddrs)
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }
}
