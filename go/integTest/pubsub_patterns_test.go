// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"time"

	"github.com/stretchr/testify/assert"
)

// TestPubSub_Pattern_WithCallback tests message receipt using callback pattern
func (suite *GlideTestSuite) TestPubSub_Pattern_WithCallback() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil) // Create message tracking channels

		channels := []ChannelDefn{
			{Channel: "test-callback-channel", Mode: ExactMode},
		}
		testMessage := "test callback message"
		expectedMessages := map[string]string{
			"test-callback-channel": testMessage,
		}

		suite.CreatePubSubReceiver(clientType, channels, 1, true)
		// Allow subscription to establish
		time.Sleep(100 * time.Millisecond)

		// Publish test message
		_, err := publisher.Publish("test-callback-channel", testMessage)
		assert.Nil(suite.T(), err)

		// Allow time for the message to be received
		time.Sleep(100 * time.Millisecond)

		suite.verifyReceivedPubsubMessages(expectedMessages, nil, CallbackMethod)
	})
}

// TestPubSub_Pattern_WithWaitForMessage tests message receipt using WaitForMessage pattern
func (suite *GlideTestSuite) TestPubSub_Pattern_WithWaitForMessage() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil)

		channels := []ChannelDefn{
			{Channel: "test-pattern-channel", Mode: ExactMode},
		}
		testMessage := "test pattern message"
		expectedMessages := map[string]string{
			"test-pattern-channel": testMessage,
		}

		receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false)

		// Get queue
		queue, err := receiver.GetQueue()
		assert.Nil(suite.T(), err)

		// Allow subscription to establish
		time.Sleep(100 * time.Millisecond)

		// Publish test message
		_, err = publisher.Publish("test-pattern-channel", testMessage)
		assert.Nil(suite.T(), err)

		// Allow time for the message to be received
		time.Sleep(100 * time.Millisecond)

		// Verify using the new verification function
		suite.verifyReceivedPubsubMessages(expectedMessages, queue, WaitForMessageMethod)
	})
}

// TestPubSub_Pattern_WithSignalChannel tests message receipt using signal channel pattern
func (suite *GlideTestSuite) TestPubSub_Pattern_WithSignalChannel() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil) // Create subscriber without callback

		channels := []ChannelDefn{
			{Channel: "test-pattern-channel", Mode: ExactMode},
		}
		testMessage := "test pattern message"
		expectedMessages := map[string]string{
			"test-pattern-channel": testMessage,
		}

		receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false)

		// Get queue
		queue, err := receiver.GetQueue()
		assert.Nil(suite.T(), err)

		// Allow subscription to establish
		time.Sleep(100 * time.Millisecond)

		// Publish test message
		_, err = publisher.Publish("test-pattern-channel", testMessage)
		assert.Nil(suite.T(), err)

		// Allow time for the message to be received
		time.Sleep(100 * time.Millisecond)

		// Verify using the new verification function
		suite.verifyReceivedPubsubMessages(expectedMessages, queue, SignalChannelMethod)
	})
}

// TestPubSub_Pattern_WithSyncLoop tests message receipt using synchronous polling pattern
func (suite *GlideTestSuite) TestPubSub_Pattern_WithSyncLoop() {
	suite.runWithPubSubClients(func(clientType ClientType) {
		publisher := suite.createAnyClient(clientType, nil)

		testMessage := "test pattern message"
		channels := []ChannelDefn{
			{Channel: "test-pattern-channel", Mode: ExactMode},
		}
		receiver := suite.CreatePubSubReceiver(clientType, channels, 1, false)

		// Get queue
		queue, err := receiver.GetQueue()
		assert.Nil(suite.T(), err)

		// Allow subscription to establish
		time.Sleep(100 * time.Millisecond)

		// Publish test message
		_, err = publisher.Publish("test-pattern-channel", testMessage)
		assert.Nil(suite.T(), err)

		// Verify using the new verification function
		expectedMessages := map[string]string{
			"test-pattern-channel": testMessage,
		}
		suite.verifyReceivedPubsubMessages(expectedMessages, queue, SyncLoopMethod)
	})
}
