// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestSubscribeEmptySetRaisesError tests that subscribing to empty set raises error
func (suite *GlideTestSuite) TestSubscribeEmptySetRaisesError() {
	ctx := context.Background()

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Empty slice should raise error
	err := client.Subscribe(ctx, []string{}, 5000)
	assert.Error(suite.T(), err)
}

// TestUnsubscribeSpecificChannels tests unsubscribing from specific channels
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
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestPUnsubscribeSpecificPatterns tests unsubscribing from specific patterns
func (suite *GlideTestSuite) TestPUnsubscribeSpecificPatterns() {
	ctx := context.Background()
	pattern1 := "pattern1_*"
	pattern2 := "pattern2_*"

	channels := []ChannelDefn{
		{Channel: pattern1, Mode: PatternMode},
		{Channel: pattern2, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestSUnsubscribeSpecificShardedChannels tests unsubscribing from specific sharded channels
func (suite *GlideTestSuite) TestSUnsubscribeSpecificShardedChannels() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()
	channel1 := "sharded1"
	channel2 := "sharded2"

	channels := []ChannelDefn{
		{Channel: channel1, Mode: ShardedMode},
		{Channel: channel2, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, suite.T())
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

// TestUnsubscribeAllTypes tests unsubscribing from all subscription types
func (suite *GlideTestSuite) TestUnsubscribeAllTypes() {
	ctx := context.Background()
	channel := "test_channel"
	pattern := "test_pattern_*"

	channels := []ChannelDefn{
		{Channel: channel, Mode: ExactMode},
		{Channel: pattern, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestMixedSubscriptionMethodsAllTypes tests using lazy and blocking for all types
func (suite *GlideTestSuite) TestMixedSubscriptionMethodsAllTypes() {
	ctx := context.Background()

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestCustomCommandWithPubSub tests using custom commands with pubsub client
func (suite *GlideTestSuite) TestCustomCommandWithPubSub() {
	ctx := context.Background()
	channel := "custom_cmd_channel"

	channels := []ChannelDefn{
		{Channel: channel, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestLazyVsBlockingBehavior tests the actual behavioral difference
func (suite *GlideTestSuite) TestLazyVsBlockingBehavior() {
	ctx := context.Background()

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestGetSubscriptionsDesiredVsActual tests the difference between desired and actual
func (suite *GlideTestSuite) TestGetSubscriptionsDesiredVsActual() {
	ctx := context.Background()
	channel := "test_desired_actual"

	channels := []ChannelDefn{
		{Channel: "initial", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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
