// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #include "../lib.h"
import "C"

import (
	"fmt"
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/api/errors"
)

// Registry to track clients by their pointer address
var (
	clientRegistry   = make(map[uintptr]*baseClient)
	clientRegistryMu sync.RWMutex
)

// RegisterClient registers a client in the registry using its pointer value
func RegisterClient(client *baseClient, ptrValue uintptr) {
	clientRegistryMu.Lock()
	defer clientRegistryMu.Unlock()
	clientRegistry[ptrValue] = client
}

// UnregisterClient removes a client from the registry
func UnregisterClient(ptrValue uintptr) {
	clientRegistryMu.Lock()
	defer clientRegistryMu.Unlock()
	delete(clientRegistry, ptrValue)
}

// GetClientByPtr gets a client from the registry by its pointer value
func GetClientByPtr(ptrValue uintptr) *baseClient {
	clientRegistryMu.RLock()
	defer clientRegistryMu.RUnlock()
	return clientRegistry[ptrValue]
}

//export successCallback
func successCallback(channelPtr unsafe.Pointer, cResponse *C.struct_CommandResponse) {
	response := cResponse
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))
	resultChannel <- payload{value: response, error: nil}
}

//export failureCallback
func failureCallback(channelPtr unsafe.Pointer, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	defer C.free_error_message(cErrorMessage)
	msg := C.GoString(cErrorMessage)
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))
	resultChannel <- payload{value: nil, error: errors.GoError(uint32(cErrorType), msg)}
}

//export pubSubCallback
func pubSubCallback(clientPtr unsafe.Pointer, pushKind C.PushKind, message unsafe.Pointer, message_len C.int, channel unsafe.Pointer, channel_len C.int, pattern unsafe.Pointer, pattern_len C.int) {

	if clientPtr == nil {
		return
	}

	msg := string(C.GoBytes(message, message_len))
	cha := string(C.GoBytes(channel, channel_len))
	pat := ""
	if pattern_len > 0 && pattern != nil {
		pat = string(C.GoBytes(pattern, pattern_len))
	}

	go func() {

		// Process different types of push messages
		message, err := getMessage(pushKind, msg, cha, pat)
		if err != nil {
			// todo log
			return
		}

		if clientPtr != nil {
			// Look up the client in our registry using the pointer address
			ptrValue := uintptr(clientPtr)
			client := GetClientByPtr(ptrValue)

			if client != nil {
				// If the client has a message handler, use it
				if handler := client.GetMessageHandler(); handler != nil {
					handler.Handle(pushKind, message)
				}
			} else {
				// TODO log
				fmt.Printf("Client not found for pointer: %v\n", ptrValue)
			}
		}
	}()
}

func getMessage(pushKind C.PushKind, msgContent string, channel string, pattern string) (*PubSubMessage, error) {
	switch pushKind {
	case C.PushMessage, C.PushSMessage:
		return NewPubSubMessage(msgContent, channel), nil

	case C.PushPMessage:
		return NewPubSubMessageWithPattern(msgContent, channel, pattern), nil

	case C.PushSubscribe, C.PushPSubscribe, C.PushSSubscribe, C.PushUnsubscribe, C.PushPUnsubscribe, C.PushSUnsubscribe:
		return NewPubSubMessage(msgContent, channel), nil

	default:
		// log unsupported push kind
		return nil, fmt.Errorf("unsupported push kind")
	}
}
