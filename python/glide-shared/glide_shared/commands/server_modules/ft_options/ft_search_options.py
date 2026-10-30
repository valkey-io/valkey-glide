# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import List, Mapping, Optional

from glide_shared.commands.command_args import OrderBy
from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtSearchKeywords,
)
from glide_shared.constants import TEncodable

# OrderBy (imported above) is re-exported from command_args rather than
# redefined here: FT.SEARCH's SORTBY direction is the exact same
# ASC/DESC concept FT.AGGREGATE's SortBy clause already uses via
# command_args.OrderBy. The public name `OrderBy` stays importable from this
# module for existing callers.


class InfoScope(Enum):
    """Controls which nodes provide index information in cluster mode."""

    LOCAL = "LOCAL"
    """Only the executing (local) node provides index information. This is the default."""
    PRIMARY = "PRIMARY"
    """Primary nodes of every shard are queried. Only valid in cluster mode."""
    CLUSTER = "CLUSTER"
    """All nodes (primary and replica) are queried. Only valid in cluster mode."""


class ShardScope(Enum):
    """Shard scope for cluster query operations."""

    ALLSHARDS = "ALLSHARDS"
    """Terminate with timeout error if not all shards respond. This is the default."""
    SOMESHARDS = "SOMESHARDS"
    """Generate a best-effort reply if not all shards respond within the timeout."""


class ConsistencyMode(Enum):
    """Consistency mode for cluster query operations."""

    CONSISTENT = "CONSISTENT"
    """Terminate with an error if the cluster is in an inconsistent state. This is the default."""
    INCONSISTENT = "INCONSISTENT"
    """Generate a best-effort reply if the cluster remains inconsistent within the timeout."""


class FtSearchLimit:
    """
    This class represents the arguments for the LIMIT option of the FT.SEARCH command.
    """

    def __init__(self, offset: int, count: int):
        """
        Initialize a new FtSearchLimit instance.

        Args:
            offset (int): The number of keys to skip before returning the result for the FT.SEARCH command.
            count (int): The total number of keys to be returned by FT.SEARCH command.
        """
        self.offset = offset
        self.count = count

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the LIMIT option of FT.SEARCH.

        Returns:
            List[TEncodable]: A list of LIMIT option arguments.
        """
        args: List[TEncodable] = [
            FtSearchKeywords.LIMIT,
            str(self.offset),
            str(self.count),
        ]
        return args


class ReturnField:
    """
    This class represents the arguments for the RETURN option of the FT.SEARCH command.
    """

    def __init__(
        self, field_identifier: TEncodable, alias: Optional[TEncodable] = None
    ):
        """
        Initialize a new ReturnField instance.

        Args:
            field_identifier (TEncodable): The identifier for the field of the key that has to returned as a result of
                FT.SEARCH command.
            alias (Optional[TEncodable]): The alias to override the name of the field in the FT.SEARCH result.
        """
        self.field_identifier = field_identifier
        self.alias = alias

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the RETURN option of FT.SEARCH.

        Returns:
            List[TEncodable]: A list of RETURN option arguments.
        """
        args: List[TEncodable] = [self.field_identifier]
        if self.alias:
            args.append(FtSearchKeywords.AS)
            args.append(self.alias)
        return args


class FtSearchOptions:
    """
    This class represents the input options to be used in the FT.SEARCH command.
    All fields in this class are optional inputs for FT.SEARCH.
    """

    def __init__(
        self,
        return_fields: Optional[List[ReturnField]] = None,
        timeout: Optional[int] = None,
        params: Optional[Mapping[TEncodable, TEncodable]] = None,
        limit: Optional[FtSearchLimit] = None,
        count: Optional[bool] = False,
        nocontent: Optional[bool] = False,
        dialect: Optional[int] = None,
        verbatim: Optional[bool] = False,
        inorder: Optional[bool] = False,
        slop: Optional[int] = None,
        sortby: Optional[TEncodable] = None,
        sortby_order: Optional[OrderBy] = None,
        withsortkeys: Optional[bool] = False,
        shard_scope: Optional[ShardScope] = None,
        consistency: Optional[ConsistencyMode] = None,
        in_keys: Optional[List[TEncodable]] = None,
        with_scores: Optional[bool] = False,
        scorer: Optional[TEncodable] = None,
    ):
        """
        Initialize the FT.SEARCH optional fields.

        Args:
            return_fields (Optional[List[ReturnField]]): The fields of a key that are returned by FT.SEARCH command.
                See `ReturnField`.
            timeout (Optional[int]): This value overrides the timeout parameter of the module.
                The unit for the timeout is in milliseconds.
            params (Optional[Mapping[TEncodable, TEncodable]]): Param key/value pairs that can be referenced from within the
                query expression.
            limit (Optional[FtSearchLimit]): This option provides pagination capability. Only the keys that satisfy the offset
                and count values are returned. See `FtSearchLimit`.
            count (Optional[bool]): Returns only the total number of matching documents, with no document rows at
                all (not even keys). Previously this emitted a bare ``COUNT`` token, which
                valkey-search's FT.SEARCH parser does not recognise at all -- confirmed live, the server rejects it
                outright with ``"Unexpected argument 'COUNT'"`` (``src/commands/ft_search_parser.cc`` has no COUNT
                keyword registered). This now maps to the server's actual documented count-only form instead,
                ``LIMIT 0 0`` (confirmed live: returns just the total count with zero result rows). Mutually
                exclusive with ``limit``, since both control the same LIMIT clause; raises ``ValueError`` if both
                are set.
            nocontent (Optional[bool]): If set, the query returns only the document IDs and not the content.
                The document entries in the result will have empty attribute maps.
            dialect (Optional[int]): The query dialect version to use. The only supported dialect is ``2``.
            verbatim (Optional[bool]): If set, stemming is not applied to text terms in the query.
            inorder (Optional[bool]): If set, proximity matching of text terms must be in order.
            slop (Optional[int]): Specifies a slop value for proximity matching of text terms.
            sortby (Optional[TEncodable]): Field name to sort results by. Sorting is applied before the LIMIT clause.
            sortby_order (Optional[OrderBy]): Sort direction (ASC or DESC). Only used when ``sortby`` is set.
            withsortkeys (Optional[bool]): If set and ``sortby`` is specified, augments the output
                with the sort key value. When enabled, each document value in the result map becomes
                a two-element list ``[sort_key, field_map]`` instead of just ``field_map``. The sort
                key is the value of the field used for sorting, or ``None`` if the field is missing.
            shard_scope (Optional[ShardScope]): Controls shard participation in cluster mode. See `ShardScope`.
            consistency (Optional[ConsistencyMode]): Controls consistency requirements in cluster mode. See `ConsistencyMode`.
            in_keys (Optional[List[TEncodable]]): Restricts the search to only this set of keys, filtering out any
                matching result whose key is not in the list. The result count is adjusted to reflect the
                filtered set. ``[]`` (an explicit empty list) is distinct from the default ``None``: ``None``
                omits INKEYS entirely (no restriction), while ``[]`` sends ``INKEYS 0`` and excludes every result.
            with_scores (Optional[bool]): If set, augments the output with each document's relevance score
                (valkey-search 1.3.0). This changes the result shape: each document value in the result map
                becomes a two-element list ``[score, field_map]`` instead of just ``field_map`` (a `bytes`-encoded
                decimal string, e.g. ``b"0.287682086229"`` -- confirmed live, not a float, on both RESP2 and RESP3).
                With ``nocontent=True``, the shape becomes ``[score, {}]``, with an empty field map. Combining
                ``withsortkeys=True`` with ``nocontent=True`` gives the same ``[score, {}]``, because the server drops
                the sort key in that combination (confirmed live). It is not ``[score, None, {}]``. With
                both ``with_scores=True`` and ``withsortkeys=True`` and content present, each row becomes a
                three-element list ``[score, sort_key, field_map]`` (`glide-core`'s
                ``FTSearchWithScoresAndSortKeysReturnType``), a genuinely different shape from either flag alone.
            scorer (Optional[TEncodable]): The scoring function used to rank results (valkey-search 1.3.0,
                ``src/indexes/scoring/scorer.h``). ``"BM25STD"`` is the only value currently registered and
                selectable; any other value is rejected by the server. Has no visible effect unless combined with
                ``with_scores=True`` (or inspected indirectly via ``sortby``).

        Note:
            Vector Range queries (``@field:[VECTOR_RANGE <radius> $blob]``) are written directly into the
            query string passed to `FT.SEARCH`, not as a field on this class; see the examples below.
            As of valkey-search 1.3.0-rc1, a VECTOR_RANGE predicate used as a pre-filter inside a KNN query
            is rejected by the server (a "single-VR model" limitation of that release) -- VECTOR_RANGE and
            KNN cannot currently be combined in the same query.

        Examples:
            >>> await client.custom_command(["FT.SEARCH", "idx", "@vec:[VECTOR_RANGE 5 $blob]=>{$yield_distance_as: dist}",
            ...     "PARAMS", "2", "blob", query_vector_bytes, "SORTBY", "dist"])
            # Finds all vectors within radius 5 of the query vector, sorted by distance, which is returned
            # under the "dist" property via YIELD_DISTANCE_AS.
        """
        self.return_fields = return_fields
        self.timeout = timeout
        self.params = params
        self.limit = limit
        self.count = count
        self.nocontent = nocontent
        self.dialect = dialect
        self.verbatim = verbatim
        self.inorder = inorder
        self.slop = slop
        self.sortby = sortby
        self.sortby_order = sortby_order
        self.withsortkeys = withsortkeys
        self.shard_scope = shard_scope
        self.consistency = consistency
        self.in_keys = in_keys
        self.with_scores = with_scores
        self.scorer = scorer

    def _validate(self) -> None:
        """Validate mutually dependent options."""
        if self.sortby is None and self.sortby_order is not None:
            raise ValueError("sortby_order requires sortby to be set.")
        if self.sortby is None and self.withsortkeys:
            raise ValueError("withsortkeys requires sortby to be set.")
        if self.count and self.limit is not None:
            raise ValueError(
                "count and limit are mutually exclusive -- count maps to "
                "LIMIT 0 0 internally, so an explicit limit "
                "would conflict with it."
            )

    def _query_flags_to_args(self) -> List[TEncodable]:
        """Serialize cluster-mode and query-parsing flags."""
        args: List[TEncodable] = []
        if self.shard_scope:
            args.append(self.shard_scope.value)
        if self.consistency:
            args.append(self.consistency.value)
        if self.nocontent:
            args.append(FtSearchKeywords.NOCONTENT)
        if self.verbatim:
            args.append(FtSearchKeywords.VERBATIM)
        if self.inorder:
            args.append(FtSearchKeywords.INORDER)
        if self.slop is not None:
            args.extend([FtSearchKeywords.SLOP, str(self.slop)])
        return args

    def _in_keys_to_args(self) -> List[TEncodable]:
        """Serialize the INKEYS option."""
        if self.in_keys is None:
            return []
        return [FtSearchKeywords.INKEYS, str(len(self.in_keys))] + list(self.in_keys)

    def _return_fields_to_args(self) -> List[TEncodable]:
        """Serialize the RETURN option."""
        if not self.return_fields:
            return []
        return_field_args: List[TEncodable] = []
        for return_field in self.return_fields:
            return_field_args.extend(return_field.to_args())
        return [
            FtSearchKeywords.RETURN,
            str(len(return_field_args)),
        ] + return_field_args

    def _sortby_to_args(self) -> List[TEncodable]:
        """Serialize the SORTBY and WITHSORTKEYS options."""
        args: List[TEncodable] = []
        if self.sortby is not None:
            args.append(FtSearchKeywords.SORTBY)
            args.append(self.sortby)
            if self.sortby_order:
                args.append(self.sortby_order.value)
        if self.withsortkeys:
            args.append(FtSearchKeywords.WITHSORTKEYS)
        return args

    def _params_to_args(self) -> List[TEncodable]:
        """Serialize the PARAMS option."""
        if not self.params:
            return []
        args: List[TEncodable] = [FtSearchKeywords.PARAMS, str(len(self.params) * 2)]
        for name, value in self.params.items():
            args.append(name)
            args.append(value)
        return args

    def to_args(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.SEARCH command.

        Returns:
            List[TEncodable]:
                List of FT.SEARCH optional arguments.
        """
        self._validate()
        args: List[TEncodable] = self._query_flags_to_args()
        args.extend(self._in_keys_to_args())
        args.extend(self._return_fields_to_args())
        args.extend(self._sortby_to_args())
        if self.timeout:
            args.append(FtSearchKeywords.TIMEOUT)
            args.append(str(self.timeout))
        args.extend(self._params_to_args())
        if self.limit:
            args.extend(self.limit.to_args())
        if self.count:
            # COUNT is not a real FT.SEARCH keyword (confirmed
            # live -- the server rejects a bare COUNT token outright). The
            # count-only form the server actually recognises is LIMIT 0 0;
            # _validate() above has already confirmed self.limit is unset
            # (they're mutually exclusive) by the time this runs.
            args.extend([FtSearchKeywords.LIMIT, "0", "0"])
        if self.with_scores:
            args.append(FtSearchKeywords.WITHSCORES)
        if self.scorer is not None:
            args.extend([FtSearchKeywords.SCORER, self.scorer])
        if self.dialect is not None:
            args.extend([FtSearchKeywords.DIALECT, str(self.dialect)])
        return args
