// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestPubSubPushKind(t *testing.T) {

	var kind PushKind = Message

	assert.Equal(t, "Message", kind.String())
}

func TestCreatePubSubSubscription(t *testing.T) {
	subConfig := NewStandaloneSubscriptionConfig().
		WithSubscription(ExactChannelMode, "testChannel")
	address := NodeAddress{}
	clientConfig := NewGlideClientConfiguration().
		WithAddress(&address).
		WithSubscriptionConfig(subConfig)

	client1, err := NewGlideClient(clientConfig)
	assert.NoError(t, err)
	assert.NotNil(t, client1)

	clientConfigNoSub := NewGlideClientConfiguration().
		WithAddress(&address)

	client2, err := NewGlideClient(clientConfigNoSub)
	assert.NoError(t, err)

	client2.Publish("testChannel", "testMessage")

}
