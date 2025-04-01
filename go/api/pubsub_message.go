// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"encoding/json"
	"fmt"
)

type PubSubMessage struct {
	Message string
	Channel string
	Pattern Result[string]
}

func NewPubSubMessage(message, channel string) *PubSubMessage {
	return &PubSubMessage{
		Message: message,
		Channel: channel,
		Pattern: CreateNilStringResult(),
	}
}

func NewPubSubMessageWithPattern(message, channel, pattern string) *PubSubMessage {
	return &PubSubMessage{
		Message: message,
		Channel: channel,
		Pattern: CreateStringResult(pattern),
	}
}

func (msg *PubSubMessage) ToString() string {
	jsonBytes, err := json.Marshal(msg)
	if err != nil {
		return ""
	}
	return string(jsonBytes)
}

// PushKind represents the type of push message received from the server
type PushKind int

// PushKind enum values - these match the numeric values from the Rust side
const (
	Disconnection PushKind = iota // 0
	Other                         // 1
	Invalidate                    // 2
	Message                       // 3
	PMessage                      // 4
	SMessage                      // 5
	Unsubscribe                   // 6
	PUnsubscribe                  // 7
	SUnsubscribe                  // 8
	Subscribe                     // 9
	PSubscribe                    // 10
	SSubscribe                    // 11
)

// String returns the string representation of a PushKind
func (kind PushKind) String() string {
	switch kind {
	case Disconnection:
		return "Disconnection"
	case Other:
		return "Other"
	case Invalidate:
		return "Invalidate"
	case Message:
		return "Message"
	case PMessage:
		return "PMessage"
	case SMessage:
		return "SMessage"
	case Unsubscribe:
		return "Unsubscribe"
	case PUnsubscribe:
		return "PUnsubscribe"
	case SUnsubscribe:
		return "SUnsubscribe"
	case Subscribe:
		return "Subscribe"
	case PSubscribe:
		return "PSubscribe"
	case SSubscribe:
		return "SSubscribe"
	default:
		return fmt.Sprintf("Unknown(%d)", kind)
	}
}

// PushKindFromString converts a string representation to a PushKind
func PushKindFromString(s string) PushKind {
	switch s {
	case "Disconnection":
		return Disconnection
	case "Other":
		return Other
	case "Invalidate":
		return Invalidate
	case "Message":
		return Message
	case "PMessage":
		return PMessage
	case "SMessage":
		return SMessage
	case "Unsubscribe":
		return Unsubscribe
	case "PUnsubscribe":
		return PUnsubscribe
	case "SUnsubscribe":
		return SUnsubscribe
	case "Subscribe":
		return Subscribe
	case "PSubscribe":
		return PSubscribe
	case "SSubscribe":
		return SSubscribe
	default:
		return Other
	}
}

// PushInfo represents a message received from the server
type PushInfo struct {
	Kind    PushKind
	Message *PubSubMessage
}
