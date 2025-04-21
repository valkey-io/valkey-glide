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
	tls             = flag.Bool("tls", false, "one")
	clusterHosts    = flag.String("cluster-endpoints", "", "two")
	standaloneHosts = flag.String("standalone-endpoints", "", "three")
	pubsub          = flag.Bool("pubsub", false, "Run PubSub tests if true, otherwise, skip the test")
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

	for _, client := range suite.clusterClients {
		client.Close()
	}
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

func (suite *GlideTestSuite) SkipIfServerVersionLowerThanBy(version string) {
	if suite.serverVersion < version {
		suite.T().Skipf("This feature is added in version %s", version)
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

func (suite *GlideTestSuite) getAllClientTypes() []ClientType {
	return []ClientType{GlideClient, GlideClusterClient}
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

func (suite *GlideTestSuite) runWithPubSubClients(test func(clientType ClientType)) {
	// if *pubsub != true {
	// 	suite.T().Skip("skipping pubsub tests")
	// }

	for _, clientType := range suite.getAllClientTypes() {
		suite.T().Run(fmt.Sprint(clientType.String()), func(t *testing.T) {
			test(clientType)
		})
	}
}

type TestChannelMode int

const (
	ExactMode TestChannelMode = iota
	PatternMode
)

type ChannelDefn struct {
	Mode    TestChannelMode
	Channel string
}

var callbackCtx = sync.Map{}

const MESSAGE_DELIVERY_DELAY = 500 // ms

type MessageReadMethod int

const (
	CallbackMethod MessageReadMethod = iota
	WaitForMessageMethod
	SignalChannelMethod
	SyncLoopMethod
)

// verifyReceivedPubsubMessages verifies that the received messages match the expected messages
// based on the message reading method used
func (suite *GlideTestSuite) verifyReceivedPubsubMessages(
	expectedMessages map[string]string,
	queue *api.PubSubMessageQueue,
	messageReadMethod MessageReadMethod,
) {
	switch messageReadMethod {
	case CallbackMethod:
		receivedMessages := convertSyncMapToMap(&callbackCtx)
		assert.Equal(suite.T(), expectedMessages, receivedMessages)

	case WaitForMessageMethod:
		// For WaitForMessage method, we need to wait for each message
		receivedMessages := make(map[string]string)
		for channel := range expectedMessages {
			select {
			case msg := <-queue.WaitForMessage():
				receivedMessages[msg.Channel] = msg.Message
			case <-time.After(5 * time.Second):
				assert.Fail(suite.T(), "Timed out waiting for message on channel: %s", channel)
			}
		}
		assert.Equal(suite.T(), expectedMessages, receivedMessages)

	case SignalChannelMethod:
		// For signal channel method, we need to wait for signals and pop messages
		receivedMessages := make(map[string]string)
		signalCh := make(chan struct{}, 1)
		queue.RegisterSignalChannel(signalCh)
		defer queue.UnregisterSignalChannel(signalCh)

		timeout := time.After(25 * time.Second)
		for len(receivedMessages) < len(expectedMessages) {
			select {
			case <-signalCh:
				if msg := queue.Pop(); msg != nil {
					receivedMessages[msg.Channel] = msg.Message
				}
			case <-timeout:
				assert.Fail(suite.T(), "Timed out waiting for messages")
				return
			default:
				time.Sleep(100 * time.Millisecond)
			}
		}
		assert.Equal(suite.T(), expectedMessages, receivedMessages)

	case SyncLoopMethod:
		// For sync loop method, we need to poll for messages
		receivedMessages := make(map[string]string)
		timeout := time.After(5 * time.Second)
		for len(receivedMessages) < len(expectedMessages) {
			if msg := queue.Pop(); msg != nil {
				receivedMessages[msg.Channel] = msg.Message
			}

			select {
			case <-timeout:
				assert.Fail(suite.T(), "Timed out waiting for messages")
				return
			default:
				time.Sleep(100 * time.Millisecond)
			}
		}
		assert.Equal(suite.T(), expectedMessages, receivedMessages)
	}
}

// convert sync.Map with PubSubMessage to map[string]string
func convertSyncMapToMap(syncMap *sync.Map) map[string]string {
	mapResult := make(map[string]string)
	syncMap.Range(func(key, value any) bool {
		mapResult[value.(*api.PubSubMessage).Channel] = value.(*api.PubSubMessage).Message
		return true
	})
	return mapResult
}

// CreatePubSubReceiver sets up a Pub/Sub receiver for the provided client.
// It supports both standalone and cluster client types and configures the
// subscription based on the provided parameters.
//
// Parameters:
//   - client: The Pub/Sub client implementing either api.GlideClientCommands
//     or api.GlideClusterClientCommands.
//   - channels: A slice of ChannelDefn objects, each containing:
//   - SMode: The subscription mode for standalone clients.
//   - CMode: The subscription mode for cluster clients.
//   - Channel: The name of the Pub/Sub channel to subscribe to.
//   - callback: A function to handle incoming Pub/Sub messages.
//   - ctx: A context object passed to the callback function.
//
// Behavior:
//   - For standalone clients (api.GlideClientCommands), it creates a standalone
//     subscription using the provided subscription modes, channels, callback, and ctx.
//   - For cluster clients (api.GlideClusterClientCommands), it creates a cluster
//     subscription using the provided subscription modes, channels, callback, and ctx.
//   - If the client type is unsupported, the function will fail the test with
//     an appropriate error message.
func (suite *GlideTestSuite) CreatePubSubReceiver(
	clientType ClientType,
	channels []ChannelDefn,
	clientId int,
	withCallback bool,
) api.BaseClient {
	callback := func(message *api.PubSubMessage, context any) {
		callbackCtx.Store(clientId, message)
	}
	switch clientType {
	case GlideClient:
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
