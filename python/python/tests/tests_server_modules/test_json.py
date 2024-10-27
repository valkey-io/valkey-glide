# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json as OuterJson
import typing

import pytest
from glide.async_commands.core import ConditionalChange, InfoSection
from glide.async_commands.server_modules import json
from glide.async_commands.server_modules.json import JsonGetOptions
from glide.config import ProtocolVersion
from glide.constants import OK
from glide.exceptions import RequestError
from glide.glide_client import TGlideClient
from tests.test_async_client import get_random_string, parse_info_response


@pytest.mark.asyncio
class TestJson:
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
    async def test_json_del(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Non-exiseting paths
        assert await json.delete(glide_client, key, "$..path") == 0
        assert await json.delete(glide_client, key, "..path") == 0

        assert await json.delete(glide_client, key, "$..a") == 2
        assert await json.get(glide_client, key, "$..a") == b"[]"

        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.delete(glide_client, key, "..a") == 2
        with pytest.raises(RequestError):
            assert await json.get(glide_client, key, "..a")

        result = await json.get(glide_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [{"b": {"b": 2.5, "c": True}}]

        assert await json.delete(glide_client, key, "$") == 1
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert await json.delete(glide_client, key, ".") == 1
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert await json.delete(glide_client, key) == 1
        assert await json.delete(glide_client, key) == 0
        assert await json.get(glide_client, key, "$") == None

        # Non-existing keys
        assert await json.delete(glide_client, "non_existing_key", "$") == 0
        assert await json.delete(glide_client, "non_existing_key", ".") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_forget(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Non-existing paths
        assert await json.forget(glide_client, key, "$..path") == 0
        assert await json.forget(glide_client, key, "..path") == 0

        assert await json.forget(glide_client, key, "$..a") == 2
        assert await json.get(glide_client, key, "$..a") == b"[]"

        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.forget(glide_client, key, "..a") == 2
        with pytest.raises(RequestError):
            assert await json.get(glide_client, key, "..a")

        result = await json.get(glide_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [{"b": {"b": 2.5, "c": True}}]

        assert await json.forget(glide_client, key, "$") == 1
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert await json.forget(glide_client, key, ".") == 1
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert await json.forget(glide_client, key) == 1
        assert await json.forget(glide_client, key) == 0
        assert await json.get(glide_client, key, "$") == None

        # Non-existing keys
        assert await json.forget(glide_client, "non_existing_key", "$") == 0
        assert await json.forget(glide_client, "non_existing_key", ".") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_objkeys(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": {"x": 1, "y": 2}, "b": 2.5, "c": True}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        keys = await json.objkeys(glide_client, key, "$")
        assert keys == [[b"a", b"b"]]

        keys = await json.objkeys(glide_client, key, ".")
        assert keys == [b"a", b"b"]

        keys = await json.objkeys(glide_client, key, "$..")
        assert keys == [[b"a", b"b"], [b"a", b"b", b"c"], [b"x", b"y"]]

        keys = await json.objkeys(glide_client, key, "..")
        assert keys == [b"a", b"b"]

        keys = await json.objkeys(glide_client, key, "$..b")
        assert keys == [[b"a", b"b", b"c"], []]

        keys = await json.objkeys(glide_client, key, "..b")
        assert keys == [b"a", b"b", b"c"]

        # path doesn't exist
        assert await json.objkeys(glide_client, key, "$.non_existing_path") == []
        assert await json.objkeys(glide_client, key, "non_existing_path") == None

        # Value at path isnt an object
        assert await json.objkeys(glide_client, key, "$.a") == [[]]
        with pytest.raises(RequestError):
            assert await json.objkeys(glide_client, key, ".a")

        # Non-existing key
        assert await json.objkeys(glide_client, "non_exiting_key", "$") == None
        assert await json.objkeys(glide_client, "non_exiting_key", ".") == None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_toggle(self, glide_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"bool": True, "nested": {"bool": False, "nested": {"bool": 10}}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.toggle(glide_client, key, "$..bool") == [False, True, None]
        assert await json.toggle(glide_client, key, "bool") is True
        assert await json.toggle(glide_client, key, "$.not_existing") == []

        assert await json.toggle(glide_client, key, "$.nested") == [None]
        with pytest.raises(RequestError):
            assert await json.toggle(glide_client, key, "nested")

        with pytest.raises(RequestError):
            assert await json.toggle(glide_client, key, ".not_existing")

        with pytest.raises(RequestError):
            assert await json.toggle(glide_client, "non_exiting_key", "$")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_type(self, glide_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "key1": "value1",
            "key2": 2,
            "key3": [1, 2, 3],
            "key4": {"nested_key": {"key1": [4, 5]}},
            "key5": None,
            "key6": True,
        }
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        result = await json.type(glide_client, key, "$")
        assert result == [b"object"]

        result = await json.type(glide_client, key, "$..key1")
        assert result == [b"string", b"array"]

        result = await json.type(glide_client, key, "$.key2")
        assert result == [b"integer"]

        result = await json.type(glide_client, key, "$.key3")
        assert result == [b"array"]

        result = await json.type(glide_client, key, "$.key4")
        assert result == [b"object"]

        result = await json.type(glide_client, key, "$.key4.nested_key")
        assert result == [b"object"]

        result = await json.type(glide_client, key, "$.key5")
        assert result == [b"null"]

        result = await json.type(glide_client, key, "$.key6")
        assert result == [b"boolean"]

        # Check for non-existent path in enhanced mode $.key7
        result = await json.type(glide_client, key, "$.key7")
        assert result == []

        # Check for non-existent path within an existing key (array bound)
        result = await json.type(
            glide_client, key, "$.key3[3]"
        )  # Out of bounds for the array
        assert result == []

        # Legacy path (without $) - will return None for non-existing path
        result = await json.type(glide_client, key, "key7")
        assert result is None  # Legacy path returns None for non-existent key

        # Check for multiple path match in legacy
        result = await json.type(glide_client, key, "..key1")
        assert result == b"string"

        # Check for non-existent key with enhanced path
        result = await json.type(glide_client, "non_existent_key", "$.key1")
        assert result is None

        # Check for non-existent key with legacy path
        result = await json.type(glide_client, "non_existent_key", "key1")
        assert result is None  # Returns None for legacy path when the key doesn't exist

        # Check for all types in the JSON document using JSON Path
        result = await json.type(glide_client, key, "$[*]")
        assert result == [
            b"string",
            b"integer",
            b"array",
            b"object",
            b"null",
            b"boolean",
        ]

        # Check for all types in the JSON document using legacy path
        result = await json.type(glide_client, key, "[*]")
        assert result == b"string"  # Expecting only the first type (string for key1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_arrlen(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = '{"a": [1, 2, 3], "b": {"a": [1, 2], "c": {"a": 42}}}'
        assert await json.set(glide_client, key, "$", json_value) == OK

        assert await json.arrlen(glide_client, key, "$.a") == [3]

        assert await json.arrlen(glide_client, key, "$..a") == [3, 2, None]

        # Legacy path retrieves the first array match at ..a
        assert await json.arrlen(glide_client, key, "..a") == 3

        # Value at path is not an array
        assert await json.arrlen(glide_client, key, "$") == [None]
        with pytest.raises(RequestError):
            assert await json.arrlen(glide_client, key, ".")

        # Path doesn't exist
        assert await json.arrlen(glide_client, key, "$.non_existing_path") == []
        with pytest.raises(RequestError):
            assert await json.arrlen(glide_client, key, "non_existing_path")

        # Non-existing key
        assert await json.arrlen(glide_client, "non_existing_key", "$.a") is None
        assert await json.arrlen(glide_client, "non_existing_key", ".a") is None

        # No path
        with pytest.raises(RequestError):
            assert await json.arrlen(glide_client, key)

        assert await json.set(glide_client, key, "$", "[1, 2, 3, 4]") == OK
        assert await json.arrlen(glide_client, key) == 4

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_clear(self, glide_client: TGlideClient):
        key = get_random_string(5)

        json_value = '{"obj":{"a":1, "b":2}, "arr":[1,2,3], "str": "foo", "bool": true, "int": 42, "float": 3.14, "nullVal": null}'
        assert await json.set(glide_client, key, "$", json_value) == OK

        assert await json.clear(glide_client, key, "$.*") == 6
        result = await json.get(glide_client, key, "$")
        assert (
            result
            == b'[{"obj":{},"arr":[],"str":"","bool":false,"int":0,"float":0.0,"nullVal":null}]'
        )
        assert await json.clear(glide_client, key, "$.*") == 0

        assert await json.set(glide_client, key, "$", json_value) == OK
        assert await json.clear(glide_client, key, "*") == 6

        json_value = '{"a": 1, "b": {"a": [5, 6, 7], "b": {"a": true}}, "c": {"a": "value", "b": {"a": 3.5}}, "d": {"a": {"foo": "foo"}}, "nullVal": null}'
        assert await json.set(glide_client, key, "$", json_value) == OK

        assert await json.clear(glide_client, key, "b.a[1:3]") == 2
        assert await json.clear(glide_client, key, "b.a[1:3]") == 0
        assert (
            await json.get(glide_client, key, "$..a")
            == b'[1,[5,0,0],true,"value",3.5,{"foo":"foo"}]'
        )
        assert await json.clear(glide_client, key, "..a") == 6
        assert await json.get(glide_client, key, "$..a") == b'[0,[],false,"",0.0,{}]'

        assert await json.clear(glide_client, key, "$..a") == 0

        # Path doesn't exists
        assert await json.clear(glide_client, key, "$.path") == 0
        assert await json.clear(glide_client, key, "path") == 0

        # Key doesn't exists
        with pytest.raises(RequestError):
            await json.clear(glide_client, "non_existing_key")

        with pytest.raises(RequestError):
            await json.clear(glide_client, "non_existing_key", "$")

        with pytest.raises(RequestError):
            await json.clear(glide_client, "non_existing_key", ".")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_numincrby(self, glide_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "key1": 1,
            "key2": 3.5,
            "key3": {"nested_key": {"key1": [4, 5]}},
            "key4": [1, 2, 3],
            "key5": 0,
            "key6": "hello",
            "key7": None,
            "key8": {"nested_key": {"key1": 69}},
            "key9": 1.7976931348623157e308,
        }

        # Set the initial JSON document at the key
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Test JSONPath
        # Increment integer value (key1) by 5
        result = await json.numincrby(glide_client, key, "$.key1", 5)
        assert result == b"[6]"  # Expect 1 + 5 = 6

        # Increment float value (key2) by 2.5
        result = await json.numincrby(glide_client, key, "$.key2", 2.5)
        assert result == b"[6]"  # Expect 3.5 + 2.5 = 6

        # Increment nested object (key3.nested_key.key1[0]) by 7
        result = await json.numincrby(glide_client, key, "$.key3.nested_key.key1[1]", 7)
        assert result == b"[12]"  # Expect 4 + 7 = 12

        # Increment array element (key4[1]) by 1
        result = await json.numincrby(glide_client, key, "$.key4[1]", 1)
        assert result == b"[3]"  # Expect 2 + 1 = 3

        # Increment zero value (key5) by 10.23 (float number)
        result = await json.numincrby(glide_client, key, "$.key5", 10.23)
        assert result == b"[10.23]"  # Expect 0 + 10.23 = 10.23

        # Increment a string value (key6) by a number
        result = await json.numincrby(glide_client, key, "$.key6", 99)
        assert result == b"[null]"  # Expect null

        # Increment a None value (key7) by a number
        result = await json.numincrby(glide_client, key, "$.key7", 51)
        assert result == b"[null]"  # Expect null

        # Check increment for all numbers in the document using JSON Path (First Null: key3 as an entire object. Second Null: The path checks under key3, which is an object, for numeric values).
        result = await json.numincrby(glide_client, key, "$..*", 5)
        assert (
            result
            == b"[11,11,null,null,15.23,null,null,null,1.7976931348623157e+308,null,null,9,17,6,8,8,null,74]"
        )

        # Check for multiple path match in enhanced
        result = await json.numincrby(glide_client, key, "$..key1", 1)
        assert result == b"[12,null,75]"

        # Check for non existent path in JSONPath
        result = await json.numincrby(glide_client, key, "$.key10", 51)
        assert result == b"[]"  # Expect Empty Array

        # Check for non existent key in JSONPath
        with pytest.raises(RequestError):
            await json.numincrby(glide_client, "non_existent_key", "$.key10", 51)

        # Check for Overflow in JSONPath
        with pytest.raises(RequestError):
            await json.numincrby(glide_client, key, "$.key9", 1.7976931348623157e308)

        # Decrement integer value (key1) by 12
        result = await json.numincrby(glide_client, key, "$.key1", -12)
        assert result == b"[0]"  # Expect 12 - 12 = 0

        # Decrement integer value (key1) by 0.5
        result = await json.numincrby(glide_client, key, "$.key1", -0.5)
        assert result == b"[-0.5]"  # Expect 0 - 0.5 = -0.5

        # Test Legacy Path
        # Increment float value (key1) by 5 (integer)
        result = await json.numincrby(glide_client, key, "key1", 5)
        assert result == b"4.5"  # Expect -0.5 + 5 = 4.5

        # Decrement float value (key1) by 5.5 (integer)
        result = await json.numincrby(glide_client, key, "key1", -5.5)
        assert result == b"-1"  # Expect 4.5 - 5.5 = -1

        # Increment int value (key2) by 2.5 (a float number)
        result = await json.numincrby(glide_client, key, "key2", 2.5)
        assert result == b"13.5"  # Expect 11 + 2.5 = 13.5

        # Increment nested value (key3.nested_key.key1[0]) by 7
        result = await json.numincrby(glide_client, key, "key3.nested_key.key1[0]", 7)
        assert result == b"16"  # Expect 9 + 7 = 16

        # Increment array element (key4[1]) by 1
        result = await json.numincrby(glide_client, key, "key4[1]", 1)
        assert result == b"9"  # Expect 8 + 1 = 9

        # Increment a float value (key5) by 10.2 (a float number)
        result = await json.numincrby(glide_client, key, "key5", 10.2)
        assert result == b"25.43"  # Expect 15.23 + 10.2 = 25.43

        # Check for multiple path match in legacy and assure that the result of the last updated value is returned
        result = await json.numincrby(glide_client, key, "..key1", 1)
        assert result == b"76"

        # Check if the rest of the key1 path matches were updated and not only the last value
        result = await json.get(glide_client, key, "$..key1")  # type: ignore
        assert (
            result == b"[0,[16,17],76]"
        )  # First is 0 as 0 + 0 = 0, Second doesn't change as its an array type (non-numeric), third is 76 as 0 + 76 = 0

        # Check for non existent path in legacy
        with pytest.raises(RequestError):
            await json.numincrby(glide_client, key, ".key10", 51)

        # Check for non existent key in legacy
        with pytest.raises(RequestError):
            await json.numincrby(glide_client, "non_existent_key", ".key10", 51)

        # Check for Overflow in legacy
        with pytest.raises(RequestError):
            await json.numincrby(glide_client, key, ".key9", 1.7976931348623157e308)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_nummultby(self, glide_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "key1": 1,
            "key2": 3.5,
            "key3": {"nested_key": {"key1": [4, 5]}},
            "key4": [1, 2, 3],
            "key5": 0,
            "key6": "hello",
            "key7": None,
            "key8": {"nested_key": {"key1": 69}},
            "key9": 3.5953862697246314e307,
        }

        # Set the initial JSON document at the key
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Test JSONPath
        # Multiply integer value (key1) by 5
        result = await json.nummultby(glide_client, key, "$.key1", 5)
        assert result == b"[5]"  # Expect 1 * 5 = 5

        # Multiply float value (key2) by 2.5
        result = await json.nummultby(glide_client, key, "$.key2", 2.5)
        assert result == b"[8.75]"  # Expect 3.5 * 2.5 = 8.75

        # Multiply nested object (key3.nested_key.key1[1]) by 7
        result = await json.nummultby(glide_client, key, "$.key3.nested_key.key1[1]", 7)
        assert result == b"[35]"  # Expect 5 * 7 = 35

        # Multiply array element (key4[1]) by 1
        result = await json.nummultby(glide_client, key, "$.key4[1]", 1)
        assert result == b"[2]"  # Expect 2 * 1 = 2

        # Multiply zero value (key5) by 10.23 (float number)
        result = await json.nummultby(glide_client, key, "$.key5", 10.23)
        assert result == b"[0]"  # Expect 0 * 10.23 = 0

        # Multiply a string value (key6) by a number
        result = await json.nummultby(glide_client, key, "$.key6", 99)
        assert result == b"[null]"  # Expect null

        # Multiply a None value (key7) by a number
        result = await json.nummultby(glide_client, key, "$.key7", 51)
        assert result == b"[null]"  # Expect null

        # Check multiplication for all numbers in the document using JSON Path
        # key1: 5 * 5 = 25
        # key2: 8.75 * 5 = 43.75
        # key3.nested_key.key1[0]: 4 * 5 = 20
        # key3.nested_key.key1[1]: 35 * 5 = 175
        # key4[0]: 1 * 5 = 5
        # key4[1]: 2 * 5 = 10
        # key4[2]: 3 * 5 = 15
        # key5: 0 * 5 = 0
        # key8.nested_key.key1: 69 * 5 = 345
        # key9: 3.5953862697246314e307 * 5 = 1.7976931348623157e308
        result = await json.nummultby(glide_client, key, "$..*", 5)
        assert (
            result
            == b"[25,43.75,null,null,0,null,null,null,1.7976931348623157e+308,null,null,20,175,5,10,15,null,345]"
        )

        # Check for multiple path matches in JSONPath
        # key1: 25 * 2 = 50
        # key8.nested_key.key1: 345 * 2 = 690
        result = await json.nummultby(glide_client, key, "$..key1", 2)
        assert result == b"[50,null,690]"  # After previous multiplications

        # Check for non-existent path in JSONPath
        result = await json.nummultby(glide_client, key, "$.key10", 51)
        assert result == b"[]"  # Expect Empty Array

        # Check for non-existent key in JSONPath
        with pytest.raises(RequestError):
            await json.nummultby(glide_client, "non_existent_key", "$.key10", 51)

        # Check for Overflow in JSONPath
        with pytest.raises(RequestError):
            await json.nummultby(glide_client, key, "$.key9", 1.7976931348623157e308)

        # Multiply integer value (key1) by -12
        result = await json.nummultby(glide_client, key, "$.key1", -12)
        assert result == b"[-600]"  # Expect 50 * -12 = -600

        # Multiply integer value (key1) by -0.5
        result = await json.nummultby(glide_client, key, "$.key1", -0.5)
        assert result == b"[300]"  # Expect -600 * -0.5 = 300

        # Test Legacy Path
        # Multiply int value (key1) by 5 (integer)
        result = await json.nummultby(glide_client, key, "key1", 5)
        assert result == b"1500"  # Expect 300 * 5 = -1500

        # Multiply int value (key1) by -5.5 (float number)
        result = await json.nummultby(glide_client, key, "key1", -5.5)
        assert result == b"-8250"  # Expect -150 * -5.5 = -8250

        # Multiply int float (key2) by 2.5 (a float number)
        result = await json.nummultby(glide_client, key, "key2", 2.5)
        assert result == b"109.375"  # Expect 43.75 * 2.5 = 109.375

        # Multiply nested value (key3.nested_key.key1[0]) by 7
        result = await json.nummultby(glide_client, key, "key3.nested_key.key1[0]", 7)
        assert result == b"140"  # Expect 20 * 7 = 140

        # Multiply array element (key4[1]) by 1
        result = await json.nummultby(glide_client, key, "key4[1]", 1)
        assert result == b"10"  # Expect 10 * 1 = 10

        # Multiply a float value (key5) by 10.2 (a float number)
        result = await json.nummultby(glide_client, key, "key5", 10.2)
        assert result == b"0"  # Expect 0 * 10.2 = 0

        # Check for multiple path matches in legacy and assure that the result of the last updated value is returned
        # last updated value is key8.nested_key.key1: 690 * 2 = 1380
        result = await json.nummultby(glide_client, key, "..key1", 2)
        assert result == b"1380"  # Expect the last updated key1 value multiplied by 2

        # Check if the rest of the key1 path matches were updated and not only the last value
        result = await json.get(glide_client, key, "$..key1")  # type: ignore
        assert result == b"[-16500,[140,175],1380]"

        # Check for non-existent path in legacy
        with pytest.raises(RequestError):
            await json.nummultby(glide_client, key, ".key10", 51)

        # Check for non-existent key in legacy
        with pytest.raises(RequestError):
            await json.nummultby(glide_client, "non_existent_key", ".key10", 51)

        # Check for Overflow in legacy
        with pytest.raises(RequestError):
            await json.nummultby(glide_client, key, ".key9", 1.7976931348623157e308)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_strlen(self, glide_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"a": "foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.strlen(glide_client, key, "$..a") == [3, 5, None]
        assert await json.strlen(glide_client, key, "a") == 3

        assert await json.strlen(glide_client, key, "$.nested") == [None]
        with pytest.raises(RequestError):
            assert await json.strlen(glide_client, key, "nested")

        with pytest.raises(RequestError):
            assert await json.strlen(glide_client, key)

        assert await json.strlen(glide_client, key, "$.non_existing_path") == []
        with pytest.raises(RequestError):
            await json.strlen(glide_client, key, ".non_existing_path")

        assert await json.strlen(glide_client, "non_exiting_key", ".") is None
        assert await json.strlen(glide_client, "non_exiting_key", "$") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_strappend(self, glide_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"a": "foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}
        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert await json.strappend(glide_client, key, '"bar"', "$..a") == [6, 8, None]
        assert await json.strappend(glide_client, key, OuterJson.dumps("foo"), "a") == 9

        json_str = await json.get(glide_client, key, ".")
        assert isinstance(json_str, bytes)
        assert OuterJson.loads(json_str) == {
            "a": "foobarfoo",
            "nested": {"a": "hellobar"},
            "nested2": {"a": 31},
        }

        assert await json.strappend(
            glide_client, key, OuterJson.dumps("bar"), "$.nested"
        ) == [None]

        with pytest.raises(RequestError):
            await json.strappend(glide_client, key, OuterJson.dumps("bar"), ".nested")

        with pytest.raises(RequestError):
            await json.strappend(glide_client, key, OuterJson.dumps("bar"))

        assert (
            await json.strappend(
                glide_client, key, OuterJson.dumps("try"), "$.non_existing_path"
            )
            == []
        )
        with pytest.raises(RequestError):
            await json.strappend(
                glide_client, key, OuterJson.dumps("try"), "non_existing_path"
            )

        with pytest.raises(RequestError):
            await json.strappend(
                glide_client, "non_exiting_key", OuterJson.dumps("try")
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @typing.no_type_check  # since this is a complex test, skip typing to be more effective
    async def test_json_arrinsert(self, glide_client: TGlideClient):
        key = get_random_string(10)

        assert (
            await json.set(
                glide_client,
                key,
                "$",
                """
            {
                "a": [],
                "b": { "a": [1, 2, 3, 4] },
                "c": { "a": "not an array" },
                "d": [{ "a": ["x", "y"] }, { "a": [["foo"]] }],
                "e": [{ "a": 42 }, { "a": {} }],
                "f": { "a": [true, false, null] }
            }
            """,
            )
            == OK
        )

        # Insert different types of values into the matching paths
        result = await json.arrinsert(
            glide_client,
            key,
            "$..a",
            0,
            ['"string_value"', "123", '{"key": "value"}', "true", "null", '["bar"]'],
        )
        assert result == [6, 10, None, 8, 7, None, None, 9]

        updated_doc = await json.get(glide_client, key)

        expected_doc = {
            "a": ["string_value", 123, {"key": "value"}, True, None, ["bar"]],
            "b": {
                "a": [
                    "string_value",
                    123,
                    {"key": "value"},
                    True,
                    None,
                    ["bar"],
                    1,
                    2,
                    3,
                    4,
                ],
            },
            "c": {"a": "not an array"},
            "d": [
                {
                    "a": [
                        "string_value",
                        123,
                        {"key": "value"},
                        True,
                        None,
                        ["bar"],
                        "x",
                        "y",
                    ]
                },
                {
                    "a": [
                        "string_value",
                        123,
                        {"key": "value"},
                        True,
                        None,
                        ["bar"],
                        ["foo"],
                    ]
                },
            ],
            "e": [{"a": 42}, {"a": {}}],
            "f": {
                "a": [
                    "string_value",
                    123,
                    {"key": "value"},
                    True,
                    None,
                    ["bar"],
                    True,
                    False,
                    None,
                ]
            },
        }

        assert OuterJson.loads(updated_doc) == expected_doc

        # Insert into a specific index (non-zero)
        result = await json.arrinsert(
            glide_client,
            key,
            "$..a",
            2,
            ['"insert_at_2"'],
        )
        assert result == [7, 11, None, 9, 8, None, None, 10]

        # Check document after insertion at index 2
        updated_doc_at_2 = await json.get(glide_client, key)
        expected_doc["a"].insert(2, "insert_at_2")
        expected_doc["b"]["a"].insert(2, "insert_at_2")
        expected_doc["d"][0]["a"].insert(2, "insert_at_2")
        expected_doc["d"][1]["a"].insert(2, "insert_at_2")
        expected_doc["f"]["a"].insert(2, "insert_at_2")
        assert OuterJson.loads(updated_doc_at_2) == expected_doc

        # Insert with a legacy path
        result = await json.arrinsert(
            glide_client,
            key,
            "..a",  # legacy path
            0,
            ['"legacy_value"'],
        )
        assert (
            result == 8
        )  # Returns length of the first modified array (in this case, 'a')

        # Check document after insertion at root legacy path (all matching arrays should be updated)
        updated_doc_legacy = await json.get(glide_client, key)

        # Update `expected_doc` with the new value inserted at index 0 of all matching arrays
        expected_doc["a"].insert(0, "legacy_value")
        expected_doc["b"]["a"].insert(0, "legacy_value")
        expected_doc["d"][0]["a"].insert(0, "legacy_value")
        expected_doc["d"][1]["a"].insert(0, "legacy_value")
        expected_doc["f"]["a"].insert(0, "legacy_value")

        assert OuterJson.loads(updated_doc_legacy) == expected_doc

        # Insert with an index out of range for some arrays
        with pytest.raises(RequestError):
            await json.arrinsert(
                glide_client,
                key,
                "$..a",
                10,  # Index out of range for some paths but valid for others
                ['"out_of_range_value"'],
            )

        with pytest.raises(RequestError):
            await json.arrinsert(
                glide_client,
                key,
                "..a",
                10,  # Index out of range for some paths but valid for others
                ['"out_of_range_value"'],
            )

        # Negative index insertion (should insert from the end of the array)
        result = await json.arrinsert(
            glide_client,
            key,
            "$..a",
            -1,
            ['"negative_index_value"'],
        )
        assert result == [9, 13, None, 11, 10, None, None, 12]  # Update valid paths

        # Check document after negative index insertion
        updated_doc_negative = await json.get(glide_client, key)
        expected_doc["a"].insert(-1, "negative_index_value")
        expected_doc["b"]["a"].insert(-1, "negative_index_value")
        expected_doc["d"][0]["a"].insert(-1, "negative_index_value")
        expected_doc["d"][1]["a"].insert(-1, "negative_index_value")
        expected_doc["f"]["a"].insert(-1, "negative_index_value")
        assert OuterJson.loads(updated_doc_negative) == expected_doc

        # Non-existing path
        with pytest.raises(RequestError):
            await json.arrinsert(glide_client, key, ".path", 5, ['"value"'])

        await json.arrinsert(glide_client, key, "$.path", 5, ['"value"']) == []

        # Key doesnt exist
        with pytest.raises(RequestError):
            await json.arrinsert(glide_client, "non_existent_key", "$", 5, ['"value"'])

        with pytest.raises(RequestError):
            await json.arrinsert(glide_client, "non_existent_key", ".", 5, ['"value"'])

        # value at path is not an array
        with pytest.raises(RequestError):
            await json.arrinsert(glide_client, key, ".e", 5, ['"value"'])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_debug_fields(self, glide_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "key1": 1,
            "key2": 3.5,
            "key3": {"nested_key": {"key1": [4, 5]}},
            "key4": [1, 2, 3],
            "key5": 0,
            "key6": "hello",
            "key7": None,
            "key8": {"nested_key": {"key1": 3.5953862697246314e307}},
            "key9": 3.5953862697246314e307,
            "key10": True,
        }

        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Test JSONPath - Fields Subcommand
        # Test integer
        result = await json.debug_fields(glide_client, key, "$.key1")
        assert result == [1]

        # Test float
        result = await json.debug_fields(glide_client, key, "$.key2")
        assert result == [1]

        # Test Nested Value
        result = await json.debug_fields(glide_client, key, "$.key3")
        assert result == [4]

        result = await json.debug_fields(glide_client, key, "$.key3.nested_key.key1")
        assert result == [2]

        # Test Array
        result = await json.debug_fields(glide_client, key, "$.key4[2]")
        assert result == [1]

        # Test String
        result = await json.debug_fields(glide_client, key, "$.key6")
        assert result == [1]

        # Test Null
        result = await json.debug_fields(glide_client, key, "$.key7")
        assert result == [1]

        # Test Bool
        result = await json.debug_fields(glide_client, key, "$.key10")
        assert result == [1]

        # Test all keys
        result = await json.debug_fields(glide_client, key, "$[*]")
        assert result == [1, 1, 4, 3, 1, 1, 1, 2, 1, 1]

        # Test multiple paths
        result = await json.debug_fields(glide_client, key, "$..key1")
        assert result == [1, 2, 1]

        # Test for non-existent path
        result = await json.debug_fields(glide_client, key, "$.key11")
        assert result == []

        # Test for non-existent key
        result = await json.debug_fields(glide_client, "non_existent_key", "$.key10")
        assert result == None

        # Test no provided path
        # Total Fields (19) - breakdown:
        # Top-Level Fields: 10
        # Fields within key3: 4 ($.key3, $.key3.nested_key, $.key3.nested_key.key1, $.key3.nested_key.key1)
        # Fields within key4: 3 ($.key4[0], $.key4[1], $.key4[2])
        # Fields within key8: 2 ($.key8, $.key8.nested_key)
        result = await json.debug_fields(glide_client, key)
        assert result == 19

        # Test legacy path - Fields Subcommand
        # Test integer
        result = await json.debug_fields(glide_client, key, ".key1")
        assert result == 1

        # Test float
        result = await json.debug_fields(glide_client, key, ".key2")
        assert result == 1

        # Test Nested Value
        result = await json.debug_fields(glide_client, key, ".key3")
        assert result == 4

        result = await json.debug_fields(glide_client, key, ".key3.nested_key.key1")
        assert result == 2

        # Test Array
        result = await json.debug_fields(glide_client, key, ".key4[2]")
        assert result == 1

        # Test String
        result = await json.debug_fields(glide_client, key, ".key6")
        assert result == 1

        # Test Null
        result = await json.debug_fields(glide_client, key, ".key7")
        assert result == 1

        # Test Bool
        result = await json.debug_fields(glide_client, key, ".key10")
        assert result == 1

        # Test multiple paths
        result = await json.debug_fields(glide_client, key, "..key1")
        assert result == 1  # Returns number of fields of the first JSON value

        # Test for non-existent path
        with pytest.raises(RequestError):
            await json.debug_fields(glide_client, key, ".key11")

        # Test for non-existent key
        result = await json.debug_fields(glide_client, "non_existent_key", ".key10")
        assert result == None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_debug_memory(self, glide_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "key1": 1,
            "key2": 3.5,
            "key3": {"nested_key": {"key1": [4, 5]}},
            "key4": [1, 2, 3],
            "key5": 0,
            "key6": "hello",
            "key7": None,
            "key8": {"nested_key": {"key1": 3.5953862697246314e307}},
            "key9": 3.5953862697246314e307,
            "key10": True,
        }

        assert await json.set(glide_client, key, "$", OuterJson.dumps(json_value)) == OK
        # Test JSONPath - Memory Subcommand
        # Test integer
        result = await json.debug_memory(glide_client, key, "$.key1")
        assert result == [16]
        # Test float
        result = await json.debug_memory(glide_client, key, "$.key2")
        assert result == [16]
        # Test Nested Value
        result = await json.debug_memory(glide_client, key, "$.key3.nested_key.key1[0]")
        assert result == [16]
        # Test Array
        result = await json.debug_memory(glide_client, key, "$.key4")
        assert result == [16 * 4]

        result = await json.debug_memory(glide_client, key, "$.key4[2]")
        assert result == [16]
        # Test String
        result = await json.debug_memory(glide_client, key, "$.key6")
        assert result == [16]
        # Test Null
        result = await json.debug_memory(glide_client, key, "$.key7")
        assert result == [16]
        # Test Bool
        result = await json.debug_memory(glide_client, key, "$.key10")
        assert result == [16]
        # Test all keys
        result = await json.debug_memory(glide_client, key, "$[*]")
        assert result == [16, 16, 110, 64, 16, 16, 16, 101, 39, 16]
        # Test multiple paths
        result = await json.debug_memory(glide_client, key, "$..key1")
        assert result == [16, 48, 39]
        # Test for non-existent path
        result = await json.debug_memory(glide_client, key, "$.key11")
        assert result == []
        # Test for non-existent key
        result = await json.debug_memory(glide_client, "non_existent_key", "$.key10")
        assert result == None
        # Test no provided path
        # Total Memory (504 bytes) - visual breakdown:
        # ├── Root Object Overhead (129 bytes)
        # └── JSON Elements (374 bytes)
        #    ├── key1: 16 bytes
        #    ├── key2: 16 bytes
        #    ├── key3: 110 bytes
        #    ├── key4: 64 bytes
        #    ├── key5: 16 bytes
        #    ├── key6: 16 bytes
        #    ├── key7: 16 bytes
        #    ├── key8: 101 bytes
        #    └── key9: 39 bytes
        result = await json.debug_memory(glide_client, key)
        assert result == 504
        # Test Legacy Path - Memory Subcommand
        # Test integer
        result = await json.debug_memory(glide_client, key, ".key1")
        assert result == 16
        # Test float
        result = await json.debug_memory(glide_client, key, ".key2")
        assert result == 16
        # Test Nested Value
        result = await json.debug_memory(glide_client, key, ".key3.nested_key.key1[0]")
        assert result == 16
        # Test Array
        result = await json.debug_memory(glide_client, key, ".key4[2]")
        assert result == 16
        # Test String
        result = await json.debug_memory(glide_client, key, ".key6")
        assert result == 16
        # Test Null
        result = await json.debug_memory(glide_client, key, ".key7")
        assert result == 16
        # Test Bool
        result = await json.debug_memory(glide_client, key, ".key10")
        assert result == 16
        # Test multiple paths
        result = await json.debug_memory(glide_client, key, "..key1")
        assert result == 16  # Returns the memory usage of the first JSON value
        # Test for non-existent path
        with pytest.raises(RequestError):
            await json.debug_memory(glide_client, key, ".key11")
        # Test for non-existent key
        result = await json.debug_memory(glide_client, "non_existent_key", ".key10")
        assert result == None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @typing.no_type_check
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_json_arrtrim(self, glide_client: TGlideClient):
        key = get_random_string(5)

        # Test with enhanced path syntax
        json_value = '{"a": [0, 1, 2, 3, 4, 5, 6, 7, 8], "b": {"a": [0, 9, 10, 11, 12, 13], "c": {"a": 42}}}'
        assert await json.set(glide_client, key, "$", json_value) == OK

        # Basic trim
        assert await json.arrtrim(glide_client, key, "$..a", 1, 7) == [7, 5, None]
        assert OuterJson.loads(await json.get(glide_client, key, "$..a")) == [
            [1, 2, 3, 4, 5, 6, 7],
            [9, 10, 11, 12, 13],
            42,
        ]

        # Test negative start (should be treated as 0)
        assert await json.arrtrim(glide_client, key, "$.a", -1, 5) == [6]
        assert OuterJson.loads(await json.get(glide_client, key, "$.a")) == [
            [1, 2, 3, 4, 5, 6]
        ]
        assert await json.arrtrim(glide_client, key, ".a", -1, 5) == 6
        assert OuterJson.loads(await json.get(glide_client, key, ".a")) == [
            1,
            2,
            3,
            4,
            5,
            6,
        ]

        # Test end >= size (should be treated as size-1)
        assert await json.arrtrim(glide_client, key, "$.a", 0, 10) == [6]
        assert OuterJson.loads(await json.get(glide_client, key, "$.a")) == [
            [1, 2, 3, 4, 5, 6]
        ]

        assert await json.arrtrim(glide_client, key, ".a", 0, 10) == 6
        assert OuterJson.loads(await json.get(glide_client, key, ".a")) == [
            1,
            2,
            3,
            4,
            5,
            6,
        ]

        # Test start >= size (should empty the array)
        assert await json.arrtrim(glide_client, key, "$.a", 7, 10) == [0]
        assert OuterJson.loads(await json.get(glide_client, key, "$.a")) == [[]]

        assert await json.set(glide_client, key, ".a", '["a", "b", "c"]') == OK
        assert await json.arrtrim(glide_client, key, ".a", 7, 10) == 0
        assert OuterJson.loads(await json.get(glide_client, key, ".a")) == []

        # Test start > end (should empty the array)
        assert await json.arrtrim(glide_client, key, "$..a", 2, 1) == [0, 0, None]
        assert OuterJson.loads(await json.get(glide_client, key, "$..a")) == [
            [],
            [],
            42,
        ]
        assert await json.set(glide_client, key, "..a", '["a", "b", "c", "d"]') == OK
        assert await json.arrtrim(glide_client, key, "..a", 2, 1) == 0
        assert OuterJson.loads(await json.get(glide_client, key, ".a")) == []

        # Multiple path match
        assert await json.set(glide_client, key, "$", json_value) == OK
        assert await json.arrtrim(glide_client, key, "..a", 1, 10) == 8
        assert OuterJson.loads(await json.get(glide_client, key, "$..a")) == [
            [1, 2, 3, 4, 5, 6, 7, 8],
            [9, 10, 11, 12, 13],
            42,
        ]

        # Test with non-existent path
        with pytest.raises(RequestError):
            await json.arrtrim(glide_client, key, ".non_existent", 0, 1)

        assert await json.arrtrim(glide_client, key, "$.non_existent", 0, 1) == []

        # Test with non-array path
        assert await json.arrtrim(glide_client, key, "$", 0, 1) == [None]

        with pytest.raises(RequestError):
            await json.arrtrim(glide_client, key, ".", 0, 1)

        # Test with non-existent key
        with pytest.raises(RequestError):
            await json.arrtrim(glide_client, "non_existent_key", "$", 0, 1)

        # Test with non-existent key
        with pytest.raises(RequestError):
            await json.arrtrim(glide_client, "non_existent_key", ".", 0, 1)

        # Test empty array
        assert await json.set(glide_client, key, "$.empty", "[]") == OK
        assert await json.arrtrim(glide_client, key, "$.empty", 0, 1) == [0]
        assert await json.arrtrim(glide_client, key, ".empty", 0, 1) == 0
        assert OuterJson.loads(await json.get(glide_client, key, "$.empty")) == [[]]
