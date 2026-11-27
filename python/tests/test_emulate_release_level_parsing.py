# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from tests.utils.ft_test_helpers import parse_emulate_release_level


def test_parses_dict_reply():
    assert parse_emulate_release_level({"search.emulate-release": "1.3.0"}) == "1.3.0"


def test_parses_dict_reply_with_bytes():
    assert parse_emulate_release_level({b"search.emulate-release": b"1.0.0"}) == "1.0.0"


def test_parses_flat_list_reply():
    assert parse_emulate_release_level(["search.emulate-release", "1.3.0"]) == "1.3.0"


def test_parses_flat_list_reply_with_bytes():
    assert parse_emulate_release_level([b"search.emulate-release", b"1.0.0"]) == "1.0.0"


def test_empty_dict_reply_returns_none():
    assert parse_emulate_release_level({}) is None


def test_empty_list_reply_returns_none():
    assert parse_emulate_release_level([]) is None


def test_dict_reply_missing_key_returns_none():
    assert parse_emulate_release_level({"some.other.key": "value"}) is None


def test_scalar_reply_returns_none():
    assert parse_emulate_release_level(b"1.3.0") is None
    assert parse_emulate_release_level("1.3.0") is None
    assert parse_emulate_release_level(None) is None


def test_odd_length_flat_list_drops_dangling_key():
    assert parse_emulate_release_level(["search.emulate-release"]) is None


def test_tuple_reply_is_accepted_like_a_list():
    assert parse_emulate_release_level(("search.emulate-release", "1.3.0")) == "1.3.0"
