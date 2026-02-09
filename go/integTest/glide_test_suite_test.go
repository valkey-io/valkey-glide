// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

type ClientTypeFlag uint

const (
	StandaloneFlag ClientTypeFlag = 1 << iota
	ClusterFlag
)

func (c ClientTypeFlag) Has(ctype ClientTypeFlag) bool {
	return c&ctype != 0
}

type GlideTestSuite struct {
	suite.Suite
	standaloneHosts []config.NodeAddress
	clusterHosts    []config.NodeAddress
	tls             bool
	serverVersion   string
	clients         []interfaces.GlideClientCommands
	clusterClients  []interfaces.GlideClusterClientCommands
}

var (
	tls             = flag.Bool("tls", false, "Set to true to enable TLS connections")
	clusterHosts    = flag.String("cluster-endpoints", "", "Specifies specific endpoints the cluster nodes are running on")
	standaloneHosts = flag.String(
		"standalone-endpoints",
		"",
		"Specifies specific endpoints the standalone server is running on",
	)
	longTimeoutTests = flag.Bool("long-timeout-tests", false, "Set to true to run tests with longer timeouts")
	otelTest         = flag.Bool("otel-test", false, "Set to true to run opentelemetry tests")
)

func (suite *GlideTestSuite) SetupSuite() {
	// Stop cluster in case previous test run was interrupted or crashed and didn't stop.
	// If an error occurs, we ignore it in case the servers actually were stopped before running this.
	runClusterManager(suite, []string{"stop", "--prefix", "cluster"}, true)

	// Delete dirs if stop failed due to https://github.com/valkey-io/valkey-glide/issues/849
	err := os.RemoveAll("../../utils/clusters")
	if err != nil && !os.IsNotExist(err) {
		log.Fatal(err)
	}

	cmd := []string{}
	suite.tls = false
	if *tls {
		cmd = []string{"--tls"}
		suite.tls = true
	}
	suite.T().Logf("TLS = %t", suite.tls)

	// Note: code does not start standalone if cluster hosts are given and vice versa
	startServer := true

	if *standaloneHosts != "" {
		suite.standaloneHosts = parseHosts(suite, *standaloneHosts)
		startServer = false
	}
	if *clusterHosts != "" {
		suite.clusterHosts = parseHosts(suite, *clusterHosts)
		startServer = false
	}
	if startServer {
		// Start standalone instance
		clusterManagerOutput := runClusterManager(suite, append(cmd, "start", "-r", "3"), false)
		suite.standaloneHosts = extractAddresses(suite, clusterManagerOutput)

		// Start cluster
		clusterManagerOutput = runClusterManager(suite, append(cmd, "start", "--cluster-mode", "-r", "3"), false)
		suite.clusterHosts = extractAddresses(suite, clusterManagerOutput)
	}

	suite.T().Logf("Standalone hosts = %s", fmt.Sprint(suite.standaloneHosts))
	suite.T().Logf("Cluster hosts = %s", fmt.Sprint(suite.clusterHosts))

	// Get server version
	suite.serverVersion = getServerVersion(suite)
	suite.T().Logf("Detected server version = %s", suite.serverVersion)

	// OpenTelemetry illegal config tests and setup
	if *otelTest {
		WrongOpenTelemetryConfig(suite)
		intervalMs := int64(otelSpanFlushIntervalMs)
		openTelemetryConfig := glide.OpenTelemetryConfig{
			Traces: &glide.OpenTelemetryTracesConfig{
				Endpoint:         validFileEndpointTraces,
				SamplePercentage: 100,
			},
			Metrics: &glide.OpenTelemetryMetricsConfig{
				Endpoint: validEndpointMetrics,
			},
			FlushIntervalMs: &intervalMs,
			// Configure SpanFromContext to enable parent-child span relationships
			SpanFromContext: glide.DefaultSpanFromContext,
		}
		err := glide.GetOtelInstance().Init(openTelemetryConfig)
		assert.NoError(suite.T(), err)
	}
}

func parseHosts(suite *GlideTestSuite, addresses string) []config.NodeAddress {
	var result []config.NodeAddress

	addressList := strings.Split(addresses, ",")
	for _, address := range addressList {
		parts := strings.Split(address, ":")
		port, err := strconv.Atoi(parts[1])
		if err != nil {
			suite.T().Fatalf("Failed to parse port from string %s: %s", parts[1], err.Error())
		}

		result = append(result, config.NodeAddress{Host: parts[0], Port: port})
	}
	return result
}

func extractClusterFolder(suite *GlideTestSuite, output string) string {
	lines := strings.Split(output, "\n")
	foundFolder := false
	clusterFolder := ""

	for _, line := range lines {
		if strings.Contains(line, "CLUSTER_FOLDER=") {
			parts := strings.SplitN(line, "CLUSTER_FOLDER=", 2)
			if len(parts) != 2 {
				suite.T().Fatalf("invalid CLUSTER_FOLDER line format: %s", line)
			}
			clusterFolder = strings.TrimSpace(parts[1])
			foundFolder = true
		}
	}

	if !foundFolder {
		suite.T().Fatalf("missing required output fields")
	}
	return clusterFolder
}

func extractAddresses(suite *GlideTestSuite, output string) []config.NodeAddress {
	for _, line := range strings.Split(output, "\n") {
		if !strings.HasPrefix(line, "CLUSTER_NODES=") {
			continue
		}

		addresses := strings.Split(line, "=")[1]
		return parseHosts(suite, addresses)
	}

	suite.T().Fatalf("Failed to parse port from cluster_manager.py output")
	return []config.NodeAddress{}
}

func runClusterManager(suite *GlideTestSuite, args []string, ignoreExitCode bool) string {
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

func getServerVersion(suite *GlideTestSuite) string {
	var err error = nil
	if len(suite.standaloneHosts) > 0 {
		clientConfig := config.NewClientConfiguration().
			WithAddress(&suite.standaloneHosts[0]).
			WithUseTLS(suite.tls).
			WithRequestTimeout(5 * time.Second)

		// If TLS is enabled, try to load custom certificates
		if suite.tls {
			if certData, certErr := loadCaCertificateForTests(); certErr == nil {
				tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
				advancedConfig := config.NewAdvancedClientConfiguration().WithTlsConfiguration(tlsConfig)
				clientConfig = clientConfig.WithAdvancedConfiguration(advancedConfig)
			}
		}

		client, err := glide.NewClient(clientConfig)
		if err == nil && client != nil {
			defer client.Close()
			info, _ := client.InfoWithOptions(
				context.Background(),
				options.InfoOptions{Sections: []constants.Section{constants.Server}},
			)
			return extractServerVersion(suite, info)
		}
	}
	if len(suite.clusterHosts) == 0 {
		if err != nil {
			suite.T().Fatalf("No cluster hosts configured, standalone failed with %s", err.Error())
		}
		suite.T().Fatal("No server hosts configured")
	}

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5 * time.Second)

	// If TLS is enabled, try to load custom certificates
	if suite.tls {
		if certData, certErr := loadCaCertificateForTests(); certErr == nil {
			tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
			advancedConfig := config.NewAdvancedClusterClientConfiguration().WithTlsConfiguration(tlsConfig)
			clientConfig = clientConfig.WithAdvancedConfiguration(advancedConfig)
		}
	}

	client, err := glide.NewClusterClient(clientConfig)
	if err == nil && client != nil {
		defer client.Close()

		info, _ := client.InfoWithOptions(
			context.Background(),
			options.ClusterInfoOptions{
				InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.Server}},
				RouteOption: &options.RouteOption{Route: config.RandomRoute},
			},
		)
		return extractServerVersion(suite, info.SingleValue())
	}
	suite.T().Fatalf("Can't connect to any server to get version: %s", err.Error())
	return ""
}

func extractServerVersion(suite *GlideTestSuite, output string) string {
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

func TestGlideTestSuite(t *testing.T) {
	suite.Run(t, new(GlideTestSuite))
}

func (suite *GlideTestSuite) TearDownSuite() {
	runClusterManager(suite, []string{"stop", "--prefix", "cluster", "--keep-folder"}, true)
}

func (suite *GlideTestSuite) TearDownTest() {
	if *otelTest {
		time.Sleep(100 * time.Millisecond)
		// Remove the span file if it exists
		if _, err := os.Stat(validEndpointTraces); err == nil {
			if err := os.Remove(validEndpointTraces); err != nil {
				suite.T().Logf("Failed to remove span file: %v", err)
			}
		}
	}

	for _, client := range suite.clients {
		client.FlushDB(context.Background())
		client.Close()
	}
	suite.clients = nil // Clear the slice

	for _, client := range suite.clusterClients {
		client.FlushDB(context.Background())
		client.Close()
	}
	suite.clusterClients = nil // Clear the slice

	// Clear the callback context for the next test
	callbackCtx.Range(func(key, value any) bool {
		callbackCtx.Delete(key)
		return true
	})
}

func (suite *GlideTestSuite) runWithDefaultClients(test func(client interfaces.BaseClientCommands)) {
	clients := suite.getDefaultClients()
	suite.runWithClients(clients, test)
}

func (suite *GlideTestSuite) runWithSpecificClients(
	clientFlag ClientTypeFlag,
	test func(client interfaces.BaseClientCommands),
) {
	clients := suite.getSpecificClients(clientFlag)
	suite.runWithClients(clients, test)
}

func (suite *GlideTestSuite) runWithTimeoutClients(test func(client interfaces.BaseClientCommands)) {
	clients := suite.getTimeoutClients()
	suite.runWithClients(clients, test)
}

func (suite *GlideTestSuite) runParallelizedWithDefaultClients(
	parallelism int,
	count int64,
	timeout time.Duration,
	test func(client interfaces.BaseClientCommands),
) {
	clients := suite.getDefaultClients()
	suite.runParallelizedWithClients(clients, parallelism, count, timeout, test)
}

func (suite *GlideTestSuite) getDefaultClients() []interfaces.BaseClientCommands {
	return suite.getSpecificClients(StandaloneFlag | ClusterFlag)
}

func (suite *GlideTestSuite) getSpecificClients(clientFlag ClientTypeFlag) []interfaces.BaseClientCommands {
	clients := make([]interfaces.BaseClientCommands, 0)
	if clientFlag.Has(StandaloneFlag) {
		standaloneClient := suite.defaultClient()
		clients = append(clients, standaloneClient)
	}
	if clientFlag.Has(ClusterFlag) {
		clusterClient := suite.defaultClusterClient()
		clients = append(clients, clusterClient)
	}
	return clients
}

func (suite *GlideTestSuite) getTimeoutClients() []interfaces.BaseClientCommands {
	clients := []interfaces.BaseClientCommands{}
	clusterTimeoutClient, err := suite.createConnectionTimeoutClient(250, 20*time.Second, nil)
	if err != nil {
		suite.T().Fatalf("Failed to create cluster timeout client: %s", err.Error())
	}
	clients = append(clients, clusterTimeoutClient)

	standaloneTimeoutClient, err := suite.createConnectionTimeoutClusterClient(250, 20*time.Second)
	if err != nil {
		suite.T().Fatalf("Failed to create standalone timeout client: %s", err.Error())
	}
	clients = append(clients, standaloneTimeoutClient)

	return clients
}

func (suite *GlideTestSuite) defaultClientConfig() *config.ClientConfiguration {
	clientConfig := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5 * time.Second)

	// Set default connection timeout for tests
	advancedConfig := config.NewAdvancedClientConfiguration().
		WithConnectionTimeout(10 * time.Second)

	// If TLS is enabled, try to load custom certificates
	if suite.tls {
		if certData, certErr := loadCaCertificateForTests(); certErr == nil {
			tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
			advancedConfig = advancedConfig.WithTlsConfiguration(tlsConfig)
		}
	}

	clientConfig = clientConfig.WithAdvancedConfiguration(advancedConfig)

	return clientConfig
}

func (suite *GlideTestSuite) defaultClient() *glide.Client {
	config := suite.defaultClientConfig()
	client, err := suite.client(config)
	require.NoError(suite.T(), err)
	return client
}

func (suite *GlideTestSuite) client(config *config.ClientConfiguration) (*glide.Client, error) {
	client, err := glide.NewClient(config)
	if err != nil {
		return nil, err
	}
	if client == nil {
		return nil, fmt.Errorf("client is nil")
	}

	suite.clients = append(suite.clients, client)
	return client, nil
}

func (suite *GlideTestSuite) defaultClusterClientConfig() *config.ClusterClientConfiguration {
	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5 * time.Second)

	// Set default connection timeout for tests
	advancedConfig := config.NewAdvancedClusterClientConfiguration().
		WithConnectionTimeout(10 * time.Second)

	// If TLS is enabled, try to load custom certificates
	if suite.tls {
		if certData, certErr := loadCaCertificateForTests(); certErr == nil {
			tlsConfig := config.NewTlsConfiguration().WithRootCertificates(certData)
			advancedConfig = advancedConfig.WithTlsConfiguration(tlsConfig)
		}
	}

	clientConfig = clientConfig.WithAdvancedConfiguration(advancedConfig)

	return clientConfig
}

func (suite *GlideTestSuite) defaultClusterClient() *glide.ClusterClient {
	config := suite.defaultClusterClientConfig()
	client, err := suite.clusterClient(config)
	require.NoError(suite.T(), err)
	return client
}

func (suite *GlideTestSuite) clusterClient(config *config.ClusterClientConfiguration) (*glide.ClusterClient, error) {
	client, err := glide.NewClusterClient(config)
	if err != nil {
		return nil, err
	}
	if client == nil {
		return nil, fmt.Errorf("cluster client is nil")
	}

	suite.clusterClients = append(suite.clusterClients, client)
	return client, nil
}

func (suite *GlideTestSuite) createConnectionTimeoutClient(
	connectTimeout, requestTimeout time.Duration,
	backoffStrategy *config.BackoffStrategy,
) (*glide.Client, error) {
	clientConfig := suite.defaultClientConfig().
		WithRequestTimeout(requestTimeout).
		WithReconnectStrategy(backoffStrategy).
		WithAdvancedConfiguration(
			config.NewAdvancedClientConfiguration().WithConnectionTimeout(connectTimeout))
	return glide.NewClient(clientConfig)
}

func (suite *GlideTestSuite) createConnectionTimeoutClusterClient(
	connectTimeout, requestTimeout time.Duration,
) (*glide.ClusterClient, error) {
	clientConfig := suite.defaultClusterClientConfig().
		WithAdvancedConfiguration(
			config.NewAdvancedClusterClientConfiguration().WithConnectionTimeout(connectTimeout)).
		WithRequestTimeout(requestTimeout)
	return glide.NewClusterClient(clientConfig)
}

func (suite *GlideTestSuite) runWithClients(
	clients []interfaces.BaseClientCommands,
	test func(client interfaces.BaseClientCommands),
) {
	for _, client := range clients {
		suite.T().Run(fmt.Sprintf("%T", client)[1:], func(t *testing.T) {
			test(client)
		})
	}
}

func (suite *GlideTestSuite) runParallelizedWithClients(
	clients []interfaces.BaseClientCommands,
	parallelism int,
	count int64,
	timeout time.Duration,
	test func(client interfaces.BaseClientCommands),
) {
	for _, client := range clients {
		suite.T().Run(fmt.Sprintf("%T", client)[1:], func(t *testing.T) {
			done := make(chan struct{}, parallelism)
			for i := 0; i < parallelism; i++ {
				go func() {
					defer func() { done <- struct{}{} }()
					for !suite.T().Failed() && atomic.AddInt64(&count, -1) > 0 {
						test(client)
					}
				}()
			}
			tm := time.NewTimer(timeout)
			defer tm.Stop()
			for i := 0; i < parallelism; i++ {
				select {
				case <-done:
				case <-tm.C:
					suite.T().Fatalf("parallelized test timeout")
				}
			}
		})
	}
}

func (suite *GlideTestSuite) verifyOK(result string, err error) {
	suite.NoError(err)
	assert.Equal(suite.T(), glide.OK, result)
}

func (suite *GlideTestSuite) SkipIfServerVersionLowerThan(version string, t *testing.T) {
	if suite.serverVersion < version {
		t.Skipf("This feature is added in version %s", version)
	}
}

func (suite *GlideTestSuite) GenerateLargeUuid() string {
	wantedLength := math.Pow(2, 16)
	id := uuid.New().String()
	for len(id) < int(wantedLength) {
		id += uuid.New().String()
	}
	return id
}

// --- PubSub Test Helpers ---
type ClientType int

const (
	StandaloneClient ClientType = iota
	ClusterClient
)

// Get the string representation of the client type
func (c *ClientType) String() string {
	return []string{"StandaloneClient", "ClusterClient"}[*c]
}

func (suite *GlideTestSuite) createAnyClient(clientType ClientType, subscription any) interfaces.BaseClientCommands {
	switch clientType {
	case StandaloneClient:
		if sub, ok := subscription.(*config.StandaloneSubscriptionConfig); ok {
			return suite.createStandaloneClientWithSubscriptions(sub)
		} else {
			return suite.defaultClient()
		}
	case ClusterClient:
		if sub, ok := subscription.(*config.ClusterSubscriptionConfig); ok {
			return suite.createClusterClientWithSubscriptions(sub)
		} else {
			return suite.defaultClusterClient()
		}
	default:
		assert.Fail(suite.T(), "Unsupported client type")
		return nil
	}
}

func (suite *GlideTestSuite) createAnyClientWithTesting(
	clientType ClientType,
	subscription any,
) (interfaces.BaseClientCommands, error) {
	switch clientType {
	case StandaloneClient:
		if sub, ok := subscription.(*config.StandaloneSubscriptionConfig); ok {
			clientConfig := suite.defaultClientConfig().WithSubscriptionConfig(sub)
			return suite.client(clientConfig)
		} else {
			return suite.client(suite.defaultClientConfig())
		}
	case ClusterClient:
		if sub, ok := subscription.(*config.ClusterSubscriptionConfig); ok {
			clientConfig := suite.defaultClusterClientConfig().WithSubscriptionConfig(sub)
			return suite.clusterClient(clientConfig)
		} else {
			return suite.clusterClient(suite.defaultClusterClientConfig())
		}
	default:
		return nil, fmt.Errorf("unsupported client type")
	}
}

func (suite *GlideTestSuite) createStandaloneClientWithSubscriptions(
	config *config.StandaloneSubscriptionConfig,
) *glide.Client {
	clientConfig := suite.defaultClientConfig().WithSubscriptionConfig(config)
	client, err := suite.client(clientConfig)
	if err != nil {
		suite.T().Fatalf("Failed to create standalone client with subscriptions: %v", err)
	}
	return client
}

func (suite *GlideTestSuite) createClusterClientWithSubscriptions(
	config *config.ClusterSubscriptionConfig,
) *glide.ClusterClient {
	clientConfig := suite.defaultClusterClientConfig().WithSubscriptionConfig(config)
	client, err := suite.clusterClient(clientConfig)
	if err != nil {
		suite.T().Fatalf("Failed to create cluster client with subscriptions: %v", err)
	}
	return client
}

type TestChannelMode int

const (
	ExactMode TestChannelMode = iota
	PatternMode
	ShardedMode
)

type ChannelDefn struct {
	Mode    TestChannelMode
	Channel string
}

var callbackCtx = sync.Map{}

const (
	MESSAGE_TIMEOUT          = 5   // second
	MESSAGE_PROCESSING_DELAY = 100 // ms
	ITERATION_DELAY          = 100 // ms
)

type MessageReadMethod int

const (
	CallbackMethod MessageReadMethod = iota
	WaitForMessageMethod
	SignalChannelMethod
	SyncLoopMethod
)

type SubscriptionMethod int

const (
	ConfigMethod SubscriptionMethod = iota
	LazyMethod
	BlockingMethod
)

// verifyPubsubMessages verifies that subscribers received the expected messages
// For single subscriber, pass a map with a single queue using any key (e.g., 1)
// For pattern subscriptions, the expectedMessages keys should be the pattern values
func (suite *GlideTestSuite) verifyPubsubMessages(
	t *testing.T,
	expectedMessages map[string]string,
	queues map[int]*glide.PubSubMessageQueue,
	messageReadMethod MessageReadMethod,
) {
	switch messageReadMethod {
	case CallbackMethod:
		receivedMessages := make(map[string]map[string]string)
		callbackCtx.Range(func(key, value any) bool {
			keyStr := key.(string)
			parts := strings.Split(keyStr, "-")
			clientId := parts[0]
			message := value.(*models.PubSubMessage)
			if _, exists := receivedMessages[clientId]; !exists {
				receivedMessages[clientId] = make(map[string]string)
			}
			// For pattern subscriptions, use the pattern value as the key
			// For channel subscriptions, use the channel name as the key
			messageKey := message.Channel
			if !message.Pattern.IsNil() {
				messageKey = message.Pattern.Value()
			}
			receivedMessages[clientId][messageKey] = message.Message
			return true
		})

		// Verify each subscriber received the expected messages
		for clientId, messages := range receivedMessages {
			if !assert.Equal(t, expectedMessages, messages, "Messages mismatch for client %s", clientId) {
				t.FailNow()
			}
		}

	case WaitForMessageMethod:
		for clientId, queue := range queues {
			receivedMessages := make(map[string]string)
			for expectedKey := range expectedMessages {
				select {
				case msg := <-queue.WaitForMessage():
					// For pattern subscriptions, use the pattern value as the key
					// For channel subscriptions, use the channel name as the key
					messageKey := msg.Channel
					if !msg.Pattern.IsNil() {
						messageKey = msg.Pattern.Value()
					}
					receivedMessages[messageKey] = msg.Message
				case <-time.After(MESSAGE_TIMEOUT * time.Second):
					assert.Fail(t, "Timed out waiting for message for key %s for client %d", expectedKey, clientId)
					t.FailNow()
				}
			}
			if !assert.Equal(t, expectedMessages, receivedMessages, "Messages mismatch for client %d", clientId) {
				t.FailNow()
			}
		}

	case SignalChannelMethod:
		for clientId, queue := range queues {
			receivedMessages := make(map[string]string)
			signalCh := make(chan struct{}, 1)
			queue.RegisterSignalChannel(signalCh)
			defer queue.UnregisterSignalChannel(signalCh)

			for len(receivedMessages) < len(expectedMessages) {
				select {
				case <-signalCh:
					// Process all available messages
					for msg := queue.Pop(); msg != nil; msg = queue.Pop() {
						// For pattern subscriptions, use the pattern value as the key
						// For channel subscriptions, use the channel name as the key
						messageKey := msg.Channel
						if !msg.Pattern.IsNil() {
							messageKey = msg.Pattern.Value()
						}
						receivedMessages[messageKey] = msg.Message
					}
				case <-time.After(MESSAGE_TIMEOUT * time.Second):
					assert.Fail(t, fmt.Sprintf("Timed out waiting for messages for client %d", clientId))
					suite.T().Logf("Received messages: %+v", receivedMessages)
					t.FailNow()
				default:
					time.Sleep(ITERATION_DELAY * time.Millisecond)
				}
			}
			if !assert.Equal(t, expectedMessages, receivedMessages, "Messages mismatch for client %d", clientId) {
				t.FailNow()
			}
		}

	case SyncLoopMethod:
		for clientId, queue := range queues {
			receivedMessages := make(map[string]string)
			for len(receivedMessages) < len(expectedMessages) {
				if msg := queue.Pop(); msg != nil {
					// For pattern subscriptions, use the pattern value as the key
					// For channel subscriptions, use the channel name as the key
					messageKey := msg.Channel
					if !msg.Pattern.IsNil() {
						messageKey = msg.Pattern.Value()
					}
					receivedMessages[messageKey] = msg.Message
				}

				select {
				case <-time.After(MESSAGE_TIMEOUT * time.Second):
					assert.Fail(t, "Timed out waiting for messages for client %d", clientId)
					t.FailNow()
				default:
					time.Sleep(ITERATION_DELAY * time.Millisecond)
				}
			}
			if !assert.Equal(t, expectedMessages, receivedMessages, "Messages mismatch for client %d", clientId) {
				t.FailNow()
			}
		}
	}
}

// CreatePubSubReceiver sets up a Pub/Sub receiver for the provided client.
// It supports both standalone and cluster client types and configures the
// subscription based on the provided parameters.
//
// Parameters:
//   - clientType: The type of client to create (GlideClient or GlideClusterClient)
//   - channels: A slice of ChannelDefn objects defining the channels to subscribe to
//   - clientId: A unique identifier for this subscriber
//   - withCallback: Whether to use callback-based message handling
//   - subscriptionMethod: How to subscribe (Config, Lazy, or Blocking)
//   - t: The testing.T instance for proper error handling in subtests
//
// Returns:
//   - The created client with the specified subscription configuration
func (suite *GlideTestSuite) CreatePubSubReceiver(
	clientType ClientType,
	channels []ChannelDefn,
	clientId int,
	withCallback bool,
	subscriptionMethod SubscriptionMethod,
	t *testing.T,
) interfaces.BaseClientCommands {
	callback := func(message *models.PubSubMessage, context any) {
		callbackCtx.Store(fmt.Sprintf("%d-%s", clientId, message.Channel), message)
	}

	switch clientType {
	case StandaloneClient:
		if len(channels) > 0 && channels[0].Mode == ShardedMode {
			t.Fatalf("Sharded mode is not supported for standalone client")
			return nil
		}

		var client *glide.Client
		var err error

		if subscriptionMethod == ConfigMethod {
			sConfig := config.NewStandaloneSubscriptionConfig()
			for _, channel := range channels {
				mode := config.PubSubChannelMode(channel.Mode)
				sConfig = sConfig.WithSubscription(mode, channel.Channel)
			}
			if withCallback {
				sConfig = sConfig.WithCallback(callback, &callbackCtx)
			}
			var baseClient interfaces.BaseClientCommands
			baseClient, err = suite.createAnyClientWithTesting(StandaloneClient, sConfig)
			require.NoError(t, err)
			client = baseClient.(*glide.Client)
		} else {
			// For Lazy/Blocking, create client with empty config
			sConfig := config.NewStandaloneSubscriptionConfig()
			if withCallback {
				sConfig = sConfig.WithCallback(callback, &callbackCtx)
			}
			var baseClient interfaces.BaseClientCommands
			baseClient, err = suite.createAnyClientWithTesting(StandaloneClient, sConfig)
			require.NoError(t, err)
			client = baseClient.(*glide.Client)

			// Subscribe dynamically
			suite.subscribeByMethod(client, nil, channels, subscriptionMethod, t)
		}
		return client

	case ClusterClient:
		var client *glide.ClusterClient
		var err error

		if subscriptionMethod == ConfigMethod {
			cConfig := config.NewClusterSubscriptionConfig()
			for _, channel := range channels {
				mode := config.PubSubClusterChannelMode(channel.Mode)
				cConfig = cConfig.WithSubscription(mode, channel.Channel)
			}
			if withCallback {
				cConfig = cConfig.WithCallback(callback, &callbackCtx)
			}
			var baseClient interfaces.BaseClientCommands
			baseClient, err = suite.createAnyClientWithTesting(ClusterClient, cConfig)
			require.NoError(t, err)
			client = baseClient.(*glide.ClusterClient)
		} else {
			// For Lazy/Blocking, create client with empty config
			cConfig := config.NewClusterSubscriptionConfig()
			if withCallback {
				cConfig = cConfig.WithCallback(callback, &callbackCtx)
			}
			var baseClient interfaces.BaseClientCommands
			baseClient, err = suite.createAnyClientWithTesting(ClusterClient, cConfig)
			require.NoError(t, err)
			client = baseClient.(*glide.ClusterClient)

			// Subscribe dynamically
			suite.subscribeByMethod(nil, client, channels, subscriptionMethod, t)
		}
		return client
	default:
		t.Fatalf("Unsupported client type")
		return nil
	}
}

func (suite *GlideTestSuite) subscribeByMethod(
	standaloneClient *glide.Client,
	clusterClient *glide.ClusterClient,
	channels []ChannelDefn,
	method SubscriptionMethod,
	t *testing.T,
) {
	if method == ConfigMethod {
		return // Already subscribed at creation
	}

	ctx := context.Background()
	timeoutMs := 5000

	// Group channels by mode
	exactChannels := []string{}
	patternChannels := []string{}
	shardedChannels := []string{}

	for _, ch := range channels {
		switch ch.Mode {
		case ExactMode:
			exactChannels = append(exactChannels, ch.Channel)
		case PatternMode:
			patternChannels = append(patternChannels, ch.Channel)
		case ShardedMode:
			shardedChannels = append(shardedChannels, ch.Channel)
		}
	}

	// Subscribe based on method
	if standaloneClient != nil {
		if len(exactChannels) > 0 {
			if method == LazyMethod {
				require.NoError(t, standaloneClient.SubscribeLazy(ctx, exactChannels))
			} else {
				require.NoError(t, standaloneClient.Subscribe(ctx, exactChannels, timeoutMs))
			}
		}
		if len(patternChannels) > 0 {
			if method == LazyMethod {
				require.NoError(t, standaloneClient.PSubscribeLazy(ctx, patternChannels))
			} else {
				require.NoError(t, standaloneClient.PSubscribe(ctx, patternChannels, timeoutMs))
			}
		}
	} else if clusterClient != nil {
		if len(exactChannels) > 0 {
			if method == LazyMethod {
				require.NoError(t, clusterClient.SubscribeLazy(ctx, exactChannels))
			} else {
				require.NoError(t, clusterClient.Subscribe(ctx, exactChannels, timeoutMs))
			}
		}
		if len(patternChannels) > 0 {
			if method == LazyMethod {
				require.NoError(t, clusterClient.PSubscribeLazy(ctx, patternChannels))
			} else {
				require.NoError(t, clusterClient.PSubscribe(ctx, patternChannels, timeoutMs))
			}
		}
		if len(shardedChannels) > 0 {
			if method == LazyMethod {
				require.NoError(t, clusterClient.SSubscribeLazy(ctx, shardedChannels))
			} else {
				require.NoError(t, clusterClient.SSubscribe(ctx, shardedChannels, timeoutMs))
			}
		}
	}

	// Wait for subscription to propagate
	time.Sleep(100 * time.Millisecond)
}

func getChannelMode(sharded bool) TestChannelMode {
	if sharded {
		return ShardedMode
	}
	return ExactMode
}

type PubSubQueuer interface {
	GetQueue() (*glide.PubSubMessageQueue, error)
}

// loadCaCertificateForTests loads the CA certificate for TLS tests.
// It looks for the certificate in the utils/tls_crts directory.
// Returns the certificate data or an error if not found.
func loadCaCertificateForTests() ([]byte, error) {
	glideHome := os.Getenv("GLIDE_HOME_DIR")
	if glideHome == "" {
		glideHome = "../.."
	}

	caCertPath := filepath.Join(glideHome, "utils", "tls_crts", "ca.crt")
	absPath, err := filepath.Abs(caCertPath)
	if err != nil {
		return nil, err
	}

	return config.LoadRootCertificatesFromFile(absPath)
}
