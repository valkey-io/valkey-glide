# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import array
import json
import struct
import time
import uuid
from typing import Dict, List, Mapping, Tuple, Union, cast

import anyio
import pytest
from glide import Batch, ClusterBatch, ft
from glide import glide_json as GlideJson
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide_shared.commands.command_args import OrderBy
from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateApply,
    FtAggregateFilter,
    FtAggregateGroupBy,
    FtAggregateLimit,
    FtAggregateOptions,
    FtAggregateReducer,
    FtAggregateSortBy,
    FtAggregateSortProperty,
    FtWithCursor,
    LoadField,
)
from glide_shared.commands.server_modules.ft_options.ft_create_options import (
    DataType,
    DistanceMetricType,
    Field,
    FtCreateOptions,
    NumericField,
    TagField,
    TextField,
    VectorAlgorithm,
    VectorField,
    VectorFieldAttributesFlat,
    VectorFieldAttributesHnsw,
    VectorType,
)
from glide_shared.commands.server_modules.ft_options.ft_hybrid_options import (
    FtHybridCombine,
    FtHybridOptions,
    FtHybridSearchArm,
    FtHybridVsimArm,
    FtHybridVsimFilter,
    HybridFilterPolicy,
)
from glide_shared.commands.server_modules.ft_options.ft_profile_options import (
    FtProfileOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    ConsistencyMode,
    FtSearchLimit,
    FtSearchOptions,
    InfoScope,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    OrderBy as FtSearchOrderBy,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    ReturnField,
    ShardScope,
)
from glide_shared.config import ProtocolVersion, ReadFrom
from glide_shared.constants import OK, FtSearchResponse, TEncodable
from glide_shared.exceptions import RequestError
from glide_shared.routes import ByAddressRoute

from tests.async_tests.conftest import create_client
from tests.conftest import _parse_emulate_release_level
from tests.utils.ft_test_helpers import (
    HYBRID_DOC_TEXT_WINNER,
    HYBRID_DOC_VECTOR_WINNER,
    HYBRID_DOCS,
    HYBRID_QUERY_VECTOR,
    HYBRID_SEARCH_ALIAS,
    HYBRID_SEARCH_QUERY,
    HYBRID_VSIM_ALIAS,
    INKEYS_DOCS,
    MULTI_SHARD_DOC_COUNT,
    REDUCERS_DOCS,
    REDUCERS_EXPECTED_FIRST_VALUE_TITLE,
    REDUCERS_EXPECTED_TOLIST_PRICES,
    REDUCERS_RANDOM_SAMPLE_POSSIBLE_PRICES,
    VECTOR_RANGE_AGGREGATE_EXPECTED_DISTANCES,
    VECTOR_RANGE_AGGREGATE_POINTS,
    VECTOR_RANGE_AGGREGATE_RADIUS,
    VECTOR_RANGE_SEARCH_EXPECTED_KEYS,
    VECTOR_RANGE_SEARCH_POINTS,
    VECTOR_RANGE_SEARCH_RADIUS,
    WILDCARD_DOCS,
    json_groupby_column,
    normalize_cluster_shards_entry,
    normalize_cluster_shards_node,
    pair_flat_list,
)
from tests.utils.utils import check_version_lt


@pytest.mark.anyio
class TestFt:
    SearchResultField = Mapping[
        TEncodable, Union[TEncodable, Mapping[TEncodable, Union[TEncodable, int]]]
    ]

    SerchResultFieldsList = List[
        Mapping[
            TEncodable,
            Union[TEncodable, Mapping[TEncodable, Union[TEncodable, int]]],
        ]
    ]

    sleep_wait_time = 1  # This value is in seconds

    async def _poll_until_indexed(
        self,
        glide_client: TGlideClient,
        index: TEncodable,
        expected_count: int,
        timeout_secs: float = 10,
    ) -> None:
        """Poll ``FT.INFO``'s ``num_docs`` until it reaches
        `expected_count`, instead of a fixed sleep. Indexing is async in
        valkey-search, so a fixed sleep is a race whose window just happens
        to be wide enough on this box today. Raises AssertionError with the
        last-seen count on timeout, not a bare index-mismatch failure further
        down the test, so a flake here is attributable to indexing lag rather
        than a real behavioural regression.

        Deliberately uses FT.INFO rather than ``FT.SEARCH <index> *``: a
        bare ``*`` match-all query is itself a 1.3.0-rc1 fix (PR #960) and
        is invalid query syntax on pre-1.3 engines ("Invalid query string
        syntax", confirmed against a built 1.2.1 module). A poll helper used
        by tests that are NOT 1.3-only (e.g. the FT.INFO PRIMARY/CLUSTER
        tests) must not itself introduce a hidden 1.3 dependency on an
        unrelated test -- found and fixed during Group 6 evidence
        collection: test_ft_info_primary_cluster_scope_without_coordinator
        failed on a 1.2.1 cluster with exactly that error until this
        helper stopped using ``*``. FT.INFO's ``num_docs`` field is
        unchanged between 1.2 and 1.3 (confirmed via byte-identical source,
        src/commands/ft_info_parser.cc, during the Round-1 FT.INFO
        investigation).

        Raises immediately (not after the full timeout) if
        ``num_docs`` is absent from the reply -- a reply-shape regression
        there should surface as its own clear failure, not get folded into
        "did not reach N documents" after a 10s wait that was never going
        to succeed.
        """
        deadline = anyio.current_time() + timeout_secs
        last_count = -1
        while anyio.current_time() < deadline:
            info = await ft.info(glide_client, index)
            if b"num_docs" not in info:
                raise AssertionError(
                    f"_poll_until_indexed: FT.INFO reply for index {index!r} "
                    f"has no 'num_docs' field (keys present: "
                    f"{list(info.keys())}) -- this is a reply-shape "
                    "regression, not an indexing-lag timeout"
                )
            last_count = cast(int, info[b"num_docs"])
            if last_count >= expected_count:
                return
            await anyio.sleep(0.1)
        raise AssertionError(
            f"_poll_until_indexed: index {index!r} did not reach "
            f"{expected_count} documents within {timeout_secs}s "
            f"(last seen: {last_count})"
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create(self, glide_client: GlideClusterClient):
        fields: List[Field] = [
            TextField("$title", "title"),
            NumericField("$published_at", "published_at"),
            TextField("$category", "category"),
        ]
        prefixes: List[TEncodable] = ["blog:post:"]

        # Create an index with multiple fields with Hash data type.
        index = str(uuid.uuid4())
        assert (
            await ft.create(
                glide_client, index, fields, FtCreateOptions(DataType.HASH, prefixes)
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index) == OK

        # Create an index with multiple fields with JSON data type.
        index2 = str(uuid.uuid4())
        assert (
            await ft.create(
                glide_client, index2, fields, FtCreateOptions(DataType.JSON, prefixes)
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index2) == OK

        # Create an index for vectors of size 2
        # FT.CREATE hash_idx1 ON HASH PREFIX 1 hash: SCHEMA vec AS VEC VECTOR HNSW 6 DIM 2 TYPE FLOAT32 DISTANCE_METRIC L2
        index3 = str(uuid.uuid4())
        prefixes = ["hash:"]
        fields = [
            VectorField(
                name="vec",
                algorithm=VectorAlgorithm.HNSW,
                attributes=VectorFieldAttributesHnsw(
                    dimensions=2,
                    distance_metric=DistanceMetricType.L2,
                    type=VectorType.FLOAT32,
                ),
                alias="VEC",
            )
        ]

        assert (
            await ft.create(
                glide_client, index3, fields, FtCreateOptions(DataType.HASH, prefixes)
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index3) == OK

        # Create a 6-dimensional JSON index using the HNSW algorithm
        # FT.CREATE json_idx1 ON JSON PREFIX 1 json: SCHEMA $.vec AS VEC VECTOR HNSW 6 DIM 6 TYPE FLOAT32 DISTANCE_METRIC L2
        index4 = str(uuid.uuid4())
        prefixes = ["json:"]
        fields = [
            VectorField(
                name="$.vec",
                algorithm=VectorAlgorithm.HNSW,
                attributes=VectorFieldAttributesHnsw(
                    dimensions=6,
                    distance_metric=DistanceMetricType.L2,
                    type=VectorType.FLOAT32,
                ),
                alias="VEC",
            )
        ]

        assert (
            await ft.create(
                glide_client, index4, fields, FtCreateOptions(DataType.JSON, prefixes)
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index4) == OK

        # Create an index without FtCreateOptions

        index5 = str(uuid.uuid4())
        assert await ft.create(glide_client, index5, fields, FtCreateOptions()) == OK
        assert await ft.dropindex(glide_client, index5) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_byte_type_input(self, glide_client: GlideClusterClient):
        fields: List[Field] = [
            TextField(b"$title", b"title"),
            NumericField(b"$published_at", b"published_at"),
            TextField(b"$category", b"category"),
        ]
        prefixes: List[TEncodable] = [b"blog:post:"]

        # Create an index with multiple fields with Hash data type with byte type input.
        index = str(uuid.uuid4())
        assert (
            await ft.create(
                glide_client,
                index.encode("utf-8"),
                fields,
                FtCreateOptions(DataType.HASH, prefixes),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_dropindex(self, glide_client: GlideClusterClient):
        # Index name for the index to be dropped.
        index_name = str(uuid.uuid4())
        fields: List[Field] = [TextField("$title", "title")]
        prefixes: List[TEncodable] = ["blog:post:"]

        # Create an index with multiple fields with Hash data type.
        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.HASH, prefixes),
            )
            == OK
        )

        # Drop the index. Expects "OK" as a response.
        assert await ft.dropindex(glide_client, index_name) == OK

        # Create an index with multiple fields with Hash data type for byte type testing
        index_name_for_bytes_type_input = str(uuid.uuid4())
        assert (
            await ft.create(
                glide_client,
                index_name_for_bytes_type_input,
                fields,
                FtCreateOptions(DataType.HASH, prefixes),
            )
            == OK
        )

        # Drop the index. Expects "OK" as a response.
        assert (
            await ft.dropindex(
                glide_client, index_name_for_bytes_type_input.encode("utf-8")
            )
            == OK
        )

        # Drop a non existent index. Expects a RequestError.
        with pytest.raises(RequestError):
            await ft.dropindex(glide_client, index_name)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search(self, glide_client: GlideClusterClient):
        json_prefix = "{json-search-" + str(uuid.uuid4()) + "}:"
        json_key1 = json_prefix + str(uuid.uuid4())
        json_key2 = json_prefix + str(uuid.uuid4())
        json_value1 = {"a": 11111, "b": 2, "c": 3}
        json_value2 = {"a": 22222, "b": 2, "c": 3}
        json_index = json_prefix + str(uuid.uuid4())

        # Create an index.
        assert (
            await ft.create(
                glide_client,
                json_index,
                schema=[
                    NumericField("$.a", "a"),
                    NumericField("$.b", "b"),
                ],
                options=FtCreateOptions(DataType.JSON, prefixes=[json_prefix]),
            )
            == OK
        )

        # Create a json key.
        assert (
            await GlideJson.set(glide_client, json_key1, "$", json.dumps(json_value1))
            == OK
        )
        assert (
            await GlideJson.set(glide_client, json_key2, "$", json.dumps(json_value2))
            == OK
        )

        # Wait for index to be updated to avoid this error - ResponseError: The index is under construction.
        time.sleep(self.sleep_wait_time)

        ft_search_options = FtSearchOptions(
            return_fields=[
                ReturnField(field_identifier="a"),
                ReturnField(field_identifier="b"),
            ]
        )

        # Search the index for string inputs.
        result1 = await ft.search(
            client=glide_client,
            index_name=json_index,
            query="@a:[-inf +inf]",
            options=ft_search_options,
        )
        # Check if we get the expected result from ft.search for string inputs.
        TestFt._ft_search_deep_compare_json_result(
            self,
            result=result1,
            json_key1=json_key1,
            json_key2=json_key2,
            json_value1=json_value1,
            json_value2=json_value2,
            fieldName1="a",
            fieldName2="b",
        )

        # FT.PROFILE may not be available on all server versions (e.g. valkey-search).
        try:
            ft_profile_result = await ft.profile(
                glide_client,
                json_index,
                FtProfileOptions.from_query_options(
                    query="@a:[-inf +inf]", query_options=ft_search_options
                ),
            )
            assert len(ft_profile_result) > 0

            # Check if we get the expected result from FT.PROFILE for string inputs.
            TestFt._ft_search_deep_compare_json_result(
                self,
                result=cast(FtSearchResponse, ft_profile_result[0]),
                json_key1=json_key1,
                json_key2=json_key2,
                json_value1=json_value1,
                json_value2=json_value2,
                fieldName1="a",
                fieldName2="b",
            )
        except RequestError:
            pass
        ft_search_options_bytes_input = FtSearchOptions(
            return_fields=[
                ReturnField(field_identifier=b"a"),
                ReturnField(field_identifier=b"b"),
            ]
        )

        # Search the index for byte type inputs.
        result2 = await ft.search(
            glide_client,
            json_index.encode("utf-8"),
            b"@a:[-inf +inf]",
            options=ft_search_options_bytes_input,
        )

        # Check if we get the expected result from ft.search for byte type inputs.
        TestFt._ft_search_deep_compare_json_result(
            self,
            result=result2,
            json_key1=json_key1,
            json_key2=json_key2,
            json_value1=json_value1,
            json_value2=json_value2,
            fieldName1="a",
            fieldName2="b",
        )

        # FT.PROFILE may not be available on all server versions (e.g. valkey-search).
        try:
            ft_profile_result = await ft.profile(
                glide_client,
                json_index.encode("utf-8"),
                FtProfileOptions.from_query_options(
                    query=b"@a:[-inf +inf]", query_options=ft_search_options_bytes_input
                ),
            )
            assert len(ft_profile_result) > 0

            # Check if we get the expected result from FT.PROFILE for byte type inputs.
            TestFt._ft_search_deep_compare_json_result(
                self,
                result=cast(FtSearchResponse, ft_profile_result[0]),
                json_key1=json_key1,
                json_key2=json_key2,
                json_value1=json_value1,
                json_value2=json_value2,
                fieldName1="a",
                fieldName2="b",
            )
        except RequestError:
            pass

        # Create an index for knn vector search.

        vector_prefix = "{vector-search-" + str(uuid.uuid4()) + "}:"
        vector_key1 = vector_prefix + str(uuid.uuid4())
        vector_key2 = vector_prefix + str(uuid.uuid4())
        vector1 = array.array("f", [1.0, 0.0])
        vector2 = array.array("f", [0.0, 1.0])
        vector_value1 = vector1.tobytes()
        vector_value2 = vector2.tobytes()
        vector_index = vector_prefix + str(uuid.uuid4())
        vector_field_name = "vector"

        assert (
            await ft.create(
                glide_client,
                vector_index,
                schema=[
                    VectorField(
                        name=vector_field_name,
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=len(vector1),  # each float32 is 4 bytes
                            distance_metric=DistanceMetricType.COSINE,
                            type=VectorType.FLOAT32,
                        ),
                    ),
                ],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[vector_prefix],
                ),
            )
            == OK
        )

        # Create vector keys.
        assert (
            await glide_client.hset(vector_key1, {vector_field_name: vector_value1})
            == 1
        )
        assert (
            await glide_client.hset(vector_key2, {vector_field_name: vector_value2})
            == 1
        )

        time.sleep(self.sleep_wait_time)

        vector_param_name = "query_vector"
        knn_query = f"*=>[KNN 1 @{vector_field_name} ${vector_param_name}]"
        knn_query_options = FtSearchOptions(
            params={vector_param_name: vector_value1},  # searching for vector1
            return_fields=[
                ReturnField(vector_field_name),
                ReturnField(f"__{vector_field_name}_score"),
            ],
        )

        max_retries = 3
        last_exception = None

        for attempt in range(max_retries):
            try:
                knn_result = await ft.search(
                    client=glide_client,
                    index_name=vector_index,
                    query=knn_query,
                    options=knn_query_options,
                )

                assert len(knn_result) == 2
                assert knn_result[0] == 1  # first index is number of results

                expected_result = {
                    vector_key1.encode(): {
                        vector_field_name.encode(): vector_value1,
                        f"__{vector_field_name}_score".encode(): str(
                            0  # cosine distance of 0 means identical vectors
                        ).encode(),
                    }
                }
                assert knn_result[1] == expected_result
                break

            except AssertionError as e:
                last_exception = e
                if attempt < max_retries - 1:
                    time.sleep(self.sleep_wait_time)
                    continue
                else:
                    raise last_exception

        assert await ft.dropindex(glide_client, json_index) == OK
        assert await ft.dropindex(glide_client, vector_index) == OK

    def _ft_search_deep_compare_json_result(
        self,
        result: List[Union[int, Mapping[TEncodable, Mapping[TEncodable, TEncodable]]]],
        json_key1: str,
        json_key2: str,
        json_value1: dict,
        json_value2: dict,
        fieldName1: str,
        fieldName2: str,
    ):
        """
        Deep compare the keys and values in FT.SEARCH result array.

        Args:
            result (List[Union[int, Mapping[TEncodable, Mapping[TEncodable, TEncodable]]]]):
            json_key1 (str): The first key in search result.
            json_key2 (str): The second key in the search result.
            json_value1 (dict): The fields map for first key in the search result.
            json_value2 (dict): The fields map for second key in the search result.
        """
        assert len(result) == 2
        assert result[0] == 2
        search_result_map: Mapping[TEncodable, Mapping[TEncodable, TEncodable]] = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result[1]
        )
        expected_result_map: Mapping[TEncodable, Mapping[TEncodable, TEncodable]] = {
            json_key1.encode(): {
                fieldName1.encode(): str(json_value1.get(fieldName1)).encode(),
                fieldName2.encode(): str(json_value1.get(fieldName2)).encode(),
            },
            json_key2.encode(): {
                fieldName1.encode(): str(json_value2.get(fieldName1)).encode(),
                fieldName2.encode(): str(json_value2.get(fieldName2)).encode(),
            },
        }
        assert search_result_map == expected_result_map

    @pytest.mark.skip(
        reason="Index aliasing (FT.ALIASADD) did not make the valkey-search 1.3"
        " cut; still open as valkey-io/valkey-search#1184. Confirmed absent in"
        " 1.3.0-rc1 via COMMAND INFO. Fast-follow once #1184 merges."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasadd(self, glide_client: GlideClusterClient):
        index_name: str = str(uuid.uuid4())
        alias: str = "alias"
        # Test ft.aliasadd throws an error if index does not exist.
        with pytest.raises(RequestError):
            await ft.aliasadd(glide_client, alias, index_name)

        # Test ft.aliasadd successfully adds an alias to an existing index.
        await TestFt._create_test_index_hash_type(self, glide_client, index_name)
        assert await ft.aliasadd(glide_client, alias, index_name) == OK
        assert await ft.dropindex(glide_client, index_name) == OK

        # Test ft.aliasadd for input of bytes type.
        index_name_string = str(uuid.uuid4())
        index_names_bytes = index_name_string.encode("utf-8")
        alias_name_bytes = b"alias-bytes"
        await TestFt._create_test_index_hash_type(self, glide_client, index_name_string)
        assert (
            await ft.aliasadd(glide_client, alias_name_bytes, index_names_bytes) == OK
        )
        assert await ft.dropindex(glide_client, index_name_string) == OK

    @pytest.mark.skip(
        reason="Index aliasing (FT.ALIASDEL) did not make the valkey-search 1.3"
        " cut; still open as valkey-io/valkey-search#1184. Confirmed absent in"
        " 1.3.0-rc1 via COMMAND INFO. Fast-follow once #1184 merges."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasdel(self, glide_client: GlideClusterClient):
        index_name: TEncodable = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt._create_test_index_hash_type(self, glide_client, index_name)

        # Test if deleting a non existent alias throws an error.
        with pytest.raises(RequestError):
            await ft.aliasdel(glide_client, alias)

        # Test if an existing alias is deleted successfully.
        assert await ft.aliasadd(glide_client, alias, index_name) == OK
        assert await ft.aliasdel(glide_client, alias) == OK

        # Test if an existing alias is deleted successfully for bytes type input.
        assert await ft.aliasadd(glide_client, alias, index_name) == OK
        assert await ft.aliasdel(glide_client, alias.encode("utf-8")) == OK

        assert await ft.dropindex(glide_client, index_name) == OK

    @pytest.mark.skip(
        reason="Index aliasing (FT.ALIASUPDATE) did not make the valkey-search 1.3"
        " cut; still open as valkey-io/valkey-search#1184. Confirmed absent in"
        " 1.3.0-rc1 via COMMAND INFO. Fast-follow once #1184 merges."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasupdate(self, glide_client: GlideClusterClient):
        index_name: str = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt._create_test_index_hash_type(self, glide_client, index_name)
        assert await ft.aliasadd(glide_client, alias, index_name) == OK
        new_alias_name: str = "newAlias"
        new_index_name: str = str(uuid.uuid4())

        await TestFt._create_test_index_hash_type(self, glide_client, new_index_name)
        assert await ft.aliasadd(glide_client, new_alias_name, new_index_name) == OK

        # Test if updating an already existing alias to point to an existing index returns "OK".
        assert await ft.aliasupdate(glide_client, new_alias_name, index_name) == OK
        assert (
            await ft.aliasupdate(
                glide_client, alias.encode("utf-8"), new_index_name.encode("utf-8")
            )
            == OK
        )

        assert await ft.dropindex(glide_client, index_name) == OK
        assert await ft.dropindex(glide_client, new_index_name) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_dropindex_ft_list(self, glide_client: GlideClusterClient):
        indexName = str(uuid.uuid4()).encode()
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)

        before = await ft.list(glide_client)
        assert indexName in before

        assert await ft.dropindex(glide_client, indexName) == OK
        after = await ft.list(glide_client)
        assert indexName not in after

        assert {_ for _ in after + [indexName]} == {_ for _ in before}

        # Drop a non existent index. Expects a RequestError.
        with pytest.raises(RequestError):
            await ft.dropindex(glide_client, indexName)

    async def _create_test_index_hash_type(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating a basic index with hash data type with one text field.
        fields: List[Field] = [TextField("title")]
        prefix = "hash-search-" + str(uuid.uuid4()) + ":"
        prefixes: List[TEncodable] = [prefix]
        result = await ft.create(
            glide_client, index_name, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_info(self, glide_client: GlideClusterClient):
        index_name = str(uuid.uuid4())
        await TestFt._create_test_index_with_vector_field(
            self, glide_client, index_name
        )
        result = await ft.info(glide_client, index_name)
        assert await ft.dropindex(glide_client, index_name) == OK
        TestFt._ft_info_deep_compare_result(self, index_name, result)

        # Test for bytes type input.
        index_name_for_bytes_input = str(uuid.uuid4())
        await TestFt._create_test_index_with_vector_field(
            self, glide_client, index_name_for_bytes_input
        )
        result = await ft.info(glide_client, index_name_for_bytes_input.encode("utf-8"))
        assert await ft.dropindex(glide_client, index_name_for_bytes_input) == OK
        TestFt._ft_info_deep_compare_result(self, index_name_for_bytes_input, result)

        # Querying a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.info(glide_client, str(uuid.uuid4()))

    def _ft_info_deep_compare_result(self, index_name: str, result):
        # The server returns FT.INFO as a flat list of key-value pairs.
        if isinstance(result, list):
            info = {}
            for i in range(0, len(result) - 1, 2):
                info[result[i]] = result[i + 1]
            result = info

        assert index_name.encode() == result.get(b"index_name")

        # index_definition is a flat list containing key_type, prefixes, etc.
        index_def = result.get(b"index_definition", [])
        if isinstance(index_def, list):
            idx_def_dict = {}
            for i in range(0, len(index_def) - 1, 2):
                idx_def_dict[index_def[i]] = index_def[i + 1]
            assert b"JSON" == idx_def_dict.get(b"key_type")

        # Fields are under "attributes" as an array of flat lists.
        fields_raw = result.get(b"attributes") or result.get(b"fields")
        assert fields_raw is not None

        # Convert each field from flat list to dict.
        fields = []
        for f in fields_raw:
            if isinstance(f, list):
                fd = {}
                for i in range(0, len(f) - 1, 2):
                    fd[f[i]] = f[i + 1]
                fields.append(fd)
            else:
                fields.append(f)

        assert len(fields) == 2
        text_field = {}
        vector_field = {}
        if fields[0].get(b"type") == b"VECTOR":
            vector_field = fields[0]
            text_field = fields[1]
        else:
            vector_field = fields[1]
            text_field = fields[0]

        # Compare vector field
        assert b"$.vec" == vector_field.get(b"identifier")
        assert b"VECTOR" == vector_field.get(b"type")
        # "attribute" in new server, "field_name" in old
        vec_alias = vector_field.get(b"attribute") or vector_field.get(b"field_name")
        assert b"VEC" == vec_alias

        # Compare text field
        assert b"$.text-field" == text_field.get(b"identifier")
        assert b"TEXT" == text_field.get(b"type")

    async def _create_test_index_with_vector_field(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating an index with JSON data type with a text and vector field.
        fields: List[Field] = [
            VectorField(
                name="$.vec",
                algorithm=VectorAlgorithm.HNSW,
                attributes=VectorFieldAttributesHnsw(
                    dimensions=2,
                    distance_metric=DistanceMetricType.L2,
                    type=VectorType.FLOAT32,
                ),
                alias="VEC",
            ),
            TextField("$.text-field", "text_field"),
        ]

        prefixes: List[TEncodable] = ["key-prefix"]

        await ft.create(
            glide_client,
            index_name,
            schema=fields,
            options=FtCreateOptions(DataType.JSON, prefixes=prefixes),
        )

    @pytest.mark.skip(
        reason="FT.EXPLAIN is not implemented by valkey-search 1.3.0-rc1."
        " Confirmed absent via COMMAND INFO (empty row). No tracking issue"
        " was found in valkey-io/valkey-search as of this round (unlike"
        " aliases, which has #1184); re-check COMMAND INFO before un-skipping."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_explain(self, glide_client: GlideClusterClient):
        index_name = str(uuid.uuid4())
        await TestFt._create_test_index_for_ft_explain_commands(
            self, glide_client, index_name
        )

        # FT.EXPLAIN on a search query containing numeric field.
        query = "@price:[0 10]"
        result = await ft.explain(glide_client, index_name, query)
        result_string = cast(bytes, result).decode(encoding="utf-8")
        assert (
            "price" in result_string and "0" in result_string and "10" in result_string
        )

        # FT.EXPLAIN on a search query containing numeric field and having bytes type input to the command.
        result = await ft.explain(glide_client, index_name.encode(), query.encode())
        result_string = cast(bytes, result).decode(encoding="utf-8")
        assert (
            "price" in result_string and "0" in result_string and "10" in result_string
        )

        # FT.EXPLAIN on a search query that returns all data.
        result = await ft.explain(glide_client, index_name, query="*")
        result_string = cast(bytes, result).decode(encoding="utf-8")
        assert "*" in result_string

        assert await ft.dropindex(glide_client, index_name)

        # FT.EXPLAIN on a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.explain(glide_client, str(uuid.uuid4()), query="*")

    @pytest.mark.skip(
        reason="FT.EXPLAINCLI is not implemented by valkey-search 1.3.0-rc1."
        " Confirmed absent via COMMAND INFO (empty row). No tracking issue"
        " was found in valkey-io/valkey-search as of this round (unlike"
        " aliases, which has #1184); re-check COMMAND INFO before un-skipping."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_explaincli(self, glide_client: GlideClusterClient):
        index_name = str(uuid.uuid4())
        await TestFt._create_test_index_for_ft_explain_commands(
            self, glide_client, index_name
        )

        # FT.EXPLAINCLI on a search query containing numeric field.
        query = "@price:[0 10]"
        result = await ft.explaincli(glide_client, index_name, query)
        result_string_arr = []
        for i in result:
            result_string_arr.append(cast(bytes, i).decode(encoding="utf-8").strip())
        assert (
            "price" in result_string_arr
            and "0" in result_string_arr
            and "10" in result_string_arr
        )

        # FT.EXPLAINCLI on a search query containing numeric field and having bytes type input to the command.
        result = await ft.explaincli(glide_client, index_name.encode(), query.encode())
        result_string_arr = []
        for i in result:
            result_string_arr.append(cast(bytes, i).decode(encoding="utf-8").strip())
        assert (
            "price" in result_string_arr
            and "0" in result_string_arr
            and "10" in result_string_arr
        )

        # FT.EXPLAINCLI on a search query that returns all data.
        result = await ft.explaincli(glide_client, index_name, query="*")
        result_string_arr = []
        for i in result:
            result_string_arr.append(cast(bytes, i).decode(encoding="utf-8").strip())
        assert "*" in result_string_arr

        assert await ft.dropindex(glide_client, index_name)

        # FT.EXPLAINCLI on a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.explaincli(glide_client, str(uuid.uuid4()), "*")

    async def _create_test_index_for_ft_explain_commands(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating an index having hash data type, one text field and one numeric field.
        fields: List[Field] = [TextField("title"), NumericField("price")]
        prefix = "hash-search-" + str(uuid.uuid4()) + ":"
        prefixes: List[TEncodable] = [prefix]

        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.HASH, prefixes),
            )
            == OK
        )

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, protocol
    ):
        prefix_bicycles = "{bicycles-" + str(uuid.uuid4()) + "}:"
        index_bicycles = prefix_bicycles + str(uuid.uuid4())
        await TestFt._create_index_for_ft_aggregate_with_bicycles_data(
            self,
            glide_client,
            index_bicycles,
            prefix_bicycles,
        )
        await TestFt._create_json_keys_for_ft_aggregate_with_bicycles_data(
            self, glide_client, prefix_bicycles
        )
        time.sleep(self.sleep_wait_time)

        ft_aggregate_options: FtAggregateOptions = FtAggregateOptions(
            loadFields=["__key"],
            clauses=[
                FtAggregateGroupBy(
                    ["@condition"], [FtAggregateReducer("COUNT", [], "bicycles")]
                )
            ],
        )

        # The GROUPBY output column name for a JSON-indexed field is gated by
        # search.emulate-release (baseline 1.3.0) -- see
        # json_groupby_column's docstring for the exact source citation.
        # Pre-1.3 modules (e.g. 1.2.1, confirmed via live CONFIG GET during
        # Group 6 evidence collection) don't define this config at all --
        # CONFIG GET returns an empty reply, not the key with an empty/
        # default value. _parse_emulate_release_level returns None for that
        # (and for any other shape it can't decode), so default to "1.0.0"
        # (the module's own documented default) in that case -- this test
        # isn't gated by search_1_3_capability, so it must behave correctly
        # on a pre-1.3 module too, unlike emulate_release_level (both use
        # one decoder shared with conftest.py's fixture rather than a
        # second inline reader with its own rule).
        emulate_release_raw = await glide_client.custom_command(
            ["CONFIG", "GET", "search.emulate-release"]
        )
        emulate_release = _parse_emulate_release_level(emulate_release_raw) or "1.0.0"
        condition_column = json_groupby_column(
            b"$.condition", b"condition", emulate_release
        )

        # Run FT.AGGREGATE command with the following arguments:
        # ['FT.AGGREGATE', '{bicycles}:1e15faab-a870-488e-b6cd-f2b76c6916a3',
        # '*', 'LOAD', '1', '__key', 'GROUPBY', '1', '@condition', 'REDUCE', 'COUNT',
        # '0', 'AS', 'bicycles']
        result = await ft.aggregate(
            glide_client,
            index_bicycles,
            query="*",
            options=ft_aggregate_options,
        )
        sorted_result = sorted(
            result, key=lambda x: (x[condition_column], x[b"bicycles"])
        )
        # valkey-search 1.3.0-rc1's FT.AGGREGATE reducer output (COUNT here) is
        # a bulk string under BOTH RESP2 and RESP3 -- it does not promote to a
        # RESP3 double the way RediSearch does. Confirmed via a raw redis-py
        # client bypassing GLIDE with protocol=2 vs protocol=3. Matches the
        # same finding already applied to test_ft_aggregate_with_movies_data.
        expected_result = sorted(
            [
                {condition_column: b"refurbished", b"bicycles": b"1"},
                {condition_column: b"new", b"bicycles": b"5"},
                {condition_column: b"used", b"bicycles": b"4"},
            ],
            key=lambda x: (x[condition_column], x[b"bicycles"]),
        )
        assert sorted_result == expected_result

        # FT.PROFILE is not implemented by valkey-search (confirmed absent via
        # COMMAND INFO on 1.3.0-rc1). Assert the rejection directly
        # rather than try/except: swallowing the error here would also hide
        # unrelated failures, and once FT.PROFILE ships this turns into a
        # loud failure instead of a silent no-op.
        with pytest.raises(RequestError, match="unknown command"):
            await ft.profile(
                glide_client,
                index_bicycles,
                FtProfileOptions.from_query_options(
                    query="*", query_options=ft_aggregate_options
                ),
            )

        assert await ft.dropindex(glide_client, index_bicycles) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, protocol
    ):
        prefix_movies = "{movies-" + str(uuid.uuid4()) + "}:"
        index_movies = prefix_movies + str(uuid.uuid4())
        # Create index for movies data.
        await TestFt._create_index_for_ft_aggregate_with_movies_data(
            self,
            glide_client,
            index_movies,
            prefix_movies,
        )
        # Set JSON keys with movies data.
        await TestFt._create_hash_keys_for_ft_aggregate_with_movies_data(
            self, glide_client, prefix_movies
        )
        # Wait for index to be updated.
        time.sleep(self.sleep_wait_time)

        # Run FT.AGGREGATE command with the following arguments:
        # ['FT.AGGREGATE', '{movies}:5a0e6257-3488-4514-96f2-f4c80f6cb0a9', '*', 'LOAD', '*', 'APPLY', 'ceil(@rating)', 'AS',
        # 'r_rating', 'GROUPBY', '1', '@genre', 'REDUCE', 'COUNT', '0', 'AS', 'nb_of_movies', 'REDUCE', 'SUM', '1', '@votes',
        # 'AS', 'nb_of_votes', 'REDUCE', 'AVG', '1', '@r_rating', 'AS', 'avg_rating', 'SORTBY', '4', '@avg_rating', 'DESC',
        # '@nb_of_votes', 'DESC']
        # Testing for bytes type input.
        ft_aggregate_options: FtAggregateOptions = FtAggregateOptions(
            loadAll=True,
            clauses=[
                FtAggregateApply(expression=b"ceil(@rating)", name=b"r_rating"),
                FtAggregateGroupBy(
                    [b"@genre"],
                    [
                        FtAggregateReducer(b"COUNT", [], b"nb_of_movies"),
                        FtAggregateReducer(b"SUM", [b"@votes"], b"nb_of_votes"),
                        FtAggregateReducer(b"AVG", [b"@r_rating"], b"avg_rating"),
                    ],
                ),
                FtAggregateSortBy(
                    properties=[
                        FtAggregateSortProperty(b"@avg_rating", OrderBy.DESC),
                        FtAggregateSortProperty(b"@nb_of_votes", OrderBy.DESC),
                    ]
                ),
            ],
        )
        result = await ft.aggregate(
            glide_client,
            index_name=index_movies.encode("utf-8"),
            query=b"*",
            options=ft_aggregate_options,
        )
        sorted_result = sorted(
            result,
            key=lambda x: (
                x[b"genre"],
                x[b"nb_of_movies"],
                x[b"nb_of_votes"],
                x[b"avg_rating"],
            ),
        )
        # Note: valkey-search 1.3.0-rc1 returns FT.AGGREGATE reducer output
        # (COUNT/SUM/AVG) as bulk strings under both RESP2 and RESP3; unlike
        # RediSearch, it does not promote these to RESP3 doubles. Confirmed via
        # a raw redis-py client bypassing GLIDE entirely: identical byte-string
        # reply shape under protocol=2 and protocol=3.
        expected_result = sorted(
            [
                {
                    b"genre": b"Drama",
                    b"nb_of_movies": b"1",
                    b"nb_of_votes": b"1563839",
                    b"avg_rating": b"10",
                },
                {
                    b"genre": b"Action",
                    b"nb_of_movies": b"2",
                    b"nb_of_votes": b"2033895",
                    b"avg_rating": b"9",
                },
                {
                    b"genre": b"Thriller",
                    b"nb_of_movies": b"1",
                    b"nb_of_votes": b"559490",
                    b"avg_rating": b"9",
                },
            ],
            key=lambda x: (
                x[b"genre"],
                x[b"nb_of_movies"],
                x[b"nb_of_votes"],
                x[b"avg_rating"],
            ),
        )
        assert expected_result == sorted_result

        # FT.PROFILE is not implemented by valkey-search (confirmed absent via
        # COMMAND INFO on 1.3.0-rc1). Assert the rejection directly
        # rather than try/except: swallowing the error here would also hide
        # unrelated failures, and once FT.PROFILE ships this turns into a
        # loud failure instead of a silent no-op.
        with pytest.raises(RequestError, match="unknown command"):
            await ft.profile(
                glide_client,
                index_movies,
                FtProfileOptions.from_query_options(
                    query="*", query_options=ft_aggregate_options
                ),
            )

        assert await ft.dropindex(glide_client, index_movies) == OK

    async def _create_index_for_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, index_name: TEncodable, prefix
    ):
        fields: List[Field] = [
            TextField("$.model", "model"),
            TextField("$.description", "description"),
            NumericField("$.price", "price"),
            TagField("$.condition", "condition", ","),
        ]
        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.JSON, prefixes=[prefix]),
            )
            == OK
        )

    async def _create_json_keys_for_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, prefix
    ):
        assert (
            await GlideJson.set(
                glide_client,
                prefix + "0",
                ".",
                '{"brand": "Velorim", "model": "Jigger", "price": 270, "description":'
                + ' "Small and powerful, the Jigger is the best ride for the smallest of tikes!'
                + " This is the tiniest kids\\u2019 pedal bike on the market available without a"
                + " coaster brake, the Jigger is the vehicle of choice for the rare tenacious"
                + ' little rider raring to go.", "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "1",
                ".",
                '{"brand": "Bicyk", "model": "Hillcraft", "price": 1200, "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "2",
                ".",
                '{"brand": "Nord", "model": "Chook air 5", "price": 815, "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "3",
                ".",
                '{"brand": "Eva", "model": "Eva 291", "price": 3400, "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "4",
                ".",
                '{"brand": "Noka Bikes", "model": "Kahuna", "price": 3200, "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "5",
                ".",
                '{"brand": "Breakout", "model": "XBN 2.1 Alloy", "price": 810, "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "6",
                ".",
                '{"brand": "ScramBikes", "model": "WattBike", "price": 2300, "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "7",
                ".",
                '{"brand": "Peaknetic", "model": "Secto", "price": 430, "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "8",
                ".",
                '{"brand": "nHill", "model": "Summit", "price": 1200, "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "9",
                ".",
                '{"model": "ThrillCycle", "brand": "BikeShind", "price": 815, "condition": "refurbished"}',
            )
            == OK
        )

    async def _create_index_for_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, index_name: TEncodable, prefix
    ):
        fields: List[Field] = [
            TextField("title"),
            NumericField("release_year"),
            NumericField("rating"),
            TagField("genre"),
            NumericField("votes"),
        ]
        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

    async def _create_hash_keys_for_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, prefix
    ):
        await glide_client.hset(
            prefix + "11002",
            {
                "title": "Star Wars: Episode V - The Empire Strikes Back",
                "release_year": "1980",
                "genre": "Action",
                "rating": "8.7",
                "votes": "1127635",
                "imdb_id": "tt0080684",
            },
        )

        await glide_client.hset(
            prefix + "11003",
            {
                "title": "The Godfather",
                "release_year": "1972",
                "genre": "Drama",
                "rating": "9.2",
                "votes": "1563839",
                "imdb_id": "tt0068646",
            },
        )

        await glide_client.hset(
            prefix + "11004",
            {
                "title": "Heat",
                "release_year": "1995",
                "genre": "Thriller",
                "rating": "8.2",
                "votes": "559490",
                "imdb_id": "tt0113277",
            },
        )

        await glide_client.hset(
            prefix + "11005",
            {
                "title": "Star Wars: Episode VI - Return of the Jedi",
                "release_year": "1983",
                "genre": "Action",
                "rating": "8.3",
                "votes": "906260",
                "imdb_id": "tt0086190",
            },
        )

    @pytest.mark.skip(
        reason="Index aliasing (FT.ALIASLIST) did not make the valkey-search 1.3"
        " cut; still open as valkey-io/valkey-search#1184. Confirmed absent in"
        " 1.3.0-rc1 via COMMAND INFO. Fast-follow once #1184 merges."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliaslist(self, glide_client: GlideClusterClient):
        index_name: str = str(uuid.uuid4())
        alias: str = "alias"
        # Create an index and add an alias.
        await TestFt._create_test_index_hash_type(self, glide_client, index_name)
        assert await ft.aliasadd(glide_client, alias, index_name) == OK

        # Create a second index and add an alias.
        index_name_string = str(uuid.uuid4())
        index_name_bytes = bytes(index_name_string, "utf-8")
        alias_name_bytes = b"alias-bytes"
        await TestFt._create_test_index_hash_type(self, glide_client, index_name_string)
        assert await ft.aliasadd(glide_client, alias_name_bytes, index_name_bytes) == OK

        # List all aliases.
        result = await ft.aliaslist(glide_client)
        assert result == {
            b"alias": index_name.encode("utf-8"),
            b"alias-bytes": index_name_bytes,
        }

        # Drop all indexes.
        assert await ft.dropindex(glide_client, index_name) == OK
        assert await ft.dropindex(glide_client, index_name_string) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_nocontent(self, glide_client: GlideClusterClient):
        prefix = "{nocontent-search-" + str(uuid.uuid4()) + "}:"
        key = prefix + "doc"
        index = prefix + "idx"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField(name="title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        assert await glide_client.hset(key, {"title": "hello world"}) == 1
        await anyio.sleep(self.sleep_wait_time)

        result = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(nocontent=True),
        )
        # NOCONTENT: count is 1, doc entry has an empty fields map
        assert result[0] == 1
        result_map = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result[1]
        )
        assert len(result_map) == 1
        for doc_fields in result_map.values():
            assert doc_fields == {}

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_dialect(self, glide_client: GlideClusterClient):
        prefix = "{dialect-search-" + str(uuid.uuid4()) + "}:"
        key1 = prefix + "1"
        index = prefix + str(uuid.uuid4())
        vec_field = "vec"

        vector1 = array.array("f", [1.0, 0.0]).tobytes()

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    VectorField(
                        name=vec_field,
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=VectorType.FLOAT32,
                        ),
                    )
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        assert await glide_client.hset(key1, {vec_field: vector1}) == 1
        await anyio.sleep(self.sleep_wait_time)

        # DIALECT 2 is the only supported dialect in valkey-search 1.1
        knn_query = f"*=>[KNN 1 @{vec_field} $query_vec]"
        result = await ft.search(
            glide_client,
            index,
            knn_query,
            options=FtSearchOptions(
                params={"query_vec": vector1},
                dialect=2,
            ),
        )
        assert result[0] == 1
        result_map = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result[1]
        )
        assert len(result_map) == 1
        # Verify the returned document has field content
        for doc_fields in result_map.values():
            assert len(doc_fields) > 0

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_dialect_invalid(self, glide_client: GlideClusterClient):
        prefix = "{dialect-invalid-" + str(uuid.uuid4()) + "}:"
        index = prefix + "idx"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField(name="title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        # dialect < 2 is not currently supported; expect an error
        with pytest.raises(RequestError) as exc_info:
            await ft.search(
                glide_client,
                index,
                "hello",
                options=FtSearchOptions(dialect=1),
            )
        assert "DIALECT" in str(exc_info.value)

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_1_2_index_options(self, glide_client: GlideClusterClient):
        """Test FT.CREATE with index-level options:
        SCORE, LANGUAGE, SKIPINITIALSCAN, MINSTEMSIZE, WITHOFFSETS/NOOFFSETS,
        NOSTOPWORDS/STOPWORDS, PUNCTUATION.

        Each sub-test uses its own unique prefix to avoid index conflicts
        when create/drop operations race across cluster shards.
        """

        # SKIPINITIALSCAN — index is created but pre-existing keys are not backfilled.
        skip_prefix = "{ft-create-skip-" + str(uuid.uuid4()) + "}:"
        index_skip = skip_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_skip,
                schema=[TextField("title")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[skip_prefix],
                    skipinitialscan=True,
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_skip) == OK

        # SCORE — accepted for RediSearch interoperability (only 1.0 is valid).
        score_prefix = "{ft-create-score-" + str(uuid.uuid4()) + "}:"
        index_score = score_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_score,
                schema=[TextField("title")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[score_prefix],
                    score=1.0,
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_score) == OK

        # LANGUAGE ENGLISH
        lang_prefix = "{ft-create-lang-" + str(uuid.uuid4()) + "}:"
        index_lang = lang_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_lang,
                schema=[TextField("body")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[lang_prefix],
                    language="ENGLISH",
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_lang) == OK

        # MINSTEMSIZE — words shorter than minstemsize are not stemmed.
        # With minstemsize=6, "running" (7 chars) is stemmed to "run",
        # but "plays" (5 chars) is NOT stemmed to "play".
        stem_prefix = "{ft-create-stem-" + str(uuid.uuid4()) + "}:"
        index_stem = stem_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_stem,
                schema=[TextField("title")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[stem_prefix],
                    minstemsize=6,
                ),
            )
            == OK
        )
        await glide_client.hset(stem_prefix + "1", {"title": "running"})
        await glide_client.hset(stem_prefix + "2", {"title": "plays"})
        time.sleep(self.sleep_wait_time)

        result = await ft.search(glide_client, index_stem, "run")
        assert result[0] == 1  # "running" is stemmed to "run"

        result = await ft.search(glide_client, index_stem, "play")
        assert result[0] == 0  # "plays" is NOT stemmed (< 6 chars)

        assert await ft.dropindex(glide_client, index_stem) == OK

        # WITHOFFSETS (default) — explicit flag
        off_prefix = "{ft-create-off-" + str(uuid.uuid4()) + "}:"
        index_offsets = off_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_offsets,
                schema=[TextField("body")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[off_prefix],
                    withoffsets=True,
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_offsets) == OK

        # NOOFFSETS — disables per-word offsets (phrase/slop queries will be rejected)
        nooff_prefix = "{ft-create-nooff-" + str(uuid.uuid4()) + "}:"
        index_nooffsets = nooff_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_nooffsets,
                schema=[TextField("body")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[nooff_prefix],
                    nooffsets=True,
                ),
            )
            == OK
        )
        await glide_client.hset(nooff_prefix + "1", {"body": "hello"})
        time.sleep(self.sleep_wait_time)

        # Basic search works
        result = await ft.search(glide_client, index_nooffsets, "hello")
        assert result[0] == 1

        # SLOP queries should be rejected when NOOFFSETS is set
        with pytest.raises(RequestError):
            await ft.search(
                glide_client,
                index_nooffsets,
                "hello",
                options=FtSearchOptions(slop=1),
            )

        assert await ft.dropindex(glide_client, index_nooffsets) == OK

        # NOSTOPWORDS — all words are indexed, including default stop words.
        nostop_prefix = "{ft-create-nostop-" + str(uuid.uuid4()) + "}:"
        index_nostop = nostop_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_nostop,
                schema=[TextField("body")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[nostop_prefix],
                    nostopwords=True,
                ),
            )
            == OK
        )
        await glide_client.hset(nostop_prefix + "1", {"body": "the quick fox"})
        time.sleep(self.sleep_wait_time)

        # "the" is normally a stop word, but NOSTOPWORDS makes it searchable
        result = await ft.search(glide_client, index_nostop, "the")
        assert result[0] == 1

        assert await ft.dropindex(glide_client, index_nostop) == OK

        # STOPWORDS with custom list — custom stop words are rejected in queries.
        stop_prefix = "{ft-create-stop-" + str(uuid.uuid4()) + "}:"
        index_stopwords = stop_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_stopwords,
                schema=[TextField("body")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[stop_prefix],
                    stopwords=["fox", "an"],
                ),
            )
            == OK
        )
        await glide_client.hset(stop_prefix + "1", {"body": "the quick fox"})
        time.sleep(self.sleep_wait_time)

        # Non-stop words are searchable
        result = await ft.search(glide_client, index_stopwords, "the")
        assert result[0] == 1
        result = await ft.search(glide_client, index_stopwords, "quick")
        assert result[0] == 1

        # Custom stop word "fox" should be rejected
        with pytest.raises(RequestError):
            await ft.search(glide_client, index_stopwords, "fox")

        assert await ft.dropindex(glide_client, index_stopwords) == OK

        # PUNCTUATION with custom characters
        punct_prefix = "{ft-create-punct-" + str(uuid.uuid4()) + "}:"
        index_punct = punct_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_punct,
                schema=[TextField("body")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[punct_prefix],
                    punctuation=".,!?",
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_punct) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_1_2_field_options(self, glide_client: GlideClusterClient):
        """Test FT.CREATE with field-level options:
        TextField: NOSTEM, WITHSUFFIXTRIE, NOSUFFIXTRIE, WEIGHT
        All field types: SORTABLE

        Each sub-test uses its own unique prefix to avoid index conflicts
        when create/drop operations race across cluster shards.
        """

        # TextField with NOSTEM — stemming is disabled, so "hellos" won't match "hello"
        nostem_prefix = "{ft-field-nostem-" + str(uuid.uuid4()) + "}:"
        index_nostem = nostem_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_nostem,
                schema=[TextField("title", nostem=True)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[nostem_prefix]
                ),
            )
            == OK
        )
        await glide_client.hset(nostem_prefix + "1", {"title": "hello"})
        time.sleep(self.sleep_wait_time)

        result = await ft.search(glide_client, index_nostem, "hello")
        assert result[0] == 1
        result = await ft.search(glide_client, index_nostem, "hellos")
        assert result[0] == 0  # No stemming, so "hellos" doesn't match "hello"

        assert await ft.dropindex(glide_client, index_nostem) == OK

        # TextField with WITHSUFFIXTRIE — enables suffix queries like *orld
        suffix_prefix = "{ft-field-suffix-" + str(uuid.uuid4()) + "}:"
        index_suffix = suffix_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_suffix,
                schema=[TextField("title", withsuffixtrie=True)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[suffix_prefix]
                ),
            )
            == OK
        )
        await glide_client.hset(suffix_prefix + "1", {"title": "hello world"})
        time.sleep(self.sleep_wait_time)

        # Suffix query should work with suffix trie
        result = await ft.search(glide_client, index_suffix, "*orld")
        assert result[0] == 1

        assert await ft.dropindex(glide_client, index_suffix) == OK

        # TextField with NOSUFFIXTRIE — disables suffix queries
        nosuffix_prefix = "{ft-field-nosuffix-" + str(uuid.uuid4()) + "}:"
        index_nosuffix = nosuffix_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_nosuffix,
                schema=[TextField("title", nosuffixtrie=True)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[nosuffix_prefix]
                ),
            )
            == OK
        )
        await glide_client.hset(nosuffix_prefix + "1", {"title": "hello world"})
        time.sleep(self.sleep_wait_time)

        # Suffix query should NOT work with NOSUFFIXTRIE
        with pytest.raises(RequestError):
            await ft.search(glide_client, index_nosuffix, "*orld")

        assert await ft.dropindex(glide_client, index_nosuffix) == OK

        # TextField with WEIGHT (only 1.0 is valid per the spec)
        weight_prefix = "{ft-field-weight-" + str(uuid.uuid4()) + "}:"
        index_weight = weight_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_weight,
                schema=[TextField("title", weight=1.0)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[weight_prefix]
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_weight) == OK

        # SORTABLE on TextField
        sort_text_prefix = "{ft-field-sort-text-" + str(uuid.uuid4()) + "}:"
        index_sortable_text = sort_text_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_sortable_text,
                schema=[TextField("title", sortable=True)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[sort_text_prefix]
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_sortable_text) == OK

        # SORTABLE on TagField
        sort_tag_prefix = "{ft-field-sort-tag-" + str(uuid.uuid4()) + "}:"
        index_sortable_tag = sort_tag_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_sortable_tag,
                schema=[TagField("category", sortable=True)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[sort_tag_prefix]
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_sortable_tag) == OK

        # SORTABLE on NumericField
        sort_num_prefix = "{ft-field-sort-num-" + str(uuid.uuid4()) + "}:"
        index_sortable_num = sort_num_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_sortable_num,
                schema=[NumericField("price", sortable=True)],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[sort_num_prefix]
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_sortable_num) == OK

        # Combined: multiple field options on a single index
        combined_prefix = "{ft-field-combined-" + str(uuid.uuid4()) + "}:"
        index_combined = combined_prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index_combined,
                schema=[
                    TextField("title", nostem=True, withsuffixtrie=True, sortable=True),
                    TagField("category", sortable=True),
                    NumericField("price", sortable=True),
                ],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[combined_prefix],
                    language="ENGLISH",
                    minstemsize=3,
                ),
            )
            == OK
        )
        assert await ft.dropindex(glide_client, index_combined) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_info_1_2_options(self, glide_client: GlideClusterClient):
        """Test FT.INFO with LOCAL/PRIMARY/CLUSTER scope options.
        Ref: https://valkey.io/commands/ft.info/
        PRIMARY and CLUSTER require the coordinator (use-coordinator module arg).
        """
        prefix = "{ft-info-opts-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        await glide_client.hset(prefix + "1", {"title": "hello world"})
        time.sleep(self.sleep_wait_time)

        # LOCAL scope - always works
        local_info = await ft.info(glide_client, index, scope=InfoScope.LOCAL)
        # Response is a flat list of key-value pairs
        assert b"index_name" in local_info
        assert b"num_docs" in local_info

        # LOCAL with ALLSHARDS + CONSISTENT - smoke test flags are accepted
        local_with_flags = await ft.info(
            glide_client,
            index,
            scope=InfoScope.LOCAL,
            shard_scope=ShardScope.ALLSHARDS,
            consistency=ConsistencyMode.CONSISTENT,
        )
        assert b"index_name" in local_with_flags

        # LOCAL with SOMESHARDS + INCONSISTENT - smoke test
        local_alt = await ft.info(
            glide_client,
            index,
            scope=InfoScope.LOCAL,
            shard_scope=ShardScope.SOMESHARDS,
            consistency=ConsistencyMode.INCONSISTENT,
        )
        assert b"index_name" in local_alt

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.skip(
        reason="PRIMARY/CLUSTER scope requires the module to be loaded with"
        " use-coordinator yes (src/valkey_search_options.cc), which needs the"
        " real gRPC coordinator/metadata-manager infrastructure wired across"
        " actual cluster nodes -- not just a load-time flag. Confirmed: passing"
        " --use-coordinator yes to a single ad-hoc cluster_manager.py-started"
        " node (even after the shlex fix for module args, which only solved the"
        " module-arg *syntax* problem) fails module init outright ('FATAL"
        " CONFIG FILE ERROR... Bad directive or wrong number of arguments')."
        " cluster_manager.py has no mechanism to stand up the coordinator"
        " service itself. Until a"
        " coordinator-enabled cluster is available this records PRIMARY/"
        "CLUSTER as accepted-but-effect-unverified, matching the PRIMARY case."
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_info_primary_cluster_scope_with_coordinator(
        self, glide_client: GlideClusterClient
    ):
        """Positive case for FT.INFO PRIMARY/CLUSTER scope: asserts the
        scopes actually return PRIMARY/CLUSTER-shaped data when the module is
        loaded with a working coordinator. Skipped (not run) in this harness
        -- see the skip reason for exactly what's missing and why.
        """
        prefix = "{ft-info-coord-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "hello world"})
        await self._poll_until_indexed(glide_client, index, 1)

        primary_info = await ft.info(glide_client, index, scope=InfoScope.PRIMARY)
        assert b"PRIMARY" in primary_info

        cluster_info = await ft.info(glide_client, index, scope=InfoScope.CLUSTER)
        assert b"CLUSTER" in cluster_info

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_info_primary_cluster_scope_without_coordinator(
        self, glide_client: GlideClusterClient
    ):
        """Negative case for FT.INFO PRIMARY/CLUSTER scope: without the
        coordinator (this harness's default -- see
        test_ft_info_primary_cluster_scope_with_coordinator's skip reason),
        both scopes are rejected outright. Runs whenever the coordinator is
        absent, replacing the old try/except (which passed either way and
        proved nothing) with a positive assertion of the rejection.
        """
        prefix = "{ft-info-nocoord-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "hello world"})
        await self._poll_until_indexed(glide_client, index, 1)

        with pytest.raises(RequestError, match="PRIMARY option is not valid"):
            await ft.info(glide_client, index, scope=InfoScope.PRIMARY)

        with pytest.raises(RequestError, match="CLUSTER option is not valid"):
            await ft.info(glide_client, index, scope=InfoScope.CLUSTER)

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_1_2_sortby(self, glide_client: GlideClusterClient):
        """Test FT.SEARCH SORTBY field [ASC|DESC] and WITHSORTKEYS.
        Ref: https://valkey.io/commands/ft.search/
        """
        prefix = "{ft-search-sortby-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TextField("title", sortable=True),
                    NumericField("price", sortable=True),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        # Insert documents
        await glide_client.hset(prefix + "1", {"title": "Banana", "price": "3"})
        await glide_client.hset(prefix + "2", {"title": "Apple", "price": "1"})
        await glide_client.hset(prefix + "3", {"title": "Cherry", "price": "2"})
        time.sleep(self.sleep_wait_time)

        # SORTBY price ASC — query all docs via numeric range
        result_asc = await ft.search(
            glide_client,
            index,
            "@price:[1 +inf]",
            options=FtSearchOptions(
                sortby="price",
                sortby_order=FtSearchOrderBy.ASC,
            ),
        )
        assert result_asc[0] == 3
        keys_asc = list(cast(Mapping, result_asc[1]).keys())
        # Prices should be in ascending order: 1, 2, 3
        prices_asc = [cast(Mapping, result_asc[1])[k][b"price"] for k in keys_asc]
        assert prices_asc == [b"1", b"2", b"3"]

        # SORTBY price DESC
        result_desc = await ft.search(
            glide_client,
            index,
            "@price:[1 +inf]",
            options=FtSearchOptions(
                sortby="price",
                sortby_order=FtSearchOrderBy.DESC,
            ),
        )
        assert result_desc[0] == 3
        keys_desc = list(cast(Mapping, result_desc[1]).keys())
        prices_desc = [cast(Mapping, result_desc[1])[k][b"price"] for k in keys_desc]
        assert prices_desc == [b"3", b"2", b"1"]

        # WITHSORTKEYS — each doc value becomes [sort_key, field_map]
        result_withkeys = await ft.search(
            glide_client,
            index,
            "@price:[1 +inf]",
            options=FtSearchOptions(
                sortby="price",
                sortby_order=FtSearchOrderBy.ASC,
                withsortkeys=True,
            ),
        )
        assert result_withkeys[0] == 3
        # Each value is [sort_key, field_map]; prices should be ascending
        withkeys_map = cast(Mapping, result_withkeys[1])
        sort_keys = [withkeys_map[k][0] for k in withkeys_map]
        assert sort_keys == [b"#1", b"#2", b"#3"]
        # Field maps are still accessible at index 1
        field_prices = [withkeys_map[k][1][b"price"] for k in withkeys_map]
        assert field_prices == [b"1", b"2", b"3"]

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_1_2_text_query_flags(
        self, glide_client: GlideClusterClient
    ):
        """Test FT.SEARCH text query flags: VERBATIM, INORDER, SLOP.
        Ref: https://valkey.io/commands/ft.search/
        """
        prefix = "{ft-search-text-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("body")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        await glide_client.hset(prefix + "1", {"body": "hello world"})
        await glide_client.hset(prefix + "2", {"body": "hello there"})
        await glide_client.hset(prefix + "3", {"body": "goodbye world"})
        await glide_client.hset(prefix + "4", {"body": "world hello"})
        time.sleep(self.sleep_wait_time)

        # VERBATIM - no stemming
        result_verbatim = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(verbatim=True),
        )
        # hello world, hello there, world hello
        assert result_verbatim[0] == 3

        # SLOP without INORDER - allows reordering
        result_slop = await ft.search(
            glide_client,
            index,
            "hello world",
            options=FtSearchOptions(slop=1),
        )
        # hello world, world hello
        assert result_slop[0] == 2

        # SLOP with INORDER - terms must appear in order
        result_inorder = await ft.search(
            glide_client,
            index,
            "hello world",
            options=FtSearchOptions(inorder=True, slop=1),
        )
        # only "hello world"
        assert result_inorder[0] == 1

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_1_2_shard_consistency(
        self, glide_client: GlideClusterClient
    ):
        """Test FT.SEARCH ALLSHARDS/SOMESHARDS and CONSISTENT/INCONSISTENT.
        Ref: https://valkey.io/commands/ft.search/
        These are cluster-mode options; we verify they are accepted without error.
        """
        prefix = "{ft-search-shard-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("val"), TagField("tag")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"val": "42", "tag": "test"})
        await glide_client.hset(prefix + "2", {"val": "99", "tag": "test"})
        time.sleep(self.sleep_wait_time)

        # SOMESHARDS + INCONSISTENT
        # In a healthy cluster, SOMESHARDS still returns all results.
        # This test verifies the option is accepted; partial results only occur
        # with unavailable shards.
        result2 = await ft.search(
            glide_client,
            index,
            "@tag:{test}",
            options=FtSearchOptions(
                shard_scope=ShardScope.SOMESHARDS,
                consistency=ConsistencyMode.INCONSISTENT,
            ),
        )
        assert result2[0] == 2

        # ALLSHARDS + CONSISTENT (defaults)
        result = await ft.search(
            glide_client,
            index,
            "@tag:{test}",
            options=FtSearchOptions(
                shard_scope=ShardScope.ALLSHARDS,
                consistency=ConsistencyMode.CONSISTENT,
            ),
        )
        assert result[0] == 2

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_1_2_query_flags(self, glide_client: GlideClusterClient):
        """Test FT.AGGREGATE with query-parsing flags:
        DIALECT, INORDER, VERBATIM, SLOP.
        Ref: https://valkey.io/commands/ft.aggregate/
        """
        prefix = "{ft-agg-flags-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    NumericField("score"),
                    TextField("title"),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        await glide_client.hset(
            prefix + "1",
            {"score": "10", "title": "hello world"},
        )
        await glide_client.hset(
            prefix + "2",
            {"score": "20", "title": "hello there"},
        )
        time.sleep(self.sleep_wait_time)

        # VERBATIM - disables stemming on the query
        result_verbatim = await ft.aggregate(
            glide_client,
            index,
            query="@score:[1 +inf]",
            options=FtAggregateOptions(verbatim=True),
        )
        # Both docs match; no LOAD so each record is an empty map
        assert len(result_verbatim) == 2
        assert result_verbatim[0] == {}
        assert result_verbatim[1] == {}

        # INORDER + SLOP - proximity matching flags
        result_inorder = await ft.aggregate(
            glide_client,
            index,
            query="@score:[1 +inf]",
            options=FtAggregateOptions(inorder=True, slop=1),
        )
        assert len(result_inorder) == 2
        assert result_inorder[0] == {}
        assert result_inorder[1] == {}

        # DIALECT
        result_dialect = await ft.aggregate(
            glide_client,
            index,
            query="@score:[1 +inf]",
            options=FtAggregateOptions(dialect=2),
        )
        assert len(result_dialect) == 2
        assert result_dialect[0] == {}
        assert result_dialect[1] == {}

        # LOAD - load all fields, filter to single doc
        result_load = await ft.aggregate(
            glide_client,
            index,
            query="@score:[20 +inf]",
            options=FtAggregateOptions(loadAll=True),
        )
        assert len(result_load) == 1
        assert result_load[0] != {}
        assert result_load[0][b"title"] == b"hello there"

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_load_as(
        self, glide_client: TGlideClient, emulate_release_level: str
    ):
        """FT.AGGREGATE LOAD ... AS (valkey-search 1.3.0,
        src/commands/ft_aggregate_parser.cc, compat flag
        `ft_aggregate_load_as`). Confirmed via the 1.3.0 baseline that the
        alias takes effect as the output column name. Mixes a plain string
        LOAD entry with a `LoadField` entry in the same list to confirm the
        two forms compose correctly (non-breaking addition: existing
        plain-string callers keep working unchanged).

        Below `search.emulate-release` 1.3.0, the
        `ft_aggregate_load_as` gate stays on its legacy branch, which never
        consumes the `AS` keyword -- it falls through and gets parsed as its
        own LOAD field, which doesn't exist on the schema, failing the
        clause outright (`src/commands/ft_aggregate_parser.cc`
        `ConstructLoadParser()`). Asserts that legacy failure directly,
        rather than skipping, since the two branches intentionally produce
        different outcomes and both are worth pinning down.
        """
        prefix = "{ft-load-as-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title"), NumericField("price")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "widget", "price": "9"})
        await self._poll_until_indexed(glide_client, index, 1)

        if check_version_lt(emulate_release_level, "1.3.0"):
            with pytest.raises(RequestError, match=r"Index: field `AS` does not exist"):
                await ft.aggregate(
                    glide_client,
                    index,
                    query="*",
                    options=FtAggregateOptions(
                        loadFields=["title", LoadField("price", alias="cost")]
                    ),
                )
        else:
            result = await ft.aggregate(
                glide_client,
                index,
                query="*",
                options=FtAggregateOptions(
                    loadFields=["title", LoadField("price", alias="cost")]
                ),
            )
            assert len(result) == 1
            assert result[0][b"title"] == b"widget"
            assert result[0][b"cost"] == b"9"
            assert b"price" not in result[0]

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_1_2_sortby_with_text_index(
        self, glide_client: GlideClusterClient
    ):
        """Test FT.SEARCH SORTBY on a TEXT field with SORTABLE flag.
        Verifies that SORTBY works correctly on a text field declared SORTABLE.
        """
        prefix = "{ft-sortby-text-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TextField("name", sortable=True),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        await glide_client.hset(prefix + "1", {"name": "Zebra"})
        await glide_client.hset(prefix + "2", {"name": "Aardvark"})
        await glide_client.hset(prefix + "3", {"name": "Mango"})
        time.sleep(self.sleep_wait_time)

        # SORTBY name ASC — query all docs by matching any of the known names
        result = await ft.search(
            glide_client,
            index,
            "Zebra|Aardvark|Mango",
            options=FtSearchOptions(sortby="name", sortby_order=FtSearchOrderBy.ASC),
        )
        assert result[0] == 3
        keys = list(cast(Mapping, result[1]).keys())
        names = [cast(Mapping, result[1])[k][b"name"] for k in keys]
        assert names == [b"Aardvark", b"Mango", b"Zebra"]

        # SORTBY name DESC
        result_desc = await ft.search(
            glide_client,
            index,
            "Zebra|Aardvark|Mango",
            options=FtSearchOptions(sortby="name", sortby_order=FtSearchOrderBy.DESC),
        )
        keys_desc = list(cast(Mapping, result_desc[1]).keys())
        names_desc = [cast(Mapping, result_desc[1])[k][b"name"] for k in keys_desc]
        assert names_desc == [b"Zebra", b"Mango", b"Aardvark"]

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_wildcard(self, glide_client: TGlideClient):
        """Test FT.SEARCH with a bare `*` query, matching every document in the
        index. Prior to valkey-search 1.3 (PR #960) a bare `*` did not run as
        match-all; this directly exercises the fix instead of the
        `@field:[-inf +inf]` workaround used elsewhere in this file.
        """
        prefix = "{ft-wildcard-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title"), NumericField("price")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", WILDCARD_DOCS["1"])
        await glide_client.hset(prefix + "2", WILDCARD_DOCS["2"])
        await glide_client.hset(prefix + "3", WILDCARD_DOCS["3"])
        await self._poll_until_indexed(glide_client, index, len(WILDCARD_DOCS))

        result = await ft.search(glide_client, index, "*", options=FtSearchOptions())
        assert result[0] == len(WILDCARD_DOCS)
        result_map = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result[1]
        )
        assert len(result_map) == len(WILDCARD_DOCS)

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_inkeys(self, glide_client: TGlideClient):
        """Test FT.SEARCH INKEYS option (valkey-search 1.3, PR #967): restricts
        results to only the given set of keys.
        """
        prefix = "{ft-inkeys-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        key1 = prefix + "1"
        key2 = prefix + "2"
        key3 = prefix + "3"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(key1, INKEYS_DOCS["1"])
        await glide_client.hset(key2, INKEYS_DOCS["2"])
        await glide_client.hset(key3, INKEYS_DOCS["3"])
        await self._poll_until_indexed(glide_client, index, len(INKEYS_DOCS))

        # Without INKEYS, all 3 docs match.
        result_all = await ft.search(glide_client, index, "hello")
        assert result_all[0] == len(INKEYS_DOCS)

        # INKEYS restricts the result set to only the listed keys.
        result = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(in_keys=[key1, key2]),
        )
        assert result[0] == 2
        result_map = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result[1]
        )
        assert set(result_map.keys()) == {key1.encode(), key2.encode()}

        # An empty INKEYS list excludes every result.
        result_empty = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(in_keys=[]),
        )
        assert result_empty[0] == 0

        # Bytes type input for keys.
        result_bytes = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(in_keys=[key3.encode("utf-8")]),
        )
        assert result_bytes[0] == 1
        result_bytes_map = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result_bytes[1]
        )
        assert set(result_bytes_map.keys()) == {key3.encode()}

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_reducers_1_3(self, glide_client: TGlideClient):
        """Test the valkey-search 1.3 FT.AGGREGATE reducers: TOLIST (#932),
        FIRST_VALUE (#946), RANDOM_SAMPLE (#949).
        """
        prefix = "{ft-reducers-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TagField("category"),
                    NumericField("price"),
                    TextField("title"),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        await glide_client.hset(prefix + "1", REDUCERS_DOCS["1"])
        await glide_client.hset(prefix + "2", REDUCERS_DOCS["2"])
        await glide_client.hset(prefix + "3", REDUCERS_DOCS["3"])
        await self._poll_until_indexed(glide_client, index, len(REDUCERS_DOCS))

        # TOLIST: collects unique "price" values seen in the group, in
        # first-encountered order; duplicates are removed. No explicit
        # loadFields for "@price" here -- this relies on valkey-search 1.3's
        # auto-load of fields referenced by a pipeline stage (PR #1341,
        # "Implicitly load fields referenced by a pipeline stage"); on a
        # pre-1.3 engine this reducer would need an explicit LOAD clause for
        # @price to resolve.
        result_tolist = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[
                    FtAggregateGroupBy(
                        ["@category"],
                        [FtAggregateReducer.to_list("@price", name="prices")],
                    )
                ]
            ),
        )
        assert len(result_tolist) == 1
        # L-11: TOLIST's "first-encountered order" guarantee (source:
        # ft_aggregate_exec.cc, ToList::ProcessRecord appends in processing
        # order) is NOT the same as HSET insertion order from this test's
        # perspective -- the pipeline's actual encounter order is driven by
        # the index's internal scan order, which this test cannot control or
        # observe independently of the result itself. Tried asserting the
        # raw (unsorted) order against seed data inserted out of numeric
        # order (30 before 10) and it did not hold: the engine returned
        # ['10', '30'] regardless. Sorting here (and only asserting dedup +
        # membership, not order) is honest about what this test actually
        # verifies; the comment on FtAggregateReducer.to_list documents the
        # order guarantee as a source-level fact, not something this
        # integration test independently confirms.
        prices = sorted(
            v.decode() if isinstance(v, bytes) else v
            for v in result_tolist[0][b"prices"]
        )
        assert prices == sorted(REDUCERS_EXPECTED_TOLIST_PRICES)

        # FIRST_VALUE with BY/DESC: returns the title of the record with the
        # highest price in the group.
        result_first_value = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                loadFields=["@title", "@price"],
                clauses=[
                    FtAggregateGroupBy(
                        ["@category"],
                        [
                            FtAggregateReducer.first_value(
                                "@title",
                                by="@price",
                                order=OrderBy.DESC,
                                name="most_expensive_title",
                            )
                        ],
                    )
                ],
            ),
        )
        assert len(result_first_value) == 1
        assert (
            result_first_value[0][b"most_expensive_title"]
            == REDUCERS_EXPECTED_FIRST_VALUE_TITLE
        )

        # RANDOM_SAMPLE: returns at most `size` sampled "price" values; with a
        # sample size >= the group's record count, every value is included.
        result_random_sample = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[
                    FtAggregateGroupBy(
                        ["@category"],
                        [FtAggregateReducer.random_sample("@price", 10, name="sample")],
                    )
                ]
            ),
        )
        assert len(result_random_sample) == 1
        sample = result_random_sample[0][b"sample"]
        assert isinstance(sample, list)
        assert len(sample) == len(REDUCERS_DOCS)  # sample size >= group count

        # RANDOM_SAMPLE with size < count: the reservoir must cap at exactly
        # `size`, not merely tolerate a larger size (a server that
        # ignored `size` entirely would still pass the size>=count case
        # above). Every sampled value must also be a genuine member of the
        # group, i.e. a subset of the full population, not a server bug
        # inventing values.
        result_random_sample_capped = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[
                    FtAggregateGroupBy(
                        ["@category"],
                        [FtAggregateReducer.random_sample("@price", 2, name="sample")],
                    )
                ]
            ),
        )
        assert len(result_random_sample_capped) == 1
        capped_sample = result_random_sample_capped[0][b"sample"]
        assert isinstance(capped_sample, list)
        assert len(capped_sample) == 2  # capped at size, not the group's count of 3
        assert set(capped_sample).issubset(REDUCERS_RANDOM_SAMPLE_POSSIBLE_PRICES)

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_vector_range(self, glide_client: TGlideClient):
        """Test Vector Range queries in FT.SEARCH (valkey-search 1.3, PR #985):
        `@field:[VECTOR_RANGE <radius> $blob]`, optional `$YIELD_DISTANCE_AS`, and
        combination with AND/OR/negation.
        """
        prefix = "{ft-vecrange-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        vec_field = "vec"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    VectorField(
                        name=vec_field,
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=VectorType.FLOAT32,
                        ),
                    ),
                    TagField("category"),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        # Points at known L2 distances from the origin: 0, 1, 4, 9.
        vectors = {
            prefix + key: (array.array("f", list(coords)), category)
            for key, (coords, category) in VECTOR_RANGE_SEARCH_POINTS.items()
        }
        for key, (vec, category) in vectors.items():
            await glide_client.hset(
                key, {vec_field: vec.tobytes(), "category": category}
            )
        await self._poll_until_indexed(glide_client, index, len(vectors))

        query_blob = array.array("f", [0.0, 0.0]).tobytes()

        # Standalone VECTOR_RANGE: radius 2 covers distances 0 and 1 (keys 0, 1);
        # key 2 (distance 4) and key 3 (distance 9) fall outside.
        result = await ft.search(
            glide_client,
            index,
            f"@{vec_field}:[VECTOR_RANGE {VECTOR_RANGE_SEARCH_RADIUS} $blob]",
            options=FtSearchOptions(params={"blob": query_blob}, nocontent=True),
        )
        assert result[0] == len(VECTOR_RANGE_SEARCH_EXPECTED_KEYS)
        keys = set(cast(Mapping, result[1]).keys())
        assert keys == {
            prefix.encode() + k.encode() for k in VECTOR_RANGE_SEARCH_EXPECTED_KEYS
        }

        # YIELD_DISTANCE_AS: the computed distance is returned under the given
        # property name, resolved via PARAMS for both the radius and the blob.
        result_dist = await ft.search(
            glide_client,
            index,
            f"@{vec_field}:[VECTOR_RANGE $r $blob]=>{{$yield_distance_as: dist}}",
            options=FtSearchOptions(
                params={"blob": query_blob, "r": "0"},
                return_fields=[ReturnField("dist")],
            ),
        )
        assert result_dist[0] == 1
        result_dist_map = cast(Mapping, result_dist[1])
        (fields,) = result_dist_map.values()
        assert float(fields[b"dist"]) == 0.0

        # AND with a tag filter: radius 2 (keys 0,1) intersected with category
        # "a" (keys 0,2) leaves only key 0.
        result_and = await ft.search(
            glide_client,
            index,
            f"@{vec_field}:[VECTOR_RANGE 2 $blob] @category:{{a}}",
            options=FtSearchOptions(params={"blob": query_blob}, nocontent=True),
        )
        assert result_and[0] == 1
        assert set(cast(Mapping, result_and[1]).keys()) == {prefix.encode() + b"0"}

        # OR with a tag filter: radius 0 (key 0 only) union category "b"
        # (keys 1, 3) gives keys 0, 1, 3.
        result_or = await ft.search(
            glide_client,
            index,
            f"@{vec_field}:[VECTOR_RANGE 0 $blob] | @category:{{b}}",
            options=FtSearchOptions(params={"blob": query_blob}, nocontent=True),
        )
        assert result_or[0] == 3
        assert set(cast(Mapping, result_or[1]).keys()) == {
            prefix.encode() + b"0",
            prefix.encode() + b"1",
            prefix.encode() + b"3",
        }

        # Negation: everything NOT within radius 1 of the origin (excludes
        # keys 0 and 1, leaves keys 2 and 3).
        result_negate = await ft.search(
            glide_client,
            index,
            f"-@{vec_field}:[VECTOR_RANGE 1 $blob]",
            options=FtSearchOptions(params={"blob": query_blob}, nocontent=True),
        )
        assert result_negate[0] == 2
        assert set(cast(Mapping, result_negate[1]).keys()) == {
            prefix.encode() + b"2",
            prefix.encode() + b"3",
        }

        # Known RC1 limitation: a VECTOR_RANGE predicate used as a KNN
        # pre-filter is rejected ("single-VR model"). This is a server-side
        # limitation of valkey-search 1.3.0-rc1, not a GLIDE restriction.
        with pytest.raises(RequestError, match="KNN"):
            await ft.search(
                glide_client,
                index,
                f"@{vec_field}:[VECTOR_RANGE 5 $blob]=>[KNN 1 @{vec_field} $blob]",
                options=FtSearchOptions(params={"blob": query_blob}),
            )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_vector_range(self, glide_client: TGlideClient):
        """Test Vector Range queries in FT.AGGREGATE (valkey-search 1.3, PR #985)."""
        prefix = "{ft-agg-vecrange-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        vec_field = "vec"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    VectorField(
                        name=vec_field,
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=VectorType.FLOAT32,
                        ),
                    ),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        vectors = [
            array.array("f", list(coords)) for coords in VECTOR_RANGE_AGGREGATE_POINTS
        ]
        for i, vec in enumerate(vectors):
            await glide_client.hset(prefix + str(i), {vec_field: vec.tobytes()})
        await self._poll_until_indexed(glide_client, index, len(vectors))

        query_blob = array.array("f", [0.0, 0.0]).tobytes()

        # FT.AGGREGATE with a VECTOR_RANGE filter and YIELD_DISTANCE_AS,
        # loading the distance and sorting by it.
        result = await ft.aggregate(
            glide_client,
            index,
            query=(
                f"@{vec_field}:[VECTOR_RANGE {VECTOR_RANGE_AGGREGATE_RADIUS} $blob]"
                "=>{$yield_distance_as: dist}"
            ),
            options=FtAggregateOptions(
                loadFields=["dist"],
                params={"blob": query_blob},
                clauses=[
                    FtAggregateSortBy(
                        properties=[FtAggregateSortProperty("@dist", OrderBy.ASC)]
                    )
                ],
            ),
        )
        assert len(result) == len(VECTOR_RANGE_AGGREGATE_EXPECTED_DISTANCES)
        distances = sorted(float(row[b"dist"]) for row in result)
        assert distances == VECTOR_RANGE_AGGREGATE_EXPECTED_DISTANCES

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_vsim_range_rejected(self, glide_client: TGlideClient):
        """FT.HYBRID's VSIM ... RANGE form parses for shape but is
        rejected at execution on valkey-search 1.3.0-rc1 -- confirmed live
        (ParseFtHybridCommand's own comment in
        src/commands/ft_hybrid_parser.cc: "Validate the RANGE shape;
        ParseFtHybridCommand returns UnimplementedError after parse
        completes"). Per Jon's decision, RANGE stays excluded from any
        future typed FT.HYBRID API entirely -- this test exists so that if
        rc1's limitation is ever lifted upstream, this fails loudly instead
        of the exclusion silently going stale.

        This goes through custom_command directly rather than the typed
        `ft.hybrid` wrapper (which now exists), because RANGE stays
        excluded from the typed API entirely per the decision above --
        this also exercises the RANGE clause's own quirk confirmed during
        this investigation: `RANGE
        <count> ...` takes a token-budget count for its own sub-arguments
        (mirroring INKEYS/PARAMS/RETURN elsewhere in this module), not an
        arbitrary result count -- `RANGE 2 RADIUS 5.0` because `RADIUS 5.0`
        is 2 tokens, not `RANGE 10 RADIUS 5.0` as a naive reading of
        "RANGE <n>" might suggest.
        """
        prefix = "{ft-hybrid-range-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        vec_field = "vec"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TextField("title"),
                    VectorField(
                        name=vec_field,
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=VectorType.FLOAT32,
                        ),
                    ),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        query_blob = array.array("f", [1.0, 2.0]).tobytes()

        with pytest.raises(RequestError, match="RANGE is not yet supported"):
            await glide_client.custom_command(
                [
                    "FT.HYBRID",
                    index,
                    "SEARCH",
                    "hello",
                    "VSIM",
                    f"@{vec_field}",
                    "$blob",
                    "RANGE",
                    "2",
                    "RADIUS",
                    "5.0",
                    "PARAMS",
                    "2",
                    "blob",
                    query_blob,
                ]
            )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_sortby_adjacent_limit_widens_retained_rows(
        self, glide_client: TGlideClient, emulate_release_level: str
    ):
        """valkey-search 1.3.0's "LIMIT widens an adjacent SORTBY"
        compatibility fix (src/commands/ft_aggregate_parser.cc
        ResolveSortByBounds(), "sortby_max_follows_limit"). Before 1.3, a
        bare SORTBY always truncated to its own default (10 rows) or
        explicit MAX, so a LIMIT past that point returned fewer rows than
        asked for -- confirmed in the source's own measured table (cited
        below) and reproduced live here, matching every row of that table:

            SORTBY, no MAX, no LIMIT          ->    10 rows (the default)
            SORTBY MAX 5, no LIMIT            ->     5 rows
            SORTBY + LIMIT 0 15 (adjacent)    ->    15 rows (LIMIT raises it)
            SORTBY MAX 5 + LIMIT 0 12         ->    12 rows (LIMIT outranks MAX)
            LIMIT 0 15 then SORTBY            ->    15 rows (either side counts)
            SORTBY + GROUPBY + LIMIT 0 15     ->    10 rows (LIMIT is not
                                                      adjacent -- GROUPBY sits
                                                      between them, so it does
                                                      not widen SORTBY)

        No existing test exercised FtAggregateLimit or FtSearchLimit at all
        before this (Q0 rc1 surface inventory) -- this test uses
        both typed classes rather than hand-built argv, so the typed API
        itself is exercised, not just the raw wire behaviour.

        Below `search.emulate-release` 1.3.0, the
        `sortby_max_follows_limit` gate stays on its legacy branch
        (`sortby->max_` keeps the explicit MAX, or the SORTBY default if
        none was given, regardless of any adjacent LIMIT). Only the two
        rows above where an adjacent LIMIT would otherwise widen the
        retained window change: both cap at the SORTBY's own max (10 and 5
        respectively) instead of the LIMIT's count. The no-adjacent-LIMIT
        rows and the GROUPBY-blocks-widening row are identical on both
        branches, since neither one involves the gate at all.
        """
        prefix = "{ft-sortby-limit-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        legacy = check_version_lt(emulate_release_level, "1.3.0")

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("n", sortable=True)],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for i in range(1, 21):
            await glide_client.hset(prefix + f"d{i}", {"n": str(i)})
        await self._poll_until_indexed(glide_client, index, 20)

        sort_asc = FtAggregateSortBy(
            properties=[FtAggregateSortProperty("@n", OrderBy.ASC)]
        )

        # SORTBY, no MAX, no LIMIT -> the default 10 rows. Unaffected by the
        # gate: no adjacent LIMIT, same on both branches.
        result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(clauses=[sort_asc]),
        )
        assert len(result) == 10

        # SORTBY MAX 5, no LIMIT -> 5 rows. Unaffected by the gate: no
        # adjacent LIMIT, same on both branches.
        sort_max_5 = FtAggregateSortBy(
            properties=[FtAggregateSortProperty("@n", OrderBy.ASC)], max=5
        )
        result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(clauses=[sort_max_5]),
        )
        assert len(result) == 5

        # SORTBY + LIMIT 0 15, adjacent -> the 1.3 fix raises the retained
        # window to 15 rows; below 1.3.0, the adjacent LIMIT does not widen
        # SORTBY's own default-10 retention.
        result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[sort_asc, FtAggregateLimit(offset=0, count=15)]
            ),
        )
        assert len(result) == (10 if legacy else 15)

        # SORTBY MAX 5 + LIMIT 0 12, adjacent -> the 1.3 fix lets LIMIT
        # outrank MAX; below 1.3.0, SORTBY's own MAX 5 still wins.
        result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[sort_max_5, FtAggregateLimit(offset=0, count=12)]
            ),
        )
        assert len(result) == (5 if legacy else 12)

        # LIMIT 0 15 THEN SORTBY -- either side of the pair counts as
        # adjacent, not just LIMIT-after-SORTBY. Same legacy-vs-fixed split
        # as the SORTBY-then-LIMIT row above.
        result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[FtAggregateLimit(offset=0, count=15), sort_asc]
            ),
        )
        assert len(result) == (10 if legacy else 15)

        # SORTBY + GROUPBY + LIMIT 0 15 -- GROUPBY sits between SORTBY and
        # LIMIT, so the LIMIT is not adjacent and must NOT widen SORTBY's
        # default-10 retention. Unaffected by the gate: GROUPBY blocks
        # widening on both branches.
        result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[
                    sort_asc,
                    FtAggregateGroupBy(properties=["@n"], reducers=[]),
                    FtAggregateLimit(offset=0, count=15),
                ]
            ),
        )
        assert len(result) == 10

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_limit_pagination(self, glide_client: TGlideClient):
        """FtSearchLimit exercised directly -- offset/count pagination
        on FT.SEARCH, unaffected by the SORTBY-specific 1.3 fix above (FT.
        SEARCH's LIMIT is the server's ordinary offset/count pagination,
        with no analogous MAX-widening behaviour to verify)."""
        prefix = "{ft-search-limit-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("n", sortable=True)],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for i in range(1, 11):
            await glide_client.hset(prefix + f"d{i}", {"n": str(i)})
        await self._poll_until_indexed(glide_client, index, 10)

        result = await ft.search(
            glide_client,
            index,
            "*",
            options=FtSearchOptions(limit=FtSearchLimit(offset=0, count=3)),
        )
        assert result[0] == 10
        assert len(cast(Mapping, result[1])) == 3

        result_page_2 = await ft.search(
            glide_client,
            index,
            "*",
            options=FtSearchOptions(limit=FtSearchLimit(offset=3, count=3)),
        )
        assert result_page_2[0] == 10
        assert len(cast(Mapping, result_page_2[1])) == 3

        # Offset past the end returns the total count with no rows -- a
        # bare single-element [count] array, not [count, {}] (confirmed
        # live, same zero-match passthrough shape documented for WITHSCORES
        # during earlier investigation).
        result_past_end = await ft.search(
            glide_client,
            index,
            "*",
            options=FtSearchOptions(limit=FtSearchLimit(offset=100, count=3)),
        )
        assert result_past_end[0] == 10
        assert len(result_past_end) == 1

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "vector_type,encode",
        [
            (
                VectorType.FLOAT16,
                lambda values: b"".join(struct.pack("<e", v) for v in values),
            ),
            (
                VectorType.BFLOAT16,
                lambda values: b"".join(struct.pack("<f", v)[2:4] for v in values),
            ),
        ],
    )
    async def test_ft_create_float16_bfloat16_vector(
        self,
        glide_client: TGlideClient,
        vector_type: VectorType,
        encode,
    ):
        """FLOAT16 and BFLOAT16 vector types (valkey-search 1.3,
        src/indexes/vector_base.h -- 2-byte-per-element formats). GLIDE does
        not encode vectors on the caller's behalf for any type, FLOAT32
        included; callers must pass pre-encoded bytes. Test values (0.0,
        1.0, 2.0) are exact binary powers so FLOAT16's own rounding and
        BFLOAT16's round-to-nearest-ties-to-even (src/indexes/bfloat16.h)
        agree with the simple truncation used to encode BFLOAT16 here --
        avoids coupling this test's correctness to the engine's specific
        rounding mode.
        """
        prefix = "{ft-vectype-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        vec_field = "vec"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    VectorField(
                        name=vec_field,
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=vector_type,
                        ),
                    ),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        points = {
            "0": (0.0, 0.0),
            "1": (1.0, 0.0),
            "2": (2.0, 0.0),
        }
        for key_suffix, coords in points.items():
            await glide_client.hset(prefix + key_suffix, {vec_field: encode(coords)})
        await self._poll_until_indexed(glide_client, index, len(points))

        query_blob = encode((1.0, 0.0))
        knn_query = f"*=>[KNN 1 @{vec_field} $blob]"
        result = await ft.search(
            glide_client,
            index,
            knn_query,
            options=FtSearchOptions(
                params={"blob": query_blob},
                return_fields=[ReturnField(f"__{vec_field}_score")],
            ),
        )
        assert result[0] == 1
        result_map = cast(Mapping[TEncodable, Mapping], result[1])
        matched_key = (prefix + "1").encode()
        assert matched_key in result_map
        assert float(result_map[matched_key][f"__{vec_field}_score".encode()]) == 0.0

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_filter(self, glide_client: TGlideClient):
        """FT.CREATE FILTER (valkey-search 1.3.0,
        src/commands/ft_create_parser.cc -- ParseFilter() genuinely sets
        index_schema_proto.set_filter(), confirmed functional (not a no-op
        like NOHL) during Group 7 triage. A document whose key matches a
        configured prefix is still excluded from the index if it fails the
        filter expression.
        """
        prefix = "{ft-filter-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("age")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[prefix],
                    filter="@age > 18",
                ),
            )
            == OK
        )

        await glide_client.hset(prefix + "adult", {"age": "25"})
        await glide_client.hset(prefix + "minor", {"age": "10"})
        await self._poll_until_indexed(glide_client, index, 1)

        result = await ft.search(
            glide_client, index, "*", options=FtSearchOptions(nocontent=True)
        )
        assert result[0] == 1
        result_map = cast(Mapping[TEncodable, Mapping], result[1])
        assert set(result_map.keys()) == {(prefix + "adult").encode()}

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_sortable_unf(self, glide_client: TGlideClient):
        """FT.CREATE SORTABLE UNF (valkey-search 1.3.0,
        src/commands/ft_create_parser.cc -- UNF is only recognized directly
        after SORTABLE, as in Redis).

        UNF is parsed, stored, and round-tripped through FT.INFO -- all
        asserted below. It does NOT change observed SORTBY order on this
        engine build: confirmed empirically (both via raw valkey-cli probes
        before writing this test, and via the assertion at the end of this
        test) that TEXT/TAG sort keys are always compared byte-for-byte
        (ASCII order, uppercase before lowercase) regardless of UNF or
        CASESENSITIVE -- e.g. "Banana" sorts before "apple" identically
        whether UNF is set or not. No normalisation call site references
        AttributeOptions::unf anywhere in the sort/compare path
        (src/attribute.h/.cc, src/index_schema.cc only plumb it through to
        storage and FT.INFO). If a future valkey-search version wires UNF
        into sort-key normalisation, this assertion will fail loudly and
        should be updated with a source citation, the same way the other
        engine-behaviour-pinning tests in this file are.
        """
        prefix = "{ft-unf-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title", sortable=True, unf=True)],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        info_raw = await ft.info(glide_client, index)
        info = pair_flat_list(info_raw)
        attributes_raw = cast(List, info[b"attributes"])
        assert len(attributes_raw) == 1
        attr = pair_flat_list(attributes_raw[0])
        assert attr[b"SORTABLE"] == b"1"
        assert attr[b"UNF"] == b"1"

        await glide_client.hset(prefix + "1", {"title": "apple"})
        await glide_client.hset(prefix + "2", {"title": "Banana"})
        await self._poll_until_indexed(glide_client, index, 2)

        result = await ft.search(
            glide_client,
            index,
            "*",
            options=FtSearchOptions(
                return_fields=[ReturnField("title")],
                sortby="title",
                sortby_order=OrderBy.ASC,
            ),
        )
        ordered_keys = list(cast(Mapping[TEncodable, Mapping], result[1]).keys())
        # ASCII order: uppercase 'B' (0x42) sorts before lowercase 'a' (0x61).
        assert ordered_keys == [(prefix + "2").encode(), (prefix + "1").encode()]

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_score_field_in_index_definition(
        self, glide_client: TGlideClient, emulate_release_level: str
    ):
        """FT.CREATE's SCORE_FIELD option (
        FtCreateOptions(score_field=...)) is reported back under FT.INFO's
        `index_definition`, alongside a `default_score` entry -- confirmed
        live. No existing test converted or asserted either field even
        though SCORE_FIELD support was added; this closes that gap.

        Below `search.emulate-release` 1.3.0, the
        `ft_info_score_field` gate stays on its legacy branch
        (`src/index_schema.cc` `RespondWithInfo()`): `index_definition`
        never gains a `score_field` pair at all (6 elements, not 8, before
        any `filter` pair), and `default_score` is always the literal
        string `"1"` rather than the real configured score as a double --
        SCORE only ever accepted 1.0 at the time, so the literal was
        always accurate. Asserts that legacy shape directly.
        """
        prefix = "{ft-score-field-info-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[prefix],
                    score_field="boost",
                ),
            )
            == OK
        )

        info_raw = await ft.info(glide_client, index)
        info = pair_flat_list(info_raw)
        index_def = pair_flat_list(info[b"index_definition"])
        if check_version_lt(emulate_release_level, "1.3.0"):
            assert b"score_field" not in index_def
            assert index_def[b"default_score"] == b"1"
        else:
            assert index_def[b"score_field"] == b"boost"
            assert b"default_score" in index_def

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_no_highlight(self, glide_client: TGlideClient):
        """Q9: FT.CREATE NOHL (FtCreateOptions(no_highlight=)). Confirmed
        live and in source (src/commands/ft_create_parser.cc: "Highlighting
        is not supported, so NOHL is accepted and the match result
        deliberately discarded") that this is a genuine no-op on
        valkey-search <= 1.3.0-rc1 -- unlike SORTABLE UNF (also a no-op,
        but stored and reported by FT.INFO), NOHL is not even stored: this
        test asserts only that the server accepts the index creation, not
        any round-trip through FT.INFO, since there is nothing to round-trip.
        """
        prefix = "{ft-nohl-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(
                    data_type=DataType.HASH, prefixes=[prefix], no_highlight=True
                ),
            )
            == OK
        )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_with_scores_and_scorer(self, glide_client: TGlideClient):
        """Scoring had no live integration test before this. FtSearchOptions(
        with_scores=True, scorer="BM25STD") -- confirmed live BM25STD is
        the only registered/selectable scorer name, and WITHSCORES changes
        each document's value from a plain field map to a [score,
        field_map] pair.

        The original version of this test seeded two documents
        with an equal number of occurrences of the search term ("hello
        world" / "hello there"), so BM25STD gave them identical scores and
        a scorer that always returned a constant (or that silently fell
        back to a no-op) would have passed undetected -- `score > 0` is
        true either way. This version repeats the search term in one
        document so the two scores are confirmed live to differ, and
        asserts the repeated-term document scores strictly higher."""
        prefix = "{ft-with-scores-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "hello hello hello world"})
        await glide_client.hset(prefix + "2", {"title": "hello there"})
        await self._poll_until_indexed(glide_client, index, 2)

        result = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(with_scores=True, scorer="BM25STD"),
        )
        assert result[0] == 2
        result_map = cast(Mapping, result[1])
        assert len(result_map) == 2
        scores_by_key: Dict[bytes, float] = {}
        for key, doc_value in result_map.items():
            score_and_fields = cast(List, doc_value)
            assert len(score_and_fields) == 2
            score = float(score_and_fields[0])
            assert score > 0
            scores_by_key[cast(bytes, key)] = score
            fields = cast(Mapping, score_and_fields[1])
            assert b"title" in fields
        assert (
            scores_by_key[(prefix + "1").encode()]
            > scores_by_key[(prefix + "2").encode()]
        ), (
            "the document repeating the search term should score strictly "
            f"higher under BM25STD: {scores_by_key}"
        )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_with_scores_and_withsortkeys_combined_shape(
        self, glide_client: TGlideClient
    ):
        """The combined WITHSCORES+WITHSORTKEYS reply-shape converter
        (glide-core's FTSearchWithScoresAndSortKeysReturnType) had no live
        integration test. Confirmed live: each document's value becomes a
        [score, sort_key, field_map] triple, a genuinely different shape
        from either flag alone (plain [field_map], or [score, field_map],
        or [sort_key, field_map])."""
        prefix = "{ft-scores-sortkeys-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TextField("title", sortable=True),
                    NumericField("price", sortable=True),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "hello", "price": "10"})
        await glide_client.hset(prefix + "2", {"title": "world", "price": "20"})
        await self._poll_until_indexed(glide_client, index, 2)

        result = await ft.search(
            glide_client,
            index,
            "*",
            options=FtSearchOptions(
                with_scores=True,
                sortby="price",
                sortby_order=FtSearchOrderBy.ASC,
                withsortkeys=True,
            ),
        )
        assert result[0] == 2
        result_map = cast(Mapping, result[1])
        assert len(result_map) == 2
        for doc_value in result_map.values():
            triple = cast(List, doc_value)
            assert len(triple) == 3
            score, sort_key, fields = triple
            assert float(score) >= 0
            assert sort_key is not None
            assert b"title" in cast(Mapping, fields)

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_add_scores_sortable_by_score(
        self, glide_client: TGlideClient
    ):
        """FtAggregateOptions(add_scores=True) exposes __score as a
        pipeline field usable in SORTBY, confirmed live. No live
        integration test existed for ADDSCORES before this."""
        prefix = "{ft-add-scores-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "hello world"})
        await glide_client.hset(prefix + "2", {"title": "hello there friend"})
        await self._poll_until_indexed(glide_client, index, 2)

        result = await ft.aggregate(
            glide_client,
            index,
            "hello",
            options=FtAggregateOptions(
                add_scores=True,
                loadFields=["title"],
                clauses=[
                    FtAggregateSortBy(
                        properties=[FtAggregateSortProperty("@__score", OrderBy.DESC)]
                    )
                ],
            ),
        )
        assert len(result) == 2
        for row in result:
            assert b"__score" in row
            assert float(row[b"__score"]) > 0
        # Sorted descending by __score -- the fused ordering must be stable
        # and monotonic, not merely present.
        scores = [float(row[b"__score"]) for row in result]
        assert scores == sorted(scores, reverse=True)

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_create_score_field_affects_ranking(
        self, glide_client: TGlideClient
    ):
        """FtCreateOptions(score_field=...) had no live integration
        test confirming it actually affects ranking, as opposed to merely
        being accepted. Two otherwise-identical documents, one with a much
        higher score_field value, must rank first and score higher under
        WITHSCORES -- confirmed live this is a real multiplicative boost on
        the relevance score, not a no-op like NOHL/UNF."""
        prefix = "{ft-score-field-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(
                    data_type=DataType.HASH,
                    prefixes=[prefix],
                    score_field="boost",
                ),
            )
            == OK
        )
        await glide_client.hset(
            prefix + "low", {"title": "hello world", "boost": "1.0"}
        )
        await glide_client.hset(
            prefix + "high", {"title": "hello world", "boost": "100.0"}
        )
        await self._poll_until_indexed(glide_client, index, 2)

        result = await ft.search(
            glide_client,
            index,
            "hello",
            options=FtSearchOptions(with_scores=True),
        )
        assert result[0] == 2
        result_map = cast(Mapping, result[1])
        # The high-boost document must rank first (FT.SEARCH's result map
        # preserves server-returned ranking order) and score strictly
        # higher than the low-boost one.
        keys_in_order = list(result_map.keys())
        assert keys_in_order[0] == (prefix + "high").encode()
        high_score = float(cast(List, result_map[(prefix + "high").encode()])[0])
        low_score = float(cast(List, result_map[(prefix + "low").encode()])[0])
        assert high_score > low_score

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_multi_shard_no_hashtag(self, glide_client: GlideClusterClient):
        """A cross-shard FT.CREATE/FT.SEARCH/FT.AGGREGATE test with no
        hashtag co-location, re-checking on 1.3.0-rc1 whether FT.CREATE
        broadcasts cluster-wide and whether ALLSHARDS makes search/aggregate
        see documents on every shard (the 1.2.1
        baseline found FT.CREATE single-node and ALLSHARDS non-aggregating).

        This test verifies per-node placement directly (running
        the module's list-objects command on every primary, not just the node
        you connected to) rather than inferring it from search results, and
        asserts the OBSERVED cross-shard behaviour rather than reshaping the
        fixture to force a pass. If the result below ever changes (i.e. a
        future valkey-search version makes FT.CREATE broadcast or ALLSHARDS
        aggregate), this assertion will fail loudly and should be updated
        with a source citation for the fix, the same way the compat-gated
        tests elsewhere in this file are.

        Key placement across shards is deterministic (affonsov review
        #7335), not left to chance: after FT.CREATE lands on one primary,
        this test finds the real CLUSTER KEYSLOT outcome for enough
        candidate key suffixes to guarantee at least one key on the
        index-owning shard and the rest elsewhere, rather than generating a
        fixed suffix set and hoping it spreads. The earlier version had an
        approximately 0.8% chance per run (on a 3-primary cluster) of every
        key landing off the owning shard, which would have hung
        `_poll_until_indexed` waiting for a document count that could never
        arrive even though the implementation was behaving correctly.
        """
        prefix = "ft-multishard-" + str(uuid.uuid4()) + ":"
        index = prefix + "idx"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

        cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        node_addrs = cluster.nodes_addr
        assert len(node_addrs) >= 2, "test requires a real multi-shard cluster"

        # CLUSTER SHARDS gives this cluster's slot ranges; mapped to a
        # callable rather than hardcoding port numbers, so this doesn't
        # depend on a specific cluster layout.
        shards_reply = cast(
            List, await glide_client.custom_command(["CLUSTER", "SHARDS"])
        )
        slot_ranges: List[tuple] = []
        for shard_idx, shard in enumerate(shards_reply):
            slots = normalize_cluster_shards_entry(shard)[b"slots"]
            for lo, hi in zip(slots[0::2], slots[1::2]):
                slot_ranges.append((lo, hi, shard_idx))

        def _shard_index_for_slot(slot: int) -> int:
            for lo, hi, shard_idx in slot_ranges:
                if lo <= slot <= hi:
                    return shard_idx
            raise AssertionError(f"slot {slot} not covered by any shard range")

        # Per-node placement check: ask every PRIMARY directly
        # which indexes it has, rather than inferring placement from search
        # results (which is exactly the ambiguity this check exists to
        # resolve -- "object missing on this node" vs "object present but the
        # query matched nothing"). Restricted to primaries, not every node in
        # node_addrs: a replica-bearing cluster (e.g. CI's 3-primary/3-replica
        # topology) correctly replicates the index from its primary to that
        # primary's own replica(s), same as any other write command -- so
        # checking all nodes would see 2 nodes per shard instead of 1 and is
        # not what this test is asking. CLUSTER SHARDS' nodes[].role is the
        # authoritative source for which address is the primary (nodes_addr
        # itself is role-agnostic).
        primary_addrs = [
            (node[b"ip"].decode(), int(node[b"port"]))
            for shard in shards_reply
            for raw_node in normalize_cluster_shards_entry(shard)[b"nodes"]
            for node in (normalize_cluster_shards_node(raw_node),)
            if node[b"role"] == b"master"
        ]
        assert len(primary_addrs) >= 2, (
            "test fixture bug, not an engine finding: CLUSTER SHARDS reported "
            f"fewer than 2 primaries ({primary_addrs})"
        )

        list_by_node = {}
        for host, port in primary_addrs:
            node_list = await glide_client.custom_command(
                ["FT._LIST"], route=ByAddressRoute(host, port)
            )
            list_by_node[(host, port)] = cast(List[bytes], node_list)
        nodes_with_index = [
            addr for addr, lst in list_by_node.items() if index.encode() in lst
        ]
        # Confirmed on 1.3.0-rc1 (owner: valkey-search engine, not GLIDE):
        # FT.CREATE is NOT broadcast cluster-wide -- it lands on exactly the
        # one primary the client happened to route the command to, matching
        # the 1.2.1 baseline. If this list ever contains more
        # than one primary, valkey-search has started broadcasting FT.CREATE
        # and this assertion (and the rest of the test) should be revisited
        # with a source citation.
        assert len(nodes_with_index) == 1, (
            f"expected FT.CREATE to land on exactly one primary; got "
            f"{nodes_with_index} (list_by_node={list_by_node})"
        )
        index_owning_shard = next(
            shard_idx
            for shard_idx, shard in enumerate(shards_reply)
            for raw_node in normalize_cluster_shards_entry(shard)[b"nodes"]
            for node in (normalize_cluster_shards_node(raw_node),)
            if (node[b"ip"].decode(), int(node[b"port"])) == nodes_with_index[0]
            and node[b"role"] == b"master"
        )

        # affonsov review (#7335): deterministic slot coverage instead of
        # relying on MULTI_SHARD_DOC_COUNT random-suffix keys happening to
        # land on the index-owning shard by chance (~0.8% failure rate on a
        # 3-primary cluster with the fixed suffix set this test used to
        # generate unconditionally). Walk an unbounded suffix counter and
        # classify each candidate key by its real CLUSTER KEYSLOT outcome.
        # Capped at exactly 1 owning-shard key (just enough for
        # _poll_until_indexed/the "partial" assertions below to have
        # something to observe) and MULTI_SHARD_DOC_COUNT - 1 elsewhere keys
        # (the cross-shard spread this test is actually about), so the two
        # lists concatenate to exactly MULTI_SHARD_DOC_COUNT keys with no
        # truncation risk either way. No hashtag is used on any key:
        # letting the cluster's own hashing decide placement is still
        # exactly what happens here, just checked and
        # topped up as needed instead of assumed.
        on_owning_shard: List[str] = []
        elsewhere: List[str] = []
        suffix = 0
        while (
            len(on_owning_shard) < 1 or len(elsewhere) < MULTI_SHARD_DOC_COUNT - 1
        ) and suffix < MULTI_SHARD_DOC_COUNT * 50:
            candidate = f"{prefix}doc{suffix}"
            slot = cast(
                int,
                await glide_client.custom_command(["CLUSTER", "KEYSLOT", candidate]),
            )
            if (
                _shard_index_for_slot(slot) == index_owning_shard
                and not on_owning_shard
            ):
                on_owning_shard.append(candidate)
            elif len(elsewhere) < MULTI_SHARD_DOC_COUNT - 1:
                elsewhere.append(candidate)
            suffix += 1
        assert on_owning_shard, (
            "test fixture bug, not an engine finding: scanned "
            f"{suffix} candidate keys without finding one that hashes onto "
            f"the index-owning shard ({index_owning_shard}) -- re-check "
            "CLUSTER SHARDS or raise the scan bound above"
        )
        assert elsewhere, (
            "test fixture bug, not an engine finding: scanned "
            f"{suffix} candidate keys without finding one that hashes onto "
            f"a DIFFERENT shard than the index-owning one "
            f"({index_owning_shard}) -- re-check CLUSTER SHARDS or raise "
            "the scan bound above"
        )
        key_names = (on_owning_shard + elsewhere)[:MULTI_SHARD_DOC_COUNT]
        for i, key in enumerate(key_names):
            await glide_client.hset(key, {"title": f"doc number {i}"})

        await self._poll_until_indexed(glide_client, index, 1)

        # Observed (not forced) cross-shard behaviour on 1.3.0-rc1, owner:
        # valkey-search engine. FT.SEARCH only sees documents that are both
        # (a) present on the index-owning shard's keyspace and (b) indexed by
        # that shard's local index -- it does not fan out to other shards'
        # copies of the same key range. ALLSHARDS is accepted as a flag but
        # does not change this (confirmed via raw CLI before writing this
        # test).
        search_result = await ft.search(
            glide_client, index, "*", options=FtSearchOptions(nocontent=True)
        )
        observed_count = cast(int, search_result[0])
        result_map = cast(Mapping[TEncodable, Mapping], search_result[1])
        observed_keys = set(result_map.keys())
        # Every key FT.SEARCH did find must be a real seeded key (sanity: the
        # engine isn't inventing results), and the count must be strictly
        # less than the full 12 -- the whole point of this test is that
        # cross-shard aggregation does NOT happen on 1.3.0-rc1. If this ever
        # becomes 12, that is upstream progress and this test should be
        # updated to assert the fixed behaviour with a source citation,
        # mirroring the compat-gated tests elsewhere in this file.
        assert observed_keys.issubset({k.encode() for k in key_names})
        assert 0 < observed_count < len(key_names), (
            f"expected a partial (single-shard) result on 1.3.0-rc1, got "
            f"{observed_count}/{len(key_names)} -- re-check whether "
            "valkey-search now broadcasts FT.CREATE or aggregates "
            "ALLSHARDS across shards; if so, this is upstream progress, not "
            "a regression, and this assertion should be updated with a "
            "source citation"
        )

        aggregate_result = await ft.aggregate(
            glide_client,
            index,
            query="*",
            options=FtAggregateOptions(
                clauses=[
                    FtAggregateGroupBy([], [FtAggregateReducer("COUNT", [], "total")])
                ]
            ),
        )
        aggregate_total = int(aggregate_result[0][b"total"])
        # Same observed engine limitation applies to FT.AGGREGATE: it counts
        # only the owning shard's documents, matching FT.SEARCH's count
        # exactly (both are scoped to the single node that owns the index).
        assert aggregate_total == observed_count

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_cursor_affinity_prefer_replica_client(
        self, request, glide_client: GlideClusterClient, protocol: ProtocolVersion
    ):
        """FT.AGGREGATE/FT.HYBRID
        WITHCURSOR and every FT.CURSOR now route to the PRIMARY of the
        index-name slot regardless of ReadFrom (glide-core/redis-rs/redis/
        src/cluster_routing.rs). This is the client-visible consequence of
        that routing choice: paged queries always read from the primary, so
        a PREFER_REPLICA client's normal replica-spreading behaviour for
        other reads does not apply to cursor paging, and a failover loses
        any cursors open on the old primary (the server already discards a
        replica's cursors on its own full sync, per the design note cited
        in the routing change -- this is the same category of event, not a
        new failure mode this change introduces).

        Without this routing pin, a cursor opened on one node could have
        its FT.CURSOR READ follow-up land on a different replica in the
        same shard that never served the original query, and get "Cursor
        not found" partway through paging -- confirmed as a real, not
        theoretical, failure during the Hazard 2 investigation and fixed
        here.
        """
        prefix = "{ft-cursor-affinity-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("n", sortable=True)],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for i in range(1, 21):
            await glide_client.hset(prefix + f"d{i}", {"n": str(i)})
        await self._poll_until_indexed(glide_client, index, 20)

        prefer_replica_client = cast(
            GlideClusterClient,
            await create_client(
                request,
                cluster_mode=True,
                protocol=protocol,
                read_from=ReadFrom.PREFER_REPLICA,
            ),
        )
        try:
            # Page an FT.AGGREGATE WITHCURSOR to exhaustion. If cursor reads
            # were spread across replicas the way an ordinary readonly
            # command's PREFER_REPLICA would, a later page could land on a
            # node that never served the opening query and fail with
            # "Cursor not found" -- the whole point of this test is that it
            # doesn't, because every page is pinned to the same primary.
            total_rows = 0
            page = cast(
                List,
                await prefer_replica_client.custom_command(
                    [
                        "FT.AGGREGATE",
                        index,
                        "*",
                        "LOAD",
                        "1",
                        "n",
                        "WITHCURSOR",
                        "COUNT",
                        "5",
                    ]
                ),
            )
            cursor_id = page[1]
            total_rows += len(page[0])
            # Track the actual n values seen across all pages, not
            # just a row count -- a bug that duplicated or dropped specific
            # rows while still returning 20 total rows (e.g. a paging
            # off-by-one landing on the same page twice) would pass a
            # count-only check but fail this one.
            seen_n_values = {int(row[b"n"]) for row in page[0]}
            pages_read = 1
            while cursor_id != 0:
                page = cast(
                    List,
                    await prefer_replica_client.custom_command(
                        ["FT.CURSOR", "READ", index, str(cursor_id)]
                    ),
                )
                total_rows += len(page[0])
                seen_n_values.update(int(row[b"n"]) for row in page[0])
                cursor_id = page[1]
                pages_read += 1
                assert pages_read <= 10, "paging did not terminate as expected"
            assert total_rows == 20
            assert seen_n_values == set(range(1, 21)), (
                "paging returned the right row COUNT but the wrong SET of "
                f"rows: {sorted(seen_n_values)}"
            )

            # FT.CURSOR DEL on an open cursor.
            page = cast(
                List,
                await prefer_replica_client.custom_command(
                    [
                        "FT.AGGREGATE",
                        index,
                        "*",
                        "LOAD",
                        "1",
                        "n",
                        "WITHCURSOR",
                        "COUNT",
                        "5",
                    ]
                ),
            )
            open_cursor_id = page[1]
            assert (
                await prefer_replica_client.custom_command(
                    ["FT.CURSOR", "DEL", index, str(open_cursor_id)]
                )
                == OK
            )

            # Reading an unknown cursor id errors.
            with pytest.raises(RequestError, match="Cursor"):
                await prefer_replica_client.custom_command(
                    ["FT.CURSOR", "READ", index, "999999999"]
                )

            # Reading the just-deleted cursor also errors.
            with pytest.raises(RequestError, match="Cursor"):
                await prefer_replica_client.custom_command(
                    ["FT.CURSOR", "READ", index, str(open_cursor_id)]
                )

            # Negative control, mirroring the maintainers' own
            # TestCursorCluster.test_cursor_is_node_local
            # (integration/test_cursor.py:305): a read explicitly routed to
            # a REPLICA of the index's OWN slot fails, confirming the
            # cursor really is node-local and the earlier default-routed
            # reads in this test weren't succeeding merely because every
            # node happens to be able to answer it. Must be the replica of
            # the SAME shard that owns the index -- on a multi-shard
            # cluster, a replica of a DIFFERENT shard doesn't have the
            # index at all and fails with "Index not found" before the
            # cursor lookup is ever reached, which is a different error
            # than the one this control is checking for.
            shards_reply = cast(
                List, await prefer_replica_client.custom_command(["CLUSTER", "SHARDS"])
            )
            index_slot = cast(
                int,
                await prefer_replica_client.custom_command(
                    ["CLUSTER", "KEYSLOT", index]
                ),
            )
            replica_addrs = []
            for shard in shards_reply:
                normalized_shard = normalize_cluster_shards_entry(shard)
                slots = normalized_shard[b"slots"]
                owns_index_slot = any(
                    lo <= index_slot <= hi for lo, hi in zip(slots[0::2], slots[1::2])
                )
                if not owns_index_slot:
                    continue
                for raw_node in normalized_shard[b"nodes"]:
                    node = normalize_cluster_shards_node(raw_node)
                    if node[b"role"] == b"replica":
                        replica_addrs.append((node[b"ip"].decode(), int(node[b"port"])))
            assert len(replica_addrs) >= 1, (
                "test fixture bug, not an engine finding: CLUSTER SHARDS "
                f"reported no replica for the index's own shard (slot "
                f"{index_slot}, shards: {shards_reply})"
            )
            page = cast(
                List,
                await prefer_replica_client.custom_command(
                    [
                        "FT.AGGREGATE",
                        index,
                        "*",
                        "LOAD",
                        "1",
                        "n",
                        "WITHCURSOR",
                        "COUNT",
                        "5",
                    ]
                ),
            )
            another_cursor_id = page[1]
            for host, port in replica_addrs:
                with pytest.raises(RequestError, match="Cursor"):
                    await prefer_replica_client.custom_command(
                        ["FT.CURSOR", "READ", index, str(another_cursor_id)],
                        route=ByAddressRoute(host, port),
                    )
        finally:
            await prefer_replica_client.close()

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_typed_cursor_paged_to_exhaustion(
        self, glide_client: TGlideClient
    ):
        """The typed cursor API (FtAggregateOptions(with_cursor=...),
        ft.cursor_read): pages an FT.AGGREGATE WITHCURSOR to exhaustion
        using the typed functions rather than raw custom_command, in both
        standalone and cluster mode (single-shard, no replica -- the
        PREFER_REPLICA affinity scenario is covered separately against a
        real replica-bearing cluster by
        test_ft_cursor_affinity_prefer_replica_client, which already uses
        raw custom_command to exercise FT.CURSOR directly; this test
        exercises the typed wrapper's own argument serialization and
        reply unwrapping instead)."""
        prefix = "{ft-typed-cursor-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("n", sortable=True)],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for i in range(1, 21):
            await glide_client.hset(prefix + f"d{i}", {"n": str(i)})
        await self._poll_until_indexed(glide_client, index, 20)

        total_rows = 0
        page = cast(
            List,
            await ft.aggregate(
                glide_client,
                index,
                "*",
                options=FtAggregateOptions(
                    loadFields=["n"], with_cursor=FtWithCursor(count=5)
                ),
            ),
        )
        cursor_id = page[1]
        total_rows += len(page[0])
        # Track the actual n values seen across all pages, not
        # just a row count -- mirrors the same guard already applied to
        # this test's raw sibling, test_ft_cursor_affinity_prefer_replica_
        # client, for the identical reason (a paging bug that duplicates
        # or drops specific rows while still returning 20 total rows would
        # otherwise pass undetected).
        seen_n_values = {int(row[b"n"]) for row in page[0]}
        pages_read = 1
        while cursor_id != 0:
            page = cast(List, await ft.cursor_read(glide_client, index, cursor_id))
            total_rows += len(page[0])
            seen_n_values.update(int(row[b"n"]) for row in page[0])
            cursor_id = page[1]
            pages_read += 1
            assert pages_read <= 10, "paging did not terminate as expected"
        assert total_rows == 20
        assert seen_n_values == set(range(1, 21)), (
            "paging returned the right row COUNT but the wrong SET of "
            f"rows: {sorted(seen_n_values)}"
        )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_typed_cursor_del_and_errors(
        self, glide_client: TGlideClient
    ):
        """The typed cursor API's error paths: ft.cursor_del on an open
        cursor, then reading the just-deleted cursor errors, and reading
        an unknown cursor id errors -- mirroring the raw-custom_command
        version of these checks in test_ft_cursor_affinity_prefer_replica_
        client, but through the typed functions."""
        prefix = "{ft-typed-cursor-del-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[NumericField("n", sortable=True)],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for i in range(1, 6):
            await glide_client.hset(prefix + f"d{i}", {"n": str(i)})
        await self._poll_until_indexed(glide_client, index, 5)

        page = cast(
            List,
            await ft.aggregate(
                glide_client,
                index,
                "*",
                options=FtAggregateOptions(
                    loadFields=["n"], with_cursor=FtWithCursor(count=1)
                ),
            ),
        )
        open_cursor_id = page[1]
        assert open_cursor_id != 0

        assert await ft.cursor_del(glide_client, index, open_cursor_id) == OK

        with pytest.raises(RequestError, match="Cursor"):
            await ft.cursor_read(glide_client, index, open_cursor_id)

        with pytest.raises(RequestError, match="Cursor"):
            await ft.cursor_read(glide_client, index, 999999999)

        assert await ft.dropindex(glide_client, index) == OK

    async def _create_hybrid_index(
        self, glide_client: TGlideClient, prefix: str
    ) -> TEncodable:
        """Helper that creates an index with a TEXT field (``title``) and a
        2-dimensional FLAT FLOAT32 VECTOR field (``vec``), seeds the shared
        HYBRID_DOCS fixture (moved into ft_test_helpers.py so
        the async and sync suites assert against the identical corpus), and
        waits for both documents to be indexed. Returns the index name."""
        index = prefix + "index"
        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TextField("title"),
                    VectorField(
                        name="vec",
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=VectorType.FLOAT32,
                        ),
                    ),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for doc_name, fields in HYBRID_DOCS.items():
            vec_x, vec_y = cast(Tuple[float, float], fields["vec"])
            await glide_client.hset(
                prefix + doc_name,
                {
                    "title": cast(str, fields["title"]),
                    "vec": struct.pack("2f", vec_x, vec_y),
                },
            )
        await self._poll_until_indexed(glide_client, index, len(HYBRID_DOCS))
        return index

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_basic_query(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """A basic FT.HYBRID query with no COMBINE/LOAD/options --
        confirmed live, the default reply is just the document key and the
        fused score."""
        prefix = "{ft-hybrid-basic-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm("hello"),
                FtHybridVsimArm("vec", struct.pack("2f", 1.0, 1.0), k=5),
            ),
        )
        assert len(result) == 2
        keys = {row[b"__key"] for row in result}
        assert keys == {
            (prefix + HYBRID_DOC_TEXT_WINNER).encode(),
            (prefix + HYBRID_DOC_VECTOR_WINNER).encode(),
        }
        for row in result:
            assert b"__score" in row

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_aggregate_clause_filters_post_fusion(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """Regression test: with no combine/local_only/load_*/timeout set,
        a leading aggregate-tail clause used to be emitted right after the
        VSIM arm's own tokens, where the server's VSIM parser claims it as
        its own uncounted VSIM pre-filter (which uses FT.SEARCH-style
        query syntax) instead of an aggregate-pipeline FILTER (which uses
        the FT.AGGREGATE expression grammar) -- confirmed live, the
        misparse surfaces as "Invalid: filter expression:
        `(@ss > 0)`. Unexpected character at position 6: `>`, expecting
        `:`" when the ordering bug is present. The SEARCH arm here yields
        its own score under the alias "ss" (reachable with no LOAD
        clause, confirmed live), and only matches
        HYBRID_DOC_VECTOR_WINNER's query ("goodbye"); the VSIM arm (k=5)
        matches both documents, so the UNION going into the aggregate
        tail is both documents. The FtAggregateFilter keeps only rows
        where "ss" is a positive score -- correct (post-fusion) behaviour
        keeps only HYBRID_DOC_VECTOR_WINNER (the only row the SEARCH arm
        actually scored); HYBRID_DOC_TEXT_WINNER (reached only via the
        VSIM arm, which never set "ss") is dropped."""
        prefix = "{ft-hybrid-postfusion-filter-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm("goodbye", yield_score_as="ss"),
                FtHybridVsimArm("vec", struct.pack("2f", 1.0, 1.0), k=5),
                options=FtHybridOptions(clauses=[FtAggregateFilter("@ss > 0")]),
            ),
        )
        assert len(result) == 1
        assert result[0][b"__key"] == (prefix + HYBRID_DOC_VECTOR_WINNER).encode()

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "combine, expected_first",
        [
            (FtHybridCombine.Linear(alpha=1.0, beta=0.0), HYBRID_DOC_TEXT_WINNER),
            (FtHybridCombine.Linear(alpha=0.0, beta=1.0), HYBRID_DOC_VECTOR_WINNER),
            (
                FtHybridCombine.Function("@" + HYBRID_SEARCH_ALIAS),
                HYBRID_DOC_TEXT_WINNER,
            ),
            (
                FtHybridCombine.Function("@" + HYBRID_VSIM_ALIAS),
                HYBRID_DOC_VECTOR_WINNER,
            ),
        ],
        ids=["LINEAR-text-only", "LINEAR-vector-only", "FUNCTION-text", "FUNCTION-vec"],
    )
    async def test_ft_hybrid_combine_methods_fuse_results(
        self,
        glide_client: TGlideClient,
        protocol: ProtocolVersion,
        combine: FtHybridCombine,
        expected_first: str,
    ):
        """Different COMBINE weightings genuinely change the fused
        ranking, not just the fused score's numeric value --
        the HYBRID_DOCS fixture (one doc the SEARCH arm strongly favours,
        one the VSIM arm strongly favours) lets LINEAR's ALPHA/BETA and
        FUNCTION's expr each be driven to a method-specific winner and
        checked by key, not just by shape. The SEARCH arm's own
        YIELD_SCORE_AS alias is only ever present on the row the SEARCH
        arm's text query actually matched (confirmed live: FT.HYBRID's
        UNION semantics still surface the other document via the VSIM arm
        alone, with no `ss` column at all, since the SEARCH arm never
        scored it) -- so this checks `ss` specifically on
        HYBRID_DOC_TEXT_WINNER's row, not on every row."""
        prefix = "{ft-hybrid-combine-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm(
                    HYBRID_SEARCH_QUERY, yield_score_as=HYBRID_SEARCH_ALIAS
                ),
                FtHybridVsimArm(
                    "vec",
                    struct.pack("2f", *HYBRID_QUERY_VECTOR),
                    k=5,
                    yield_score_as=HYBRID_VSIM_ALIAS,
                ),
                options=FtHybridOptions(combine=combine),
            ),
        )
        assert len(result) == 2
        rows_by_key = {row[b"__key"]: row for row in result}
        assert (prefix + HYBRID_DOC_TEXT_WINNER).encode() in rows_by_key
        assert (prefix + HYBRID_DOC_VECTOR_WINNER).encode() in rows_by_key
        for row in result:
            assert b"__score" in row
            # The VSIM arm's own alias is reachable from every row here --
            # k=5 covers the whole corpus, so both documents are always in
            # its candidate set regardless of which COMBINE method fused
            # the scores.
            assert HYBRID_VSIM_ALIAS.encode() in row
        # The method-specific winner, by key -- not just a shape check.
        assert result[0][b"__key"] == (prefix + expected_first).encode()
        # The SEARCH arm's alias is present only on the row it actually
        # matched -- confirmed live, see the docstring above.
        assert (
            HYBRID_SEARCH_ALIAS.encode()
            in rows_by_key[(prefix + HYBRID_DOC_TEXT_WINNER).encode()]
        )
        assert (
            HYBRID_SEARCH_ALIAS.encode()
            not in rows_by_key[(prefix + HYBRID_DOC_VECTOR_WINNER).encode()]
        )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_vector_inline_bytes_and_param_reference(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """The query vector can be given as raw bytes (the client
        auto-generates an internal PARAMS entry) or as an explicit $name
        string (the caller manages their own params entry) -- confirmed
        live both forms reach the server identically since FT.HYBRID's
        VSIM clause has no wire-level inline vector literal at all.

        The original version of this test used k >= the corpus
        size, so a mis-encoded vector (e.g. a byte-order or field-order
        swap) would still return the same keys and pass undetected --
        FT.HYBRID's UNION semantics mean every document surfaces via the
        SEARCH arm regardless of what VSIM does. This version uses k=1
        instead and checks the VSIM arm's own `vs` YIELD_SCORE_AS alias,
        which (confirmed live) is present only on the single document
        VSIM's KNN search actually selected -- so a mis-encoded vector
        that picked the wrong nearest neighbour, or decoded to a NaN/zero
        vector that effectively randomised the KNN order, would surface
        the alias on the wrong key (or no key at all) and fail here.
        """
        prefix = "{ft-hybrid-vecparam-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)
        vec = struct.pack("2f", *HYBRID_QUERY_VECTOR)

        inline_result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm(HYBRID_SEARCH_QUERY),
                FtHybridVsimArm("vec", vec, k=1, yield_score_as=HYBRID_VSIM_ALIAS),
            ),
        )
        param_result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm(HYBRID_SEARCH_QUERY),
                FtHybridVsimArm("vec", "$myvec", k=1, yield_score_as=HYBRID_VSIM_ALIAS),
                options=FtHybridOptions(params={"myvec": vec}),
            ),
        )
        assert len(inline_result) == len(param_result) == 2
        inline_keys = {row[b"__key"] for row in inline_result}
        param_keys = {row[b"__key"] for row in param_result}
        assert inline_keys == param_keys

        for result in (inline_result, param_result):
            rows_with_vs = [row for row in result if HYBRID_VSIM_ALIAS.encode() in row]
            assert len(rows_with_vs) == 1, (
                "k=1 should select exactly one document as the VSIM arm's "
                f"own KNN match: {result}"
            )
            assert (
                rows_with_vs[0][b"__key"]
                == (prefix + HYBRID_DOC_VECTOR_WINNER).encode()
            )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "policy", [HybridFilterPolicy.ADHOC, HybridFilterPolicy.BATCHES]
    )
    async def test_ft_hybrid_vsim_filter_with_policy(
        self,
        glide_client: TGlideClient,
        protocol: ProtocolVersion,
        policy: HybridFilterPolicy,
    ):
        """A VSIM pre-filter with an explicit POLICY (forcing the
        counted wire form) narrows the VSIM arm's own candidate set.
        Confirmed live: FT.HYBRID fuses the UNION of both arms' matches, so
        the SEARCH arm is given a query that matches nothing here --
        otherwise the SEARCH arm's own (unfiltered) match for the same
        document would still surface it regardless of the VSIM filter,
        which is not what this test is checking."""
        prefix = "{ft-hybrid-filter-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm("nonexistentterm12345"),
                FtHybridVsimArm(
                    "vec",
                    struct.pack("2f", 1.0, 1.0),
                    k=5,
                    filter=FtHybridVsimFilter("@title:hello", policy=policy),
                ),
            ),
        )
        assert len(result) == 1
        assert result[0][b"__key"] == (prefix + HYBRID_DOC_TEXT_WINNER).encode()

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_per_arm_and_combine_yield_score_as_columns(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """Both arms' YIELD_SCORE_AS aliases and COMBINE's own
        YIELD_SCORE_AS all appear as distinctly-named columns in the same
        reply. The SEARCH arm's own alias is only ever present
        on the row the SEARCH arm actually matched (same reasoning as
        test_ft_hybrid_combine_methods_fuse_results's docstring), so this
        checks it specifically on HYBRID_DOC_TEXT_WINNER's row rather than
        asserting it unconditionally on every row."""
        prefix = "{ft-hybrid-yieldscore-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm(
                    HYBRID_SEARCH_QUERY, yield_score_as=HYBRID_SEARCH_ALIAS
                ),
                FtHybridVsimArm(
                    "vec",
                    struct.pack("2f", *HYBRID_QUERY_VECTOR),
                    k=5,
                    yield_score_as=HYBRID_VSIM_ALIAS,
                ),
                options=FtHybridOptions(
                    combine=FtHybridCombine.RRF(yield_score_as="fused")
                ),
            ),
        )
        assert len(result) == 2
        rows_by_key = {row[b"__key"]: row for row in result}
        for row in result:
            assert HYBRID_VSIM_ALIAS.encode() in row
            assert b"fused" in row
            assert b"__score" not in row  # renamed via YIELD_SCORE_AS
        assert (
            HYBRID_SEARCH_ALIAS.encode()
            in rows_by_key[(prefix + HYBRID_DOC_TEXT_WINNER).encode()]
        )
        assert (
            HYBRID_SEARCH_ALIAS.encode()
            not in rows_by_key[(prefix + HYBRID_DOC_VECTOR_WINNER).encode()]
        )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_with_cursor_prefer_replica_client_paged_to_exhaustion(
        self, request, glide_client: GlideClusterClient, protocol: ProtocolVersion
    ):
        """FT.HYBRID WITHCURSOR, paged to exhaustion via a
        PREFER_REPLICA client, mirroring the same cursor-affinity scenario
        already covered for FT.AGGREGATE -- confirmed live, FT.HYBRID's
        own cursor is pinned to the primary the same way, with zero
        "Cursor not found" errors mid-paging."""
        prefix = "{ft-hybrid-cursor-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        prefer_replica_client = cast(
            GlideClusterClient,
            await create_client(
                request,
                cluster_mode=True,
                protocol=protocol,
                read_from=ReadFrom.PREFER_REPLICA,
            ),
        )
        try:
            total_rows = 0
            page = cast(
                List,
                await ft.hybrid(
                    prefer_replica_client,
                    index,
                    FtHybridSearchArm("*"),
                    FtHybridVsimArm("vec", struct.pack("2f", 1.0, 1.0), k=5),
                    options=FtHybridOptions(with_cursor=FtWithCursor(count=1)),
                ),
            )
            cursor_id = page[1]
            total_rows += len(page[0])
            # affonsov review (#7335): track the actual __key values seen
            # across all pages, not just a row count -- mirrors the same
            # guard already applied to this test's FT.AGGREGATE sibling,
            # test_ft_aggregate_typed_cursor_..., for the identical reason
            # (a paging bug that duplicates or drops a specific row while
            # still returning the right total row count would otherwise
            # pass undetected). __key is present on every row without an
            # explicit loadFields, confirmed by the other FT.HYBRID tests
            # in this file that read row[b"__key"] the same way.
            seen_keys = {row[b"__key"] for row in page[0]}
            pages_read = 1
            while cursor_id != 0:
                # Confirmed live: an FT.HYBRID-opened cursor reads
                # identically through the FT.AGGREGATE typed cursor API --
                # the server does not distinguish which command created it.
                next_page = cast(
                    List,
                    await ft.cursor_read(prefer_replica_client, index, cursor_id),
                )
                total_rows += len(next_page[0])
                seen_keys.update(row[b"__key"] for row in next_page[0])
                cursor_id = next_page[1]
                pages_read += 1
                assert pages_read <= 10, "paging did not terminate as expected"
            assert total_rows == 2
            assert seen_keys == {
                (prefix + HYBRID_DOC_TEXT_WINNER).encode(),
                (prefix + HYBRID_DOC_VECTOR_WINNER).encode(),
            }, (
                "paging returned the right row COUNT but the wrong SET of "
                f"keys: {seen_keys}"
            )
        finally:
            await prefer_replica_client.close()

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_localonly_accepted(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """LOCALONLY (a valkey-search extension not present in
        RediSearch) is accepted and does
        not change the result shape on a single-shard/standalone test
        cluster."""
        prefix = "{ft-hybrid-localonly-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm("hello"),
                FtHybridVsimArm("vec", struct.pack("2f", 1.0, 1.0), k=5),
                options=FtHybridOptions(local_only=True),
            ),
        )
        assert len(result) == 2

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search_scorer_tfidf_rejected(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """No test exercised an unregistered SCORER value.
        BM25STD is the only scorer valkey-search 1.3.0-rc1 registers
        (confirmed live, see test_ft_search_with_scores_and_scorer); TFIDF
        is the classic RediSearch default elsewhere, which makes it a
        realistic value for a caller to try here and have it silently
        accepted (or silently fall back) rather than rejected -- confirmed
        live the server does reject it outright."""
        prefix = "{ft-scorer-tfidf-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[TextField("title")],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        await glide_client.hset(prefix + "1", {"title": "hello world"})
        await self._poll_until_indexed(glide_client, index, 1)

        with pytest.raises(RequestError, match="SCORER"):
            await ft.search(
                glide_client,
                index,
                "hello",
                options=FtSearchOptions(with_scores=True, scorer="TFIDF"),
            )

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_without_k_uses_engine_default(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """No test exercised a VSIM arm with no `k` at all.
        Confirmed live: valkey-search 1.3.0-rc1's own engine default is
        K=10 when the caller omits it (not "no limit", and not an error),
        so this seeds a corpus larger than 10 and confirms the VSIM arm's
        contribution is capped there -- the SEARCH arm's own query is
        deliberately given a term that matches nothing, so only the VSIM
        arm (via FT.HYBRID's UNION) can be contributing any rows at all,
        making the result count a direct measurement of the VSIM arm's own
        candidate set size."""
        prefix = "{ft-hybrid-default-k-" + str(uuid.uuid4()) + "}:"
        index = prefix + "index"
        doc_count = 15

        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    TextField("title"),
                    VectorField(
                        name="vec",
                        algorithm=VectorAlgorithm.FLAT,
                        attributes=VectorFieldAttributesFlat(
                            dimensions=2,
                            distance_metric=DistanceMetricType.L2,
                            type=VectorType.FLOAT32,
                        ),
                    ),
                ],
                options=FtCreateOptions(data_type=DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )
        for i in range(1, doc_count + 1):
            await glide_client.hset(
                prefix + f"d{i}",
                {"title": "goodbye", "vec": struct.pack("2f", float(i), float(i))},
            )
        await self._poll_until_indexed(glide_client, index, doc_count)

        result = cast(
            List,
            await ft.hybrid(
                glide_client,
                index,
                FtHybridSearchArm("nonexistentterm12345"),
                FtHybridVsimArm("vec", struct.pack("2f", 1.0, 1.0)),
            ),
        )
        assert len(result) == 10

        assert await ft.dropindex(glide_client, index) == OK

    @pytest.mark.requires_search_1_3
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_hybrid_inside_multi_exec_rejected(
        self, glide_client: TGlideClient, protocol: ProtocolVersion
    ):
        """No test exercised FT.HYBRID inside a MULTI/EXEC
        transaction. Confirmed in source (valkey-search 1.3.0-rc1
        src/query/multi_search.h:54, MultiSearchParameters::
        ExecuteSyncLocal) and live: FT.HYBRID is rejected outright inside
        MULTI/EXEC (and inside a Lua script, or when the reader thread
        pool is disabled) because the per-arm results cannot be
        revalidated against concurrent mutations before fusion under
        forced synchronous execution. Goes through custom_command inside a
        Batch -- there is no typed transaction wrapper for FT.HYBRID, nor
        should there be one, since this path exists purely to confirm the
        rejection, not to exercise the typed API."""
        prefix = "{ft-hybrid-multiexec-" + str(uuid.uuid4()) + "}:"
        index = await self._create_hybrid_index(glide_client, prefix)

        batch = Batch(is_atomic=True)
        batch.custom_command(
            [
                "FT.HYBRID",
                index,
                "SEARCH",
                HYBRID_SEARCH_QUERY,
                "VSIM",
                "@vec",
                "$q",
                "PARAMS",
                "2",
                "q",
                struct.pack("2f", *HYBRID_QUERY_VECTOR),
            ]
        )
        with pytest.raises(RequestError, match="FT.HYBRID"):
            if isinstance(glide_client, GlideClusterClient):
                await glide_client.exec(cast(ClusterBatch, batch), raise_on_error=True)
            else:
                await cast(GlideClient, glide_client).exec(
                    cast(Batch, batch), raise_on_error=True
                )

        assert await ft.dropindex(glide_client, index) == OK
