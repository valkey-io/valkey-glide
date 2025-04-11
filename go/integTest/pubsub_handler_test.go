// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"sync"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

// TestMessageHandlerReceivesPublishedMessages verifies that the message handler properly receives
// and processes published messages with correct context and channel information
func (suite *PubSubTestSuite) TestMessageHandlerReceivesPublishedMessages() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create message tracking channels
	messageReceived := make(chan bool, 1)
	messageContent := make(chan *api.PubSubMessage, 1)

	// Test context with verification fields
	type testContext struct {
		channelName string
		received    bool
		mu          sync.Mutex
	}
	ctx := &testContext{
		channelName: "test-handler-channel",
		received:    false,
	}

	// Create message handler callback
	callback := func(message *api.PubSubMessage, context any) {
		if tc, ok := context.(*testContext); ok {
			tc.mu.Lock()
			defer tc.mu.Unlock()

			// Verify message matches expected channel
			if message.Channel == tc.channelName {
				tc.received = true
				messageReceived <- true
				messageContent <- message
			}
		}
	}

	// Create subscription config with the message handler
	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, ctx.channelName).
		WithCallback(callback, ctx)

	// Create subscriber with the subscription configuration
	suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Allow time for subscription to be established
	time.Sleep(100 * time.Millisecond)

	// Publish test message
	testMessage := "test handler message"
	_, err := publisher.CustomCommand([]string{"PUBLISH", ctx.channelName, testMessage})
	assert.NoError(suite.T(), err)

	// Wait for message handler to process the message
	select {
	case <-messageReceived:
		// Get the message content
		msg := <-messageContent

		// Verify message content
		assert.Equal(suite.T(), testMessage, msg.Message)
		assert.Equal(suite.T(), ctx.channelName, msg.Channel)

		// Verify context was updated
		ctx.mu.Lock()
		assert.True(suite.T(), ctx.received)
		ctx.mu.Unlock()
	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Message handler did not receive the message within timeout")
	}
}

// TestMessageHandlerWithMultipleChannels verifies that a single message handler can
// properly receive and process messages from multiple subscribed channels
func (suite *PubSubTestSuite) TestMessageHandlerWithMultipleChannels() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create channels to track messages from different channels
	messages := make(chan *api.PubSubMessage, 2)
	var wg sync.WaitGroup
	wg.Add(2) // Expect one message from each channel

	// Test context to track messages from multiple channels
	type testContext struct {
		channels map[string]bool
		mu       sync.Mutex
	}
	ctx := &testContext{
		channels: map[string]bool{
			"test-handler-channel-1": false,
			"test-handler-channel-2": false,
		},
	}

	// Create message handler callback that processes messages from both channels
	callback := func(message *api.PubSubMessage, context any) {
		if tc, ok := context.(*testContext); ok {
			tc.mu.Lock()
			defer tc.mu.Unlock()

			if _, exists := tc.channels[message.Channel]; exists {
				tc.channels[message.Channel] = true
				messages <- message
				wg.Done()
			}
		}
	}

	// Create subscription config for multiple channels
	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-handler-channel-1").
		WithSubscription(api.ExactChannelMode, "test-handler-channel-2").
		WithCallback(callback, ctx)

	// Create subscriber with the subscription configuration
	suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Allow time for subscriptions to be established
	time.Sleep(100 * time.Millisecond)

	// Publish test messages to both channels
	testMessages := map[string]string{
		"test-handler-channel-1": "message for channel 1",
		"test-handler-channel-2": "message for channel 2",
	}

	for channel, message := range testMessages {
		_, err := publisher.CustomCommand([]string{"PUBLISH", channel, message})
		assert.NoError(suite.T(), err)
		time.Sleep(50 * time.Millisecond) // Small delay between publishes
	}

	// Wait for all messages to be processed or timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Expected - received messages from both channels
		close(messages)

		// Verify all messages were received and processed
		receivedMessages := make(map[string]string)
		for msg := range messages {
			receivedMessages[msg.Channel] = msg.Message
		}

		assert.Equal(suite.T(), len(testMessages), len(receivedMessages))
		for channel, expectedMsg := range testMessages {
			actualMsg, exists := receivedMessages[channel]
			assert.True(suite.T(), exists, "Message from channel %s was not received", channel)
			assert.Equal(suite.T(), expectedMsg, actualMsg, "Unexpected message content for channel %s", channel)
		}

		// Verify context tracking
		ctx.mu.Lock()
		for channel, received := range ctx.channels {
			assert.True(suite.T(), received, "Channel %s was not marked as received in context", channel)
		}
		ctx.mu.Unlock()

	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Timed out waiting for messages from all channels")
	}
}

// TestMessageHandlerWithPatternSubscriptions verifies that the message handler correctly
// processes messages from pattern-based subscriptions
func (suite *PubSubTestSuite) TestMessageHandlerWithPatternSubscriptions() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create channels to track messages for different patterns
	messagesByPattern := make(map[string][]*api.PubSubMessage)
	var mu sync.Mutex
	var wg sync.WaitGroup
	wg.Add(4) // Expect 2 messages for each pattern

	// Test context with pattern tracking
	type testContext struct {
		patterns map[string]bool
	}
	ctx := &testContext{
		patterns: map[string]bool{
			"test-news-*":    false,
			"test-weather-*": false,
		},
	}

	// Create message handler callback for pattern subscriptions
	callback := func(message *api.PubSubMessage, context any) {
		if tc, ok := context.(*testContext); ok {
			pattern := message.Pattern.Value()
			mu.Lock()
			// Update pattern received status
			if _, exists := tc.patterns[pattern]; exists {
				tc.patterns[pattern] = true
				// Track message by pattern
				messagesByPattern[pattern] = append(messagesByPattern[pattern], message)
			}
			mu.Unlock()
			wg.Done()
		}
	}

	// Create subscription config for multiple patterns
	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.PatternChannelMode, "test-news-*").
		WithSubscription(api.PatternChannelMode, "test-weather-*").
		WithCallback(callback, ctx)

	// Create subscriber with the subscription configuration
	suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Allow time for subscriptions to be established
	time.Sleep(100 * time.Millisecond)

	// Publish test messages matching different patterns
	testMessages := map[string]string{
		"test-news-sports":    "Latest sports update",
		"test-news-politics":  "Political news flash",
		"test-weather-local":  "Local weather forecast",
		"test-weather-global": "Global weather report",
		"test-other-channel":  "Should not receive this",
	}

	for channel, message := range testMessages {
		_, err := publisher.CustomCommand([]string{"PUBLISH", channel, message})
		assert.NoError(suite.T(), err)
		time.Sleep(50 * time.Millisecond) // Small delay between publishes
	}

	// Wait for all expected messages or timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Verify all patterns received messages
		mu.Lock()
		defer mu.Unlock()

		// Check pattern statuses
		for pattern, received := range ctx.patterns {
			assert.True(suite.T(), received, "Pattern %s should have received messages", pattern)
		}

		// Verify messages received for each pattern
		newsMessages := messagesByPattern["test-news-*"]
		weatherMessages := messagesByPattern["test-weather-*"]

		assert.Equal(suite.T(), 2, len(newsMessages), "Should receive exactly 2 news messages")
		assert.Equal(suite.T(), 2, len(weatherMessages), "Should receive exactly 2 weather messages")

		// Verify news messages
		newsChannels := make(map[string]bool)
		for _, msg := range newsMessages {
			assert.Equal(suite.T(), "test-news-*", msg.Pattern.Value())
			newsChannels[msg.Channel] = true
			assert.Equal(suite.T(), testMessages[msg.Channel], msg.Message)
		}
		assert.True(suite.T(), newsChannels["test-news-sports"])
		assert.True(suite.T(), newsChannels["test-news-politics"])

		// Verify weather messages
		weatherChannels := make(map[string]bool)
		for _, msg := range weatherMessages {
			assert.Equal(suite.T(), "test-weather-*", msg.Pattern.Value())
			weatherChannels[msg.Channel] = true
			assert.Equal(suite.T(), testMessages[msg.Channel], msg.Message)
		}
		assert.True(suite.T(), weatherChannels["test-weather-local"])
		assert.True(suite.T(), weatherChannels["test-weather-global"])

	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Timed out waiting for pattern messages")
	}
}

// TestMessageHandlerErrorHandling verifies that the message handler properly handles
// errors and panics in the callback function
func (suite *PubSubTestSuite) TestMessageHandlerErrorHandling() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create channels to track message processing
	messageProcessed := make(chan bool, 1)
	secondMessageProcessed := make(chan bool, 1)

	// Test context for tracking callback execution
	type testContext struct {
		shouldPanic bool
		mu          sync.Mutex
	}
	ctx := &testContext{
		shouldPanic: true,
	}

	// Create callback that will panic on first message but succeed on second
	callback := func(message *api.PubSubMessage, context any) {
		if tc, ok := context.(*testContext); ok {
			tc.mu.Lock()
			shouldPanic := tc.shouldPanic
			tc.mu.Unlock()

			if shouldPanic {
				tc.mu.Lock()
				tc.shouldPanic = false
				tc.mu.Unlock()
				messageProcessed <- true
				panic("intentional panic in message handler")
			}

			// Second message should process normally
			secondMessageProcessed <- true
		}
	}

	// Create subscription config with the panicking callback
	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-error-channel").
		WithCallback(callback, ctx)

	// Create subscriber with the subscription configuration
	suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Allow time for subscription to be established
	time.Sleep(100 * time.Millisecond)

	// Send first message that will trigger panic
	_, err := publisher.CustomCommand([]string{"PUBLISH", "test-error-channel", "trigger panic"})
	assert.NoError(suite.T(), err)

	// Wait for first message to be processed (and panic)
	select {
	case <-messageProcessed:
		// Expected - message was processed and triggered panic
	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "First message was not processed")
	}

	// Allow time for any panic recovery
	time.Sleep(100 * time.Millisecond)

	// Send second message that should process normally
	_, err = publisher.CustomCommand([]string{"PUBLISH", "test-error-channel", "normal message"})
	assert.NoError(suite.T(), err)

	// Wait for second message to be processed normally
	select {
	case <-secondMessageProcessed:
		// Expected - second message was processed normally
	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Second message was not processed after panic recovery")
	}

	// Verify final state
	ctx.mu.Lock()
	assert.False(suite.T(), ctx.shouldPanic, "Context should have been updated through both messages")
	ctx.mu.Unlock()
}
