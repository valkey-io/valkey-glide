// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"log"
	"sync"
	"sync/atomic"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

const requestRegistryShardCount = 64

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

	msg := C.GoStringN((*C.char)(message), message_len)
	cha := C.GoStringN((*C.char)(channel), channel_len)
	pat := models.CreateNilStringResult()
	if pattern_len > 0 && pattern != nil {
		pat = models.CreateStringResult(C.GoStringN((*C.char)(pattern), pattern_len))
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
