// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
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

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			queue1, _ := receiver1.(PubSubQueuer).GetQueue()
			queue2, _ := receiver2.(PubSubQueuer).GetQueue()

			time.Sleep(200 * time.Millisecond)

			// Publish to channel1
			suite.PublishMessage(publisher, clientType, channel1, "msg1", false)
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
			suite.PublishMessage(publisher, clientType, channel2, "msg2", false)
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

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			queue1, _ := receiver1.(PubSubQueuer).GetQueue()
			queue2, _ := receiver2.(PubSubQueuer).GetQueue()

			time.Sleep(100 * time.Millisecond)

			suite.PublishMessage(publisher, clientType, channel, "broadcast", false)
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
