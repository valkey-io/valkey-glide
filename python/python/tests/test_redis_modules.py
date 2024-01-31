# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide.async_commands.core import InfoSection
from glide.redis_client import TRedisClient
from tests.test_async_client import parse_info_response


@pytest.mark.asyncio
class TestRedisModules:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_search_module_loaded(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "search" in res["module"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_json_module_loadded(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "ReJSON" in res["module"]
