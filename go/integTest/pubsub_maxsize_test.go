// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// TestPubSubMaxSizeMessageCallback tests large message with callback
func (suite *GlideTestSuite) TestPubSubMaxSizeMessageCallback() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "max_size_callback"
			largeMsg := strings.Repeat("b", 1024*1024)

			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, true, ConfigMethod, t)
			defer receiver.Close()

			ctx := context.Background()
			time.Sleep(100 * time.Millisecond)

			if clientType == StandaloneClient {
				publisher := suite.defaultClient()
				defer publisher.Close()
				publisher.Publish(ctx, channel, largeMsg)
			} else {
				publisher := suite.defaultClusterClient()
				defer publisher.Close()
				publisher.Publish(ctx, channel, largeMsg, false)
			}
			time.Sleep(500 * time.Millisecond)

			// Check callback received it
			key := "1-" + channel
			value, ok := callbackCtx.Load(key)
			assert.True(t, ok)
			if ok {
				msg := value.(*models.PubSubMessage)
				assert.Equal(t, largeMsg, msg.Message)
			}
		})
	}
}

// TestShardedMaxSizeMessage tests large sharded message
func (suite *GlideTestSuite) TestShardedMaxSizeMessage() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	channel := "max_size_sharded"
	largeMsg := strings.Repeat("c", 1024*1024)

	channels := []ChannelDefn{{Channel: channel, Mode: ShardedMode}}
	receiver := suite.CreatePubSubReceiver(ClusterClient, channels, 1, false, ConfigMethod, suite.T())
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
