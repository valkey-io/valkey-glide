// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import "context"

// PubSubCommands defines the interface for Pub/Sub operations available in both standalone and cluster modes.
type PubSubCommands interface {
	// PubSubChannels returns a list of all channels in the database.
	PubSubChannels(ctx context.Context) ([]string, error)
	// PubSubChannelsWithPattern returns a list of all channels that match the given pattern.
	PubSubChannelsWithPattern(ctx context.Context, pattern string) ([]string, error)
	// PubSubNumPat returns the number of patterns that are subscribed to by clients.
	PubSubNumPat(ctx context.Context) (int64, error)
	// PubSubNumSub returns the number of subscribers for a channel.
	PubSubNumSub(ctx context.Context, channels ...string) (map[string]int64, error)
}

type PubSubStandaloneCommands interface {
	// Publish publishes a message to a channel. Returns the number of clients that received the message.
	Publish(ctx context.Context, channel string, message string) (int64, error)
}

// PubSubClusterCommands defines additional Pub/Sub operations available only in cluster mode.
type PubSubClusterCommands interface {
	// Publish publishes a message to a channel. Returns the number of clients that received the message.
	Publish(ctx context.Context, channel string, message string, sharded bool) (int64, error)
	PubSubShardChannels(ctx context.Context) ([]string, error)
	PubSubShardChannelsWithPattern(ctx context.Context, pattern string) ([]string, error)
	PubSubShardNumSub(ctx context.Context, channels ...string) (map[string]int64, error)
}
