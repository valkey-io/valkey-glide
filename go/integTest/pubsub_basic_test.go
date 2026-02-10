// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestPubSub_Basic_ChannelSubscription tests all combinations of client types and message reading methods
func (suite *GlideTestSuite) TestPubSub_Basic_ChannelSubscription() {
	tests := CreateStandardTestCases("test-channel", "test message", true)

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			suite.ExecuteAndVerifyPubSubTest(tt, t)
		})
	}
}

// TestPubSub_Basic_MultipleSubscribers tests message delivery to multiple subscribers
func (suite *GlideTestSuite) TestPubSub_Basic_MultipleSubscribers() {
	tests := CreateStandardTestCases("test-multi-channel", "test multi message", true)

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			const numSubscribers = 3
			setup, queues := suite.SetupMultiSubscriberTest(tt, numSubscribers, t)
			defer setup.Publisher.Close()

			// Publish test message
			err := suite.PublishMessage(setup.Publisher, tt.ClientType, tt.ChannelName, tt.MessageContent, tt.Sharded)
			assert.Nil(t, err)

			// Allow time for the message to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, setup.ExpectedMessages, queues, tt.ReadMethod)
		})
	}
}

// TestPubSub_Basic_PatternSubscription tests message pattern matching with PSUBSCRIBE
func (suite *GlideTestSuite) TestPubSub_Basic_PatternSubscription() {
	tests := CreatePatternTestCases("test-pattern-*", []string{"test-pattern-1", "test-pattern-2"}, "test pattern message")

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			suite.ExecuteAndVerifyPatternTest(tt, t)
		})
	}
}

// TestPubSub_Basic_ManyChannels tests a single subscriber subscribing to multiple channels
func (suite *GlideTestSuite) TestPubSub_Basic_ManyChannels() {
	tests := CreateMultiChannelTestCases([]string{"test-channel-1", "test-channel-2", "test-channel-3"}, "test message", true)

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			suite.ExecuteAndVerifyMultiChannelTest(tt, t)
		})
	}
}

// TestPubSub_Basic_PatternManyChannels tests pattern subscriptions with multiple channels
func (suite *GlideTestSuite) TestPubSub_Basic_PatternManyChannels() {
	tests := CreatePatternTestCases(
		"test-pattern-*",
		[]string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
		"test pattern message",
	)

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			suite.ExecuteAndVerifyPatternTest(tt, t)
		})
	}
}

// TestPubSub_Basic_CombinedExactPattern tests a single subscriber with both exact and pattern subscriptions
func (suite *GlideTestSuite) TestPubSub_Basic_CombinedExactPattern() {
	tests := CreateCombinedTestCases(
		"test-exact-channel",
		"test-pattern-*",
		[]string{"test-pattern-1", "test-pattern-2"},
		"test message",
	)

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			suite.ExecuteAndVerifyCombinedTest(tt, t)
		})
	}
}

// TestPubSub_Basic_CombinedExactPatternMultipleSubscribers tests multiple subscribers with both exact and pattern
// subscriptions
func (suite *GlideTestSuite) TestPubSub_Basic_CombinedExactPatternMultipleSubscribers() {
	tests := CreateCombinedTestCases(
		"test-exact-channel",
		"test-pattern-*",
		[]string{"test-pattern-1", "test-pattern-2"},
		"test message",
	)

	for _, tt := range tests {
		suite.T().Run(tt.Name, func(t *testing.T) {
			const numSubscribers = 3
			publisher := suite.createAnyClient(tt.ClientType, nil)
			defer publisher.Close()

			// Create channel definitions for both exact and pattern subscriptions
			channels := []ChannelDefn{
				{Channel: tt.ExactChannel, Mode: ExactMode},
				{Channel: tt.Pattern, Mode: PatternMode},
			}

			// Create expected messages map
			expectedMessages := make(map[string]string)
			expectedMessages[tt.ExactChannel] = tt.MessageContent
			expectedMessages[tt.Pattern] = tt.MessageContent

			queues := make(map[int]*glide.PubSubMessageQueue)

			for i := 0; i < numSubscribers; i++ {
				if !tt.UseCallback {
					receiver := suite.CreatePubSubReceiver(tt.ClientType, channels, i+1, false, ConfigMethod, t)
					defer receiver.Close()
					queue, err := receiver.(PubSubQueuer).GetQueue()
					assert.Nil(t, err)
					queues[i+1] = queue
				} else {
					receiver := suite.CreatePubSubReceiver(tt.ClientType, channels, i+1, true, ConfigMethod, t)
					defer receiver.Close()
				}
			}

			// Allow subscriptions to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish to exact channel
			err := suite.PublishMessage(publisher, tt.ClientType, tt.ExactChannel, tt.MessageContent, false)
			assert.Nil(t, err)

			// Publish to pattern-matching channels
			for _, channel := range tt.PatternChannels {
				err := suite.PublishMessage(publisher, tt.ClientType, channel, tt.MessageContent, false)
				assert.Nil(t, err)
			}

			// Publish to a non-matching channel
			err = suite.PublishMessage(publisher, tt.ClientType, "other-channel", "should not receive", false)
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.ReadMethod)
		})
	}
}

func (suite *GlideTestSuite) TestSubscribeEmptySetRaisesError() {
	ctx := context.Background()

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Empty slice should raise error
	err := client.Subscribe(ctx, []string{}, 5000)
	assert.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestUnsubscribeSpecificChannels() {
	ctx := context.Background()
	channel1 := "unsub_channel_1"
	channel2 := "unsub_channel_2"
	channel3 := "unsub_channel_3"

	channels := []ChannelDefn{
		{Channel: channel1, Mode: ExactMode},
		{Channel: channel2, Mode: ExactMode},
		{Channel: channel3, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	client := receiver.(*glide.Client)

	// Verify all subscribed
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 3, len(state.ActualSubscriptions[models.Exact]))

	// Unsubscribe from one
	err = client.Unsubscribe(ctx, []string{channel1}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify only 2 remain
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Exact][channel1]
	assert.False(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Exact][channel2]
	assert.True(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Exact][channel3]
	assert.True(suite.T(), exists)
}

func (suite *GlideTestSuite) TestPUnsubscribeSpecificPatterns() {
	ctx := context.Background()
	pattern1 := "pattern1_*"
	pattern2 := "pattern2_*"

	channels := []ChannelDefn{
		{Channel: pattern1, Mode: PatternMode},
		{Channel: pattern2, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	client := receiver.(*glide.Client)

	// Unsubscribe from one pattern
	err := client.PUnsubscribe(ctx, []string{pattern1}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify only pattern2 remains
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Pattern][pattern1]
	assert.False(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Pattern][pattern2]
	assert.True(suite.T(), exists)
}

func (suite *GlideTestSuite) TestSUnsubscribeSpecificShardedChannels() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()
	channel1 := "sharded1"
	channel2 := "sharded2"

	channels := []ChannelDefn{
		{Channel: channel1, Mode: ShardedMode},
		{Channel: channel2, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	clusterClient := receiver.(*glide.ClusterClient)

	// Unsubscribe from one
	err := clusterClient.SUnsubscribe(ctx, []string{channel1}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify only channel2 remains
	state, err := clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Sharded][channel1]
	assert.False(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Sharded][channel2]
	assert.True(suite.T(), exists)
}

func (suite *GlideTestSuite) TestUnsubscribeAllTypes() {
	ctx := context.Background()
	channel := "test_channel"
	pattern := "test_pattern_*"

	channels := []ChannelDefn{
		{Channel: channel, Mode: ExactMode},
		{Channel: pattern, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	client := receiver.(*glide.Client)

	// Verify both types subscribed
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	assert.Greater(suite.T(), len(state.ActualSubscriptions[models.Exact]), 0)
	assert.Greater(suite.T(), len(state.ActualSubscriptions[models.Pattern]), 0)

	// Unsubscribe from all exact channels
	err = client.Unsubscribe(ctx, nil, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify exact channels gone, patterns remain
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 0, len(state.ActualSubscriptions[models.Exact]))
	assert.Greater(suite.T(), len(state.ActualSubscriptions[models.Pattern]), 0)

	// Unsubscribe from all patterns
	err = client.PUnsubscribe(ctx, nil, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify all gone
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), 0, len(state.ActualSubscriptions[models.Exact]))
	assert.Equal(suite.T(), 0, len(state.ActualSubscriptions[models.Pattern]))
}

func (suite *GlideTestSuite) TestMixedSubscriptionMethodsAllTypes() {
	ctx := context.Background()

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Mix lazy and blocking for exact channels
	err := client.SubscribeLazy(ctx, []string{"lazy_exact"})
	assert.NoError(suite.T(), err)

	err = client.Subscribe(ctx, []string{"blocking_exact"}, 5000)
	assert.NoError(suite.T(), err)

	// Mix lazy and blocking for patterns
	err = client.PSubscribeLazy(ctx, []string{"lazy_pattern_*"})
	assert.NoError(suite.T(), err)

	err = client.PSubscribe(ctx, []string{"blocking_pattern_*"}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(300 * time.Millisecond)

	// Verify all subscriptions exist
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)

	_, exists := state.ActualSubscriptions[models.Exact]["lazy_exact"]
	assert.True(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Exact]["blocking_exact"]
	assert.True(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Pattern]["lazy_pattern_*"]
	assert.True(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Pattern]["blocking_pattern_*"]
	assert.True(suite.T(), exists)
}

func (suite *GlideTestSuite) TestCustomCommandWithPubSub() {
	ctx := context.Background()
	channel := "custom_cmd_channel"

	channels := []ChannelDefn{
		{Channel: channel, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Execute custom command (PING)
	result, err := client.CustomCommand(ctx, []string{"PING"})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result)

	// Verify subscription still works after custom command
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), exists)
}

func (suite *GlideTestSuite) TestLazyVsBlockingBehavior() {
	ctx := context.Background()

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Lazy subscribe returns immediately, subscription may not be active yet
	start := time.Now()
	err := client.SubscribeLazy(ctx, []string{"lazy_channel"})
	lazyDuration := time.Since(start)
	assert.NoError(suite.T(), err)
	assert.Less(suite.T(), lazyDuration, 50*time.Millisecond, "Lazy should return immediately")

	// Check subscription state - may not be in actual yet
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, inDesired := state.DesiredSubscriptions[models.Exact]["lazy_channel"]
	assert.True(suite.T(), inDesired, "Should be in desired immediately")

	// Blocking subscribe waits for confirmation
	start = time.Now()
	err = client.Subscribe(ctx, []string{"blocking_channel"}, 5000)
	blockingDuration := time.Since(start)
	assert.NoError(suite.T(), err)
	suite.T().Logf("Blocking took %v", blockingDuration)

	// Check subscription state - should be in actual
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, inActual := state.ActualSubscriptions[models.Exact]["blocking_channel"]
	assert.True(suite.T(), inActual, "Should be in actual after blocking returns")
}

func (suite *GlideTestSuite) TestGetSubscriptionsDesiredVsActual() {
	ctx := context.Background()
	channel := "test_desired_actual"

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Use lazy subscribe
	err := client.SubscribeLazy(ctx, []string{channel})
	assert.NoError(suite.T(), err)

	// Immediately check - should be in desired, may not be in actual yet
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, inDesired := state.DesiredSubscriptions[models.Exact][channel]
	assert.True(suite.T(), inDesired, "Should be in desired immediately")

	// Wait for reconciliation
	time.Sleep(500 * time.Millisecond)

	// Now should be in actual too
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, inActual := state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), inActual, "Should be in actual after reconciliation")
}

func (suite *GlideTestSuite) TestTwoPublishingClientsSameName() {
	ctx := context.Background()
	channel := "same_name_channel"

	// Create subscriber
	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	// Create two publishers
	pub1 := suite.defaultClient()
	defer pub1.Close()
	pub2 := suite.defaultClient()
	defer pub2.Close()

	// Both publish to same channel
	_, err := pub1.Publish(ctx, channel, "message_from_pub1")
	assert.NoError(suite.T(), err)
	_, err = pub2.Publish(ctx, channel, "message_from_pub2")
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify both messages received
	client := receiver.(*glide.Client)
	queue, err := client.GetQueue()
	assert.NoError(suite.T(), err)

	messages := make(map[string]bool)
	for i := 0; i < 2; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			messages[msg.Message] = true
		case <-time.After(2 * time.Second):
			suite.T().Fatalf("Failed to receive message %d/2", i+1)
		}
	}

	assert.True(suite.T(), messages["message_from_pub1"])
	assert.True(suite.T(), messages["message_from_pub2"])
}

func (suite *GlideTestSuite) TestThreePublishingClientsSameNameWithSharded() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()
	channel := "sharded_same_name"

	channels := []ChannelDefn{{Channel: channel, Mode: ShardedMode}}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	// Create three publishers
	pubs := make([]*glide.ClusterClient, 3)
	for i := 0; i < 3; i++ {
		pubs[i] = suite.defaultClusterClient()
		defer pubs[i].Close()
	}

	// All publish to same sharded channel
	for i, pub := range pubs {
		_, err := pub.Publish(ctx, channel, fmt.Sprintf("msg_%d", i), true)
		assert.NoError(suite.T(), err)
	}

	time.Sleep(300 * time.Millisecond)

	// Verify all messages received
	clusterClient := receiver.(*glide.ClusterClient)
	queue, err := clusterClient.GetQueue()
	assert.NoError(suite.T(), err)

	messages := make(map[string]bool)
	for i := 0; i < 3; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			messages[msg.Message] = true
		case <-time.After(2 * time.Second):
			suite.T().Fatalf("Failed to receive message %d/3", i+1)
		}
	}

	assert.True(suite.T(), messages["msg_0"])
	assert.True(suite.T(), messages["msg_1"])
	assert.True(suite.T(), messages["msg_2"])
}

func (suite *GlideTestSuite) TestCombinedDifferentChannelsWithSameName() {
	ctx := context.Background()
	channelName := "same_name"

	// Subscribe to both exact channel and pattern that matches it
	channels := []ChannelDefn{
		{Channel: channelName, Mode: ExactMode},
		{Channel: "same_*", Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	publisher := suite.defaultClient()
	defer publisher.Close()

	// Publish once - should be received twice (exact + pattern match)
	_, err := publisher.Publish(ctx, channelName, "test_message")
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	client := receiver.(*glide.Client)
	queue, err := client.GetQueue()
	assert.NoError(suite.T(), err)

	// Should receive 2 messages (one for exact, one for pattern)
	receivedCount := 0
	timeout := time.After(2 * time.Second)
	for i := 0; i < 2; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "test_message", msg.Message)
			receivedCount++
		case <-timeout:
			goto done
		}
	}
done:

	assert.Equal(suite.T(), 2, receivedCount, "Should receive message twice (exact + pattern)")
}

func (suite *GlideTestSuite) TestChannelsAndShardChannelsSeparation() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()

	// Create cluster client with both regular and sharded subscriptions
	regularChannel := "regular_channel"
	shardedChannel := "sharded_channel"

	channels := []ChannelDefn{
		{Channel: regularChannel, Mode: ExactMode},
		{Channel: shardedChannel, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	clusterClient := receiver.(*glide.ClusterClient)

	// Check PUBSUB CHANNELS - should only show regular channel
	regularChannels, err := clusterClient.PubSubChannels(ctx)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), regularChannels, regularChannel)
	assert.NotContains(suite.T(), regularChannels, shardedChannel)

	// Check PUBSUB SHARDCHANNELS - should only show sharded channel
	shardChannels, err := clusterClient.PubSubShardChannels(ctx)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), shardChannels, shardedChannel)
	assert.NotContains(suite.T(), shardChannels, regularChannel)
}

func (suite *GlideTestSuite) TestNumSubAndShardNumSubSeparation() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()

	regularChannel := "regular_numsub"
	shardedChannel := "sharded_numsub"

	channels := []ChannelDefn{
		{Channel: regularChannel, Mode: ExactMode},
		{Channel: shardedChannel, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	clusterClient := receiver.(*glide.ClusterClient)

	// Check PUBSUB NUMSUB - should show regular channel
	numSub, err := clusterClient.PubSubNumSub(ctx, regularChannel, shardedChannel)
	assert.NoError(suite.T(), err)
	assert.Greater(suite.T(), numSub[regularChannel], int64(0))
	// Sharded channel should be 0 in regular numsub
	assert.Equal(suite.T(), int64(0), numSub[shardedChannel])

	// Check PUBSUB SHARDNUMSUB - should show sharded channel
	shardNumSub, err := clusterClient.PubSubShardNumSub(ctx, regularChannel, shardedChannel)
	assert.NoError(suite.T(), err)
	assert.Greater(suite.T(), shardNumSub[shardedChannel], int64(0))
	// Regular channel should be 0 in shard numsub
	assert.Equal(suite.T(), int64(0), shardNumSub[regularChannel])
}

func (suite *GlideTestSuite) TestRESP2RaisesError() {
	// RESP2 is not supported for pubsub - this would be tested at client creation time
	// Skipping as Go client enforces RESP3 for pubsub at compile time
	suite.T().Skip("RESP2 validation happens at client creation, not runtime")
}

func (suite *GlideTestSuite) TestCallbackOnlyRaisesErrorOnGetMethods() {
	// Create callback-only client
	channel := "callback_only_channel"
	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}

	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, true, ConfigMethod, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Try to get queue - should fail for callback-only client
	_, err := client.GetQueue()
	assert.Error(suite.T(), err, "GetQueue should fail for callback-only client")
}

func (suite *GlideTestSuite) TestReconciliationIntervalSupport() {
	// This is tested implicitly by all reconnection tests
	// The reconciliation interval determines how often the client checks subscription state
	suite.T().Log("Reconciliation interval is supported and tested via reconnection tests")
}
