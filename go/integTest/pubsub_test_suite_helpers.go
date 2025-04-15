// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"errors"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"testing"

	"github.com/stretchr/testify/suite"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// TestSuiteInterface defines the common methods needed from a test suite
type TestSuiteInterface interface {
	suite.TestingSuite
	T() *testing.T
}

// PubSubServerVersionGetter is an interface for getting server version
type PubSubServerVersionGetter interface {
	TestSuiteInterface
	GetStandaloneHosts() []api.NodeAddress
	GetClusterHosts() []api.NodeAddress
	GetTLS() bool
}

// Helper methods for server management - with unique names to avoid conflicts
func pubsubParseHosts(suite TestSuiteInterface, addresses string) []api.NodeAddress {
	var result []api.NodeAddress

	addressList := strings.Split(addresses, ",")
	for _, address := range addressList {
		parts := strings.Split(address, ":")
		port, err := strconv.Atoi(parts[1])
		if err != nil {
			suite.T().Fatalf("Failed to parse port from string %s: %s", parts[1], err.Error())
		}

		result = append(result, api.NodeAddress{Host: parts[0], Port: port})
	}
	return result
}

func pubsubExtractAddresses(suite TestSuiteInterface, output string) []api.NodeAddress {
	for _, line := range strings.Split(output, "\n") {
		if !strings.HasPrefix(line, "CLUSTER_NODES=") {
			continue
		}

		addresses := strings.Split(line, "=")[1]
		return pubsubParseHosts(suite, addresses)
	}

	suite.T().Fatalf("Failed to parse port from cluster_manager.py output")
	return []api.NodeAddress{}
}

func pubsubRunClusterManager(suite TestSuiteInterface, args []string, ignoreExitCode bool) string {
	pythonArgs := append([]string{"../../utils/cluster_manager.py"}, args...)
	output, err := exec.Command("python3", pythonArgs...).CombinedOutput()
	if len(output) > 0 && !ignoreExitCode {
		suite.T().Logf("cluster_manager.py output:\n====\n%s\n====\n", string(output))
	}

	if err != nil {
		var exitError *exec.ExitError
		isExitError := errors.As(err, &exitError)
		if !isExitError {
			suite.T().Fatalf("Unexpected error while executing cluster_manager.py: %s", err.Error())
		}

		if len(exitError.Stderr) > 0 {
			suite.T().Logf("cluster_manager.py stderr:\n====\n%s\n====\n", string(exitError.Stderr))
		}

		if !ignoreExitCode {
			suite.T().Fatalf("cluster_manager.py script failed: %s", exitError.Error())
		}
	}

	return string(output)
}

func pubsubGetServerVersion(suite PubSubServerVersionGetter) string {
	var err error = nil
	hosts := suite.GetStandaloneHosts()
	if len(hosts) > 0 {
		clientConfig := api.NewGlideClientConfiguration().
			WithAddress(&hosts[0]).
			WithUseTLS(suite.GetTLS()).
			WithRequestTimeout(5000)

		client, err := api.NewGlideClient(clientConfig)
		if err == nil && client != nil {
			defer client.Close()
			info, _ := client.InfoWithOptions(options.InfoOptions{Sections: []options.Section{options.Server}})
			return pubsubExtractServerVersion(suite, info)
		}
	}

	clusterHosts := suite.GetClusterHosts()
	if len(clusterHosts) == 0 {
		if err != nil {
			suite.T().Fatalf("No cluster hosts configured, standalone failed with %s", err.Error())
		}
		suite.T().Fatal("No server hosts configured")
	}

	clientConfig := api.NewGlideClusterClientConfiguration().
		WithAddress(&clusterHosts[0]).
		WithUseTLS(suite.GetTLS()).
		WithRequestTimeout(5000)

	client, err := api.NewGlideClusterClient(clientConfig)
	if err == nil && client != nil {
		defer client.Close()

		info, _ := client.InfoWithOptions(
			options.ClusterInfoOptions{
				InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Server}},
				RouteOption: &options.RouteOption{Route: config.RandomRoute},
			},
		)
		return pubsubExtractServerVersion(suite, info.SingleValue())
	}
	suite.T().Fatalf("Can't connect to any server to get version: %s", err.Error())
	return ""
}

func pubsubExtractServerVersion(suite TestSuiteInterface, output string) string {
	// output format:
	//   # Server
	//   redis_version:7.2.3
	//	 ...
	// It can contain `redis_version` or `valkey_version` key or both. If both, `valkey_version` should be taken
	for _, line := range strings.Split(output, "\r\n") {
		if strings.Contains(line, "valkey_version") {
			return strings.Split(line, ":")[1]
		}
	}

	for _, line := range strings.Split(output, "\r\n") {
		if strings.Contains(line, "redis_version") {
			return strings.Split(line, ":")[1]
		}
	}
	suite.T().Fatalf("Can't read server version from INFO command output: %s", output)
	return ""
}

// SetupCoverage configures code coverage for the PubSub test suite
func SetupCoverage(t *testing.T) func() {
	// Get coverage output directory from environment or use default
	coverageDir := os.Getenv("COVERAGE_DIR")
	if coverageDir == "" {
		coverageDir = "coverage"
	}

	// Ensure coverage directory exists
	if err := os.MkdirAll(coverageDir, 0o755); err != nil {
		t.Fatalf("Failed to create coverage directory: %v", err)
	}

	// Coverage will be written by the -coverprofile flag when running tests
	// Return a no-op cleanup function since file handling is managed by the test runner
	return func() {}
}
