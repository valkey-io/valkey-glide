// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"strings"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestPubSubExactCoexistence tests WaitForMessage and Pop working together
func (suite *GlideTestSuite) TestPubSubExactCoexistence() {
	channel := "coexist_test"

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.Client).GetQueue()

	time.Sleep(100 * time.Millisecond)

	// Publish two messages
	publisher.Publish(ctx, channel, "msg1")
	publisher.Publish(ctx, channel, "msg2")

	time.Sleep(200 * time.Millisecond)

	// Receive first with WaitForMessage (async style)
	select {
	case msg1 := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "msg1", msg1.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout waiting for msg1")
	}

	// Receive second with Pop (sync style)
	msg2 := queue.Pop()
	assert.NotNil(suite.T(), msg2)
	assert.Equal(suite.T(), "msg2", msg2.Message)
}

// TestPubSubPatternCoexistence tests pattern subscription with both retrieval methods
func (suite *GlideTestSuite) TestPubSubPatternCoexistence() {
	pattern := "news.*"
	channel := "news.sports"

	channels := []ChannelDefn{{Channel: pattern, Mode: PatternMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.Client).GetQueue()

	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, "msg1")
	publisher.Publish(ctx, channel, "msg2")

	time.Sleep(200 * time.Millisecond)

	select {
	case msg1 := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "msg1", msg1.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout waiting for msg1")
	}

	msg2 := queue.Pop()
	assert.NotNil(suite.T(), msg2)
	assert.Equal(suite.T(), "msg2", msg2.Message)
}

// TestPubSubMaxSizeMessage tests large message handling
func (suite *GlideTestSuite) TestPubSubMaxSizeMessage() {
	channel := "max_size_test"
	largeMsg := strings.Repeat("a", 1024*1024)

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.Client).GetQueue()

	time.Sleep(100 * time.Millisecond)

	publisher.Publish(ctx, channel, string(largeMsg))

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), string(largeMsg), msg.Message)
	case <-time.After(5 * time.Second):
		suite.T().Fatal("Timeout waiting for large message")
	}
}

// TestPubSubCustomCommand tests using CustomCommand with pubsub
func (suite *GlideTestSuite) TestPubSubCustomCommand() {
	channel := "custom_cmd_test"

	channels := []ChannelDefn{{Channel: channel, Mode: ExactMode}}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	publisher := suite.defaultClient()
	defer publisher.Close()

	ctx := context.Background()
	queue, _ := receiver.(*glide.Client).GetQueue()

	time.Sleep(100 * time.Millisecond)

	// Use CustomCommand to publish
	publisher.CustomCommand(ctx, []string{"PUBLISH", channel, "test_msg"})

	time.Sleep(100 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "test_msg", msg.Message)
	case <-time.After(3 * time.Second):
		suite.T().Fatal("Timeout waiting for custom command message")
	}
}
