# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Unit coverage for `_parse_emulate_release_level`: the dict and
flat-list `CONFIG GET` reply shapes, and the empty/missing-key reply from a
pre-1.3 module where `search.emulate-release` doesn't exist."""

from tests.conftest import _parse_emulate_release_level


def test_parses_dict_reply():
    assert _parse_emulate_release_level({"search.emulate-release": "1.3.0"}) == "1.3.0"


def test_parses_dict_reply_with_bytes():
    assert (
        _parse_emulate_release_level({b"search.emulate-release": b"1.0.0"}) == "1.0.0"
    )


def test_parses_flat_list_reply():
    assert _parse_emulate_release_level(["search.emulate-release", "1.3.0"]) == "1.3.0"


def test_parses_flat_list_reply_with_bytes():
    assert (
        _parse_emulate_release_level([b"search.emulate-release", b"1.0.0"]) == "1.0.0"
    )


def test_empty_dict_reply_returns_none():
    assert _parse_emulate_release_level({}) is None


def test_empty_list_reply_returns_none():
    assert _parse_emulate_release_level([]) is None


def test_dict_reply_missing_key_returns_none():
    assert _parse_emulate_release_level({"some.other.key": "value"}) is None


def test_scalar_reply_returns_none():
    """A scalar reply (neither dict nor list/tuple) must return
    None rather than raise, so emulate_release_level can fail loudly with
    a clear message instead of a bare TypeError."""
    assert _parse_emulate_release_level(b"1.3.0") is None
    assert _parse_emulate_release_level("1.3.0") is None
    assert _parse_emulate_release_level(None) is None


def test_odd_length_flat_list_drops_dangling_key():
    """An odd-length flat list (a malformed reply) silently drops the
    trailing unpaired element via zip rather than raising; it still
    returns None if search.emulate-release was the dropped element."""
    assert _parse_emulate_release_level(["search.emulate-release"]) is None


def test_tuple_reply_is_accepted_like_a_list():
    assert _parse_emulate_release_level(("search.emulate-release", "1.3.0")) == "1.3.0"
