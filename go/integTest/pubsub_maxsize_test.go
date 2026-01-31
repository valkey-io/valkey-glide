// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestPubSubMaxSizeMessageStandalone tests 1MB message on standalone
func (suite *GlideTestSuite) TestPubSubMaxSizeMessageStandalone() {
	channel := "max_size_standalone"
	largeMsg := strings.Repeat("a", 1024*1024)

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.Client).GetQueue()

	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, largeMsg)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), largeMsg, msg.Message)
	case <-time.After(5 * time.Second):
		suite.T().Fatal("Timeout")
	}
}

// TestPubSubMaxSizeMessageCallback tests large message with callback
func (suite *GlideTestSuite) TestPubSubMaxSizeMessageCallback() {
	channel := "max_size_callback"
	largeMsg := strings.Repeat("b", 1024*1024)

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, true, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, largeMsg)
	time.Sleep(500 * time.Millisecond)

	// Check callback received it
	key := "1-" + channel
	value, ok := callbackCtx.Load(key)
	assert.True(suite.T(), ok)
	if ok {
		msg := value.(*models.PubSubMessage)
		assert.Equal(suite.T(), largeMsg, msg.Message)
	}
}

// TestShardedMaxSizeMessage tests large sharded message
func (suite *GlideTestSuite) TestShardedMaxSizeMessage() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	channel := "max_size_sharded"
	largeMsg := strings.Repeat("c", 1024*1024)

	channels := []ChannelDefn{{Channel: channel, Mode: ShardedMode}}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClusterClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.ClusterClient).GetQueue()

	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, largeMsg, true)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), largeMsg, msg.Message)
	case <-time.After(5 * time.Second):
		suite.T().Fatal("Timeout")
	}
}
