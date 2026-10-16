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
    search. Only meaningful inside a counted VSIM FILTER block -- valkey-search's existing
    uncounted ``FILTER <expr>`` extension on VSIM (no leading token count) cannot carry a policy at
    all; the server requires the counted form to accept POLICY, confirmed live
    (``src/commands/ft_hybrid_parser.cc``: POLICY and BATCH_SIZE are only recognised inside the
    counted VSIM FILTER block's own sub-argument scan). This is a genuinely different grammar
    axis from `FtHybridCombine`'s RRF/LINEAR/FUNCTION method -- one controls how the vector search
    itself is pre-filtered, the other controls how the SEARCH and VSIM arms' scores are fused
    after both arms have already run.
    """

    ADHOC = "ADHOC"
    """Evaluates the filter against every candidate as it is produced (brute-force), rather than
    pre-restricting the vector index's own search space. ``EF_RUNTIME`` on the same VSIM arm is
    rejected when this policy is set (confirmed live: "EF_RUNTIME is irrelevant for POLICY
    ADHOC")."""
    BATCHES = "BATCHES"
    """Evaluates the filter in batches drawn from the vector index. Required for `batch_size` to
    be set on `FtHybridVsimFilter` (confirmed live: "BATCH_SIZE requires POLICY BATCHES")."""


class FtHybridVsimFilter:
    """
    A pre-filter on the VSIM arm's vector search, written in the same query language the SEARCH
    arm uses (e.g. ``@title:hello``). Confirmed live: with a filter present, the VSIM arm becomes
    an ordinary FT.SEARCH-style vector query under the hood (``<filter>=>[KNN ...]``), so the
    filter decides membership and the arm's score stays the vector distance -- it is never
    replaced by a text-relevance score the way a plain hybrid text query's score would be.
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
                `HybridFilterPolicy`. Setting this forces the wire-level counted VSIM FILTER form
                (required for the server to recognise POLICY at all -- confirmed live).
            batch_size (Optional[int]): Tunes how many candidates ``POLICY BATCHES`` draws per
                batch. Requires ``policy=HybridFilterPolicy.BATCHES``. Confirmed live: the value is
                accepted but currently discarded by the server (valkey-search issue #1457 is
                referenced directly in ``src/commands/ft_hybrid_parser.cc`` for this), so this
                currently has no observable effect; included for forward compatibility and because
                the server still validates its presence/ordering against ``policy``.

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
            # Uncounted extension: a bare expression, no POLICY/BATCH_SIZE
            # possible in this form (confirmed live).
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
    ``=>[KNN ...]`` clause) is rejected here -- confirmed live and in
    ``src/commands/ft_hybrid_parser.cc`` -- use `FtHybridVsimArm` for the vector half instead.
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
            scorer (Optional[TEncodable]): The scoring function used to rank this arm's results
                (valkey-search 1.3.0, ``src/indexes/scoring/scorer.h``). ``"BM25STD"`` is the only
                value currently registered and selectable; any other value is rejected by the
                server.
            yield_score_as (Optional[TEncodable]): Exposes this arm's score under the given
                column name, reachable from `FtAggregateApply`, `FtAggregateSortProperty`,
                `FtAggregateGroupBy`/`FtAggregateReducer` expressions, and `FtHybridCombine`'s
                ``Function`` variant's ``expr``. Confirmed live: this alias cannot itself be named
                inside a `LoadField`/`FtHybridOptions.load_fields` entry (the server rejects it
                with "Index field `<alias>` does not exist") -- it is already present in the
                default reply with no LOAD needed, and remains reachable from every other clause
                type regardless of whether a LOAD clause is present.
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
      PARAMS entry under the hood and references it on the wire. Confirmed live and in
      ``src/commands/ft_hybrid_parser.cc``: FT.HYBRID's VSIM clause has no wire-level inline
      vector literal at all -- the parameter token must start with ``$`` or the server rejects it
      outright ("VSIM: vector parameter must start with '$'"), so an auto-generated PARAMS entry
      is the only way to offer a "just pass the bytes" call shape.
    - As an explicit ``$name`` string: the caller manages their own entry in
      `FtHybridOptions.params`, exactly like `FtSearchOptions.params`/`FtAggregateOptions.params`
      already work for every other PARAMS-driven query.
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
            k (Optional[int]): The number of nearest neighbours to return. Confirmed live:
                optional on the wire -- omitting the whole KNN block (which happens when `k`,
                `ef_runtime` and `shard_k_ratio` are all unset) defaults to 10
                (``src/commands/ft_hybrid_parser.cc`` ``kDefaultKnnK``), the same default RediSearch
                documents for FT.HYBRID's VSIM clause.
            ef_runtime (Optional[int]): HNSW search-time effort parameter. Only valid when the KNN
                block is written (RANGE is not currently supported by this typed API).
            shard_k_ratio (Optional[float]): Tunes how much of `k` each shard returns during a
                cluster fanout. Confirmed live: parsed and accepted but does not change query
                results, only how much work each shard does to produce them -- accepted here so a
                query written for RediSearch is not rejected by this client either.
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
            Only a `str` starting with ``$`` is treated as a parameter reference. A raw vector's
            packed binary representation is arbitrary and can legitimately start with the byte
            ``0x24`` (``$``) by coincidence; treating `bytes`/`bytearray` the same way as `str`
            here would misclassify that vector as a parameter reference, skip generating the
            internal `PARAMS` entry in `to_args`, and send the raw vector bytes in the
            parameter-reference position, which the server cannot resolve. `bytes`/`bytearray` is
            therefore always raw vector data regardless of its first byte.
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
        if isinstance(self.field, (bytes, bytearray)):
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
            constant (Optional[float]): The RRF constant. Must be non-negative (confirmed live:
                the server rejects a negative value -- it places a pole at ``rank == -constant``).
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
    Fuses the two arms' results linearly: ``alpha * search_score + beta * vsim_score``. Confirmed
    live: writing neither `alpha` nor `beta` defaults both to 0.3/0.7 respectively; writing exactly
    one without the other is rejected by the server as more likely a typo than an intentional
    default.
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
    SEARCH+VSIM two-arm shape) ``@__search_score`` / ``@__vector_score`` specifically. Confirmed
    live and in ``src/commands/ft_hybrid_parser.cc``.

    This is a valkey-search extension not present in RediSearch's FT.HYBRID (included here
    anyway rather than dropped).
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
            window (Optional[int]): How many of each arm's results take part in fusion. Confirmed
                live: if left unset, the server defaults this to the widest window allowed rather
                than the usual RRF/LINEAR default, since a caller-written expression typically
                expects to see every matching candidate, not a top-window slice.
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


# Namespaced access (FtHybridCombine.RRF(...), .Linear(...), .Function(...)) alongside the
# importable top-level names above, mirroring how callers may prefer either style.
FtHybridCombine.RRF = FtHybridRrf
FtHybridCombine.Linear = FtHybridLinear
FtHybridCombine.Function = FtHybridFunction


class FtHybridOptions:
    """
    This class represents the optional arguments for the FT.HYBRID command, applied to the
    aggregate-suffix tail that follows the mandatory SEARCH, VSIM and optional COMBINE clauses
    (which are passed directly to `glide.async_commands.ft.hybrid`/
    `glide_sync.sync_commands.ft.hybrid`, not through this class).
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
                score aliases automatically. Confirmed live: unlike a named `load_fields` list,
                ``LOAD *`` cannot name an arm's score alias at all (there is nothing to name), so
                this form has no equivalent of the LOAD-clause alias restriction described on
                `FtHybridSearchArm.yield_score_as`.
            load_fields (Optional[List[Union[TEncodable, LoadField]]]): Loads only the fields
                passed in this list, reusing `LoadField` from the FT.AGGREGATE options module
                verbatim (the LOAD clause's grammar is byte-identical between the two commands).
                Must NOT include either arm's `yield_score_as` alias -- confirmed live, the server
                rejects it outright even though the alias is otherwise freely reachable; see
                `FtHybridSearchArm.yield_score_as`'s docstring. With no LOAD clause at all (both
                `load_all` and `load_fields` left unset), FT.HYBRID's default reply is the
                document key and the fused score -- a default FT.AGGREGATE has no equivalent of.
            clauses (Optional[List[FtAggregateClause]]): FILTER, LIMIT, GROUPBY, SORTBY and APPLY
                clauses, reusing `FtAggregateClause` and its subclasses verbatim from the
                FT.AGGREGATE options module (confirmed: FT.HYBRID forwards this whole tail to the
                exact same aggregate pipeline parser FT.AGGREGATE uses). Confirmed live: every arm
                alias is freely reachable from these clauses (APPLY, SORTBY, GROUPBY/REDUCE) --
                only the LOAD clause has the restriction described above. FT.HYBRID also applies
                its own default LIMIT of 10 rows when no `FtAggregateLimit` clause is given here
                (confirmed live and in source) -- FT.AGGREGATE has no such default and returns
                everything. `to_args` always emits `clauses` after `params`'s own serialized
                arguments (PARAMS is always present for FT.HYBRID, so this tail is never empty) --
                emitting a leading FILTER clause right after the VSIM arm would otherwise let the
                server's VSIM parser claim it as a VSIM pre-filter instead (confirmed live,
                `ft_hybrid_parser.cc`'s `vsim_scoped` FILTER handling).
            with_cursor (Optional[FtWithCursor]): Requests a paged reply. See
                `FtWithCursor`.
            local_only (Optional[bool]): Restricts the query to the local shard rather than
                fanning out across the cluster. This is a valkey-search extension not present in
                RediSearch's FT.HYBRID (included here anyway rather than
                dropped). Confirmed live: LOCALONLY is accepted anywhere in this tail BEFORE
                PARAMS (including after DIALECT or a LOAD clause) but is rejected
                ("Unexpected argument `LOCALONLY`") once PARAMS has been parsed, regardless of
                what follows PARAMS. `to_args` always emits it before `params` and `clauses`
                (PARAMS always follows it immediately), so this restriction is handled
                transparently regardless of the order its own arguments were given in.
            timeout (Optional[int]): Overrides the timeout parameter of the module, in
                milliseconds.
            params (Optional[Mapping[TEncodable, TEncodable]]): Param key/value pairs referenced
                from within the SEARCH query, a VSIM arm's ``$name`` vector reference, or any
                clause's expression text. If a `FtHybridVsimArm` in the same call is given raw
                vector bytes rather than an explicit ``$name`` string, an internally generated
                entry is merged into this mapping automatically -- a name collision with a
                caller-supplied entry here raises `ValueError` rather than silently overwriting
                either one.
            dialect (Optional[int]): The query dialect version to use. FT.HYBRID is fixed at
                dialect 2 server-side (confirmed in source); passing any other value is rejected
                by the server.
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
        """Merge `params` with an internally generated VSIM PARAMS entry, raising if the
        generated name collides with a caller-supplied one."""
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
            # Must come before PARAMS -- confirmed live, see the docstring
            # above. `to_args` always emits it this early regardless of
            # argument order, so this restriction is handled transparently.
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
            # Must come after PARAMS -- a leading FILTER clause emitted
            # right after the VSIM arm would otherwise be claimed by the
            # server's VSIM parser as a VSIM pre-filter instead (confirmed
            # live, see the docstring above).
            for clause in self.clauses:
                args.extend(clause.to_args())
        if self.with_cursor is not None:
            args.extend(self.with_cursor.to_args())
        if self.dialect is not None:
            args.extend([FtHybridKeywords.DIALECT, str(self.dialect)])
        return args
