// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestLazyVsBlockingSubscription tests the difference between lazy and blocking subscriptions
func (suite *GlideTestSuite) TestLazyVsBlockingSubscription() {
	channel := "lazy_vs_blocking_channel"
	ctx := context.Background()

	publisher := suite.defaultClient()
	defer publisher.Close()

	// Create receiver with initial subscription to avoid empty channels issue
	channels := []ChannelDefn{
		{Channel: "initial_channel", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	// Wait for initial subscription
	time.Sleep(100 * time.Millisecond)

	// Test lazy subscription - should return immediately without waiting for server confirmation
	start := time.Now()
	err = receiver.(*glide.Client).SubscribeLazy(ctx, []string{channel})
	lazyDuration := time.Since(start)
	assert.NoError(suite.T(), err)
	assert.Less(suite.T(), lazyDuration, 100*time.Millisecond, "Lazy subscribe should return immediately")

	// Wait for subscription to actually establish
	time.Sleep(200 * time.Millisecond)

	// Verify subscription works
	_, err = publisher.Publish(ctx, channel, "lazy_message")
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	// Drain initial channel messages if any
	for {
		select {
		case msg := <-queue.WaitForMessage():
			if msg.Message == "lazy_message" {
				goto foundLazy
			}
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Lazy subscription failed to receive message")
		}
	}
foundLazy:

	// Test blocking subscription - should wait for server confirmation
	channel2 := "blocking_channel"
	start = time.Now()
	err = receiver.(*glide.Client).Subscribe(ctx, []string{channel2}, 5000)
	blockingDuration := time.Since(start)
	assert.NoError(suite.T(), err)
	// Blocking may be fast on localhost, but should complete successfully
	suite.T().Logf("Blocking subscribe took %v", blockingDuration)

	// Should be able to receive immediately after blocking subscribe returns
	_, err = publisher.Publish(ctx, channel2, "blocking_message")
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "blocking_message", msg.Message)
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Blocking subscription failed to receive message")
	}
}

// TestUnsubscribeAll tests unsubscribing from all channels using nil
func (suite *GlideTestSuite) TestUnsubscribeAllChannels() {
	ctx := context.Background()
	channel1 := "unsubscribe_all_1"
	channel2 := "unsubscribe_all_2"
	pattern1 := "unsub_pattern_*"

	publisher := suite.defaultClient()
	defer publisher.Close()

	channels := []ChannelDefn{
		{Channel: channel1, Mode: ExactMode},
		{Channel: channel2, Mode: ExactMode},
		{Channel: pattern1, Mode: PatternMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	// Verify all subscriptions work
	_, err = publisher.Publish(ctx, channel1, "msg1")
	assert.NoError(suite.T(), err)
	time.Sleep(100 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "msg1", msg.Message)
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Failed to receive on channel1")
	}

	// Unsubscribe from all exact channels using nil (blocking - no sleep needed)
	err = receiver.(*glide.Client).Unsubscribe(ctx, nil, 5000)
	assert.NoError(suite.T(), err)

	// Verify exact channels don't receive, but pattern still does
	_, err = publisher.Publish(ctx, channel1, "should_not_receive")
	assert.NoError(suite.T(), err)
	_, err = publisher.Publish(ctx, "unsub_pattern_test", "pattern_msg")
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	select {
	case msg := <-queue.WaitForMessage():
		assert.Equal(suite.T(), "pattern_msg", msg.Message)
		assert.Contains(suite.T(), msg.Channel, "unsub_pattern")
	case <-time.After(2 * time.Second):
		suite.T().Fatal("Pattern subscription should still work")
	}

	// Unsubscribe from all patterns using nil
	err = receiver.(*glide.Client).PUnsubscribe(ctx, nil, 5000)
	assert.NoError(suite.T(), err)

	// Verify no messages received
	_, err = publisher.Publish(ctx, "unsub_pattern_test2", "should_not_receive")
	assert.NoError(suite.T(), err)

	time.Sleep(200 * time.Millisecond)

	select {
	case <-queue.WaitForMessage():
		suite.T().Fatal("Should not receive any messages after unsubscribing from all")
	case <-time.After(500 * time.Millisecond):
		// Expected - no messages
	}
}

// TestSubscribeTimeout tests that blocking subscribe respects timeout
func (suite *GlideTestSuite) TestSubscribeTimeout() {
	ctx := context.Background()
	channel := "timeout_test_channel"

	channels := []ChannelDefn{}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, LazyMethod, suite.T())
	defer receiver.Close()

	// Subscribe with very short timeout should still succeed for normal operation
	err := receiver.(*glide.Client).Subscribe(ctx, []string{channel}, 100)
	assert.NoError(suite.T(), err)

	// Subscribe with 0 timeout should block indefinitely until confirmation
	channel2 := "timeout_test_channel2"
	err = receiver.(*glide.Client).Subscribe(ctx, []string{channel2}, 0)
	assert.NoError(suite.T(), err)
}

// TestNegativeTimeoutError tests that negative timeout returns error
func (suite *GlideTestSuite) TestNegativeTimeoutError() {
	ctx := context.Background()
	channel := "negative_timeout_channel"

	channels := []ChannelDefn{
		{Channel: "initial_channel", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 1, false, ConfigMethod, suite.T())
	defer receiver.Close()

	// Negative timeout should return error
	err := receiver.(*glide.Client).Subscribe(ctx, []string{channel}, -1)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "timeout must be non-negative")
}

// TestDynamicSubscriptionManagement tests adding and removing subscriptions dynamically
func (suite *GlideTestSuite) TestDynamicSubscriptionManagement() {
	ctx := context.Background()
	channel1 := "dynamic_1"
	channel2 := "dynamic_2"
	channel3 := "dynamic_3"

	publisher := suite.defaultClient()
	defer publisher.Close()

	// Start with one initial subscription
	channels := []ChannelDefn{
		{Channel: "initial_dynamic", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	// Dynamically add subscriptions
	err = receiver.(*glide.Client).Subscribe(ctx, []string{channel1, channel2}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	// Verify both channels work
	_, err = publisher.Publish(ctx, channel1, "msg1")
	assert.NoError(suite.T(), err)
	_, err = publisher.Publish(ctx, channel2, "msg2")
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	receivedChannels := make(map[string]bool)
	for i := 0; i < 2; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			receivedChannels[msg.Channel] = true
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Failed to receive messages")
		}
	}
	assert.True(suite.T(), receivedChannels[channel1])
	assert.True(suite.T(), receivedChannels[channel2])

	// Add one more channel
	err = receiver.(*glide.Client).Subscribe(ctx, []string{channel3}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	// Remove channel2
	err = receiver.(*glide.Client).Unsubscribe(ctx, []string{channel2}, 5000)
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	// Verify channel1 and channel3 work, but not channel2
	_, err = publisher.Publish(ctx, channel1, "msg3")
	assert.NoError(suite.T(), err)
	_, err = publisher.Publish(ctx, channel2, "should_not_receive")
	assert.NoError(suite.T(), err)
	_, err = publisher.Publish(ctx, channel3, "msg4")
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	receivedChannels = make(map[string]bool)
	for i := 0; i < 2; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			receivedChannels[msg.Channel] = true
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Failed to receive messages")
		}
	}
	assert.True(suite.T(), receivedChannels[channel1])
	assert.True(suite.T(), receivedChannels[channel3])
	assert.False(suite.T(), receivedChannels[channel2])
}

// TestMixedSubscriptionMethods tests using both lazy and blocking methods together
func (suite *GlideTestSuite) TestMixedSubscriptionMethods() {
	ctx := context.Background()
	lazyChannel := "lazy_channel"
	blockingChannel := "blocking_channel"

	publisher := suite.defaultClient()
	defer publisher.Close()

	channels := []ChannelDefn{
		{Channel: "initial_mixed", Mode: ExactMode},
	}
	receiver := suite.CreatePubSubReceiver(StandaloneClient, channels, 10, false, ConfigMethod, suite.T())
	defer receiver.Close()

	queue, err := receiver.(*glide.Client).GetQueue()
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	// Use lazy subscribe for one channel
	err = receiver.(*glide.Client).SubscribeLazy(ctx, []string{lazyChannel})
	assert.NoError(suite.T(), err)

	// Use blocking subscribe for another
	err = receiver.(*glide.Client).Subscribe(ctx, []string{blockingChannel}, 5000)
	assert.NoError(suite.T(), err)

	// Lazy needs time to establish
	time.Sleep(200 * time.Millisecond)

	// Both should work
	_, err = publisher.Publish(ctx, lazyChannel, "lazy_msg")
	assert.NoError(suite.T(), err)
	_, err = publisher.Publish(ctx, blockingChannel, "blocking_msg")
	assert.NoError(suite.T(), err)

	time.Sleep(100 * time.Millisecond)

	receivedChannels := make(map[string]bool)
	for i := 0; i < 2; i++ {
		select {
		case msg := <-queue.WaitForMessage():
			receivedChannels[msg.Channel] = true
		case <-time.After(2 * time.Second):
			suite.T().Fatal("Failed to receive messages")
		}
	}
	assert.True(suite.T(), receivedChannels[lazyChannel])
	assert.True(suite.T(), receivedChannels[blockingChannel])
}
