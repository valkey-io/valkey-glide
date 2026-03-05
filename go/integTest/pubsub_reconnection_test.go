// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestResubscribeAfterConnectionKillExactChannels tests that exact channel subscriptions
// are automatically restored after connection is killed
func (suite *GlideTestSuite) TestResubscribeAfterConnectionKillExactChannels() {
	ctx := context.Background()
	channel := "test_channel_reconnect"

	// Test standalone
	channels := []ChannelDefn{
		{Channel: channel, Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	// Wait for initial subscription
	time.Sleep(200 * time.Millisecond)

	// Verify initial subscription
	client := receiver.(*glide.Client)
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), exists, "Initial subscription should exist")

	// Kill all connections
	_, _ = client.CustomCommand(ctx, []string{"CLIENT", "KILL", "TYPE", "NORMAL"})
	// Error expected since we killed our own connection

	// Wait for reconnection and resubscription
	time.Sleep(2 * time.Second)

	// Verify subscription is restored
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), exists, "Subscription should be restored after reconnection")

	// Test cluster
	clusterReceiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer clusterReceiver.Close()

	time.Sleep(200 * time.Millisecond)

	clusterClient := clusterReceiver.(*glide.ClusterClient)
	state, err = clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), exists)

	// Kill connections on cluster
	_, _ = clusterClient.CustomCommand(ctx, []string{"CLIENT", "KILL", "TYPE", "NORMAL"})

	time.Sleep(2 * time.Second)

	state, err = clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Exact][channel]
	assert.True(suite.T(), exists, "Cluster subscription should be restored")
}

// TestResubscribeAfterConnectionKillManyChannels tests that multiple exact channel
// subscriptions are restored after connection kill
func (suite *GlideTestSuite) TestResubscribeAfterConnectionKillManyChannels() {
	ctx := context.Background()

	// Create 10 channels
	var channels []ChannelDefn
	expectedChannels := make(map[string]bool)
	for i := 0; i < 10; i++ {
		channelName := suite.T().Name() + "_channel_" + string(rune('0'+i))
		channels = append(channels, ChannelDefn{Channel: channelName, Mode: ExactMode})
		expectedChannels[channelName] = true
	}

	// Test standalone
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(300 * time.Millisecond)

	// Verify all initial subscriptions
	client := receiver.(*glide.Client)
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	for channelName := range expectedChannels {
		_, exists := state.ActualSubscriptions[models.Exact][channelName]
		assert.True(suite.T(), exists, "Channel %s should be subscribed", channelName)
	}

	// Kill connections
	client.CustomCommand(ctx, []string{"CLIENT", "KILL", "TYPE", "NORMAL"})

	time.Sleep(2 * time.Second)

	// Verify all subscriptions restored
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	for channelName := range expectedChannels {
		_, exists := state.ActualSubscriptions[models.Exact][channelName]
		assert.True(suite.T(), exists, "Channel %s should be restored", channelName)
	}
}

// TestResubscribeAfterConnectionKillPatterns tests that pattern subscriptions
// are restored after connection kill
func (suite *GlideTestSuite) TestResubscribeAfterConnectionKillPatterns() {
	ctx := context.Background()
	pattern := "test_pattern_*"

	// Test standalone
	channels := []ChannelDefn{
		{Channel: pattern, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	// Verify initial subscription
	client := receiver.(*glide.Client)
	state, err := client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Pattern][pattern]
	assert.True(suite.T(), exists, "Initial pattern subscription should exist")

	// Kill connections
	client.CustomCommand(ctx, []string{"CLIENT", "KILL", "TYPE", "NORMAL"})

	time.Sleep(2 * time.Second)

	// Verify subscription restored
	state, err = client.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Pattern][pattern]
	assert.True(suite.T(), exists, "Pattern subscription should be restored")

	// Test cluster
	clusterReceiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer clusterReceiver.Close()

	time.Sleep(200 * time.Millisecond)

	clusterClient := clusterReceiver.(*glide.ClusterClient)
	state, err = clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Pattern][pattern]
	assert.True(suite.T(), exists)

	clusterClient.CustomCommand(ctx, []string{"CLIENT", "KILL", "TYPE", "NORMAL"})

	time.Sleep(2 * time.Second)

	state, err = clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Pattern][pattern]
	assert.True(suite.T(), exists, "Cluster pattern subscription should be restored")
}

// TestResubscribeAfterConnectionKillSharded tests that sharded subscriptions
// are restored after connection kill (cluster only, Valkey 7.0+)
func (suite *GlideTestSuite) TestResubscribeAfterConnectionKillSharded() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	ctx := context.Background()
	channel := "test_sharded_reconnect"

	channels := []ChannelDefn{
		{Channel: channel, Mode: ShardedMode},
	}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	time.Sleep(200 * time.Millisecond)

	// Verify initial subscription
	clusterClient := receiver.(*glide.ClusterClient)
	state, err := clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists := state.ActualSubscriptions[models.Sharded][channel]
	assert.True(suite.T(), exists, "Initial sharded subscription should exist")

	// Kill connections
	clusterClient.CustomCommand(ctx, []string{"CLIENT", "KILL", "TYPE", "NORMAL"})

	time.Sleep(2 * time.Second)

	// Verify subscription restored
	state, err = clusterClient.GetSubscriptions(ctx)
	assert.NoError(suite.T(), err)
	_, exists = state.ActualSubscriptions[models.Sharded][channel]
	assert.True(suite.T(), exists, "Sharded subscription should be restored")
}
