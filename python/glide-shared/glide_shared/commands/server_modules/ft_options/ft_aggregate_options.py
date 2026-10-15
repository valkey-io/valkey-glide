# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from typing import List, Mapping, Optional, Union

from glide_shared.commands.command_args import OrderBy
from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtAggregateKeywords,
    FtAggregateReducerKeywords,
)
from glide_shared.constants import TEncodable


class FtAggregateClause(ABC):
    """
    Abstract base class for the FT.AGGREGATE command clauses.
    """

    @abstractmethod
    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the clause of the FT.AGGREGATE command.

        Returns:
            List[TEncodable]: A list of arguments for the clause of the FT.AGGREGATE command.
        """
        args: List[TEncodable] = []
        return args


class FtAggregateLimit(FtAggregateClause):
    """
    A clause for limiting the number of retained records.

    An adjacent SORTBY clause's retained-row window widens to match this LIMIT's offset + count
    on valkey-search 1.3.0 and above (``sortby_max_follows_limit`` compatibility flag,
    ``src/commands/ft_aggregate_parser.cc`` `ResolveSortByBounds()`) -- below that
    `search.emulate-release` level, SORTBY still truncates to its own default (10 rows) or
    explicit MAX regardless of a LIMIT past that point. "Adjacent" means immediately before or
    after SORTBY in the clause list; a stage such as GROUPBY in between blocks the widening on
    both branches. Requires `search.emulate-release` >= 1.3.0 for the widened behavior -- see
    upstream COMPATIBILITY.md's "Sunsetting of Incompatible Behavior" section.
    """

    def __init__(self, offset: int, count: int):
        """
        Initialize a new FtAggregateLimit instance.

        Args:
            offset (int): Starting point from which the records have to be retained.
            count (int): The total number of records to be retained.
        """
        self.offset = offset
        self.count = count

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the Limit clause.

        Returns:
            List[TEncodable]: A list of Limit clause arguments.
        """
        return [FtAggregateKeywords.LIMIT, str(self.offset), str(self.count)]


class FtAggregateFilter(FtAggregateClause):
    """
    A clause for filtering the results using predicate expression relating to values in each result. It is applied post query
    and relate to the current state of the pipeline.
    """

    def __init__(self, expression: TEncodable):
        """
        Initialize a new FtAggregateFilter instance.

        Args:
            expression (TEncodable): The expression to filter the results.
        """
        self.expression = expression

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the Filter clause.

        Returns:
            List[TEncodable]: A list arguments for the filter clause.
        """
        return [FtAggregateKeywords.FILTER, self.expression]


class FtAggregateReducer:
    """
    A clause for reducing the matching results in each group using a reduction function. The matching results are reduced into
    a single record.
    """

    def __init__(
        self,
        function: TEncodable,
        args: List[TEncodable],
        name: Optional[TEncodable] = None,
    ):
        """
        Initialize a new FtAggregateReducer instance.

        Args:
            function (TEncodable): The reduction function names for the respective group.
            args (List[TEncodable]): The list of arguments for the reducer.
            name (Optional[TEncodable]): User defined property name for the reducer.
        """
        self.function = function
        self.args = args
        self.name = name

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the Reducer.

        Returns:
            List[TEncodable]: A list of arguments for the reducer.
        """
        args: List[TEncodable] = [
            FtAggregateKeywords.REDUCE,
            self.function,
            str(len(self.args)),
        ] + self.args
        if self.name is not None:
            args.extend([FtAggregateKeywords.AS, self.name])
        return args

    @classmethod
    def to_list(
        cls, field: TEncodable, name: Optional[TEncodable] = None
    ) -> "FtAggregateReducer":
        """
        Create a TOLIST reducer, which collects all values for `field` across the records in
        a group into a single array, removing duplicates and nil values. If a value is itself
        an array, it is flattened one level before being added. Values are returned in the order
        they were first encountered.

        Args:
            field (TEncodable): The property whose values are collected into a list.
            name (Optional[TEncodable]): User defined property name for the reducer. If not
                provided, the server generates a default name.

        Returns:
            FtAggregateReducer: A reducer that can be passed to `FtAggregateGroupBy`.

        Examples:
            >>> FtAggregateReducer.to_list("@color", name="colors")
            # Collects the unique set of "color" values seen in each group into "colors".
        """
        return cls(FtAggregateReducerKeywords.TOLIST, [field], name)

    @classmethod
    def first_value(
        cls,
        field: TEncodable,
        by: Optional[TEncodable] = None,
        order: Optional[OrderBy] = None,
        name: Optional[TEncodable] = None,
    ) -> "FtAggregateReducer":
        """
        Create a FIRST_VALUE reducer, which returns the value of `field` from a single record in
        the group. Without `by`, the first record encountered wins. With `by`, the record whose
        `by` property is smallest (`OrderBy.ASC`, the default) or largest (`OrderBy.DESC`) wins;
        ties keep the first-encountered record.

        Args:
            field (TEncodable): The property whose value is returned.
            by (Optional[TEncodable]): The property used to choose which record's value of
                `field` is returned. Required if `order` is specified.
            order (Optional[OrderBy]): The direction used to pick the winning record when `by` is
                set. Defaults to `OrderBy.ASC` on the server when `by` is given without `order`.
            name (Optional[TEncodable]): User defined property name for the reducer. If not
                provided, the server generates a default name.

        Returns:
            FtAggregateReducer: A reducer that can be passed to `FtAggregateGroupBy`.

        Raises:
            ValueError: If `order` is specified without `by`.

        Examples:
            >>> FtAggregateReducer.first_value("@title", name="any_title")
            # Returns the "title" of an arbitrary record in each group.

            >>> FtAggregateReducer.first_value("@title", by="@price", order=OrderBy.DESC, name="most_expensive_title")
            # Returns the "title" of the record with the highest "price" in each group.
        """
        if order is not None and by is None:
            raise ValueError("first_value: order requires by to be set.")
        args: List[TEncodable] = [field]
        if by is not None:
            args.extend([FtAggregateKeywords.BY, by])
            if order is not None:
                args.append(order.value)
        return cls(FtAggregateReducerKeywords.FIRST_VALUE, args, name)

    @classmethod
    def random_sample(
        cls, field: TEncodable, size: int, name: Optional[TEncodable] = None
    ) -> "FtAggregateReducer":
        """
        Create a RANDOM_SAMPLE reducer, which returns a uniform random sample of up to `size`
        values of `field` from the records in a group, using reservoir sampling.

        Args:
            field (TEncodable): The property to sample values from.
            size (int): The maximum number of values to sample. Must be a non-negative integer no
                greater than ``1000``; the server rejects values outside this range.
            name (Optional[TEncodable]): User defined property name for the reducer. If not
                provided, the server generates a default name.

        Returns:
            FtAggregateReducer: A reducer that can be passed to `FtAggregateGroupBy`.

        Examples:
            >>> FtAggregateReducer.random_sample("@score", 5, name="score_sample")
            # Returns up to 5 randomly sampled "score" values from each group.
        """
        return cls(FtAggregateReducerKeywords.RANDOM_SAMPLE, [field, str(size)], name)


class FtAggregateGroupBy(FtAggregateClause):
    """
    A clause for grouping the results in the pipeline based on one or more properties.

    valkey-search 1.3.0's ``src/commands/ft_aggregate_exec.cc`` (``ExpandGroupKeys()``) has code
    that treats an array-valued group key as a multi-value field -- the record would join one
    group per element, matching RediSearch's documented semantics -- and this is exercised by the
    engine's own unit tests using synthetic array-valued records. However, no end-to-end
    FT.CREATE + FT.AGGREGATE path that produces a genuinely array-typed group key was found during
    this investigation (a JSON array stored in a TAG field, and a HASH TAG field with a
    comma-separated value, both grouped as a single scalar string rather than expanding -- the
    attribute reference GROUPBY resolves field values through does not appear to construct an
    array `Value` for either shape). This needs further investigation before being documented as
    a usable feature; treat it as present-in-source but not yet confirmed reachable from GLIDE
    Python, rather than as parity with RediSearch.
    """

    def __init__(
        self, properties: List[TEncodable], reducers: List[FtAggregateReducer]
    ):
        """
        Initialize a new FtAggregateGroupBy instance.

        Args:
            properties (List[TEncodable]): The list of properties to be used for grouping the results in the pipeline.
            reducers (List[Reducer]): The list of functions that handles the group entries by performing multiple
                aggregate operations.
        """
        self.properties = properties
        self.reducers = reducers

    def to_args(self) -> List[TEncodable]:
        args = [
            FtAggregateKeywords.GROUPBY,
            str(len(self.properties)),
        ] + self.properties
        if self.reducers:
            for reducer in self.reducers:
                args.extend(reducer.to_args())
        return args


class FtAggregateSortProperty:
    """
    This class represents the a single property for the SortBy clause.
    """

    def __init__(self, property: TEncodable, order: OrderBy):
        """
        Initialize a new FtAggregateSortProperty instance.

        Args:
            property (TEncodable): The sorting parameter.
            order (OrderBy): The order for the sorting. This option can be added for each property.
        """
        self.property = property
        self.order = order

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the SortBy clause property.

        Returns:
            List[TEncodable]: A list of arguments for the SortBy clause property.
        """
        return [self.property, self.order.value]


class FtAggregateSortBy(FtAggregateClause):
    """
    A clause for sorting the pipeline up until the point of SORTBY, using a list of properties.
    """

    def __init__(
        self, properties: List[FtAggregateSortProperty], max: Optional[int] = None
    ):
        """
        Initialize a new FtAggregateSortBy instance.

        Args:
            properties (List[FtAggregateSortProperty]): A list of sorting parameters for the sort operation.
            max: (Optional[int]): The MAX value for optimizing the sorting, by sorting only for the n-largest
                elements. Omitting this falls back to a default of 10 retained rows unless an adjacent
                `FtAggregateLimit` widens it (valkey-search 1.3.0 and above only -- see `FtAggregateLimit`'s
                docstring for the `sortby_max_follows_limit` gate and `search.emulate-release` requirement).
        """
        self.properties = properties
        self.max = max

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the SortBy clause.

        Returns:
            List[TEncodable]: A list of arguments for the SortBy clause.
        """
        args: List[TEncodable] = [
            FtAggregateKeywords.SORTBY,
            str(len(self.properties) * 2),
        ]
        for property in self.properties:
            args.extend(property.to_args())
        if self.max:
            args.extend([FtAggregateKeywords.MAX, str(self.max)])
        return args


class LoadField:
    """
    A single entry in the LOAD clause of FT.AGGREGATE, optionally renaming the loaded field via
    AS. Modelled on `ReturnField` (the equivalent concept for FT.SEARCH's RETURN option).

    ``AS <alias>`` in the LOAD clause is a compatibility fix introduced in valkey-search 1.3.0
    (``src/commands/ft_aggregate_parser.cc``, gated by the ``ft_aggregate_load_as`` compatibility
    flag -- see ``COMPATIBILITY.md``). Below ``search.emulate-release 1.3.0``, the legacy branch
    does not recognise ``AS`` as a rename keyword at all: it is consumed as just another LOAD
    field token rather than a rename directive. GLIDE does not validate this client-side; against
    a pre-1.3.0 emulate-release the server rejects the command outright with a `RequestError`
    reading "Index: field `AS` does not exist" (confirmed live against 1.3.0-rc1, and asserted in
    `tests/async_tests/tests_server_modules/test_ft.py`'s `test_ft_aggregate_load_as`). Use the
    `requires_search_1_3` marker (or equivalent capability check) around any code path that relies
    on the alias actually renaming the column. Requires `search.emulate-release` >= 1.3.0 -- see
    upstream COMPATIBILITY.md's "Sunsetting of Incompatible Behavior" section.
    """

    def __init__(
        self, field_identifier: TEncodable, alias: Optional[TEncodable] = None
    ):
        """
        Initialize a new LoadField instance.

        Args:
            field_identifier (TEncodable): The identifier for the field to load.
            alias (Optional[TEncodable]): The alias to emit the field's column under. Requires
                `search.emulate-release` >= 1.3.0 to take effect.
        """
        self.field_identifier = field_identifier
        self.alias = alias

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for this LOAD clause entry.

        Returns:
            List[TEncodable]: A list of arguments for this LOAD entry.
        """
        args: List[TEncodable] = [self.field_identifier]
        if self.alias is not None:
            args.append(FtAggregateKeywords.AS)
            args.append(self.alias)
        return args


def load_fields_to_args(
    load_fields: Optional[List[Union[TEncodable, "LoadField"]]],
) -> List[TEncodable]:
    """Serialize a LOAD clause's field list for FT.AGGREGATE and FT.HYBRID (shared,
    since the two commands' LOAD clause grammar is byte-identical, confirmed in source -- see
    `LoadField`'s docstring). The count is a token budget, not an entry count -- each `LoadField`
    with an alias contributes 3 tokens (field, AS, alias) rather than 1, matching the parser's own
    accounting (src/commands/ft_aggregate_parser.cc: "`cnt` is a token budget...the `AS` keyword
    and its alias count against the budget")."""
    if not load_fields:
        return []
    load_field_args: List[TEncodable] = []
    for entry in load_fields:
        if isinstance(entry, LoadField):
            load_field_args.extend(entry.to_args())
        else:
            load_field_args.append(entry)
    return [
        FtAggregateKeywords.LOAD,
        str(len(load_field_args)),
    ] + load_field_args


def params_to_args(
    params: Optional[Mapping[TEncodable, TEncodable]],
    params_keyword: TEncodable = FtAggregateKeywords.PARAMS,
) -> List[TEncodable]:
    """Serialize a PARAMS option for FT.AGGREGATE and FT.HYBRID (shared, since the two
    commands' PARAMS grammar is byte-identical). `params_keyword` lets a caller (e.g.
    `FtHybridOptions`) pass its own command-specific keyword constant rather than this module's
    FT.AGGREGATE one, even though the two currently serialize to the same literal text.
    """
    if not params:
        return []
    args: List[TEncodable] = [
        params_keyword,
        str(len(params) * 2),
    ]
    for name, value in params.items():
        args.extend([name, value])
    return args


class FtAggregateApply(FtAggregateClause):
    """
    A clause for applying a 1-to-1 transformation on one or more properties and stores the result as a new property down the
    pipeline or replaces any property using this transformation.
    """

    def __init__(self, expression: TEncodable, name: TEncodable):
        """
        Initialize a new FtAggregateApply instance.

        Args:
            expression (TEncodable): The expression to be transformed.
            name (TEncodable): The new property name to store the result of apply. This name can be referenced by further
                APPLY/SORTBY/GROUPBY/REDUCE operations down the pipeline.
        """
        self.expression = expression
        self.name = name

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the Apply clause.

        Returns:
            List[TEncodable]: A list of arguments for the Apply clause.
        """
        return [
            FtAggregateKeywords.APPLY,
            self.expression,
            FtAggregateKeywords.AS,
            self.name,
        ]


class FtWithCursor:
    """
    The WITHCURSOR option, requesting a paged reply instead of the whole result set at once.
    Shared verbatim between FT.AGGREGATE and FT.HYBRID -- confirmed in
    ``src/commands/ft_hybrid_parser.cc``: WITHCURSOR is handled by the exact same aggregate-suffix
    parser both commands drive, so the option's own grammar (``WITHCURSOR [COUNT n] [MAXIDLE
    ms]``) and reply shape are identical regardless of which command opened the cursor. A paged
    query, and every subsequent `glide.async_commands.ft.
    cursor_read`/`cursor_del` (or `glide_sync.sync_commands.ft.cursor_read`/`cursor_del`) against
    the cursor it opens, is pinned to the PRIMARY of the index's slot regardless of ``ReadFrom``
    -- a cursor is a per-process, node-local resource, and this is a deliberate, documented
    exception to GLIDE's normal read routing.
    """

    def __init__(self, count: Optional[int] = None, maxidle: Optional[int] = None):
        """
        Initialize a new FtWithCursor instance.

        Args:
            count (Optional[int]): The number of rows to return per page.
            maxidle (Optional[int]): How long (in milliseconds) the cursor may sit idle between
                reads before the server discards it.
        """
        self.count = count
        self.maxidle = maxidle

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the WITHCURSOR option.

        Returns:
            List[TEncodable]: A list of WITHCURSOR option arguments.
        """
        args: List[TEncodable] = [FtAggregateKeywords.WITHCURSOR]
        if self.count is not None:
            args.extend([FtAggregateKeywords.COUNT, str(self.count)])
        if self.maxidle is not None:
            args.extend([FtAggregateKeywords.MAXIDLE, str(self.maxidle)])
        return args


class FtAggregateOptions:
    """
    This class represents the optional arguments for the FT.AGGREGATE command.
    """

    def __init__(
        self,
        loadAll: Optional[bool] = False,
        loadFields: Optional[List[Union[TEncodable, LoadField]]] = None,
        timeout: Optional[int] = None,
        params: Optional[Mapping[TEncodable, TEncodable]] = None,
        clauses: Optional[List[FtAggregateClause]] = None,
        verbatim: Optional[bool] = False,
        inorder: Optional[bool] = False,
        slop: Optional[int] = None,
        dialect: Optional[int] = None,
        add_scores: Optional[bool] = False,
        scorer: Optional[TEncodable] = None,
        with_cursor: Optional[FtWithCursor] = None,
    ):
        """
        Initialize a new FtAggregateOptions instance.

        Args:
            loadAll (Optional[bool]): An option to load all fields declared in the index.
            loadFields (Optional[List[Union[TEncodable, LoadField]]]): An option to load only the fields passed in
                this list. Each entry is either a plain field identifier (loaded under its own name, as before) or a
                `LoadField` to additionally rename it via ``AS`` (valkey-search 1.3.0; see `LoadField`'s docstring
                for the compatibility gate). The two forms can be freely mixed in the same list.
            timeout (Optional[int]): Overrides the timeout parameter of the module.
            params (Optional[Mapping[TEncodable, TEncodable]]): The key/value pairs can be referenced from within the
                query expression.
            clauses (Optional[List[FtAggregateClause]]): FILTER, LIMIT, GROUPBY, SORTBY and APPLY clauses, that can be
                repeated multiple times in any order and be freely intermixed. They are applied in the order specified, with
                the output of one clause feeding the input of the next clause.
            verbatim (Optional[bool]): If set, stemming is not applied to term searches.
            inorder (Optional[bool]): If set, proximity matching of terms must be in order.
            slop (Optional[int]): Specifies a slop value for proximity matching of terms.
            dialect (Optional[int]): The query dialect version to use. The only supported dialect is ``2``.
            add_scores (Optional[bool]): If set, exposes the relevance score as a pipeline field named
                ``__score`` (valkey-search 1.3.0, ``src/commands/ft_aggregate_parser.cc`` ``kAddScoresParam``,
                ``src/commands/ft_aggregate_exec.cc``). Confirmed live: ``__score`` can then be referenced like any
                other pipeline field in a `LoadField`, `FtAggregateApply` expression, or an
                `FtAggregateSortProperty` (e.g. ``FtAggregateSortProperty("@__score", OrderBy.DESC)``) placed in
                `clauses`.
            scorer (Optional[TEncodable]): The scoring function used to compute ``__score`` and rank results
                (valkey-search 1.3.0, ``src/indexes/scoring/scorer.h``). ``"BM25STD"`` is the only value currently
                registered and selectable; any other value is rejected by the server. Has no visible effect unless
                combined with ``add_scores=True`` (or inspected indirectly via a SORTBY on ``__score``).
            with_cursor (Optional[FtWithCursor]): Requests a paged reply instead of the whole result set at once.
                See `FtWithCursor`. The reply shape becomes a two-element array of ``[rows, cursor_id]``, where
                ``rows`` is this same aggregate response shape and ``cursor_id`` is passed to a subsequent
                `glide.async_commands.ft.cursor_read`/`glide_sync.sync_commands.ft.cursor_read` call to fetch the
                next page (``0`` once exhausted).

        Note:
            A paged (``with_cursor``) query, and every subsequent FT.CURSOR READ/DEL against the cursor it opens,
            always reads from the PRIMARY of the index's slot, regardless of the client's ``ReadFrom``
            configuration. This is because a cursor is a per-process, node-local
            resource on the server that opened it -- it is not shared or forwarded between nodes, so a read that
            lands on a different node (a replica a ``PREFER_REPLICA``/AZ-affinity client would otherwise have
            used) fails with "Cursor not found". A failover of the primary that was holding an open cursor loses
            that cursor, the same as any other cursor-discarding event on that node. A plain aggregate without
            ``with_cursor`` is unaffected and still prefers replicas as normal.
        """
        self.loadAll = loadAll
        self.loadFields = loadFields
        self.timeout = timeout
        self.params = params
        self.clauses = clauses
        self.verbatim = verbatim
        self.inorder = inorder
        self.slop = slop
        self.dialect = dialect
        self.add_scores = add_scores
        self.scorer = scorer
        self.with_cursor = with_cursor

    def _query_flags_to_args(self) -> List[TEncodable]:
        """Serialize the query-parsing flags (VERBATIM/INORDER/SLOP)."""
        args: List[TEncodable] = []
        if self.verbatim:
            args.append(FtAggregateKeywords.VERBATIM)
        if self.inorder:
            args.append(FtAggregateKeywords.INORDER)
        if self.slop is not None:
            args.extend([FtAggregateKeywords.SLOP, str(self.slop)])
        return args

    def _scoring_to_args(self) -> List[TEncodable]:
        """Serialize the ADDSCORES and SCORER options."""
        args: List[TEncodable] = []
        if self.add_scores:
            args.append(FtAggregateKeywords.ADDSCORES)
        if self.scorer is not None:
            args.extend([FtAggregateKeywords.SCORER, self.scorer])
        return args

    def to_args(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.AGGREGATE command.

        Returns:
            List[TEncodable]: A list of optional arguments for the FT.AGGREGATE command.
        """
        args: List[TEncodable] = self._query_flags_to_args()
        if self.loadAll:
            args.extend([FtAggregateKeywords.LOAD, "*"])
        else:
            args.extend(load_fields_to_args(self.loadFields))
        if self.timeout:
            args.extend([FtAggregateKeywords.TIMEOUT, str(self.timeout)])
        args.extend(params_to_args(self.params))
        if self.clauses:
            for clause in self.clauses:
                args.extend(clause.to_args())
        if self.dialect is not None:
            args.extend([FtAggregateKeywords.DIALECT, str(self.dialect)])
        args.extend(self._scoring_to_args())
        if self.with_cursor is not None:
            args.extend(self.with_cursor.to_args())
        return args
