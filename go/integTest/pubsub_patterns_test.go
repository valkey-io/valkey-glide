// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

// TestPubSub_Patterns tests all combinations of client types and message reading methods
func (suite *GlideTestSuite) TestPubSub_Patterns() {
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		channelName    string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			channelName:    "test-callback-channel",
			messageContent: "test callback message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			channelName:    "test-callback-channel",
			messageContent: "test callback message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			publisher := suite.createAnyClient(tt.clientType, nil)

			channels := []ChannelDefn{
				{Channel: tt.channelName, Mode: ExactMode},
			}
			expectedMessages := map[string]string{
				tt.channelName: tt.messageContent,
			}

			var queue *api.PubSubMessageQueue
			if !tt.useCallback {
				receiver := suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
				var err error
				queue, err = receiver.GetQueue()
				assert.Nil(t, err)
			} else {
				suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(100 * time.Millisecond)

			// Publish test message
			_, err := publisher.Publish(tt.channelName, tt.messageContent)
			assert.Nil(t, err)

			// Allow time for the message to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyReceivedPubsubMessages(expectedMessages, queue, tt.readMethod)
		})
	}
}
