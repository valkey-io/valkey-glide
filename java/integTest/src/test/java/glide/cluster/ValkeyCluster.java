/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import glide.api.models.configuration.NodeAddress;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** ValkeyCluster class for managing test clusters */
public class ValkeyCluster implements AutoCloseable {
    private static final Path SCRIPT_FILE =
            Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils")
                    .resolve("cluster_manager.py");

    private static final Path VPC_MANAGER_SCRIPT =
            Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils")
                    .resolve("multi_engine_manager.py");

    private static final Path REMOTE_MANAGER_SCRIPT =
            Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils")
                    .resolve("remote_cluster_manager.py");

    /** Get platform-specific Python command with WSL support */
    private static List<String> getPythonCommand() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            // Check if we should use VPC or remote cluster managers
            String vpcHost = System.getenv("VALKEY_VPC_HOST");
            String remoteHost = System.getenv("VALKEY_REMOTE_HOST");

            if (vpcHost != null || remoteHost != null) {
                // Use native Windows Python for remote/VPC managers
                return Arrays.asList("python3");
            } else {
                // Use WSL for local cluster manager
                return Arrays.asList("wsl", "--", "python3");
            }
        } else {
            return Arrays.asList("python3");
        }
    }

    /** Get the appropriate cluster manager script and arguments */
    private static ClusterManagerInfo getClusterManagerInfo() {
        String vpcHost = System.getenv("VALKEY_VPC_HOST");
        String remoteHost = System.getenv("VALKEY_REMOTE_HOST");

        if (vpcHost != null && !vpcHost.isEmpty()) {
            // Use VPC multi-engine manager
            return new ClusterManagerInfo(VPC_MANAGER_SCRIPT, "vpc", vpcHost);
        } else if (remoteHost != null && !remoteHost.isEmpty()) {
            // Use remote cluster manager
            return new ClusterManagerInfo(REMOTE_MANAGER_SCRIPT, "remote", remoteHost);
        } else {
            // Use local cluster manager
            return new ClusterManagerInfo(SCRIPT_FILE, "local", null);
        }
    }

    private static class ClusterManagerInfo {
        final Path scriptPath;
        final String type;
        final String host;

        ClusterManagerInfo(Path scriptPath, String type, String host) {
            this.scriptPath = scriptPath;
            this.type = type;
            this.host = host;
        }
    }

    private boolean tls = false;
    private String clusterFolder;
    private List<NodeAddress> nodesAddr;
    private ClusterManagerInfo managerInfo;

    /**
     * Creates a new ValkeyCluster instance
     *
     * @param tls Whether to use TLS
     * @param clusterMode Whether to use cluster mode
     * @param shardCount Number of shards (default 3)
     * @param replicaCount Number of replicas (default 1)
     * @param loadModule Optional list of module paths to load
     * @param addresses Optional list of existing cluster addresses
     */
    public ValkeyCluster(
            boolean tls,
            boolean clusterMode,
            int shardCount,
            int replicaCount,
            List<String> loadModule,
            List<List<String>> addresses)
            throws IOException, InterruptedException {

        this.managerInfo = getClusterManagerInfo();

        if (addresses != null && !addresses.isEmpty()) {
            initFromExistingCluster(addresses);
        } else {
            this.tls = tls;
            List<String> command = new ArrayList<>();
            command.addAll(getPythonCommand());
            command.add(managerInfo.scriptPath.toString());

            // Add manager-specific arguments
            if ("vpc".equals(managerInfo.type)) {
                command.add("--host");
                command.add(managerInfo.host);
                command.add("start");

                // Add engine version if specified
                String engineVersion = System.getProperty("engine-version", "valkey-8.0");
                command.add("--engine");
                command.add(engineVersion);

                if (clusterMode) {
                    command.add("--cluster-mode");
                }
            } else if ("remote".equals(managerInfo.type)) {
                command.add("--host");
                command.add(managerInfo.host);
                command.add("start");

                if (clusterMode) {
                    command.add("--cluster-mode");
                }
            } else {
                // Local cluster manager
                command.add("start"); // Action must come first

                if (clusterMode) {
                    command.add("--cluster-mode");
                }

                // Add host parameter - use environment variable or default to localhost
                String host = System.getenv("VALKEY_INTEG_TEST_IP");
                if (host == null || host.isEmpty()) {
                    host = "127.0.0.1";
                }
                command.add("--host");
                command.add(host);
            }

            if (tls) {
                command.add("--tls");
            }

            command.add("-n");
            command.add(String.valueOf(shardCount));
            command.add("-r");
            command.add(String.valueOf(replicaCount));

            if (loadModule != null && !loadModule.isEmpty()) {
                for (String module : loadModule) {
                    command.add("--load-module");
                    command.add(module);
                }
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (!process.waitFor(120, TimeUnit.SECONDS)) { // Increased timeout for remote operations
                process.destroy();
                throw new RuntimeException("Timeout waiting for cluster creation");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("Failed to create cluster: " + output);
            }

            if ("vpc".equals(managerInfo.type) || "remote".equals(managerInfo.type)) {
                parseRemoteClusterOutput(output.toString());
            } else {
                parseClusterScriptStartOutput(output.toString());
            }
        }
    }

    /** Constructor with default values */
    public ValkeyCluster(boolean tls) throws IOException, InterruptedException {
        this(tls, false, 3, 1, null, null);
    }

    private void parseRemoteClusterOutput(String output) {
        // Parse CLUSTER_ENDPOINTS=host1:port1,host2:port2,... format
        for (String line : output.split("\n")) {
            if (line.contains("CLUSTER_ENDPOINTS=")) {
                this.nodesAddr = new ArrayList<>();
                String[] parts = line.split("CLUSTER_ENDPOINTS=");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid CLUSTER_ENDPOINTS format");
                }

                String[] endpoints = parts[1].split(",");
                if (endpoints.length == 0) {
                    throw new IllegalArgumentException("No cluster endpoints found");
                }

                for (String endpoint : endpoints) {
                    String[] hostPort = endpoint.trim().split(":");
                    if (hostPort.length != 2) {
                        throw new IllegalArgumentException("Invalid endpoint format: " + endpoint);
                    }

                    try {
                        int port = Integer.parseInt(hostPort[1]);
                        this.nodesAddr.add(NodeAddress.builder().host(hostPort[0]).port(port).build());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid port number in endpoint: " + endpoint);
                    }
                }

                // Set a dummy cluster folder for remote clusters
                this.clusterFolder = "remote-cluster";
                return;
            }
        }

        throw new IllegalArgumentException("No CLUSTER_ENDPOINTS found in output: " + output);
    }

    private void parseClusterScriptStartOutput(String output) {
        if (!output.contains("CLUSTER_FOLDER") || !output.contains("CLUSTER_NODES")) {
            throw new IllegalArgumentException("Invalid cluster script output");
        }

        for (String line : output.split("\n")) {
            if (line.contains("CLUSTER_FOLDER=")) {
                String[] parts = line.split("CLUSTER_FOLDER=");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid CLUSTER_FOLDER format");
                }
                this.clusterFolder = parts[1];
            }

            if (line.contains("CLUSTER_NODES=")) {
                this.nodesAddr = new ArrayList<>();
                String[] parts = line.split("CLUSTER_NODES=");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid CLUSTER_NODES format");
                }

                String[] addresses = parts[1].split(",");
                if (addresses.length == 0) {
                    throw new IllegalArgumentException("No cluster nodes found");
                }

                for (String address : addresses) {
                    String[] hostPort = address.split(":");
                    if (hostPort.length != 2) {
                        throw new IllegalArgumentException("Invalid address format");
                    }

                    try {
                        int port = Integer.parseInt(hostPort[1]);
                        this.nodesAddr.add(NodeAddress.builder().host(hostPort[0]).port(port).build());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid port number");
                    }
                }
            }
        }
    }

    public List<NodeAddress> getNodesAddr() {
        return nodesAddr;
    }

    private void initFromExistingCluster(List<List<String>> addresses) {
        this.nodesAddr = new ArrayList<>();
        for (List<String> address : addresses) {
            if (address.size() >= 2) {
                try {
                    String host = address.get(0);
                    int port = Integer.parseInt(address.get(1));
                    this.nodesAddr.add(NodeAddress.builder().host(host).port(port).build());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port number in address: " + address);
                }
            }
        }
    }

    @Override
    public void close() throws IOException, InterruptedException {
        if (clusterFolder != null && !clusterFolder.isEmpty()) {
            List<String> command = new ArrayList<>();
            command.addAll(getPythonCommand());

            // Use appropriate script based on manager type
            if ("vpc".equals(managerInfo.type)) {
                command.add(managerInfo.scriptPath.toString());
                command.add("--host");
                command.add(managerInfo.host);
                command.add("stop");

                // Add engine version if specified
                String engineVersion = System.getProperty("engine-version", "valkey-8.0");
                command.add("--engine");
                command.add(engineVersion);
            } else if ("remote".equals(managerInfo.type)) {
                command.add(managerInfo.scriptPath.toString());
                command.add("--host");
                command.add(managerInfo.host);
                command.add("stop");
            } else {
                // Local cluster manager
                command.add(managerInfo.scriptPath.toString());

                if (tls) {
                    command.add("--tls");
                }

                command.add("stop");

                // Add host parameter - use environment variable or default to localhost
                String host = System.getenv("VALKEY_INTEG_TEST_IP");
                if (host == null || host.isEmpty()) {
                    host = "127.0.0.1";
                }
                command.add("--host");
                command.add(host);

                command.add("--cluster-folder");
                command.add(clusterFolder);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            try {
                int timeoutSeconds =
                        ("vpc".equals(managerInfo.type) || "remote".equals(managerInfo.type)) ? 30 : 20;
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroy();
                    throw new IOException("Timeout waiting for cluster shutdown");
                }

                if (process.exitValue() != 0) {
                    throw new IOException("Failed to stop cluster: " + output);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for cluster shutdown", e);
            }
        }
    }
}
