// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"sync"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

// TestBasicPubSubWithGlideClient tests basic publish and subscribe functionality with GlideClient
func (suite *PubSubTestSuite) TestBasicPubSubWithGlideClient() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create a subscriber client with subscription configuration
	messageReceived := make(chan *api.PubSubMessage, 1)
	var messageContext struct {
		channel string
	}

	callback := func(message *api.PubSubMessage, ctx any) {
		messageReceived <- message
	}

	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-channel").
		WithCallback(callback, &messageContext)

	suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Give some time for subscription to be established
	time.Sleep(100 * time.Millisecond)

	// Publish a message
	testMessage := "hello world"
	result, err := publisher.Publish("test-channel", testMessage)
	// result, err := publisher.CustomCommand([]string{"PUBLISH", "test-channel", testMessage})
	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), result)

	// Wait for the message to be received
	var receivedMessage *api.PubSubMessage
	select {
	case receivedMessage = <-messageReceived:
	case <-time.After(5 * time.Second):
		assert.Fail(suite.T(), "Timed out waiting for message")
	}

	// Verify the message content
	if receivedMessage != nil {
		assert.Equal(suite.T(), testMessage, receivedMessage.Message)
		assert.Equal(suite.T(), "test-channel", receivedMessage.Channel)
		assert.True(suite.T(), receivedMessage.Pattern.IsNil())
	}
}

// TestMultipleSubscribersWithGlideClient tests message delivery to multiple subscribers
func (suite *PubSubTestSuite) TestMultipleSubscribersWithGlideClient() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create channels for message tracking
	messages1 := make(chan *api.PubSubMessage, 10)
	messages2 := make(chan *api.PubSubMessage, 10)

	// Create subscriber clients with different channels
	callback1 := func(message *api.PubSubMessage, ctx any) {
		messages1 <- message
	}

	callback2 := func(message *api.PubSubMessage, ctx any) {
		messages2 <- message
	}

	// Subscribe to different channels
	subscriptionConfig1 := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-channel-multi").
		WithCallback(callback1, nil)

	subscriptionConfig2 := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-channel-multi").
		WithCallback(callback2, nil)

	suite.createClientWithSubscriptions("subscriber1", subscriptionConfig1)
	suite.createClientWithSubscriptions("subscriber2", subscriptionConfig2)

	// Give time for subscriptions to be established
	time.Sleep(500 * time.Millisecond)

	// Number of messages to send to each channel
	const messageCount = 1

	// Send messages to both channels
	for i := 0; i < messageCount; i++ {
		msg := fmt.Sprintf("Hello Subscribers - msg %d", i)

		result1, err := publisher.Publish("test-channel-multi", msg)
		assert.Nil(suite.T(), err)
		assert.NotNil(suite.T(), result1)
		suite.T().Logf("Published message to channel")

		// Small delay between messages to ensure ordering
		time.Sleep(100 * time.Millisecond)
	}

	// Wait for messages to be received with timeout
	timeout := time.After(5 * time.Second)
	received1 := make([]*api.PubSubMessage, 0, messageCount)
	received2 := make([]*api.PubSubMessage, 0, messageCount)

	// Wait for messageCount messages or timeout
	waitForMessages := func(ch chan *api.PubSubMessage, received *[]*api.PubSubMessage) {
		for len(*received) < messageCount {
			select {
			case msg := <-ch:
				*received = append(*received, msg)
			case <-timeout:
				return // Exit on timeout
			}
		}
	}

	// Use wait groups to wait for both channels concurrently
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		waitForMessages(messages1, &received1)
	}()

	go func() {
		defer wg.Done()
		waitForMessages(messages2, &received2)
	}()

	// Wait for both to complete or timeout
	wgDone := make(chan struct{})
	go func() {
		wg.Wait()
		close(wgDone)
	}()

	select {
	case <-wgDone:
		// Both channels processed
	case <-timeout:
		// Timeout occurred (the individual waitForMessages functions will have stopped)
	}

	// Verify results
	assert.Equal(suite.T(), messageCount, len(received1), "Client 1 should receive exactly %d messages", messageCount)
	assert.Equal(suite.T(), messageCount, len(received2), "Client 2 should receive exactly %d messages", messageCount)

	// Verify message content and order
	for i := 0; i < messageCount; i++ {
		if i < len(received1) {
			expectedMsg1 := fmt.Sprintf("Hello Subscribers - msg %d", i)
			assert.Equal(suite.T(), expectedMsg1, received1[i].Message, "Unexpected message for client 1")
		}

		if i < len(received2) {
			expectedMsg2 := fmt.Sprintf("Hello Subscribers - msg %d", i)
			assert.Equal(suite.T(), expectedMsg2, received2[i].Message, "Unexpected message for client 2")
		}
	}
}

// TestUnsubscribeWithGlideClient tests unsubscribe functionality
func (suite *PubSubTestSuite) TestUnsubscribeWithGlideClient() {
	suite.T().Skip("Skipping unsubscribe test due to Rust implementation limitation - not yet implemented in lib.rs:574")

	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create a subscriber client with subscription configuration
	messageReceived := make(chan *api.PubSubMessage, 10)
	callback := func(message *api.PubSubMessage, ctx any) {
		messageReceived <- message
	}

	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-channel-unsub").
		WithCallback(callback, nil)

	subscriber := suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Give some time for subscription to be established
	time.Sleep(100 * time.Millisecond)

	// Publish a message and verify it's received
	testMessage := "message before unsubscribe"
	_, err := publisher.Publish("test-channel-unsub", testMessage)
	assert.Nil(suite.T(), err)

	// Wait for the message to be received
	var receivedMessage *api.PubSubMessage
	select {
	case receivedMessage = <-messageReceived:
		assert.Equal(suite.T(), testMessage, receivedMessage.Message)
	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Timed out waiting for message before unsubscribe")
	}

	// Unsubscribe
	_, err = subscriber.CustomCommand([]string{"UNSUBSCRIBE", "test-channel-unsub"})
	assert.Nil(suite.T(), err)

	// Give some time for unsubscribe to take effect
	time.Sleep(100 * time.Millisecond)

	// Publish another message
	testMessage2 := "message after unsubscribe"
	_, err = publisher.Publish("test-channel-unsub", testMessage2)
	assert.Nil(suite.T(), err)

	// Verify the message is not received (timeout expected)
	select {
	case receivedMessage = <-messageReceived:
		assert.Fail(suite.T(), "Received message after unsubscribe: %s", receivedMessage.Message)
	case <-time.After(1 * time.Second):
		// Expected timeout - no message should be received
	}
}

// TestPatternSubscribeWithGlideClient tests message pattern matching with PSUBSCRIBE
func (suite *PubSubTestSuite) TestPatternSubscribeWithGlideClient() {
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create a subscriber client with pattern subscription
	messageReceived := make(chan *api.PubSubMessage, 10)
	var wg sync.WaitGroup
	wg.Add(2) // Expect 2 messages

	callback := func(message *api.PubSubMessage, ctx any) {
		messageReceived <- message
		wg.Done()
	}

	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.PatternChannelMode, "test-pattern-*").
		WithCallback(callback, nil)

	suite.createClientWithSubscriptions("subscriber", subscriptionConfig)

	// Give some time for subscription to be established
	time.Sleep(100 * time.Millisecond)

	// Publish messages to channels matching the pattern
	_, err := publisher.Publish("test-pattern-1", "message to pattern 1")
	assert.Nil(suite.T(), err)

	_, err = publisher.Publish("test-pattern-2", "message to pattern 2")
	assert.Nil(suite.T(), err)

	// Publish a message to a non-matching channel
	_, err = publisher.Publish("other-channel", "should not receive")
	assert.Nil(suite.T(), err)

	// Wait for the expected messages or timeout
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		// Expected - received both messages
	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Timed out waiting for pattern messages")
	}

	// Collect and verify received messages
	close(messageReceived)
	receivedMessages := make(map[string]string)

	for message := range messageReceived {
		receivedMessages[message.Channel] = message.Message
		assert.False(suite.T(), message.Pattern.IsNil())
		assert.Equal(suite.T(), "test-pattern-*", message.Pattern.Value())
	}

	assert.Equal(suite.T(), 2, len(receivedMessages))
	assert.Equal(suite.T(), "message to pattern 1", receivedMessages["test-pattern-1"])
	assert.Equal(suite.T(), "message to pattern 2", receivedMessages["test-pattern-2"])
}
