# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import asyncio

import pytest
from glide_shared import RequestError
from glide_shared.cache import ClientSideCache, EvictionPolicy
from glide_shared.config import ProtocolVersion

from tests.async_tests.conftest import create_client


@pytest.mark.anyio
class TestClientSideCache:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_basic_cache_hit_with_metrics(self, request, protocol, cluster_mode):
        """Test basic cache hit/miss behavior with metrics tracking."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=60_000,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set a key
        assert await client.set("cache_test_key", "cache_test_value") == "OK"

        # First GET - cache miss
        assert await client.get("cache_test_key") == b"cache_test_value"

        # Entry count should be 1
        assert await client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        # Second GET - cache hit
        assert await client.get("cache_test_key") == b"cache_test_value"

        # Third GET - cache hit
        assert await client.get("cache_test_key") == b"cache_test_value"

        # Verify metrics: 1 miss + 2 hits = 3 total
        hit_rate = await client.get_cache_hit_rate()
        miss_rate = await client.get_cache_miss_rate()
        total_lookups = await client.get_cache_total_lookups()

        assert hit_rate == pytest.approx(2.0 / 3.0), "Expected 66.67% hit rate"
        assert miss_rate == pytest.approx(1.0 / 3.0), "Expected 33.33% miss rate"
        assert abs(hit_rate + miss_rate - 1.0) < 0.0001, "Rates should sum to 1.0"
        assert total_lookups == 3, "Expected 3 total lookups (1 miss + 2 hits)"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_without_metrics(self, request, protocol, cluster_mode):
        """Test that cache works but metrics are disabled."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=60_000,
            enable_metrics=False,  # Disabled
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Cache should work
        assert await client.set("key", "value") == "OK"
        assert await client.get("key") == b"value"
        assert await client.get("key") == b"value"  # Should be cached

        # metrics should fail
        with pytest.raises(Exception) as exc_info:
            await client.get_cache_hit_rate()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_miss_rate()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_total_lookups()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_evictions()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_expirations()
        assert "metrics" in str(exc_info.value).lower()

        assert await client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_nil_values_not_cached(self, request, protocol, cluster_mode):
        """Test that NIL values are not cached."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=60_000,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # GET non-existent key (returns None)
        assert await client.get("nonexistent_key") is None

        # Entry count should be 0
        assert await client.get_cache_entry_count() == 0, "Expected 0 entries in cache"

        # GET again - should NOT be cached (NIL values not cached)
        assert await client.get("nonexistent_key") is None

        # Miss rate should be 100%, total lookups = 2
        miss_rate = await client.get_cache_miss_rate()
        assert miss_rate == 1.0, "Expected 100% miss rate"
        assert await client.get_cache_total_lookups() == 2, "Expected 2 total lookups"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_ttl_expiration(self, request, protocol, cluster_mode):
        """Test that cache entries expire after TTL."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=2_000,  # 2 seconds
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set and GET
        assert await client.set("ttl_key", "ttl_value") == "OK"
        assert await client.get("ttl_key") == b"ttl_value"

        assert await client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        # Second GET - from cache
        assert await client.get("ttl_key") == b"ttl_value"

        # Wait for TTL to expire
        await asyncio.sleep(3)

        # GET after expiration - should fetch from server again
        assert await client.get("ttl_key") == b"ttl_value"

        # Expiration count should be 1
        expirations = await client.get_cache_expirations()
        assert expirations == 1, "Expected 1 expiration"

        # Miss rate should be 2 misses out of 3 total = 66.67%
        miss_rate = await client.get_cache_miss_rate()
        assert miss_rate == pytest.approx(2.0 / 3.0), "Expected 66.67% miss rate"
        assert await client.get_cache_total_lookups() == 3, "Expected 3 total lookups"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_multiple_keys(self, request, protocol, cluster_mode):
        """Test caching of multiple keys."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=60_000,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set 3 keys
        for i in range(1, 4):
            assert await client.set(f"key{i}", f"value{i}") == "OK"

        # GET each key twice (miss + hit)
        for i in range(1, 4):
            assert await client.get(f"key{i}") == f"value{i}".encode()
            assert await client.get(f"key{i}") == f"value{i}".encode()

        # Entry count should be 3
        assert await client.get_cache_entry_count() == 3, "Expected 3 entries in cache"

        # Verify metrics: 3 misses + 3 hits = 50% hit rate
        hit_rate = await client.get_cache_hit_rate()
        assert hit_rate == 0.5, "Expected 50% hit rate"
        assert (
            await client.get_cache_total_lookups() == 6
        ), "Expected 6 total lookups (3 misses + 3 hits)"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_no_cache_metrics(self, request, protocol, cluster_mode):
        """Test that without cache, all requests hit the server, and metrics are not available."""
        # No cache configured
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=None,
        )

        # Set and GET multiple times
        assert await client.set("key", "value") == "OK"
        assert await client.get("key") == b"value"
        assert await client.get("key") == b"value"
        assert await client.get("key") == b"value"

        # Metrics should error
        with pytest.raises(Exception) as exc_info:
            await client.get_cache_hit_rate()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_miss_rate()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_total_lookups()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_evictions()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_expirations()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            await client.get_cache_entry_count()
        assert "not enabled" in str(exc_info.value).lower()

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_eviction_policy_lru(self, request, protocol, cluster_mode):
        """Test LRU eviction policy."""
        cache = ClientSideCache.create(
            max_cache_kb=1,  # 1 KB to force eviction
            entry_ttl_ms=600_000,
            eviction_policy=EvictionPolicy.LRU,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Use larger values to force eviction
        value = "x" * 250  # ~250 bytes

        # Set and cache 3 keys
        for i in range(1, 4):
            assert await client.set(f"lru_key{i}", value) == "OK"
            assert await client.get(f"lru_key{i}") == value.encode()

        # Cache should have 3 entries now
        assert await client.get_cache_entry_count() == 3, "Expected 3 entries in cache"

        # Access key1 to make it recently used
        assert await client.get("lru_key1") == value.encode()

        # Add 2 more keys - should evict key2 and key3 (least recently used)
        for i in range(4, 6):
            assert await client.set(f"lru_key{i}", value) == "OK"
            assert await client.get(f"lru_key{i}") == value.encode()

        # Verify 2 evictions occurred
        evictions = await client.get_cache_evictions()
        assert evictions == 2, "Expected 2 evictions"

        # Verify cache is working (hit rate > 0)
        hit_rate = await client.get_cache_hit_rate()
        assert hit_rate > 0, "Cache should have some hits"

        # Check that key1 is still cached
        assert await client.get("lru_key1") == value.encode()
        new_hit_rate = await client.get_cache_hit_rate()
        assert new_hit_rate > hit_rate, "Key1 should still be in cache"

        # Check that key2 and key3 are evicted
        old_miss_rate = await client.get_cache_miss_rate()
        assert await client.get("lru_key2") == value.encode()
        assert await client.get("lru_key3") == value.encode()
        new_miss_rate = await client.get_cache_miss_rate()
        assert (
            new_miss_rate > old_miss_rate
        ), "Key2 and Key3 should be evicted from cache"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_eviction_policy_lfu(self, request, protocol, cluster_mode):
        """Test LFU (Least Frequently Used) eviction policy."""
        cache = ClientSideCache.create(
            max_cache_kb=1,  # 1 KB - small cache to trigger evictions
            entry_ttl_ms=600_000,
            eviction_policy=EvictionPolicy.LFU,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        value = "x" * 250  # ~250 bytes

        # Set key1 and access it multiple times (high frequency)
        assert await client.set("key1", value) == "OK"
        for _ in range(5):
            assert await client.get("key1") == value.encode()
        # key1 frequency: 5

        # Set key2 and access it a few times (medium frequency)
        assert await client.set("key2", value) == "OK"
        for _ in range(2):
            assert await client.get("key2") == value.encode()
        # key2 frequency: 2

        # Set key3 with minimal access (low frequency)
        assert await client.set("key3", value) == "OK"
        assert await client.get("key3") == value.encode()
        # key3 frequency: 1

        # Verify cache is working
        hit_rate = await client.get_cache_hit_rate()
        assert hit_rate > 0, "Cache should have some hits"

        # Cache should have 3 entries now
        assert await client.get_cache_entry_count() == 3, "Expected 3 entries in cache"

        # Set key4 - this should trigger eviction of key3 (lowest frequency)
        assert await client.set("key4", value) == "OK"
        assert await client.get("key4") == value.encode()
        # key4 frequency: 1

        # Check that cache entry count is still 3
        assert await client.get_cache_entry_count() == 3, "Expected 3 entries in cache"
        # Verify 1 eviction occurred
        assert await client.get_cache_evictions() == 1, "Expected 1 eviction"

        # Check that key1 (highest frequency) is still cached
        old_hit_rate = await client.get_cache_hit_rate()
        assert await client.get("key1") == value.encode()
        new_hit_rate = await client.get_cache_hit_rate()
        assert (
            new_hit_rate > old_hit_rate
        ), "key1 (highest frequency) should still be cached"

        # Check that key3 (lowest frequency) was evicted
        old_miss_rate = await client.get_cache_miss_rate()
        assert await client.get("key3") == value.encode()  # Should be a miss
        new_miss_rate = await client.get_cache_miss_rate()
        assert (
            new_miss_rate > old_miss_rate
        ), "key3 (lowest frequency) should have been evicted"

        # key2 (medium frequency) should still be cached
        old_hit_rate = await client.get_cache_hit_rate()
        assert await client.get("key2") == value.encode()
        new_hit_rate = await client.get_cache_hit_rate()
        assert (
            new_hit_rate > old_hit_rate
        ), "key2 (medium frequency) should still be cached"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_shared_cache(self, request, protocol, cluster_mode):
        cache = ClientSideCache.create(
            max_cache_kb=1024,
            entry_ttl_ms=60_000,
            eviction_policy=None,
            enable_metrics=True,
        )
        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        client2 = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        assert await client.set("shared_key", "value") == "OK"
        assert await client.get("shared_key") == b"value"

        # Entry count should be 1
        entry_count = await client2.get_cache_entry_count()
        assert entry_count == 1, "Expected 1 entry in shared cache"

        assert await client2.get("shared_key") == b"value"

        assert await client2.get_cache_hit_rate() == 0.5
        assert await client.get_cache_hit_rate() == 0.5
        assert (
            await client.get_cache_total_lookups() == 2
        ), "Expected 2 total lookups on shared cache"
        await client.close()
        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_no_ttl_expiration(self, request, protocol, cluster_mode):
        """Test that entry_ttl_ms=0 disables TTL expiration — entries persist until evicted."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=0,  # No TTL expiration
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set and GET
        assert await client.set("no_ttl_key", "no_ttl_value") == "OK"
        assert await client.get("no_ttl_key") == b"no_ttl_value"

        # Entry should be cached
        assert await client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        # Wait a bit and verify entry is still cached (no TTL expiration)
        await asyncio.sleep(3)

        # GET should still be a cache hit (no expiration)
        assert await client.get("no_ttl_key") == b"no_ttl_value"

        # Verify no expirations occurred
        expirations = await client.get_cache_expirations()
        assert expirations == 0, "Expected 0 expirations with TTL disabled"

        # Verify metrics: 1 miss + 2 hits = 3 total
        assert await client.get_cache_total_lookups() == 2, "Expected 2 total lookups"
        hit_rate = await client.get_cache_hit_rate()
        assert hit_rate == 0.5, "Expected 66.67% hit rate"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cache_wrong_key_type_raises_error(
        self, request, protocol, cluster_mode
    ):
        """Test that attempting to cache unsupported key types raises an error."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=60_000,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        assert await client.set("string-key", "value") == "OK"
        assert await client.get("string-key") == b"value"

        with pytest.raises(RequestError) as exc_info:
            await client.hgetall("string-key")  # Wrong type

        assert "WRONGTYPE" in str(exc_info.value)

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cacheable_commands(self, request, protocol, cluster_mode):
        """Test that only cacheable commands are cached."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=60_000,
            enable_metrics=True,
        )

        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # SET command - not cacheable
        assert await client.set("key", "value") == "OK"

        # GET command - cacheable
        assert await client.get("key") == b"value"

        # check that now the cache entry count is 1
        entry_count = await client.get_cache_entry_count()
        assert entry_count == 1, "Expected 1 entry in cache after GET"

        # HGETALL command - cacheable
        assert await client.hset("hashkey", {"field1": "val1"}) == 1
        assert await client.hgetall("hashkey") == {b"field1": b"val1"}

        entry_count = await client.get_cache_entry_count()
        assert entry_count == 2, "Expected 2 entries in cache after HGETALL"

        # SMEMBERS command - cacheable
        assert await client.sadd("setkey", ["member1"]) == 1
        assert await client.smembers("setkey") == {b"member1"}

        entry_count = await client.get_cache_entry_count()
        assert entry_count == 3, "Expected 3 entries in cache after SMEMBERS"

        await client.flushall()
        await client.close()

    def test_cache_entry_ttl_ms_validation(self):
        """Test that entry_ttl_ms validation allows 0 and rejects negative values."""
        # 0 should be allowed (no expiration)
        cache = ClientSideCache.create(max_cache_kb=1, entry_ttl_ms=0)
        assert cache.entry_ttl_ms == 0

        # Positive should be allowed
        cache = ClientSideCache.create(max_cache_kb=1, entry_ttl_ms=60_000)
        assert cache.entry_ttl_ms == 60_000

        # Negative should raise
        with pytest.raises(ValueError, match="entry_ttl_ms must be non-negative"):
            ClientSideCache.create(max_cache_kb=1, entry_ttl_ms=-1)
