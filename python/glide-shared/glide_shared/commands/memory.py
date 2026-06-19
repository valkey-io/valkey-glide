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
    overhead_hashtable_slot_to_keyspace_map: Optional[int] = None  # cluster only


@dataclass
class MemoryStats:
    """Represents a MEMORY STATS response."""

    peak_allocated: int
    total_allocated: int
    startup_allocated: int
    replication_backlog: int
    clients_slaves: int
    clients_normal: int
    aof_buffer: int
    lua_caches: int
    overhead_total: int
    keys_count: int
    keys_bytes_per_key: int
    dataset_bytes: int
    dataset_percentage: float
    peak_percentage: float
    allocator_allocated: int
    allocator_active: int
    allocator_resident: int
    allocator_fragmentation_ratio: float
    allocator_fragmentation_bytes: int
    allocator_rss_ratio: float
    allocator_rss_bytes: int
    rss_overhead_ratio: float
    rss_overhead_bytes: int
    fragmentation: float
    fragmentation_bytes: int
    cluster_links: int
    functions_caches: int
    allocator_muzzy: int

    # Valkey 8.0+
    overhead_db_hashtable_lut: Optional[int] = None
    overhead_db_hashtable_rehashing: Optional[int] = None
    db_dict_rehashing_count: Optional[int] = None

    db: Dict[int, MemoryStatsDb] = field(default_factory=dict)


_MEMORY_STATS_DB_PREFIX = b"db."


def _parse_memory_stats_db(map: Mapping[bytes, Any]) -> MemoryStatsDb:
    """Parses map from a MEMORY STATS response into a ``MemoryStatsDb``."""
    return MemoryStatsDb(
        overhead_hashtable_main=int(map[b"overhead.hashtable.main"]),
        overhead_hashtable_expires=int(map[b"overhead.hashtable.expires"]),
        overhead_hashtable_slot_to_keyspace_map=(
            int(map[b"overhead.hashtable.slot-to-keyspace-map"])
            if b"overhead.hashtable.slot-to-keyspace-map" in map
            else None
        ),
    )


def _parse_memory_stats(response: Mapping[bytes, Any]) -> MemoryStats:
    """Parses a ``MEMORY STATS`` response into a ``MemoryStats``."""

    db_map: Dict[int, MemoryStatsDb] = {}
    for raw_key, value in response.items():
        if raw_key.startswith(_MEMORY_STATS_DB_PREFIX) and raw_key != b"db.dict.rehashing.count":
            suffix = raw_key[len(_MEMORY_STATS_DB_PREFIX):]
            db_map[int(suffix)] = _parse_memory_stats_db(value)

    return MemoryStats(
        peak_allocated=int(response[b"peak.allocated"]),
        total_allocated=int(response[b"total.allocated"]),
        startup_allocated=int(response[b"startup.allocated"]),
        replication_backlog=int(response[b"replication.backlog"]),
        clients_slaves=int(response[b"clients.slaves"]),
        clients_normal=int(response[b"clients.normal"]),
        aof_buffer=int(response[b"aof.buffer"]),
        lua_caches=int(response[b"lua.caches"]),
        overhead_total=int(response[b"overhead.total"]),
        keys_count=int(response[b"keys.count"]),
        keys_bytes_per_key=int(response[b"keys.bytes-per-key"]),
        dataset_bytes=int(response[b"dataset.bytes"]),
        dataset_percentage=float(response[b"dataset.percentage"]),
        peak_percentage=float(response[b"peak.percentage"]),
        allocator_allocated=int(response[b"allocator.allocated"]),
        allocator_active=int(response[b"allocator.active"]),
        allocator_resident=int(response[b"allocator.resident"]),
        allocator_fragmentation_ratio=float(response[b"allocator-fragmentation.ratio"]),
        allocator_fragmentation_bytes=int(response[b"allocator-fragmentation.bytes"]),
        allocator_rss_ratio=float(response[b"allocator-rss.ratio"]),
        allocator_rss_bytes=int(response[b"allocator-rss.bytes"]),
        rss_overhead_ratio=float(response[b"rss-overhead.ratio"]),
        rss_overhead_bytes=int(response[b"rss-overhead.bytes"]),
        fragmentation=float(response[b"fragmentation"]),
        fragmentation_bytes=int(response[b"fragmentation.bytes"]),
        cluster_links=int(response[b"cluster.links"]),
        functions_caches=int(response[b"functions.caches"]),
        allocator_muzzy=int(response[b"allocator.muzzy"]),
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
        db_dict_rehashing_count=(
            int(response[b"db.dict.rehashing.count"])
            if b"db.dict.rehashing.count" in response
            else None
        ),
        db=db_map,
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
