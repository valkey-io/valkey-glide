/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.PeriodicChecksStatus;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration test for failover recovery with multi-key fan-out operations.
 *
 * <p>Verifies that cross-slot DEL operations recover after a primary node crash when using
 * refreshTopologyFromInitialNodes=true and periodic checks disabled. The client connects directly
 * to the first cluster node as its sole initial node. Killing the node with SIGKILL leaves TCP
 * connections in half-open state, matching real crash behavior.
 *
 * <p>Without the fallback in calculate_topology_from_random_nodes, the topology refresh tries to
 * reconnect to the dead initial node and hangs, preventing recovery. With the fallback, it queries
 * existing healthy connections and discovers the updated topology from the promoted replica.
 *
 * <p>Requires: valkey-server (or redis-server), python3, cluster_manager.py
 */
@Tag("failover")
@Timeout(180)
public class FailoverRecoveryTests {

    @SneakyThrows
    @Test
    public void test_fanout_del_recovers_when_initial_node_unreachable() {
        try (ValkeyCluster cluster = new ValkeyCluster(false, true, 3, 1, null, null)) {
            List<NodeAddress> nodes = cluster.getNodesAddr();
            assertTrue(nodes.size() >= 6, "Need at least 6 nodes (3 primaries + 3 replicas)");

            NodeAddress firstNode = nodes.get(0);
            int targetPort = firstNode.getPort();
            String targetHost = firstNode.getHost();

            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(NodeAddress.builder().host(targetHost).port(targetPort).build())
                            .advancedConfiguration(
                                    AdvancedGlideClusterClientConfiguration.builder()
                                            .refreshTopologyFromInitialNodes(true)
                                            .periodicChecks(PeriodicChecksStatus.DISABLED)
                                            .build())
                            .requestTimeout(5000)
                            .build();

            GlideClusterClient client = GlideClusterClient.createClient(config).get(30, TimeUnit.SECONDS);

            try {
                // Populate keys across slots
                for (int i = 0; i < 100; i++) {
                    client.set("key:" + i, "value:" + i).get(5, TimeUnit.SECONDS);
                }
                client.del(new String[] {"key:0", "key:1", "key:2"}).get(5, TimeUnit.SECONDS);

                // Get the primary's node ID and PID before killing it
                String info =
                        (String)
                                client
                                        .customCommand(
                                                new String[] {"INFO", "server"}, new ByAddressRoute(targetHost, targetPort))
                                        .get(5, TimeUnit.SECONDS)
                                        .getSingleValue();
                String pid = null;
                for (String line : info.split("\n")) {
                    if (line.startsWith("process_id:")) {
                        pid = line.split(":")[1].trim().replace("\r", "");
                        break;
                    }
                }
                assertTrue(pid != null && !pid.isEmpty(), "Should find server PID");

                String nodeId =
                        ((String)
                                        client
                                                .customCommand(
                                                        new String[] {"CLUSTER", "MYID"},
                                                        new ByAddressRoute(targetHost, targetPort))
                                                .get(5, TimeUnit.SECONDS)
                                                .getSingleValue())
                                .trim();

                // Find the replica port by querying a different node
                String replicaPort = findReplicaPort(client, nodes, targetHost, targetPort, nodeId);

                // Start continuous cross-slot DELs
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                AtomicBoolean running = new AtomicBoolean(true);

                Thread delThread =
                        new Thread(
                                () -> {
                                    int round = 0;
                                    while (running.get() && round < 800) {
                                        String[] keys = {
                                            "key:" + (round % 30),
                                            "key:" + ((round + 10) % 30),
                                            "key:" + ((round + 20) % 30)
                                        };
                                        try {
                                            client.del(keys).get(5, TimeUnit.SECONDS);
                                            successCount.incrementAndGet();
                                        } catch (Exception e) {
                                            errorCount.incrementAndGet();
                                        }
                                        round++;
                                        try {
                                            Thread.sleep(100);
                                        } catch (InterruptedException ie) {
                                            break;
                                        }
                                    }
                                });
                delThread.start();
                Thread.sleep(3000); // steady state

                // Kill the initial node with SIGKILL via cluster_manager.py
                killNodeByPid(pid);
                Thread.sleep(2000);

                // Promote the replica using a separate client
                if (replicaPort != null) {
                    promoteReplica(targetHost, Integer.parseInt(replicaPort));
                }

                // Wait 55s, then verify no new errors in the last 10s.
                // Without the fallback, errors keep accumulating (client never recovers).
                // With the fallback, the client recovers and errors stop.
                Thread.sleep(55000);
                int errorsAtSnapshot = errorCount.get();
                Thread.sleep(10000);
                running.set(false);
                delThread.join(10000);

                int successes = successCount.get();
                int errors = errorCount.get();
                int errorsInLast10s = errors - errorsAtSnapshot;
                System.out.printf(
                        "Failover test: successes=%d, errors=%d, errorsInLast10s=%d%n",
                        successes, errors, errorsInLast10s);

                assertTrue(
                        successes > errors,
                        String.format("Client should recover. Successes: %d, Errors: %d", successes, errors));
                assertTrue(
                        errorsInLast10s == 0,
                        String.format(
                                "Errors still accumulating after 55s (got %d in last 10s, total %d)."
                                        + " Client did not recover.",
                                errorsInLast10s, errors));
            } finally {
                client.close();
            }
        }
    }

    /** Find the replica port for the given primary by querying a different cluster node. */
    private String findReplicaPort(
            GlideClusterClient client,
            List<NodeAddress> nodes,
            String primaryHost,
            int primaryPort,
            String primaryNodeId)
            throws Exception {
        for (NodeAddress node : nodes) {
            if (node.getPort() == primaryPort) continue;
            try {
                String clusterNodes =
                        (String)
                                client
                                        .customCommand(
                                                new String[] {"CLUSTER", "NODES"},
                                                new ByAddressRoute(node.getHost(), node.getPort()))
                                        .get(5, TimeUnit.SECONDS)
                                        .getSingleValue();
                for (String line : clusterNodes.split("\n")) {
                    if (line.contains("slave") && line.contains(primaryNodeId)) {
                        String addr = line.split("\\s+")[1].split("@")[0];
                        return addr.split(":")[1];
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
        return null;
    }

    /** Kill a node by PID using cluster_manager.py (cross-platform, handles WSL on Windows). */
    private void killNodeByPid(String pid) throws Exception {
        Path scriptFile =
                java.nio.file.Paths.get(System.getProperty("user.dir"))
                        .getParent()
                        .getParent()
                        .resolve("utils")
                        .resolve("cluster_manager.py");
        new ProcessBuilder(
                        "python3", scriptFile.toString(), "stop", "--prefix", "none-will-match", "--pids", pid)
                .redirectErrorStream(true)
                .start()
                .waitFor(10, TimeUnit.SECONDS);
    }

    /** Promote a replica by issuing CLUSTER FAILOVER FORCE via a short-lived client. */
    private void promoteReplica(String host, int port) throws Exception {
        GlideClusterClientConfiguration replicaConfig =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .requestTimeout(5000)
                        .build();
        GlideClusterClient replicaClient =
                GlideClusterClient.createClient(replicaConfig).get(10, TimeUnit.SECONDS);
        try {
            replicaClient
                    .customCommand(
                            new String[] {"CLUSTER", "FAILOVER", "FORCE"}, new ByAddressRoute(host, port))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            replicaClient.close();
        }
    }
}
