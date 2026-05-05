# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import threading
from dataclasses import dataclass
from enum import Enum
from typing import ClassVar, Optional

from glide_shared.protobuf.connection_request_pb2 import (
    EvictionPolicy as ProtobufEvictionPolicy,
)


class EvictionPolicy(Enum):
    """
    Defined policies for evicting entries from the client-side cache when it reaches its maximum size.

    When the cache is full, it must evict existing entries to make room for new ones.

    Attributes:
        LRU (Least Recently Used) : Evicts the least recently accessed entry. Best for recency-biased
            workloads like event streams and job queues.
        LFU (Least Frequently Used) : Evicts the least frequently accessed entry. Best for frequency-biased
            workloads like user profiles and product catalogs.
    """

    LRU = ProtobufEvictionPolicy.LRU
    LFU = ProtobufEvictionPolicy.LFU


@dataclass
class ClientSideCache:
    """
    Configuration for client-side caching with TTL-based expiration.

    This class configures a local cache that stores read command responses
    on the client side to reduce network round-trips and server load. The cache
    uses Time-To-Live (TTL) based expiration, where entries are automatically
    removed after a specified duration.

    Cached entries expire based on TTL. Server-side key changes are not propagated
    to the cache, so values may become stale before TTL expires.
    Expiration is lazy — entries are removed when accessed after their TTL, not
    proactively in the background.
    Supported read commands: GET, HGETALL, SMEMBERS.
    """

    # Class variables - shared across all instances
    _counter_lock: ClassVar[threading.Lock] = threading.Lock()
    _counter: ClassVar[int] = 0

    # Instance variables - unique per instance
    cache_id: str
    max_cache_kb: int
    entry_ttl_ms: int
    eviction_policy: Optional[EvictionPolicy] = None
    enable_metrics: bool = False

    @classmethod
    def create(
        cls,
        max_cache_kb: int,
        entry_ttl_ms: int,
        eviction_policy: Optional[EvictionPolicy] = None,
        enable_metrics: bool = False,
    ) -> "ClientSideCache":
        """
        Create a new client-side cache configuration with an auto-generated unique ID.

        In order for 2 clients to share the same cache, they must be created with the
        same ``ClientSideCache`` instance.

        - Clients with different ``ClientSideCache`` instances will have separate caches,
          even if the configurations are identical.
        - Clients using different DBs cannot share the same cache.
        - Clients using different ACL users cannot share the same cache.

        Args:
            max_cache_kb (int): Maximum size of the cache in kilobytes (KB). This limits
                the total memory used by cached keys and values. When this limit is reached,
                entries are evicted based on the eviction policy.
            entry_ttl_ms (int): Time-To-Live for cached entries in milliseconds. After this
                duration, entries automatically expire and are removed from the cache.
                Set to 0 to disable TTL expiration (entries remain until evicted or invalidated).
            eviction_policy (Optional[EvictionPolicy]): Policy for evicting entries when
                the cache reaches its maximum size. If not specified (None), the default
                policy of LRU will be used.
                See `EvictionPolicy` enum for available options.
            enable_metrics (bool): If True, enables collection of cache metrics such as hit/miss rates.

        Returns:
            ClientSideCache: A new ClientSideCache instance.

        Example:
            Create a basic cache:
            >>> cache = ClientSideCache.create(
            ...     max_cache_kb=10,  # 10 KB
            ...     entry_ttl_ms=60000,  # 1 minute TTL
            ...     eviction_policy=EvictionPolicy.LRU,
            ...     enable_metrics=True
            ... )
        """
        if max_cache_kb <= 0:
            raise ValueError("max_cache_kb must be positive")
        if entry_ttl_ms < 0:
            raise ValueError("entry_ttl_ms must be non-negative (0 = no expiration)")

        with cls._counter_lock:
            cache_id = str(cls._counter)
            cls._counter += 1

        return cls(
            cache_id=cache_id,
            max_cache_kb=max_cache_kb,
            entry_ttl_ms=entry_ttl_ms,
            eviction_policy=eviction_policy,
            enable_metrics=enable_metrics,
        )
