// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestPubSubExactCoexistence tests WaitForMessage and Pop working together
func (suite *GlideTestSuite) TestPubSubExactCoexistence() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "coexist_test"

			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			ctx := context.Background()
			var queue *glide.PubSubMessageQueue
			if clientType == StandaloneClient {
				publisher := suite.defaultClient()
				defer publisher.Close()
				queue, _ = receiver.(*glide.Client).GetQueue()

				time.Sleep(100 * time.Millisecond)

				// Publish two messages
				publisher.Publish(ctx, channel, "msg1")
				publisher.Publish(ctx, channel, "msg2")
			} else {
				publisher := suite.defaultClusterClient()
				defer publisher.Close()
				queue, _ = receiver.(*glide.ClusterClient).GetQueue()

				time.Sleep(100 * time.Millisecond)

				// Publish two messages
				publisher.Publish(ctx, channel, "msg1", false)
				publisher.Publish(ctx, channel, "msg2", false)
			}

			time.Sleep(200 * time.Millisecond)

			// Receive first with WaitForMessage (async style)
			select {
			case msg1 := <-queue.WaitForMessage():
				assert.Equal(t, "msg1", msg1.Message)
			case <-time.After(3 * time.Second):
				t.Fatal("Timeout waiting for msg1")
			}

			// Receive second with Pop (sync style)
			msg2 := queue.Pop()
			assert.NotNil(t, msg2)
			assert.Equal(t, "msg2", msg2.Message)
		})
	}
}

// TestPubSubPatternCoexistence tests pattern subscription with both retrieval methods
func (suite *GlideTestSuite) TestPubSubPatternCoexistence() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			pattern := "news.*"
			channel := "news.sports"

			channels := []ChannelDefn{{Channel: pattern, Mode: PatternMode}}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			ctx := context.Background()
			var queue *glide.PubSubMessageQueue
			if clientType == StandaloneClient {
				publisher := suite.defaultClient()
				defer publisher.Close()
				queue, _ = receiver.(*glide.Client).GetQueue()

				time.Sleep(100 * time.Millisecond)

				publisher.Publish(ctx, channel, "msg1")
				publisher.Publish(ctx, channel, "msg2")
			} else {
				publisher := suite.defaultClusterClient()
				defer publisher.Close()
				queue, _ = receiver.(*glide.ClusterClient).GetQueue()

				time.Sleep(100 * time.Millisecond)

				publisher.Publish(ctx, channel, "msg1", false)
				publisher.Publish(ctx, channel, "msg2", false)
			}

			time.Sleep(200 * time.Millisecond)

			select {
			case msg1 := <-queue.WaitForMessage():
				assert.Equal(t, "msg1", msg1.Message)
			case <-time.After(3 * time.Second):
				t.Fatal("Timeout waiting for msg1")
			}

			msg2 := queue.Pop()
			assert.NotNil(t, msg2)
			assert.Equal(t, "msg2", msg2.Message)
		})
	}
}

// TestPubSubMaxSizeMessage tests large message handling
func (suite *GlideTestSuite) TestPubSubMaxSizeMessage() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "max_size_test"
			largeMsg := strings.Repeat("a", 1024*1024)

			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			ctx := context.Background()
			var queue *glide.PubSubMessageQueue
			if clientType == StandaloneClient {
				publisher := suite.defaultClient()
				defer publisher.Close()
				queue, _ = receiver.(*glide.Client).GetQueue()

				time.Sleep(100 * time.Millisecond)

				publisher.Publish(ctx, channel, string(largeMsg))
			} else {
				publisher := suite.defaultClusterClient()
				defer publisher.Close()
				queue, _ = receiver.(*glide.ClusterClient).GetQueue()

				time.Sleep(100 * time.Millisecond)

				publisher.Publish(ctx, channel, string(largeMsg), false)
			}

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(t, string(largeMsg), msg.Message)
			case <-time.After(5 * time.Second):
				t.Fatal("Timeout waiting for large message")
			}
		})
	}
}
