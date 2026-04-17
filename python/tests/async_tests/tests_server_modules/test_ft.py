# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import array
import asyncio
import json
import time
import uuid
from typing import List, Mapping, Union, cast

import pytest
from glide import ft
from glide import glide_json as GlideJson
from glide.glide_client import GlideClusterClient
from glide_shared.commands.command_args import OrderBy
from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateApply,
    FtAggregateGroupBy,
    FtAggregateOptions,
    FtAggregateReducer,
    FtAggregateSortBy,
    FtAggregateSortProperty,
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
from glide_shared.commands.server_modules.ft_options.ft_profile_options import (
    FtProfileOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    ConsistencyMode,
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
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK, FtSearchResponse, TEncodable
from glide_shared.exceptions import RequestError


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

    @pytest.mark.skip(reason="FT.ALIASADD is currently unsupported")
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

    @pytest.mark.skip(reason="FT.ALIASDEL is currently unsupported")
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

    @pytest.mark.skip(reason="FT.ALIASUPDATE is currently unsupported")
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

    @pytest.mark.skip(reason="FT.EXPLAIN is currently unsupported")
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

    @pytest.mark.skip(reason="FT.EXPLAINCLI is currently unsupported")
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

    @pytest.mark.skip(reason="FT.AGGREGATE is currently not fully supported")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, protocol
    ):
        prefix_bicycles = "{bicycles}:"
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
        sorted_result = sorted(result, key=lambda x: (x[b"condition"], x[b"bicycles"]))
        expected_result = sorted(
            [
                {
                    b"condition": b"refurbished",
                    b"bicycles": b"1" if (protocol == ProtocolVersion.RESP2) else 1.0,
                },
                {
                    b"condition": b"new",
                    b"bicycles": b"5" if (protocol == ProtocolVersion.RESP2) else 5.0,
                },
                {
                    b"condition": b"used",
                    b"bicycles": b"4" if (protocol == ProtocolVersion.RESP2) else 4.0,
                },
            ],
            key=lambda x: (x[b"condition"], x[b"bicycles"]),
        )
        assert sorted_result == expected_result

        # Test FT.PROFILE for the above mentioned FT.AGGREGATE query
        ft_profile_result = await ft.profile(
            glide_client,
            index_bicycles,
            FtProfileOptions.from_query_options(
                query="*", query_options=ft_aggregate_options
            ),
        )
        assert len(ft_profile_result) > 0
        assert (
            sorted(
                ft_profile_result[0], key=lambda x: (x[b"condition"], x[b"bicycles"])
            )
            == expected_result
        )

        assert await ft.dropindex(glide_client, index_bicycles) == OK

    @pytest.mark.skip(reason="FT.AGGREGATE is currently not fully supported")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, protocol
    ):
        prefix_movies = "{movies}:"
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
        # 'r_rating', 'GROUPBY', '1', '@genre', 'REDUCE', 'COUNT', '0', 'AS', 'nb_of_movies', 'REDUCE', 'SUM', '1', 'votes',
        # 'AS', 'nb_of_votes', 'REDUCE', 'AVG', '1', 'r_rating', 'AS', 'avg_rating', 'SORTBY', '4', '@avg_rating', 'DESC',
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
                        FtAggregateReducer(b"SUM", [b"votes"], b"nb_of_votes"),
                        FtAggregateReducer(b"AVG", [b"r_rating"], b"avg_rating"),
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
        expected_result = sorted(
            [
                {
                    b"genre": b"Drama",
                    b"nb_of_movies": (
                        b"1" if (protocol == ProtocolVersion.RESP2) else 1.0
                    ),
                    b"nb_of_votes": (
                        b"1563839" if (protocol == ProtocolVersion.RESP2) else 1563839.0
                    ),
                    b"avg_rating": (
                        b"10" if (protocol == ProtocolVersion.RESP2) else 10.0
                    ),
                },
                {
                    b"genre": b"Action",
                    b"nb_of_movies": (
                        b"2" if (protocol == ProtocolVersion.RESP2) else 2.0
                    ),
                    b"nb_of_votes": (
                        b"2033895" if (protocol == ProtocolVersion.RESP2) else 2033895.0
                    ),
                    b"avg_rating": b"9" if (protocol == ProtocolVersion.RESP2) else 9.0,
                },
                {
                    b"genre": b"Thriller",
                    b"nb_of_movies": (
                        b"1" if (protocol == ProtocolVersion.RESP2) else 1.0
                    ),
                    b"nb_of_votes": (
                        b"559490" if (protocol == ProtocolVersion.RESP2) else 559490.0
                    ),
                    b"avg_rating": b"9" if (protocol == ProtocolVersion.RESP2) else 9.0,
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

        # Test FT.PROFILE for the above mentioned FT.AGGREGATE query
        ft_profile_result = await ft.profile(
            glide_client,
            index_movies,
            FtProfileOptions.from_query_options(
                query="*", query_options=ft_aggregate_options
            ),
        )
        assert len(ft_profile_result) > 0
        assert (
            sorted(
                ft_profile_result[0],
                key=lambda x: (
                    x[b"genre"],
                    x[b"nb_of_movies"],
                    x[b"nb_of_votes"],
                    x[b"avg_rating"],
                ),
            )
            == expected_result
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

    @pytest.mark.skip(reason="FT.ALIASLIST is currently unsupported")
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
        await asyncio.sleep(self.sleep_wait_time)

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
        await asyncio.sleep(self.sleep_wait_time)

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

        # PRIMARY scope - works with coordinator, otherwise rejected
        try:
            primary_info = await ft.info(glide_client, index, scope=InfoScope.PRIMARY)
            assert b"PRIMARY" in primary_info
        except Exception as e:
            assert "PRIMARY option is not valid" in str(e)

        # CLUSTER scope - works with coordinator, otherwise rejected
        try:
            cluster_info = await ft.info(glide_client, index, scope=InfoScope.CLUSTER)
            assert b"CLUSTER" in cluster_info
        except Exception as e:
            assert "CLUSTER option is not valid" in str(e)

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
