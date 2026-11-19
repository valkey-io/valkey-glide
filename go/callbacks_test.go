// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"bytes"
	"context"
	"errors"
	"log"
	"reflect"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
	"unsafe"
)

// TestBeginRequestRegistersAndTracksRequest verifies that new requests are visible to both the FFI callback and Close.
func TestBeginRequestRegistersAndTracksRequest(t *testing.T) {
	resultChannel := make(chan payload, 1)
	client := &baseClient{
		pending: make(map[uintptr]struct{}),
		mu:      &sync.Mutex{},
	}

	client.mu.Lock()
	requestID := client.beginRequest(resultChannel)
	client.mu.Unlock()

	if _, ok := client.pending[requestID]; !ok {
		t.Fatal("expected request to be tracked as pending")
	}
	registeredChannel, ok := takeRequest(requestID)
	if !ok {
		t.Fatal("expected request to be registered")
	}
	if registeredChannel != resultChannel {
		t.Fatal("registered channel does not match the result channel")
	}
}

// TestRequestRegistryClaimsEachRequestOnce verifies that claimed IDs cannot be claimed again.
func TestRequestRegistryClaimsEachRequestOnce(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)

	claimedChannel, ok := takeRequest(requestID)
	if !ok {
		t.Fatal("expected registered request to be claimed")
	}
	if claimedChannel != resultChannel {
		t.Fatal("claimed channel does not match the registered channel")
	}
	if _, ok := takeRequest(requestID); ok {
		t.Fatal("request must not be claimed twice")
	}
}

// TestRequestRegistryConcurrentClaimsHaveOneWinner verifies atomic request ownership.
func TestRequestRegistryConcurrentClaimsHaveOneWinner(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)
	const contenders = 32

	var claims atomic.Int32
	var wg sync.WaitGroup
	for range contenders {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, ok := takeRequest(requestID); ok {
				claims.Add(1)
			}
		}()
	}
	wg.Wait()

	if got := claims.Load(); got != 1 {
		t.Fatalf("expected exactly one claimant, got %d", got)
	}
}

func TestRequestRegistryConcurrentRegistrationsAreDistinct(t *testing.T) {
	const registrations = 256
	requestIDs := make(chan uintptr, registrations)
	var wg sync.WaitGroup

	for range registrations {
		wg.Add(1)
		go func() {
			defer wg.Done()
			requestIDs <- registerRequest(make(chan payload, 1))
		}()
	}
	wg.Wait()
	close(requestIDs)

	seen := make(map[uintptr]struct{}, registrations)
	for requestID := range requestIDs {
		if _, exists := seen[requestID]; exists {
			t.Fatalf("duplicate request ID %d", requestID)
		}
		seen[requestID] = struct{}{}
		if _, ok := takeRequest(requestID); !ok {
			t.Fatalf("request ID %d was not claimable", requestID)
		}
	}
}

func BenchmarkRequestRegistryParallel(b *testing.B) {
	b.ReportAllocs()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			requestID := registerRequest(make(chan payload, 1))
			if _, ok := takeRequest(requestID); !ok {
				b.Fatal("registered request was not claimable")
			}
		}
	})
}

// TestLateSuccessCallbackAfterCancellationIsDropped verifies that cancelled requests discard late successes.
func TestLateSuccessCallbackAfterCancellationIsDropped(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)
	client := &baseClient{
		pending: map[uintptr]struct{}{requestID: {}},
		mu:      &sync.Mutex{},
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.waitForResponse(ctx, requestID, resultChannel)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context cancellation, got %v", err)
	}
	if _, ok := takeRequest(requestID); ok {
		t.Fatal("cancelled request must be removed from the registry")
	}

	// A late callback must not dereference a released Go pointer or panic.
	deliverSuccess(requestID, nil)
}

// TestLateFailureCallbackAfterCancellationIsDropped verifies that cancelled requests discard late failures.
func TestLateFailureCallbackAfterCancellationIsDropped(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)
	if _, ok := takeRequest(requestID); !ok {
		t.Fatal("expected cancellation to claim the registered request")
	}

	// A late failure callback must not attempt to resolve an obsolete request.
	deliverFailure(requestID, nil, 0)
}

// TestFailureCallbackDeliversCopiedError verifies that a registered request receives a converted Go error.
func TestFailureCallbackDeliversCopiedError(t *testing.T) {
	const disconnectErrorType = 3 // RequestErrorType::Disconnect

	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)
	borrowedMessage := []byte("connection lost\x00")

	callback := reflect.ValueOf(deliverFailure)
	cErrorMessage := unsafe.Pointer(&borrowedMessage[0])
	callbackMessage := reflect.NewAt(callback.Type().In(1), unsafe.Pointer(&cErrorMessage)).Elem()
	callbackErrorType := reflect.New(callback.Type().In(2)).Elem()
	callbackErrorType.SetUint(disconnectErrorType)

	callback.Call([]reflect.Value{
		reflect.ValueOf(requestID),
		callbackMessage,
		callbackErrorType,
	})
	clear(borrowedMessage)

	result := <-resultChannel
	if result.value != nil {
		t.Fatalf("expected failure without a response value, got %#v", result.value)
	}
	if result.error == nil {
		t.Fatal("expected a converted Go error")
	}
	var disconnectError *DisconnectError
	if !errors.As(result.error, &disconnectError) {
		t.Fatalf("expected DisconnectError, got %T", result.error)
	}
	if !strings.Contains(result.error.Error(), "connection lost") {
		t.Fatalf("expected error to contain copied C message, got %q", result.error)
	}
}

// TestWaitForResponseDiscardsCallbackWinningCancellation verifies cleanup when a callback claims first.
func TestWaitForResponseDiscardsCallbackWinningCancellation(t *testing.T) {
	resultChannel := make(chan payload)
	requestID := registerRequest(resultChannel)
	if _, ok := takeRequest(requestID); !ok {
		t.Fatal("expected callback to claim the registered request")
	}

	client := &baseClient{mu: &sync.Mutex{}}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.waitForResponse(ctx, requestID, resultChannel)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context cancellation, got %v", err)
	}

	delivered := make(chan struct{})
	go func() {
		resultChannel <- payload{}
		close(delivered)
	}()

	select {
	case <-delivered:
	case <-time.After(time.Second):
		t.Fatal("expected cancellation cleanup to drain the callback response")
	}
}

// TestLateSuccessCallbackAfterClientCloseIsDropped verifies that Close discards late successes.
func TestLateSuccessCallbackAfterClientCloseIsDropped(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)
	client := &baseClient{
		pending: map[uintptr]struct{}{requestID: {}},
		mu:      &sync.Mutex{},
	}

	client.failPendingRequests(NewClosingError("client closed"))
	result := <-resultChannel
	if result.error == nil {
		t.Fatal("expected close to fail the pending request")
	}
	if _, ok := takeRequest(requestID); ok {
		t.Fatal("closed request must be removed from the registry")
	}

	// A callback arriving after Close is discarded instead of touching Go memory.
	deliverSuccess(requestID, nil)
}

// TestDuplicateSuccessCallbackIsDropped verifies that only the first callback response is delivered.
func TestDuplicateSuccessCallbackIsDropped(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)

	deliverSuccess(requestID, nil)
	result := <-resultChannel
	if result.value != nil || result.error != nil {
		t.Fatalf("unexpected callback payload: %#v", result)
	}

	// The second callback is a no-op instead of a second send or a panic.
	deliverSuccess(requestID, nil)
}

func TestHandlePubSubCallbackCopiesBeforeReturnAndCapturesHandler(t *testing.T) {
	const callbackID = uintptr(0xA11CE)
	const providerResolverID = uintptr(0xB0B)
	if callbackID == providerResolverID {
		t.Fatal("test requires distinct callback and provider/resolver IDs")
	}
	client := &baseClient{
		pending:        make(map[uintptr]struct{}),
		mu:             &sync.Mutex{},
		messageHandler: NewMessageHandler(nil, nil),
		resolverID:     providerResolverID,
	}
	registerClient(client, callbackID)

	message := []byte("message")
	channel := []byte("channel")
	pattern := []byte("pattern")
	if !handlePubSubCallback(
		callbackID,
		unsafe.Pointer(&message[0]),
		int64(len(message)),
		unsafe.Pointer(&channel[0]),
		int64(len(channel)),
		unsafe.Pointer(&pattern[0]),
		int64(len(pattern)),
	) {
		t.Fatal("valid callback payload was rejected")
	}

	// Rust may release its borrowed buffers and Close may unregister the client
	// as soon as the callback returns. Accepted delivery must use only copied
	// bytes and the already-captured handler.
	clear(message)
	clear(channel)
	clear(pattern)
	unregisterClient(callbackID)

	select {
	case received := <-client.messageHandler.GetQueue().WaitForMessage():
		if received.Message != "message" || received.Channel != "channel" {
			t.Fatalf("unexpected PubSub message: %#v", received)
		}
		if received.Pattern.IsNil() || received.Pattern.Value() != "pattern" {
			t.Fatalf("unexpected PubSub pattern: %#v", received.Pattern)
		}
	case <-time.After(time.Second):
		t.Fatal("valid PubSub callback was not delivered")
	}
}

func TestHandlePubSubCallbackRejectsInvalidPointersAndLengths(t *testing.T) {
	payload := []byte("payload")
	payloadPointer := unsafe.Pointer(&payload[0])
	validLength := int64(len(payload))
	overGoStringNCapacity := maxGoStringNLength + 1

	tests := map[string]struct {
		message, channel, pattern          unsafe.Pointer
		messageLen, channelLen, patternLen int64
	}{
		"zero callback":     {payloadPointer, payloadPointer, nil, validLength, validLength, 0},
		"nil message":       {nil, payloadPointer, nil, validLength, validLength, 0},
		"nil channel":       {payloadPointer, nil, nil, validLength, validLength, 0},
		"nil pattern":       {payloadPointer, payloadPointer, nil, validLength, validLength, 1},
		"negative message":  {payloadPointer, payloadPointer, nil, -1, validLength, 0},
		"negative channel":  {payloadPointer, payloadPointer, nil, validLength, -1, 0},
		"negative pattern":  {payloadPointer, payloadPointer, nil, validLength, validLength, -1},
		"oversized message": {payloadPointer, payloadPointer, nil, overGoStringNCapacity, validLength, 0},
		"oversized channel": {payloadPointer, payloadPointer, nil, validLength, overGoStringNCapacity, 0},
		"oversized pattern": {payloadPointer, payloadPointer, payloadPointer, validLength, validLength, overGoStringNCapacity},
	}

	for name, test := range tests {
		t.Run(name, func(t *testing.T) {
			var output bytes.Buffer
			originalOutput := log.Writer()
			originalFlags := log.Flags()
			log.SetOutput(&output)
			log.SetFlags(0)
			t.Cleanup(func() {
				log.SetOutput(originalOutput)
				log.SetFlags(originalFlags)
			})

			callbackID := uintptr(1)
			if name == "zero callback" {
				callbackID = 0
			}
			if handlePubSubCallback(
				callbackID,
				test.message,
				test.messageLen,
				test.channel,
				test.channelLen,
				test.pattern,
				test.patternLen,
			) {
				t.Fatal("invalid callback payload was accepted")
			}
			if output.String() != "invalid PubSub callback payload\n" {
				t.Fatalf("unexpected invalid-payload log: %q", output.String())
			}
		})
	}
}
