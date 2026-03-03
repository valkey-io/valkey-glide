/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package com.company.glide.dnsfailover;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.PeriodicChecksManualInterval;

/**
 * E2E test for DNS-based failover with refreshTopologyFromInitialNodes.
 *
 * <p>After a client connects to a cluster, a DNS failover should cause the client to reconnect to
 * the new cluster the DNS is pointing to. This is simulated by updating the DNS within Docker to
 * point to a different cluster.
 *
 * <p><b>Test Flow:</b>
 *
 * <ol>
 *   <li>Connect to Cluster A.
 *   <li>Write data to Cluster A.
 *   <li>Simulate DNS failover (update Docker DNS).
 *   <li>Wait for topology refresh.
 *   <li>Verify client reconnected to Cluster B.
 *   <li>Verify data from Cluster A is no longer accessible.
 * </ol>
 */
public class DnsFailoverTest {

    private static final int CLUSTER_INIT_WAIT_MS = 10000;
    private static final int TOPOLOGY_REFRESH_WAIT_MS = 5000;
    private static final int DNS_PROPAGATION_WAIT_MS = 1000;
    private static final int PERIODIC_CHECK_INTERVAL_SEC = 2;

    private static final String NETWORK_NAME = "dns-failover-test_test-net";
    private static final String DNS_SERVER_IP = "172.31.0.2";
    private static final String CLUSTER_B_NODE_1_IP = "172.31.0.21";

    public static void main(String[] args) {
        try {
            runTest();
        } catch (Exception e) {
            System.err.println("DNS Failover Test FAILED");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runTest() throws Exception {
        System.out.println("Waiting for clusters to initialize...");
        Thread.sleep(CLUSTER_INIT_WAIT_MS);

        System.out.println("Connecting to Cluster A...");
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host("cluster.local").port(6379).build())
                        .useTLS(false)
                        .requestTimeout(5000)
                        .advancedConfiguration(
                                AdvancedGlideClusterClientConfiguration.builder()
                                        .refreshTopologyFromInitialNodes(true)
                                        .periodicChecks(
                                                PeriodicChecksManualInterval.builder()
                                                        .durationInSec(PERIODIC_CHECK_INTERVAL_SEC)
                                                        .build())
                                        .build())
                        .build();

        GlideClusterClient client = GlideClusterClient.createClient(config).get();

        System.out.println("Writing data to Cluster A...");
        String keyA = "test_key_cluster_a";
        String valueA = "value_from_cluster_a";
        client.set(keyA, valueA).get();
        verifyKeyValue(client, keyA, valueA);

        System.out.println("Simulating DNS failover...");
        System.out.println("  Stopping Cluster A...");
        executeCommand("docker stop cluster-a-node-1 cluster-a-node-2 cluster-a-node-3");
        System.out.println("  Updating DNS to point to Cluster B...");
        updateDns(CLUSTER_B_NODE_1_IP);

        System.out.println("Waiting for topology refresh...");
        Thread.sleep(TOPOLOGY_REFRESH_WAIT_MS);

        System.out.println("Verifying connection to Cluster B...");
        String keyB = "test_key_cluster_b";
        String valueB = "value_from_cluster_b";
        client.set(keyB, valueB).get();
        verifyKeyValue(client, keyB, valueB);

        String checkA = client.get(keyA).get();
        if (checkA != null) {
            throw new RuntimeException("ERROR: Client still sees data from Cluster A");
        }

        System.out.println("DNS Failover Test PASSED");

        client.close();
    }

    private static void verifyKeyValue(GlideClusterClient client, String key, String expectedValue)
            throws Exception {
        String actual = client.get(key).get();
        if (!expectedValue.equals(actual)) {
            throw new RuntimeException(
                    String.format("Expected '%s' but got '%s' for key '%s'", expectedValue, actual, key));
        }
    }

    private static void updateDns(String newIp) throws Exception {
        executeCommand("docker stop dns-server");
        executeCommand("docker rm dns-server");

        String command =
                String.format(
                        "docker run -d --name dns-server "
                                + "--network %s "
                                + "--ip %s "
                                + "--cap-add=NET_ADMIN "
                                + "andyshinn/dnsmasq:latest "
                                + "--log-queries --no-resolv --address=/cluster.local/%s",
                        NETWORK_NAME, DNS_SERVER_IP, newIp);
        executeCommand(command);

        Thread.sleep(DNS_PROPAGATION_WAIT_MS);
    }

    private static void executeCommand(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[] {"sh", "-c", command});
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
}
