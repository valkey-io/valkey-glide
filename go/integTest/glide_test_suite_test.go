// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"errors"
	"flag"
	"fmt"
	"log"
	"math"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

type GlideTestSuite struct {
	suite.Suite
	standaloneHosts []api.NodeAddress
	clusterHosts    []api.NodeAddress
	tls             bool
	serverVersion   string
	clients         []api.GlideClientCommands
	clusterClients  []api.GlideClusterClientCommands
}

var (
	tls              = flag.Bool("tls", false, "one")
	clusterHosts     = flag.String("cluster-endpoints", "", "two")
	standaloneHosts  = flag.String("standalone-endpoints", "", "three")
	pubsubtest       = flag.Bool("pubsub", false, "Set to true to run pubsub tests")
	longTimeoutTests = flag.Bool("long-timeout-tests", false, "Set to true to run tests with longer timeouts")
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
}

func parseHosts(suite *GlideTestSuite, addresses string) []api.NodeAddress {
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

func extractAddresses(suite *GlideTestSuite, output string) []api.NodeAddress {
	for _, line := range strings.Split(output, "\n") {
		if !strings.HasPrefix(line, "CLUSTER_NODES=") {
			continue
		}

		addresses := strings.Split(line, "=")[1]
		return parseHosts(suite, addresses)
	}

	suite.T().Fatalf("Failed to parse port from cluster_manager.py output")
	return []api.NodeAddress{}
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
		clientConfig := api.NewGlideClientConfiguration().
			WithAddress(&suite.standaloneHosts[0]).
			WithUseTLS(suite.tls).
			WithRequestTimeout(5000)

		client, err := api.NewGlideClient(clientConfig)
		if err == nil && client != nil {
			defer client.Close()
			info, _ := client.InfoWithOptions(options.InfoOptions{Sections: []options.Section{options.Server}})
			return extractServerVersion(suite, info)
		}
	}
	if len(suite.clusterHosts) == 0 {
		if err != nil {
			suite.T().Fatalf("No cluster hosts configured, standalone failed with %s", err.Error())
		}
		suite.T().Fatal("No server hosts configured")
	}

	clientConfig := api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
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
	for _, client := range suite.clients {
		client.Close()
	}
	suite.clients = nil // Clear the slice

	for _, client := range suite.clusterClients {
		client.Close()
	}
	suite.clusterClients = nil // Clear the slice

	// Clear the callback context for the next test
	callbackCtx.Range(func(key, value any) bool {
		callbackCtx.Delete(key)
		return true
	})
}

func (suite *GlideTestSuite) runWithDefaultClients(test func(client api.BaseClient)) {
	clients := suite.getDefaultClients()
	suite.runWithClients(clients, test)
}

func (suite *GlideTestSuite) runWithTimeoutClients(test func(client api.BaseClient)) {
	clients := suite.getTimeoutClients()
	suite.runWithClients(clients, test)
}

func (suite *GlideTestSuite) runParallelizedWithDefaultClients(
	parallelism int,
	count int64,
	timeout time.Duration,
	test func(client api.BaseClient),
) {
	clients := suite.getDefaultClients()
	suite.runParallelizedWithClients(clients, parallelism, count, timeout, test)
}

func (suite *GlideTestSuite) getDefaultClients() []api.BaseClient {
	return []api.BaseClient{suite.defaultClient(), suite.defaultClusterClient()}
}

func (suite *GlideTestSuite) getTimeoutClients() []api.BaseClient {
	clients := []api.BaseClient{}
	clusterTimeoutClient, err := suite.createConnectionTimeoutClient(250, 20000, nil)
	if err != nil {
		suite.T().Fatalf("Failed to create cluster timeout client: %s", err.Error())
	}
	clients = append(clients, clusterTimeoutClient)

	standaloneTimeoutClient, err := suite.createConnectionTimeoutClusterClient(250, 20000)
	if err != nil {
		suite.T().Fatalf("Failed to create standalone timeout client: %s", err.Error())
	}
	clients = append(clients, standaloneTimeoutClient)

	return clients
}

func (suite *GlideTestSuite) defaultClientConfig() *api.GlideClientConfiguration {
	return api.NewGlideClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5000)
}

func (suite *GlideTestSuite) defaultClient() api.GlideClientCommands {
	config := suite.defaultClientConfig()
	return suite.client(config)
}

func (suite *GlideTestSuite) client(config *api.GlideClientConfiguration) api.GlideClientCommands {
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	suite.clients = append(suite.clients, client)
	return client
}

func (suite *GlideTestSuite) defaultClusterClientConfig() *api.GlideClusterClientConfiguration {
	return api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5000)
}

func (suite *GlideTestSuite) defaultClusterClient() api.GlideClusterClientCommands {
	config := suite.defaultClusterClientConfig()
	return suite.clusterClient(config)
}

func (suite *GlideTestSuite) clusterClient(config *api.GlideClusterClientConfiguration) api.GlideClusterClientCommands {
	client, err := api.NewGlideClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	suite.clusterClients = append(suite.clusterClients, client)
	return client
}

func (suite *GlideTestSuite) createConnectionTimeoutClient(
	connectTimeout, requestTimeout int,
	backoffStrategy *api.BackoffStrategy,
) (api.GlideClientCommands, error) {
	clientConfig := suite.defaultClientConfig().
		WithRequestTimeout(requestTimeout).
		WithReconnectStrategy(backoffStrategy).
		WithAdvancedConfiguration(
			api.NewAdvancedGlideClientConfiguration().WithConnectionTimeout(connectTimeout))
	return api.NewGlideClient(clientConfig)
}

func (suite *GlideTestSuite) createConnectionTimeoutClusterClient(
	connectTimeout, requestTimeout int,
) (api.GlideClusterClientCommands, error) {
	clientConfig := suite.defaultClusterClientConfig().
		WithAdvancedConfiguration(
			api.NewAdvancedGlideClusterClientConfiguration().WithConnectionTimeout(connectTimeout)).
		WithRequestTimeout(requestTimeout)
	return api.NewGlideClusterClient(clientConfig)
}

func (suite *GlideTestSuite) runWithClients(clients []api.BaseClient, test func(client api.BaseClient)) {
	for _, client := range clients {
		suite.T().Run(fmt.Sprintf("%T", client)[5:], func(t *testing.T) {
			test(client)
		})
	}
}

func (suite *GlideTestSuite) runParallelizedWithClients(
	clients []api.BaseClient,
	parallelism int,
	count int64,
	timeout time.Duration,
	test func(client api.BaseClient),
) {
	for _, client := range clients {
		suite.T().Run(fmt.Sprintf("%T", client)[5:], func(t *testing.T) {
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
					suite.T().Fatalf("parallelized test timeout in %s", timeout)
				}
			}
		})
	}
}

func (suite *GlideTestSuite) verifyOK(result string, err error) {
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), api.OK, result)
}

func (suite *GlideTestSuite) SkipIfServerVersionLowerThanBy(version string, t *testing.T) {
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
	GlideClient ClientType = iota
	GlideClusterClient
)

// Get the string representation of the client type
func (c *ClientType) String() string {
	return []string{"GlideClient", "GlideClusterClient"}[*c]
}

func (suite *GlideTestSuite) createAnyClient(clientType ClientType, subscription any) api.BaseClient {
	switch clientType {
	case GlideClient:
		if sub, ok := subscription.(*api.StandaloneSubscriptionConfig); ok {
			return suite.createStandaloneClientWithSubscriptions(sub)
		} else {
			return suite.defaultClient()
		}
	case GlideClusterClient:
		if sub, ok := subscription.(*api.ClusterSubscriptionConfig); ok {
			return suite.createClusterClientWithSubscriptions(sub)
		} else {
			return suite.defaultClusterClient()
		}
	default:
		assert.Fail(suite.T(), "Unsupported client type")
		return nil
	}
}

func (suite *GlideTestSuite) createStandaloneClientWithSubscriptions(
	config *api.StandaloneSubscriptionConfig,
) api.GlideClientCommands {
	clientConfig := suite.defaultClientConfig().WithSubscriptionConfig(config)
	return suite.client(clientConfig)
}

func (suite *GlideTestSuite) createClusterClientWithSubscriptions(
	config *api.ClusterSubscriptionConfig,
) api.GlideClusterClientCommands {
	clientConfig := suite.defaultClusterClientConfig().WithSubscriptionConfig(config)
	return suite.clusterClient(clientConfig)
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

// verifyPubsubMessages verifies that subscribers received the expected messages
// For single subscriber, pass a map with a single queue using any key (e.g., 1)
// For pattern subscriptions, the expectedMessages keys should be the pattern values
func (suite *GlideTestSuite) verifyPubsubMessages(
	t *testing.T,
	expectedMessages map[string]string,
	queues map[int]*api.PubSubMessageQueue,
	messageReadMethod MessageReadMethod,
) {
	switch messageReadMethod {
	case CallbackMethod:
		receivedMessages := make(map[string]map[string]string)
		callbackCtx.Range(func(key, value any) bool {
			keyStr := key.(string)
			parts := strings.Split(keyStr, "-")
			clientId := parts[0]
			message := value.(*api.PubSubMessage)
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
//
// Returns:
//   - The created client with the specified subscription configuration
func (suite *GlideTestSuite) CreatePubSubReceiver(
	clientType ClientType,
	channels []ChannelDefn,
	clientId int,
	withCallback bool,
) api.BaseClient {
	callback := func(message *api.PubSubMessage, context any) {
		callbackCtx.Store(fmt.Sprintf("%d-%s", clientId, message.Channel), message)
	}
	switch clientType {
	case GlideClient:
		if channels[0].Mode == ShardedMode {
			assert.Fail(suite.T(), "Sharded mode is not supported for standalone client")
			return nil
		}

		sConfig := api.NewStandaloneSubscriptionConfig()
		for _, channel := range channels {
			mode := api.PubSubChannelMode(channel.Mode)
			sConfig = sConfig.WithSubscription(mode, channel.Channel)
		}
		if withCallback {
			sConfig = sConfig.WithCallback(callback, &callbackCtx)
		}
		return suite.createAnyClient(GlideClient, sConfig)
	case GlideClusterClient:
		cConfig := api.NewClusterSubscriptionConfig()
		for _, channel := range channels {
			mode := api.PubSubClusterChannelMode(channel.Mode)
			cConfig = cConfig.WithSubscription(mode, channel.Channel)
		}
		if withCallback {
			cConfig = cConfig.WithCallback(callback, &callbackCtx)
		}
		return suite.createAnyClient(GlideClusterClient, cConfig)
	default:
		assert.Fail(suite.T(), "Unsupported client type")
		return nil
	}
}

func getChannelMode(sharded bool) TestChannelMode {
	if sharded {
		return ShardedMode
	}
	return ExactMode
}
