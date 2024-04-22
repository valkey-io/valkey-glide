// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"errors"
	"fmt"
	"log"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/suite"
)

type GlideTestSuite struct {
	suite.Suite
	standalonePorts []int
	clusterPorts    []int
	redisVersion    string
}

func (suite *GlideTestSuite) SetupSuite() {
	// Stop cluster in case previous test run was interrupted or crashed and didn't stop.
	// If an error occurs, we ignore it in case the servers actually were stopped before running this.
	runClusterManager(suite, []string{"stop", "--prefix", "redis-cluster"}, true)

	// Delete dirs if stop failed due to https://github.com/aws/glide-for-redis/issues/849
	err := os.RemoveAll("../../utils/clusters")
	if err != nil && !os.IsNotExist(err) {
		log.Fatal(err)
	}

	// Start standalone Redis instance
	clusterManagerOutput := runClusterManager(suite, []string{"start", "-r", "0"}, false)

	suite.standalonePorts = extractPorts(suite, clusterManagerOutput)
	suite.T().Logf("Standalone ports = %s", fmt.Sprint(suite.standalonePorts))

	// Start Redis cluster
	clusterManagerOutput = runClusterManager(suite, []string{"start", "--cluster-mode"}, false)

	suite.clusterPorts = extractPorts(suite, clusterManagerOutput)
	suite.T().Logf("Cluster ports = %s", fmt.Sprint(suite.clusterPorts))

	// Get Redis version
	byteOutput, err := exec.Command("redis-server", "-v").Output()
	if err != nil {
		suite.T().Fatal(err.Error())
	}

	suite.redisVersion = extractRedisVersion(string(byteOutput))
	suite.T().Logf("Redis version = %s", suite.redisVersion)
}

func extractPorts(suite *GlideTestSuite, output string) []int {
	var ports []int
	for _, line := range strings.Split(output, "\n") {
		if !strings.HasPrefix(line, "CLUSTER_NODES=") {
			continue
		}

		addresses := strings.Split(line, "=")[1]
		addressList := strings.Split(addresses, ",")
		for _, address := range addressList {
			portString := strings.Split(address, ":")[1]
			port, err := strconv.Atoi(portString)
			if err != nil {
				suite.T().Fatalf("Failed to parse port from cluster_manager.py output: %s", err.Error())
			}

			ports = append(ports, port)
		}
	}

	return ports
}

func runClusterManager(suite *GlideTestSuite, args []string, ignoreExitCode bool) string {
	pythonArgs := append([]string{"../../utils/cluster_manager.py"}, args...)
	output, err := exec.Command("python3", pythonArgs...).Output()
	if len(output) > 0 {
		suite.T().Logf("cluster_manager.py stdout:\n====\n%s\n====\n", string(output))
	}

	if err != nil {
		var exitError *exec.ExitError
		isExitError := errors.As(err, &exitError)
		if !isExitError {
			suite.T().Fatalf("Unexpected error while executing cluster_manager.py: %s", err.Error())
		}

		if exitError.Stderr != nil && len(exitError.Stderr) > 0 {
			suite.T().Logf("cluster_manager.py stderr:\n====\n%s\n====\n", string(exitError.Stderr))
		}

		if !ignoreExitCode {
			suite.T().Fatalf("cluster_manager.py script failed: %s", exitError.Error())
		}
	}

	return string(output)
}

func extractRedisVersion(output string) string {
	// Expected output format:
	// Redis server v=7.2.3 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=7504b1fedf883f2
	versionSection := strings.Split(output, " ")[2]
	return strings.Split(versionSection, "=")[1]
}

func TestGlideTestSuite(t *testing.T) {
	suite.Run(t, new(GlideTestSuite))
}

func (suite *GlideTestSuite) TearDownSuite() {
	runClusterManager(suite, []string{"stop", "--prefix", "redis-cluster", "--keep-folder"}, false)
}
