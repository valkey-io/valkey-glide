# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import array
import json
import time
import uuid
from typing import List, Mapping, Union, cast

import pytest

from glide.async_commands.command_args import OrderBy
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules import glide_json as GlideJson
from glide.async_commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateApply,
    FtAggregateGroupBy,
    FtAggregateOptions,
    FtAggregateReducer,
    FtAggregateSortBy,
    FtAggregateSortProperty,
)
from glide.async_commands.server_modules.ft_options.ft_create_options import (
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
from glide.async_commands.server_modules.ft_options.ft_profile_options import (
    FtProfileOptions,
)
from glide.async_commands.server_modules.ft_options.ft_search_options import (
    FtSearchOptions,
    ReturnField,
)
from glide.config import ProtocolVersion
from glide.constants import OK, FtSearchResponse, TEncodable
from glide.exceptions import RequestError
from glide.glide_client import GlideClusterClient


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
            TextField("$title"),
            NumericField("$published_at"),
            TextField("$category"),
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
            TextField(b"$title"),
            NumericField(b"$published_at"),
            TextField(b"$category"),
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
        fields: List[Field] = [TextField("$title")]
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
                options=FtCreateOptions(DataType.JSON),
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
                ReturnField(field_identifier="a", alias="a_new"),
                ReturnField(field_identifier="b", alias="b_new"),
            ]
        )

        # Search the index for string inputs.
        result1 = await ft.search(
            client=glide_client,
            index_name=json_index,
            query="*",
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

        # Test FT.PROFILE for the above mentioned FT.SEARCH query and search options.

        ft_profile_result = await ft.profile(
            glide_client,
            json_index,
            FtProfileOptions.from_query_options(
                query="*", query_options=ft_search_options
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
        ft_search_options_bytes_input = FtSearchOptions(
            return_fields=[
                ReturnField(field_identifier=b"a", alias=b"a_new"),
                ReturnField(field_identifier=b"b", alias=b"b_new"),
            ]
        )

        # Search the index for byte type inputs.
        result2 = await ft.search(
            glide_client,
            json_index.encode("utf-8"),
            b"*",
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

        # Test FT.PROFILE for the above mentioned FT.SEARCH query and search options for byte type inputs.
        ft_profile_result = await ft.profile(
            glide_client,
            json_index.encode("utf-8"),
            FtProfileOptions.from_query_options(
                query=b"*", query_options=ft_search_options_bytes_input
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

        # Create an index for knn vector search.

        vector_prefix = "vector-search:"
        vector_key1 = vector_prefix + str(uuid.uuid4())
        vector_key2 = vector_prefix + str(uuid.uuid4())
        vector1 = array.array("f", [0.0, 0.0])
        vector2 = array.array("f", [1.0, 1.0])
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
                    1  # <- cos score of 1 means identical vectors
                ).encode(),
            }
        }
        assert knn_result[1] == expected_result

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
        prefix = "{hash-search-" + str(uuid.uuid4()) + "}:"
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
        assert index_name.encode() == result.get(b"index_name")
        assert b"JSON" == result.get(b"key_type")
        assert [b"key-prefix"] == result.get(b"key_prefixes")

        # Get vector and text fields from the fields array.
        fields: TestFt.SerchResultFieldsList = cast(
            TestFt.SerchResultFieldsList, result.get(b"fields")
        )
        assert len(fields) == 2
        text_field: TestFt.SearchResultField = {}
        vector_field: TestFt.SearchResultField = {}
        if fields[0].get(b"type") == b"VECTOR":
            vector_field = cast(TestFt.SearchResultField, fields[0])
            text_field = cast(TestFt.SearchResultField, fields[1])
        else:
            vector_field = cast(TestFt.SearchResultField, fields[1])
            text_field = cast(TestFt.SearchResultField, fields[0])

        # Compare vector field arguments
        assert b"$.vec" == vector_field.get(b"identifier")
        assert b"VECTOR" == vector_field.get(b"type")
        assert b"VEC" == vector_field.get(b"field_name")
        vector_field_params: Mapping[TEncodable, Union[TEncodable, int]] = cast(
            Mapping[TEncodable, Union[TEncodable, int]],
            vector_field.get(b"vector_params"),
        )
        assert DistanceMetricType.L2.value.encode() == vector_field_params.get(
            b"distance_metric"
        )
        assert 2 == vector_field_params.get(b"dimension")
        assert b"HNSW" == vector_field_params.get(b"algorithm")
        assert b"FLOAT32" == vector_field_params.get(b"data_type")

        # Compare text field arguments.
        assert b"$.text-field" == text_field.get(b"identifier")
        assert b"TEXT" == text_field.get(b"type")
        assert b"text-field" == text_field.get(b"field_name")

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
            TextField("$.text-field", "text-field"),
        ]

        prefixes: List[TEncodable] = ["key-prefix"]

        await ft.create(
            glide_client,
            index_name,
            schema=fields,
            options=FtCreateOptions(DataType.JSON, prefixes=prefixes),
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
        prefix = "{hash-search-" + str(uuid.uuid4()) + "}:"
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
