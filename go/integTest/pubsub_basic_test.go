// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
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
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()

			channels := []ChannelDefn{
				{Channel: "initial", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			// Empty slice should raise error
			var err error
			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)
				err = client.Subscribe(ctx, []string{}, 5000)
			} else {
				client := receiver.(*glide.ClusterClient)
				err = client.Subscribe(ctx, []string{}, 5000)
			}
			assert.Error(t, err)
		})
	}
}

func (suite *GlideTestSuite) TestUnsubscribeSpecificChannels() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel1 := "unsub_channel_1"
			channel2 := "unsub_channel_2"
			channel3 := "unsub_channel_3"

			channels := []ChannelDefn{
				{Channel: channel1, Mode: ExactMode},
				{Channel: channel2, Mode: ExactMode},
				{Channel: channel3, Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			time.Sleep(200 * time.Millisecond)

			// Get subscriptions and unsubscribe based on client type
			var state *models.PubSubState
			var err error

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				assert.Equal(t, 3, len(state.ActualSubscriptions[models.Exact]))

				// Unsubscribe from one (blocking - no sleep needed)
				err = client.Unsubscribe(ctx, []string{channel1}, 5000)
				assert.NoError(t, err)

				// Verify only 2 remain
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				assert.Equal(t, 3, len(state.ActualSubscriptions[models.Exact]))

				// Unsubscribe from one (blocking - no sleep needed)
				err = client.Unsubscribe(ctx, []string{channel1}, 5000)
				assert.NoError(t, err)

				// Verify only 2 remain
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			}

			_, exists := state.ActualSubscriptions[models.Exact][channel1]
			assert.False(t, exists)
			_, exists = state.ActualSubscriptions[models.Exact][channel2]
			assert.True(t, exists)
			_, exists = state.ActualSubscriptions[models.Exact][channel3]
			assert.True(t, exists)
		})
	}
}

func (suite *GlideTestSuite) TestPUnsubscribeSpecificPatterns() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			pattern1 := "pattern1_*"
			pattern2 := "pattern2_*"

			channels := []ChannelDefn{
				{Channel: pattern1, Mode: PatternMode},
				{Channel: pattern2, Mode: PatternMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			time.Sleep(200 * time.Millisecond)

			var state *models.PubSubState
			var err error

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Unsubscribe from one pattern (blocking - no sleep needed)
				err = client.PUnsubscribe(ctx, []string{pattern1}, 5000)
				assert.NoError(t, err)

				// Verify only pattern2 remains
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)

				// Unsubscribe from one pattern (blocking - no sleep needed)
				err = client.PUnsubscribe(ctx, []string{pattern1}, 5000)
				assert.NoError(t, err)

				// Verify only pattern2 remains
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			}

			_, exists := state.ActualSubscriptions[models.Pattern][pattern1]
			assert.False(t, exists)
			_, exists = state.ActualSubscriptions[models.Pattern][pattern2]
			assert.True(t, exists)
		})
	}
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

	// Verify only channel2 remains
	state, err := clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Sharded][channel1]
	assert.False(suite.T(), exists)
	_, exists = state.ActualSubscriptions[models.Sharded][channel2]
	assert.True(suite.T(), exists)
}

func (suite *GlideTestSuite) TestUnsubscribeAllTypes() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel := "test_channel"
			pattern := "test_pattern_*"

			channels := []ChannelDefn{
				{Channel: channel, Mode: ExactMode},
				{Channel: pattern, Mode: PatternMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			time.Sleep(200 * time.Millisecond)

			var state *models.PubSubState
			var err error

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Verify both types subscribed
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				assert.Greater(t, len(state.ActualSubscriptions[models.Exact]), 0)
				assert.Greater(t, len(state.ActualSubscriptions[models.Pattern]), 0)

				// Unsubscribe from all exact channels
				err = client.Unsubscribe(ctx, nil, 5000)
				assert.NoError(t, err)

				time.Sleep(200 * time.Millisecond)

				// Verify exact channels gone, patterns remain
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				assert.Equal(t, 0, len(state.ActualSubscriptions[models.Exact]))
				assert.Greater(t, len(state.ActualSubscriptions[models.Pattern]), 0)

				// Unsubscribe from all patterns
				err = client.PUnsubscribe(ctx, nil, 5000)
				assert.NoError(t, err)

				time.Sleep(200 * time.Millisecond)

				// Verify all gone
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)

				// Verify both types subscribed
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				assert.Greater(t, len(state.ActualSubscriptions[models.Exact]), 0)
				assert.Greater(t, len(state.ActualSubscriptions[models.Pattern]), 0)

				// Unsubscribe from all exact channels
				err = client.Unsubscribe(ctx, nil, 5000)
				assert.NoError(t, err)

				time.Sleep(200 * time.Millisecond)

				// Verify exact channels gone, patterns remain
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				assert.Equal(t, 0, len(state.ActualSubscriptions[models.Exact]))
				assert.Greater(t, len(state.ActualSubscriptions[models.Pattern]), 0)

				// Unsubscribe from all patterns
				err = client.PUnsubscribe(ctx, nil, 5000)
				assert.NoError(t, err)

				time.Sleep(200 * time.Millisecond)

				// Verify all gone
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			}

			assert.Equal(t, 0, len(state.ActualSubscriptions[models.Exact]))
			assert.Equal(t, 0, len(state.ActualSubscriptions[models.Pattern]))
		})
	}
}

func (suite *GlideTestSuite) TestMixedSubscriptionMethodsAllTypes() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()

			channels := []ChannelDefn{
				{Channel: "initial", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			var state *models.PubSubState
			var err error

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Mix lazy and blocking for exact channels
				err = client.SubscribeLazy(ctx, []string{"lazy_exact"})
				assert.NoError(t, err)

				err = client.Subscribe(ctx, []string{"blocking_exact"}, 5000)
				assert.NoError(t, err)

				// Mix lazy and blocking for patterns
				err = client.PSubscribeLazy(ctx, []string{"lazy_pattern_*"})
				assert.NoError(t, err)

				err = client.PSubscribe(ctx, []string{"blocking_pattern_*"}, 5000)
				assert.NoError(t, err)

				time.Sleep(300 * time.Millisecond)

				// Verify all subscriptions exist
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)

				// Mix lazy and blocking for exact channels
				err = client.SubscribeLazy(ctx, []string{"lazy_exact"})
				assert.NoError(t, err)

				err = client.Subscribe(ctx, []string{"blocking_exact"}, 5000)
				assert.NoError(t, err)

				// Mix lazy and blocking for patterns
				err = client.PSubscribeLazy(ctx, []string{"lazy_pattern_*"})
				assert.NoError(t, err)

				err = client.PSubscribe(ctx, []string{"blocking_pattern_*"}, 5000)
				assert.NoError(t, err)

				time.Sleep(300 * time.Millisecond)

				// Verify all subscriptions exist
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			}

			_, exists := state.ActualSubscriptions[models.Exact]["lazy_exact"]
			assert.True(t, exists)
			_, exists = state.ActualSubscriptions[models.Exact]["blocking_exact"]
			assert.True(t, exists)
			_, exists = state.ActualSubscriptions[models.Pattern]["lazy_pattern_*"]
			assert.True(t, exists)
			_, exists = state.ActualSubscriptions[models.Pattern]["blocking_pattern_*"]
			assert.True(t, exists)
		})
	}
}

func (suite *GlideTestSuite) TestCustomCommandWithPubSub() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel := "custom_cmd_channel"
			message := "custom_cmd_message"

			// Create client without config-based subscriptions - we'll subscribe using custom_command
			receiver := suite.CreatePubSubReceiver(clientType, nil, 10, false, ConfigMethod, t)
			defer receiver.Close()

			// Create sender using createAnyClient
			sender := suite.createAnyClient(clientType, nil)
			defer sender.Close()

			// Get the queue for reading messages
			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.NoError(t, err)

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Subscribe using custom_command (lazy subscribe)
				result, err := client.CustomCommand(ctx, []string{"SUBSCRIBE", channel})
				assert.NoError(t, err)
				assert.Nil(t, result) // SUBSCRIBE returns nil

				// Wait for subscription to be established
				time.Sleep(500 * time.Millisecond)

				// Verify subscription is established
				state, err := client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, exists := state.ActualSubscriptions[models.Exact][channel]
				assert.True(t, exists, "Channel should be subscribed")

				// Publish a message
				err = suite.PublishMessage(sender, clientType, channel, message, false)
				assert.NoError(t, err)

				// Read the message from the queue
				select {
				case msg := <-queue.WaitForMessage():
					assert.Equal(t, message, msg.Message)
					assert.Equal(t, channel, msg.Channel)
				case <-time.After(3 * time.Second):
					t.Fatal("Timeout waiting for message")
				}

				// Unsubscribe using custom_command
				result, err = client.CustomCommand(ctx, []string{"UNSUBSCRIBE", channel})
				assert.NoError(t, err)
				assert.Nil(t, result) // UNSUBSCRIBE returns nil

				// Wait for unsubscription
				time.Sleep(500 * time.Millisecond)

				// Verify unsubscription
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, exists = state.ActualSubscriptions[models.Exact][channel]
				assert.False(t, exists, "Channel should be unsubscribed")
			} else {
				client := receiver.(*glide.ClusterClient)

				// Subscribe using custom_command (lazy subscribe)
				result, err := client.CustomCommand(ctx, []string{"SUBSCRIBE", channel})
				assert.NoError(t, err)
				assert.Nil(t, result.SingleValue()) // SUBSCRIBE returns nil

				// Wait for subscription to be established
				time.Sleep(500 * time.Millisecond)

				// Verify subscription is established
				state, err := client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, exists := state.ActualSubscriptions[models.Exact][channel]
				assert.True(t, exists, "Channel should be subscribed")

				// Publish a message
				err = suite.PublishMessage(sender, clientType, channel, message, false)
				assert.NoError(t, err)

				// Read the message from the queue
				select {
				case msg := <-queue.WaitForMessage():
					assert.Equal(t, message, msg.Message)
					assert.Equal(t, channel, msg.Channel)
				case <-time.After(3 * time.Second):
					t.Fatal("Timeout waiting for message")
				}

				// Unsubscribe using custom_command
				result, err = client.CustomCommand(ctx, []string{"UNSUBSCRIBE", channel})
				assert.NoError(t, err)
				assert.Nil(t, result.SingleValue()) // UNSUBSCRIBE returns nil

				// Wait for unsubscription
				time.Sleep(500 * time.Millisecond)

				// Verify unsubscription
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, exists = state.ActualSubscriptions[models.Exact][channel]
				assert.False(t, exists, "Channel should be unsubscribed")
			}
		})
	}
}

func (suite *GlideTestSuite) TestLazyVsBlockingBehavior() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()

			channels := []ChannelDefn{
				{Channel: "initial", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			var state *models.PubSubState
			var err error
			var lazyDuration, blockingDuration time.Duration

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Lazy subscribe returns immediately, subscription may not be active yet
				start := time.Now()
				err = client.SubscribeLazy(ctx, []string{"lazy_channel"})
				lazyDuration = time.Since(start)
				assert.NoError(t, err)
				assert.Less(t, lazyDuration, 50*time.Millisecond, "Lazy should return immediately")

				// Check subscription state - may not be in actual yet
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, inDesired := state.DesiredSubscriptions[models.Exact]["lazy_channel"]
				assert.True(t, inDesired, "Should be in desired immediately")

				// Blocking subscribe waits for confirmation
				start = time.Now()
				err = client.Subscribe(ctx, []string{"blocking_channel"}, 5000)
				blockingDuration = time.Since(start)
				assert.NoError(t, err)
				t.Logf("Blocking took %v", blockingDuration)

				// Check subscription state - should be in actual
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)

				// Lazy subscribe returns immediately, subscription may not be active yet
				start := time.Now()
				err = client.SubscribeLazy(ctx, []string{"lazy_channel"})
				lazyDuration = time.Since(start)
				assert.NoError(t, err)
				assert.Less(t, lazyDuration, 50*time.Millisecond, "Lazy should return immediately")

				// Check subscription state - may not be in actual yet
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, inDesired := state.DesiredSubscriptions[models.Exact]["lazy_channel"]
				assert.True(t, inDesired, "Should be in desired immediately")

				// Blocking subscribe waits for confirmation
				start = time.Now()
				err = client.Subscribe(ctx, []string{"blocking_channel"}, 5000)
				blockingDuration = time.Since(start)
				assert.NoError(t, err)
				t.Logf("Blocking took %v", blockingDuration)

				// Check subscription state - should be in actual
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			}

			_, inActual := state.ActualSubscriptions[models.Exact]["blocking_channel"]
			assert.True(t, inActual, "Should be in actual after blocking returns")
		})
	}
}

func (suite *GlideTestSuite) TestGetSubscriptionsDesiredVsActual() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel := "test_desired_actual"

			channels := []ChannelDefn{
				{Channel: "initial", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			var state *models.PubSubState
			var err error

			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Use lazy subscribe
				err = client.SubscribeLazy(ctx, []string{channel})
				assert.NoError(t, err)

				// Immediately check - should be in desired, may not be in actual yet
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, inDesired := state.DesiredSubscriptions[models.Exact][channel]
				assert.True(t, inDesired, "Should be in desired immediately")

				// Wait for reconciliation
				time.Sleep(500 * time.Millisecond)

				// Now should be in actual too
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)

				// Use lazy subscribe
				err = client.SubscribeLazy(ctx, []string{channel})
				assert.NoError(t, err)

				// Immediately check - should be in desired, may not be in actual yet
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
				_, inDesired := state.DesiredSubscriptions[models.Exact][channel]
				assert.True(t, inDesired, "Should be in desired immediately")

				// Wait for reconciliation
				time.Sleep(500 * time.Millisecond)

				// Now should be in actual too
				state, err = client.GetSubscriptions(ctx)
				assert.NoError(t, err)
			}

			_, inActual := state.ActualSubscriptions[models.Exact][channel]
			assert.True(t, inActual, "Should be in actual after reconciliation")
		})
	}
}

func (suite *GlideTestSuite) TestTwoPublishingClientsSameName() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel := "same_name_channel"

			// Create subscriber
			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			time.Sleep(200 * time.Millisecond)

			// Create two publishers and publish based on client type
			if clientType == StandaloneClient {
				pub1 := suite.defaultClient()
				defer pub1.Close()
				pub2 := suite.defaultClient()
				defer pub2.Close()

				// Both publish to same channel
				_, err := pub1.Publish(ctx, channel, "message_from_pub1")
				assert.NoError(t, err)
				_, err = pub2.Publish(ctx, channel, "message_from_pub2")
				assert.NoError(t, err)
			} else {
				pub1 := suite.defaultClusterClient()
				defer pub1.Close()
				pub2 := suite.defaultClusterClient()
				defer pub2.Close()

				// Both publish to same channel
				_, err := pub1.Publish(ctx, channel, "message_from_pub1", false)
				assert.NoError(t, err)
				_, err = pub2.Publish(ctx, channel, "message_from_pub2", false)
				assert.NoError(t, err)
			}

			time.Sleep(200 * time.Millisecond)

			// Verify both messages received
			var queue *glide.PubSubMessageQueue
			var err error
			if clientType == StandaloneClient {
				queue, err = receiver.(*glide.Client).GetQueue()
			} else {
				queue, err = receiver.(*glide.ClusterClient).GetQueue()
			}
			assert.NoError(t, err)

			messages := make(map[string]bool)
			for i := 0; i < 2; i++ {
				select {
				case msg := <-queue.WaitForMessage():
					messages[msg.Message] = true
				case <-time.After(2 * time.Second):
					t.Fatalf("Failed to receive message %d/2", i+1)
				}
			}

			assert.True(t, messages["message_from_pub1"])
			assert.True(t, messages["message_from_pub2"])
		})
	}
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
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channelName := "same_name"

			// Subscribe to both exact channel and pattern that matches it
			channels := []ChannelDefn{
				{Channel: channelName, Mode: ExactMode},
				{Channel: "same_*", Mode: PatternMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			time.Sleep(200 * time.Millisecond)

			// Create publisher and publish based on client type
			if clientType == StandaloneClient {
				publisher := suite.defaultClient()
				defer publisher.Close()

				// Publish once - should be received twice (exact + pattern match)
				_, err := publisher.Publish(ctx, channelName, "test_message")
				assert.NoError(t, err)
			} else {
				publisher := suite.defaultClusterClient()
				defer publisher.Close()

				// Publish once - should be received twice (exact + pattern match)
				_, err := publisher.Publish(ctx, channelName, "test_message", false)
				assert.NoError(t, err)
			}

			time.Sleep(200 * time.Millisecond)

			var queue *glide.PubSubMessageQueue
			var err error
			if clientType == StandaloneClient {
				queue, err = receiver.(*glide.Client).GetQueue()
			} else {
				queue, err = receiver.(*glide.ClusterClient).GetQueue()
			}
			assert.NoError(t, err)

			// Should receive 2 messages (one for exact, one for pattern)
			receivedCount := 0
			timeout := time.After(2 * time.Second)
			for i := 0; i < 2; i++ {
				select {
				case msg := <-queue.WaitForMessage():
					assert.Equal(t, "test_message", msg.Message)
					receivedCount++
				case <-timeout:
					goto done
				}
			}
		done:

			assert.Equal(t, 2, receivedCount, "Should receive message twice (exact + pattern)")
		})
	}
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
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			// Create callback-only client
			channel := "callback_only_channel"
			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}

			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, true, ConfigMethod, t)
			defer receiver.Close()

			// Try to get queue - should fail for callback-only client
			var err error
			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)
				_, err = client.GetQueue()
			} else {
				client := receiver.(*glide.ClusterClient)
				_, err = client.GetQueue()
			}
			assert.Error(t, err, "GetQueue should fail for callback-only client")
		})
	}
}

func (suite *GlideTestSuite) TestReconciliationIntervalSupport() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			intervalMs := 1000
			pollIntervalMs := 100
			timeoutSec := 5.0

			var getStats func() map[string]uint64
			var closeClient func()

			if clientType == StandaloneClient {
				// Create standalone client with configured reconciliation interval
				sConfig := config.NewStandaloneSubscriptionConfig()
				advancedConfig := config.NewAdvancedClientConfiguration().
					WithPubSubReconciliationIntervalMs(intervalMs)
				clientConfig := suite.defaultClientConfig().
					WithSubscriptionConfig(sConfig).
					WithAdvancedConfiguration(advancedConfig)

				client, err := suite.client(clientConfig)
				require.NoError(t, err)
				closeClient = func() { client.Close() }
				getStats = func() map[string]uint64 { return client.GetStatistics() }
			} else {
				// Create cluster client with configured reconciliation interval
				cConfig := config.NewClusterSubscriptionConfig()
				advancedConfig := config.NewAdvancedClusterClientConfiguration().
					WithPubSubReconciliationIntervalMs(intervalMs)
				clientConfig := suite.defaultClusterClientConfig().
					WithSubscriptionConfig(cConfig).
					WithAdvancedConfiguration(advancedConfig)

				client, err := suite.clusterClient(clientConfig)
				require.NoError(t, err)
				closeClient = func() { client.Close() }
				getStats = func() map[string]uint64 { return client.GetStatistics() }
			}
			defer closeClient()

			pollForTimestampChange := func(previousTs int64) (int64, error) {
				start := time.Now()
				for time.Since(start).Seconds() < timeoutSec {
					stats := getStats()
					currentTs, ok := stats["subscription_last_sync_timestamp"]
					if !ok {
						time.Sleep(time.Duration(pollIntervalMs) * time.Millisecond)
						continue
					}
					if int64(currentTs) != previousTs {
						return int64(currentTs), nil
					}
					time.Sleep(time.Duration(pollIntervalMs) * time.Millisecond)
				}
				return 0, fmt.Errorf("sync timestamp did not change within %.1fs. Previous: %d", timeoutSec, previousTs)
			}

			// Get initial timestamp (may be 0 if sync hasn't happened yet)
			initialStats := getStats()
			initialTs := int64(initialStats["subscription_last_sync_timestamp"])

			// Wait for first sync event (if initialTs is 0, this waits for first sync)
			firstSyncTs, err := pollForTimestampChange(initialTs)
			require.NoError(t, err)

			// Wait for second sync event
			secondSyncTs, err := pollForTimestampChange(firstSyncTs)
			require.NoError(t, err)

			// Compute actual interval
			actualIntervalMs := secondSyncTs - firstSyncTs

			// Assert interval is positive and at most 2x the configured interval
			// Note: Reconciliation can be triggered immediately by subscription changes,
			// so we only enforce an upper bound based on the timer interval
			maxInterval := int64(intervalMs) * 2 // Maximum 2x the configured interval
			assert.Greater(t, actualIntervalMs, int64(0),
				"Reconciliation interval (%dms) should be positive", actualIntervalMs)
			assert.LessOrEqual(t, actualIntervalMs, maxInterval,
				"Reconciliation interval (%dms) should be <= %dms", actualIntervalMs, maxInterval)
		})
	}
}
