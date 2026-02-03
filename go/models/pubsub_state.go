// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package models

// PubSubChannelMode represents the type of pubsub subscription.
type PubSubChannelMode int

const (
	Exact PubSubChannelMode = iota
	Pattern
	Sharded
)

// PubSubState represents the subscription state of a client.
type PubSubState struct {
	// DesiredSubscriptions contains the channels/patterns the client intends to be subscribed to.
	DesiredSubscriptions map[PubSubChannelMode]map[string]struct{}
	// ActualSubscriptions contains the channels/patterns the client is actually subscribed to on the server.
	ActualSubscriptions map[PubSubChannelMode]map[string]struct{}
}

// NewPubSubState creates a new PubSubState with initialized maps.
func NewPubSubState() *PubSubState {
	return &PubSubState{
		DesiredSubscriptions: make(map[PubSubChannelMode]map[string]struct{}),
		ActualSubscriptions:  make(map[PubSubChannelMode]map[string]struct{}),
	}
}
