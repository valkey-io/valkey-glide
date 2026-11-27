# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from enum import Enum
from typing import ClassVar, List, Mapping, Optional, Type, Union

from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateClause,
    FtWithCursor,
    LoadField,
    load_fields_to_args,
    params_to_args,
)
from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtAggregateKeywords,
    FtHybridKeywords,
)
from glide_shared.constants import TEncodable


class HybridFilterPolicy(Enum):
    """
    Controls how a VSIM pre-filter (see `FtHybridVsimFilter`) is applied against the vector
    search. It is only meaningful inside a counted VSIM FILTER block, because the uncounted
    ``FILTER <expr>`` form on VSIM cannot carry a policy. This is a different axis from
    `FtHybridCombine`'s RRF/LINEAR/FUNCTION method, which controls how the SEARCH and VSIM arms'
    scores are fused after both arms have run.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    ADHOC = "ADHOC"
    """Evaluates the filter against every candidate as it is produced (brute-force), rather than
    pre-restricting the vector index's own search space. ``EF_RUNTIME`` on the same VSIM arm is
    rejected when this policy is set."""
    BATCHES = "BATCHES"
    """Evaluates the filter in batches drawn from the vector index. Required for `batch_size` to
    be set on `FtHybridVsimFilter`."""


class FtHybridVsimFilter:
    """
    A pre-filter on the VSIM arm's vector search, written in the same query language the SEARCH
    arm uses (e.g. ``@title:hello``). With a filter present, the VSIM arm runs as an ordinary
    FT.SEARCH-style vector query (``<filter>=>[KNN ...]``). The filter decides membership, and the
    arm's score stays the vector distance.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        expression: TEncodable,
        policy: Optional[HybridFilterPolicy] = None,
        batch_size: Optional[int] = None,
    ):
        """
        Initialize a new FtHybridVsimFilter instance.

        Args:
            expression (TEncodable): The filter expression, in the same query language FT.SEARCH
                uses (e.g. ``@title:hello``).
            policy (Optional[HybridFilterPolicy]): Controls how the filter is applied. See
                `HybridFilterPolicy`. Setting this uses the counted VSIM FILTER form, which the
                server requires in order to recognise POLICY.
            batch_size (Optional[int]): Tunes how many candidates ``POLICY BATCHES`` draws per
                batch. Requires ``policy=HybridFilterPolicy.BATCHES``. The server accepts the value
                but currently discards it (valkey-search issue #1457), so it has no observable
                effect yet. It is still sent, and the server validates its presence and ordering
                against ``policy``.

        Raises:
            ValueError: If `batch_size` is set without ``policy=HybridFilterPolicy.BATCHES``.
        """
        if batch_size is not None and policy != HybridFilterPolicy.BATCHES:
            raise ValueError("batch_size requires policy=HybridFilterPolicy.BATCHES.")
        self.expression = expression
        self.policy = policy
        self.batch_size = batch_size

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the VSIM FILTER clause.

        Returns:
            List[TEncodable]: A list of FILTER clause arguments, in the server's counted form
                when `policy` or `batch_size` is set, or the uncounted extension form otherwise.
        """
        sub_args: List[TEncodable] = []
        if self.policy is not None:
            sub_args.extend([FtHybridKeywords.POLICY, self.policy.value])
        if self.batch_size is not None:
            sub_args.extend([FtHybridKeywords.BATCH_SIZE, str(self.batch_size)])
        if not sub_args:
            return [FtHybridKeywords.FILTER, self.expression]
        token_count = 1 + len(sub_args)
        return [
            FtHybridKeywords.FILTER,
            str(token_count),
            self.expression,
        ] + sub_args


class FtHybridSearchArm:
    """
    The SEARCH arm of an FT.HYBRID query: an ordinary (non-vector) FT.SEARCH-style text/tag/
    numeric query, run alongside the VSIM arm and fused with it. A vector query (e.g. a
    ``=>[KNN ...]`` clause) is rejected by the server here; use `FtHybridVsimArm` for the vector
    half instead. The SEARCH arm does not substitute ``$name`` parameters: a ``$name`` written in
    this query is sent as literal text.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        query: TEncodable,
        scorer: Optional[TEncodable] = None,
        yield_score_as: Optional[TEncodable] = None,
    ):
        """
        Initialize a new FtHybridSearchArm instance.

        Args:
            query (TEncodable): The text/tag/numeric query, in the same language FT.SEARCH uses.
                Must not be a vector query (e.g. must not contain a ``=>[KNN ...]`` clause); the
                server rejects that combination.
            scorer (Optional[TEncodable]): The scoring function used to rank this arm's results.
                ``"BM25STD"`` is the only value currently registered and selectable; any other
                value is rejected by the server.
            yield_score_as (Optional[TEncodable]): Exposes this arm's score under the given
                column name, reachable from `FtAggregateApply`, `FtAggregateSortProperty`,
                `FtAggregateGroupBy`/`FtAggregateReducer` expressions, and `FtHybridCombine`'s
                ``Function`` variant's ``expr``. This alias cannot be named inside a
                `LoadField`/`FtHybridOptions.load_fields` entry; the server rejects it with
                "Index field `<alias>` does not exist". The score is already present in the default
                reply without a LOAD clause.
        """
        self.query = query
        self.scorer = scorer
        self.yield_score_as = yield_score_as

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the SEARCH clause.

        Returns:
            List[TEncodable]: A list of SEARCH clause arguments.
        """
        args: List[TEncodable] = [FtHybridKeywords.SEARCH, self.query]
        if self.scorer is not None:
            args.extend([FtHybridKeywords.SCORER, self.scorer])
        if self.yield_score_as is not None:
            args.extend([FtHybridKeywords.YIELD_SCORE_AS, self.yield_score_as])
        return args


class FtHybridVsimArm:
    """
    The VSIM arm of an FT.HYBRID query: a vector similarity search against a vector field, run
    alongside the SEARCH arm and fused with it.

    The query vector can be given two ways, mirroring ``redis-py``'s ``vector_data`` convention so
    code written against RediSearch's typed client ports over with the same two call shapes:

    - As raw bytes (or any other `TEncodable`): the client generates an internal ``$``-prefixed
      PARAMS entry under the hood and references it on the wire. FT.HYBRID's VSIM clause has no
      inline vector literal: the parameter token must start with ``$``, or the server rejects it
      ("VSIM: vector parameter must start with '$'"). An auto-generated PARAMS entry is therefore
      the only way to offer a "just pass the bytes" call shape.
    - As an explicit ``$name`` string: the caller manages their own entry in
      `FtHybridOptions.params`, exactly like `FtSearchOptions.params`/`FtAggregateOptions.params`
      already work for every other PARAMS-driven query.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        field: TEncodable,
        vector: TEncodable,
        k: Optional[int] = None,
        ef_runtime: Optional[int] = None,
        shard_k_ratio: Optional[float] = None,
        yield_score_as: Optional[TEncodable] = None,
        filter: Optional[FtHybridVsimFilter] = None,
    ):
        """
        Initialize a new FtHybridVsimArm instance.

        Args:
            field (TEncodable): The vector field's identifier, without the leading ``@`` (added
                automatically).
            vector (TEncodable): The query vector, either as raw bytes/any `TEncodable` (an
                internal PARAMS entry is generated automatically) or as an explicit ``$name``
                string referencing an entry the caller has placed in `FtHybridOptions.params`.
                A string starting with ``$`` is always treated as a parameter reference, never as
                literal vector bytes.
            k (Optional[int]): The number of nearest neighbours to return. Optional on the wire:
                omitting the whole KNN block (which happens when `k`, `ef_runtime` and
                `shard_k_ratio` are all unset) defaults to 10, the same default RediSearch
                documents for FT.HYBRID's VSIM clause.
            ef_runtime (Optional[int]): HNSW search-time effort parameter. Only valid when the KNN
                block is written (RANGE is not currently supported by this typed API).
            shard_k_ratio (Optional[float]): Tunes how much of `k` each shard returns during a
                cluster fanout. The server parses and accepts it but does not change query results,
                only how much work each shard does to produce them. It is accepted here so a query
                written for RediSearch is not rejected by this client.
            yield_score_as (Optional[TEncodable]): Exposes this arm's score (the vector distance)
                under the given column name. See `FtHybridSearchArm.yield_score_as`'s docstring for
                the LOAD-clause restriction that applies identically here.
            filter (Optional[FtHybridVsimFilter]): A pre-filter on the vector search. See
                `FtHybridVsimFilter`.

        Raises:
            ValueError: If `ef_runtime` is set while `filter.policy` is
                `HybridFilterPolicy.ADHOC` -- the server rejects that combination outright
                ("EF_RUNTIME is irrelevant for POLICY ADHOC", see `HybridFilterPolicy.ADHOC`'s
                docstring). Mirrors the client-side validation `FtHybridVsimFilter` already does
                for `batch_size` with a non-`BATCHES` policy, so this is caught immediately rather
                than on a server round trip.
        """
        if (
            ef_runtime is not None
            and filter is not None
            and filter.policy == HybridFilterPolicy.ADHOC
        ):
            raise ValueError(
                "ef_runtime is not supported with filter.policy=HybridFilterPolicy.ADHOC "
                "(the server rejects this combination: "
                '"EF_RUNTIME is irrelevant for POLICY ADHOC").'
            )
        self.field = field
        self.vector = vector
        self.k = k
        self.ef_runtime = ef_runtime
        self.shard_k_ratio = shard_k_ratio
        self.yield_score_as = yield_score_as
        self.filter = filter

    def is_param_reference(self) -> bool:
        """
        True if `vector` is a ``$name`` parameter reference rather than raw vector bytes.

        Returns:
            bool: True if `vector` already names a parameter (starts with ``$``), False if it is
                raw vector bytes that `to_args` requires a generated parameter name for.

        Note:
            Only a `str` starting with ``$`` is a parameter reference. A raw vector's packed bytes
            can legitimately start with ``0x24`` (``$``), so `bytes`/`bytearray` are always raw
            vector data, whatever their first byte.
        """
        return isinstance(self.vector, str) and self.vector.startswith("$")

    def to_args(
        self, generated_param_name: Optional[TEncodable] = None
    ) -> List[TEncodable]:
        """
        Get the arguments for the VSIM clause.

        Args:
            generated_param_name (Optional[TEncodable]): The ``$``-prefixed parameter name to
                reference on the wire when `vector` was given as raw bytes rather than an explicit
                ``$name`` string. Supplied by `FtHybridOptions.to_args` when needed; ignored if
                `vector` is already a parameter reference.

        Returns:
            List[TEncodable]: A list of VSIM clause arguments.
        """
        param_ref: TEncodable
        if self.is_param_reference():
            param_ref = self.vector
        else:
            if generated_param_name is None:
                raise ValueError(
                    "generated_param_name is required when `vector` is raw bytes "
                    "rather than an explicit $name parameter reference"
                )
            param_ref = generated_param_name
        field_ref: TEncodable
        if isinstance(self.field, (bytes, bytearray, memoryview)):
            field_ref = b"@" + bytes(self.field)
        else:
            field_ref = f"@{self.field}"
        args: List[TEncodable] = [FtHybridKeywords.VSIM, field_ref, param_ref]
        if (
            self.k is not None
            or self.ef_runtime is not None
            or self.shard_k_ratio is not None
        ):
            sub_args: List[TEncodable] = []
            if self.k is not None:
                sub_args.extend([FtHybridKeywords.K, str(self.k)])
            if self.ef_runtime is not None:
                sub_args.extend([FtHybridKeywords.EF_RUNTIME, str(self.ef_runtime)])
            if self.shard_k_ratio is not None:
                sub_args.extend(
                    [FtHybridKeywords.SHARD_K_RATIO, str(self.shard_k_ratio)]
                )
            args.extend([FtHybridKeywords.KNN, str(len(sub_args))] + sub_args)
        if self.yield_score_as is not None:
            args.extend([FtHybridKeywords.YIELD_SCORE_AS, self.yield_score_as])
        if self.filter is not None:
            args.extend(self.filter.to_args())
        return args


class FtHybridCombine(ABC):
    """
    Abstract base class for the COMBINE clause of FT.HYBRID, controlling how the SEARCH and VSIM
    arms' per-document scores are fused into one ranking. See `FtHybridCombine.RRF`,
    `FtHybridCombine.Linear` and `FtHybridCombine.Function`.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    RRF: ClassVar[Type["FtHybridRrf"]]
    Linear: ClassVar[Type["FtHybridLinear"]]
    Function: ClassVar[Type["FtHybridFunction"]]

    @abstractmethod
    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the COMBINE clause.

        Returns:
            List[TEncodable]: A list of COMBINE clause arguments.
        """
        args: List[TEncodable] = []
        return args


class FtHybridRrf(FtHybridCombine):
    """
    Fuses the two arms' results using Reciprocal Rank Fusion: each arm's rank (not its raw score)
    contributes ``1 / (constant + rank)`` to the fused score.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        constant: Optional[float] = None,
        window: Optional[int] = None,
        yield_score_as: Optional[TEncodable] = None,
    ):
        """
        Initialize a new FtHybridRrf instance.

        Args:
            constant (Optional[float]): The RRF constant. Must be non-negative: the server rejects a
                negative value.
            window (Optional[int]): How many of each arm's results take part in fusion (and, in
                cluster mode, how many each shard is asked to fetch). Bounded by the server's
                ``max-combine-window`` config.
            yield_score_as (Optional[TEncodable]): Exposes the fused score under the given column
                name instead of the default ``__score``.

        Raises:
            ValueError: If `constant` is negative.
        """
        if constant is not None and constant < 0:
            raise ValueError("constant must not be negative.")
        self.constant = constant
        self.window = window
        self.yield_score_as = yield_score_as

    def to_args(self) -> List[TEncodable]:
        sub_args: List[TEncodable] = []
        if self.constant is not None:
            sub_args.extend([FtHybridKeywords.CONSTANT, str(self.constant)])
        if self.window is not None:
            sub_args.extend([FtHybridKeywords.WINDOW, str(self.window)])
        if self.yield_score_as is not None:
            sub_args.extend([FtHybridKeywords.YIELD_SCORE_AS, self.yield_score_as])
        return [
            FtHybridKeywords.COMBINE,
            FtHybridKeywords.RRF,
            str(len(sub_args)),
        ] + sub_args


class FtHybridLinear(FtHybridCombine):
    """
    Fuses the two arms' results linearly: ``alpha * search_score + beta * vsim_score``. When
    neither `alpha` nor `beta` is written, the server defaults them to 0.3 and 0.7. Writing exactly
    one without the other is rejected by the server.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        alpha: Optional[float] = None,
        beta: Optional[float] = None,
        window: Optional[int] = None,
        yield_score_as: Optional[TEncodable] = None,
    ):
        """
        Initialize a new FtHybridLinear instance.

        Args:
            alpha (Optional[float]): The SEARCH arm's weight. Defaults (server-side) to ``0.3``
                when neither `alpha` nor `beta` is set. Setting one without the other is rejected
                by the server.
            beta (Optional[float]): The VSIM arm's weight. Defaults (server-side) to ``0.7`` when
                neither `alpha` nor `beta` is set.
            window (Optional[int]): How many of each arm's results take part in fusion.
            yield_score_as (Optional[TEncodable]): Exposes the fused score under the given column
                name instead of the default ``__score``.

        Raises:
            ValueError: If exactly one of `alpha`/`beta` is set without the other.
        """
        if (alpha is None) != (beta is None):
            raise ValueError(
                "alpha and beta must both be set, or both left unset -- the "
                "server rejects writing exactly one of them."
            )
        self.alpha = alpha
        self.beta = beta
        self.window = window
        self.yield_score_as = yield_score_as

    def to_args(self) -> List[TEncodable]:
        sub_args: List[TEncodable] = []
        if self.alpha is not None:
            sub_args.extend([FtHybridKeywords.ALPHA, str(self.alpha)])
        if self.beta is not None:
            sub_args.extend([FtHybridKeywords.BETA, str(self.beta)])
        if self.window is not None:
            sub_args.extend([FtHybridKeywords.WINDOW, str(self.window)])
        if self.yield_score_as is not None:
            sub_args.extend([FtHybridKeywords.YIELD_SCORE_AS, self.yield_score_as])
        return [
            FtHybridKeywords.COMBINE,
            FtHybridKeywords.LINEAR,
            str(len(sub_args)),
        ] + sub_args


class FtHybridFunction(FtHybridCombine):
    """
    Fuses the two arms' results using a caller-supplied expression. ``expr`` is a raw string
    written in the same expression language `FtAggregateApply`'s ``expression`` uses -- the server
    compiles it against a context where each arm's score is reachable via its own
    ``YIELD_SCORE_AS`` alias (if any), a positional default ``@__arm<i>_score``, and (for this
    SEARCH+VSIM two-arm shape) ``@__search_score`` / ``@__vector_score`` specifically.

    This is a valkey-search extension not present in RediSearch's FT.HYBRID.

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        expr: TEncodable,
        window: Optional[int] = None,
        yield_score_as: Optional[TEncodable] = None,
    ):
        """
        Initialize a new FtHybridFunction instance.

        Args:
            expr (TEncodable): The fusion expression, as a raw string (e.g.
                ``"@__search_score + @__vector_score"`` or, with explicit arm aliases,
                ``"@ss + @vs"``).
            window (Optional[int]): How many of each arm's results take part in fusion. If left
                unset, the server uses the widest window allowed rather than the usual RRF/LINEAR
                default, since a caller-written expression typically expects to see every matching
                candidate.
            yield_score_as (Optional[TEncodable]): Exposes the fused score under the given column
                name instead of the default ``__score``.
        """
        self.expr = expr
        self.window = window
        self.yield_score_as = yield_score_as

    def to_args(self) -> List[TEncodable]:
        sub_args: List[TEncodable] = [FtHybridKeywords.EXPR, self.expr]
        if self.window is not None:
            sub_args.extend([FtHybridKeywords.WINDOW, str(self.window)])
        if self.yield_score_as is not None:
            sub_args.extend([FtHybridKeywords.YIELD_SCORE_AS, self.yield_score_as])
        return [
            FtHybridKeywords.COMBINE,
            FtHybridKeywords.FUNCTION,
            str(len(sub_args)),
        ] + sub_args


FtHybridCombine.RRF = FtHybridRrf
FtHybridCombine.Linear = FtHybridLinear
FtHybridCombine.Function = FtHybridFunction


class FtHybridOptions:
    """
    This class represents the optional arguments for the FT.HYBRID command, applied to the
    aggregate-suffix tail that follows the mandatory SEARCH, VSIM and optional COMBINE clauses
    (which are passed directly to `glide.async_commands.ft.hybrid`/
    `glide_sync.sync_commands.ft.hybrid`, not through this class).

    Since: Valkey 9.1.0 and Valkey Search 1.3.0.
    """

    def __init__(
        self,
        combine: Optional[FtHybridCombine] = None,
        load_all: Optional[bool] = False,
        load_fields: Optional[List[Union[TEncodable, LoadField]]] = None,
        clauses: Optional[List[FtAggregateClause]] = None,
        with_cursor: Optional[FtWithCursor] = None,
        local_only: Optional[bool] = False,
        timeout: Optional[int] = None,
        params: Optional[Mapping[TEncodable, TEncodable]] = None,
        dialect: Optional[int] = None,
    ):
        """
        Initialize a new FtHybridOptions instance.

        Args:
            combine (Optional[FtHybridCombine]): Controls how the SEARCH and VSIM arms' scores
                are fused. See `FtHybridCombine.RRF`, `FtHybridCombine.Linear` and
                `FtHybridCombine.Function`. If omitted, the server's own default fusion method
                applies.
            load_all (Optional[bool]): Loads every field declared in the index, plus both arms'
                score aliases. Because ``LOAD *`` names no fields, it has no equivalent of the
                LOAD-clause alias restriction described on `FtHybridSearchArm.yield_score_as`.
            load_fields (Optional[List[Union[TEncodable, LoadField]]]): Loads only the fields
                passed in this list, reusing `LoadField` from the FT.AGGREGATE options module
                verbatim (the LOAD clause's grammar is byte-identical between the two commands).
                Must NOT include either arm's `yield_score_as` alias: the server rejects it, even
                though the alias is otherwise reachable from other clauses; see
                `FtHybridSearchArm.yield_score_as`. With no LOAD clause at all (both `load_all` and
                `load_fields` left unset), FT.HYBRID's default reply is the document key and the
                fused score, which a default FT.AGGREGATE does not return.
            clauses (Optional[List[FtAggregateClause]]): FILTER, LIMIT, GROUPBY, SORTBY and APPLY
                clauses, reusing `FtAggregateClause` and its subclasses from the FT.AGGREGATE options
                module. FT.HYBRID passes this tail to the same aggregate pipeline parser FT.AGGREGATE
                uses. Every arm alias is reachable from these clauses (APPLY, SORTBY, GROUPBY/REDUCE);
                only the LOAD clause has the restriction described above. FT.HYBRID applies its own
                default LIMIT of 10 rows when no `FtAggregateLimit` clause is given here, whereas
                FT.AGGREGATE returns every row. `to_args` emits `clauses` after `params`. PARAMS is
                present whenever the VSIM arm carries a vector, so a leading FILTER clause never lands
                directly after the VSIM arm, where the server would read it as a VSIM pre-filter.
            with_cursor (Optional[FtWithCursor]): Requests a paged reply. See
                `FtWithCursor`.
            local_only (Optional[bool]): Restricts the query to the local shard rather than fanning
                out across the cluster. This is a valkey-search extension not present in RediSearch's
                FT.HYBRID. LOCALONLY is accepted only before PARAMS on the wire, and the server rejects
                it once PARAMS has been parsed. `to_args` always emits it before `params` and `clauses`,
                so the order of the options does not affect it.
            timeout (Optional[int]): Overrides the timeout parameter of the module, in
                milliseconds.
            params (Optional[Mapping[TEncodable, TEncodable]]): Param key/value pairs referenced
                from a VSIM arm's ``$name`` vector reference, or from FILTER and APPLY clause
                expressions. The SEARCH arm never substitutes parameters: a ``$name`` written in the
                SEARCH query is passed through as literal text. If a `FtHybridVsimArm` in the same
                call is given raw vector bytes rather than an explicit ``$name`` string, an internally
                generated entry is merged into this mapping automatically. A name collision with a
                caller-supplied entry here raises `ValueError` rather than silently overwriting either
                one.
            dialect (Optional[int]): The query dialect version to use. FT.HYBRID is fixed at
                dialect 2 server-side; passing any other value is rejected by the server.
        """
        self.combine = combine
        self.load_all = load_all
        self.load_fields = load_fields
        self.clauses = clauses
        self.with_cursor = with_cursor
        self.local_only = local_only
        self.timeout = timeout
        self.params = params
        self.dialect = dialect

    def _merge_params(
        self, generated_vsim_param: Optional[Mapping[TEncodable, TEncodable]]
    ) -> dict:
        merged_params: dict = dict(self.params) if self.params else {}
        if generated_vsim_param:
            for name, value in generated_vsim_param.items():
                if name in merged_params:
                    raise ValueError(
                        f"Generated VSIM parameter name '{name!r}' collides with "
                        "an entry already present in params; rename the "
                        "caller-supplied params entry."
                    )
                merged_params[name] = value
        return merged_params

    def to_args(
        self, generated_vsim_param: Optional[Mapping[TEncodable, TEncodable]] = None
    ) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.HYBRID command's aggregate-suffix tail.

        Args:
            generated_vsim_param (Optional[Mapping[TEncodable, TEncodable]]): An internally
                generated PARAMS entry for a `FtHybridVsimArm` given raw vector bytes, merged with
                `params` here. Supplied by `glide.async_commands.ft.hybrid`/
                `glide_sync.sync_commands.ft.hybrid`; not intended to be set directly by callers.

        Returns:
            List[TEncodable]: A list of FT.HYBRID optional arguments.

        Raises:
            ValueError: If `generated_vsim_param`'s key collides with an entry already present in
                `params`.
        """
        args: List[TEncodable] = []
        if self.local_only:
            args.append(FtHybridKeywords.LOCALONLY)
        if self.load_all:
            args.extend([FtAggregateKeywords.LOAD, "*"])
        else:
            args.extend(load_fields_to_args(self.load_fields))
        if self.timeout is not None:
            args.extend([FtHybridKeywords.TIMEOUT, str(self.timeout)])
        args.extend(
            params_to_args(
                self._merge_params(generated_vsim_param),
                params_keyword=FtHybridKeywords.PARAMS,
            )
        )
        if self.clauses:
            for clause in self.clauses:
                args.extend(clause.to_args())
        if self.with_cursor is not None:
            args.extend(self.with_cursor.to_args())
        if self.dialect is not None:
            args.extend([FtHybridKeywords.DIALECT, str(self.dialect)])
        return args
