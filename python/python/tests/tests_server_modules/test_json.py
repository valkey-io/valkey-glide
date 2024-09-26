# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json as OuterJson

import pytest
from glide.async_commands.core import ConditionalChange, InfoSection
from glide.async_commands.server_modules import json
from glide.async_commands.server_modules.json import JsonGetOptions
from glide.config import ProtocolVersion
from glide.constants import OK
from glide.exceptions import RequestError
from glide.glide_client import TGlideClient
from tests.test_async_client import get_random_string, parse_info_response

from glide.async_commands.server_modules import vss

@pytest.mark.asyncio
class TestJson:

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_vss_info(self, glide_client: TGlideClient):
        
        index = "idx"
        print("---------"+await vss.info(glide_client, index))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_set_get(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": 2}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        result = await json.get(glide_client, key, ".")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == json_value

        result = await json.get(glide_client, key, ["$.a", "$.b"])
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == {"$.a": [1.0], "$.b": [2]}

        assert await json.get(glide_client, "non_existing_key", "$") is None
        assert await json.get(glide_client, key, "$.d") == b"[]"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_set_get_multiple_values(self, glide_client: TGlideClient):
        key = get_random_string(5)

        assert (
            await json.set(
                glide_client,
                key,
                "$",
                OuterJson.dumps({"a": {"c": 1, "d": 4}, "b": {"c": 2}, "c": True}),
            )
            == OK
        )

        result = await json.get(glide_client, key, "$..c")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [True, 1, 2]

        result = await json.get(glide_client, key, ["$..c", "$.c"])
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == {"$..c": [True, 1, 2], "$.c": [True]}

        assert await json.set(glide_client, key, "$..c", '"new_value"') == OK
        result = await json.get(glide_client, key, "$..c")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == ["new_value"] * 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_set_conditional_set(self, glide_client: TGlideClient):
        key = get_random_string(5)
        value = OuterJson.dumps({"a": 1.0, "b": 2})
        assert (
            await json.set(
                glide_client,
                key,
                "$",
                value,
                ConditionalChange.ONLY_IF_EXISTS,
            )
            is None
        )
        assert (
            await json.set(
                glide_client,
                key,
                "$",
                value,
                ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == OK
        )

        assert (
            await json.set(
                glide_client,
                key,
                "$.a",
                "4.5",
                ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            is None
        )

        assert await json.get(glide_client, key, ".a") == b"1.0"

        assert (
            await json.set(
                glide_client,
                key,
                "$.a",
                "4.5",
                ConditionalChange.ONLY_IF_EXISTS,
            )
            == OK
        )

        assert await json.get(glide_client, key, ".a") == b"4.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_get_formatting(self, glide_client: TGlideClient):
        key = get_random_string(5)
        assert (
            await json.set(
                glide_client,
                key,
                "$",
                OuterJson.dumps({"a": 1.0, "b": 2, "c": {"d": 3, "e": 4}}),
            )
            == OK
        )

        result = await json.get(
            glide_client, key, "$", JsonGetOptions(indent="  ", newline="\n", space=" ")
        )

        expected_result = b'[\n  {\n    "a": 1.0,\n    "b": 2,\n    "c": {\n      "d": 3,\n      "e": 4\n    }\n  }\n]'
        assert result == expected_result

        result = await json.get(
            glide_client, key, "$", JsonGetOptions(indent="~", newline="\n", space="*")
        )

        expected_result = b'[\n~{\n~~"a":*1.0,\n~~"b":*2,\n~~"c":*{\n~~~"d":*3,\n~~~"e":*4\n~~}\n~}\n]'
        assert result == expected_result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_del(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.delete(glide_client, key, "$..a") == 2
        assert await json.get(glide_client, key, "$..a") == b"[]"

        result = await json.get(glide_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [{"b": {"b": 2.5, "c": True}}]

        assert await json.delete(glide_client, key, "$") == 1
        assert await json.delete(glide_client, key) == 0
        assert await json.get(glide_client, key, "$") == None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_forget(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.forget(glide_client, key, "$..a") == 2
        assert await json.get(glide_client, key, "$..a") == b"[]"

        result = await json.get(glide_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [{"b": {"b": 2.5, "c": True}}]

        assert await json.forget(glide_client, key, "$") == 1
        assert await json.forget(glide_client, key) == 0
        assert await json.get(glide_client, key, "$") == None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_toggle(self, glide_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"bool": True, "nested": {"bool": False, "nested": {"bool": 10}}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.toggle(glide_client, key, "$..bool") == [False, True, None]
        assert await json.toggle(glide_client, key, "bool") is True

        assert await json.toggle(glide_client, key, "$.nested") == [None]
        with pytest.raises(RequestError):
            assert await json.toggle(glide_client, key, "nested")

        with pytest.raises(RequestError):
            assert await json.toggle(glide_client, "non_exiting_key", "$")
