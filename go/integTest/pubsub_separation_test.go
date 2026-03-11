// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestChannelSeparation tests that different channels don't interfere
func (suite *GlideTestSuite) TestChannelSeparation() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel1 := "channel_a"
			channel2 := "channel_b"

			channels1 := []ChannelDefn{{Channel: channel1, Mode: ExactMode}}
			receiver1 := suite.CreatePubSubReceiver(clientType, channels1, 1, false, ConfigMethod, t)
			defer receiver1.Close()

			channels2 := []ChannelDefn{{Channel: channel2, Mode: ExactMode}}
			receiver2 := suite.CreatePubSubReceiver(clientType, channels2, 2, false, ConfigMethod, t)
			defer receiver2.Close()

			ctx := context.Background()
			var queue1, queue2 *glide.PubSubMessageQueue
			var standalonePublisher *glide.Client
			var clusterPublisher *glide.ClusterClient

			if clientType == StandaloneClient {
				standalonePublisher = suite.defaultClient()
				defer standalonePublisher.Close()
				queue1, _ = receiver1.(*glide.Client).GetQueue()
				queue2, _ = receiver2.(*glide.Client).GetQueue()
			} else {
				clusterPublisher = suite.defaultClusterClient()
				defer clusterPublisher.Close()
				queue1, _ = receiver1.(*glide.ClusterClient).GetQueue()
				queue2, _ = receiver2.(*glide.ClusterClient).GetQueue()
			}

			time.Sleep(200 * time.Millisecond)

			// Publish to channel1
			if clientType == StandaloneClient {
				standalonePublisher.Publish(ctx, channel1, "msg1")
			} else {
				clusterPublisher.Publish(ctx, channel1, "msg1", false)
			}
			time.Sleep(300 * time.Millisecond)

			// Only receiver1 should get it
			select {
			case msg := <-queue1.WaitForMessage():
				assert.Equal(t, "msg1", msg.Message)
			case <-time.After(3 * time.Second):
				t.Fatal("Timeout waiting for msg1")
			}

			// Verify receiver2 didn't get it
			msg := queue2.Pop()
			assert.Nil(t, msg, "receiver2 should not get channel1 message")

			// Publish to channel2
			if clientType == StandaloneClient {
				standalonePublisher.Publish(ctx, channel2, "msg2")
			} else {
				clusterPublisher.Publish(ctx, channel2, "msg2", false)
			}
			time.Sleep(300 * time.Millisecond)

			// Only receiver2 should get it
			select {
			case msg := <-queue2.WaitForMessage():
				assert.Equal(t, "msg2", msg.Message)
			case <-time.After(3 * time.Second):
				t.Fatal("Timeout waiting for msg2")
			}

			// Verify receiver1 didn't get it
			msg = queue1.Pop()
			assert.Nil(t, msg, "receiver1 should not get channel2 message")
		})
	}
}

// TestMultipleClientsOneChannel tests multiple clients on same channel
func (suite *GlideTestSuite) TestMultipleClientsOneChannel() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "shared_channel"

			channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
			receiver1 := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver1.Close()

			receiver2 := suite.CreatePubSubReceiver(clientType, channels, 2, false, ConfigMethod, t)
			defer receiver2.Close()

			ctx := context.Background()
			var queue1, queue2 *glide.PubSubMessageQueue
			var standalonePublisher *glide.Client
			var clusterPublisher *glide.ClusterClient

			if clientType == StandaloneClient {
				standalonePublisher = suite.defaultClient()
				defer standalonePublisher.Close()
				queue1, _ = receiver1.(*glide.Client).GetQueue()
				queue2, _ = receiver2.(*glide.Client).GetQueue()
			} else {
				clusterPublisher = suite.defaultClusterClient()
				defer clusterPublisher.Close()
				queue1, _ = receiver1.(*glide.ClusterClient).GetQueue()
				queue2, _ = receiver2.(*glide.ClusterClient).GetQueue()
			}

			time.Sleep(100 * time.Millisecond)

			if clientType == StandaloneClient {
				standalonePublisher.Publish(ctx, channel, "broadcast")
			} else {
				clusterPublisher.Publish(ctx, channel, "broadcast", false)
			}
			time.Sleep(200 * time.Millisecond)

			// Both should receive
			select {
			case msg1 := <-queue1.WaitForMessage():
				assert.Equal(t, "broadcast", msg1.Message)
			case <-time.After(3 * time.Second):
				t.Fatal("Timeout on receiver1")
			}

			select {
			case msg2 := <-queue2.WaitForMessage():
				assert.Equal(t, "broadcast", msg2.Message)
			case <-time.After(3 * time.Second):
				t.Fatal("Timeout on receiver2")
			}
		})
	}
}
