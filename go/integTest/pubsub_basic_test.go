// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

// TestPubSub_Patterns tests all combinations of client types and message reading methods
func (suite *GlideTestSuite) TestPubSub_Basic_ChannelSubscription() {
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

			var receiver api.BaseClient
			queues := make(map[int]*api.PubSubMessageQueue)
			if !tt.useCallback {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
				queue, err := receiver.GetQueue()
				assert.Nil(t, err)
				queues[1] = queue
			} else {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(100 * time.Millisecond)

			// Publish test message
			_, err := publisher.Publish(tt.channelName, tt.messageContent)
			assert.Nil(t, err)

			// Allow time for the message to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up
			if receiver != nil {
				receiver.Close()
			}
		})
	}
}

// TestPubSub_Basic_MultipleSubscribers tests message delivery to multiple subscribers
func (suite *GlideTestSuite) TestPubSub_Basic_MultipleSubscribers() {
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
			channelName:    "test-multi-callback-channel",
			messageContent: "test multi callback message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			channelName:    "test-multi-callback-channel",
			messageContent: "test multi callback message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
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

			// Create multiple subscribers
			const numSubscribers = 3
			queues := make(map[int]*api.PubSubMessageQueue)
			subscribers := make([]api.BaseClient, numSubscribers)

			for i := 0; i < numSubscribers; i++ {
				if !tt.useCallback {
					receiver := suite.CreatePubSubReceiver(tt.clientType, channels, i+1, false)
					subscribers[i] = receiver
					queue, err := receiver.GetQueue()
					assert.Nil(t, err)
					queues[i+1] = queue
				} else {
					receiver := suite.CreatePubSubReceiver(tt.clientType, channels, i+1, true)
					subscribers[i] = receiver
				}
			}

			// Allow subscriptions to establish
			time.Sleep(100 * time.Millisecond)

			// Publish test message
			_, err := publisher.Publish(tt.channelName, tt.messageContent)
			assert.Nil(t, err)

			// Allow time for the message to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up subscribers
			for _, subscriber := range subscribers {
				subscriber.Close()
			}
		})
	}
}

// TestPubSub_Basic_PatternSubscription tests message pattern matching with PSUBSCRIBE
func (suite *GlideTestSuite) TestPubSub_Basic_PatternSubscription() {
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		pattern        string
		channels       []string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2"},
			messageContent: "test pattern message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			publisher := suite.createAnyClient(tt.clientType, nil)

			channels := []ChannelDefn{
				{Channel: tt.pattern, Mode: PatternMode},
			}
			expectedMessages := make(map[string]string)
			expectedMessages[tt.pattern] = tt.messageContent

			var receiver api.BaseClient
			queues := make(map[int]*api.PubSubMessageQueue)
			if !tt.useCallback {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
				queue, err := receiver.GetQueue()
				assert.Nil(t, err)
				queues[1] = queue
			} else {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(100 * time.Millisecond)

			// Publish test messages to matching channels
			for _, channel := range tt.channels {
				_, err := publisher.Publish(channel, tt.messageContent)
				assert.Nil(t, err)
			}

			// Publish a message to a non-matching channel
			_, err := publisher.Publish("other-channel", "should not receive")
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up
			if receiver != nil {
				receiver.Close()
			}
		})
	}
}

// TestPubSub_Basic_ManyChannels tests a single subscriber subscribing to multiple channels
func (suite *GlideTestSuite) TestPubSub_Basic_ManyChannels() {
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		channelNames   []string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			channelNames:   []string{"test-channel-2", "test-channel-1", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			publisher := suite.createAnyClient(tt.clientType, nil)

			// Create channel definitions for all channels
			channels := make([]ChannelDefn, len(tt.channelNames))
			for i, channelName := range tt.channelNames {
				channels[i] = ChannelDefn{Channel: channelName, Mode: ExactMode}
			}

			// Create expected messages map
			expectedMessages := make(map[string]string)
			for _, channelName := range tt.channelNames {
				expectedMessages[channelName] = tt.messageContent
			}

			var receiver api.BaseClient
			queues := make(map[int]*api.PubSubMessageQueue)
			if !tt.useCallback {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
				queue, err := receiver.GetQueue()
				assert.Nil(t, err)
				queues[1] = queue
			} else {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(100 * time.Millisecond)

			// TODO: For SignalChannel Async tests this will result in all of the messages getting stacked in the queue
			// before the SignalChannel is registered in `verifyPubSubMessages`. We have logic to ensure that a signal
			// handles all messages currently in the queue, but we should create a custom test that registers the SignalChannel
			// and then publishes the messages to the channels so that we can test multiple messages arriving over time and triggering
			// the signal handler multiple times..
			for _, channelName := range tt.channelNames {
				_, err := publisher.Publish(channelName, tt.messageContent)
				assert.Nil(t, err)
			}

			// Allow time for the messages to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up
			if receiver != nil {
				receiver.Close()
			}
		})
	}
}

// TestPubSub_Basic_PatternManyChannels tests pattern subscriptions with multiple channels
func (suite *GlideTestSuite) TestPubSub_Basic_PatternManyChannels() {
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		pattern        string
		channels       []string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			pattern:        "test-pattern-*",
			channels:       []string{"test-pattern-1", "test-pattern-2", "test-pattern-3"},
			messageContent: "test pattern message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			publisher := suite.createAnyClient(tt.clientType, nil)

			// Create channel definitions for pattern subscription
			channels := []ChannelDefn{
				{Channel: tt.pattern, Mode: PatternMode},
			}

			// Create expected messages map
			expectedMessages := make(map[string]string)
			expectedMessages[tt.pattern] = tt.messageContent

			var receiver api.BaseClient
			queues := make(map[int]*api.PubSubMessageQueue)
			if !tt.useCallback {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
				queue, err := receiver.GetQueue()
				assert.Nil(t, err)
				queues[1] = queue
			} else {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(100 * time.Millisecond)

			// Publish test messages to matching channels
			for _, channel := range tt.channels {
				_, err := publisher.Publish(channel, tt.messageContent)
				assert.Nil(t, err)
			}

			// Publish a message to a non-matching channel
			_, err := publisher.Publish("other-channel", "should not receive")
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up
			if receiver != nil {
				receiver.Close()
			}
		})
	}
}

// TestPubSub_Basic_CombinedExactPattern tests a single subscriber with both exact and pattern subscriptions
func (suite *GlideTestSuite) TestPubSub_Basic_CombinedExactPattern() {
	tests := []struct {
		name            string
		clientType      ClientType
		readMethod      MessageReadMethod
		useCallback     bool
		exactChannel    string
		pattern         string
		patternChannels []string
		messageContent  string
	}{
		{
			name:            "Standalone with Callback",
			clientType:      GlideClient,
			readMethod:      CallbackMethod,
			useCallback:     true,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Standalone with WaitForMessage",
			clientType:      GlideClient,
			readMethod:      WaitForMessageMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Standalone with SignalChannel",
			clientType:      GlideClient,
			readMethod:      SignalChannelMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Standalone with SyncLoop",
			clientType:      GlideClient,
			readMethod:      SyncLoopMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with Callback",
			clientType:      GlideClusterClient,
			readMethod:      CallbackMethod,
			useCallback:     true,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with WaitForMessage",
			clientType:      GlideClusterClient,
			readMethod:      WaitForMessageMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with SignalChannel",
			clientType:      GlideClusterClient,
			readMethod:      SignalChannelMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with SyncLoop",
			clientType:      GlideClusterClient,
			readMethod:      SyncLoopMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			publisher := suite.createAnyClient(tt.clientType, nil)

			// Create channel definitions for both exact and pattern subscriptions
			channels := []ChannelDefn{
				{Channel: tt.exactChannel, Mode: ExactMode},
				{Channel: tt.pattern, Mode: PatternMode},
			}

			// Create expected messages map
			expectedMessages := make(map[string]string)
			expectedMessages[tt.exactChannel] = tt.messageContent
			expectedMessages[tt.pattern] = tt.messageContent

			var receiver api.BaseClient
			queues := make(map[int]*api.PubSubMessageQueue)
			if !tt.useCallback {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
				queue, err := receiver.GetQueue()
				assert.Nil(t, err)
				queues[1] = queue
			} else {
				receiver = suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(100 * time.Millisecond)

			// Publish to exact channel
			_, err := publisher.Publish(tt.exactChannel, tt.messageContent)
			assert.Nil(t, err)

			// Publish to pattern-matching channels
			for _, channel := range tt.patternChannels {
				_, err := publisher.Publish(channel, tt.messageContent)
				assert.Nil(t, err)
			}

			// Publish to a non-matching channel
			_, err = publisher.Publish("other-channel", "should not receive")
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up
			if receiver != nil {
				receiver.Close()
			}
		})
	}
}

// TestPubSub_Basic_CombinedExactPatternMultipleSubscribers tests multiple subscribers with both exact and pattern subscriptions
func (suite *GlideTestSuite) TestPubSub_Basic_CombinedExactPatternMultipleSubscribers() {
	tests := []struct {
		name            string
		clientType      ClientType
		readMethod      MessageReadMethod
		useCallback     bool
		exactChannel    string
		pattern         string
		patternChannels []string
		messageContent  string
	}{
		{
			name:            "Standalone with Callback",
			clientType:      GlideClient,
			readMethod:      CallbackMethod,
			useCallback:     true,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Standalone with WaitForMessage",
			clientType:      GlideClient,
			readMethod:      WaitForMessageMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Standalone with SignalChannel",
			clientType:      GlideClient,
			readMethod:      SignalChannelMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Standalone with SyncLoop",
			clientType:      GlideClient,
			readMethod:      SyncLoopMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with Callback",
			clientType:      GlideClusterClient,
			readMethod:      CallbackMethod,
			useCallback:     true,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with WaitForMessage",
			clientType:      GlideClusterClient,
			readMethod:      WaitForMessageMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with SignalChannel",
			clientType:      GlideClusterClient,
			readMethod:      SignalChannelMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
		{
			name:            "Cluster with SyncLoop",
			clientType:      GlideClusterClient,
			readMethod:      SyncLoopMethod,
			useCallback:     false,
			exactChannel:    "test-exact-channel",
			pattern:         "test-pattern-*",
			patternChannels: []string{"test-pattern-1", "test-pattern-2"},
			messageContent:  "test message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			publisher := suite.createAnyClient(tt.clientType, nil)

			// Create channel definitions for both exact and pattern subscriptions
			channels := []ChannelDefn{
				{Channel: tt.exactChannel, Mode: ExactMode},
				{Channel: tt.pattern, Mode: PatternMode},
			}

			// Create expected messages map
			expectedMessages := make(map[string]string)
			expectedMessages[tt.exactChannel] = tt.messageContent
			expectedMessages[tt.pattern] = tt.messageContent

			// Create multiple subscribers
			const numSubscribers = 3
			queues := make(map[int]*api.PubSubMessageQueue)
			subscribers := make([]api.BaseClient, numSubscribers)

			for i := 0; i < numSubscribers; i++ {
				if !tt.useCallback {
					receiver := suite.CreatePubSubReceiver(tt.clientType, channels, i+1, false)
					subscribers[i] = receiver
					queue, err := receiver.GetQueue()
					assert.Nil(t, err)
					queues[i+1] = queue
				} else {
					receiver := suite.CreatePubSubReceiver(tt.clientType, channels, i+1, true)
					subscribers[i] = receiver
				}
			}

			// Allow subscriptions to establish
			time.Sleep(100 * time.Millisecond)

			// Publish to exact channel
			_, err := publisher.Publish(tt.exactChannel, tt.messageContent)
			assert.Nil(t, err)

			// Publish to pattern-matching channels
			for _, channel := range tt.patternChannels {
				_, err := publisher.Publish(channel, tt.messageContent)
				assert.Nil(t, err)
			}

			// Publish to a non-matching channel
			_, err = publisher.Publish("other-channel", "should not receive")
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(100 * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)

			// Clean up subscribers
			for _, subscriber := range subscribers {
				subscriber.Close()
			}
		})
	}
}
