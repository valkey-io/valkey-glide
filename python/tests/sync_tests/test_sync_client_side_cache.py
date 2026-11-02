# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import time

import pytest
from glide_shared import ClosingError, RequestError
from glide_shared.cache import ClientSideCache, EvictionPolicy
from glide_shared.config import ProtocolVersion
from glide_shared.routes import AllNodes
from glide_sync import GlideClusterClient as SyncGlideClusterClient

from tests.sync_tests.conftest import create_sync_client
from tests.utils.utils import (
    assert_client_tracking_info,
    build_client_side_cache,
    cache_hits,
    get_random_string,
    sync_assert_cache_bypassed,
    sync_assert_cache_hit,
    sync_read_until_cached,
    sync_wait_for,
)


class TestSyncClientSideCache:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_basic_cache_hit_with_metrics(self, request, protocol, cluster_mode):
        """Test basic cache hit/miss behavior with metrics tracking."""
        cache = build_client_side_cache(max_cache_kb=1, enable_metrics=True)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set a key
        assert client.set("cache_test_key", "cache_test_value") == "OK"

        # First GET - cache miss
        assert client.get("cache_test_key") == b"cache_test_value"

        # Entry count should be 1
        assert client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        # Second GET - cache hit
        assert client.get("cache_test_key") == b"cache_test_value"

        # Third GET - cache hit
        assert client.get("cache_test_key") == b"cache_test_value"

        # Verify metrics: 1 miss + 2 hits = 3 total
        hit_rate = client.get_cache_hit_rate()
        miss_rate = client.get_cache_miss_rate()
        total_lookups = client.get_cache_total_lookups()

        assert hit_rate == pytest.approx(2.0 / 3.0), "Expected 66.67% hit rate"
        assert miss_rate == pytest.approx(1.0 / 3.0), "Expected 33.33% miss rate"
        assert abs(hit_rate + miss_rate - 1.0) < 0.0001, "Rates should sum to 1.0"
        assert total_lookups == 3, "Expected 3 total lookups (1 miss + 2 hits)"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_without_metrics(self, request, protocol, cluster_mode):
        """Test that cache works but metrics are disabled."""
        cache = build_client_side_cache(max_cache_kb=1, enable_metrics=False)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Cache should work
        assert client.set("key", "value") == "OK"
        assert client.get("key") == b"value"
        assert client.get("key") == b"value"  # Should be cached

        # metrics should fail
        with pytest.raises(Exception) as exc_info:
            client.get_cache_hit_rate()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_miss_rate()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_total_lookups()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_evictions()
        assert "metrics" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_expirations()
        assert "metrics" in str(exc_info.value).lower()

        assert client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_nil_values_not_cached(self, request, protocol, cluster_mode):
        """Test that NIL values are not cached."""
        cache = build_client_side_cache(max_cache_kb=1, enable_metrics=True)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # GET non-existent key (returns None)
        assert client.get("nonexistent_key") is None

        # Entry count should be 0
        assert client.get_cache_entry_count() == 0, "Expected 0 entries in cache"

        # GET again - should NOT be cached (NIL values not cached)
        assert client.get("nonexistent_key") is None

        # Miss rate should be 100%, total lookups = 2
        miss_rate = client.get_cache_miss_rate()
        assert miss_rate == 1.0, "Expected 100% miss rate"
        assert client.get_cache_total_lookups() == 2, "Expected 2 total lookups"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_ttl_expiration(self, request, protocol, cluster_mode):
        """Test that cache entries expire after TTL."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=500,
            enable_metrics=True,
        )

        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set and GET
        assert client.set("ttl_key", "ttl_value") == "OK"
        assert client.get("ttl_key") == b"ttl_value"

        assert client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        # Second GET - from cache
        assert client.get("ttl_key") == b"ttl_value"

        time.sleep(1)

        # GET after expiration - should fetch from server again
        assert client.get("ttl_key") == b"ttl_value"

        # Expiration count should be 1
        expirations = client.get_cache_expirations()
        assert expirations == 1, "Expected 1 expiration"

        # Miss rate should be 2 misses out of 3 total = 66.67%
        miss_rate = client.get_cache_miss_rate()
        assert miss_rate == pytest.approx(2.0 / 3.0), "Expected 66.67% miss rate"
        assert client.get_cache_total_lookups() == 3, "Expected 3 total lookups"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_multiple_keys(self, request, protocol, cluster_mode):
        """Test caching of multiple keys."""
        cache = build_client_side_cache(max_cache_kb=1, enable_metrics=True)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set 3 keys
        for i in range(1, 4):
            assert client.set(f"key{i}", f"value{i}") == "OK"

        # GET each key twice (miss + hit)
        for i in range(1, 4):
            assert client.get(f"key{i}") == f"value{i}".encode()
            assert client.get(f"key{i}") == f"value{i}".encode()

        # Entry count should be 3
        assert client.get_cache_entry_count() == 3, "Expected 3 entries in cache"

        # Verify metrics: 3 misses + 3 hits = 50% hit rate
        hit_rate = client.get_cache_hit_rate()
        assert hit_rate == 0.5, "Expected 50% hit rate"
        assert (
            client.get_cache_total_lookups() == 6
        ), "Expected 6 total lookups (3 misses + 3 hits)"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_no_cache_metrics(self, request, protocol, cluster_mode):
        """Test that without cache, all requests hit the server, and metrics are not available."""
        # No cache configured
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=None,
        )

        # Set and GET multiple times
        assert client.set("key", "value") == "OK"
        assert client.get("key") == b"value"
        assert client.get("key") == b"value"
        assert client.get("key") == b"value"

        # Metrics should error
        with pytest.raises(Exception) as exc_info:
            client.get_cache_hit_rate()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_miss_rate()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_total_lookups()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_evictions()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_expirations()
        assert "not enabled" in str(exc_info.value).lower()

        with pytest.raises(Exception) as exc_info:
            client.get_cache_entry_count()
        assert "not enabled" in str(exc_info.value).lower()

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_eviction_policy_lru(self, request, protocol, cluster_mode):
        """Test LRU eviction policy."""
        cache = build_client_side_cache(
            max_cache_kb=1,  # 1 KB to force eviction
            eviction_policy=EvictionPolicy.LRU,
            enable_metrics=True,
        )

        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Use larger values to force eviction
        value = "x" * 250  # ~250 bytes

        # Set and cache 3 keys
        for i in range(1, 4):
            assert client.set(f"lru_key{i}", value) == "OK"
            assert client.get(f"lru_key{i}") == value.encode()

        # Cache should have 3 entries now
        assert client.get_cache_entry_count() == 3, "Expected 3 entries in cache"

        # Access key1 to make it recently used
        assert client.get("lru_key1") == value.encode()

        # Add 2 more keys - should evict key2 and key3 (least recently used)
        for i in range(4, 6):
            assert client.set(f"lru_key{i}", value) == "OK"
            assert client.get(f"lru_key{i}") == value.encode()

        # Verify 2 evictions occurred
        evictions = client.get_cache_evictions()
        assert evictions == 2, "Expected 2 evictions"

        # Verify cache is working (hit rate > 0)
        hit_rate = client.get_cache_hit_rate()
        assert hit_rate > 0, "Cache should have some hits"

        # Check that key1 is still cached
        assert client.get("lru_key1") == value.encode()
        new_hit_rate = client.get_cache_hit_rate()
        assert new_hit_rate > hit_rate, "Key1 should still be in cache"

        # Check that key2 and key3 are evicted
        old_miss_rate = client.get_cache_miss_rate()
        assert client.get("lru_key2") == value.encode()
        assert client.get("lru_key3") == value.encode()
        new_miss_rate = client.get_cache_miss_rate()
        assert (
            new_miss_rate > old_miss_rate
        ), "Key2 and Key3 should be evicted from cache"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_eviction_policy_lfu(self, request, protocol, cluster_mode):
        """Test LFU (Least Frequently Used) eviction policy."""
        cache = build_client_side_cache(
            max_cache_kb=1,  # 1 KB - small cache to trigger evictions
            eviction_policy=EvictionPolicy.LFU,
            enable_metrics=True,
        )

        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        value = "x" * 250  # ~250 bytes

        # Set key1 and access it multiple times (high frequency)
        assert client.set("key1", value) == "OK"
        for _ in range(5):
            assert client.get("key1") == value.encode()
        # key1 frequency: 5

        # Set key2 and access it a few times (medium frequency)
        assert client.set("key2", value) == "OK"
        for _ in range(2):
            assert client.get("key2") == value.encode()
        # key2 frequency: 2

        # Set key3 with minimal access (low frequency)
        assert client.set("key3", value) == "OK"
        assert client.get("key3") == value.encode()
        # key3 frequency: 1

        # Verify cache is working
        hit_rate = client.get_cache_hit_rate()
        assert hit_rate > 0, "Cache should have some hits"

        # Cache should have 3 entries now
        assert client.get_cache_entry_count() == 3, "Expected 3 entries in cache"

        # Set key4 - this should trigger eviction of key3 (lowest frequency)
        assert client.set("key4", value) == "OK"
        assert client.get("key4") == value.encode()
        # key4 frequency: 1

        # Check that cache entry count is still 3
        assert client.get_cache_entry_count() == 3, "Expected 3 entries in cache"
        # Verify 1 eviction occurred
        assert client.get_cache_evictions() == 1, "Expected 1 eviction"

        # Check that key1 (highest frequency) is still cached
        old_hit_rate = client.get_cache_hit_rate()
        assert client.get("key1") == value.encode()
        new_hit_rate = client.get_cache_hit_rate()
        assert (
            new_hit_rate > old_hit_rate
        ), "key1 (highest frequency) should still be cached"

        # Check that key3 (lowest frequency) was evicted
        old_miss_rate = client.get_cache_miss_rate()
        assert client.get("key3") == value.encode()  # Should be a miss
        new_miss_rate = client.get_cache_miss_rate()
        assert (
            new_miss_rate > old_miss_rate
        ), "key3 (lowest frequency) should have been evicted"

        # key2 (medium frequency) should still be cached
        old_hit_rate = client.get_cache_hit_rate()
        assert client.get("key2") == value.encode()
        new_hit_rate = client.get_cache_hit_rate()
        assert (
            new_hit_rate > old_hit_rate
        ), "key2 (medium frequency) should still be cached"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_shared_cache(self, request, protocol, cluster_mode):
        cache = build_client_side_cache(enable_metrics=True)
        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        client2 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        assert client.set("shared_key", "value") == "OK"
        assert client.get("shared_key") == b"value"

        # Entry count should be 1
        entry_count = client2.get_cache_entry_count()
        assert entry_count == 1, "Expected 1 entry in shared cache"

        assert client2.get("shared_key") == b"value"

        assert client2.get_cache_hit_rate() == 0.5
        assert client.get_cache_hit_rate() == 0.5
        assert (
            client.get_cache_total_lookups() == 2
        ), "Expected 2 total lookups on shared cache"
        client.close()
        client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_no_ttl_expiration(self, request, protocol, cluster_mode):
        """Test that entry_ttl_ms=0 disables TTL expiration — entries persist until evicted."""
        cache = ClientSideCache.create(
            max_cache_kb=1,
            entry_ttl_ms=0,  # No TTL expiration
            enable_metrics=True,
        )

        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # Set and GET
        assert client.set("no_ttl_key", "no_ttl_value") == "OK"
        assert client.get("no_ttl_key") == b"no_ttl_value"

        # Entry should be cached
        assert client.get_cache_entry_count() == 1, "Expected 1 entry in cache"

        time.sleep(0.5)

        # GET should still be a cache hit (no expiration)
        assert client.get("no_ttl_key") == b"no_ttl_value"

        # Verify no expirations occurred
        expirations = client.get_cache_expirations()
        assert expirations == 0, "Expected 0 expirations with TTL disabled"

        # Verify metrics: 1 miss + 1 hit = 2 total
        assert client.get_cache_total_lookups() == 2, "Expected 2 total lookups"
        hit_rate = client.get_cache_hit_rate()
        assert hit_rate == 0.5, "Expected 50% hit rate"

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cache_wrong_key_type_raises_error(
        self, request, protocol, cluster_mode
    ):
        """Test that attempting to cache unsupported key types raises an error."""
        cache = build_client_side_cache(max_cache_kb=1, enable_metrics=True)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        assert client.set("string-key", "value") == "OK"
        assert client.get("string-key") == b"value"

        with pytest.raises(RequestError) as exc_info:
            client.hgetall("string-key")  # Wrong type

        assert "WRONGTYPE" in str(exc_info.value)

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cacheable_commands(self, request, protocol, cluster_mode):
        """Test that only cacheable commands are cached."""
        cache = build_client_side_cache(max_cache_kb=1, enable_metrics=True)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            cache=cache,
        )

        # SET command - not cacheable
        assert client.set("key", "value") == "OK"

        # GET command - cacheable
        assert client.get("key") == b"value"

        # check that now the cache entry count is 1
        entry_count = client.get_cache_entry_count()
        assert entry_count == 1, "Expected 1 entry in cache after GET"

        # HGETALL command - cacheable
        assert client.hset("hashkey", {"field1": "val1"}) == 1
        assert client.hgetall("hashkey") == {b"field1": b"val1"}

        entry_count = client.get_cache_entry_count()
        assert entry_count == 2, "Expected 2 entries in cache after HGETALL"

        # SMEMBERS command - cacheable
        assert client.sadd("setkey", ["member1"]) == 1
        assert client.smembers("setkey") == {b"member1"}

        entry_count = client.get_cache_entry_count()
        assert entry_count == 3, "Expected 3 entries in cache after SMEMBERS"

        client.flushall()
        client.close()

    def test_sync_cache_entry_ttl_ms_validation(self):
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

    def test_sync_server_assisted_defaults_to_false(self):
        """Test that server_assisted defaults to False."""
        cache = build_client_side_cache()
        assert cache.server_assisted is False

    def test_sync_server_assisted_can_be_set_to_true(self):
        """Test that server_assisted can be set to True."""
        cache = build_client_side_cache(server_assisted=True)
        assert cache.server_assisted is True

    def test_sync_server_assisted_can_be_explicitly_set_to_false(self):
        """Test that server_assisted can be explicitly set to False."""
        cache = build_client_side_cache(server_assisted=False)
        assert cache.server_assisted is False

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_server_assisted_invalidation(self, request, cluster_mode):
        """Test that server-assisted caching invalidates entries when modified by another client."""
        cache = build_client_side_cache(max_cache_kb=1, server_assisted=True)
        client_a = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )
        client_b = create_sync_client(
            request,
            cluster_mode=cluster_mode,
        )

        key = "test_sync_invalidation_" + get_random_string(10)

        # Client A caches the key
        assert client_a.set(key, "v1") == "OK"
        assert client_a.get(key) == b"v1"  # miss, populates cache
        assert client_a.get(key) == b"v1"  # hit

        # Client B modifies the key — triggers server invalidation
        assert client_b.set(key, "v2") == "OK"

        # Poll until invalidation is processed and Client A sees the new value
        sync_wait_for(
            lambda: client_a.get(key) == b"v2",
            "Cache was not invalidated after key was modified by another client",
        )

        client_a.close()
        client_b.close()

    # ==================== Tracking prefix ====================

    def test_sync_tracking_prefix_defaults_to_none(self):
        """Test that tracking_prefix defaults to None (all keys are tracked)."""
        cache = build_client_side_cache(server_assisted=True)
        assert cache.tracking_prefix is None
        assert cache.tracking_prefix_bytes() is None

    @pytest.mark.parametrize("prefix", ["xxx:", b"xxx:"], ids=["str", "bytes"])
    def test_sync_tracking_prefix_accepts_str_and_bytes(self, prefix):
        """Test that create() accepts a str or bytes prefix and encodes str as UTF-8."""
        cache = build_client_side_cache(server_assisted=True, tracking_prefix=prefix)
        assert cache.tracking_prefix == prefix
        assert cache.tracking_prefix_bytes() == b"xxx:"

    def test_sync_tracking_prefix_rejects_empty_prefix(self):
        """Test that an empty prefix is rejected; omit it to track all keys."""
        for empty in ("", b""):
            with pytest.raises(ValueError, match="tracking_prefix must not be empty"):
                build_client_side_cache(server_assisted=True, tracking_prefix=empty)

    def test_sync_tracking_prefix_requires_server_assisted(self):
        """Test that a prefix without server_assisted=True is rejected."""
        with pytest.raises(
            ValueError, match="tracking_prefix requires server_assisted"
        ):
            build_client_side_cache(tracking_prefix="xxx:")
        with pytest.raises(
            ValueError, match="tracking_prefix requires server_assisted"
        ):
            build_client_side_cache(server_assisted=False, tracking_prefix=b"xxx:")

    def test_sync_tracking_prefix_validated_on_direct_construction(self):
        """Test that constructing the dataclass directly is validated too."""
        with pytest.raises(
            ValueError, match="tracking_prefix requires server_assisted"
        ):
            ClientSideCache(
                cache_id="direct",
                max_cache_kb=1,
                entry_ttl_ms=0,
                tracking_prefix="xxx:",
            )
        with pytest.raises(ValueError, match="tracking_prefix must not be empty"):
            ClientSideCache(
                cache_id="direct",
                max_cache_kb=1,
                entry_ttl_ms=0,
                server_assisted=True,
                tracking_prefix="",
            )
        with pytest.raises(TypeError, match="tracking_prefix must be a str or bytes"):
            ClientSideCache(
                cache_id="direct",
                max_cache_kb=1,
                entry_ttl_ms=0,
                server_assisted=True,
                tracking_prefix=123,  # type: ignore[arg-type]
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_is_registered_with_the_server(
        self, request, cluster_mode
    ):
        """The prefix is registered with CLIENT TRACKING on every node connection."""
        cache = build_client_side_cache(server_assisted=True, tracking_prefix="xxx:")
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )

        info = client.client_tracking_info()
        assert_client_tracking_info(info, on=True, prefix="xxx:")

        if isinstance(client, SyncGlideClusterClient):
            multi_info = client.client_tracking_info(AllNodes())
            assert isinstance(multi_info, dict)
            assert len(multi_info) > 1
            for node_info in multi_info.values():
                assert_client_tracking_info(node_info, on=True, prefix="xxx:")

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_server_assisted_without_prefix_tracks_all_keys(
        self, request, cluster_mode
    ):
        """Without a prefix the server reports the empty prefix (every key is tracked)."""
        cache = build_client_side_cache(server_assisted=True)
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )

        info = client.client_tracking_info()
        assert_client_tracking_info(info, on=True, prefix="")

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_scopes_invalidations_between_clients(
        self, request, cluster_mode
    ):
        """Two clients with different prefixes: each caches and is invalidated only for
        its own namespace, and reads keys outside its prefix straight from the server.
        """
        cache_a = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix="xxx:",
        )
        cache_b = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix=b"yyy:",
        )
        client_a = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache_a,
        )
        client_b = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache_b,
        )
        writer = create_sync_client(request, cluster_mode=cluster_mode)

        suffix = get_random_string(8)
        key_a = f"xxx:{suffix}"
        key_a_stable = f"xxx:{suffix}:stable"  # never written after being cached
        key_b = f"yyy:{suffix}"
        assert writer.set(key_a, "a1") == "OK"
        assert writer.set(key_a_stable, "s1") == "OK"
        assert writer.set(key_b, "b1") == "OK"

        # Each client caches its own namespace.
        sync_read_until_cached(client_a, lambda: client_a.get(key_a), b"a1")
        sync_read_until_cached(client_a, lambda: client_a.get(key_a_stable), b"s1")
        sync_read_until_cached(client_b, lambda: client_b.get(key_b), b"b1")
        assert client_a.get_cache_entry_count() == 2
        assert client_b.get_cache_entry_count() == 1
        sync_assert_cache_hit(client_a, lambda: client_a.get(key_a), b"a1")
        sync_assert_cache_hit(client_b, lambda: client_b.get(key_b), b"b1")

        # Keys outside the prefix are served by the server, never cached, not counted.
        sync_assert_cache_bypassed(client_a, lambda: client_a.get(key_b), b"b1")
        sync_assert_cache_bypassed(client_b, lambda: client_b.get(key_a), b"a1")
        assert client_a.get_cache_entry_count() == 2, "client A must not cache yyy:*"
        assert client_b.get_cache_entry_count() == 1, "client B must not cache xxx:*"

        # A write in namespace A invalidates client A only.
        assert writer.set(key_a, "a2") == "OK"

        sync_wait_for(
            lambda: client_a.get(key_a) == b"a2",
            "client A did not observe the xxx:* invalidation",
        )
        assert client_b.get_cache_entry_count() == 1, "client B keeps its yyy:* entry"
        sync_assert_cache_hit(client_b, lambda: client_b.get(key_b), b"b1")

        # A write in namespace B invalidates client B only.
        assert writer.set(key_b, "b2") == "OK"

        sync_wait_for(
            lambda: client_b.get(key_b) == b"b2",
            "client B did not observe the yyy:* invalidation",
        )
        sync_assert_cache_hit(client_a, lambda: client_a.get(key_a_stable), b"s1")

        # Client A reads the updated yyy:* value straight from the server (never stale)
        # and still does not cache it.
        sync_assert_cache_bypassed(client_a, lambda: client_a.get(key_b), b"b2")

        client_a.close()
        client_b.close()
        writer.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_bypasses_cache_for_keys_outside_prefix(
        self, request, cluster_mode
    ):
        """GET and MGET never cache keys outside the prefix; MGET mixing tracked and
        untracked keys returns a complete, ordered result and stores only the tracked key.
        """
        cache = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix="xxx:",
        )
        cached = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )
        writer = create_sync_client(request, cluster_mode=cluster_mode)

        suffix = get_random_string(8)
        tracked = f"xxx:{suffix}"
        untracked = f"zzz:{suffix}"
        assert writer.set(tracked, "t1") == "OK"
        assert writer.set(untracked, "u1") == "OK"

        # GET on an untracked key: served by the server, not cached, not counted.
        sync_assert_cache_bypassed(cached, lambda: cached.get(untracked), b"u1")
        sync_assert_cache_bypassed(cached, lambda: cached.get(untracked), b"u1")
        assert cached.get_cache_entry_count() == 0
        assert cached.get_cache_total_lookups() == 0

        # The writer changes it and the prefixed client sees the new value immediately.
        assert writer.set(untracked, "u2") == "OK"
        sync_assert_cache_bypassed(cached, lambda: cached.get(untracked), b"u2")

        # MGET mixing tracked and untracked keys: complete ordered result, only the
        # tracked key is stored (and only the tracked key is a lookup).
        sync_read_until_cached(
            cached, lambda: cached.mget([tracked, untracked]), [b"t1", b"u2"]
        )
        assert cached.get_cache_entry_count() == 1
        assert cache_hits(cached) == 0

        # Second MGET: the tracked key hits, the untracked key reflects a fresh write.
        assert writer.set(untracked, "u3") == "OK"
        sync_assert_cache_hit(
            cached, lambda: cached.mget([untracked, tracked]), [b"u3", b"t1"]
        )
        assert cached.get_cache_entry_count() == 1

        # The tracked key is still invalidated by the server.
        assert writer.set(tracked, "t2") == "OK"

        sync_wait_for(
            lambda: cached.mget([tracked, untracked]) == [b"t2", b"u3"],
            "the tracked MGET key was not invalidated",
        )

        cached.close()
        writer.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_bypasses_cache_for_hgetall_and_smembers(
        self, request, cluster_mode
    ):
        """HGETALL and SMEMBERS on keys outside the prefix are never cached either."""
        cache = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix="xxx:",
        )
        cached = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )
        writer = create_sync_client(request, cluster_mode=cluster_mode)

        suffix = get_random_string(8)
        tracked_hash, untracked_hash = f"xxx:h:{suffix}", f"zzz:h:{suffix}"
        tracked_set, untracked_set = f"xxx:s:{suffix}", f"zzz:s:{suffix}"
        for key in (tracked_hash, untracked_hash):
            assert writer.hset(key, {"f": "v1"}) == 1
        for key in (tracked_set, untracked_set):
            assert writer.sadd(key, ["m1"]) == 1

        sync_assert_cache_bypassed(
            cached, lambda: cached.hgetall(untracked_hash), {b"f": b"v1"}
        )
        sync_assert_cache_bypassed(
            cached, lambda: cached.smembers(untracked_set), {b"m1"}
        )
        assert cached.get_cache_entry_count() == 0
        assert cached.get_cache_total_lookups() == 0

        sync_read_until_cached(
            cached, lambda: cached.hgetall(tracked_hash), {b"f": b"v1"}
        )
        sync_read_until_cached(cached, lambda: cached.smembers(tracked_set), {b"m1"})
        assert cached.get_cache_entry_count() == 2
        sync_assert_cache_hit(
            cached, lambda: cached.hgetall(tracked_hash), {b"f": b"v1"}
        )
        sync_assert_cache_hit(cached, lambda: cached.smembers(tracked_set), {b"m1"})

        # Untracked keys always reflect the latest server state.
        assert writer.hset(untracked_hash, {"f": "v2"}) == 0
        assert writer.sadd(untracked_set, ["m2"]) == 1
        sync_assert_cache_bypassed(
            cached, lambda: cached.hgetall(untracked_hash), {b"f": b"v2"}
        )
        sync_assert_cache_bypassed(
            cached, lambda: cached.smembers(untracked_set), {b"m1", b"m2"}
        )
        assert cached.get_cache_entry_count() == 2

        cached.close()
        writer.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_flush_still_clears_cache(self, request, cluster_mode):
        """FLUSHALL still clears the prefixed cache (the server sends a null invalidation)."""
        cache = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix="xxx:",
        )
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )

        key = f"xxx:{get_random_string(8)}"
        assert client.set(key, "value") == "OK"
        sync_read_until_cached(client, lambda: client.get(key), b"value")
        assert client.get_cache_entry_count() == 1

        assert client.flushall() == "OK"

        sync_wait_for(
            lambda: client.get_cache_entry_count() == 0,
            "the null invalidation did not flush the cache",
        )

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_core_configuration_errors(
        self, request, cluster_mode, monkeypatch
    ):
        """The core rejects the same invalid configurations at client creation, before
        connecting. The Python validation is bypassed here to reach the core check."""
        monkeypatch.setattr(
            ClientSideCache, "_validate_tracking_prefix", lambda self: None
        )

        cache = build_client_side_cache(server_assisted=False, tracking_prefix="xxx:")
        with pytest.raises(
            ClosingError, match="tracking_prefix requires server_assisted"
        ):
            create_sync_client(
                request,
                cluster_mode=cluster_mode,
                protocol=ProtocolVersion.RESP3,
                cache=cache,
            )

        cache = build_client_side_cache(server_assisted=True, tracking_prefix="")
        with pytest.raises(ClosingError, match="tracking_prefix must not be empty"):
            create_sync_client(
                request,
                cluster_mode=cluster_mode,
                protocol=ProtocolVersion.RESP3,
                cache=cache,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_tracking_prefix_shared_cache(self, request, cluster_mode):
        """Two clients created with the same prefixed ClientSideCache share the cache."""
        cache = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix="xxx:",
        )
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )
        client2 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )

        key = f"xxx:{get_random_string(8)}"
        assert client.set(key, "shared") == "OK"
        sync_read_until_cached(client, lambda: client.get(key), b"shared")
        assert client.get_cache_entry_count() == 1
        assert client2.get_cache_entry_count() == 1, "both clients see the same cache"
        sync_assert_cache_hit(client2, lambda: client2.get(key), b"shared")

        client.close()
        client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_shared_cache_id_requires_same_tracking_prefix(
        self, request, cluster_mode
    ):
        """Mutating the prefix on the same instance between two client creations
        (same cache_id, different prefix) makes the second creation fail."""
        cache = build_client_side_cache(
            entry_ttl_ms=0,
            enable_metrics=True,
            server_assisted=True,
            tracking_prefix="xxx:",
        )
        first = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )

        # Same id, different prefix: rejected before connecting.
        cache.tracking_prefix = "yyy:"
        with pytest.raises(ClosingError, match="different tracking prefix"):
            create_sync_client(
                request,
                cluster_mode=cluster_mode,
                protocol=ProtocolVersion.RESP3,
                cache=cache,
            )

        # Same id, no prefix at all: rejected as well.
        cache.tracking_prefix = None
        with pytest.raises(ClosingError, match="different tracking prefix"):
            create_sync_client(
                request,
                cluster_mode=cluster_mode,
                protocol=ProtocolVersion.RESP3,
                cache=cache,
            )

        # The first client is unaffected by the failed attempts.
        key = f"xxx:{get_random_string(8)}"
        assert first.set(key, "v") == "OK"
        sync_read_until_cached(first, lambda: first.get(key), b"v")
        assert first.get_cache_entry_count() == 1

        # Same id, same prefix again: shares the cache.
        cache.tracking_prefix = "xxx:"
        second = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
            cache=cache,
        )
        assert second.get_cache_entry_count() == 1, "both clients see the same cache"

        first.close()
        second.close()
