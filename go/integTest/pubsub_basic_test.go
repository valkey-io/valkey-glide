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
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		sharded        bool
		channelName    string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        false,
			channelName:    "test-callback-channel",
			messageContent: "test callback message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        false,
			channelName:    "test-callback-channel",
			messageContent: "test callback message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with Callback Sharded",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        true,
			channelName:    "test-callback-channel",
			messageContent: "test callback message",
		},
		{
			name:           "Cluster with WaitForMessage Sharded",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        true,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SignalChannel Sharded",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        true,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
		{
			name:           "Cluster with SyncLoop Sharded",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        true,
			channelName:    "test-pattern-channel",
			messageContent: "test pattern message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			if tt.sharded {
				suite.SkipIfServerVersionLowerThanBy("7.0.0", t)
			}
			publisher := suite.createAnyClient(tt.clientType, nil)

			channels := []ChannelDefn{
				{Channel: tt.channelName, Mode: getChannelMode(tt.sharded)},
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
				suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish test message
			var err error
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish(tt.channelName, tt.messageContent, tt.sharded)
			} else {
				_, err = publisher.(*api.GlideClient).Publish(tt.channelName, tt.messageContent)
			}
			assert.Nil(t, err)

			// Allow time for the message to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}

// TestPubSub_Basic_MultipleSubscribers tests message delivery to multiple subscribers
func (suite *GlideTestSuite) TestPubSub_Basic_MultipleSubscribers() {
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		sharded        bool
		channelName    string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        false,
			channelName:    "test-multi-callback-channel",
			messageContent: "test multi callback message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        false,
			channelName:    "test-multi-callback-channel",
			messageContent: "test multi callback message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        false,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with Callback Sharded",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        true,
			channelName:    "test-multi-callback-channel",
			messageContent: "test multi callback message",
		},
		{
			name:           "Cluster with WaitForMessage Sharded",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        true,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with SignalChannel Sharded",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        true,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
		{
			name:           "Cluster with SyncLoop Sharded",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        true,
			channelName:    "test-multi-pattern-channel",
			messageContent: "test multi pattern message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			if tt.sharded {
				suite.SkipIfServerVersionLowerThanBy("7.0.0", t)
			}
			publisher := suite.createAnyClient(tt.clientType, nil)

			channels := []ChannelDefn{
				{Channel: tt.channelName, Mode: getChannelMode(tt.sharded)},
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
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish test message
			var err error
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish(tt.channelName, tt.messageContent, tt.sharded)
			} else {
				_, err = publisher.(*api.GlideClient).Publish(tt.channelName, tt.messageContent)
			}
			assert.Nil(t, err)

			// Allow time for the message to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}

// TestPubSub_Basic_PatternSubscription tests message pattern matching with PSUBSCRIBE
func (suite *GlideTestSuite) TestPubSub_Basic_PatternSubscription() {
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
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
				suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish test messages to matching channels
			for _, channel := range tt.channels {
				var err error
				if tt.clientType == GlideClusterClient {
					_, err = publisher.(*api.GlideClusterClient).Publish(channel, tt.messageContent, false)
				} else {
					_, err = publisher.(*api.GlideClient).Publish(channel, tt.messageContent)
				}
				assert.Nil(t, err)
			}

			// Publish a message to a non-matching channel
			var err error
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish("other-channel", "should not receive", false)
			} else {
				_, err = publisher.(*api.GlideClient).Publish("other-channel", "should not receive")
			}
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}

// TestPubSub_Basic_ManyChannels tests a single subscriber subscribing to multiple channels
func (suite *GlideTestSuite) TestPubSub_Basic_ManyChannels() {
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
	tests := []struct {
		name           string
		clientType     ClientType
		readMethod     MessageReadMethod
		useCallback    bool
		sharded        bool
		channelNames   []string
		messageContent string
	}{
		{
			name:           "Standalone with Callback",
			clientType:     GlideClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Standalone with WaitForMessage",
			clientType:     GlideClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Standalone with SignalChannel",
			clientType:     GlideClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        false,
			channelNames:   []string{"test-channel-2", "test-channel-1", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Standalone with SyncLoop",
			clientType:     GlideClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with Callback",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with WaitForMessage",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with SignalChannel",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with SyncLoop",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        false,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with Callback Sharded",
			clientType:     GlideClusterClient,
			readMethod:     CallbackMethod,
			useCallback:    true,
			sharded:        true,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with WaitForMessage Sharded",
			clientType:     GlideClusterClient,
			readMethod:     WaitForMessageMethod,
			useCallback:    false,
			sharded:        true,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with SignalChannel Sharded",
			clientType:     GlideClusterClient,
			readMethod:     SignalChannelMethod,
			useCallback:    false,
			sharded:        true,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
		{
			name:           "Cluster with SyncLoop Sharded",
			clientType:     GlideClusterClient,
			readMethod:     SyncLoopMethod,
			useCallback:    false,
			sharded:        true,
			channelNames:   []string{"test-channel-1", "test-channel-2", "test-channel-3"},
			messageContent: "test message",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			if tt.sharded {
				suite.SkipIfServerVersionLowerThanBy("7.0.0", t)
			}
			publisher := suite.createAnyClient(tt.clientType, nil)

			// Create channel definitions for all channels
			channels := make([]ChannelDefn, len(tt.channelNames))
			for i, channelName := range tt.channelNames {
				channels[i] = ChannelDefn{Channel: channelName, Mode: getChannelMode(tt.sharded)}
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
				suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			for _, channelName := range tt.channelNames {
				if tt.clientType == GlideClusterClient {
					_, err := publisher.(*api.GlideClusterClient).Publish(channelName, tt.messageContent, tt.sharded)
					assert.Nil(t, err)
				} else {
					_, err := publisher.(*api.GlideClient).Publish(channelName, tt.messageContent)
					assert.Nil(t, err)
				}
			}

			// Allow time for the messages to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}

// TestPubSub_Basic_PatternManyChannels tests pattern subscriptions with multiple channels
func (suite *GlideTestSuite) TestPubSub_Basic_PatternManyChannels() {
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
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
				suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish test messages to matching channels
			for _, channel := range tt.channels {
				var err error
				if tt.clientType == GlideClusterClient {
					_, err = publisher.(*api.GlideClusterClient).Publish(channel, tt.messageContent, false)
				} else {
					_, err = publisher.(*api.GlideClient).Publish(channel, tt.messageContent)
				}
				assert.Nil(t, err)
			}

			// Publish a message to a non-matching channel
			var err error
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish("other-channel", "should not receive", false)
			} else {
				_, err = publisher.(*api.GlideClient).Publish("other-channel", "should not receive")
			}
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}

// TestPubSub_Basic_CombinedExactPattern tests a single subscriber with both exact and pattern subscriptions
func (suite *GlideTestSuite) TestPubSub_Basic_CombinedExactPattern() {
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
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
				suite.CreatePubSubReceiver(tt.clientType, channels, 1, true)
			}

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish to exact channel
			var err error
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish(tt.exactChannel, tt.messageContent, false)
			} else {
				_, err = publisher.(*api.GlideClient).Publish(tt.exactChannel, tt.messageContent)
			}
			assert.Nil(t, err)

			// Publish to pattern-matching channels
			for _, channel := range tt.patternChannels {
				var err error
				if tt.clientType == GlideClusterClient {
					_, err = publisher.(*api.GlideClusterClient).Publish(channel, tt.messageContent, false)
				} else {
					_, err = publisher.(*api.GlideClient).Publish(channel, tt.messageContent)
				}
				assert.Nil(t, err)
			}

			// Publish to a non-matching channel
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish("other-channel", "should not receive", false)
			} else {
				_, err = publisher.(*api.GlideClient).Publish("other-channel", "should not receive")
			}
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}

// TestPubSub_Basic_CombinedExactPatternMultipleSubscribers tests multiple subscribers with both exact and pattern
// subscriptions
func (suite *GlideTestSuite) TestPubSub_Basic_CombinedExactPatternMultipleSubscribers() {
	if !*pubsubtest {
		suite.T().Skip("Pubsub tests are disabled")
	}
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
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Publish to exact channel
			var err error
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish(tt.exactChannel, tt.messageContent, false)
			} else {
				_, err = publisher.(*api.GlideClient).Publish(tt.exactChannel, tt.messageContent)
			}
			assert.Nil(t, err)

			// Publish to pattern-matching channels
			for _, channel := range tt.patternChannels {
				var err error
				if tt.clientType == GlideClusterClient {
					_, err = publisher.(*api.GlideClusterClient).Publish(channel, tt.messageContent, false)
				} else {
					_, err = publisher.(*api.GlideClient).Publish(channel, tt.messageContent)
				}
				assert.Nil(t, err)
			}

			// Publish to a non-matching channel
			if tt.clientType == GlideClusterClient {
				_, err = publisher.(*api.GlideClusterClient).Publish("other-channel", "should not receive", false)
			} else {
				_, err = publisher.(*api.GlideClient).Publish("other-channel", "should not receive")
			}
			assert.Nil(t, err)

			// Allow time for the messages to be received
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Verify using the verification function
			suite.verifyPubsubMessages(t, expectedMessages, queues, tt.readMethod)
		})
	}
}
