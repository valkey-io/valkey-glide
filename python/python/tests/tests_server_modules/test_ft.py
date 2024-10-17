# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import uuid
from typing import List

import pytest
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    Field,
    FtCreateOptions,
    TextField,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TEncodable
from glide.exceptions import RequestError
from glide.glide_client import GlideClusterClient


@pytest.mark.asyncio
class TestFt:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasadd(self, glide_client: GlideClusterClient):
        indexName: str = str(uuid.uuid4())
        alias: str = "alias"
        # Test ft.aliasadd throws an error if index does not exist.
        with pytest.raises(RequestError):
            await ft.aliasadd(glide_client, alias, indexName)

        # Test ft.aliasadd successfully adds an alias to an existing index.
        await TestFt.create_test_index_hash_type(self, glide_client, indexName)
        assert await ft.aliasadd(glide_client, alias, indexName) == OK

        # Test ft.aliasadd for input of bytes type.
        indexNameString = str(uuid.uuid4())
        indexNameBytes = bytes(indexNameString, "utf-8")
        aliasNameBytes = b"alias-bytes"
        await TestFt.create_test_index_hash_type(self, glide_client, indexNameString)
        assert await ft.aliasadd(glide_client, aliasNameBytes, indexNameBytes) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasdel(self, glide_client: GlideClusterClient):
        indexName: TEncodable = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt.create_test_index_hash_type(self, glide_client, indexName)

        # Test if deleting a non existent alias throws an error.
        with pytest.raises(RequestError):
            await ft.aliasdel(glide_client, alias)

        # Test if an existing alias is deleted successfully.
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.aliasdel(glide_client, alias) == OK

        # Test if an existing alias is deleted successfully for bytes type input.
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.aliasdel(glide_client, bytes(alias, "utf-8")) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasupdate(self, glide_client: GlideClusterClient):
        indexName: str = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt.create_test_index_hash_type(self, glide_client, indexName)
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        newAliasName: str = "newAlias"
        newIndexName: str = str(uuid.uuid4())

        await TestFt.create_test_index_hash_type(self, glide_client, newIndexName)
        assert await ft.aliasadd(glide_client, newAliasName, newIndexName) == OK

        # Test if updating an already existing alias to point to an existing index returns "OK".
        assert await ft.aliasupdate(glide_client, newAliasName, indexName) == OK
        assert (
            await ft.aliasupdate(
                glide_client, bytes(alias, "utf-8"), bytes(newIndexName, "utf-8")
            )
            == OK
        )

    async def create_test_index_hash_type(
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
