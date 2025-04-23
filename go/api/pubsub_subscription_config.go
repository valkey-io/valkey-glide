// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/protobuf"
)

// *** BaseSubscriptionConfig ***
type MessageCallback func(message *PubSubMessage, ctx any)

type BaseSubscriptionConfig struct {
	callback      MessageCallback
	context       any
	subscriptions map[uint32][]string
}

func NewBaseSubscriptionConfig() *BaseSubscriptionConfig {
	return &BaseSubscriptionConfig{}
}

func (config *BaseSubscriptionConfig) toProtobuf() *protobuf.PubSubSubscriptions {
	request := protobuf.PubSubSubscriptions{
		ChannelsOrPatternsByType: make(map[uint32]*protobuf.PubSubChannelsOrPatterns),
	}

	if config.subscriptions != nil {
		for mode, channelsSlice := range config.subscriptions {

			channels := make([][]byte, len(channelsSlice))
			for idx, channel := range channelsSlice {
				channels[idx] = []byte(channel)
			}

			request.ChannelsOrPatternsByType[mode] = &protobuf.PubSubChannelsOrPatterns{
				ChannelsOrPatterns: channels,
			}
		}
	}
	return &request
}

// *** StandaloneSubscriptionConfig ***

type PubSubChannelMode int

const (
	ExactChannelMode PubSubChannelMode = iota
	PatternChannelMode
)

func (mode PubSubChannelMode) String() string {
	return [...]string{"EXACT", "PATTERN"}[mode]
}

type StandaloneSubscriptionConfig struct {
	*BaseSubscriptionConfig
}

func NewStandaloneSubscriptionConfig() *StandaloneSubscriptionConfig {
	return &StandaloneSubscriptionConfig{
		BaseSubscriptionConfig: NewBaseSubscriptionConfig(),
	}
}

func (config *StandaloneSubscriptionConfig) WithCallback(callback MessageCallback, context any) *StandaloneSubscriptionConfig {
	config.callback = callback
	config.context = context
	return config
}

func (config *StandaloneSubscriptionConfig) WithSubscription(
	mode PubSubChannelMode,
	channelOrPattern string,
) *StandaloneSubscriptionConfig {
	if config.subscriptions == nil {
		config.subscriptions = make(map[uint32][]string)
	}
	modeKey := uint32(mode)
	channels := config.subscriptions[modeKey]

	newValue := append(channels, channelOrPattern)
	config.subscriptions[modeKey] = newValue

	return config
}

// *** ClusterSubscriptionConfig ***

type PubSubClusterChannelMode int

const (
	ExactClusterChannelMode PubSubClusterChannelMode = iota
	PatternClusterChannelMode
	ShardedClusterChannelMode
)

func (mode PubSubClusterChannelMode) String() string {
	return [...]string{"EXACT", "PATTERN", "SHARDED"}[mode]
}

type ClusterSubscriptionConfig struct {
	*BaseSubscriptionConfig
}

func NewClusterSubscriptionConfig() *ClusterSubscriptionConfig {
	return &ClusterSubscriptionConfig{
		BaseSubscriptionConfig: NewBaseSubscriptionConfig(),
	}
}

func (config *ClusterSubscriptionConfig) WithCallback(callback MessageCallback, context any) *ClusterSubscriptionConfig {
	config.callback = callback
	config.context = context
	return config
}

func (config *ClusterSubscriptionConfig) WithSubscription(
	mode PubSubClusterChannelMode,
	channelOrPattern string,
) *ClusterSubscriptionConfig {
	if config.subscriptions == nil {
		config.subscriptions = make(map[uint32][]string)
	}
	modeKey := uint32(mode)
	channels := config.subscriptions[modeKey]

	config.subscriptions[modeKey] = append(channels, channelOrPattern)
	return config
}
