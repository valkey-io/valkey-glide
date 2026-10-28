# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import struct
from datetime import datetime, timedelta, timezone

import pytest
from glide_shared.commands.command_args import OrderBy
from glide_shared.commands.core_options import (
    ExpiryGetEx,
    ExpirySet,
    ExpiryType,
    ExpiryTypeGetEx,
    MigrateOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateOptions,
    FtAggregateReducer,
    FtWithCursor,
)
from glide_shared.commands.server_modules.ft_options.ft_create_options import (
    FtCreateOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_hybrid_options import (
    FtHybridCombine,
    FtHybridOptions,
    FtHybridSearchArm,
    FtHybridVsimArm,
    FtHybridVsimFilter,
    HybridFilterPolicy,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    FtSearchLimit,
    FtSearchOptions,
)

from tests.conftest import classify_inkeys_probe_error
from tests.utils.ft_test_helpers import json_groupby_column
from tests.utils.utils import is_single_response


class TestCommandsUnitTests:
    def test_expiry_cmd_args(self):
        exp_sec = ExpirySet(ExpiryType.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpirySet(ExpiryType.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpirySet(ExpiryType.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpirySet(ExpiryType.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpirySet(ExpiryType.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpirySet(
            ExpiryType.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

    def test_get_expiry_cmd_args(self):
        exp_sec = ExpiryGetEx(ExpiryTypeGetEx.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpiryGetEx(ExpiryTypeGetEx.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpiryGetEx(
            ExpiryTypeGetEx.MILLSEC, timedelta(seconds=5)
        )
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpiryGetEx(
            ExpiryTypeGetEx.MILLSEC, timedelta(seconds=5)
        )
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpiryGetEx(ExpiryTypeGetEx.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpiryGetEx(
            ExpiryTypeGetEx.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpiryGetEx(ExpiryTypeGetEx.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpiryGetEx(
            ExpiryTypeGetEx.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

        exp_persist = ExpiryGetEx(
            ExpiryTypeGetEx.PERSIST,
            None,
        )
        assert exp_persist.get_cmd_args() == ["PERSIST"]

    def test_expiry_raises_on_value_error(self):
        with pytest.raises(ValueError):
            ExpirySet(ExpiryType.SEC, 5.5)

    def test_expiry_equality(self):
        assert ExpirySet(ExpiryType.SEC, 2) == ExpirySet(ExpiryType.SEC, 2)
        assert ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        ) == ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )

        assert not ExpirySet(ExpiryType.SEC, 1) == 1

    def test_is_single_response(self):
        assert is_single_response("This is a string value", "")
        assert is_single_response(["value", "value"], [""])
        assert not is_single_response(
            [["value", ["value"]], ["value", ["valued"]]], [""]
        )
        assert is_single_response(None, None)


class TestMigrateOptions:
    def test_no_options(self):
        assert MigrateOptions().to_args() == []

    def test_copy(self):
        assert MigrateOptions(copy=True).to_args() == ["COPY"]

    def test_replace(self):
        assert MigrateOptions(replace=True).to_args() == ["REPLACE"]

    def test_copy_and_replace(self):
        assert MigrateOptions(copy=True, replace=True).to_args() == ["COPY", "REPLACE"]

    def test_password(self):
        assert MigrateOptions(password="secret").to_args() == ["AUTH", "secret"]

    def test_username_and_password(self):
        assert MigrateOptions(username="user", password="secret").to_args() == [
            "AUTH2",
            "user",
            "secret",
        ]

    def test_all_options(self):
        assert MigrateOptions(
            copy=True, replace=True, username="user", password="secret"
        ).to_args() == ["COPY", "REPLACE", "AUTH2", "user", "secret"]


class TestFtAggregateReducerUnitTests:
    """Unit tests for the valkey-search 1.3 FtAggregateReducer factory methods
    (TOLIST, FIRST_VALUE, RANDOM_SAMPLE). These construct arguments only; no server
    is required."""

    def test_to_list(self):
        reducer = FtAggregateReducer.to_list("@color")
        assert reducer.to_args() == ["REDUCE", "TOLIST", "1", "@color"]

    def test_to_list_with_name(self):
        reducer = FtAggregateReducer.to_list("@color", name="colors")
        assert reducer.to_args() == [
            "REDUCE",
            "TOLIST",
            "1",
            "@color",
            "AS",
            "colors",
        ]

    def test_first_value_simple(self):
        reducer = FtAggregateReducer.first_value("@title")
        assert reducer.to_args() == ["REDUCE", "FIRST_VALUE", "1", "@title"]

    def test_first_value_by_default_order(self):
        reducer = FtAggregateReducer.first_value("@title", by="@price")
        assert reducer.to_args() == [
            "REDUCE",
            "FIRST_VALUE",
            "3",
            "@title",
            "BY",
            "@price",
        ]

    def test_first_value_by_explicit_asc(self):
        reducer = FtAggregateReducer.first_value(
            "@title", by="@price", order=OrderBy.ASC
        )
        assert reducer.to_args() == [
            "REDUCE",
            "FIRST_VALUE",
            "4",
            "@title",
            "BY",
            "@price",
            "ASC",
        ]

    def test_first_value_by_explicit_desc_with_name(self):
        reducer = FtAggregateReducer.first_value(
            "@title", by="@price", order=OrderBy.DESC, name="most_expensive"
        )
        assert reducer.to_args() == [
            "REDUCE",
            "FIRST_VALUE",
            "4",
            "@title",
            "BY",
            "@price",
            "DESC",
            "AS",
            "most_expensive",
        ]

    def test_first_value_order_without_by_raises(self):
        with pytest.raises(ValueError):
            FtAggregateReducer.first_value("@title", order=OrderBy.DESC)

    def test_random_sample(self):
        reducer = FtAggregateReducer.random_sample("@score", 5)
        assert reducer.to_args() == ["REDUCE", "RANDOM_SAMPLE", "2", "@score", "5"]

    def test_random_sample_with_name(self):
        reducer = FtAggregateReducer.random_sample("@score", 5, name="sample")
        assert reducer.to_args() == [
            "REDUCE",
            "RANDOM_SAMPLE",
            "2",
            "@score",
            "5",
            "AS",
            "sample",
        ]

    def test_random_sample_zero_size(self):
        # 0 is a valid (if degenerate) sample size; the client does not validate
        # the bound client-side, so this must construct without error.
        reducer = FtAggregateReducer.random_sample("@score", 0)
        assert reducer.to_args() == ["REDUCE", "RANDOM_SAMPLE", "2", "@score", "0"]

    def test_name_empty_string_is_not_omitted(self):
        # L-2: `if self.name:` would treat name="" the same as name=None and
        # silently drop the AS clause. An explicit empty string is a caller
        # choice (however unusual) and must still emit `AS ""`, distinct from
        # not passing a name at all.
        reducer = FtAggregateReducer("COUNT", [], name="")
        assert reducer.to_args() == ["REDUCE", "COUNT", "0", "AS", ""]

    def test_name_none_is_omitted(self):
        reducer = FtAggregateReducer("COUNT", [], name=None)
        assert reducer.to_args() == ["REDUCE", "COUNT", "0"]


class TestFtSearchOptionsInKeys:
    """Unit tests for the valkey-search 1.3 INKEYS option on FtSearchOptions."""

    def test_in_keys_absent_by_default(self):
        assert "INKEYS" not in FtSearchOptions().to_args()

    def test_in_keys_populated(self):
        args = FtSearchOptions(in_keys=["key1", "key2", "key3"]).to_args()
        assert args == ["INKEYS", "3", "key1", "key2", "key3"]

    def test_in_keys_empty_list(self):
        # An explicit empty list is distinct from omitting in_keys: it still
        # emits INKEYS 0, which the server treats as excluding every result.
        args = FtSearchOptions(in_keys=[]).to_args()
        assert args == ["INKEYS", "0"]

    def test_in_keys_combined_with_other_options(self):
        args = FtSearchOptions(in_keys=["key1"], nocontent=True, dialect=2).to_args()
        assert args == ["NOCONTENT", "INKEYS", "1", "key1", "DIALECT", "2"]


class TestFtSearchOptionsScoring:
    """Unit tests for WITHSCORES/SCORER on FtSearchOptions, and the
    count=True fix (COUNT is not a real FT.SEARCH keyword --
    confirmed live the server rejects it outright -- so it now maps to the
    LIMIT 0 0 count-only form instead)."""

    def test_with_scores_absent_by_default(self):
        assert "WITHSCORES" not in FtSearchOptions().to_args()

    def test_with_scores_emits_flag(self):
        args = FtSearchOptions(with_scores=True).to_args()
        assert "WITHSCORES" in args

    def test_scorer_emits_keyword_and_value(self):
        args = FtSearchOptions(scorer="BM25STD").to_args()
        assert args == ["SCORER", "BM25STD"]

    def test_with_scores_and_scorer_combined(self):
        args = FtSearchOptions(with_scores=True, scorer="BM25STD").to_args()
        assert args == ["WITHSCORES", "SCORER", "BM25STD"]

    def test_count_maps_to_limit_0_0_not_count_keyword(self):
        # COUNT used to be emitted literally, but
        # src/commands/ft_search_parser.cc has no COUNT keyword at all --
        # confirmed live, the server rejects a bare COUNT token with
        # "Unexpected argument `COUNT`". LIMIT 0 0 is the server's actual
        # documented count-only form (confirmed live: returns just the
        # total count, zero result rows).
        args = FtSearchOptions(count=True).to_args()
        assert "COUNT" not in args
        assert args == ["LIMIT", "0", "0"]

    def test_count_and_limit_are_mutually_exclusive(self):
        with pytest.raises(ValueError):
            FtSearchOptions(count=True, limit=FtSearchLimit(0, 5)).to_args()

    def test_count_absent_by_default(self):
        assert "LIMIT" not in FtSearchOptions().to_args()
        assert "COUNT" not in FtSearchOptions().to_args()


class TestFtAggregateOptionsScoring:
    """Unit tests for ADDSCORES/SCORER on FtAggregateOptions. Both
    exist on FT.AGGREGATE's parser table (src/commands/ft_aggregate_parser.cc
    kAddScoresParam/kScorerParam) even though ft.aggregate.json's documented
    argument spec omits them -- confirmed by reading the parser directly."""

    def test_add_scores_absent_by_default(self):
        assert "ADDSCORES" not in FtAggregateOptions().to_args()

    def test_add_scores_emits_flag(self):
        args = FtAggregateOptions(add_scores=True).to_args()
        assert "ADDSCORES" in args

    def test_scorer_emits_keyword_and_value(self):
        args = FtAggregateOptions(scorer="BM25STD").to_args()
        assert args == ["SCORER", "BM25STD"]

    def test_add_scores_and_scorer_combined_after_other_clauses(self):
        args = FtAggregateOptions(
            add_scores=True, scorer="BM25STD", loadFields=["price"]
        ).to_args()
        assert args == ["LOAD", "1", "price", "ADDSCORES", "SCORER", "BM25STD"]


class TestFtAggregateOptionsWithCursor:
    """Unit tests for the FT.AGGREGATE typed cursor API:
    FtAggregateOptions(with_cursor=FtWithCursor(...)). The typed cursor
    option itself (FtWithCursor) is tested directly in TestFtWithCursor;
    these tests cover its placement in FtAggregateOptions.to_args()."""

    def test_with_cursor_absent_by_default(self):
        assert "WITHCURSOR" not in FtAggregateOptions().to_args()

    def test_with_cursor_bare(self):
        args = FtAggregateOptions(with_cursor=FtWithCursor()).to_args()
        assert args == ["WITHCURSOR"]

    def test_with_cursor_count_and_maxidle(self):
        args = FtAggregateOptions(
            with_cursor=FtWithCursor(count=10, maxidle=5000)
        ).to_args()
        assert args == ["WITHCURSOR", "COUNT", "10", "MAXIDLE", "5000"]

    def test_with_cursor_combined_with_load_and_scoring(self):
        args = FtAggregateOptions(
            loadFields=["price"],
            add_scores=True,
            with_cursor=FtWithCursor(count=5),
        ).to_args()
        assert args == [
            "LOAD",
            "1",
            "price",
            "ADDSCORES",
            "WITHCURSOR",
            "COUNT",
            "5",
        ]


class TestFtHybridSearchArm:
    """Unit tests for FtHybridSearchArm.to_args()."""

    def test_bare_query(self):
        assert FtHybridSearchArm("hello").to_args() == ["SEARCH", "hello"]

    def test_scorer(self):
        args = FtHybridSearchArm("hello", scorer="BM25STD").to_args()
        assert args == ["SEARCH", "hello", "SCORER", "BM25STD"]

    def test_yield_score_as(self):
        args = FtHybridSearchArm("hello", yield_score_as="ss").to_args()
        assert args == ["SEARCH", "hello", "YIELD_SCORE_AS", "ss"]

    def test_scorer_and_yield_score_as_combined(self):
        args = FtHybridSearchArm(
            "hello", scorer="BM25STD", yield_score_as="ss"
        ).to_args()
        assert args == [
            "SEARCH",
            "hello",
            "SCORER",
            "BM25STD",
            "YIELD_SCORE_AS",
            "ss",
        ]


class TestFtHybridVsimArm:
    """Unit tests for FtHybridVsimArm.to_args(), including the raw-bytes
    vs. $param-reference vector handling (confirmed live: FT.HYBRID's
    VSIM clause has no wire-level inline
    vector literal at all -- a $-prefixed PARAMS reference is the only form
    the server accepts, so raw bytes need a generated parameter name)."""

    def test_param_reference_vector_used_directly(self):
        args = FtHybridVsimArm("vec", "$myvec").to_args()
        assert args == ["VSIM", "@vec", "$myvec"]

    def test_raw_bytes_vector_requires_generated_param_name(self):
        args = FtHybridVsimArm("vec", b"rawbytes").to_args(
            generated_param_name="$generated"
        )
        assert args == ["VSIM", "@vec", "$generated"]

    def test_is_param_reference_detects_dollar_prefixed_string(self):
        assert FtHybridVsimArm("vec", "$myvec").is_param_reference()
        assert not FtHybridVsimArm("vec", "notaparam").is_param_reference()

    def test_is_param_reference_treats_bytes_as_always_raw_vector_data(self):
        # affonsov review (#7335): a raw vector's packed binary
        # representation is arbitrary and can legitimately start with the
        # byte 0x24 ("$") by coincidence. bytes/bytearray must never be
        # treated as a $name parameter reference regardless of its first
        # byte -- only an actual str starting with "$" is a reference.
        # struct.pack("2f", 1.0000042915344238, 1.0) is the smallest float32
        # pair whose packed bytes start with 0x24 on a little-endian
        # platform, a concrete example of a vector this bug would have
        # misclassified.
        packed_vector_starting_with_dollar_byte = struct.pack(
            "2f", 1.0000042915344238, 1.0
        )
        assert packed_vector_starting_with_dollar_byte.startswith(b"$")
        assert not FtHybridVsimArm(
            "vec", packed_vector_starting_with_dollar_byte
        ).is_param_reference()
        assert not FtHybridVsimArm("vec", b"$myvec").is_param_reference()
        assert not FtHybridVsimArm("vec", bytearray(b"$myvec")).is_param_reference()
        assert not FtHybridVsimArm("vec", b"rawbytes").is_param_reference()

    def test_to_args_requires_generated_param_name_for_raw_bytes_starting_with_dollar(
        self,
    ):
        # Companion to the is_param_reference test above: to_args must
        # still raise (not silently pass the raw bytes through) when a
        # 0x24-leading raw vector reaches it without a generated param name,
        # the same way it already does for any other raw-bytes vector.
        packed_vector_starting_with_dollar_byte = struct.pack(
            "2f", 1.0000042915344238, 1.0
        )
        with pytest.raises(ValueError):
            FtHybridVsimArm("vec", packed_vector_starting_with_dollar_byte).to_args()
        args = FtHybridVsimArm("vec", packed_vector_starting_with_dollar_byte).to_args(
            generated_param_name="$generated"
        )
        assert args == ["VSIM", "@vec", "$generated"]

    def test_knn_block_omitted_when_no_knn_sub_args_given(self):
        # Confirmed live: omitting the whole KNN block defaults K to 10
        # server-side (kDefaultKnnK) -- the same default RediSearch
        # documents. k is Optional here to match that engine behaviour.
        args = FtHybridVsimArm("vec", "$q").to_args()
        assert "KNN" not in args

    def test_k_emits_knn_block(self):
        args = FtHybridVsimArm("vec", "$q", k=5).to_args()
        assert args == ["VSIM", "@vec", "$q", "KNN", "2", "K", "5"]

    def test_ef_runtime_and_shard_k_ratio(self):
        args = FtHybridVsimArm(
            "vec", "$q", k=5, ef_runtime=100, shard_k_ratio=0.5
        ).to_args()
        assert args == [
            "VSIM",
            "@vec",
            "$q",
            "KNN",
            "6",
            "K",
            "5",
            "EF_RUNTIME",
            "100",
            "SHARD_K_RATIO",
            "0.5",
        ]

    def test_ef_runtime_rejected_with_adhoc_filter_policy(self):
        # affonsov review (#7335): the server rejects EF_RUNTIME outright
        # when filter.policy=ADHOC ("EF_RUNTIME is irrelevant for POLICY
        # ADHOC"); catch it client-side instead of a round trip, mirroring
        # FtHybridVsimFilter's own batch_size-requires-BATCHES validation.
        with pytest.raises(ValueError):
            FtHybridVsimArm(
                "vec",
                "$q",
                ef_runtime=100,
                filter=FtHybridVsimFilter(
                    "@title:hello", policy=HybridFilterPolicy.ADHOC
                ),
            )

    def test_ef_runtime_allowed_with_batches_filter_policy(self):
        # Companion to the rejection test above: EF_RUNTIME is fine with
        # any other policy, including BATCHES, or with no filter at all.
        args = FtHybridVsimArm(
            "vec",
            "$q",
            ef_runtime=100,
            filter=FtHybridVsimFilter(
                "@title:hello", policy=HybridFilterPolicy.BATCHES
            ),
        ).to_args()
        assert "EF_RUNTIME" in args

    def test_ef_runtime_allowed_with_unfiltered_vsim(self):
        args = FtHybridVsimArm("vec", "$q", ef_runtime=100).to_args()
        assert "EF_RUNTIME" in args

    def test_yield_score_as_after_knn_block(self):
        args = FtHybridVsimArm("vec", "$q", k=5, yield_score_as="vs").to_args()
        assert args == [
            "VSIM",
            "@vec",
            "$q",
            "KNN",
            "2",
            "K",
            "5",
            "YIELD_SCORE_AS",
            "vs",
        ]

    def test_filter_appended_after_yield_score_as(self):
        args = FtHybridVsimArm(
            "vec",
            "$q",
            yield_score_as="vs",
            filter=FtHybridVsimFilter("@title:hello"),
        ).to_args()
        assert args == [
            "VSIM",
            "@vec",
            "$q",
            "YIELD_SCORE_AS",
            "vs",
            "FILTER",
            "@title:hello",
        ]


class TestFtHybridVsimFilter:
    """Unit tests for FtHybridVsimFilter.to_args(), covering the
    uncounted extension form vs. the counted form POLICY/BATCH_SIZE force."""

    def test_bare_expression_uses_uncounted_extension_form(self):
        args = FtHybridVsimFilter("@title:hello").to_args()
        assert args == ["FILTER", "@title:hello"]

    def test_policy_forces_counted_form(self):
        args = FtHybridVsimFilter(
            "@title:hello", policy=HybridFilterPolicy.ADHOC
        ).to_args()
        assert args == ["FILTER", "3", "@title:hello", "POLICY", "ADHOC"]

    def test_batch_size_requires_policy_batches(self):
        with pytest.raises(ValueError):
            FtHybridVsimFilter("@title:hello", batch_size=10)

    def test_policy_batches_with_batch_size(self):
        args = FtHybridVsimFilter(
            "@title:hello",
            policy=HybridFilterPolicy.BATCHES,
            batch_size=10,
        ).to_args()
        assert args == [
            "FILTER",
            "5",
            "@title:hello",
            "POLICY",
            "BATCHES",
            "BATCH_SIZE",
            "10",
        ]


class TestFtHybridCombine:
    """Unit tests for FtHybridCombine.RRF/.Linear/.Function to_args(),
    including the defaulting and mutual-exclusivity rules confirmed live
    against src/commands/ft_hybrid_parser.cc."""

    def test_rrf_bare(self):
        assert FtHybridCombine.RRF().to_args() == ["COMBINE", "RRF", "0"]

    def test_rrf_with_constant(self):
        args = FtHybridCombine.RRF(constant=60).to_args()
        assert args == ["COMBINE", "RRF", "2", "CONSTANT", "60"]

    def test_rrf_negative_constant_rejected(self):
        # Confirmed live: a negative constant places a pole at
        # rank == -constant: the server rejects it.
        with pytest.raises(ValueError):
            FtHybridCombine.RRF(constant=-1)

    def test_rrf_with_window_and_yield_score_as(self):
        args = FtHybridCombine.RRF(window=50, yield_score_as="fused").to_args()
        assert args == [
            "COMBINE",
            "RRF",
            "4",
            "WINDOW",
            "50",
            "YIELD_SCORE_AS",
            "fused",
        ]

    def test_linear_bare_defaults_both_server_side(self):
        # Confirmed live: writing neither alpha nor beta defaults both
        # (0.3/0.7 server-side) -- the client sends nothing and lets the
        # server apply its own default, rather than hardcoding it here.
        assert FtHybridCombine.Linear().to_args() == ["COMBINE", "LINEAR", "0"]

    def test_linear_with_both_weights(self):
        args = FtHybridCombine.Linear(alpha=0.5, beta=0.5).to_args()
        assert args == [
            "COMBINE",
            "LINEAR",
            "4",
            "ALPHA",
            "0.5",
            "BETA",
            "0.5",
        ]

    def test_linear_one_weight_without_the_other_rejected(self):
        # Confirmed live: the server rejects writing exactly one of
        # ALPHA/BETA as more likely a typo than an intentional default.
        with pytest.raises(ValueError):
            FtHybridCombine.Linear(alpha=0.5)
        with pytest.raises(ValueError):
            FtHybridCombine.Linear(beta=0.5)

    def test_function_requires_expr(self):
        args = FtHybridCombine.Function("@ss + @vs").to_args()
        assert args == ["COMBINE", "FUNCTION", "2", "EXPR", "@ss + @vs"]

    def test_function_with_window(self):
        args = FtHybridCombine.Function("@ss + @vs", window=1000).to_args()
        assert args == [
            "COMBINE",
            "FUNCTION",
            "4",
            "EXPR",
            "@ss + @vs",
            "WINDOW",
            "1000",
        ]


class TestFtWithCursor:
    """Unit tests for FtWithCursor.to_args(). Shared verbatim between
    FT.AGGREGATE and FT.HYBRID (confirmed in source: both commands forward
    WITHCURSOR to the same aggregate-suffix parser)."""

    def test_bare_withcursor(self):
        assert FtWithCursor().to_args() == ["WITHCURSOR"]

    def test_count_and_maxidle(self):
        args = FtWithCursor(count=10, maxidle=5000).to_args()
        assert args == ["WITHCURSOR", "COUNT", "10", "MAXIDLE", "5000"]


class TestFtHybridOptions:
    """Unit tests for FtHybridOptions.to_args(), including the
    LOCALONLY-must-be-first ordering fix (confirmed live: writing it after
    LOAD/PARAMS/any other clause is rejected outright, unlike DIALECT)."""

    def test_empty_options_emits_nothing(self):
        assert FtHybridOptions().to_args() == []

    def test_load_all(self):
        assert FtHybridOptions(load_all=True).to_args() == ["LOAD", "*"]

    def test_load_fields(self):
        args = FtHybridOptions(load_fields=["title", "price"]).to_args()
        assert args == ["LOAD", "2", "title", "price"]

    def test_local_only_emitted_first_regardless_of_field_order(self):
        # Confirmed live: LOCALONLY must be the very first token of the
        # tail. to_args() always emits it first, so field-construction
        # order in Python never matters.
        args = FtHybridOptions(
            local_only=True, load_fields=["title"], dialect=2
        ).to_args()
        assert args == ["LOCALONLY", "LOAD", "1", "title", "DIALECT", "2"]

    def test_with_cursor(self):
        args = FtHybridOptions(with_cursor=FtWithCursor(count=5)).to_args()
        assert args == ["WITHCURSOR", "COUNT", "5"]

    def test_params(self):
        args = FtHybridOptions(params={"q": "value"}).to_args()
        assert args == ["PARAMS", "2", "q", "value"]

    def test_generated_vsim_param_merged_with_caller_params(self):
        args = FtHybridOptions(params={"other": "x"}).to_args(
            generated_vsim_param={"generated123": b"rawbytes"}
        )
        assert args[0] == "PARAMS"
        assert args[1] == "4"
        pairs = dict(zip(args[2::2], args[3::2]))
        assert pairs == {"other": "x", "generated123": b"rawbytes"}

    def test_generated_vsim_param_name_collision_raises(self):
        with pytest.raises(ValueError):
            FtHybridOptions(params={"q": "caller value"}).to_args(
                generated_vsim_param={"q": b"generated value"}
            )

    def test_timeout(self):
        args = FtHybridOptions(timeout=100).to_args()
        assert args == ["TIMEOUT", "100"]

    def test_clauses_applied_in_order(self):
        from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
            FtAggregateLimit,
        )

        args = FtHybridOptions(clauses=[FtAggregateLimit(0, 10)]).to_args()
        assert args == ["LIMIT", "0", "10"]

    def test_params_emitted_before_clauses(self):
        # Regression test: with no combine/local_only/load_*/timeout, a
        # leading clause used to land right after the VSIM arm's own
        # tokens, where the server's VSIM parser claims a leading FILTER
        # as its own pre-filter instead (confirmed live,
        # ft_hybrid_parser.cc's vsim_scoped FILTER handling). PARAMS is
        # always present for FT.HYBRID, so to_args() must always put it
        # ahead of clauses regardless of field-construction order.
        from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
            FtAggregateFilter,
        )

        args = FtHybridOptions(
            clauses=[FtAggregateFilter("@price>0")], params={"q": "value"}
        ).to_args()
        assert args == ["PARAMS", "2", "q", "value", "FILTER", "@price>0"]

    def test_local_only_params_and_clauses_order(self):
        # Pins the full tail order end to end: LOCALONLY, then LOAD, then
        # PARAMS, then clauses -- the three live-confirmed orderings this
        # class's docstring documents, all in a single call.
        from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
            FtAggregateLimit,
        )

        args = FtHybridOptions(
            local_only=True,
            load_fields=["title"],
            params={"q": "value"},
            clauses=[FtAggregateLimit(0, 10)],
        ).to_args()
        assert args == [
            "LOCALONLY",
            "LOAD",
            "1",
            "title",
            "PARAMS",
            "2",
            "q",
            "value",
            "LIMIT",
            "0",
            "10",
        ]


class TestFtCreateOptionsScoreField:
    """Unit tests for FtCreateOptions(score_field=)."""

    def test_score_field_absent_by_default(self):
        assert "SCORE_FIELD" not in FtCreateOptions().to_args()

    def test_score_field_emits_before_schema_position(self):
        # SCORE_FIELD is a top-level FT.CREATE option (like FILTER/ON/
        # PREFIX), not a field-definition keyword -- confirmed live,
        # placing it after SCHEMA's field list makes the parser treat it
        # as part of the preceding field definition instead. FtCreateOptions
        # itself only emits the pre-SCHEMA option block; the caller is
        # responsible for appending "SCHEMA" and the field list after it
        # (same convention as `filter`), so this test only pins the token
        # order within that block.
        args = FtCreateOptions(filter="@price>0", score_field="price").to_args()
        assert args == ["FILTER", "@price>0", "SCORE_FIELD", "price"]


class TestFtCreateOptionsNoHighlight:
    """Unit tests for Q9: FtCreateOptions(no_highlight=). NOHL is a genuine
    no-op on valkey-search <= 1.3.0-rc1 -- confirmed live and in source
    (src/commands/ft_create_parser.cc: "Highlighting is not supported, so
    NOHL is accepted and the match result deliberately discarded"). Unlike
    SORTABLE UNF (also a no-op but stored and reported by FT.INFO), NOHL
    is not even stored -- confirmed live via FT.INFO showing no NOHL-
    related field at all after FT.CREATE idx NOHL SCHEMA ...."""

    def test_no_highlight_absent_by_default(self):
        assert "NOHL" not in FtCreateOptions().to_args()

    def test_no_highlight_emits_flag(self):
        args = FtCreateOptions(no_highlight=True).to_args()
        assert args == ["NOHL"]


class TestJsonGroupbyColumnCompatHelper:
    """Unit tests for the compat-expectation helper (tests/utils/ft_test_helpers.py)."""

    def test_below_1_3_0_returns_path(self):
        assert json_groupby_column("$.c", "c", "1.0.0") == "$.c"
        assert json_groupby_column("$.c", "c", "1.2.1") == "$.c"

    def test_at_or_above_1_3_0_returns_alias(self):
        assert json_groupby_column("$.c", "c", "1.3.0") == "c"
        assert json_groupby_column("$.c", "c", "1.3.1") == "c"
        assert json_groupby_column("$.c", "c", "2.0.0") == "c"

    def test_malformed_version_raises_value_error(self):
        with pytest.raises(ValueError):
            json_groupby_column("$.c", "c", "not-a-version")
        with pytest.raises(ValueError):
            json_groupby_column("$.c", "c", "1.3")

    def test_rc_suffix_tolerated(self):
        # A "-suffix" (e.g. "-rc1") is stripped before parsing the
        # X.Y.Z part, rather than making the whole string unparseable.
        assert json_groupby_column("$.c", "c", "1.3.0-rc1") == "c"
        assert json_groupby_column("$.c", "c", "1.2.1-rc1") == "$.c"


class TestClassifyInkeysProbeError:
    """Unit tests for the pure classification function the search
    1.3 capability gate uses to decide skip-vs-run for 16 tests
    (tests/conftest.py, extracted from _probe_search_1_3_capability)."""

    def test_raw_server_text_classifies_as_pre_1_3(self):
        assert classify_inkeys_probe_error("Unexpected argument `INKEYS`") is True

    def test_glide_reformatted_text_classifies_as_pre_1_3(self):
        # GLIDE's RequestError splits the server's error text on its first
        # word as if it were an error code, reformatting "Unexpected
        # argument `INKEYS`" into "Unexpected: argument `INKEYS`"
        # (confirmed empirically against a built 1.2.1 module).
        assert classify_inkeys_probe_error("Unexpected: argument `INKEYS`") is True

    def test_unrelated_error_does_not_classify_as_pre_1_3(self):
        assert classify_inkeys_probe_error("WRONGTYPE Operation against a key") is False
        assert classify_inkeys_probe_error("OOM command not allowed") is False
