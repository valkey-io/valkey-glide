// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"log"
	"math"
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// maxPubSubPayloadLen is the largest length the pub/sub callback can copy.
// C.GoBytes takes a 32-bit length, so anything longer cannot be copied whole.
const maxPubSubPayloadLen = math.MaxInt32

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
	resultChannel <- payload{value: response, error: nil}
}

//export failureCallback
func failureCallback(channelPtr unsafe.Pointer, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	msg := C.GoString(cErrorMessage)
	resultChannel := *(*chan payload)(getPinnedPtr(channelPtr))
	resultChannel <- payload{value: nil, error: GoError(uint32(cErrorType), msg)}
}

// pubSubBytes copies length bytes from ptr, reporting failure when length falls
// outside the range C.GoBytes accepts. The FFI layer passes int64_t lengths, so
// narrowing to the 32-bit length C.GoBytes takes has to be checked rather than
// silently truncating an oversized payload. name identifies the field in the log
// line.
func pubSubBytes(name string, ptr unsafe.Pointer, length int64) ([]byte, bool) {
	if length < 0 || length > maxPubSubPayloadLen {
		log.Printf("Dropping pub/sub message: %s length %d is outside the supported range of 0 to %d bytes\n",
			name, length, maxPubSubPayloadLen)
		return nil, false
	}
	return C.GoBytes(ptr, C.int(length)), true
}

//
//export pubSubCallback
func pubSubCallback(
	clientPtr unsafe.Pointer,
	pushKind C.PushKind,
	message unsafe.Pointer,
	message_len C.int64_t,
	channel unsafe.Pointer,
	channel_len C.int64_t,
	pattern unsafe.Pointer,
	pattern_len C.int64_t,
) {
	if clientPtr == nil {
		return
	}

	messageBytes, ok := pubSubBytes("message", message, int64(message_len))
	if !ok {
		return
	}
	channelBytes, ok := pubSubBytes("channel", channel, int64(channel_len))
	if !ok {
		return
	}

	msg := string(messageBytes)
	cha := string(channelBytes)
	pat := models.CreateNilStringResult()
	if pattern_len > 0 && pattern != nil {
		patternBytes, ok := pubSubBytes("pattern", pattern, int64(pattern_len))
		if !ok {
			return
		}
		pat = models.CreateStringResult(string(patternBytes))
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
