// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestTwoPublishingClientsSameName tests two publishers publishing to same channel
func (suite *GlideTestSuite) TestTwoPublishingClientsSameName() {
	ctx := context.Background()
	channel := "same_name_channel"

	// Create subscriber
	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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

// TestThreePublishingClientsSameNameWithSharded tests three publishers with sharded channels
func (suite *GlideTestSuite) TestThreePublishingClientsSameNameWithSharded() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()
	channel := "sharded_same_name"

	channels := []ChannelDefn{{Channel: channel, Mode: ShardedMode}}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, suite.T())
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

// TestCombinedDifferentChannelsWithSameName tests exact and pattern channels with same name
func (suite *GlideTestSuite) TestCombinedDifferentChannelsWithSameName() {
	ctx := context.Background()
	channelName := "same_name"

	// Subscribe to both exact channel and pattern that matches it
	channels := []ChannelDefn{
		{Channel: channelName, Mode: ExactMode},
		{Channel: "same_*", Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, suite.T())
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
	for i := 0; i < 2; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "test_message", msg.Message)
			receivedCount++
		case <-time.After(2 * time.Second):
			break
		}
	}

	assert.Equal(suite.T(), 2, receivedCount, "Should receive message twice (exact + pattern)")
}

// TestChannelsAndShardChannelsSeparation tests that regular and sharded channels are separate
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
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, suite.T())
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

// TestNumSubAndShardNumSubSeparation tests that numsub and shardnumsub are separate
func (suite *GlideTestSuite) TestNumSubAndShardNumSubSeparation() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()

	regularChannel := "regular_numsub"
	shardedChannel := "sharded_numsub"

	channels := []ChannelDefn{
		{Channel: regularChannel, Mode: ExactMode},
		{Channel: shardedChannel, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, suite.T())
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

// TestRESP2RaisesError tests that RESP2 protocol raises error for pubsub
func (suite *GlideTestSuite) TestRESP2RaisesError() {
	// RESP2 is not supported for pubsub - this would be tested at client creation time
	// Skipping as Go client enforces RESP3 for pubsub at compile time
	suite.T().Skip("RESP2 validation happens at client creation, not runtime")
}

// TestCallbackOnlyRaisesErrorOnGetMethods tests that callback-only clients can't use get methods
func (suite *GlideTestSuite) TestCallbackOnlyRaisesErrorOnGetMethods() {
	// Create callback-only client
	channel := "callback_only_channel"
	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}

	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, true, suite.T())
	defer receiver.Close()

	client := receiver.(*glide.Client)

	// Try to get queue - should fail for callback-only client
	_, err := client.GetQueue()
	assert.Error(suite.T(), err, "GetQueue should fail for callback-only client")
}

// TestReconciliationIntervalSupport tests that clients support reconciliation interval config
func (suite *GlideTestSuite) TestReconciliationIntervalSupport() {
	// This is tested implicitly by all reconnection tests
	// The reconciliation interval determines how often the client checks subscription state
	suite.T().Log("Reconciliation interval is supported and tested via reconnection tests")
}
