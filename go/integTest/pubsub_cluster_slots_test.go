// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestSSubscribeChannelsDifferentSlots tests subscribing to sharded channels
// in different hash slots (cluster only, Redis 7.0+)
func (suite *GlideTestSuite) TestSSubscribeChannelsDifferentSlots() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()

	// These channels hash to different slots due to hash tags
	channels := []string{
		"{slot1}channel_a",
		"{slot2}channel_b",
		"{slot3}channel_c",
		"{slot1}channel_d",
		"{slot4}channel_e",
	}

	messages := make(map[string]string)
	for _, ch := range channels {
		messages[ch] = "msg_" + ch
	}

	// Create receiver without initial subscriptions
	channelDefns := []ChannelDefn{
		{Channel: "initial_sharded", Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channelDefns, 20, false, suite.T())
	defer receiver.Close()

	clusterReceiver := receiver.(*glide.ClusterClient)

	// Subscribe dynamically to all channels
	err := clusterReceiver.SSubscribe(ctx, channels, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(300 * time.Millisecond)

	// Verify subscriptions
	state, err := clusterReceiver.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	for _, channel := range channels {
		_, exists := state.ActualSubscriptions[models.Sharded][channel]
		assert.True(suite.T(), exists, "Channel %s should be subscribed", channel)
	}

	// Create publisher
	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	// Publish to all channels
	for channel, message := range messages {
		_, pubErr := publisher.Publish(ctx, channel, message, true)
		assert.NoError(suite.T(), pubErr)
	}

	time.Sleep(1 * time.Second)

	// Retrieve all messages
	queue, queueErr := clusterReceiver.GetQueue()
	assert.NoError(suite.T(), queueErr)

	receivedMessages := make(map[string]string)
	for i := 0; i < len(channels); i++ {
		select {
		case msg := <-queue.WaitForMessage():
			receivedMessages[msg.Channel] = msg.Message
		case <-time.After(5 * time.Second):
			suite.T().Fatalf("Failed to receive message %d/%d", i+1, len(channels))
		}
	}

	// Verify all messages received
	for channel, expectedMsg := range messages {
		actualMsg, exists := receivedMessages[channel]
		assert.True(suite.T(), exists, "Should receive message for channel %s", channel)
		assert.Equal(suite.T(), expectedMsg, actualMsg, "Message mismatch for channel %s", channel)
	}
}

// TestSUnsubscribeChannelsDifferentSlots tests unsubscribing from sharded channels
// in different hash slots (cluster only, Redis 7.0+)
func (suite *GlideTestSuite) TestSUnsubscribeChannelsDifferentSlots() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()

	channels := []string{
		"{slot1}channel_a",
		"{slot2}channel_b",
	}

	// Create receiver and subscribe dynamically
	channelDefns := []ChannelDefn{
		{Channel: "initial_sharded", Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channelDefns, 10, false, suite.T())
	defer receiver.Close()

	clusterReceiver := receiver.(*glide.ClusterClient)

	err := clusterReceiver.SSubscribe(ctx, channels, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(300 * time.Millisecond)

	// Verify subscriptions
	state, err := clusterReceiver.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	for _, channel := range channels {
		_, exists := state.ActualSubscriptions[models.Sharded][channel]
		assert.True(suite.T(), exists, "Channel %s should be subscribed", channel)
	}

	// Unsubscribe from one channel
	channelToUnsub := "{slot1}channel_a"
	err = clusterReceiver.SUnsubscribe(ctx, []string{channelToUnsub}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(300 * time.Millisecond)

	// Verify only one channel remains
	state, err = clusterReceiver.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)

	_, unsubExists := state.ActualSubscriptions[models.Sharded][channelToUnsub]
	assert.False(suite.T(), unsubExists, "Unsubscribed channel should not be in actual")

	_, remainsExists := state.ActualSubscriptions[models.Sharded]["{slot2}channel_b"]
	assert.True(suite.T(), remainsExists, "Other channel should still be subscribed")
}

// TestShardedPubSubMultipleSlots tests sharded pubsub with channels across multiple slots
func (suite *GlideTestSuite) TestShardedPubSubMultipleSlots() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()

	// Create channels with different hash tags to ensure different slots
	channels := []ChannelDefn{
		{Channel: "{user1}notifications", Mode: ShardedMode},
		{Channel: "{user2}notifications", Mode: ShardedMode},
		{Channel: "{user3}notifications", Mode: ShardedMode},
	}

	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, suite.T())
	defer receiver.Close()

	time.Sleep(300 * time.Millisecond)

	// Verify all subscriptions
	clusterReceiver := receiver.(*glide.ClusterClient)
	var state *models.PubSubState
	var err error
	state, err = clusterReceiver.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	for _, ch := range channels {
		_, exists := state.ActualSubscriptions[models.Sharded][ch.Channel]
		assert.True(suite.T(), exists, "Channel %s should be subscribed", ch.Channel)
	}

	// Create publisher
	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	// Publish to each channel
	for _, ch := range channels {
		_, err := publisher.Publish(ctx, ch.Channel, "test_message_"+ch.Channel, true)
		assert.NoError(suite.T(), err)
	}

	time.Sleep(500 * time.Millisecond)

	// Verify messages received
	queue, queueErr := clusterReceiver.GetQueue()
	assert.NoError(suite.T(), queueErr)

	receivedChannels := make(map[string]bool)
	for i := 0; i < len(channels); i++ {
		select {
		case msg := <-queue.WaitForMessage():
			receivedChannels[msg.Channel] = true
		case <-time.After(3 * time.Second):
			suite.T().Fatalf("Failed to receive message %d/%d", i+1, len(channels))
		}
	}

	for _, ch := range channels {
		assert.True(suite.T(), receivedChannels[ch.Channel],
			"Should receive message for channel %s", ch.Channel)
	}
}
