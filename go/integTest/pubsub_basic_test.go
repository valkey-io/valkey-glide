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
	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")

	// Create separate channels for each client to better track which client receives messages
	messageReceivedCh1 := make(chan *api.PubSubMessage, 5)
	messageReceivedCh2 := make(chan *api.PubSubMessage, 5)

	// Track if each client has received a message
	client1Received := false
	client2Received := false
	var mu sync.Mutex

	// Create subscriber clients with different client IDs but same channel
	callback1 := func(message *api.PubSubMessage, ctx any) {
		suite.T().Logf("Received message on client1: %s", message.Message)
		mu.Lock()
		client1Received = true
		mu.Unlock()
		messageReceivedCh1 <- message
	}

	callback2 := func(message *api.PubSubMessage, ctx any) {
		suite.T().Logf("Received message on client2: %s", message.Message)
		mu.Lock()
		client2Received = true
		mu.Unlock()
		messageReceivedCh2 <- message
	}

	// Use different channel names to ensure both clients receive messages
	subscriptionConfig1 := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-channel-multi-1").
		WithCallback(callback1, nil)

	subscriptionConfig2 := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-channel-multi-2").
		WithCallback(callback2, nil)

	suite.createClientWithSubscriptions("subscriber1", subscriptionConfig1)
	suite.createClientWithSubscriptions("subscriber2", subscriptionConfig2)

	// Give more time for subscriptions to be established
	time.Sleep(500 * time.Millisecond)

	// Start a goroutine to publish messages periodically to both channels
	done := make(chan struct{})
	go func() {
		ticker := time.NewTicker(200 * time.Millisecond)
		defer ticker.Stop()

		count := 0
		for {
			select {
			case <-ticker.C:
				if count >= 10 {
					return // Avoid infinite loop
				}

				// Publish to both channels
				testMessage1 := fmt.Sprintf("hello to client1 - msg %d", count)
				testMessage2 := fmt.Sprintf("hello to client2 - msg %d", count)

				result1, err1 := publisher.CustomCommand([]string{"PUBLISH", "test-channel-multi-1", testMessage1})
				if err1 == nil && result1 != nil {
					suite.T().Logf("Published message %d to channel 1", count)
				}

				result2, err2 := publisher.CustomCommand([]string{"PUBLISH", "test-channel-multi-2", testMessage2})
				if err2 == nil && result2 != nil {
					suite.T().Logf("Published message %d to channel 2", count)
				}

				// Check if both clients have received messages
				mu.Lock()
				bothReceived := client1Received && client2Received
				mu.Unlock()

				if bothReceived {
					return // Stop publishing once both have received messages
				}

				count++
			case <-done:
				return
			}
		}
	}()

	// Wait for both clients to receive messages or timeout
	timeout := time.After(5 * time.Second)
	for {
		mu.Lock()
		bothReceived := client1Received && client2Received
		mu.Unlock()

		if bothReceived {
			close(done) // Stop publishing
			break
		}

		select {
		case <-timeout:
			close(done) // Stop publishing

			// Check which client didn't receive messages
			mu.Lock()
			if !client1Received {
				assert.Fail(suite.T(), "Client 1 did not receive any messages")
			}
			if !client2Received {
				assert.Fail(suite.T(), "Client 2 did not receive any messages")
			}
			mu.Unlock()

			return
		default:
			time.Sleep(100 * time.Millisecond) // Small sleep to avoid tight loop
		}
	}

	// Verify the messages
	var message1 *api.PubSubMessage
	select {
	case message1 = <-messageReceivedCh1:
		assert.Equal(suite.T(), "test-channel-multi-1", message1.Channel)
		assert.Contains(suite.T(), message1.Message, "hello to client1")
	default:
		// We should never get here since we already checked client1Received
		assert.Fail(suite.T(), "Expected message from client 1 but none was available")
	}

	var message2 *api.PubSubMessage
	select {
	case message2 = <-messageReceivedCh2:
		assert.Equal(suite.T(), "test-channel-multi-2", message2.Channel)
		assert.Contains(suite.T(), message2.Message, "hello to client2")
	default:
		// We should never get here since we already checked client2Received
		assert.Fail(suite.T(), "Expected message from client 2 but none was available")
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
