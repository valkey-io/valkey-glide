// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"log"
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// Registry to track clients by their pointer address
var (
	clientRegistry   = make(map[uintptr]*baseClient)
	clientRegistryMu sync.RWMutex
)

// registerClient registers a client in the registry using its pointer value
func registerClient(client *baseClient, ptrValue uintptr) {
	clientRegistryMu.Lock()
	defer clientRegistryMu.Unlock()
	clientRegistry[ptrValue] = client
}

// unregisterClient removes a client from the registry
func unregisterClient(ptrValue uintptr) {
	clientRegistryMu.Lock()
	defer clientRegistryMu.Unlock()
	delete(clientRegistry, ptrValue)
}

// getClientByPtr gets a client from the registry by its pointer value
func getClientByPtr(ptrValue uintptr) *baseClient {
	clientRegistryMu.RLock()
	defer clientRegistryMu.RUnlock()
	return clientRegistry[ptrValue]
}

//export successCallback
func successCallback(channelPtr unsafe.Pointer, cResponse *C.struct_CommandResponse) {
	response := cResponse
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))

	// For FlatBuffer responses, the data is malloc'd by Rust and ownership
	// transfers to Go. Use unsafe.Slice for zero-copy access.
	if response != nil && response.response_type == 10 { // FlatBuffer
		dataLen := int(response.string_value_len)
		if dataLen > 0 && response.string_value != nil {
			// Zero-copy: create a Go slice backed by the malloc'd memory.
			// Go owns this memory and must free it with C.free.
			goSlice := unsafe.Slice((*byte)(unsafe.Pointer(response.string_value)), dataLen)
			resultChannel <- payload{
				value:      nil,
				error:      nil,
				flatBuf:    goSlice,
				flatBufPtr: unsafe.Pointer(response.string_value),
			}
			return
		}
	}

	resultChannel <- payload{value: response, error: nil}
}

//export failureCallback
func failureCallback(channelPtr unsafe.Pointer, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	msg := C.GoString(cErrorMessage)
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))
	resultChannel <- payload{value: nil, error: GoError(uint32(cErrorType), msg)}
}

//
//export pubSubCallback
func pubSubCallback(
	clientPtr unsafe.Pointer,
	pushKind C.PushKind,
	message unsafe.Pointer,
	message_len C.int,
	channel unsafe.Pointer,
	channel_len C.int,
	pattern unsafe.Pointer,
	pattern_len C.int,
) {
	if clientPtr == nil {
		return
	}

	msg := string(C.GoBytes(message, message_len))
	cha := string(C.GoBytes(channel, channel_len))
	pat := models.CreateNilStringResult()
	if pattern_len > 0 && pattern != nil {
		pat = models.CreateStringResult(string(C.GoBytes(pattern, pattern_len)))
	}

	go func() {
		// Process different types of push messages
		message := models.NewPubSubMessageWithPattern(msg, cha, pat)

		if clientPtr != nil {
			// Look up the client in our registry using the pointer address
			ptrValue := uintptr(clientPtr)
			client := getClientByPtr(ptrValue)

			if client != nil {
				// If the client has a message handler, use it
				if handler := client.getMessageHandler(); handler != nil {
					handler.handleMessage(message)
				}
			} else {
				log.Printf("Client not found for pointer: %v\n", ptrValue)
			}
		}
	}()
}
