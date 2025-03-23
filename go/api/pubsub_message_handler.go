// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"errors"
	"fmt"
	"log"
	"sync"
)

var (
	ErrPubSubPushInvalid       = errors.New("received invalid push: empty or in incorrect format")
	ErrPubSubPushMissingKind   = errors.New("received invalid push: missing kind field")
	ErrPubSubPushMissingValues = errors.New("received invalid push: missing values field")
)

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

type MessageCallbackError struct {
	cause error
}

func (e *MessageCallbackError) Error() string {
	return fmt.Sprintf("error in message callback: %v", e.cause)
}

func (e *MessageCallbackError) Cause() error {
	return e.cause
}

// *** Message Handler ***

type ResponseResolver func(response any) (any, error)

type MessageHandler struct {
	callback MessageCallback
	context  any
	resolver ResponseResolver
	queue    *PubSubMessageQueue
}

func NewMessageHandler(callback MessageCallback, context any, resolver ResponseResolver) *MessageHandler {
	return &MessageHandler{
		callback: callback,
		context:  context,
		resolver: resolver,
		queue:    NewPubSubMessageQueue(),
	}
}

// Handle processes the incoming response and invokes the callback if available
func (handler *MessageHandler) Handle(response any) error {
	data, err := handler.resolver(response)
	if err != nil {
		return err
	}

	// Handle cases where the response might be a map or might include a direct kind value
	var kind PushKind
	var values []any

	switch v := data.(type) {
	case map[string]any:
		// Traditional path from Rust conversion
		push := v
		if len(push) == 0 {
			log.Println("invalid push", ErrPubSubPushInvalid.Error())
			return ErrPubSubPushInvalid
		}

		// The kind might be a string or an int, depending on the source
		switch kindVal := push["kind"].(type) {
		case string:
			kind = PushKindFromString(kindVal)
		case int:
			kind = PushKind(kindVal)
		case float64: // JSON numbers come as float64
			kind = PushKind(int(kindVal))
		default:
			log.Println("invalid push", ErrPubSubPushMissingKind.Error())
			return ErrPubSubPushMissingKind
		}

		// Values might be direct or nested in an array response
		switch vals := push["values"].(type) {
		case []any:
			values = vals
		case map[string]any:
			if arr, ok := vals["array_value"].([]any); ok {
				values = arr
			} else {
				log.Println("invalid push", ErrPubSubPushMissingValues.Error())
				return ErrPubSubPushMissingValues
			}
		default:
			log.Println("invalid push", ErrPubSubPushMissingValues.Error())
			return ErrPubSubPushMissingValues
		}

	case struct {
		Kind   PushKind
		Values []any
	}:
		// Direct structure from new callback
		kind = v.Kind
		values = v.Values

	default:
		log.Println("invalid push", ErrPubSubPushInvalid.Error())
		return ErrPubSubPushInvalid
	}

	// Convert value to string based on its type
	toString := func(v any) string {
		switch val := v.(type) {
		case []byte:
			return string(val)
		case string:
			return val
		case int64:
			return fmt.Sprintf("%d", val)
		case float64:
			return fmt.Sprintf("%g", val)
		case int:
			return fmt.Sprintf("%d", val)
		case map[string]any:
			// Handle CommandResponse structure
			if str, ok := val["string_value"].(string); ok {
				return str
			}
			if i, ok := val["int_value"].(float64); ok {
				return fmt.Sprintf("%d", int64(i))
			}
			return fmt.Sprintf("%v", val)
		default:
			return fmt.Sprintf("%v", val)
		}
	}

	// Process based on kind
	switch kind {
	case Disconnection:
		log.Println("disconnect notification", "Transport disconnected, messages might be lost")

	case Message, SMessage:
		if len(values) < 2 {
			return fmt.Errorf("invalid Message/SMessage: expected 2 values, got %d", len(values))
		}
		channel := toString(values[0]) // string
		message := toString(values[1]) // string
		return handler.handleMessage(NewPubSubMessage(message, channel))

	case PMessage:
		// Pattern message: values are [pattern, channel, message]
		if len(values) < 3 {
			return fmt.Errorf("invalid PMessage: expected 3 values, got %d", len(values))
		}
		pattern := CreateStringResult(toString(values[0])) // Result[string]
		channel := toString(values[1])                     // string
		message := toString(values[2])                     // string
		return handler.handleMessage(NewPubSubMessageWithPattern(message, channel, pattern.Value()))

	case Subscribe, PSubscribe, SSubscribe, Unsubscribe, PUnsubscribe, SUnsubscribe:
		valuesStr := make([]string, len(values))
		for i, v := range values {
			valuesStr[i] = toString(v)
		}
		log.Printf("subscribe/unsubscribe notification: type='%s' values=%v\n", kind.String(), valuesStr)
		// We don't return here anymore - subscription notifications should not stop message processing

	default:
		log.Printf("unknown notification message: '%s'\n", kind.String())
	}

	return nil
}

func (handler *MessageHandler) handleMessage(message *PubSubMessage) error {
	if handler.callback != nil {
		defer func() {
			if r := recover(); r != nil {
				err, ok := r.(error)
				if !ok {
					err = fmt.Errorf("%v", r)
				}
				log.Println("panic in message callback", err.Error())
			}
		}()

		handler.callback(message, handler.context)
		return nil
	}

	handler.queue.Push(message)
	return nil
}

func (handler *MessageHandler) GetQueue() *PubSubMessageQueue {
	return handler.queue
}

// *** Message Queue ***

type PubSubMessageQueue struct {
	mu                      sync.Mutex
	messages                []*PubSubMessage
	waiters                 []chan *PubSubMessage
	nextMessageReadyCh      chan struct{}
	nextMessageReadySignals []chan struct{}
}

func NewPubSubMessageQueue() *PubSubMessageQueue {
	return &PubSubMessageQueue{
		messages:           make([]*PubSubMessage, 0),
		waiters:            make([]chan *PubSubMessage, 0),
		nextMessageReadyCh: make(chan struct{}, 1),
	}
}

func (queue *PubSubMessageQueue) Push(message *PubSubMessage) {
	queue.mu.Lock()
	defer queue.mu.Unlock()

	// If there's a waiter, deliver the message directly
	if len(queue.waiters) > 0 {
		waiterCh := queue.waiters[0]
		queue.waiters = queue.waiters[1:]
		waiterCh <- message
		return
	}

	// Otherwise, add to the queue
	queue.messages = append(queue.messages, message)

	// Signal that a new message is ready
	select {
	case queue.nextMessageReadyCh <- struct{}{}:
	default:
		// Channel already has a signal
	}

	// Signal any waiters
	for _, ch := range queue.nextMessageReadySignals {
		select {
		case ch <- struct{}{}:
		default:
			// Channel is full, receiver might not be listening
		}
	}
}

func (queue *PubSubMessageQueue) Pop() *PubSubMessage {
	queue.mu.Lock()
	defer queue.mu.Unlock()

	if len(queue.messages) == 0 {
		return nil
	}

	message := queue.messages[0]
	queue.messages = queue.messages[1:]
	return message
}

func (queue *PubSubMessageQueue) WaitForMessage() <-chan *PubSubMessage {
	queue.mu.Lock()
	defer queue.mu.Unlock()

	// If a message is already queued, return it immediately
	if len(queue.messages) > 0 {
		messageCh := make(chan *PubSubMessage, 1)
		message := queue.messages[0]
		queue.messages = queue.messages[1:]
		messageCh <- message
		return messageCh
	}

	// Otherwise register a waiter
	messageCh := make(chan *PubSubMessage, 1)
	queue.waiters = append(queue.waiters, messageCh)
	return messageCh
}

func (queue *PubSubMessageQueue) RegisterSignalChannel(ch chan struct{}) {
	queue.mu.Lock()
	defer queue.mu.Unlock()
	queue.nextMessageReadySignals = append(queue.nextMessageReadySignals, ch)
}

func (queue *PubSubMessageQueue) UnregisterSignalChannel(ch chan struct{}) {
	queue.mu.Lock()
	defer queue.mu.Unlock()

	for idx, channel := range queue.nextMessageReadySignals {
		if channel == ch {
			queue.nextMessageReadySignals = append(queue.nextMessageReadySignals[:idx], queue.nextMessageReadySignals[idx+1:]...)
			break
		}
	}
}
