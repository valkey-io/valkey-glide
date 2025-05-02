// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// PubSubCommands defines the interface for Pub/Sub operations available in both standalone and cluster modes.
type PubSubCommands interface {
	// PubSubChannels returns a list of all channels in the database.
	PubSubChannels() ([]string, error)
	// PubSubChannelsWithPattern returns a list of all channels that match the given pattern.
	PubSubChannelsWithPattern(pattern string) ([]string, error)
	// PubSubNumPat returns the number of patterns that are subscribed to by clients.
	PubSubNumPat() (int64, error)
	// PubSubNumSub returns the number of subscribers for a channel.
	PubSubNumSub(channels ...string) (map[string]int64, error)
}

type PubSubStandaloneCommands interface {
	// Publish publishes a message to a channel. Returns the number of clients that received the message.
	Publish(channel string, message string) (int64, error)
}

// PubSubClusterCommands defines additional Pub/Sub operations available only in cluster mode.
type PubSubClusterCommands interface {
	// Publish publishes a message to a channel. Returns the number of clients that received the message.
	Publish(channel string, message string, sharded bool) (int64, error)
	PubSubShardChannels() ([]string, error)
	PubSubShardChannelsWithPattern(pattern string) ([]string, error)
	PubSubShardNumSub(channels ...string) (map[string]int64, error)
}

type PubSubHandler interface {
	GetQueue() (*PubSubMessageQueue, error)
}
