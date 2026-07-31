// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
//
// void successCallback(void *channelPtr, struct CommandResponse *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
import "C"

import (
	"context"
	"errors"
	"sync"
	"time"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"google.golang.org/protobuf/proto"
)

// PoolConfig holds configuration for a client-instance pool.
type PoolConfig struct {
	// Maximum number of clients in the pool. Default: 10.
	MaxSize int
	// Minimum idle clients to pre-warm at creation. Default: 1.
	MinIdle int
	// Idle eviction timeout. Default: 5 minutes.
	IdleTimeout time.Duration
	// Request timeout for cleanup operations. Default: 5 seconds.
	RequestTimeout time.Duration
	// Maximum time to wait when pool is exhausted. Default: 5 seconds.
	AcquireTimeout time.Duration
}

// DefaultPoolConfig returns a PoolConfig with sensible defaults.
func DefaultPoolConfig() PoolConfig {
	return PoolConfig{
		MaxSize:        10,
		MinIdle:        1,
		IdleTimeout:    5 * time.Minute,
		RequestTimeout: 5 * time.Second,
		AcquireTimeout: 5 * time.Second,
	}
}

// ClientPool is a bounded, LIFO-reuse pool of GlideClient instances backed
// by the Rust core. Each client has its own dedicated TCP connection.
//
// Use [NewClientPool] to create a pool. Borrow clients via [Acquire] and
// return them via [Release].
type ClientPool struct {
	poolID     int64
	config     PoolConfig
	clientConf *config.ClientConfiguration
	connReq    []byte // serialized ConnectionRequest protobuf
	mu         sync.Mutex
	closed     bool
	// pooledCache maps client_id → *PooledClient wrapper (reused across borrows)
	pooledCache map[int64]*PooledClient
}

// PooledClient wraps a Client borrowed from a pool.
// Its Close() method returns the client to the pool instead of destroying
// the underlying connection. This prevents accidentally destroying pooled connections.
type PooledClient struct {
	*Client
	pool     *ClientPool
	clientID int64
}

// Close returns the client to the pool. It does NOT destroy the underlying connection.
// Safe to call multiple times (idempotent).
func (pc *PooledClient) Close() {
	if pc.pool != nil {
		pc.pool.Release(pc.clientID)
	}
}

// NewClientPool creates a new client-instance pool.
//
// The pool pre-warms MinIdle connections in the background.
func NewClientPool(clientConfig *config.ClientConfiguration, poolConfig PoolConfig) (*ClientPool, error) {
	if poolConfig.MaxSize < 1 {
		return nil, errors.New("MaxSize must be >= 1")
	}
	if poolConfig.MinIdle > poolConfig.MaxSize {
		return nil, errors.New("MinIdle must be <= MaxSize")
	}
	if poolConfig.IdleTimeout <= 0 {
		poolConfig.IdleTimeout = 5 * time.Minute
	}
	if poolConfig.RequestTimeout <= 0 {
		poolConfig.RequestTimeout = 5 * time.Second
	}
	if poolConfig.AcquireTimeout <= 0 {
		poolConfig.AcquireTimeout = 5 * time.Second
	}

	// Reject pubsub subscriptions — pool state reset doesn't UNSUBSCRIBE
	if clientConfig.HasSubscription() {
		return nil, errors.New(
			"pool clients cannot have pubsub subscriptions configured; " +
				"use the main client's pubsub API instead",
		)
	}

	// Serialize connection request protobuf
	request, err := clientConfig.ToProtobuf()
	if err != nil {
		return nil, err
	}
	connReqBytes, err := proto.Marshal(request)
	if err != nil {
		return nil, err
	}

	// Create the Rust pool with async clients (uses Go's success/failure callbacks)
	clientType, err := buildAsyncClientType(
		C.SuccessCallback(unsafe.Pointer(C.successCallback)),
		C.FailureCallback(unsafe.Pointer(C.failureCallback)),
	)
	if err != nil {
		return nil, err
	}

	poolID := C.glide_pool_create(
		C.uint32_t(poolConfig.MaxSize),
		C.uint32_t(poolConfig.MinIdle),
		C.uint64_t(poolConfig.IdleTimeout.Milliseconds()),
		C.uint64_t(poolConfig.RequestTimeout.Milliseconds()),
		(*C.uint8_t)(unsafe.Pointer(&connReqBytes[0])),
		C.uintptr_t(len(connReqBytes)),
		&clientType,
	)
	if poolID < 0 {
		return nil, errors.New("failed to create pool")
	}

	pool := &ClientPool{
		poolID:      int64(poolID),
		config:      poolConfig,
		clientConf:  clientConfig,
		connReq:     connReqBytes,
		pooledCache: make(map[int64]*PooledClient),
	}

	// Connectivity probe: create one client to validate the config eagerly.
	// If this fails, propagate the actual connection error (not a timeout).
	probeClient, err := NewClient(clientConfig)
	if err != nil {
		pool.Close()
		return nil, err
	}
	probeClient.Close()

	return pool, nil
}

// Acquire borrows a client from the pool with the configured timeout.
// Returns the client_id handle.
func (p *ClientPool) Acquire(ctx context.Context) (int64, error) {
	return p.AcquireWithTimeout(ctx, p.config.AcquireTimeout)
}

// AcquireWithTimeout borrows a client from the pool with a custom timeout.
func (p *ClientPool) AcquireWithTimeout(ctx context.Context, timeout time.Duration) (int64, error) {
	if p.closed {
		return -1, errors.New("pool is closed")
	}

	deadline := time.Now().Add(timeout)
	backoff := time.Millisecond

	for {
		select {
		case <-ctx.Done():
			return -1, ctx.Err()
		default:
		}

		clientID := C.glide_pool_try_acquire(C.uint64_t(p.poolID))
		if clientID >= 0 {
			return int64(clientID), nil
		}
		if clientID == -2 {
			return -1, errors.New("invalid pool — pool was destroyed")
		}

		remaining := time.Until(deadline)
		if remaining <= 0 {
			return -1, errors.New("pool exhausted: timed out waiting for a client")
		}

		sleep := backoff
		if sleep > remaining {
			sleep = remaining
		}
		time.Sleep(sleep)
		backoff *= 2
		if backoff > 50*time.Millisecond {
			backoff = 50 * time.Millisecond
		}
	}
}

// Release returns a borrowed client to the pool.
func (p *ClientPool) Release(clientID int64) {
	C.glide_pool_release(C.uint64_t(p.poolID), C.uint64_t(clientID))
}

// GetClient returns a usable Client wrapper for the given client_id.
// The wrapper is cached and reused across borrows of the same client_id.
//
// The pooled client uses synchronous command dispatch (not async callbacks).
// Do NOT call Close() on the returned Client — use Release(clientID) instead.
func (p *ClientPool) GetClient(clientID int64) (*PooledClient, error) {
	p.mu.Lock()
	defer p.mu.Unlock()

	if cached, ok := p.pooledCache[clientID]; ok {
		return cached, nil
	}

	adapterPtr := C.glide_pool_get_client_ptr(C.uint64_t(clientID))
	if adapterPtr == nil {
		return nil, errors.New("client_id has no associated client")
	}

	// Create a Client wrapper pointing to the pooled adapter.
	// The adapter is an AsyncClient type — commands via C.command() fire callbacks.
	client := &Client{
		baseClient: baseClient{
			coreClient: unsafe.Pointer(adapterPtr),
			pending:    make(map[unsafe.Pointer]struct{}),
			mu:         &sync.Mutex{},
		},
	}
	client.setMessageHandler(NewMessageHandler(nil, nil))

	// Register in client registry for pubsub (if needed)
	registerClient(&client.baseClient, uintptr(unsafe.Pointer(adapterPtr)))

	pooled := &PooledClient{
		Client:   client,
		pool:     p,
		clientID: clientID,
	}

	p.pooledCache[clientID] = pooled
	return pooled, nil
}

// IdleCount returns the number of idle clients in the pool.
func (p *ClientPool) IdleCount() int {
	var idle C.uint32_t
	C.glide_pool_metrics(C.uint64_t(p.poolID), &idle, nil, nil)
	return int(idle)
}

// ActiveCount returns the number of currently borrowed clients.
func (p *ClientPool) ActiveCount() int {
	var active C.uint32_t
	C.glide_pool_metrics(C.uint64_t(p.poolID), nil, &active, nil)
	return int(active)
}

// TotalCount returns the total number of clients (idle + active + creating).
func (p *ClientPool) TotalCount() int {
	var total C.uint32_t
	C.glide_pool_metrics(C.uint64_t(p.poolID), nil, nil, &total)
	return int(total)
}

// Close destroys the pool and all pooled clients.
func (p *ClientPool) Close() {
	p.mu.Lock()
	defer p.mu.Unlock()

	if p.closed {
		return
	}
	p.closed = true
	C.glide_pool_destroy(C.uint64_t(p.poolID))
	p.pooledCache = nil
}

// NewClusterClientPool creates a new client-instance pool for cluster configurations.
//
// The pool pre-warms MinIdle connections in the background.
func NewClusterClientPool(clientConfig *config.ClusterClientConfiguration, poolConfig PoolConfig) (*ClientPool, error) {
	if poolConfig.MaxSize < 1 {
		return nil, errors.New("MaxSize must be >= 1")
	}
	if poolConfig.MinIdle > poolConfig.MaxSize {
		return nil, errors.New("MinIdle must be <= MaxSize")
	}
	if poolConfig.IdleTimeout <= 0 {
		poolConfig.IdleTimeout = 5 * time.Minute
	}
	if poolConfig.RequestTimeout <= 0 {
		poolConfig.RequestTimeout = 5 * time.Second
	}
	if poolConfig.AcquireTimeout <= 0 {
		poolConfig.AcquireTimeout = 5 * time.Second
	}

	// Reject pubsub subscriptions — pool state reset doesn't UNSUBSCRIBE
	if clientConfig.HasSubscription() {
		return nil, errors.New(
			"pool clients cannot have pubsub subscriptions configured; " +
				"use the main client's pubsub API instead",
		)
	}

	// Serialize connection request protobuf
	request, err := clientConfig.ToProtobuf()
	if err != nil {
		return nil, err
	}
	connReqBytes, err := proto.Marshal(request)
	if err != nil {
		return nil, err
	}

	// Create the Rust pool with async clients (uses Go's success/failure callbacks)
	clientType, err := buildAsyncClientType(
		C.SuccessCallback(unsafe.Pointer(C.successCallback)),
		C.FailureCallback(unsafe.Pointer(C.failureCallback)),
	)
	if err != nil {
		return nil, err
	}

	poolID := C.glide_pool_create(
		C.uint32_t(poolConfig.MaxSize),
		C.uint32_t(poolConfig.MinIdle),
		C.uint64_t(poolConfig.IdleTimeout.Milliseconds()),
		C.uint64_t(poolConfig.RequestTimeout.Milliseconds()),
		(*C.uint8_t)(unsafe.Pointer(&connReqBytes[0])),
		C.uintptr_t(len(connReqBytes)),
		&clientType,
	)
	if poolID < 0 {
		return nil, errors.New("failed to create pool")
	}

	pool := &ClientPool{
		poolID:      int64(poolID),
		config:      poolConfig,
		clientConf:  nil, // cluster config — standalone field unused
		connReq:     connReqBytes,
		pooledCache: make(map[int64]*PooledClient),
	}

	// Connectivity probe: create one client to validate the config eagerly.
	// If this fails, propagate the actual connection error (not a timeout).
	probeClient, err := NewClusterClient(clientConfig)
	if err != nil {
		pool.Close()
		return nil, err
	}
	probeClient.Close()

	return pool, nil
}
