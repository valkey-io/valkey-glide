// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

// TestSubscriptionReconnection verifies that subscriptions automatically reconnect
// and continue receiving messages after temporary network interruptions
func (suite *PubSubTestSuite) TestSubscriptionReconnection() {
	suite.T().Skip("Skipping reconnection test pending research on Java PubSub implementation behavior")

	// Create message tracking channels
	messageReceived := make(chan *api.PubSubMessage, 10)

	callback := func(message *api.PubSubMessage, context any) {
		messageReceived <- message
	}

	subscriptionConfig := api.NewStandaloneSubscriptionConfig().
		WithSubscription(api.ExactChannelMode, "test-reconnect-channel").
		WithCallback(callback, nil)

	subscriber := suite.createClientWithSubscriptions("subscriber", subscriptionConfig)
	assert.NotNil(suite.T(), subscriber)

	// Create a publisher client
	publisher := suite.createDefaultClient("publisher")
	assert.NotNil(suite.T(), publisher)

	// Allow time for subscription to be established
	time.Sleep(100 * time.Millisecond)

	// Test message delivery before network interruption
	_, err := publisher.CustomCommand([]string{"PUBLISH", "test-reconnect-channel", "before disconnect"})
	assert.NoError(suite.T(), err)

	select {
	case msg := <-messageReceived:
		assert.Equal(suite.T(), "before disconnect", msg.Message)
	case <-time.After(2 * time.Second):
		assert.Fail(suite.T(), "Message should be received before network interruption")
	}

	// Simulate network interruption by stopping and restarting the server
	pubsubRunClusterManager(suite, []string{"stop", "--prefix", "cluster"}, true)
	time.Sleep(1 * time.Second) // Allow time for disconnect

	pubsubRunClusterManager(suite, []string{"start", "-r", "3"}, false)
	time.Sleep(2 * time.Second) // Allow time for reconnection

	// Verify subscription recovers after reconnection
	_, err = publisher.CustomCommand([]string{"PUBLISH", "test-reconnect-channel", "after reconnect"})
	assert.NoError(suite.T(), err)

	select {
	case msg := <-messageReceived:
		assert.Equal(suite.T(), "after reconnect", msg.Message)
	case <-time.After(5 * time.Second):
		assert.Fail(suite.T(), "Message should be received after reconnection")
	}
}
