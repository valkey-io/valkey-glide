# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import pytest
from glide_shared.config import ProtocolVersion
from tests.async_tests.conftest import create_client
from glide_shared.cache import ClientSideCache


@pytest.mark.anyio
class TestClientSideCache:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_shared_cache(self, request, protocol, cluster_mode):
        cache = ClientSideCache.create(
            max_cache_kb=1024,
            entry_ttl_seconds=60,
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

        await client.set("shared_key", "value") == "OK"
        assert await client.get("shared_key") == b"value"

        assert await client2.get("shared_key") == b"value"

        assert await client2.get_cache_hit_rate() == 1.0
        assert await client.get_cache_hit_rate() == 0.0
        await client.close()
        await client2.close()
