// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
)

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

func TestLateFailureCallbackAfterCancellationIsDropped(t *testing.T) {
	resultChannel := make(chan payload, 1)
	requestID := registerRequest(resultChannel)
	if _, ok := takeRequest(requestID); !ok {
		t.Fatal("expected cancellation to claim the registered request")
	}

	// A late failure callback must not attempt to resolve an obsolete request.
	deliverFailure(requestID, nil, 0)
}

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
