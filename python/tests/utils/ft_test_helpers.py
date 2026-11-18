# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Shared, server-agnostic test helpers for the valkey-search 1.3 feature
tests (async and sync). Fixtures and expectations that do not depend on
whether the client is async or sync live here so both test files assert the
same thing from one place. Does not restructure the legacy (pre-1.3)
test suites -- only the NEW 1.3 tests use this module.
"""

from typing import Any, Dict, List, Mapping, Tuple, TypeVar, cast

from glide_shared.constants import TEncodable

T = TypeVar("T")


def pair_flat_list(raw: object) -> Mapping[TEncodable, Any]:
    """Normalises a server reply that may come back as either a RESP3 map
    or a RESP2 flat alternating key/value list into a plain mapping, so
    callers don't reinvent this pairing inline at each call site (
    a reinvented inline version in a new test reintroduced the odd-length
    silent-drop hazard fixed in `normalize_cluster_shards_entry`/
    `normalize_cluster_shards_node` -- this is the FT.INFO-shaped
    equivalent of those two, generalised since FT.INFO's `attributes`
    entries and `index_definition` need the exact same normalisation).

    Raises ValueError on an odd-length list, same guard and reasoning as
    `normalize_cluster_shards_entry`.
    """
    if isinstance(raw, Mapping):
        return raw
    flat = cast(List, raw)
    if len(flat) % 2 != 0:
        raise ValueError(
            f"pair_flat_list: odd-length flat list ({len(flat)} items), "
            f"can't pair into key/value: {flat!r}"
        )
    return dict(zip(flat[0::2], flat[1::2]))


def _parse_emulate_release(
    emulate_release: str, caller_name: str
) -> Tuple[int, int, int]:
    """Shared version-string parsing for the compat-expectation helpers
    below. Raises ValueError (never a bare parse exception or silent
    fallback) on anything that isn't a dotted X.Y.Z version string (an
    optional ``-suffix``, e.g. ``-rc1``, is stripped before parsing and
    otherwise ignored -- a module build whose own `emulate-release`
    reports a pre-release suffix should still parse, since the X.Y.Z part
    is all these helpers compare against), so a test using one of these
    helpers fails loudly if the harness's active emulate-release can't be
    parsed.
    """
    numeric_part = emulate_release.strip().split("-", 1)[0]
    parts = numeric_part.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise ValueError(
            f"{caller_name}: emulate_release must be a dotted X.Y.Z version "
            f"string, optionally with a '-suffix' (e.g. '1.3.0' or "
            f"'1.3.0-rc1'), got {emulate_release!r}. This is almost always "
            "a harness bug (CONFIG GET search.emulate-release returned "
            "something unexpected), not a server behaviour change."
        )
    return cast(Tuple[int, int, int], tuple(int(p) for p in parts))


def json_groupby_column(path: T, alias: T, emulate_release: str) -> T:
    """The FT.AGGREGATE GROUPBY output column name for a JSON-indexed field,
    which valkey-search names differently depending on `search.emulate-release`
    (baseline 1.3.0):

      * < 1.3.0 (the module's own default is 1.0.0): the raw schema
        identifier, i.e. the JSON path itself (e.g. "$.condition").
      * >= 1.3.0: the FT.CREATE AS alias (e.g. "condition").

    Source: valkey-search 1.3.0-rc1 `OutputNameFor()` in
    `src/commands/ft_aggregate_parser.cc`, gated by
    `VALKEY_SEARCH_COMPATIBILITY_FIX(1, 3, 0, "aggregate_json_field_names",
    ...)`. Confirmed live via the "compatibility-aggregate_json_field_names"
    INFO counter incrementing only on the legacy branch.

    Raises ValueError on a malformed `emulate_release` string rather than
    silently picking a branch, so a test using this helper fails loudly (not
    a bare KeyError) if the harness's active emulate-release can't be parsed.
    """
    if _parse_emulate_release(emulate_release, "json_groupby_column") >= (1, 3, 0):
        return alias
    return path


# --- Shared seed data / expected values for the NEW 1.3 feature tests ---
# The async and sync test files duplicate these tests near-verbatim
# (same schema, same seed values, same expected results, differing only in
# await/client-variable-name boilerplate). Centralising the data literals
# here means a transcription slip can no longer make the two files assert
# different things for the same scenario, without restructuring either
# file's control flow or the legacy (pre-1.3) tests. Proportionate scope:
# just the 5 new 1.3 feature tests this round touched.
#
# Typed as Dict[str, Dict[str, str]] (not inferred) so that passing a value
# straight into TEncodable-typed client calls (e.g. hset) type-checks in
# both test files without a per-call-site cast.

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

# category "a" has 2 distinct prices (10, 30) across 3 records (10 appears
# twice) -- used by TOLIST (dedupes; see L-11 note on REDUCERS_EXPECTED_
# TOLIST_PRICES below for why order isn't asserted), FIRST_VALUE (BY price
# DESC picks the highest), and RANDOM_SAMPLE (reservoir over all 3 raw
# values, not deduped).
REDUCERS_DOCS: Dict[str, Dict[TEncodable, TEncodable]] = {
    "1": {"category": "a", "price": "10", "title": "cheap_a"},
    "2": {"category": "a", "price": "30", "title": "expensive_a"},
    "3": {"category": "a", "price": "10", "title": "cheap_a_dup"},
}
# L-11: TOLIST's "first-encountered order" is a source-level guarantee
# (ft_aggregate_exec.cc, ToList::ProcessRecord appends in processing order),
# but that processing order is the index's internal scan order, not this
# test's HSET insertion sequence -- confirmed empirically: seeding "30"
# before "10" still returned ["10", "30"] from the live engine. The order
# this constant lists is therefore the SORTED expectation, not an
# order-revealing one; the integration test sorts both sides before
# comparing and documents why inline.
REDUCERS_EXPECTED_TOLIST_PRICES: List[str] = ["10", "30"]
REDUCERS_EXPECTED_FIRST_VALUE_TITLE = b"expensive_a"
REDUCERS_RANDOM_SAMPLE_POSSIBLE_PRICES = {b"10", b"30"}

# 2D points at known L2 distances from the origin (0, 1, 4, 9) so a radius of
# 2 is unambiguous: it must include distances 0 and 1 and exclude 4 and 9.
VECTOR_RANGE_SEARCH_POINTS: Dict[str, Tuple[Tuple[float, float], str]] = {
    "0": ((0.0, 0.0), "a"),
    "1": ((1.0, 0.0), "b"),
    "2": ((2.0, 0.0), "a"),
    "3": ((3.0, 0.0), "b"),
}
VECTOR_RANGE_SEARCH_RADIUS = 2
VECTOR_RANGE_SEARCH_EXPECTED_KEYS = {"0", "1"}

# Distances 0, 1, 100 from the origin -- a radius of 5 must include the first
# two and exclude the third.
VECTOR_RANGE_AGGREGATE_POINTS: List[Tuple[float, float]] = [
    (0.0, 0.0),
    (1.0, 0.0),
    (10.0, 0.0),
]
VECTOR_RANGE_AGGREGATE_RADIUS = 5
VECTOR_RANGE_AGGREGATE_EXPECTED_DISTANCES: List[float] = [0.0, 1.0]

MULTI_SHARD_DOC_COUNT = 12

# FT.HYBRID fixture: two docs where the SEARCH arm and the
# VSIM arm rank them in OPPOSITE order, so a COMBINE method that is actually
# weighting the two arms differently (not just always picking the same
# winner regardless of method) can be told apart from one that isn't.
# Confirmed live: with ALPHA=1/BETA=0 (text only), HYBRID_DOC_TEXT_WINNER
# ranks first; with ALPHA=0/BETA=1 (vector only), HYBRID_DOC_VECTOR_WINNER
# ranks first. HYBRID_DOC_TEXT_WINNER's title repeats "hello" for a strong
# BM25 match and its vector sits far from the query vector; HYBRID_DOC_
# VECTOR_WINNER's title has no "hello" at all (so the SEARCH arm never
# scores it on text relevance, though FT.HYBRID's UNION semantics still
# surface it via the VSIM arm) and its vector sits essentially on top of
# the query vector.
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


def normalize_cluster_shards_entry(
    shard: object,
) -> Dict[bytes, Any]:
    """`CLUSTER SHARDS` replies as a map per shard under RESP3 but as a flat
    alternating key/value list under RESP2 (confirmed empirically: the
    multi-shard 1.3 test is parametrized over both protocols). Normalises
    one shard entry to a plain `Dict[bytes, Any]` so callers never branch
    on protocol themselves. Does not recurse into the `nodes` sub-list
    (RESP2/RESP3 shape differs there too) -- see
    `normalize_cluster_shards_node` for that. Return type is `Any`-valued,
    not further narrowed, because each key's value has its own shape
    (slots: flat int list; nodes: list of maps) that only the caller knows
    how to use.
    """
    if isinstance(shard, Mapping):
        return dict(shard)
    # zip(x[0::2], x[1::2]) on an odd-length list silently drops the
    # trailing element instead of raising -- a genuine reply-shape
    # regression (an unpaired key with no value) would otherwise disappear
    # instead of failing loudly. A bare `assert` is stripped
    # entirely under `python -O`, silently disabling this guard in that
    # mode -- raise ValueError instead so it can never be optimized away.
    if len(shard) % 2 != 0:  # type: ignore[arg-type]
        raise ValueError(
            f"normalize_cluster_shards_entry: odd-length flat list "
            f"({len(shard)} items), can't pair into key/value: {shard!r}"  # type: ignore[arg-type]
        )
    return dict(zip(shard[0::2], shard[1::2]))  # type: ignore[index]


def normalize_cluster_shards_node(node: object) -> Dict[bytes, Any]:
    """Same normalisation as `normalize_cluster_shards_entry`, for one entry
    of a shard's `nodes` list (each node has its own RESP2/RESP3 shape
    distinction, independent of the shard entry's own)."""
    if isinstance(node, Mapping):
        return dict(node)
    # See normalize_cluster_shards_entry's comment above -- raise
    # rather than assert so python -O can't silently strip this guard.
    if len(node) % 2 != 0:  # type: ignore[arg-type]
        raise ValueError(
            f"normalize_cluster_shards_node: odd-length flat list "
            f"({len(node)} items), can't pair into key/value: {node!r}"  # type: ignore[arg-type]
        )
    return dict(zip(node[0::2], node[1::2]))  # type: ignore[index]
