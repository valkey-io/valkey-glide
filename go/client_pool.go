// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
//
// void successCallback(uintptr_t requestID, struct CommandResponse *message);
// void failureCallback(uintptr_t requestID, char *errMessage, RequestErrorType errType);
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

const customCredentialProviderPoolError = "pool clients cannot use a custom IAM credentials provider; " +
	"configure IAM without a credential provider to use the default AWS credential chain"

var errCustomCredentialProviderPool = errors.New(customCredentialProviderPoolError)

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
	// Maximum inactivity time for a borrowed client before the pool reclaims it.
	// The timer resets on every command sent. The abandon monitor skips clients
	// executing blocking commands (BLPOP, XREAD BLOCK, etc.).
	// Set to a negative value to disable abandon detection. Default: 5 minutes.
	// Zero is treated as "use default" (5 minutes).
	AbandonTimeout time.Duration
}

// DefaultPoolConfig returns a PoolConfig with sensible defaults.
func DefaultPoolConfig() PoolConfig {
	return PoolConfig{
		MaxSize:        10,
		MinIdle:        1,
		IdleTimeout:    5 * time.Minute,
		RequestTimeout: 5 * time.Second,
		AcquireTimeout: 5 * time.Second,
		AbandonTimeout: 5 * time.Minute,
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
	// pooledCache maps lease id → the *PooledClient of that borrow. A borrow's
	// wrapper is never reused, so a stale one cannot reach the next borrow.
	pooledCache map[int64]*PooledClient
}

// PooledClient wraps a Client borrowed from a pool.
// Its Close() method returns the client to the pool instead of destroying
// the underlying connection. This prevents accidentally destroying pooled connections.
type PooledClient struct {
	*Client
	pool      *ClientPool
	leaseID   int64
	closeOnce sync.Once
}

// Close returns the client to the pool. It does NOT destroy the underlying connection.
// Safe to call multiple times (idempotent). After Close, commands on this
// PooledClient, or on its embedded Client, fail with a ClosingError.
func (pc *PooledClient) Close() {
	pc.closeOnce.Do(func() {
		pc.detach()
		pc.pool.release(pc.leaseID)
	})
}

// detach makes later commands fail as on a closed client, fails the ones in
// flight (dropping the last adapter reference cancels them without a
// callback), and gives back this wrapper's adapter reference without closing
// the connection.
func (pc *PooledClient) detach() {
	pc.mu.Lock()
	adapter := pc.coreClient
	pc.coreClient = nil
	if adapter != nil {
		if getClientByPtr(uintptr(adapter)) == &pc.baseClient {
			unregisterClient(uintptr(adapter))
		}
		pc.failPendingRequests(NewClosingError("pooled client was returned to the pool"))
	}
	pc.mu.Unlock()
	if adapter != nil {
		C.glide_pool_release_client_ref(adapter)
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
	if poolConfig.AbandonTimeout == 0 {
		poolConfig.AbandonTimeout = 5 * time.Minute
	} else if poolConfig.AbandonTimeout < 0 {
		poolConfig.AbandonTimeout = 0 // negative → disabled (FFI interprets 0ms as disabled)
	}

	if clientConfig.GetCredentialProvider() != nil {
		return nil, errCustomCredentialProviderPool
	}

	// Reject pubsub subscriptions — pool state reset doesn't UNSUBSCRIBE
	if clientConfig.HasSubscription() {
		return nil, errors.New(
			"pool clients cannot have pubsub subscriptions configured; " +
				"use the main client's pubsub API instead",
		)
	}

	// Reject a custom address resolver. It is a per-client callback registered
	// with the core outside the serialized ConnectionRequest; glide_pool_create
	// takes only the bytes, so the pool cannot forward it. The probe below runs
	// a real client and can pass, but pooled connections would then use the
	// untranslated address and fail at runtime — so fail fast here instead.
	if clientConfig.GetAddressResolver() != nil {
		return nil, errors.New(
			"pool clients cannot use a custom address resolver; " +
				"resolve addresses before configuring the pool",
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
		C.uint64_t(poolConfig.AbandonTimeout.Milliseconds()),
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
// Returns the borrow's lease id, for [ClientPool.GetClient] and [ClientPool.Release].
// Each borrow gets a new one, even of the same connection.
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

// Release returns a borrow to the pool by its lease id. Releasing a borrow
// that has already ended does nothing.
func (p *ClientPool) Release(leaseID int64) {
	p.mu.Lock()
	pooled := p.pooledCache[leaseID]
	p.mu.Unlock()
	if pooled != nil {
		pooled.Close()
		return
	}
	p.release(leaseID)
}

// release ends the lease under p.mu, which GetClient also holds, so GetClient
// never wraps a lease that ends before the wrapper is cached.
func (p *ClientPool) release(leaseID int64) {
	p.mu.Lock()
	defer p.mu.Unlock()
	delete(p.pooledCache, leaseID)
	C.glide_pool_release(C.uint64_t(p.poolID), C.uint64_t(leaseID))
}

// GetClient returns the PooledClient of a current borrow, by its lease id.
// Repeated calls for one borrow return the same wrapper; a borrow that has
// ended returns a ClosingError.
//
// The pooled client uses synchronous command dispatch (not async callbacks).
func (p *ClientPool) GetClient(leaseID int64) (*PooledClient, error) {
	p.mu.Lock()
	defer p.mu.Unlock()

	if p.closed {
		return nil, NewClosingError("pool is closed")
	}
	if cached, ok := p.pooledCache[leaseID]; ok {
		return cached, nil
	}

	adapterPtr := C.glide_pool_lease_client(C.uint64_t(p.poolID), C.uint64_t(leaseID), nil)
	if adapterPtr == nil {
		return nil, NewClosingError("pooled client was returned to the pool")
	}

	// Create a Client wrapper pointing to the pooled adapter.
	// The adapter is an AsyncClient type — commands via C.command() fire callbacks.
	// connReqBytes carries the pool's serialized ConnectionRequest so ScopedConnection
	// works on the borrowed client. clientConfig is intentionally not set: ScopedConnection
	// always uses connReqBytes when present, so a config object here would never be read.
	client := &Client{
		baseClient: baseClient{
			coreClient: unsafe.Pointer(adapterPtr),
			pending:    make(map[uintptr]struct{}),
			mu:         &sync.Mutex{},
		},
		connReqBytes: p.connReq,
	}
	client.setMessageHandler(NewMessageHandler(nil, nil))

	// Register in client registry for pubsub (if needed)
	registerClient(&client.baseClient, uintptr(unsafe.Pointer(adapterPtr)))

	pooled := &PooledClient{
		Client:  client,
		pool:    p,
		leaseID: leaseID,
	}

	p.pooledCache[leaseID] = pooled
	return pooled, nil
}

func (p *ClientPool) metrics() C.PoolMetrics {
	return C.glide_pool_metrics(C.uint64_t(p.poolID))
}

// IdleCount returns the number of idle clients in the pool.
func (p *ClientPool) IdleCount() int {
	return int(p.metrics().idle)
}

// ActiveCount returns the number of currently borrowed clients.
func (p *ClientPool) ActiveCount() int {
	return int(p.metrics().active)
}

// TotalCount returns the total number of clients (idle + active + creating).
func (p *ClientPool) TotalCount() int {
	return int(p.metrics().total)
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
	for _, pooled := range p.pooledCache {
		// Not closeOnce: a PooledClient.Close in progress waits on p.mu.
		pooled.detach()
	}
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

	if poolConfig.AbandonTimeout == 0 {
		poolConfig.AbandonTimeout = 5 * time.Minute
	} else if poolConfig.AbandonTimeout < 0 {
		poolConfig.AbandonTimeout = 0 // negative → disabled (FFI interprets 0ms as disabled)
	}

	if clientConfig.GetCredentialProvider() != nil {
		return nil, errCustomCredentialProviderPool
	}

	// Reject pubsub subscriptions — pool state reset doesn't UNSUBSCRIBE
	if clientConfig.HasSubscription() {
		return nil, errors.New(
			"pool clients cannot have pubsub subscriptions configured; " +
				"use the main client's pubsub API instead",
		)
	}

	// Reject a custom address resolver. It is a per-client callback registered
	// with the core outside the serialized ConnectionRequest; glide_pool_create
	// takes only the bytes, so the pool cannot forward it. The probe below runs
	// a real client and can pass, but pooled connections would then use the
	// untranslated address and fail at runtime — so fail fast here instead.
	if clientConfig.GetAddressResolver() != nil {
		return nil, errors.New(
			"pool clients cannot use a custom address resolver; " +
				"resolve addresses before configuring the pool",
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
		C.uint64_t(poolConfig.AbandonTimeout.Milliseconds()),
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
