// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func defaultResponseResolver(response any) (any, error) {
	return response, nil
}

func TestPubSubPushKind(t *testing.T) {

	var kind PushKind = Message

	assert.Equal(t, "Message", kind.String())
}

func TestCreatePubSubSubscription(t *testing.T) {
	subConfig := NewStandaloneSubscriptionConfig().
		WithSubscription(ExactChannelMode, "testChannel")

	// Register a message handler with a callback
	messageReceived := false
	callback := func(msg *PubSubMessage, context any) {
		messageReceived = true
		assert.Equal(t, "testMessage", msg.Message)
		assert.Equal(t, "testChannel", msg.Channel)
	}
	subConfig.WithCallback(callback, nil)

	address := NodeAddress{}
	clientConfig := NewGlideClientConfiguration().
		WithAddress(&address).
		WithSubscriptionConfig(subConfig)

	client1, err := NewGlideClient(clientConfig)
	assert.NoError(t, err)
	assert.NotNil(t, client1)

	// Create second client for publishing
	clientConfigNoSub := NewGlideClientConfiguration().
		WithAddress(&address)
	client2, err := NewGlideClient(clientConfigNoSub)
	assert.NoError(t, err)

	// Give subscription time to set up
	time.Sleep(100 * time.Millisecond)

	// Publish test message
	t.Log("Publishing message...")
	clientCount, err := client2.Publish("testChannel", "testMessage")
	assert.NoError(t, err)
	t.Logf("Published message to %d channels", clientCount)
	// Wait for message with timeout
	deadline := time.Now().Add(2 * time.Second)
	for !messageReceived && time.Now().Before(deadline) {
		time.Sleep(50 * time.Millisecond)
	}

	assert.True(t, messageReceived, "Message callback should have been invoked")
}
