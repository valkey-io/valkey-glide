// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestChannelSeparation tests that different channels don't interfere
func (suite *GlideTestSuite) TestChannelSeparation() {
	channel1 := "channel_a"
	channel2 := "channel_b"

	channels1 := []ChannelDefn{{Channel: channel1, Mode: ExactMode}}
	receiver1 := suite.CreatePubSubReceiver(StandaloneClient, channels1, 1, false, suite.T())
	defer receiver1.Close()

	channels2 := []ChannelDefn{{Channel: channel2, Mode: ExactMode}}
	receiver2 := suite.CreatePubSubReceiver(StandaloneClient, channels2, 2, false, suite.T())
	defer receiver2.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue1, _ := receiver1.(*glide.Client).GetQueue()
	queue2, _ := receiver2.(*glide.Client).GetQueue()

	time.Sleep(200 * time.Millisecond)

	// Publish to channel1
	publisher.Publish(ctx, channel1, "msg1")
	time.Sleep(300 * time.Millisecond)

	// Only receiver1 should get it
	select {
	case msg := <-queue1.WaitForMessage():
		assert.Equal(suite.T(), "msg1", msg.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout waiting for msg1")
	}

	// Verify receiver2 didn't get it
	msg := queue2.Pop()
	assert.Nil(suite.T(), msg, "receiver2 should not get channel1 message")

	// Publish to channel2
	publisher.Publish(ctx, channel2, "msg2")
	time.Sleep(300 * time.Millisecond)

	// Only receiver2 should get it
	select {
	case msg := <-queue2.WaitForMessage():
		assert.Equal(suite.T(), "msg2", msg.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout waiting for msg2")
	}

	// Verify receiver1 didn't get it
	msg = queue1.Pop()
	assert.Nil(suite.T(), msg, "receiver1 should not get channel2 message")
}

// TestMultipleClientsOneChannel tests multiple clients on same channel
func (suite *GlideTestSuite) TestMultipleClientsOneChannel() {
	channel := "shared_channel"

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver1 := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, suite.T())
	defer receiver1.Close()

	receiver2 := suite.CreatePubSubReceiver(StandaloneClient, channels, 2, false, suite.T())
	defer receiver2.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue1, _ := receiver1.(*glide.Client).GetQueue()
	queue2, _ := receiver2.(*glide.Client).GetQueue()

	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, "broadcast")
	time.Sleep(200 * time.Millisecond)

	// Both should receive
	select {
	case msg1 := <-queue1.WaitForMessage():
		assert.Equal(suite.T(), "broadcast", msg1.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout on receiver1")
	}

	select {
	case msg2 := <-queue2.WaitForMessage():
		assert.Equal(suite.T(), "broadcast", msg2.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout on receiver2")
	}
}
