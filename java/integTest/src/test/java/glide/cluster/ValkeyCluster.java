/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import glide.api.models.configuration.NodeAddress;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
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
    
    private static final Path REMOTE_SCRIPT_FILE =
            Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils")
                    .resolve("remote_cluster_manager.py");
                    
    private static final Path ENHANCED_SCRIPT_FILE =
            Paths.get(System.getProperty("user.dir"))
                    .getParent()
                    .getParent()
                    .resolve("utils")
                    .resolve("enhanced_cluster_manager.py");

    private boolean tls = false;
    private String clusterFolder;
    private List<NodeAddress> nodesAddr;
    private boolean isRemoteCluster = false;
    private String remoteHost = null;
    private boolean useConfigFile = false;
    private String configFilePath = "/tmp/valkey_cluster_config.json";

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
            // Check if we should use remote cluster manager
            String remoteClusterHost = System.getenv("VALKEY_REMOTE_HOST");
            if (remoteClusterHost != null && !remoteClusterHost.isEmpty()) {
                startRemoteCluster(remoteClusterHost, tls, clusterMode, shardCount, replicaCount, loadModule);
            } else {
                startLocalCluster(tls, clusterMode, shardCount, replicaCount, loadModule);
            }
        }
    }

    /** Constructor with default values */
    public ValkeyCluster(boolean tls) throws IOException, InterruptedException {
        this(tls, false, 3, 1, null, null);
    }

    /**
     * Creates a ValkeyCluster by reading configuration from a file
     * This is useful when using the enhanced_cluster_manager.py script
     *
     * @param configFilePath Path to the JSON configuration file
     */
    public ValkeyCluster(String configFilePath) throws IOException, InterruptedException {
        this.useConfigFile = true;
        this.configFilePath = configFilePath;
        initFromConfigFile();
    }

    /**
     * Creates a ValkeyCluster using the enhanced cluster manager with strace monitoring
     *
     * @param tls Whether to use TLS
     * @param clusterMode Whether to use cluster mode
     * @param shardCount Number of shards
     * @param replicaCount Number of replicas
     * @param enableStrace Whether to enable strace signal monitoring
     * @param configFilePath Path where config file should be written (null for default)
     * @param straceOutputDir Directory for strace logs (null for default)
     */
    public ValkeyCluster(
            boolean tls,
            boolean clusterMode,
            int shardCount,
            int replicaCount,
            boolean enableStrace,
            String configFilePath,
            String straceOutputDir)
            throws IOException, InterruptedException {
        
        this.useConfigFile = true;
        this.configFilePath = configFilePath != null ? configFilePath : "/tmp/valkey_cluster_config.json";
        
        startEnhancedCluster(tls, clusterMode, shardCount, replicaCount, enableStrace, straceOutputDir);
        initFromConfigFile();
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

    private void startLocalCluster(boolean tls, boolean clusterMode, int shardCount, int replicaCount, List<String> loadModule) 
            throws IOException, InterruptedException {
        this.tls = tls;
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(SCRIPT_FILE.toString());

        if (tls) {
            command.add("--tls");
        }

        command.add("start");
        
        // Add prefix for cluster identification
        command.add("--prefix");
        command.add(tls ? "tls-cluster" : "cluster");

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

        System.out.println("Starting cluster with command: " + String.join(" ", command));
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
            System.err.println("Cluster creation failed with exit code: " + process.exitValue());
            System.err.println("Output: " + output);
            throw new RuntimeException("Failed to create cluster: " + output);
        }
        System.out.println("Cluster started successfully. Output: " + output);

        parseClusterScriptStartOutput(output.toString());
    }

    private void startRemoteCluster(String host, boolean tls, boolean clusterMode, int shardCount, int replicaCount, List<String> loadModule) 
            throws IOException, InterruptedException {
        this.isRemoteCluster = true;
        this.remoteHost = host;
        this.tls = tls;
        
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(REMOTE_SCRIPT_FILE.toString());
        command.add("--host");
        command.add(host);
        command.add("start");

        if (clusterMode) {
            command.add("--cluster-mode");
        }

        if (tls) {
            command.add("--tls");
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

        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroy();
            throw new RuntimeException("Timeout waiting for remote cluster creation");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to create remote cluster: " + output);
        }

        parseRemoteClusterOutput(output.toString());
    }

    private void parseRemoteClusterOutput(String output) {
        // Parse output from remote_cluster_manager.py
        for (String line : output.split("\n")) {
            if (line.startsWith("CLUSTER_NODES=")) {
                String nodesStr = line.substring("CLUSTER_NODES=".length());
                this.nodesAddr = new ArrayList<>();
                
                for (String addr : nodesStr.split(",")) {
                    String[] hostPort = addr.split(":");
                    if (hostPort.length == 2) {
                        this.nodesAddr.add(
                            NodeAddress.builder()
                                .host(hostPort[0])
                                .port(Integer.parseInt(hostPort[1]))
                                .build());
                    }
                }
                break;
            }
        }
        
        if (this.nodesAddr == null || this.nodesAddr.isEmpty()) {
            throw new RuntimeException("Failed to parse remote cluster nodes from output: " + output);
        }
    }

    private void startEnhancedCluster(
            boolean tls, 
            boolean clusterMode, 
            int shardCount, 
            int replicaCount, 
            boolean enableStrace,
            String straceOutputDir) throws IOException, InterruptedException {
        
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(ENHANCED_SCRIPT_FILE.toString());
        
        command.add("--config-file");
        command.add(this.configFilePath);
        
        if (straceOutputDir != null) {
            command.add("--strace-output");
            command.add(straceOutputDir);
        }
        
        if (enableStrace) {
            command.add("--enable-strace");
        }
        
        if (tls) {
            command.add("--tls");
        }
        
        if (clusterMode) {
            command.add("--cluster-mode");
        }
        
        command.add("--shard-count");
        command.add(String.valueOf(shardCount));
        command.add("--replica-count");
        command.add(String.valueOf(replicaCount));
        
        command.add("start");
        
        System.out.println("Starting enhanced cluster with command: " + String.join(" ", command));
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

        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroy();
            throw new RuntimeException("Timeout waiting for enhanced cluster creation");
        }

        if (process.exitValue() != 0) {
            System.err.println("Enhanced cluster creation failed with exit code: " + process.exitValue());
            System.err.println("Output: " + output);
            throw new RuntimeException("Failed to create enhanced cluster: " + output);
        }
        
        System.out.println("Enhanced cluster started successfully. Output: " + output);
    }

    private void initFromConfigFile() throws IOException {
        Path configPath = Paths.get(this.configFilePath);
        
        if (!Files.exists(configPath)) {
            throw new IOException("Configuration file not found: " + this.configFilePath);
        }
        
        try {
            String configContent = Files.readString(configPath);
            
            // Simple JSON parsing without external libraries
            this.tls = configContent.contains("\"tls_enabled\": true");
            
            // Extract cluster folder
            String folderPattern = "\"cluster_folder\": \"";
            int folderStart = configContent.indexOf(folderPattern);
            if (folderStart != -1) {
                folderStart += folderPattern.length();
                int folderEnd = configContent.indexOf("\"", folderStart);
                if (folderEnd != -1) {
                    this.clusterFolder = configContent.substring(folderStart, folderEnd);
                }
            }
            
            // Extract node addresses
            this.nodesAddr = new ArrayList<>();
            String nodesPattern = "\"nodes\": [";
            int nodesStart = configContent.indexOf(nodesPattern);
            if (nodesStart != -1) {
                int nodesEnd = configContent.indexOf("]", nodesStart);
                if (nodesEnd != -1) {
                    String nodesSection = configContent.substring(nodesStart + nodesPattern.length(), nodesEnd);
                    
                    // Parse each node entry
                    String[] nodeEntries = nodesSection.split("\\},\\s*\\{");
                    for (String nodeEntry : nodeEntries) {
                        // Clean up the entry
                        nodeEntry = nodeEntry.replace("{", "").replace("}", "").trim();
                        
                        String host = null;
                        int port = 0;
                        
                        // Extract host
                        String hostPattern = "\"host\": \"";
                        int hostStart = nodeEntry.indexOf(hostPattern);
                        if (hostStart != -1) {
                            hostStart += hostPattern.length();
                            int hostEnd = nodeEntry.indexOf("\"", hostStart);
                            if (hostEnd != -1) {
                                host = nodeEntry.substring(hostStart, hostEnd);
                            }
                        }
                        
                        // Extract port
                        String portPattern = "\"port\": ";
                        int portStart = nodeEntry.indexOf(portPattern);
                        if (portStart != -1) {
                            portStart += portPattern.length();
                            int portEnd = nodeEntry.indexOf(",", portStart);
                            if (portEnd == -1) portEnd = nodeEntry.length();
                            String portStr = nodeEntry.substring(portStart, portEnd).trim();
                            try {
                                port = Integer.parseInt(portStr);
                            } catch (NumberFormatException e) {
                                // Skip invalid port
                                continue;
                            }
                        }
                        
                        if (host != null && port > 0) {
                            this.nodesAddr.add(
                                NodeAddress.builder().host(host).port(port).build());
                        }
                    }
                }
            }
            
            if (this.nodesAddr.isEmpty()) {
                throw new IOException("No valid node addresses found in configuration file");
            }
            
            System.out.println("Loaded cluster configuration from: " + this.configFilePath);
            System.out.println("TLS: " + this.tls + ", Nodes: " + this.nodesAddr.size());
            
        } catch (Exception e) {
            throw new IOException("Failed to parse configuration file: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze strace logs for SIGTERM and other signals
     * Only works if the cluster was started with strace monitoring enabled
     */
    public void analyzeStraceSignals() throws IOException, InterruptedException {
        if (!useConfigFile) {
            System.out.println("Strace analysis only available for enhanced cluster manager");
            return;
        }
        
        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(ENHANCED_SCRIPT_FILE.toString());
        command.add("--config-file");
        command.add(this.configFilePath);
        command.add("analyze");
        
        System.out.println("Analyzing strace signals with command: " + String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line); // Print analysis results in real-time
            }
        }

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroy();
            throw new RuntimeException("Timeout waiting for strace analysis");
        }
        
        if (process.exitValue() != 0) {
            System.err.println("Strace analysis failed: " + output);
        }
    }

    @Override
    public void close() throws IOException {
        // Stop strace monitoring if using enhanced cluster manager
        if (useConfigFile) {
            try {
                List<String> command = new ArrayList<>();
                command.add("python3");
                command.add(ENHANCED_SCRIPT_FILE.toString());
                command.add("--config-file");
                command.add(this.configFilePath);
                command.add("stop");
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroy();
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to stop strace monitoring: " + e.getMessage());
            }
        }
        
        if (isRemoteCluster && remoteHost != null) {
            // Stop remote cluster
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add(REMOTE_SCRIPT_FILE.toString());
            command.add("--host");
            command.add(remoteHost);
            command.add("stop");

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
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroy();
                    throw new IOException("Timeout waiting for remote cluster shutdown");
                }

                if (process.exitValue() != 0) {
                    System.err.println("Warning: Failed to stop remote cluster: " + output);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for remote cluster shutdown", e);
            }
        } else if (clusterFolder != null && !clusterFolder.isEmpty()) {
            // Stop local cluster
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add(SCRIPT_FILE.toString());

            if (tls) {
                command.add("--tls");
            }

            command.add("stop");
            command.add("--prefix");
            command.add(tls ? "tls-cluster" : "cluster");
            command.add("--keep-folder");

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
                throw new IOException("Interrupted while waiting for cluster shutdown", e);
            }
        }
    }
}
