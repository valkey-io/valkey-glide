# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Response models and parsing helpers for ``MEMORY STATS`` command."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Mapping, Optional, Union, cast


@dataclass
class MemoryStatsDb:
    """Database memory overhead statistics from MEMORY STATS."""

    overhead_hashtable_main: int
    overhead_hashtable_expires: int


@dataclass
class MemoryStats:
    """Represents a MEMORY STATS response."""

    db: Dict[int, MemoryStatsDb] = field(default_factory=dict)

    allocator_active: int = 0
    allocator_allocated: int = 0
    allocator_fragmentation_bytes: int = 0
    allocator_resident: int = 0
    allocator_rss_bytes: int = 0
    aof_buffer: int = 0
    clients_normal: int = 0
    clients_slaves: int = 0
    dataset_bytes: int = 0
    fragmentation_bytes: int = 0
    keys_bytes_per_key: int = 0
    keys_count: int = 0
    lua_caches: int = 0
    overhead_total: int = 0
    peak_allocated: int = 0
    replication_backlog: int = 0
    rss_overhead_bytes: int = 0
    startup_allocated: int = 0
    total_allocated: int = 0

    allocator_fragmentation_ratio: float = 0.0
    allocator_rss_ratio: float = 0.0
    dataset_percentage: float = 0.0
    fragmentation: float = 0.0
    peak_percentage: float = 0.0
    rss_overhead_ratio: float = 0.0

    # Optional Redis 7.0+ fields
    cluster_links: Optional[int] = None
    functions_caches: Optional[int] = None

    # Optional Valkey 8.0+ fields
    allocator_muzzy: Optional[int] = None
    db_dict_rehashing_count: Optional[int] = None
    overhead_db_hashtable_lut: Optional[int] = None
    overhead_db_hashtable_rehashing: Optional[int] = None


_MEMORY_STATS_DB_PREFIX = b"db."


def _parse_memory_stats_db(map: Mapping[bytes, Any]) -> MemoryStatsDb:
    """Parses map from a MEMORY STATS response into a ``MemoryStatsDb``."""
    return MemoryStatsDb(
        overhead_hashtable_main=int(map[b"overhead.hashtable.main"]),
        overhead_hashtable_expires=int(map[b"overhead.hashtable.expires"]),
    )


def _parse_memory_stats(response: Mapping[bytes, Any]) -> MemoryStats:
    """Parses a ``MEMORY STATS`` response into a ``MemoryStats``."""

    db_map: Dict[int, MemoryStatsDb] = {}
    for raw_key, value in response.items():
        if (
            raw_key.startswith(_MEMORY_STATS_DB_PREFIX)
            and raw_key != b"db.dict.rehashing.count"
        ):
            suffix = raw_key[len(_MEMORY_STATS_DB_PREFIX) :]
            db_map[int(suffix)] = _parse_memory_stats_db(value)

    return MemoryStats(
        db=db_map,
        allocator_active=int(response[b"allocator.active"]),
        allocator_allocated=int(response[b"allocator.allocated"]),
        allocator_fragmentation_bytes=int(response[b"allocator-fragmentation.bytes"]),
        allocator_resident=int(response[b"allocator.resident"]),
        allocator_rss_bytes=int(response[b"allocator-rss.bytes"]),
        aof_buffer=int(response[b"aof.buffer"]),
        clients_normal=int(response[b"clients.normal"]),
        clients_slaves=int(response[b"clients.slaves"]),
        dataset_bytes=int(response[b"dataset.bytes"]),
        fragmentation_bytes=int(response[b"fragmentation.bytes"]),
        keys_bytes_per_key=int(response[b"keys.bytes-per-key"]),
        keys_count=int(response[b"keys.count"]),
        lua_caches=int(response[b"lua.caches"]),
        overhead_total=int(response[b"overhead.total"]),
        peak_allocated=int(response[b"peak.allocated"]),
        replication_backlog=int(response[b"replication.backlog"]),
        rss_overhead_bytes=int(response[b"rss-overhead.bytes"]),
        startup_allocated=int(response[b"startup.allocated"]),
        total_allocated=int(response[b"total.allocated"]),
        allocator_fragmentation_ratio=float(response[b"allocator-fragmentation.ratio"]),
        allocator_rss_ratio=float(response[b"allocator-rss.ratio"]),
        dataset_percentage=float(response[b"dataset.percentage"]),
        fragmentation=float(response[b"fragmentation"]),
        peak_percentage=float(response[b"peak.percentage"]),
        rss_overhead_ratio=float(response[b"rss-overhead.ratio"]),
        # Optional Redis 7.0+ fields
        cluster_links=(
            int(response[b"cluster.links"]) if b"cluster.links" in response else None
        ),
        functions_caches=(
            int(response[b"functions.caches"])
            if b"functions.caches" in response
            else None
        ),
        # Optional Valkey 8.0+ fields
        allocator_muzzy=(
            int(response[b"allocator.muzzy"])
            if b"allocator.muzzy" in response
            else None
        ),
        db_dict_rehashing_count=(
            int(response[b"db.dict.rehashing.count"])
            if b"db.dict.rehashing.count" in response
            else None
        ),
        overhead_db_hashtable_lut=(
            int(response[b"overhead.db.hashtable.lut"])
            if b"overhead.db.hashtable.lut" in response
            else None
        ),
        overhead_db_hashtable_rehashing=(
            int(response[b"overhead.db.hashtable.rehashing"])
            if b"overhead.db.hashtable.rehashing" in response
            else None
        ),
    )


def _parse_memory_stats_cluster(
    response: Mapping[bytes, Any],
) -> Union[MemoryStats, Dict[bytes, MemoryStats]]:
    """Parses a cluster ``MEMORY STATS`` response."""
    if b"peak.allocated" in response:
        return _parse_memory_stats(response)
    return {
        addr: _parse_memory_stats(cast(Mapping, value))
        for addr, value in response.items()
    }
