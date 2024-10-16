# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json as OuterJson
import uuid
from typing import List, Mapping
import time

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
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search(self, glide_client: GlideClusterClient):
        json_key = "a:" + str(uuid.uuid4())
        json_key2 = "z:" + str(uuid.uuid4())
        json_value = {"a": 11111, "b": 2, "c": 3}
        json_value2 = {"a": 22222, "b": 2, "c": 3}
        prefixes: List[TEncodable] = []
        prefixes.append("{json}:")
        index = "a:"+str(uuid.uuid4())

        # Create an index
        assert (
            await ft.create(
                glide_client,
                index,
                schema=[
                    NumericField("$.a", "a"),
                    NumericField("$.b", "b"),
                ],
                options=FtCreateOptions(DataType.JSON),
            )
            == OK
        )


        # Create a json key
        assert (
            await json.set(glide_client, json_key, "$", OuterJson.dumps(json_value))
            == OK
        )
        assert (
            await json.set(glide_client, json_key2, "$", OuterJson.dumps(json_value2))
            == OK
        )

        # Wait for index to be updated to avoid this error - ResponseError: The index is under construction.
        time.sleep(0.5)

        # Search the index
        result = await ft.search(
            glide_client,
            index,
            "*",
            options=FtSeachOptions(
                return_fields=[
                    ReturnField(field_identifier="a", alias="a_new"),
                    ReturnField(field_identifier="b", alias="b_new"),
                ]
            ),
        )

        #searchResultMap: Mapping[TEncodable, Mapping[TEncodable, TEncodable]]  = result[1]
        
        print("----------")
        print(result)
        print(len(result))
       # assert len(result) == 2
       # assert result[0] == 2
        assert True == False
