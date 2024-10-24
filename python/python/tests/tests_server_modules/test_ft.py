# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import uuid
from typing import List, Mapping, Union, cast

import pytest
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    DistanceMetricType,
    Field,
    FtCreateOptions,
    TextField,
    VectorAlgorithm,
    VectorField,
    VectorFieldAttributesHnsw,
    VectorType,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TEncodable
from glide.exceptions import RequestError
from glide.glide_client import GlideClusterClient


@pytest.mark.asyncio
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

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasadd(self, glide_client: GlideClusterClient):
        indexName: str = str(uuid.uuid4())
        alias: str = "alias"
        # Test ft.aliasadd throws an error if index does not exist.
        with pytest.raises(RequestError):
            await ft.aliasadd(glide_client, alias, indexName)

        # Test ft.aliasadd successfully adds an alias to an existing index.
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.dropindex(glide_client, indexName=indexName) == OK

        # Test ft.aliasadd for input of bytes type.
        indexNameString = str(uuid.uuid4())
        indexNameBytes = bytes(indexNameString, "utf-8")
        aliasNameBytes = b"alias-bytes"
        await TestFt._create_test_index_hash_type(self, glide_client, indexNameString)
        assert await ft.aliasadd(glide_client, aliasNameBytes, indexNameBytes) == OK
        assert await ft.dropindex(glide_client, indexName=indexNameString) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasdel(self, glide_client: GlideClusterClient):
        indexName: TEncodable = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)

        # Test if deleting a non existent alias throws an error.
        with pytest.raises(RequestError):
            await ft.aliasdel(glide_client, alias)

        # Test if an existing alias is deleted successfully.
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.aliasdel(glide_client, alias) == OK

        # Test if an existing alias is deleted successfully for bytes type input.
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.aliasdel(glide_client, bytes(alias, "utf-8")) == OK

        assert await ft.dropindex(glide_client, indexName=indexName) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasupdate(self, glide_client: GlideClusterClient):
        indexName: str = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        newAliasName: str = "newAlias"
        newIndexName: str = str(uuid.uuid4())

        await TestFt._create_test_index_hash_type(self, glide_client, newIndexName)
        assert await ft.aliasadd(glide_client, newAliasName, newIndexName) == OK

        # Test if updating an already existing alias to point to an existing index returns "OK".
        assert await ft.aliasupdate(glide_client, newAliasName, indexName) == OK
        assert (
            await ft.aliasupdate(
                glide_client, bytes(alias, "utf-8"), bytes(newIndexName, "utf-8")
            )
            == OK
        )

        assert await ft.dropindex(glide_client, indexName=indexName) == OK
        assert await ft.dropindex(glide_client, indexName=newIndexName) == OK

    async def _create_test_index_hash_type(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating a basic index with hash data type with one text field.
        fields: List[Field] = []
        text_field_title: TextField = TextField("$title")
        fields.append(text_field_title)

        prefix = "{json-search-" + str(uuid.uuid4()) + "}:"
        prefixes: List[TEncodable] = []
        prefixes.append(prefix)

        result = await ft.create(
            glide_client, index_name, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_info(self, glide_client: GlideClusterClient):
        indexName = str(uuid.uuid4())
        await TestFt._create_test_index_with_vector_field(
            self, glide_client=glide_client, index_name=indexName
        )
        result = await ft.info(glide_client, indexName)
        assert await ft.dropindex(glide_client, indexName=indexName) == OK

        assert indexName.encode() == result.get(b"index_name")
        assert b"JSON" == result.get(b"key_type")
        assert [b"key-prefix"] == result.get(b"key_prefixes")

        # Get vector and text fields from the fields array.
        fields: TestFt.SerchResultFieldsList = cast(
            TestFt.SerchResultFieldsList, result.get(b"fields")
        )
        assert len(fields) == 2
        textField: TestFt.SearchResultField = {}
        vectorField: TestFt.SearchResultField = {}
        if fields[0].get(b"type") == b"VECTOR":
            vectorField = cast(TestFt.SearchResultField, fields[0])
            textField = cast(TestFt.SearchResultField, fields[1])
        else:
            vectorField = cast(TestFt.SearchResultField, fields[1])
            textField = cast(TestFt.SearchResultField, fields[0])

        # Compare vector field arguments
        assert b"$.vec" == vectorField.get(b"identifier")
        assert b"VECTOR" == vectorField.get(b"type")
        assert b"VEC" == vectorField.get(b"field_name")
        vectorFieldParams: Mapping[TEncodable, Union[TEncodable, int]] = cast(
            Mapping[TEncodable, Union[TEncodable, int]],
            vectorField.get(b"vector_params"),
        )
        assert DistanceMetricType.L2.value.encode() == vectorFieldParams.get(
            b"distance_metric"
        )
        assert 2 == vectorFieldParams.get(b"dimension")
        assert b"HNSW" == vectorFieldParams.get(b"algorithm")
        assert b"FLOAT32" == vectorFieldParams.get(b"data_type")

        # Compare text field arguments.
        assert b"$.text-field" == textField.get(b"identifier")
        assert b"TEXT" == textField.get(b"type")
        assert b"text-field" == textField.get(b"field_name")

        # Querying a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.info(glide_client, str(uuid.uuid4()))

    async def _create_test_index_with_vector_field(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating an index with JSON data type with a text and vector field.
        fields: List[Field] = []
        textField: Field = TextField("$.text-field", "text-field")

        vectorFieldHash: VectorField = VectorField(
            name="$.vec",
            algorithm=VectorAlgorithm.HNSW,
            attributes=VectorFieldAttributesHnsw(
                dimensions=2, distance_metric=DistanceMetricType.L2, type=VectorType.FLOAT32
            ),
            alias="VEC",
        )
        fields.append(vectorFieldHash)
        fields.append(textField)

        prefixes: List[TEncodable] = []
        prefixes.append("key-prefix")

        await ft.create(
            glide_client,
            indexName=index_name,
            schema=fields,
            options=FtCreateOptions(DataType.JSON, prefixes=prefixes),
        )
