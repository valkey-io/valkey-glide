// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"fmt"
	"sync"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/internal/protobuf"
)

// EvictionPolicy represents the eviction policy for client-side cache entries.
// When the cache reaches its maximum size, it must evict existing entries to make room for new ones.
type EvictionPolicy int

const (
	// EvictionPolicyLRU (Least Recently Used) evicts the least recently accessed entry.
	// Best for recency-biased workloads like event streams and job queues.
	EvictionPolicyLRU EvictionPolicy = 0
	// EvictionPolicyLFU (Least Frequently Used) evicts the least frequently accessed entry.
	// Best for frequency-biased workloads like user profiles and product catalogs.
	EvictionPolicyLFU EvictionPolicy = 1
)

// String returns the string representation of the EvictionPolicy.
func (e EvictionPolicy) String() string {
	switch e {
	case EvictionPolicyLRU:
		return "LRU"
	case EvictionPolicyLFU:
		return "LFU"
	default:
		return fmt.Sprintf("EvictionPolicy(%d)", int(e))
	}
}

// ClientSideCache represents configuration for client-side caching with TTL-based expiration.
//
// This struct configures a local cache that stores read command responses
// on the client side to reduce network round-trips and server load. The cache
// uses Time-To-Live (TTL) based expiration, where entries are automatically
// removed after a specified duration.
//
// Important:
//   - Glide currently supports TTL-based caching only. Invalidation-based
//     client-side caching (where the server notifies clients of key changes) is not
//     currently supported. This means cached values may become stale if updated on
//     the server before the TTL expires.
//   - Currently, Glide's client-side cache supports lazy eviction only. Expired entries
//     are removed only when accessed after their TTL has expired. There is no proactive
//     background cleanup of expired entries.
//   - Currently, only read commands that retrieve entire values are cached (GET, HGETALL, SMEMBERS).
type ClientSideCache struct {
	// CacheId is a unique identifier for the cache instance.
	// Multiple clients can share the same cache by using the same cache ID.
	CacheId string

	// MaxCacheKb is the maximum size of the cache in kilobytes (KB).
	// This limits the total memory used by cached keys and values.
	// When this limit is reached, entries are evicted based on the eviction policy.
	MaxCacheKb uint64

	// EntryTtlMs is the Time-To-Live for cached entries in milliseconds.
	// After this duration, entries automatically expire and are removed from the cache.
	// If nil, no expiration is applied.
	EntryTtlMs *uint64

	// EvictionPolicy is the policy for evicting entries when the cache reaches its maximum size.
	// If nil, the default policy of LRU will be used.
	EvictionPolicy *EvictionPolicy

	// EnableMetrics enables collection of cache metrics such as hit/miss rates.
	EnableMetrics bool
}

// Package-level variables for generating unique cache IDs
var (
	uuidPrefix string
	counter    uint64
	counterMu  sync.Mutex
)

// Initialize the UUID prefix once when the package is loaded
func init() {
	uuidPrefix = uuid.New().String()[:8]
}

// NewClientSideCache creates a new ClientSideCache configuration with an auto-generated unique ID.
//
// This function configures a local cache that stores read command responses
// on the client side to reduce network round-trips and server load. The cache
// uses Time-To-Live (TTL) based expiration, where entries are automatically
// removed after a specified duration.
//
// Important:
//   - Glide currently supports TTL-based caching only. Invalidation-based
//     client-side caching (where the server notifies clients of key changes) is not
//     currently supported. This means cached values may become stale if updated on
//     the server before the TTL expires.
//   - Currently, Glide's client-side cache supports lazy eviction only. Expired entries
//     are removed only when accessed after their TTL has expired. There is no proactive
//     background cleanup of expired entries.
//   - Currently, only read commands that retrieve entire values are cached (GET, HGETALL, SMEMBERS).
//
// Note: In order for 2 clients to share the same cache, they must be
// created with the same ClientSideCache instance.
//   - Clients with different ClientSideCache instances will have separate caches,
//     even if the configurations are identical.
//   - Clients using different db's cannot share the same cache.
//   - Clients using different ACL users cannot share the same cache.
//
// Parameters:
//   - maxCacheKb: Maximum size of the cache in kilobytes (KB). Must be positive.
//
// Returns:
//   - *ClientSideCache: A new ClientSideCache instance with auto-generated cache ID.
//
// Example:
//
//	cache := NewClientSideCache(1024) // 1 MB cache
//	cache.EntryTtlMs = &[]uint64{60000}[0] // 1 minute TTL
//	cache.EvictionPolicy = &[]EvictionPolicy{EvictionPolicyLRU}[0]
//	cache.EnableMetrics = true
func NewClientSideCache(maxCacheKb uint64) *ClientSideCache {
	if maxCacheKb == 0 {
		panic("maxCacheKb must be positive")
	}

	counterMu.Lock()
	cacheId := fmt.Sprintf("%s-%d", uuidPrefix, counter)
	counter++
	counterMu.Unlock()

	return &ClientSideCache{
		CacheId:        cacheId,
		MaxCacheKb:     maxCacheKb,
		EntryTtlMs:     nil,
		EvictionPolicy: nil,
		EnableMetrics:  false,
	}
}

// WithEntryTtlMs sets the TTL for cache entries in milliseconds.
// Returns the same ClientSideCache instance for method chaining.
func (c *ClientSideCache) WithEntryTtlMs(ttlMs uint64) *ClientSideCache {
	if ttlMs == 0 {
		panic("ttlMs must be positive")
	}
	c.EntryTtlMs = &ttlMs
	return c
}

// WithEvictionPolicy sets the eviction policy for the cache.
// Returns the same ClientSideCache instance for method chaining.
func (c *ClientSideCache) WithEvictionPolicy(policy EvictionPolicy) *ClientSideCache {
	c.EvictionPolicy = &policy
	return c
}

// WithMetrics enables or disables cache metrics collection.
// Returns the same ClientSideCache instance for method chaining.
func (c *ClientSideCache) WithMetrics(enable bool) *ClientSideCache {
	c.EnableMetrics = enable
	return c
}

// toProtobuf converts the ClientSideCache configuration to protobuf format.
// This method is used internally to serialize the cache configuration for
// communication with the Rust core.
func (c *ClientSideCache) toProtobuf() *protobuf.ClientSideCache {
	protoCache := &protobuf.ClientSideCache{
		CacheId:       c.CacheId,
		MaxCacheKb:    c.MaxCacheKb,
		EnableMetrics: c.EnableMetrics,
	}

	if c.EntryTtlMs != nil {
		protoCache.EntryTtlMs = *c.EntryTtlMs
	}

	if c.EvictionPolicy != nil {
		switch *c.EvictionPolicy {
		case EvictionPolicyLRU:
			policy := protobuf.EvictionPolicy_LRU
			protoCache.EvictionPolicy = &policy
		case EvictionPolicyLFU:
			policy := protobuf.EvictionPolicy_LFU
			protoCache.EvictionPolicy = &policy
		}
	}

	return protoCache
}
