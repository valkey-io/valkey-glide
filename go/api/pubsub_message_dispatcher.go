// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"sync"
)

type dispatchItem struct {
	handler *MessageHandler
	message *PubSubMessage
}

// MessageDispatcher manages PubSub subscriptions and message routing
type MessageDispatcher struct {
	clients     map[string]*MessageHandler
	channelSubs map[string]map[string]bool // channel -> clientID -> exists
	patternSubs map[string]map[string]bool // pattern -> clientID -> exists
	mu          sync.RWMutex
}

// NewMessageDispatcher creates a new message dispatcher
func NewMessageDispatcher() *MessageDispatcher {
	return &MessageDispatcher{
		clients:     make(map[string]*MessageHandler),
		channelSubs: make(map[string]map[string]bool),
		patternSubs: make(map[string]map[string]bool),
	}
}

// RegisterClient adds a client to the dispatcher
func (md *MessageDispatcher) RegisterClient(clientID string, handler *MessageHandler) {
	md.mu.Lock()
	defer md.mu.Unlock()

	md.clients[clientID] = handler
}

// UnregisterClient removes a client from the dispatcher
func (md *MessageDispatcher) UnregisterClient(clientID string) {
	md.mu.Lock()
	defer md.mu.Unlock()

	delete(md.clients, clientID)

	// Remove client from all channel subscriptions
	for channel, clients := range md.channelSubs {
		delete(clients, clientID)
		if len(clients) == 0 {
			delete(md.channelSubs, channel)
		}
	}

	// Remove client from all pattern subscriptions
	for pattern, clients := range md.patternSubs {
		delete(clients, clientID)
		if len(clients) == 0 {
			delete(md.patternSubs, pattern)
		}
	}
}

// AddSubscription registers a channel subscription for a client
func (md *MessageDispatcher) AddSubscription(clientID string, channel string) {
	md.mu.Lock()
	defer md.mu.Unlock()

	if _, ok := md.channelSubs[channel]; !ok {
		md.channelSubs[channel] = make(map[string]bool)
	}
	md.channelSubs[channel][clientID] = true
}

// AddPatternSubscription registers a pattern subscription for a client
func (md *MessageDispatcher) AddPatternSubscription(clientID string, pattern string) {
	md.mu.Lock()
	defer md.mu.Unlock()

	if _, ok := md.patternSubs[pattern]; !ok {
		md.patternSubs[pattern] = make(map[string]bool)
	}
	md.patternSubs[pattern][clientID] = true
}

// RemoveSubscription unregisters a channel subscription for a client
func (md *MessageDispatcher) RemoveSubscription(clientID string, channel string) {
	md.mu.Lock()
	defer md.mu.Unlock()

	if clients, ok := md.channelSubs[channel]; ok {
		delete(clients, clientID)
		if len(clients) == 0 {
			delete(md.channelSubs, channel)
		}
	}
}

// RemovePatternSubscription unregisters a pattern subscription for a client
func (md *MessageDispatcher) RemovePatternSubscription(clientID string, pattern string) {
	md.mu.Lock()
	defer md.mu.Unlock()

	if clients, ok := md.patternSubs[pattern]; ok {
		delete(clients, clientID)
		if len(clients) == 0 {
			delete(md.patternSubs, pattern)
		}
	}
}

// DispatchMessage routes a message to all subscribed clients
func (md *MessageDispatcher) DispatchMessage(pushInfo PushInfo) {
	var itemsToDispatch []dispatchItem
	switch pushInfo.Kind {
	case Message, SMessage:
		md.mu.RLock()
		channel := pushInfo.Message.Channel
		// Deliver to all clients subscribed to this channel
		if clients, ok := md.channelSubs[channel]; ok {
			for clientID := range clients {
				if handler, exists := md.clients[clientID]; exists && handler != nil {
					itemsToDispatch = append(itemsToDispatch, dispatchItem{handler: handler, message: pushInfo.Message})
				}
			}
		}
		md.mu.RUnlock()
	case PMessage:
		md.mu.RLock()
		pattern := pushInfo.Message.Pattern.Value()
		// Deliver to all clients subscribed to this pattern
		if clients, ok := md.patternSubs[pattern]; ok {
			for clientID := range clients {
				if handler, exists := md.clients[clientID]; exists && handler != nil {
					itemsToDispatch = append(itemsToDispatch, dispatchItem{handler: handler, message: pushInfo.Message})
				}
			}
		}
		md.mu.RUnlock()
	case Subscribe, PSubscribe, SSubscribe:
		// Skip automatic subscription management here
		// Subscriptions should be handled explicitly by calling AddSubscription/AddPatternSubscription
		// directly for the specific client that is subscribing
	case Unsubscribe, PUnsubscribe, SUnsubscribe:
		// Skip automatic unsubscription management here
		// Unsubscriptions should be handled explicitly by calling RemoveSubscription/RemovePatternSubscription
		// directly for the specific client that is unsubscribing
	}

	// Dispatch the message to all clients
	for _, item := range itemsToDispatch {
		item.handler.handleMessage(item.message)
	}
}

// RegisterSubscriptionConfig registers all subscriptions from a configuration for a client
func (md *MessageDispatcher) RegisterSubscriptionConfig(clientID string, config *BaseSubscriptionConfig) {
	if config == nil || config.subscriptions == nil {
		return
	}

	// Iterate through all subscription types in the config
	for modeKey, channels := range config.subscriptions {
		mode := int(modeKey)
		
		// For each channel/pattern, add the appropriate subscription type
		for _, channelOrPattern := range channels {
			// ExactChannelMode is 0, PatternChannelMode is 1 (both in standalone and cluster configs)
			if mode == 0 { // ExactChannelMode
				md.AddSubscription(clientID, channelOrPattern)
			} else { // Any pattern-based mode (PatternChannelMode or ShardedClusterChannelMode)
				md.AddPatternSubscription(clientID, channelOrPattern)
			}
		}
	}
}

// Helper function to convert any value to string
func toString(v any) (string, bool) {
	switch val := v.(type) {
	case string:
		return val, true
	case []byte:
		return string(val), true
	case int64:
		return fmt.Sprintf("%d", val), true
	case float64:
		return fmt.Sprintf("%g", val), true
	case int:
		return fmt.Sprintf("%d", val), true
	default:
		return fmt.Sprintf("%v", val), true
	}
}
