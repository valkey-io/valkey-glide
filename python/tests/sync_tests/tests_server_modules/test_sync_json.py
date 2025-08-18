# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import copy
import json as OuterJson
import random
import typing
from typing import List, Optional

import pytest
from glide_shared.commands.batch import ClusterBatch
from glide_shared.commands.core_options import ConditionalChange
from glide_shared.commands.server_modules import json_batch
from glide_shared.commands.server_modules.json_options import (
    JsonArrIndexOptions,
    JsonArrPopOptions,
    JsonGetOptions,
)
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK
from glide_shared.exceptions import RequestError
from glide_sync import glide_json as json
from glide_sync.glide_client import GlideClusterClient, TGlideClient

from tests.utils.utils import get_random_string


def get_random_value(value_type="str"):
    if value_type == "int":
        return random.randint(1, 100)
    elif value_type == "float":
        return round(random.uniform(1, 100), 2)
    elif value_type == "str":
        return "".join(random.choices("abcdefghijklmnopqrstuvwxyz", k=5))
    elif value_type == "bool":
        return random.choice([True, False])
    elif value_type == "null":
        return None


class TestSyncJson:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_set_get(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": 2}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        result = json.get(glide_sync_client, key, ".")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == json_value

        result = json.get(glide_sync_client, key, ["$.a", "$.b"])
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == {"$.a": [1.0], "$.b": [2]}

        assert json.get(glide_sync_client, "non_existing_key", "$") is None
        assert json.get(glide_sync_client, key, "$.d") == b"[]"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_set_get_multiple_values(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        assert (
            json.set(
                glide_sync_client,
                key,
                "$",
                OuterJson.dumps({"a": {"c": 1, "d": 4}, "b": {"c": 2}, "c": True}),
            )
            == OK
        )

        result = json.get(glide_sync_client, key, "$..c")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [True, 1, 2]

        result = json.get(glide_sync_client, key, ["$..c", "$.c"])
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == {"$..c": [True, 1, 2], "$.c": [True]}

        assert json.set(glide_sync_client, key, "$..c", '"new_value"') == OK
        result = json.get(glide_sync_client, key, "$..c")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == ["new_value"] * 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_set_conditional_set(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)
        value = OuterJson.dumps({"a": 1.0, "b": 2})
        assert (
            json.set(
                glide_sync_client,
                key,
                "$",
                value,
                ConditionalChange.ONLY_IF_EXISTS,
            )
            is None
        )
        assert (
            json.set(
                glide_sync_client,
                key,
                "$",
                value,
                ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == OK
        )

        assert (
            json.set(
                glide_sync_client,
                key,
                "$.a",
                "4.5",
                ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            is None
        )

        assert json.get(glide_sync_client, key, ".a") == b"1.0"

        assert (
            json.set(
                glide_sync_client,
                key,
                "$.a",
                "4.5",
                ConditionalChange.ONLY_IF_EXISTS,
            )
            == OK
        )

        assert json.get(glide_sync_client, key, ".a") == b"4.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_get_formatting(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)
        assert (
            json.set(
                glide_sync_client,
                key,
                "$",
                OuterJson.dumps({"a": 1.0, "b": 2, "c": {"d": 3, "e": 4}}),
            )
            == OK
        )

        result = json.get(
            glide_sync_client,
            key,
            "$",
            JsonGetOptions(indent="  ", newline="\n", space=" "),
        )

        expected_result = b'[\n  {\n    "a": 1.0,\n    "b": 2,\n    "c": {\n      "d": 3,\n      "e": 4\n    }\n  }\n]'
        assert result == expected_result

        result = json.get(
            glide_sync_client,
            key,
            "$",
            JsonGetOptions(indent="~", newline="\n", space="*"),
        )

        expected_result = b'[\n~{\n~~"a":*1.0,\n~~"b":*2,\n~~"c":*{\n~~~"d":*3,\n~~~"e":*4\n~~}\n~}\n]'
        assert result == expected_result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_mget(self, glide_sync_client: TGlideClient):
        key1 = get_random_string(5)
        key2 = get_random_string(5)

        json1_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        json2_value = {"a": 3.0, "b": {"a": 1, "b": 4}}

        assert (
            json.set(glide_sync_client, key1, "$", OuterJson.dumps(json1_value)) == OK
        )
        assert (
            json.set(glide_sync_client, key2, "$", OuterJson.dumps(json2_value)) == OK
        )

        # Test with root JSONPath
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            "$",
        )
        expected_result: List[Optional[bytes]] = [
            b'[{"a":1.0,"b":{"a":1,"b":2.5,"c":true}}]',
            b'[{"a":3.0,"b":{"a":1,"b":4}}]',
        ]
        assert result == expected_result

        # Retrieves the full JSON objects from multiple keys.
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            ".",
        )
        expected_result = [
            b'{"a":1.0,"b":{"a":1,"b":2.5,"c":true}}',
            b'{"a":3.0,"b":{"a":1,"b":4}}',
        ]
        assert result == expected_result

        result = json.mget(
            glide_sync_client,
            [key1, key2],
            "$.a",
        )
        expected_result = [b"[1.0]", b"[3.0]"]
        assert result == expected_result

        # Retrieves the value of the 'b' field for multiple keys.
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            "$.b",
        )
        expected_result = [b'[{"a":1,"b":2.5,"c":true}]', b'[{"a":1,"b":4}]']
        assert result == expected_result

        # Retrieves all values of 'b' fields using recursive path for multiple keys
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            "$..b",
        )
        expected_result = [b'[{"a":1,"b":2.5,"c":true},2.5]', b'[{"a":1,"b":4},4]']
        assert result == expected_result

        # retrieves the value of the nested 'b.b' field for multiple keys
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            ".b.b",
        )
        expected_result = [b"2.5", b"4"]
        assert result == expected_result

        # JSONPath that exists in only one of the keys
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            "$.b.c",
        )
        expected_result = [b"[true]", b"[]"]
        assert result == expected_result

        # Legacy path that exists in only one of the keys
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            ".b.c",
        )
        expected_result = [b"true", None]
        assert result == expected_result

        # JSONPath doesn't exist
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            "$non_existing_path",
        )
        expected_result = [b"[]", b"[]"]
        assert result == expected_result

        # Legacy path doesn't exist
        result = json.mget(
            glide_sync_client,
            [key1, key2],
            ".non_existing_path",
        )
        assert result == [None, None]

        # JSONPath one key doesn't exist
        result = json.mget(
            glide_sync_client,
            [key1, "{non_existing_key}"],
            "$.a",
        )
        assert result == [b"[1.0]", None]

        # Legacy path one key doesn't exist
        result = json.mget(
            glide_sync_client,
            [key1, "{non_existing_key}"],
            ".a",
        )
        assert result == [b"1.0", None]

        # Both keys don't exist
        result = json.mget(
            glide_sync_client,
            ["{non_existing_key}1", "{non_existing_key}2"],
            "$a",
        )
        assert result == [None, None]

        # Test with only one key
        result = json.mget(
            glide_sync_client,
            [key1],
            "$.a",
        )
        expected_result = [b"[1.0]"]
        assert result == expected_result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_del(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Non-exiseting paths
        assert json.delete(glide_sync_client, key, "$..path") == 0
        assert json.delete(glide_sync_client, key, "..path") == 0

        assert json.delete(glide_sync_client, key, "$..a") == 2
        assert json.get(glide_sync_client, key, "$..a") == b"[]"

        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert json.delete(glide_sync_client, key, "..a") == 2
        with pytest.raises(RequestError):
            assert json.get(glide_sync_client, key, "..a")

        result = json.get(glide_sync_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [{"b": {"b": 2.5, "c": True}}]

        assert json.delete(glide_sync_client, key, "$") == 1
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert json.delete(glide_sync_client, key, ".") == 1
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert json.delete(glide_sync_client, key) == 1
        assert json.delete(glide_sync_client, key) == 0
        assert json.get(glide_sync_client, key, "$") is None

        # Non-existing keys
        assert json.delete(glide_sync_client, "non_existing_key", "$") == 0
        assert json.delete(glide_sync_client, "non_existing_key", ".") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_forget(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": 1, "b": 2.5, "c": True}}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Non-existing paths
        assert json.forget(glide_sync_client, key, "$..path") == 0
        assert json.forget(glide_sync_client, key, "..path") == 0

        assert json.forget(glide_sync_client, key, "$..a") == 2
        assert json.get(glide_sync_client, key, "$..a") == b"[]"

        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert json.forget(glide_sync_client, key, "..a") == 2
        with pytest.raises(RequestError):
            assert json.get(glide_sync_client, key, "..a")

        result = json.get(glide_sync_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [{"b": {"b": 2.5, "c": True}}]

        assert json.forget(glide_sync_client, key, "$") == 1
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert json.forget(glide_sync_client, key, ".") == 1
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK
        assert json.forget(glide_sync_client, key) == 1
        assert json.forget(glide_sync_client, key) == 0
        assert json.get(glide_sync_client, key, "$") is None

        # Non-existing keys
        assert json.forget(glide_sync_client, "non_existing_key", "$") == 0
        assert json.forget(glide_sync_client, "non_existing_key", ".") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_objkeys(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": {"x": 1, "y": 2}, "b": 2.5, "c": True}}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        keys = json.objkeys(glide_sync_client, key, "$")
        assert keys == [[b"a", b"b"]]

        keys = json.objkeys(glide_sync_client, key, ".")
        assert keys == [b"a", b"b"]

        keys = json.objkeys(glide_sync_client, key, "$..")
        assert keys == [[b"a", b"b"], [b"a", b"b", b"c"], [b"x", b"y"]]

        keys = json.objkeys(glide_sync_client, key, "..")
        assert keys == [b"a", b"b"]

        keys = json.objkeys(glide_sync_client, key, "$..b")
        assert keys == [[b"a", b"b", b"c"], []]

        keys = json.objkeys(glide_sync_client, key, "..b")
        assert keys == [b"a", b"b", b"c"]

        # path doesn't exist
        assert json.objkeys(glide_sync_client, key, "$.non_existing_path") == []
        assert json.objkeys(glide_sync_client, key, "non_existing_path") is None

        # Value at path isnt an object
        assert json.objkeys(glide_sync_client, key, "$.a") == [[]]
        with pytest.raises(RequestError):
            assert json.objkeys(glide_sync_client, key, ".a")

        # Non-existing key
        assert json.objkeys(glide_sync_client, "non_exiting_key", "$") is None
        assert json.objkeys(glide_sync_client, "non_exiting_key", ".") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_toggle(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"bool": True, "nested": {"bool": False, "nested": {"bool": 10}}}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert json.toggle(glide_sync_client, key, "$..bool") == [False, True, None]
        assert json.toggle(glide_sync_client, key, "bool") is True
        assert json.toggle(glide_sync_client, key, "$.not_existing") == []

        assert json.toggle(glide_sync_client, key, "$.nested") == [None]
        with pytest.raises(RequestError):
            assert json.toggle(glide_sync_client, key, "nested")

        with pytest.raises(RequestError):
            assert json.toggle(glide_sync_client, key, ".not_existing")

        with pytest.raises(RequestError):
            assert json.toggle(glide_sync_client, "non_exiting_key", "$")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_type(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "key1": "value1",
            "key2": 2,
            "key3": [1, 2, 3],
            "key4": {"nested_key": {"key1": [4, 5]}},
            "key5": None,
            "key6": True,
        }
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        result = json.type(glide_sync_client, key, "$")
        assert result == [b"object"]

        result = json.type(glide_sync_client, key, "$..key1")
        assert result == [b"string", b"array"]

        result = json.type(glide_sync_client, key, "$.key2")
        assert result == [b"integer"]

        result = json.type(glide_sync_client, key, "$.key3")
        assert result == [b"array"]

        result = json.type(glide_sync_client, key, "$.key4")
        assert result == [b"object"]

        result = json.type(glide_sync_client, key, "$.key4.nested_key")
        assert result == [b"object"]

        result = json.type(glide_sync_client, key, "$.key5")
        assert result == [b"null"]

        result = json.type(glide_sync_client, key, "$.key6")
        assert result == [b"boolean"]

        # Check for non-existent path in enhanced mode $.key7
        result = json.type(glide_sync_client, key, "$.key7")
        assert result == []

        # Check for non-existent path within an existing key (array bound)
        result = json.type(
            glide_sync_client, key, "$.key3[3]"
        )  # Out of bounds for the array
        assert result == []

        # Legacy path (without $) - will return None for non-existing path
        result = json.type(glide_sync_client, key, "key7")
        assert result is None  # Legacy path returns None for non-existent key

        # Check for multiple path match in legacy
        result = json.type(glide_sync_client, key, "..key1")
        assert result == b"string"

        # Check for non-existent key with enhanced path
        result = json.type(glide_sync_client, "non_existent_key", "$.key1")
        assert result is None

        # Check for non-existent key with legacy path
        result = json.type(glide_sync_client, "non_existent_key", "key1")
        assert result is None  # Returns None for legacy path when the key doesn't exist

        # Check for all types in the JSON document using JSON Path
        result = json.type(glide_sync_client, key, "$[*]")
        assert result == [
            b"string",
            b"integer",
            b"array",
            b"object",
            b"null",
            b"boolean",
        ]

        # Check for all types in the JSON document using legacy path
        result = json.type(glide_sync_client, key, "[*]")
        assert result == b"string"  # Expecting only the first type (string for key1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_objlen(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = {"a": 1.0, "b": {"a": {"x": 1, "y": 2}, "b": 2.5, "c": True}}

        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        len = json.objlen(glide_sync_client, key, "$")
        assert len == [2]

        len = json.objlen(glide_sync_client, key, ".")
        assert len == 2

        len = json.objlen(glide_sync_client, key, "$..")
        assert len == [2, 3, 2]

        len = json.objlen(glide_sync_client, key, "..")
        assert len == 2

        len = json.objlen(glide_sync_client, key, "$..b")
        assert len == [3, None]

        len = json.objlen(glide_sync_client, key, "..b")
        assert len == 3

        len = json.objlen(glide_sync_client, key, "..a")
        assert len == 2

        len = json.objlen(glide_sync_client, key)
        assert len == 2

        # path doesn't exist
        assert json.objlen(glide_sync_client, key, "$.non_existing_path") == []
        with pytest.raises(RequestError):
            json.objlen(glide_sync_client, key, "non_existing_path")

        # Value at path isnt an object
        assert json.objlen(glide_sync_client, key, "$.a") == [None]
        with pytest.raises(RequestError):
            json.objlen(glide_sync_client, key, ".a")

        # Non-existing key
        assert json.objlen(glide_sync_client, "non_exiting_key", "$") is None
        assert json.objlen(glide_sync_client, "non_exiting_key", ".") is None

        assert json.set(glide_sync_client, key, "$", '{"a": 1, "b": 2, "c":3, "d":4}')
        assert json.objlen(glide_sync_client, key) == 4

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_arrlen(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = '{"a": [1, 2, 3], "b": {"a": [1, 2], "c": {"a": 42}}}'
        assert json.set(glide_sync_client, key, "$", json_value) == OK

        assert json.arrlen(glide_sync_client, key, "$.a") == [3]

        assert json.arrlen(glide_sync_client, key, "$..a") == [3, 2, None]

        # Legacy path retrieves the first array match at ..a
        assert json.arrlen(glide_sync_client, key, "..a") == 3

        # Value at path is not an array
        assert json.arrlen(glide_sync_client, key, "$") == [None]
        with pytest.raises(RequestError):
            assert json.arrlen(glide_sync_client, key, ".")

        # Path doesn't exist
        assert json.arrlen(glide_sync_client, key, "$.non_existing_path") == []
        with pytest.raises(RequestError):
            assert json.arrlen(glide_sync_client, key, "non_existing_path")

        # Non-existing key
        assert json.arrlen(glide_sync_client, "non_existing_key", "$.a") is None
        assert json.arrlen(glide_sync_client, "non_existing_key", ".a") is None

        # No path
        with pytest.raises(RequestError):
            assert json.arrlen(glide_sync_client, key)

        assert json.set(glide_sync_client, key, "$", "[1, 2, 3, 4]") == OK
        assert json.arrlen(glide_sync_client, key) == 4

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_clear(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        json_value = (
            '{"obj":{"a":1, "b":2}, "arr":[1,2,3], '
            '"str": "foo", "bool": true, "int": 42, '
            '"float": 3.14, "nullVal": null}'
        )
        assert json.set(glide_sync_client, key, "$", json_value) == OK

        assert json.clear(glide_sync_client, key, "$.*") == 6
        result = json.get(glide_sync_client, key, "$")
        assert (
            result
            == b'[{"obj":{},"arr":[],"str":"","bool":false,"int":0,"float":0.0,"nullVal":null}]'
        )
        assert json.clear(glide_sync_client, key, "$.*") == 0

        assert json.set(glide_sync_client, key, "$", json_value) == OK
        assert json.clear(glide_sync_client, key, "*") == 6

        json_value = (
            '{"a": 1, "b": {"a": [5, 6, 7], "b": {"a": true}}, '
            '"c": {"a": "value", "b": {"a": 3.5}}, "d": {"a": '
            '{"foo": "foo"}}, "nullVal": null}'
        )
        assert json.set(glide_sync_client, key, "$", json_value) == OK

        assert json.clear(glide_sync_client, key, "b.a[1:3]") == 2
        assert json.clear(glide_sync_client, key, "b.a[1:3]") == 0
        assert (
            json.get(glide_sync_client, key, "$..a")
            == b'[1,[5,0,0],true,"value",3.5,{"foo":"foo"}]'
        )
        assert json.clear(glide_sync_client, key, "..a") == 6
        assert json.get(glide_sync_client, key, "$..a") == b'[0,[],false,"",0.0,{}]'

        assert json.clear(glide_sync_client, key, "$..a") == 0

        # Path doesn't exists
        assert json.clear(glide_sync_client, key, "$.path") == 0
        assert json.clear(glide_sync_client, key, "path") == 0

        # Key doesn't exists
        with pytest.raises(RequestError):
            json.clear(glide_sync_client, "non_existing_key")

        with pytest.raises(RequestError):
            json.clear(glide_sync_client, "non_existing_key", "$")

        with pytest.raises(RequestError):
            json.clear(glide_sync_client, "non_existing_key", ".")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_numincrby(self, glide_sync_client: TGlideClient):
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
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Test JSONPath
        # Increment integer value (key1) by 5
        result = json.numincrby(glide_sync_client, key, "$.key1", 5)
        assert result == b"[6]"  # Expect 1 + 5 = 6

        # Increment float value (key2) by 2.5
        result = json.numincrby(glide_sync_client, key, "$.key2", 2.5)
        assert result == b"[6]"  # Expect 3.5 + 2.5 = 6

        # Increment nested object (key3.nested_key.key1[0]) by 7
        result = json.numincrby(glide_sync_client, key, "$.key3.nested_key.key1[1]", 7)
        assert result == b"[12]"  # Expect 4 + 7 = 12

        # Increment array element (key4[1]) by 1
        result = json.numincrby(glide_sync_client, key, "$.key4[1]", 1)
        assert result == b"[3]"  # Expect 2 + 1 = 3

        # Increment zero value (key5) by 10.23 (float number)
        result = json.numincrby(glide_sync_client, key, "$.key5", 10.23)
        assert result == b"[10.23]"  # Expect 0 + 10.23 = 10.23

        # Increment a string value (key6) by a number
        result = json.numincrby(glide_sync_client, key, "$.key6", 99)
        assert result == b"[null]"  # Expect null

        # Increment a None value (key7) by a number
        result = json.numincrby(glide_sync_client, key, "$.key7", 51)
        assert result == b"[null]"  # Expect null

        # Check increment for all numbers in the document using JSON Path (First Null: key3 as an entire object.
        # Second Null: The path checks under key3, which is an object, for numeric values).
        result = json.numincrby(glide_sync_client, key, "$..*", 5)
        assert (
            result
            == b"[11,11,null,null,15.23,null,null,null,1.7976931348623157e+308,null,null,9,17,6,8,8,null,74]"
        )

        # Check for multiple path match in enhanced
        result = json.numincrby(glide_sync_client, key, "$..key1", 1)
        assert result == b"[12,null,75]"

        # Check for non existent path in JSONPath
        result = json.numincrby(glide_sync_client, key, "$.key10", 51)
        assert result == b"[]"  # Expect Empty Array

        # Check for non existent key in JSONPath
        with pytest.raises(RequestError):
            json.numincrby(glide_sync_client, "non_existent_key", "$.key10", 51)

        # Check for Overflow in JSONPath
        with pytest.raises(RequestError):
            json.numincrby(glide_sync_client, key, "$.key9", 1.7976931348623157e308)

        # Decrement integer value (key1) by 12
        result = json.numincrby(glide_sync_client, key, "$.key1", -12)
        assert result == b"[0]"  # Expect 12 - 12 = 0

        # Decrement integer value (key1) by 0.5
        result = json.numincrby(glide_sync_client, key, "$.key1", -0.5)
        assert result == b"[-0.5]"  # Expect 0 - 0.5 = -0.5

        # Check 'null' value
        result = json.numincrby(glide_sync_client, key, "$.key7", 5)
        assert result == b"[null]"  # Expect 'null'

        # Test Legacy Path
        # Increment float value (key1) by 5 (integer)
        result = json.numincrby(glide_sync_client, key, "key1", 5)
        assert result == b"4.5"  # Expect -0.5 + 5 = 4.5

        # Decrement float value (key1) by 5.5 (integer)
        result = json.numincrby(glide_sync_client, key, "key1", -5.5)
        assert result == b"-1"  # Expect 4.5 - 5.5 = -1

        # Increment int value (key2) by 2.5 (a float number)
        result = json.numincrby(glide_sync_client, key, "key2", 2.5)
        assert result == b"13.5"  # Expect 11 + 2.5 = 13.5

        # Increment nested value (key3.nested_key.key1[0]) by 7
        result = json.numincrby(glide_sync_client, key, "key3.nested_key.key1[0]", 7)
        assert result == b"16"  # Expect 9 + 7 = 16

        # Increment array element (key4[1]) by 1
        result = json.numincrby(glide_sync_client, key, "key4[1]", 1)
        assert result == b"9"  # Expect 8 + 1 = 9

        # Increment a float value (key5) by 10.2 (a float number)
        result = json.numincrby(glide_sync_client, key, "key5", 10.2)
        assert result == b"25.43"  # Expect 15.23 + 10.2 = 25.43

        # Check for multiple path match in legacy and assure that the result of the last updated value is returned
        result = json.numincrby(glide_sync_client, key, "..key1", 1)
        assert result == b"76"

        # Check if the rest of the key1 path matches were updated and not only the last value
        result = json.get(glide_sync_client, key, "$..key1")  # type: ignore
        assert (
            result == b"[0,[16,17],76]"
        )  # First is 0 as 0 + 0 = 0, Second doesn't change as its an array type (non-numeric), third is 76 as 0 + 76 = 0

        # Check for non existent path in legacy
        with pytest.raises(RequestError):
            json.numincrby(glide_sync_client, key, ".key10", 51)

        # Check for non existent key in legacy
        with pytest.raises(RequestError):
            json.numincrby(glide_sync_client, "non_existent_key", ".key10", 51)

        # Check for Overflow in legacy
        with pytest.raises(RequestError):
            json.numincrby(glide_sync_client, key, ".key9", 1.7976931348623157e308)

        # Check 'null' value
        with pytest.raises(RequestError):
            json.numincrby(glide_sync_client, key, ".key7", 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_nummultby(self, glide_sync_client: TGlideClient):
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
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Test JSONPath
        # Multiply integer value (key1) by 5
        result = json.nummultby(glide_sync_client, key, "$.key1", 5)
        assert result == b"[5]"  # Expect 1 * 5 = 5

        # Multiply float value (key2) by 2.5
        result = json.nummultby(glide_sync_client, key, "$.key2", 2.5)
        assert result == b"[8.75]"  # Expect 3.5 * 2.5 = 8.75

        # Multiply nested object (key3.nested_key.key1[1]) by 7
        result = json.nummultby(glide_sync_client, key, "$.key3.nested_key.key1[1]", 7)
        assert result == b"[35]"  # Expect 5 * 7 = 35

        # Multiply array element (key4[1]) by 1
        result = json.nummultby(glide_sync_client, key, "$.key4[1]", 1)
        assert result == b"[2]"  # Expect 2 * 1 = 2

        # Multiply zero value (key5) by 10.23 (float number)
        result = json.nummultby(glide_sync_client, key, "$.key5", 10.23)
        assert result == b"[0]"  # Expect 0 * 10.23 = 0

        # Multiply a string value (key6) by a number
        result = json.nummultby(glide_sync_client, key, "$.key6", 99)
        assert result == b"[null]"  # Expect null

        # Multiply a None value (key7) by a number
        result = json.nummultby(glide_sync_client, key, "$.key7", 51)
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
        result = json.nummultby(glide_sync_client, key, "$..*", 5)
        assert (
            result
            == b"[25,43.75,null,null,0,null,null,null,1.7976931348623157e+308,null,null,20,175,5,10,15,null,345]"
        )

        # Check for multiple path matches in JSONPath
        # key1: 25 * 2 = 50
        # key8.nested_key.key1: 345 * 2 = 690
        result = json.nummultby(glide_sync_client, key, "$..key1", 2)
        assert result == b"[50,null,690]"  # After previous multiplications

        # Check for non-existent path in JSONPath
        result = json.nummultby(glide_sync_client, key, "$.key10", 51)
        assert result == b"[]"  # Expect Empty Array

        # Check for non-existent key in JSONPath
        with pytest.raises(RequestError):
            json.nummultby(glide_sync_client, "non_existent_key", "$.key10", 51)

        # Check for Overflow in JSONPath
        with pytest.raises(RequestError):
            json.nummultby(glide_sync_client, key, "$.key9", 1.7976931348623157e308)

        # Multiply integer value (key1) by -12
        result = json.nummultby(glide_sync_client, key, "$.key1", -12)
        assert result == b"[-600]"  # Expect 50 * -12 = -600

        # Multiply integer value (key1) by -0.5
        result = json.nummultby(glide_sync_client, key, "$.key1", -0.5)
        assert result == b"[300]"  # Expect -600 * -0.5 = 300

        # Test Legacy Path
        # Multiply int value (key1) by 5 (integer)
        result = json.nummultby(glide_sync_client, key, "key1", 5)
        assert result == b"1500"  # Expect 300 * 5 = -1500

        # Multiply int value (key1) by -5.5 (float number)
        result = json.nummultby(glide_sync_client, key, "key1", -5.5)
        assert result == b"-8250"  # Expect -150 * -5.5 = -8250

        # Multiply int float (key2) by 2.5 (a float number)
        result = json.nummultby(glide_sync_client, key, "key2", 2.5)
        assert result == b"109.375"  # Expect 43.75 * 2.5 = 109.375

        # Multiply nested value (key3.nested_key.key1[0]) by 7
        result = json.nummultby(glide_sync_client, key, "key3.nested_key.key1[0]", 7)
        assert result == b"140"  # Expect 20 * 7 = 140

        # Multiply array element (key4[1]) by 1
        result = json.nummultby(glide_sync_client, key, "key4[1]", 1)
        assert result == b"10"  # Expect 10 * 1 = 10

        # Multiply a float value (key5) by 10.2 (a float number)
        result = json.nummultby(glide_sync_client, key, "key5", 10.2)
        assert result == b"0"  # Expect 0 * 10.2 = 0

        # Check for multiple path matches in legacy and assure that the result of the last updated value is returned
        # last updated value is key8.nested_key.key1: 690 * 2 = 1380
        result = json.nummultby(glide_sync_client, key, "..key1", 2)
        assert result == b"1380"  # Expect the last updated key1 value multiplied by 2

        # Check if the rest of the key1 path matches were updated and not only the last value
        result = json.get(glide_sync_client, key, "$..key1")  # type: ignore
        assert result == b"[-16500,[140,175],1380]"

        # Check 'null' in legacy
        with pytest.raises(RequestError):
            json.nummultby(glide_sync_client, key, ".key7", 5)

        # Check for non-existent path in legacy
        with pytest.raises(RequestError):
            json.nummultby(glide_sync_client, key, ".key10", 51)

        # Check for non-existent key in legacy
        with pytest.raises(RequestError):
            json.nummultby(glide_sync_client, "non_existent_key", ".key10", 51)

        # Check for Overflow in legacy
        with pytest.raises(RequestError):
            json.nummultby(glide_sync_client, key, ".key9", 1.7976931348623157e308)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_strlen(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"a": "foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert json.strlen(glide_sync_client, key, "$..a") == [3, 5, None]
        assert json.strlen(glide_sync_client, key, "a") == 3

        assert json.strlen(glide_sync_client, key, "$.nested") == [None]
        with pytest.raises(RequestError):
            assert json.strlen(glide_sync_client, key, "nested")

        with pytest.raises(RequestError):
            assert json.strlen(glide_sync_client, key)

        assert json.strlen(glide_sync_client, key, "$.non_existing_path") == []
        with pytest.raises(RequestError):
            json.strlen(glide_sync_client, key, ".non_existing_path")

        assert json.strlen(glide_sync_client, "non_exiting_key", ".") is None
        assert json.strlen(glide_sync_client, "non_exiting_key", "$") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_strappend(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        json_value = {"a": "foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}
        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        assert json.strappend(glide_sync_client, key, '"bar"', "$..a") == [6, 8, None]
        assert json.strappend(glide_sync_client, key, OuterJson.dumps("foo"), "a") == 9

        json_str = json.get(glide_sync_client, key, ".")
        assert isinstance(json_str, bytes)
        assert OuterJson.loads(json_str) == {
            "a": "foobarfoo",
            "nested": {"a": "hellobar"},
            "nested2": {"a": 31},
        }

        assert json.strappend(
            glide_sync_client, key, OuterJson.dumps("bar"), "$.nested"
        ) == [None]

        with pytest.raises(RequestError):
            json.strappend(glide_sync_client, key, OuterJson.dumps("bar"), ".nested")

        with pytest.raises(RequestError):
            json.strappend(glide_sync_client, key, OuterJson.dumps("bar"))

        assert (
            json.strappend(
                glide_sync_client, key, OuterJson.dumps("try"), "$.non_existing_path"
            )
            == []
        )
        with pytest.raises(RequestError):
            json.strappend(
                glide_sync_client, key, OuterJson.dumps("try"), "non_existing_path"
            )

        with pytest.raises(RequestError):
            json.strappend(glide_sync_client, "non_exiting_key", OuterJson.dumps("try"))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @typing.no_type_check  # since this is a complex test, skip typing to be more effective
    def test_sync_json_arrinsert(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)

        assert (
            json.set(
                glide_sync_client,
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
        result = json.arrinsert(
            glide_sync_client,
            key,
            "$..a",
            0,
            ['"string_value"', "123", '{"key": "value"}', "true", "null", '["bar"]'],
        )
        assert result == [6, 10, None, 8, 7, None, None, 9]

        updated_doc = json.get(glide_sync_client, key)

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
        result = json.arrinsert(
            glide_sync_client,
            key,
            "$..a",
            2,
            ['"insert_at_2"'],
        )
        assert result == [7, 11, None, 9, 8, None, None, 10]

        # Check document after insertion at index 2
        updated_doc_at_2 = json.get(glide_sync_client, key)
        expected_doc["a"].insert(2, "insert_at_2")
        expected_doc["b"]["a"].insert(2, "insert_at_2")
        expected_doc["d"][0]["a"].insert(2, "insert_at_2")
        expected_doc["d"][1]["a"].insert(2, "insert_at_2")
        expected_doc["f"]["a"].insert(2, "insert_at_2")
        assert OuterJson.loads(updated_doc_at_2) == expected_doc

        # Insert with a legacy path
        result = json.arrinsert(
            glide_sync_client,
            key,
            "..a",  # legacy path
            0,
            ['"legacy_value"'],
        )
        assert (
            result == 8
        )  # Returns length of the first modified array (in this case, 'a')

        # Check document after insertion at root legacy path (all matching arrays should be updated)
        updated_doc_legacy = json.get(glide_sync_client, key)

        # Update `expected_doc` with the new value inserted at index 0 of all matching arrays
        expected_doc["a"].insert(0, "legacy_value")
        expected_doc["b"]["a"].insert(0, "legacy_value")
        expected_doc["d"][0]["a"].insert(0, "legacy_value")
        expected_doc["d"][1]["a"].insert(0, "legacy_value")
        expected_doc["f"]["a"].insert(0, "legacy_value")

        assert OuterJson.loads(updated_doc_legacy) == expected_doc

        # Insert with an index out of range for some arrays
        with pytest.raises(RequestError):
            json.arrinsert(
                glide_sync_client,
                key,
                "$..a",
                10,  # Index out of range for some paths but valid for others
                ['"out_of_range_value"'],
            )

        with pytest.raises(RequestError):
            json.arrinsert(
                glide_sync_client,
                key,
                "..a",
                10,  # Index out of range for some paths but valid for others
                ['"out_of_range_value"'],
            )

        # Negative index insertion (should insert from the end of the array)
        result = json.arrinsert(
            glide_sync_client,
            key,
            "$..a",
            -1,
            ['"negative_index_value"'],
        )
        assert result == [9, 13, None, 11, 10, None, None, 12]  # Update valid paths

        # Check document after negative index insertion
        updated_doc_negative = json.get(glide_sync_client, key)
        expected_doc["a"].insert(-1, "negative_index_value")
        expected_doc["b"]["a"].insert(-1, "negative_index_value")
        expected_doc["d"][0]["a"].insert(-1, "negative_index_value")
        expected_doc["d"][1]["a"].insert(-1, "negative_index_value")
        expected_doc["f"]["a"].insert(-1, "negative_index_value")
        assert OuterJson.loads(updated_doc_negative) == expected_doc

        # Non-existing path
        with pytest.raises(RequestError):
            json.arrinsert(glide_sync_client, key, ".path", 5, ['"value"'])

        json.arrinsert(glide_sync_client, key, "$.path", 5, ['"value"']) == []

        # Key doesnt exist
        with pytest.raises(RequestError):
            json.arrinsert(glide_sync_client, "non_existent_key", "$", 5, ['"value"'])

        with pytest.raises(RequestError):
            json.arrinsert(glide_sync_client, "non_existent_key", ".", 5, ['"value"'])

        # value at path is not an array
        with pytest.raises(RequestError):
            json.arrinsert(glide_sync_client, key, ".e", 5, ['"value"'])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_debug_fields(self, glide_sync_client: TGlideClient):
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

        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        # Test JSONPath - Fields Subcommand
        # Test integer
        result = json.debug_fields(glide_sync_client, key, "$.key1")
        assert result == [1]

        # Test float
        result = json.debug_fields(glide_sync_client, key, "$.key2")
        assert result == [1]

        # Test Nested Value
        result = json.debug_fields(glide_sync_client, key, "$.key3")
        assert result == [4]

        result = json.debug_fields(glide_sync_client, key, "$.key3.nested_key.key1")
        assert result == [2]

        # Test Array
        result = json.debug_fields(glide_sync_client, key, "$.key4[2]")
        assert result == [1]

        # Test String
        result = json.debug_fields(glide_sync_client, key, "$.key6")
        assert result == [1]

        # Test Null
        result = json.debug_fields(glide_sync_client, key, "$.key7")
        assert result == [1]

        # Test Bool
        result = json.debug_fields(glide_sync_client, key, "$.key10")
        assert result == [1]

        # Test all keys
        result = json.debug_fields(glide_sync_client, key, "$[*]")
        assert result == [1, 1, 4, 3, 1, 1, 1, 2, 1, 1]

        # Test multiple paths
        result = json.debug_fields(glide_sync_client, key, "$..key1")
        assert result == [1, 2, 1]

        # Test for non-existent path
        result = json.debug_fields(glide_sync_client, key, "$.key11")
        assert result == []

        # Test for non-existent key
        result = json.debug_fields(glide_sync_client, "non_existent_key", "$.key10")
        assert result is None

        # Test no provided path
        # Total Fields (19) - breakdown:
        # Top-Level Fields: 10
        # Fields within key3: 4 ($.key3, $.key3.nested_key, $.key3.nested_key.key1, $.key3.nested_key.key1)
        # Fields within key4: 3 ($.key4[0], $.key4[1], $.key4[2])
        # Fields within key8: 2 ($.key8, $.key8.nested_key)
        result = json.debug_fields(glide_sync_client, key)
        assert result == 19

        # Test legacy path - Fields Subcommand
        # Test integer
        result = json.debug_fields(glide_sync_client, key, ".key1")
        assert result == 1

        # Test float
        result = json.debug_fields(glide_sync_client, key, ".key2")
        assert result == 1

        # Test Nested Value
        result = json.debug_fields(glide_sync_client, key, ".key3")
        assert result == 4

        result = json.debug_fields(glide_sync_client, key, ".key3.nested_key.key1")
        assert result == 2

        # Test Array
        result = json.debug_fields(glide_sync_client, key, ".key4[2]")
        assert result == 1

        # Test String
        result = json.debug_fields(glide_sync_client, key, ".key6")
        assert result == 1

        # Test Null
        result = json.debug_fields(glide_sync_client, key, ".key7")
        assert result == 1

        # Test Bool
        result = json.debug_fields(glide_sync_client, key, ".key10")
        assert result == 1

        # Test multiple paths
        result = json.debug_fields(glide_sync_client, key, "..key1")
        assert result == 1  # Returns number of fields of the first JSON value

        # Test for non-existent path
        with pytest.raises(RequestError):
            json.debug_fields(glide_sync_client, key, ".key11")

        # Test for non-existent key
        result = json.debug_fields(glide_sync_client, "non_existent_key", ".key10")
        assert result is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_debug_memory(self, glide_sync_client: TGlideClient):
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

        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK
        # Test JSONPath - Memory Subcommand
        # Test integer
        result = json.debug_memory(glide_sync_client, key, "$.key1")
        assert result == [16]
        # Test float
        result = json.debug_memory(glide_sync_client, key, "$.key2")
        assert result == [16]
        # Test Nested Value
        result = json.debug_memory(glide_sync_client, key, "$.key3.nested_key.key1[0]")
        assert result == [16]
        # Test Array
        result = json.debug_memory(glide_sync_client, key, "$.key4")
        assert result == [16 * 4]

        result = json.debug_memory(glide_sync_client, key, "$.key4[2]")
        assert result == [16]
        # Test String
        result = json.debug_memory(glide_sync_client, key, "$.key6")
        assert result == [16]
        # Test Null
        result = json.debug_memory(glide_sync_client, key, "$.key7")
        assert result == [16]
        # Test Bool
        result = json.debug_memory(glide_sync_client, key, "$.key10")
        assert result == [16]
        # Test all keys
        result = json.debug_memory(glide_sync_client, key, "$[*]")
        assert result == [16, 16, 110, 64, 16, 16, 16, 101, 39, 16]
        # Test multiple paths
        result = json.debug_memory(glide_sync_client, key, "$..key1")
        assert result == [16, 48, 39]
        # Test for non-existent path
        result = json.debug_memory(glide_sync_client, key, "$.key11")
        assert result == []
        # Test for non-existent key
        result = json.debug_memory(glide_sync_client, "non_existent_key", "$.key10")
        assert result is None
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
        result = json.debug_memory(glide_sync_client, key)
        assert result == 504
        # Test Legacy Path - Memory Subcommand
        # Test integer
        result = json.debug_memory(glide_sync_client, key, ".key1")
        assert result == 16
        # Test float
        result = json.debug_memory(glide_sync_client, key, ".key2")
        assert result == 16
        # Test Nested Value
        result = json.debug_memory(glide_sync_client, key, ".key3.nested_key.key1[0]")
        assert result == 16
        # Test Array
        result = json.debug_memory(glide_sync_client, key, ".key4[2]")
        assert result == 16
        # Test String
        result = json.debug_memory(glide_sync_client, key, ".key6")
        assert result == 16
        # Test Null
        result = json.debug_memory(glide_sync_client, key, ".key7")
        assert result == 16
        # Test Bool
        result = json.debug_memory(glide_sync_client, key, ".key10")
        assert result == 16
        # Test multiple paths
        result = json.debug_memory(glide_sync_client, key, "..key1")
        assert result == 16  # Returns the memory usage of the first JSON value
        # Test for non-existent path
        with pytest.raises(RequestError):
            json.debug_memory(glide_sync_client, key, ".key11")
        # Test for non-existent key
        result = json.debug_memory(glide_sync_client, "non_existent_key", ".key10")
        assert result is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @typing.no_type_check
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_arrtrim(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        # Test with enhanced path syntax
        json_value = '{"a": [0, 1, 2, 3, 4, 5, 6, 7, 8], "b": {"a": [0, 9, 10, 11, 12, 13], "c": {"a": 42}}}'
        assert json.set(glide_sync_client, key, "$", json_value) == OK

        # Basic trim
        assert json.arrtrim(glide_sync_client, key, "$..a", 1, 7) == [7, 5, None]
        assert OuterJson.loads(json.get(glide_sync_client, key, "$..a")) == [
            [1, 2, 3, 4, 5, 6, 7],
            [9, 10, 11, 12, 13],
            42,
        ]

        # Test negative start (should be treated as 0)
        assert json.arrtrim(glide_sync_client, key, "$.a", -1, 5) == [6]
        assert OuterJson.loads(json.get(glide_sync_client, key, "$.a")) == [
            [1, 2, 3, 4, 5, 6]
        ]
        assert json.arrtrim(glide_sync_client, key, ".a", -1, 5) == 6
        assert OuterJson.loads(json.get(glide_sync_client, key, ".a")) == [
            1,
            2,
            3,
            4,
            5,
            6,
        ]

        # Test end >= size (should be treated as size-1)
        assert json.arrtrim(glide_sync_client, key, "$.a", 0, 10) == [6]
        assert OuterJson.loads(json.get(glide_sync_client, key, "$.a")) == [
            [1, 2, 3, 4, 5, 6]
        ]

        assert json.arrtrim(glide_sync_client, key, ".a", 0, 10) == 6
        assert OuterJson.loads(json.get(glide_sync_client, key, ".a")) == [
            1,
            2,
            3,
            4,
            5,
            6,
        ]

        # Test start >= size (should empty the array)
        assert json.arrtrim(glide_sync_client, key, "$.a", 7, 10) == [0]
        assert OuterJson.loads(json.get(glide_sync_client, key, "$.a")) == [[]]

        assert json.set(glide_sync_client, key, ".a", '["a", "b", "c"]') == OK
        assert json.arrtrim(glide_sync_client, key, ".a", 7, 10) == 0
        assert OuterJson.loads(json.get(glide_sync_client, key, ".a")) == []

        # Test start > end (should empty the array)
        assert json.arrtrim(glide_sync_client, key, "$..a", 2, 1) == [0, 0, None]
        assert OuterJson.loads(json.get(glide_sync_client, key, "$..a")) == [
            [],
            [],
            42,
        ]
        assert json.set(glide_sync_client, key, "..a", '["a", "b", "c", "d"]') == OK
        assert json.arrtrim(glide_sync_client, key, "..a", 2, 1) == 0
        assert OuterJson.loads(json.get(glide_sync_client, key, ".a")) == []

        # Multiple path match
        assert json.set(glide_sync_client, key, "$", json_value) == OK
        assert json.arrtrim(glide_sync_client, key, "..a", 1, 10) == 8
        assert OuterJson.loads(json.get(glide_sync_client, key, "$..a")) == [
            [1, 2, 3, 4, 5, 6, 7, 8],
            [9, 10, 11, 12, 13],
            42,
        ]

        # Test with non-existent path
        with pytest.raises(RequestError):
            json.arrtrim(glide_sync_client, key, ".non_existent", 0, 1)

        assert json.arrtrim(glide_sync_client, key, "$.non_existent", 0, 1) == []

        # Test with non-array path
        assert json.arrtrim(glide_sync_client, key, "$", 0, 1) == [None]

        with pytest.raises(RequestError):
            json.arrtrim(glide_sync_client, key, ".", 0, 1)

        # Test with non-existent key
        with pytest.raises(RequestError):
            json.arrtrim(glide_sync_client, "non_existent_key", "$", 0, 1)

        # Test with non-existent key
        with pytest.raises(RequestError):
            json.arrtrim(glide_sync_client, "non_existent_key", ".", 0, 1)

        # Test empty array
        assert json.set(glide_sync_client, key, "$.empty", "[]") == OK
        assert json.arrtrim(glide_sync_client, key, "$.empty", 0, 1) == [0]
        assert json.arrtrim(glide_sync_client, key, ".empty", 0, 1) == 0
        assert OuterJson.loads(json.get(glide_sync_client, key, "$.empty")) == [[]]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_arrindex(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)

        json_value = {
            "empty_array": [],
            "single_element": ["apple"],
            "multiple_elements": ["banana", "cherry", "date"],
            "nested_arrays": [
                ["alpha"],
                ["beta", "gamma"],
                ["delta", "epsilon", "zeta"],
            ],
            "mixed_types": [1, "two", True, None, 5.5],
            "not_array": 5,
            "nested_arrays2": [
                ["a"],
                ["ab", "abc"],
                ["abcd", "abcde", "abcdef", "abcdefg", "abcdefgh", 1, 2, None, "gamma"],
            ],
            "nested_structure": {
                "level1": {
                    "level2": {
                        "level3": [
                            ["gamma", "theta"],
                            ["iota", "kappa", "gamma"],
                        ]
                    }
                }
            },
        }

        assert json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == OK

        # JSONPath Syntax Tests
        # Search for "beta" in all arrays at the root level, Non-array values return null
        result = json.arrindex(glide_sync_client, key, "$[*]", '"beta"')
        assert result == [-1, -1, -1, -1, -1, None, -1, None]

        # Search for a boolean
        result = json.arrindex(glide_sync_client, key, "$.mixed_types", "true")
        assert result == [2]  # True found at index 2 in the "mixed_types" array

        # Search for a float
        result = json.arrindex(glide_sync_client, key, "$.mixed_types", "5.5")
        assert result == [4]  # 5.5 found at index 4 in the "mixed_types" array

        # Search for "gamma" at nested level
        result = json.arrindex(glide_sync_client, key, "$.nested_arrays[*]", '"gamma"')
        assert result == [-1, 1, -1]  # "gamma" found at index 1 in the second array

        # Search for "gamma" at nested level with a specified range
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays2[*]",
            '"gamma"',
            JsonArrIndexOptions(start=0, end=5),
        )
        assert result == [-1, -1, -1]

        # Search for "gamma" at nested level with start > end
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays[*]",
            '"gamma"',
            JsonArrIndexOptions(start=2, end=1),
        )
        assert result == [-1, -1, -1]  # Invalid range, returns -1 for all

        # Search for "omega" which does not exist
        result = json.arrindex(glide_sync_client, key, "$[*]", '"omega"')
        assert result == [-1, -1, -1, -1, -1, None, -1, None]  # "omega" not found

        # Search for null values, null found at at third index in the fifth array
        result = json.arrindex(glide_sync_client, key, "$[*]", "null")
        assert result == [-1, -1, -1, -1, 3, None, -1, None]

        # Search in mixed types, "two" found at first index in the fifth array
        result = json.arrindex(glide_sync_client, key, "$[*]", '"two"')
        assert result == [-1, -1, -1, -1, 1, None, -1, None]

        # Out of range check for "start" value
        result = json.arrindex(
            glide_sync_client, key, "$[*]", '"apple"', JsonArrIndexOptions(start=-200)
        )
        assert result == [
            -1,
            0,
            -1,
            -1,
            -1,
            None,
            -1,
            None,
        ]  # Rounded to the array's start

        # Check for end = -1, tests if the function includes the last element, found "gamma" at index 8 at the third array
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays2[*]",
            '"gamma"',
            JsonArrIndexOptions(start=0, end=-1),
        )
        assert result == [-1, -1, 8]

        # Check for non-existent key
        with pytest.raises(RequestError):
            json.arrindex(
                glide_sync_client,
                "Non_existent",
                "$.nested_arrays2[*]",
                '"abcdefg"',
                JsonArrIndexOptions(start=0, end=-1),
            )

        # Check for non-existent path
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays3[*]",
            '"abcdefg"',
            JsonArrIndexOptions(start=0, end=-1),
        )
        assert result == []

        # Using JSONPath syntax to search for "gamma" in nested_structure.level1.level2.level3
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_structure.level1.level2.level3[*]",
            '"gamma"',
        )
        assert result == [
            0,
            2,
        ]  # "gamma" at index 0 in first array, index 2 in second array

        # Check for inclusive behavior of start in JSONPath syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_structure.level1.level2.level3[*]",
            '"gamma"',
            JsonArrIndexOptions(start=0),
        )
        assert result == [
            0,
            2,
        ]  # "gamma" at index 0 of level3[0] and index 2 of level3[1].

        # Check for exclusive behavior of end in JSONPath syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_structure.level1.level2.level3[*]",
            '"gamma"',
            JsonArrIndexOptions(start=0, end=2),
        )
        assert result == [
            0,
            -1,
        ]  # Only "gamma" at index 0 of level3[0] is found; gamma at index 2 of level3[1] is excluded as its not within the
        # search range.

        # Check for passing start = 0, end = 0 in JSONPath syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=0, end=0),
        )
        assert result == [2]  # "zeta" found at index 2 as the whole range was searched

        # Check for passing start = 1, end = 0 (start>end) but end is a "special value" in JSONPath syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=1, end=0),
        )
        assert result == [2]  # "zeta" found at index 2 as the whole range was searched

        # Check for passing start = 1, end = -1 (start>end) but end is a "special value" in JSONPath syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            "$.nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=1, end=-1),
        )
        assert result == [2]  # "zeta" found at index 2 as the whole range was searched

        # Restricted Path Syntax Tests
        # Search for "abcd" in the "nested_arrays2" array
        result = json.arrindex(glide_sync_client, key, ".nested_arrays2[2]", '"abcd"')
        assert result == 0  # "abcd" found at index 0

        # Search for "abcd" in the "nested_arrays2" array with specified range
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays2[2]",
            '"abcd"',
            JsonArrIndexOptions(start=1, end=4),
        )
        assert result == -1  # "abcd" not found at the specified range

        # Search for "abcdefg" in the "nested_arrays2" with start > end
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays2[2]",
            '"abcdefg"',
            JsonArrIndexOptions(start=4, end=3),
        )
        assert result == -1

        # Search for "theta" which does not exist
        result = json.arrindex(glide_sync_client, key, ".multiple_elements", '"theta"')
        assert result == -1  # "theta" not found

        # Check for non_existent path
        with pytest.raises(RequestError):
            json.arrindex(glide_sync_client, key, ".non_existent", '"value"')

        # Search in an empty array
        result = json.arrindex(glide_sync_client, key, ".empty_array", '"anything"')
        assert result == -1  # Nothing to find in empty array

        # Search for a boolean
        result = json.arrindex(glide_sync_client, key, ".mixed_types", "true")
        assert result == 2  # True found at index 2

        # Search for a float
        result = json.arrindex(glide_sync_client, key, ".mixed_types", "5.5")
        assert result == 4  # 5.5 found at index 4

        # Search for null value
        result = json.arrindex(glide_sync_client, key, ".mixed_types", "null")
        assert result == 3  # null found at index 3

        # Out of range check for "start" value
        result = json.arrindex(
            glide_sync_client,
            key,
            ".single_element",
            '"apple"',
            JsonArrIndexOptions(start=-200),
        )
        assert result == 0  # Rounded to the array's start

        # Check for end = -1, tests if the function includes the last element
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays2[2]",
            '"gamma"',
            JsonArrIndexOptions(start=0, end=-1),
        )
        assert result == 8

        # Check for non-existent key
        with pytest.raises(RequestError):
            json.arrindex(
                glide_sync_client, "Non_existent", ".nested_arrays2[1]", '"abcdefg"'
            )

        # Check for value at path is not an array
        with pytest.raises(RequestError):
            json.arrindex(glide_sync_client, key, ".not_array", "val")

        # Using legacy syntax to search for "gamma" in nested_structure
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_structure.level1.level2.level3[*]",
            '"gamma"',
        )
        assert result == 0  # Legacy syntax returns index from first matching array

        # Check for inclusive behavior of start in legacy syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays[2]",
            '"epsilon"',
            JsonArrIndexOptions(start=1),
        )
        assert result == 1  # "epsilon" found at index 1 in nested_arrays[2].

        # Check for exclusive behavior of end in legacy syntax
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=1, end=2),
        )
        assert result == -1  # "zeta" at index 2 is excluded due to exclusive end.

        # Check for passing start = 0, end = 0
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=0, end=0),
        )
        assert result == 2  # "zeta" found at index 2 as the whole range was searched

        # Check for passing start = 1, end = 0 (start>end) but end is a "special value"
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=1, end=0),
        )
        assert result == 2  # "zeta" found at index 2 as the whole range was searched

        # Check for passing start = 1, end = -1 (start>end) but end is a "special value"
        result = json.arrindex(
            glide_sync_client,
            key,
            ".nested_arrays[2]",
            '"zeta"',
            JsonArrIndexOptions(start=1, end=-1),
        )
        assert result == 2  # "zeta" found at index 2 as the whole range was searched

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_arrappend(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        initial_json_value = '{"a": 1, "b": ["one", "two"]}'
        assert json.set(glide_sync_client, key, "$", initial_json_value) == OK

        assert json.arrappend(glide_sync_client, key, "$.b", ['"three"']) == [3]
        assert json.arrappend(glide_sync_client, key, ".b", ['"four"', '"five"']) == 5

        result = json.get(glide_sync_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [
            {"a": 1, "b": ["one", "two", "three", "four", "five"]}
        ]

        assert json.arrappend(glide_sync_client, key, "$.a", ['"value"']) == [None]

        # JSONPath, path doesnt exist
        assert json.arrappend(glide_sync_client, key, "$.c", ['"value"']) == []
        # Legacy path, `path` doesnt exist
        with pytest.raises(RequestError):
            json.arrappend(glide_sync_client, key, ".c", ['"value"'])

        # Legacy path, the JSON value at `path` is not a array
        with pytest.raises(RequestError):
            json.arrappend(glide_sync_client, key, ".a", ['"value"'])

        with pytest.raises(RequestError):
            json.arrappend(glide_sync_client, "non_existing_key", "$.b", ['"six"'])
        with pytest.raises(RequestError):
            json.arrappend(glide_sync_client, "non_existing_key", ".b", ['"six"'])

        # multiple path match
        json_value = '[[], ["a"], ["a", "b"]]'
        assert json.set(glide_sync_client, key, "$", json_value) == OK
        assert json.arrappend(glide_sync_client, key, "[*]", ['"c"']) == 1
        result = json.get(glide_sync_client, key, "$")
        assert isinstance(result, bytes)
        assert OuterJson.loads(result) == [[["c"], ["a", "c"], ["a", "b", "c"]]]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_resp(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)

        # Generate random JSON content with specified types
        json_value = {
            "obj": {"a": get_random_value("int"), "b": get_random_value("float")},
            "arr": [get_random_value("int") for _ in range(3)],
            "str": get_random_value("str"),
            "bool": get_random_value("bool"),
            "int": get_random_value("int"),
            "float": get_random_value("float"),
            "nullVal": get_random_value("null"),
        }

        json_value_expected = copy.deepcopy(json_value)
        json_value_expected["obj"]["b"] = str(json_value["obj"]["b"]).encode()
        json_value_expected["float"] = str(json_value["float"]).encode()
        json_value_expected["str"] = str(json_value["str"]).encode()
        json_value_expected["bool"] = str(json_value["bool"]).lower().encode()
        assert (
            json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == "OK"
        )

        assert json.resp(glide_sync_client, key, "$.*") == [
            [
                b"{",
                [b"a", json_value_expected["obj"]["a"]],
                [b"b", json_value_expected["obj"]["b"]],
            ],
            [b"[", *json_value_expected["arr"]],
            json_value_expected["str"],
            json_value_expected["bool"],
            json_value_expected["int"],
            json_value_expected["float"],
            json_value_expected["nullVal"],
        ]

        # multiple path match, the first will be returned
        assert json.resp(glide_sync_client, key, "*") == [
            b"{",
            [b"a", json_value_expected["obj"]["a"]],
            [b"b", json_value_expected["obj"]["b"]],
        ]

        assert json.resp(glide_sync_client, key, "$") == [
            [
                b"{",
                [
                    b"obj",
                    [
                        b"{",
                        [b"a", json_value_expected["obj"]["a"]],
                        [b"b", json_value_expected["obj"]["b"]],
                    ],
                ],
                [b"arr", [b"[", *json_value_expected["arr"]]],
                [
                    b"str",
                    json_value_expected["str"],
                ],
                [
                    b"bool",
                    json_value_expected["bool"],
                ],
                [b"int", json_value["int"]],
                [
                    b"float",
                    json_value_expected["float"],
                ],
                [b"nullVal", json_value["nullVal"]],
            ],
        ]

        assert json.resp(glide_sync_client, key, "$.str") == [
            json_value_expected["str"]
        ]
        assert json.resp(glide_sync_client, key, ".str") == json_value_expected["str"]

        # Further tests with a new random JSON structure
        json_value = {
            "a": [random.randint(1, 10) for _ in range(3)],
            "b": {
                "a": [random.randint(1, 10) for _ in range(2)],
                "c": {"a": random.randint(1, 10)},
            },
        }
        assert (
            json.set(glide_sync_client, key, "$", OuterJson.dumps(json_value)) == "OK"
        )

        # Multiple path match
        assert json.resp(glide_sync_client, key, "$..a") == [
            [b"[", *json_value["a"]],
            [b"[", *json_value["b"]["a"]],
            json_value["b"]["c"]["a"],
        ]

        assert json.resp(glide_sync_client, key, "..a") == [b"[", *json_value["a"]]

        # Test for non-existent paths
        assert json.resp(glide_sync_client, key, "$.nonexistent") == []
        with pytest.raises(RequestError):
            json.resp(glide_sync_client, key, "nonexistent")

        # Test for non-existent key
        assert json.resp(glide_sync_client, "nonexistent_key", "$") is None
        assert json.resp(glide_sync_client, "nonexistent_key", ".") is None
        assert json.resp(glide_sync_client, "nonexistent_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_arrpop(self, glide_sync_client: TGlideClient):
        key = get_random_string(5)
        key2 = get_random_string(5)

        json_value = '{"a": [1, 2, true], "b": {"a": [3, 4, ["value", 3, false] ,5], "c": {"a": 42}}}'
        assert json.set(glide_sync_client, key, "$", json_value) == OK

        assert json.arrpop(
            glide_sync_client, key, JsonArrPopOptions(path="$.a", index=1)
        ) == [b"2"]
        assert (
            json.arrpop(glide_sync_client, key, JsonArrPopOptions(path="$..a"))
        ) == [b"true", b"5", None]

        assert (
            json.arrpop(glide_sync_client, key, JsonArrPopOptions(path="..a")) == b"1"
        )
        # Even if only one array element was returned, ensure second array at `..a` was popped
        assert json.get(glide_sync_client, key, "$..a") == b"[[],[3,4],42]"

        # Out of index
        assert json.arrpop(
            glide_sync_client, key, JsonArrPopOptions(path="$..a", index=10)
        ) == [None, b"4", None]

        assert (
            json.arrpop(
                glide_sync_client, key, JsonArrPopOptions(path="..a", index=-10)
            )
            == b"3"
        )

        # Path is not an array
        assert json.arrpop(glide_sync_client, key, JsonArrPopOptions(path="$")) == [
            None
        ]
        with pytest.raises(RequestError):
            assert json.arrpop(glide_sync_client, key, JsonArrPopOptions(path="."))
        with pytest.raises(RequestError):
            assert json.arrpop(glide_sync_client, key)

        # Non existing path
        assert (
            json.arrpop(
                glide_sync_client, key, JsonArrPopOptions(path="$.non_existing_path")
            )
            == []
        )
        with pytest.raises(RequestError):
            assert json.arrpop(
                glide_sync_client, key, JsonArrPopOptions(path="non_existing_path")
            )

        # Non existing key
        with pytest.raises(RequestError):
            json.arrpop(
                glide_sync_client, "non_existing_key", JsonArrPopOptions(path="$.a")
            )
        with pytest.raises(RequestError):
            json.arrpop(
                glide_sync_client, "non_existing_key", JsonArrPopOptions(path=".a")
            )

        assert (
            json.set(glide_sync_client, key2, "$", '[[], ["a"], ["a", "b", "c"]]') == OK
        )
        assert (
            json.arrpop(glide_sync_client, key2, JsonArrPopOptions(path=".", index=-1))
            == b'["a","b","c"]'
        )
        assert json.arrpop(glide_sync_client, key2) == b'["a"]'

        # pop from an empty array
        assert json.arrpop(glide_sync_client, key2, JsonArrPopOptions("$[0]")) == [None]
        assert json.arrpop(glide_sync_client, key2, JsonArrPopOptions("$[0]", 10)) == [
            None
        ]
        assert json.arrpop(glide_sync_client, key2, JsonArrPopOptions("[0]")) is None
        assert (
            json.arrpop(glide_sync_client, key2, JsonArrPopOptions("[0]", 10)) is None
        )

        # non jsonpath pops from all matching paths, even if one result is being returned
        assert (
            json.set(
                glide_sync_client, key2, "$", '[[], ["a"], ["a", "b"], ["a", "b", "c"]]'
            )
            == OK
        )

        assert json.arrpop(glide_sync_client, key2, JsonArrPopOptions("[*]")) == b'"a"'
        assert json.get(glide_sync_client, key2, ".") == b'[[],[],["a"],["a","b"]]'

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_batch_array(
        self, glide_sync_client: GlideClusterClient, is_atomic: bool
    ):
        transaction = ClusterBatch(is_atomic=is_atomic)

        key = get_random_string(5)
        json_value1 = {"a": 1.0, "b": 2}
        json_value2 = {"a": 1.0, "b": [1, 2]}

        # Test 'set', 'get', and 'clear' commands
        json_batch.set(transaction, key, "$", OuterJson.dumps(json_value1))
        json_batch.clear(transaction, key, "$")
        json_batch.set(transaction, key, "$", OuterJson.dumps(json_value1))
        json_batch.get(transaction, key, ".")

        # Test array related commands
        json_batch.set(transaction, key, "$", OuterJson.dumps(json_value2))
        json_batch.arrappend(transaction, key, "$.b", ["3", "4"])
        json_batch.arrindex(transaction, key, "$.b", "2")
        json_batch.arrinsert(transaction, key, "$.b", 2, ["5"])
        json_batch.arrlen(transaction, key, "$.b")
        json_batch.arrpop(transaction, key, JsonArrPopOptions(path="$.b", index=2))
        json_batch.arrtrim(transaction, key, "$.b", 1, 2)
        json_batch.get(transaction, key, ".")

        result = glide_sync_client.exec(transaction, raise_on_error=False)
        assert isinstance(result, list)

        assert result[0] == "OK"  # set
        assert result[1] == 1  # clear
        assert result[2] == "OK"  # set
        assert isinstance(result[3], bytes)
        assert OuterJson.loads(result[3]) == json_value1  # get

        assert result[4] == "OK"  # set
        assert result[5] == [4]  # arrappend
        assert result[6] == [1]  # arrindex
        assert result[7] == [5]  # arrinsert
        assert result[8] == [5]  # arrlen
        assert result[9] == [b"5"]  # arrpop
        assert result[10] == [2]  # arrtrim
        assert isinstance(result[11], bytes)
        assert OuterJson.loads(result[11]) == {"a": 1.0, "b": [2, 3]}  # get

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_json_batch(
        self, glide_sync_client: GlideClusterClient, is_atomic: bool
    ):
        transaction = ClusterBatch(is_atomic=is_atomic)

        key = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        key3 = f"{{key}}-3{get_random_string(5)}"
        json_value = {"a": [1, 2], "b": [3, 4], "c": "c", "d": True}

        json_batch.set(transaction, key, "$", OuterJson.dumps(json_value))

        # Test debug commands
        json_batch.debug_memory(transaction, key, "$.a")
        json_batch.debug_fields(transaction, key, "$.a")

        # Test obj commands
        json_batch.objlen(transaction, key, ".")
        json_batch.objkeys(transaction, key, ".")

        # Test num commands
        json_batch.numincrby(transaction, key, "$.a[*]", 10.0)
        json_batch.nummultby(transaction, key, "$.a[*]", 10.0)

        # Test str commands
        json_batch.strappend(transaction, key, '"-test"', "$.c")
        json_batch.strlen(transaction, key, "$.c")

        # Test type command
        json_batch.type(transaction, key, "$.a")

        # Test mget command
        json_value2 = {"b": [3, 4], "c": "c", "d": True}
        json_batch.set(transaction, key2, "$", OuterJson.dumps(json_value2))
        json_batch.mget(transaction, [key, key2, key3], "$.a")

        # Test toggle command
        json_batch.toggle(transaction, key, "$.d")

        # Test resp command
        json_batch.resp(transaction, key, "$")

        # Test del command
        json_batch.delete(transaction, key, "$.d")

        # Test forget command
        json_batch.forget(transaction, key, "$.c")

        result = glide_sync_client.exec(transaction, raise_on_error=False)
        assert isinstance(result, list)

        assert result[0] == "OK"  # set
        assert result[1] == [48]  # debug_memory
        assert result[2] == [2]  # debug_field

        assert result[3] == 4  # objlen
        assert result[4] == [b"a", b"b", b"c", b"d"]  # objkeys
        assert result[5] == b"[11,12]"  # numincrby
        assert result[6] == b"[110,120]"  # nummultby
        assert result[7] == [6]  # strappend
        assert result[8] == [6]  # strlen
        assert result[9] == [b"array"]  # type
        assert result[10] == "OK"  # set
        assert result[11] == [b"[[110,120]]", b"[]", None]  # mget
        assert result[12] == [False]  # toggle

        assert result[13] == [
            [
                b"{",
                [b"a", [b"[", 110, 120]],
                [b"b", [b"[", 3, 4]],
                [b"c", b"c-test"],
                [b"d", b"false"],
            ]
        ]  # resp

        assert result[14] == 1  # del
        assert result[15] == 1  # forget
