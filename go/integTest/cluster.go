// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// ValkeyCluster represents a test cluster instance
type ValkeyCluster struct {
	ClusterFolder string
	NodesAddr     []config.NodeAddress
	TLS           bool
}

// NewValkeyCluster creates a new ValkeyCluster instance
func NewValkeyCluster(tls bool, opts ...ClusterOption) (*ValkeyCluster, error) {
	config := defaultClusterConfig()
	for _, opt := range opts {
		opt(config)
	}

	cluster := &ValkeyCluster{TLS: tls}

	if config.addresses != nil {
		return cluster.initFromExistingCluster(config.addresses)
	}

	// Find the cluster_manager.py script
	scriptFile := filepath.Join("..", "..", "utils", "cluster_manager.py")
	if _, err := os.Stat(scriptFile); os.IsNotExist(err) {
		return nil, fmt.Errorf("cluster manager script not found at %s", scriptFile)
	}

	// Build command arguments
	args := []string{scriptFile}
	if tls {
		args = append(args, "--tls")
	}
	args = append(args, "start")
	if config.clusterMode {
		args = append(args, "--cluster-mode")
	}
	if len(config.loadModules) > 0 {
		for _, module := range config.loadModules {
			args = append(args, "--load-module", module)
		}
	}
	args = append(args, fmt.Sprintf("-n %d", config.shardCount))
	args = append(args, fmt.Sprintf("-r %d", config.replicaCount))

	// Execute cluster manager script
	cmd := exec.Command("python3", args...)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("failed to create cluster: %v\nOutput: %s", err, output)
	}

	// Parse output
	if err := cluster.parseClusterScriptStartOutput(string(output)); err != nil {
		return nil, err
	}

	return cluster, nil
}

func (c *ValkeyCluster) parseClusterScriptStartOutput(output string) error {
	lines := strings.Split(output, "\n")
	foundFolder := false
	foundNodes := false

	for _, line := range lines {
		if strings.Contains(line, "CLUSTER_FOLDER=") {
			parts := strings.SplitN(line, "CLUSTER_FOLDER=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid CLUSTER_FOLDER line format: %s", line)
			}
			c.ClusterFolder = strings.TrimSpace(parts[1])
			foundFolder = true
		}
		if strings.Contains(line, "CLUSTER_NODES=") {
			parts := strings.SplitN(line, "CLUSTER_NODES=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid CLUSTER_NODES line format: %s", line)
			}
			nodeAddrs := strings.Split(parts[1], ",")
			if len(nodeAddrs) == 0 {
				return fmt.Errorf("no nodes found in output")
			}

			c.NodesAddr = make([]config.NodeAddress, 0, len(nodeAddrs))
			for _, addr := range nodeAddrs {
				hostPort := strings.Split(strings.TrimSpace(addr), ":")
				if len(hostPort) != 2 {
					return fmt.Errorf("invalid node address format: %s", addr)
				}
				port, err := strconv.Atoi(hostPort[1])
				if err != nil {
					return fmt.Errorf("invalid port number: %s", hostPort[1])
				}
				c.NodesAddr = append(c.NodesAddr, config.NodeAddress{
					Host: hostPort[0],
					Port: port,
				})
			}
			foundNodes = true
		}
	}

	if !foundFolder || !foundNodes {
		return fmt.Errorf("missing required output fields")
	}
	return nil
}

func (c *ValkeyCluster) initFromExistingCluster(addresses [][]string) (*ValkeyCluster, error) {
	c.ClusterFolder = ""
	c.NodesAddr = make([]config.NodeAddress, 0, len(addresses))

	for _, addr := range addresses {
		if len(addr) != 2 {
			return nil, fmt.Errorf("invalid address format: expected [host, port], got %v", addr)
		}
		port, err := strconv.Atoi(addr[1])
		if err != nil {
			return nil, fmt.Errorf("invalid port number: %s", addr[1])
		}
		c.NodesAddr = append(c.NodesAddr, config.NodeAddress{
			Host: addr[0],
			Port: port,
		})
	}
	return c, nil
}

// Close stops the cluster and cleans up resources
func (c *ValkeyCluster) Close() error {
	if c.ClusterFolder == "" {
		return nil // Nothing to clean up for existing clusters
	}

	scriptFile := filepath.Join("..", "..", "utils", "cluster_manager.py")
	args := []string{scriptFile}
	if c.TLS {
		args = append(args, "--tls")
	}
	args = append(args, "stop", "--cluster-folder", c.ClusterFolder)

	cmd := exec.Command("python3", args...)
	if output, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("failed to stop cluster %s: %v\nOutput: %s", c.ClusterFolder, err, output)
	}
	return nil
}

// GetNodesAddresses returns the list of node addresses in the cluster
func (c *ValkeyCluster) GetNodesAddresses() []config.NodeAddress {
	return c.NodesAddr
}

// ClusterConfig holds configuration options for creating a cluster
type clusterConfig struct {
	clusterMode  bool
	shardCount   int
	replicaCount int
	loadModules  []string
	addresses    [][]string
}

func defaultClusterConfig() *clusterConfig {
	return &clusterConfig{
		clusterMode:  false,
		shardCount:   3,
		replicaCount: 1,
	}
}

// ClusterOption defines a cluster configuration option
type ClusterOption func(*clusterConfig)

// WithClusterMode sets whether to use cluster mode
func WithClusterMode(enabled bool) ClusterOption {
	return func(c *clusterConfig) {
		c.clusterMode = enabled
	}
}

// WithShardCount sets the number of shards
func WithShardCount(count int) ClusterOption {
	return func(c *clusterConfig) {
		c.shardCount = count
	}
}

// WithReplicaCount sets the number of replicas
func WithReplicaCount(count int) ClusterOption {
	return func(c *clusterConfig) {
		c.replicaCount = count
	}
}

// WithLoadModules sets the modules to load
func WithLoadModules(modules []string) ClusterOption {
	return func(c *clusterConfig) {
		c.loadModules = modules
	}
}

// WithExistingAddresses sets addresses for an existing cluster
func WithExistingAddresses(addresses [][]string) ClusterOption {
	return func(c *clusterConfig) {
		c.addresses = addresses
	}
}
