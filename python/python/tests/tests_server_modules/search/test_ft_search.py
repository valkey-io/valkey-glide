# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json
import time
import uuid
from typing import List, Mapping, Union, cast

import pytest
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules import json as GlideJson
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
    sleep_wait_time = 0.5  # This value is in seconds

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_search(self, glide_client: GlideClusterClient):
        prefix = "{json-search-" + str(uuid.uuid4()) + "}:"
        json_key1 = prefix + str(uuid.uuid4())
        json_key2 = prefix + str(uuid.uuid4())
        json_value1 = {"a": 11111, "b": 2, "c": 3}
        json_value2 = {"a": 22222, "b": 2, "c": 3}
        prefixes: List[TEncodable] = []
        prefixes.append(prefix)
        index = prefix + str(uuid.uuid4())

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
            await GlideJson.set(glide_client, json_key1, "$", json.dumps(json_value1))
            == OK
        )
        assert (
            await GlideJson.set(glide_client, json_key2, "$", json.dumps(json_value2))
            == OK
        )

        # Wait for index to be updated to avoid this error - ResponseError: The index is under construction.
        time.sleep(self.sleep_wait_time)

        # Search the index for string inputs
        result1 = await ft.search(
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
        # Check if we get the expected result from ft.search for string inputs
        TestFtSearch._ft_search_deep_compare_result(
            self,
            result=result1,
            json_key1=json_key1,
            json_key2=json_key2,
            json_value1=json_value1,
            json_value2=json_value2,
        )

        # Search the index for byte inputs
        result2 = await ft.search(
            glide_client,
            bytes(index, "utf-8"),
            b"*",
            options=FtSeachOptions(
                return_fields=[
                    ReturnField(field_identifier=b"a", alias=b"a_new"),
                    ReturnField(field_identifier=b"b", alias=b"b_new"),
                ]
            ),
        )

        # Check if we get the expected result from ft.search from byte inputs
        TestFtSearch._ft_search_deep_compare_result(
            self,
            result=result2,
            json_key1=json_key1,
            json_key2=json_key2,
            json_value1=json_value1,
            json_value2=json_value2,
        )

    def _ft_search_deep_compare_result(
        self,
        result: List[Union[int, Mapping[TEncodable, Mapping[TEncodable, TEncodable]]]],
        json_key1: str,
        json_key2: str,
        json_value1: dict,
        json_value2: dict,
    ):
        type_name_bytes = "bytes"
        assert len(result) == 2
        assert result[0] == 2
        searchResultMap: Mapping[TEncodable, Mapping[TEncodable, TEncodable]] = cast(
            Mapping[TEncodable, Mapping[TEncodable, TEncodable]], result[1]
        )
        for key, fieldsMap in searchResultMap.items():
            keyString = key
            if type(key).__name__ == type_name_bytes:
                print(type(key).__name__)
                keyString = cast(bytes, key).decode(encoding="utf-8")
            assert keyString == json_key1 or keyString == json_key2
            if keyString == json_key1:
                for fieldName, fieldValue in fieldsMap.items():
                    fieldNameString = fieldName
                    if type(fieldName).__name__ == type_name_bytes:
                        fieldNameString = cast(bytes, fieldName).decode(
                            encoding="utf-8"
                        )
                    fieldValueInt = int(fieldValue)
                    if type(fieldValue).__name__ == type_name_bytes:
                        fieldValueInt = int(
                            cast(bytes, fieldValue).decode(encoding="utf-8")
                        )
                    assert fieldNameString == "a" or fieldNameString == "b"
                    assert fieldValueInt == json_value1.get(
                        "a"
                    ) or fieldValueInt == json_value1.get("b")
            if keyString == json_key2:
                for fieldName, fieldValue in fieldsMap.items():
                    fieldNameString = fieldName
                    if type(fieldName).__name__ == type_name_bytes:
                        fieldNameString = cast(bytes, fieldName).decode(
                            encoding="utf-8"
                        )
                    fieldValueInt = int(fieldValue)
                    if type(fieldValue).__name__ == type_name_bytes:
                        fieldValueInt = int(
                            cast(bytes, fieldValue).decode(encoding="utf-8")
                        )
                    assert fieldNameString == "a" or fieldNameString == "b"
                    assert fieldValueInt == json_value2.get(
                        "a"
                    ) or fieldValueInt == json_value2.get("b")
