// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
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
	result, err := publisher.CustomCommand([]string{"PUBLISH", "test-channel", testMessage})
	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), result)

	// Wait for the message to be received
	var receivedMessage *api.PubSubMessage
	select {
	case receivedMessage = <-messageReceived:
	case <-time.After(2 * time.Second):
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
	// TODO: Implement this test
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
	_, err := publisher.CustomCommand([]string{"PUBLISH", "test-channel-unsub", testMessage})
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
	_, err = publisher.CustomCommand([]string{"PUBLISH", "test-channel-unsub", testMessage2})
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
	_, err := publisher.CustomCommand([]string{"PUBLISH", "test-pattern-1", "message to pattern 1"})
	assert.Nil(suite.T(), err)

	_, err = publisher.CustomCommand([]string{"PUBLISH", "test-pattern-2", "message to pattern 2"})
	assert.Nil(suite.T(), err)

	// Publish a message to a non-matching channel
	_, err = publisher.CustomCommand([]string{"PUBLISH", "other-channel", "should not receive"})
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
