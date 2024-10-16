# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json as OuterJson
import uuid
from typing import List

import pytest
from glide.async_commands.server_modules import ft, json
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    FtCreateOptions,
    NumericField,
)
from glide.async_commands.server_modules.ft_options.ft_search_options import (
    FtSeachOptions,
    ReturnField,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TEncodable
from glide.glide_client import GlideClusterClient


@pytest.mark.asyncio
class TestFtSearch:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search(self, glide_client: GlideClusterClient):

        # Test
        json_key = "json:" + str(uuid.uuid4())
        json_key2 = "json:" + str(uuid.uuid4())
        json_value = {"a": 11111, "b": 2, "c": 3}
        json_value2 = {"a": 22222, "b": 2, "c": 3}
        prefixes: List[TEncodable] = []
        prefixes.append("json:")
        # Create a json key
        assert (
            await json.set(glide_client, json_key, "$", OuterJson.dumps(json_value))
            == OK
        )
        assert (
            await json.set(glide_client, json_key2, "$", OuterJson.dumps(json_value))
            == OK
        )
        # Create an index
        assert (
            await ft.create(
                glide_client,
                "idx",
                schema=[
                    NumericField("$.a", "a"),
                    NumericField("$.b", "b"),
                ],
                options=FtCreateOptions(DataType.JSON, prefixes),
            )
            == OK
        )
        # Search the index
        result = await ft.search(
            glide_client,
            "idx",
            "*",
            options=FtSeachOptions(
                return_fields=[
                    ReturnField(field_identifier="$.a", alias="a"),
                    ReturnField(field_identifier="$.b", alias="b"),
                ]
            ),
        )
        print("----------")
        print(result)
        print(len(result))
