# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import threading
from dataclasses import dataclass
from enum import Enum
from typing import ClassVar, Optional, Union

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
    Configuration for client-side caching.

    This class configures a local cache that stores read command responses
    on the client side to reduce network round-trips and server load. Entries
    expire after a configurable Time-To-Live (TTL) and, with ``server_assisted=True``,
    are also invalidated as soon as the server reports that the key changed.

    Without ``server_assisted``, server-side key changes are not propagated to the
    cache, so values may become stale before TTL expires. With ``server_assisted=True`` the server sends an invalidation message
    whenever a tracked key is modified and the stale entry is dropped.
    Expiration is lazy — entries are removed when accessed after their TTL, not
    proactively in the background.
    Supported read commands: GET, MGET, HGETALL, SMEMBERS. An MGET response may
    combine locally cached values with values fetched from the server, so it
    does not provide an atomic snapshot across all keys.

    In server-assisted mode an optional ``tracking_prefix`` narrows the tracking to
    the keys starting with that prefix. GLIDE registers it with
    ``CLIENT TRACKING ON BCAST PREFIX <prefix>`` on every connection (standalone,
    each cluster node, and each reconnection), so the server only broadcasts
    invalidations for keys under that prefix and the client only caches those keys.
    Keys outside the prefix are always fetched from the server, are never cached, do
    not count as cache hits or misses and never go stale. Matching is a
    byte-wise, case-sensitive starts-with check; a key equal to the prefix is tracked.
    The prefix is fixed for the lifetime of the client and cannot be changed after
    connecting; clients sharing the same ``ClientSideCache`` instance must use the
    same prefix, and creating a client with a different prefix on a cache that is
    already in use fails.

    Attributes:
        cache_id (str): Identifier of the cache. Clients created with the same
            ``ClientSideCache`` instance share the cache.
        max_cache_kb (int): Maximum size of the cache in kilobytes (KB).
        entry_ttl_ms (int): Time-To-Live for cached entries in milliseconds. 0 disables
            TTL expiration.
        eviction_policy (Optional[EvictionPolicy]): Policy for evicting entries when
            the cache is full. Defaults to LRU when None.
        enable_metrics (bool): Whether to collect cache metrics such as hit/miss rates.
        server_assisted (bool): Whether to enable server-assisted invalidation.
        tracking_prefix (Optional[Union[str, bytes]]): Key prefix to track and cache in
            server-assisted mode. None tracks every key. See ``create`` for details.
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
    server_assisted: bool = False
    tracking_prefix: Optional[Union[str, bytes]] = None

    def __post_init__(self) -> None:
        """Validates the configuration on construction, including direct dataclass use."""
        self._validate_tracking_prefix()

    def _validate_tracking_prefix(self) -> None:
        """Validates ``tracking_prefix`` against ``server_assisted``.

        Mirrors the checks performed by the core at client creation so that an
        invalid configuration fails as early as possible, with a Python error.
        """
        if self.tracking_prefix is None:
            return
        if not isinstance(self.tracking_prefix, (str, bytes)):
            raise TypeError(
                "tracking_prefix must be a str or bytes, got "
                f"{type(self.tracking_prefix).__name__}"
            )
        if len(self.tracking_prefix) == 0:
            raise ValueError(
                "tracking_prefix must not be empty (omit it to track all keys)"
            )
        if not self.server_assisted:
            raise ValueError("tracking_prefix requires server_assisted=True")

    def _tracking_prefix_bytes(self) -> Optional[bytes]:
        """Returns ``tracking_prefix`` encoded as bytes (str is UTF-8 encoded), or None.

        Validates the prefix first: the dataclass is mutable, so the prefix may have
        changed since construction.
        """
        self._validate_tracking_prefix()
        if self.tracking_prefix is None:
            return None
        if isinstance(self.tracking_prefix, str):
            return self.tracking_prefix.encode("utf-8")
        return self.tracking_prefix

    @classmethod
    def create(
        cls,
        max_cache_kb: int,
        entry_ttl_ms: int,
        eviction_policy: Optional[EvictionPolicy] = None,
        enable_metrics: bool = False,
        server_assisted: bool = False,
        tracking_prefix: Optional[Union[str, bytes]] = None,
    ) -> "ClientSideCache":
        """
        Create a new client-side cache configuration with an auto-generated unique ID.

        In order for 2 clients to share the same cache, they must be created with the
        same ``ClientSideCache`` instance.

        - Clients with different ``ClientSideCache`` instances will have separate caches,
          even if the configurations are identical.
        - Clients using different DBs cannot share the same cache.
        - Clients using different ACL users cannot share the same cache.
        - Clients sharing the same cache must use the same ``tracking_prefix``.

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
            server_assisted (bool): If True, enables server-assisted client-side caching.
                When enabled, GLIDE sends CLIENT TRACKING ON BCAST during connection setup
                and the server sends invalidation messages when tracked keys are modified.
                Requires RESP3 protocol. The tracking state is owned by the client: do not
                issue RESET or CLIENT TRACKING manually on a client with server-assisted
                caching, because that stops the invalidations while the cache keeps
                serving its entries.
            tracking_prefix (Optional[Union[str, bytes]]): Key prefix to track in
                server-assisted mode. When set, GLIDE sends
                ``CLIENT TRACKING ON BCAST PREFIX <prefix>`` on every connection, so the
                server only broadcasts invalidations for keys starting with the prefix and
                the client only caches those keys. Without a prefix the server broadcasts
                an invalidation for every key written by any client, which does not scale
                when the cached working set is a small part of the keyspace. Keys outside
                the prefix are always read from the server and never cached, so they can
                never be stale; an ``MGET`` mixing tracked and untracked keys still returns
                the full ordered result and only the tracked keys are stored. Keys outside
                the prefix do not count as cache hits or misses. Matching is a byte-wise,
                case-sensitive starts-with check; a key equal to the prefix is tracked. A
                ``str`` is encoded as UTF-8.
                The prefix is fixed for the lifetime of the client. Requires
                ``server_assisted=True`` and must not be empty; leave it as None to track
                all keys.

        Returns:
            ClientSideCache: A new ClientSideCache instance.

        Raises:
            ValueError: If ``max_cache_kb`` is not positive, ``entry_ttl_ms`` is negative,
                ``tracking_prefix`` is empty, or ``tracking_prefix`` is set without
                ``server_assisted=True``.
            TypeError: If ``tracking_prefix`` is neither ``str`` nor ``bytes``.

        Example:
            Create a basic cache:
            >>> cache = ClientSideCache.create(
            ...     max_cache_kb=10,  # 10 KB
            ...     entry_ttl_ms=60000,  # 1 minute TTL
            ...     eviction_policy=EvictionPolicy.LRU,
            ...     enable_metrics=True
            ... )

            Create a server-assisted cache that only tracks the ``user:`` namespace:
            >>> cache = ClientSideCache.create(
            ...     max_cache_kb=1024,
            ...     entry_ttl_ms=0,
            ...     server_assisted=True,
            ...     tracking_prefix="user:",
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
            server_assisted=server_assisted,
            tracking_prefix=tracking_prefix,
        )
