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
func (suite *GlideTestSuite) TestPubSub_Basic_WithCallback() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil)
		// Create a subscriber client with subscription configuration
		messageReceived := make(chan *api.PubSubMessage, 1)

		// Create a client with a subscription and a callback
		channels := []ChannelDefn{
			{Channel: "test-channel", Mode: ExactMode},
		}
		suite.CreatePubSubReceiver(clientType, channels, 1, true)
		// Give some time for subscription to be established
		time.Sleep(100 * time.Millisecond)

		// Publish a message
		testMessage := "hello world"
		result, err := publisher.Publish("test-channel", testMessage)

		assert.Nil(suite.T(), err)
		assert.NotNil(suite.T(), result)

		// Wait for the message to be received
		var receivedMessage *api.PubSubMessage
		select {
		case receivedMessage = <-messageReceived:
		case <-time.After(15 * time.Second):
			assert.Fail(suite.T(), "Timed out waiting for message")
		}

		// Verify the message content
		if receivedMessage != nil {
			assert.Equal(suite.T(), testMessage, receivedMessage.Message)
			assert.Equal(suite.T(), "test-channel", receivedMessage.Channel)
			assert.True(suite.T(), receivedMessage.Pattern.IsNil())
		}
	})
}

// TestMultipleSubscribersWithGlideClient tests message delivery to multiple subscribers
func (suite *GlideTestSuite) TestPubSub_Basic_MultipleSubscribers() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil) // Create channels for message tracking
		messages1 := make(chan *api.PubSubMessage, 10)
		messages2 := make(chan *api.PubSubMessage, 10)

		// Subscribe to different channels
		channels := []ChannelDefn{
			{Channel: "test-channel-multi", Mode: ExactMode},
		}
		suite.CreatePubSubReceiver(clientType, channels, 1, true)
		suite.CreatePubSubReceiver(clientType, channels, 2, true)

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
	})
}

// TestPatternSubscribeWithGlideClient tests message pattern matching with PSUBSCRIBE
func (suite *GlideTestSuite) TestPubSub_Basic_PatternSubscription() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil) // Create a subscriber client with pattern subscription
		messageReceived := make(chan *api.PubSubMessage, 10)
		var wg sync.WaitGroup
		wg.Add(2) // Expect 2 messages

		channels := []ChannelDefn{
			{Channel: "test-pattern-*", Mode: PatternMode},
		}
		suite.CreatePubSubReceiver(clientType, channels, 1, true)

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
	})
}

// TestPubSub_Basic_WithQueue tests basic publish and subscribe functionality using the message queue
// instead of a callback function for receiving messages
func (suite *GlideTestSuite) TestPubSub_Basic_WithQueue() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil) // Create a subscriber client without a callback (will use queue instead)
		channels := []ChannelDefn{
			{Channel: "test-queue-channel", Mode: ExactMode},
		}

		// Create a receiver without a callback (nil callback will use the queue)
		receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false)

		// Give some time for subscription to be established
		time.Sleep(100 * time.Millisecond)

		// Get message handler directly using the getMessageHandler method
		queue, err := receiver.GetQueue()
		assert.Nil(suite.T(), err)

		// Set up a signal channel to be notified of new messages
		signalCh := make(chan struct{}, 1)
		queue.RegisterSignalChannel(signalCh)
		defer queue.UnregisterSignalChannel(signalCh)

		// Publish a message
		testMessage := "hello queue world"
		result, err := publisher.Publish("test-queue-channel", testMessage)

		assert.Nil(suite.T(), err)
		assert.NotNil(suite.T(), result)

		// Wait for the message to arrive using WaitForMessage method
		var receivedMessage *api.PubSubMessage

		// Set up timeout
		timeoutDuration := 5 * time.Second
		timeout := time.After(timeoutDuration)

		// Wait for a message to arrive in the queue
		select {
		case <-signalCh:
			// A message was pushed to the queue, try to pop it
			receivedMessage = queue.Pop()
		case <-timeout:
			assert.Fail(suite.T(), "Timed out waiting for message in queue")
		}

		// Alternative method using WaitForMessage (this creates a channel specifically for this message)
		if receivedMessage == nil {
			// If we didn't get a message via signalCh, try WaitForMessage
			select {
			case receivedMessage = <-queue.WaitForMessage():
				// Successfully received a message
			case <-time.After(timeoutDuration):
				assert.Fail(suite.T(), "Timed out waiting for message via WaitForMessage")
			}
		}

		// Verify the message content
		if receivedMessage != nil {
			assert.Equal(suite.T(), testMessage, receivedMessage.Message)
			assert.Equal(suite.T(), "test-queue-channel", receivedMessage.Channel)
			assert.True(suite.T(), receivedMessage.Pattern.IsNil())
		}
	})
}
