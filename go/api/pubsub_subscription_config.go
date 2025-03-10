// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"errors"
	"sync"
)

var (
	ErrPubSubConfigInvalid = errors.New("PubSub subscriptions with a context requires a callback function to be configured")
)

// *** BaseSubscriptionConfig ***

type ChannelMode interface {
	String() string
	Command() string
	UnsubscribeCommand() string
}

type MessageCallback func(message *PubSubMessage, ctx any)

type BaseSubscriptionConfig struct {
	callback MessageCallback
	context  any
}

func NewBaseSubscriptionConfig() *BaseSubscriptionConfig {
	return &BaseSubscriptionConfig{}
}

func (config *BaseSubscriptionConfig) WithCallback(callback MessageCallback, context any) *BaseSubscriptionConfig {
	config.callback = callback
	config.context = context
	return config
}

func (config *BaseSubscriptionConfig) SetCallback(callback MessageCallback) *BaseSubscriptionConfig {
	config.callback = callback
	return config
}

func (config *BaseSubscriptionConfig) GetCallback(callback MessageCallback) MessageCallback {
	return config.callback
}

func (config *BaseSubscriptionConfig) Validate() error {
	if config.context != nil && config.callback == nil {
		return ErrPubSubConfigInvalid
	}
	return nil
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

func (mode PubSubChannelMode) Command() string {
	return [...]string{"SUBSCRIBE", "PSUBSCRIBE"}[mode]
}

func (mode PubSubChannelMode) UnsubscribeCommand() string {
	return [...]string{"UNSUBSCRIBE", "PUNSUBSCRIBE"}[mode]
}

type StandaloneSubscriptionConfig struct {
	*BaseSubscriptionConfig
	subscriptions sync.Map // map[PubSubChannelMode]map[string]string
}

func NewStandaloneSubscriptionConfig() *StandaloneSubscriptionConfig {
	return &StandaloneSubscriptionConfig{
		BaseSubscriptionConfig: NewBaseSubscriptionConfig(),
	}
}

func (config *StandaloneSubscriptionConfig) WithCallback(callback MessageCallback, context any) *StandaloneSubscriptionConfig {
	config.BaseSubscriptionConfig.WithCallback(callback, context)
	return config
}

func (config *StandaloneSubscriptionConfig) SetCallback(callback MessageCallback) *StandaloneSubscriptionConfig {
	config.BaseSubscriptionConfig.SetCallback(callback)
	return config
}

func (config *StandaloneSubscriptionConfig) GetCallback() MessageCallback {
	return config.callback
}

func (config *StandaloneSubscriptionConfig) WithSubscription(mode PubSubChannelMode, channelOrPattern string) *StandaloneSubscriptionConfig {
	channelsMap, _ := config.subscriptions.LoadOrStore(mode, make(map[string]string))
	channels := channelsMap.(map[string]string)

	channels[channelOrPattern] = channelOrPattern
	return config
}

func (c *StandaloneSubscriptionConfig) GetSubscriptions(mode PubSubChannelMode) []string {
	channels := make([]string, 0)
	channelsMap, ok := c.subscriptions.Load(mode)
	if !ok {
		return channels
	}

	for channel := range channelsMap.(map[string]string) {
		channels = append(channels, channel)
	}
	return channels
}

func (config *StandaloneSubscriptionConfig) GetAllSubscriptions() map[PubSubChannelMode][]string {
	result := make(map[PubSubChannelMode][]string)

	config.subscriptions.Range(func(key, value any) bool {
		mode := key.(PubSubChannelMode)
		channelsMap := value.(map[string]string)

		channels := make([]string, 0, len(channelsMap))
		for _, channel := range channelsMap {
			channels = append(channels, channel)
		}

		result[mode] = channels
		return true
	})

	return result
}

func (config *StandaloneSubscriptionConfig) Validate() error {
	return config.BaseSubscriptionConfig.Validate()
}

// *** ClusterSubscriptionConfig ***

type PubSubClusterChannelMode int

const (
	ExactClusterChannelMode PubSubChannelMode = iota
	PatternClusterChannelMode
	ShardedClusterChannelMode
)

func (mode PubSubClusterChannelMode) String() string {
	return [...]string{"EXACT", "PATTERN", "SHARDED"}[mode]
}

func (mode PubSubClusterChannelMode) Command() string {
	return [...]string{"SUBSCRIBE", "PSUBSCRIBE", "SSUBSCRIBE"}[mode]
}

func (mode PubSubClusterChannelMode) UnsubscribeCommand() string {
	return [...]string{"UNSUBSCRIBE", "PUNSUBSCRIBE", "SUNSUBSCRIBE"}[mode]
}

type ClusterSubscriptionConfig struct {
	*BaseSubscriptionConfig
	subscriptions sync.Map // map[PubSubClusterChannelMode]map[string]string
}

func NewClusterSubscriptionConfig() *ClusterSubscriptionConfig {
	return &ClusterSubscriptionConfig{
		BaseSubscriptionConfig: NewBaseSubscriptionConfig(),
	}
}

func (config *ClusterSubscriptionConfig) WithCallback(callback MessageCallback, context any) *ClusterSubscriptionConfig {
	config.BaseSubscriptionConfig.WithCallback(callback, context)
	return config
}

func (config *ClusterSubscriptionConfig) SetCallback(callback MessageCallback) *ClusterSubscriptionConfig {
	config.BaseSubscriptionConfig.SetCallback(callback)
	return config
}

func (config *ClusterSubscriptionConfig) GetCallback() MessageCallback {
	return config.callback
}

func (config *ClusterSubscriptionConfig) WithSubscription(mode PubSubClusterChannelMode, channelOrPattern string) *ClusterSubscriptionConfig {
	channelsMap, _ := config.subscriptions.LoadOrStore(mode, make(map[string]string))
	channels := channelsMap.(map[string]string)

	channels[channelOrPattern] = channelOrPattern
	return config
}

func (c *ClusterSubscriptionConfig) GetSubscriptions(mode PubSubClusterChannelMode) []string {
	channels := make([]string, 0)
	channelsMap, ok := c.subscriptions.Load(mode)
	if !ok {
		return channels
	}

	for channel := range channelsMap.(map[string]string) {
		channels = append(channels, channel)
	}
	return channels
}

func (config *ClusterSubscriptionConfig) GetAllSubscriptions() map[PubSubClusterChannelMode][]string {
	result := make(map[PubSubClusterChannelMode][]string)

	config.subscriptions.Range(func(key, value any) bool {
		mode := key.(PubSubClusterChannelMode)
		channelsMap := value.(map[string]string)

		channels := make([]string, 0, len(channelsMap))
		for _, channel := range channelsMap {
			channels = append(channels, channel)
		}

		result[mode] = channels
		return true
	})

	return result
}

func (config *ClusterSubscriptionConfig) Validate() error {
	return config.BaseSubscriptionConfig.Validate()
}
