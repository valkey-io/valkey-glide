/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

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

    private static final boolean isWindows =
            System.getProperty("os.name").toLowerCase().contains("windows");

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
            
            if (isWindows) {
                // Use bash scripts for Windows + WSL
                command.add("wsl");
                command.add("--");
                
                if (tls) {
                    command.add("./start-cluster-tls.sh");
                } else if (replicaCount == 4) {
                    command.add("./start-cluster-az.sh");
                } else {
                    command.add("./start-cluster.sh");
                }
            } else {
                // Use Python cluster_manager.py for Linux/macOS
                command.add("python3");
                command.add(SCRIPT_FILE.toString());

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
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            // Set working directory to utils folder
            Path utilsDir = Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils");
            pb.directory(utilsDir.toFile());
            
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

            if (isWindows) {
                parseClusterScriptBashOutput(output.toString(), tls, replicaCount);
            } else {
                parseClusterScriptStartOutput(output.toString());
            }
        }
    }

    /** Constructor with default values */
    public ValkeyCluster(boolean tls) throws IOException, InterruptedException {
        this(tls, false, 3, 1, null, null);
    }

    private void parseClusterScriptBashOutput(String output, boolean tls, int replicaCount) {
        String hostPattern;
        if (tls) {
            hostPattern = "CLUSTER_TLS_HOSTS=";
        } else if (replicaCount == 4) {
            hostPattern = "AZ_CLUSTER_HOSTS=";
        } else {
            hostPattern = "CLUSTER_HOSTS=";
        }
        
        this.nodesAddr = new ArrayList<>();
        this.clusterFolder = ""; // Bash scripts manage their own directories
        
        for (String line : output.split("\n")) {
            if (line.contains(hostPattern)) {
                String[] parts = line.split(hostPattern);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid cluster hosts format");
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
                break;
            }
        }
        
        if (this.nodesAddr.isEmpty()) {
            throw new IllegalArgumentException("No cluster nodes found in bash script output");
        }
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
        if (isWindows) {
            // Use stop script for Windows + WSL
            List<String> command = new ArrayList<>();
            command.add("wsl");
            command.add("--");
            command.add("./stop-clusters.sh");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            // Set working directory to utils folder
            Path utilsDir = Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils");
            pb.directory(utilsDir.toFile());
            
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
        } else if (clusterFolder != null && !clusterFolder.isEmpty()) {
            // Use Python cluster_manager.py for Linux/macOS
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add(SCRIPT_FILE.toString());

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
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for cluster shutdown", e);
            }
        }
    }
}
