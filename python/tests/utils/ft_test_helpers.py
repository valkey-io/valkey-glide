# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Any, Dict, List, Mapping, Optional, Tuple, TypeVar, cast

from glide_shared.constants import TEncodable

from tests.utils.utils import check_version_lt

T = TypeVar("T")


def pair_flat_list(flat: object, what: str = "flat list") -> Mapping[Any, Any]:
    if isinstance(flat, Mapping):
        return flat
    items = cast(List, flat)
    if len(items) % 2 != 0:
        raise ValueError(
            f"{what}: odd-length flat list ({len(items)} items), "
            f"can't pair into key/value: {items!r}"
        )
    return dict(zip(items[0::2], items[1::2]))


def parse_emulate_release_level(config_get_raw: object) -> Optional[str]:
    if not isinstance(config_get_raw, (Mapping, list, tuple)):
        return None
    try:
        pairs = pair_flat_list(config_get_raw, what="CONFIG GET reply")
    except ValueError:
        return None
    for key, value in pairs.items():
        decoded_key = key.decode() if isinstance(key, bytes) else key
        if decoded_key == "search.emulate-release":
            return value.decode() if isinstance(value, bytes) else value
    return None


def json_groupby_column(path: T, alias: T, emulate_release: str) -> T:
    if check_version_lt(emulate_release, "1.3.0"):
        return path
    return alias


WILDCARD_DOCS: Dict[str, Dict[TEncodable, TEncodable]] = {
    "1": {"title": "alpha", "price": "10"},
    "2": {"title": "beta", "price": "20"},
    "3": {"title": "gamma", "price": "30"},
}

INKEYS_DOCS: Dict[str, Dict[TEncodable, TEncodable]] = {
    "1": {"title": "hello world"},
    "2": {"title": "hello there"},
    "3": {"title": "hello again"},
}

REDUCERS_DOCS: Dict[str, Dict[TEncodable, TEncodable]] = {
    "1": {"category": "a", "price": "10", "title": "cheap_a"},
    "2": {"category": "a", "price": "30", "title": "expensive_a"},
    "3": {"category": "a", "price": "10", "title": "cheap_a_dup"},
}
REDUCERS_EXPECTED_TOLIST_PRICES: List[str] = ["10", "30"]
REDUCERS_EXPECTED_FIRST_VALUE_TITLE = b"expensive_a"
REDUCERS_RANDOM_SAMPLE_POSSIBLE_PRICES = {b"10", b"30"}

VECTOR_RANGE_SEARCH_POINTS: Dict[str, Tuple[Tuple[float, float], str]] = {
    "0": ((0.0, 0.0), "a"),
    "1": ((1.0, 0.0), "b"),
    "2": ((2.0, 0.0), "a"),
    "3": ((3.0, 0.0), "b"),
}
VECTOR_RANGE_SEARCH_RADIUS = 2
VECTOR_RANGE_SEARCH_EXPECTED_KEYS = {"0", "1"}

VECTOR_RANGE_AGGREGATE_POINTS: List[Tuple[float, float]] = [
    (0.0, 0.0),
    (1.0, 0.0),
    (10.0, 0.0),
]
VECTOR_RANGE_AGGREGATE_RADIUS = 5
VECTOR_RANGE_AGGREGATE_EXPECTED_DISTANCES: List[float] = [0.0, 1.0]

MULTI_SHARD_DOC_COUNT = 12

HYBRID_DOC_TEXT_WINNER = "hybrid-text-winner"
HYBRID_DOC_VECTOR_WINNER = "hybrid-vector-winner"
HYBRID_DOCS: Dict[str, Dict[str, object]] = {
    HYBRID_DOC_TEXT_WINNER: {
        "title": "hello hello hello world",
        "vec": (100.0, 100.0),
    },
    HYBRID_DOC_VECTOR_WINNER: {
        "title": "goodbye",
        "vec": (1.0, 1.0),
    },
}
HYBRID_QUERY_VECTOR: Tuple[float, float] = (1.0, 1.0)
HYBRID_SEARCH_QUERY = "hello"
HYBRID_SEARCH_ALIAS = "ss"
HYBRID_VSIM_ALIAS = "vs"
