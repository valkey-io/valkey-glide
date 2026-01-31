// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestShardedPubSubManyChannels tests sharded pubsub with multiple channels
func (suite *GlideTestSuite) TestShardedPubSubManyChannels() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	numChannels := 5
	channels := make([]ChannelDefn, numChannels)
	for i := 0; i < numChannels; i++ {
		channels[i] = ChannelDefn{
			Channel: suite.T().Name() + string(rune('A'+i)),
			Mode:    ShardedMode,
		}
	}

	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.ClusterClient).GetQueue()

	time.Sleep(200 * time.Millisecond)

	// Publish to all channels
	for i := 0; i < numChannels; i++ {
		publisher.Publish(ctx, channels[i].Channel, "msg", true)
	}

	time.Sleep(300 * time.Millisecond)

	// Receive all messages
	for i := 0; i < numChannels; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			assert.Equal(suite.T(), "msg", msg.Message)
		case <-time.After(3 * time.Second):
			suite.T().Fatalf("Timeout on message %d", i)
		}
	}
}

// TestShardedPubSubCoexistence tests sharded with WaitForMessage and Pop
func (suite *GlideTestSuite) TestShardedPubSubCoexistence() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	channel := "sharded_coexist"

	channels := []ChannelDefn{{Channel: channel, Mode: ShardedMode}}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.ClusterClient).GetQueue()

	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, "msg1", true)
	publisher.Publish(ctx, channel, "msg2", true)

	time.Sleep(200 * time.Millisecond)

	msg1 := <-queue.WaitForMessage()
	assert.Equal(suite.T(), "msg1", msg1.Message)

	msg2 := queue.Pop()
	assert.NotNil(suite.T(), msg2)
	assert.Equal(suite.T(), "msg2", msg2.Message)
}

// TestSUnsubscribeShardedChannel tests unsubscribing from sharded channel
func (suite *GlideTestSuite) TestSUnsubscribeShardedChannel() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	channel := "sunsubscribe_test"

	channels := []ChannelDefn{{Channel: channel, Mode: ShardedMode}}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.ClusterClient).GetQueue()

	time.Sleep(100 * time.Millisecond)

	// Publish and receive
	publisher.Publish(ctx, channel, "before", true)
	time.Sleep(100 * time.Millisecond)

	msg := <-queue.WaitForMessage()
	assert.Equal(suite.T(), "before", msg.Message)

	// Unsubscribe
	receiver.(*glide.ClusterClient).SUnsubscribe(ctx, []string{channel})
	time.Sleep(200 * time.Millisecond)

	// Publish after unsubscribe - should not receive
	publisher.Publish(ctx, channel, "after", true)
	time.Sleep(200 * time.Millisecond)

	select {
	case <-queue.WaitForMessage():
		suite.T().Fatal("Should not receive after unsubscribe")
	default:
		// Expected
	}
}
