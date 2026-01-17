/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestUtilities.isWindows;

import glide.api.models.configuration.NodeAddress;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

    private static String getScriptPath() {
        if (!isWindows()) {
            return SCRIPT_FILE.toString();
        }

        // Use wslpath to convert Windows path to WSL format
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "wslpath", "-u", SCRIPT_FILE.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return output.toString().trim();
            }
        } catch (IOException | InterruptedException e) {
            // Fall back to original path if wslpath fails
        }

        return SCRIPT_FILE.toString();
    }

    private boolean tls = false;
    private String clusterFolder;
    private List<NodeAddress> nodesAddr;

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

        if (addresses != null && !addresses.isEmpty()) {
            initFromExistingCluster(addresses);
        } else {
            this.tls = tls;
            List<String> command = new ArrayList<>();
            if (isWindows()) {
                command.add("wsl");
            }
            command.add("python3");
            command.add(getScriptPath());

            if (tls) {
                command.add("--tls");
            }

            command.add("start");

            if (clusterMode) {
                command.add("--cluster-mode");
            }

            if (loadModule != null && !loadModule.isEmpty()) {
                for (String module : loadModule) {
                    command.add("--load-module");
                    command.add(module);
                }
            }

            command.add("-n");
            command.add(String.valueOf(shardCount));
            command.add("-r");
            command.add(String.valueOf(replicaCount));

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

            if (!process.waitFor(80, TimeUnit.SECONDS)) {
                process.destroy();
                throw new RuntimeException("Timeout waiting for cluster creation");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("Failed to create cluster: " + output);
            }

            parseClusterScriptStartOutput(output.toString());
        }
    }

    /** Constructor with default values */
    public ValkeyCluster(boolean tls) throws IOException, InterruptedException {
        this(tls, false, 3, 1, null, null);
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

                for (String addr : addresses) {
                    String[] hostPort = addr.split(":");
                    if (hostPort.length != 2) {
                        throw new IllegalArgumentException("Invalid node address format: " + addr);
                    }
                    this.nodesAddr.add(
                            NodeAddress.builder().host(hostPort[0]).port(Integer.parseInt(hostPort[1])).build());
                }
            }
        }
    }

    private void initFromExistingCluster(List<List<String>> addresses) {
        this.tls = false;
        this.clusterFolder = "";
        this.nodesAddr = new ArrayList<>();

        for (List<String> address : addresses) {
            if (address.size() != 2) {
                throw new IllegalArgumentException("Each address must contain host and port");
            }
            this.nodesAddr.add(
                    NodeAddress.builder()
                            .host(address.get(0))
                            .port(Integer.parseInt(address.get(1)))
                            .build());
        }
    }

    /** Gets the list of node addresses in the cluster */
    public List<NodeAddress> getNodesAddr() {
        return nodesAddr;
    }

    /** Gets the cluster folder path */
    public String getClusterFolder() {
        return clusterFolder;
    }

    @Override
    public void close() throws IOException {
        if (clusterFolder != null && !clusterFolder.isEmpty()) {
            List<String> command = new ArrayList<>();
            if (isWindows()) {
                command.add("wsl");
            }
            command.add("python3");
            command.add(getScriptPath());

            if (tls) {
                command.add("--tls");
            }

            command.add("stop");
            command.add("--cluster-folder");
            command.add(clusterFolder);

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
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroy();
                    throw new IOException("Timeout waiting for cluster shutdown");
                }

                if (process.exitValue() != 0) {
                    throw new IOException("Failed to stop cluster: " + output);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping cluster", e);
            }
        }
    }
}
