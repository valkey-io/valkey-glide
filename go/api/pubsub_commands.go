// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// PubSubCommands defines the interface for Pub/Sub operations available in both standalone and cluster modes.
type PubSubCommands interface {
	// Publish publishes a message to a channel. Returns the number of clients that received the message.
	Publish(message string, channel string) (int64, error)
}

// PubSubClusterCommands defines additional Pub/Sub operations available only in cluster mode.
type PubSubClusterCommands interface{}
