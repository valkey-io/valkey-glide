from typing import List

import pytest
from glide.async_commands.core import InfoSection
from glide.async_commands.search.commands import Search
from glide.async_commands.search.field import (
    Field,
    GeoField,
    GeoShapeField,
    NumericField,
    TagField,
    TextField,
    VectorField,
)
from glide.async_commands.search.index import Index, IndexType
from glide.constants import OK
from glide.exceptions import RequestError
from glide.redis_client import TRedisClient
from tests.test_async_client import (
    check_if_server_version_lt,
    get_random_string,
    parse_info_response,
)


@pytest.mark.asyncio
class TestSearchModule:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_search_module_loaded(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "search" in res["module"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_json_module_loadded(self, redis_client: TRedisClient):
        res = parse_info_response(await redis_client.info([InfoSection.MODULES]))
        assert "ReJSON" in res["module"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_hash(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(
            name=idx_name, filter="@age>15", prefix=["man:", "woman:"]
        )

        schema = [
            TextField(name="first_name"),
            TextField(name="last_name"),
            NumericField(name="age"),
            GeoField(name="location"),
        ]

        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_json(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        index_definition = Index(
            name=idx_name,
            index_type=IndexType.JSON,
            filter="@age>15",
            prefix=["man:", "woman:"],
        )

        schema = [
            TextField(name="$.first_name", alias="first_name"),
            TextField(name="$.last_name", alias="last_name"),
            NumericField(name="$.age", alias="age"),
            GeoField(name="$.location", alias="location"),
        ]

        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_existing_index(self, redis_client: TRedisClient):
        search = Search()
        index_definition = Index(name=get_random_string(5))
        schema: List[Field] = [TextField(name="field")]
        assert (
            await search.create_index(
                redis_client, index=index_definition, schema=schema
            )
            == OK
        )
        schema += [NumericField(name="numeric field")]
        with pytest.raises(RequestError) as e:
            await search.create_index(
                redis_client, index=index_definition, schema=schema
            )
        assert "index: already exists" in str(e).lower()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_create_index_hash_json(self, redis_client: TRedisClient):
        search = Search()
        idx_name = get_random_string(5)
        idx_name2 = get_random_string(5)
        index_definition = Index(
            name=idx_name,
        )

        schema = [
            TextField(name="text"),
            NumericField(name="numeric"),
            GeoField(name="geo"),
            VectorField(
                name="$.vector_field",
                algorithm="FLAT",
                attributes={"TYPE": "FLOAT32", "DIM": "8", "DISTANCE_METRIC": "COSINE"},
            ),
            TagField(name="tags"),
        ]
        if not await check_if_server_version_lt(redis_client, "7.2.0"):
            # GeoShape field is only supported on redis 7.2.0 and higher
            schema += [
                GeoShapeField(name="geoshape", coordinate_system="FLAT"),
            ]
        assert (
            await search.create_index(
                client=redis_client, index=index_definition, schema=schema
            )
            == OK
        )
        index_definition.name = idx_name2
        index_definition.index_type = IndexType.JSON
        assert (
            await search.create_index(
                client=redis_client,
                index=index_definition,
                schema=schema,
            )
            == OK
        )
