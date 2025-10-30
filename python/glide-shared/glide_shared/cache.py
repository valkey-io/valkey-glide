# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from enum import Enum
import threading
import uuid
from typing import Optional, ClassVar
from dataclasses import dataclass
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
        TINY_LFU (Tiny Least Frequently Used) : Combines frequency and recency based eviction.
            When the cache is full, it only admits new entries if they're accessed more frequently than existing ones.
            This prevents rarely-accessed or one-time keys from evicting popular entries. **Once admitted,
            entries are evicted based on least recent access (LRU)**. Best for most workloads.
    """

    LRU = ProtobufEvictionPolicy.LRU
    TINY_LFU = ProtobufEvictionPolicy.TINY_LFU


@dataclass
class ClientSideCache:
    """
    Configuration for client-side caching with TTL-based expiration.

    This class configures a local cache that stores Redis GET command responses
    on the client side to reduce network round-trips and server load. The cache
    uses Time-To-Live (TTL) based expiration, where entries are automatically
    removed after a specified duration.

    **Important**: Glide currently supports TTL-based caching only. Invalidation-based
    client-side caching (where the server notifies clients of key changes) is not
    currently supported. This means cached values may become stale if updated on
    the server before the TTL expires.
    """

    # Class variables - shared across all instances
    _uuid_prefix: ClassVar[str] = uuid.uuid4().hex[:8]
    _counter_lock: ClassVar[threading.Lock] = threading.Lock()
    _counter: ClassVar[int] = 0

    # Instance variables - unique per instance
    cache_id: str
    max_cache_kb: int
    entry_ttl_seconds: Optional[int] = None
    eviction_policy: Optional[EvictionPolicy] = None
    enable_metrics: bool = False

    @classmethod
    def create(
        cls,
        max_cache_kb: int,
        entry_ttl_seconds: Optional[int] = None,
        eviction_policy: Optional[EvictionPolicy] = None,
        enable_metrics: bool = False,
    ) -> "ClientSideCache":
        """
        Create a new client-side cache configuration with an auto-generated unique ID.

        This class configures a local cache that stores Redis GET command responses
        on the client side to reduce network round-trips and server load. The cache
        uses Time-To-Live (TTL) based expiration, where entries are automatically
        removed after a specified duration.

        **Important**: Glide currently supports TTL-based caching only. Invalidation-based
        client-side caching (where the server notifies clients of key changes) is not
        currently supported. This means cached values may become stale if updated on
        the server before the TTL expires.

        Args:
            max_cache_kb (int): Maximum size of the cache in kilobytes (KB). This limits
                the total memory used by cached keys and values. When this limit is reached,
                entries are evicted based on the eviction policy.
            entry_ttl_seconds (Optional[int]): Time-To-Live for cached entries
                in seconds. After this duration, entries automatically expire and are removed
                from the cache. If not specified (None), a default value of 60 seconds will be used.
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
            ...     max_cache_kb=1024 * 10,  # 10 MB
            ...     entry_ttl_seconds=60,  # 1 minute TTL
            ...     eviction_policy=EvictionPolicy.LRU,
            ...     enable_metrics=True
            ... )
        """
        with cls._counter_lock:
            cache_id = f"{cls._uuid_prefix}-{cls._counter}"
            cls._counter += 1

        return cls(
            cache_id=cache_id,
            max_cache_kb=max_cache_kb,
            entry_ttl_seconds=entry_ttl_seconds,
            eviction_policy=eviction_policy,
            enable_metrics=enable_metrics,
        )
