// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2"
)

// TestLazyVsBlockingSubscription tests the difference between lazy and blocking subscriptions
func (suite *GlideTestSuite) TestLazyVsBlockingSubscription() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			channel := "lazy_vs_blocking_channel"
			ctx := context.Background()

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			// Create receiver with initial subscription to avoid empty channels issue
			channels := []ChannelDefn{
				{Channel: "initial_channel", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.NoError(t, err)

			// Wait for initial subscription
			time.Sleep(100 * time.Millisecond)

			// Test lazy subscription - should return immediately without waiting for server confirmation
			start := time.Now()
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).SubscribeLazy(ctx, []string{channel})
			} else {
				err = receiver.(*glide.ClusterClient).SubscribeLazy(ctx, []string{channel})
			}
			lazyDuration := time.Since(start)
			assert.NoError(t, err)
			assert.Less(t, lazyDuration, 100*time.Millisecond, "Lazy subscribe should return immediately")

			// Wait for subscription to actually establish
			time.Sleep(200 * time.Millisecond)

			// Verify subscription works
			err = suite.PublishMessage(publisher, clientType, channel, "lazy_message", false)
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			// Drain initial channel messages if any
			for {
				select {
				case msg := <-queue.WaitForMessage():
					if msg.Message == "lazy_message" {
						goto foundLazy
					}
				case <-time.After(2 * time.Second):
					t.Fatal("Lazy subscription failed to receive message")
				}
			}
		foundLazy:

			// Test blocking subscription - should wait for server confirmation
			channel2 := "blocking_channel"
			start = time.Now()
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Subscribe(ctx, []string{channel2}, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).Subscribe(ctx, []string{channel2}, 5000)
			}
			blockingDuration := time.Since(start)
			assert.NoError(t, err)
			// Blocking may be fast on localhost, but should complete successfully
			t.Logf("Blocking subscribe took %v", blockingDuration)

			// Should be able to receive immediately after blocking subscribe returns
			err = suite.PublishMessage(publisher, clientType, channel2, "blocking_message", false)
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(t, "blocking_message", msg.Message)
			case <-time.After(2 * time.Second):
				t.Fatal("Blocking subscription failed to receive message")
			}
		})
	}
}

// TestUnsubscribeAll tests unsubscribing from all channels using nil
func (suite *GlideTestSuite) TestUnsubscribeAllChannels() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel1 := "unsubscribe_all_1"
			channel2 := "unsubscribe_all_2"
			pattern1 := "unsub_pattern_*"

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			channels := []ChannelDefn{
				{Channel: channel1, Mode: ExactMode},
				{Channel: channel2, Mode: ExactMode},
				{Channel: pattern1, Mode: PatternMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.NoError(t, err)

			time.Sleep(200 * time.Millisecond)

			// Verify all subscriptions work
			err = suite.PublishMessage(publisher, clientType, channel1, "msg1", false)
			assert.NoError(t, err)
			time.Sleep(100 * time.Millisecond)

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(t, "msg1", msg.Message)
			case <-time.After(2 * time.Second):
				t.Fatal("Failed to receive on channel1")
			}

			// Unsubscribe from all exact channels using nil (blocking - no sleep needed)
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Unsubscribe(ctx, nil, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).Unsubscribe(ctx, nil, 5000)
			}
			assert.NoError(t, err)

			// Verify exact channels don't receive, but pattern still does
			err = suite.PublishMessage(publisher, clientType, channel1, "should_not_receive", false)
			assert.NoError(t, err)
			err = suite.PublishMessage(publisher, clientType, "unsub_pattern_test", "pattern_msg", false)
			assert.NoError(t, err)

			time.Sleep(200 * time.Millisecond)

			select {
			case msg := <-queue.WaitForMessage():
				assert.Equal(t, "pattern_msg", msg.Message)
				assert.Contains(t, msg.Channel, "unsub_pattern")
			case <-time.After(2 * time.Second):
				t.Fatal("Pattern subscription should still work")
			}

			// Now test PUnsubscribe from all patterns using nil
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).PUnsubscribe(ctx, nil, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).PUnsubscribe(ctx, nil, 5000)
			}
			assert.NoError(t, err)

			// Verify pattern no longer receives
			err = suite.PublishMessage(publisher, clientType, "unsub_pattern_test2", "should_not_receive", false)
			assert.NoError(t, err)

			time.Sleep(200 * time.Millisecond)

			// Should not receive any message (use non-blocking check)
			select {
			case msg := <-queue.WaitForMessage():
				t.Fatalf("Should not receive message after PUnsubscribe(nil), got: %s", msg.Message)
			case <-time.After(500 * time.Millisecond):
				// Expected - no message received
			}
		})
	}
}

// TestSubscribeTimeout tests that blocking subscribe respects timeout
func (suite *GlideTestSuite) TestSubscribeTimeout() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel := "timeout_test_channel"

			channels := []ChannelDefn{}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, LazyMethod, t)
			defer receiver.Close()

			var err error
			if clientType == StandaloneClient {
				client := receiver.(*glide.Client)

				// Subscribe with very short timeout should still succeed for normal operation
				err = client.Subscribe(ctx, []string{channel}, 100)
				assert.NoError(t, err)

				// Subscribe with 0 timeout should block indefinitely until confirmation
				channel2 := "timeout_test_channel2"
				err = client.Subscribe(ctx, []string{channel2}, 0)
				assert.NoError(t, err)
			} else {
				client := receiver.(*glide.ClusterClient)

				// Subscribe with very short timeout should still succeed for normal operation
				err = client.Subscribe(ctx, []string{channel}, 100)
				assert.NoError(t, err)

				// Subscribe with 0 timeout should block indefinitely until confirmation
				channel2 := "timeout_test_channel2"
				err = client.Subscribe(ctx, []string{channel2}, 0)
				assert.NoError(t, err)
			}
		})
	}
}

// TestNegativeTimeoutError tests that negative timeout returns error
func (suite *GlideTestSuite) TestNegativeTimeoutError() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel := "negative_timeout_channel"

			channels := []ChannelDefn{
				{Channel: "initial_channel", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false, ConfigMethod, t)
			defer receiver.Close()

			// Negative timeout should return error
			var err error
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Subscribe(ctx, []string{channel}, -1)
			} else {
				err = receiver.(*glide.ClusterClient).Subscribe(ctx, []string{channel}, -1)
			}
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "timeout must be non-negative")
		})
	}
}

// TestDynamicSubscriptionManagement tests adding and removing subscriptions dynamically
func (suite *GlideTestSuite) TestDynamicSubscriptionManagement() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			channel1 := "dynamic_1"
			channel2 := "dynamic_2"
			channel3 := "dynamic_3"

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			// Start with one initial subscription
			channels := []ChannelDefn{
				{Channel: "initial_dynamic", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			// Dynamically add subscriptions
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Subscribe(ctx, []string{channel1, channel2}, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).Subscribe(ctx, []string{channel1, channel2}, 5000)
			}
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			// Verify both channels work
			err = suite.PublishMessage(publisher, clientType, channel1, "msg1", false)
			assert.NoError(t, err)
			err = suite.PublishMessage(publisher, clientType, channel2, "msg2", false)
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			receivedChannels := make(map[string]bool)
			for i := 0; i < 2; i++ {
				select {
				case msg := <-queue.WaitForMessage():
					receivedChannels[msg.Channel] = true
				case <-time.After(2 * time.Second):
					t.Fatal("Failed to receive messages")
				}
			}
			assert.True(t, receivedChannels[channel1])
			assert.True(t, receivedChannels[channel2])

			// Add one more channel
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Subscribe(ctx, []string{channel3}, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).Subscribe(ctx, []string{channel3}, 5000)
			}
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			// Remove channel2
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Unsubscribe(ctx, []string{channel2}, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).Unsubscribe(ctx, []string{channel2}, 5000)
			}
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			// Verify channel1 and channel3 work, but not channel2
			err = suite.PublishMessage(publisher, clientType, channel1, "msg3", false)
			assert.NoError(t, err)
			err = suite.PublishMessage(publisher, clientType, channel2, "should_not_receive", false)
			assert.NoError(t, err)
			err = suite.PublishMessage(publisher, clientType, channel3, "msg4", false)
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			receivedChannels = make(map[string]bool)
			for i := 0; i < 2; i++ {
				select {
				case msg := <-queue.WaitForMessage():
					receivedChannels[msg.Channel] = true
				case <-time.After(2 * time.Second):
					t.Fatal("Failed to receive messages")
				}
			}
			assert.True(t, receivedChannels[channel1])
			assert.True(t, receivedChannels[channel3])
			assert.False(t, receivedChannels[channel2])
		})
	}
}

// TestMixedSubscriptionMethods tests using both lazy and blocking methods together
func (suite *GlideTestSuite) TestMixedSubscriptionMethods() {
	clientTypes := []ClientType{StandaloneClient, ClusterClient}

	for _, clientType := range clientTypes {
		suite.T().Run(clientType.String(), func(t *testing.T) {
			ctx := context.Background()
			lazyChannel := "lazy_channel"
			blockingChannel := "blocking_channel"

			publisher := suite.createAnyClient(clientType, nil)
			defer publisher.Close()

			channels := []ChannelDefn{
				{Channel: "initial_mixed", Mode: ExactMode},
			}
			receiver := suite.CreatePubSubReceiver(clientType, channels, 10, false, ConfigMethod, t)
			defer receiver.Close()

			queue, err := receiver.(PubSubQueuer).GetQueue()
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			// Use lazy subscribe for one channel
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).SubscribeLazy(ctx, []string{lazyChannel})
			} else {
				err = receiver.(*glide.ClusterClient).SubscribeLazy(ctx, []string{lazyChannel})
			}
			assert.NoError(t, err)

			// Use blocking subscribe for another
			if clientType == StandaloneClient {
				err = receiver.(*glide.Client).Subscribe(ctx, []string{blockingChannel}, 5000)
			} else {
				err = receiver.(*glide.ClusterClient).Subscribe(ctx, []string{blockingChannel}, 5000)
			}
			assert.NoError(t, err)

			// Lazy needs time to establish
			time.Sleep(200 * time.Millisecond)

			// Both should work
			err = suite.PublishMessage(publisher, clientType, lazyChannel, "lazy_msg", false)
			assert.NoError(t, err)
			err = suite.PublishMessage(publisher, clientType, blockingChannel, "blocking_msg", false)
			assert.NoError(t, err)

			time.Sleep(100 * time.Millisecond)

			receivedChannels := make(map[string]bool)
			for i := 0; i < 2; i++ {
				select {
				case msg := <-queue.WaitForMessage():
					receivedChannels[msg.Channel] = true
				case <-time.After(2 * time.Second):
					t.Fatal("Failed to receive messages")
				}
			}
			assert.True(t, receivedChannels[lazyChannel])
			assert.True(t, receivedChannels[blockingChannel])
		})
	}
}
