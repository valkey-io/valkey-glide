// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include <limits.h>
// #include "lib.h"
// #define GLIDE_GO_STRING_N_MAX INT_MAX
import "C"

import (
	"log"
	"sync"
	"sync/atomic"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

const (
	requestRegistryShardCount = 64
	maxGoStringNLength        = int64(C.GLIDE_GO_STRING_N_MAX)
)

type requestRegistryShard struct {
	mu       sync.Mutex
	requests map[uintptr]chan payload
}

// requestRegistry maps FFI request IDs to their Go result channels. FFI retains
// an ID until it invokes a callback, so the ID must be safe to look up even
// after the initiating Go call has returned. Sharding avoids serializing every
// command from every client through one process-wide mutex.
var (
	requestRegistry [requestRegistryShardCount]requestRegistryShard
	nextRequestID   atomic.Uint64
)

func requestRegistryShardFor(requestID uintptr) *requestRegistryShard {
	return &requestRegistry[requestID&(requestRegistryShardCount-1)]
}

// registerRequest assigns a unique FFI request ID to resultChannel. Production callers must provide a buffered
// channel with capacity one so callbacks and failPendingRequests can deliver while client.mu is held. An unbuffered
// channel is valid only in tests that claim the request before waiting; production use can block Close indefinitely.
func registerRequest(resultChannel chan payload) uintptr {
	for {
		requestID := uintptr(nextRequestID.Add(1))
		if requestID == 0 {
			continue
		}

		shard := requestRegistryShardFor(requestID)
		shard.mu.Lock()
		if shard.requests == nil {
			shard.requests = make(map[uintptr]chan payload)
		}
		if _, exists := shard.requests[requestID]; !exists {
			shard.requests[requestID] = resultChannel
			shard.mu.Unlock()
			return requestID
		}
		shard.mu.Unlock()
	}
}

// takeRequest atomically claims a request. Exactly one of a callback,
// cancellation, or Close can claim a request ID.
func takeRequest(requestID uintptr) (chan payload, bool) {
	shard := requestRegistryShardFor(requestID)
	shard.mu.Lock()
	defer shard.mu.Unlock()

	resultChannel, ok := shard.requests[requestID]
	if ok {
		delete(shard.requests, requestID)
	}
	return resultChannel, ok
}

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

// successCallback delivers a successful FFI response to its registered request.
//
//export successCallback
func successCallback(requestID C.uintptr_t, cResponse *C.struct_CommandResponse) {
	deliverSuccess(uintptr(requestID), cResponse)
}

// deliverSuccess sends a successful response or releases it when its request was already claimed.
func deliverSuccess(requestID uintptr, cResponse *C.struct_CommandResponse) {
	resultChannel, ok := takeRequest(requestID)
	if !ok {
		C.free_command_response(cResponse)
		return
	}
	resultChannel <- payload{value: cResponse, error: nil}
}

// failureCallback delivers a failed FFI response to its registered request.
//
//export failureCallback
func failureCallback(requestID C.uintptr_t, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	deliverFailure(uintptr(requestID), cErrorMessage, cErrorType)
}

// deliverFailure sends a copied FFI error when its request has not already been claimed.
func deliverFailure(requestID uintptr, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
	resultChannel, ok := takeRequest(requestID)
	if !ok {
		return
	}
	msg := C.GoString(cErrorMessage)
	resultChannel <- payload{value: nil, error: GoError(uint32(cErrorType), msg)}
}

// handlePubSubCallback validates every borrowed field before copying it. Rust
// retains ownership of the payload and frees it after the exported callback
// returns, so all C.GoStringN calls must remain synchronous.
func handlePubSubCallback(
	callbackID uintptr,
	message unsafe.Pointer,
	messageLen int64,
	channel unsafe.Pointer,
	channelLen int64,
	pattern unsafe.Pointer,
	patternLen int64,
) bool {
	validField := func(data unsafe.Pointer, length int64) bool {
		return length >= 0 && length <= maxGoStringNLength && (length == 0 || data != nil)
	}
	if callbackID == 0 ||
		!validField(message, messageLen) ||
		!validField(channel, channelLen) ||
		!validField(pattern, patternLen) {
		// Never log borrowed PubSub data or length values: either may be
		// controlled by an application and contain sensitive information.
		log.Print("invalid PubSub callback payload")
		return false
	}

	client := getClientByPtr(callbackID)
	if client == nil {
		log.Print("PubSub client not found")
		return false
	}
	// Capture the final handler before returning to Rust. Creation failure may
	// unregister the client immediately after the native call returns, but an
	// already accepted push still owns this handler and must be delivered.
	handler := client.getMessageHandler()
	if handler == nil {
		log.Print("PubSub handler not found")
		return false
	}

	msg := C.GoStringN((*C.char)(message), C.int(messageLen))
	cha := C.GoStringN((*C.char)(channel), C.int(channelLen))
	pat := models.CreateNilStringResult()
	if patternLen > 0 {
		pat = models.CreateStringResult(C.GoStringN((*C.char)(pattern), C.int(patternLen)))
	}

	go func() {
		pubsubMessage := models.NewPubSubMessageWithPattern(msg, cha, pat)
		handler.handleMessage(pubsubMessage)
	}()
	return true
}

//export pubSubCallback
func pubSubCallback(
	callbackID C.uintptr_t,
	_ C.PushKind,
	message unsafe.Pointer,
	messageLen C.int64_t,
	channel unsafe.Pointer,
	channelLen C.int64_t,
	pattern unsafe.Pointer,
	patternLen C.int64_t,
) {
	handlePubSubCallback(
		uintptr(callbackID),
		message,
		int64(messageLen),
		channel,
		int64(channelLen),
		pattern,
		int64(patternLen),
	)
}
