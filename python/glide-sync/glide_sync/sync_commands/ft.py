# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""
module for `vector search` commands.

Transactions: these commands are ordinary custom commands as far as GLIDE's batch/transaction
machinery is concerned. Inside an atomic batch (``ClusterBatch`` with ``is_atomic=True``) on a
Cluster client, every command in the batch -- including any FT.* command -- must map to the same
slot, the same rule every other cluster command follows; there is no FT-specific exemption or
special case.
"""

from typing import List, Mapping, Optional, cast
from uuid import uuid4

from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_constants import (
    CommandNames,
    FtCreateKeywords,
    FtCursorKeywords,
)
from glide_shared.commands.server_modules.ft_options.ft_create_options import (
    Field,
    FtCreateOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_hybrid_options import (
    FtHybridOptions,
    FtHybridSearchArm,
    FtHybridVsimArm,
)
from glide_shared.commands.server_modules.ft_options.ft_profile_options import (
    FtProfileOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    ConsistencyMode,
    FtSearchOptions,
    InfoScope,
    ShardScope,
)
from glide_shared.constants import (
    TOK,
    FtAggregateResponse,
    FtInfoResponse,
    FtProfileResponse,
    FtSearchResponse,
    TEncodable,
)

from ..glide_client import TGlideClient


def create(
    client: TGlideClient,
    index_name: TEncodable,
    schema: List[Field],
    options: Optional[FtCreateOptions] = None,
) -> TOK:
    """
    Creates an index and initiates a backfill of that index.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name.
        schema (List[Field]): Fields to populate into the index. Equivalent to `SCHEMA` block in the module API.
        options (Optional[FtCreateOptions]): Optional arguments for the FT.CREATE command.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        >>> from glide import ft
        >>> schema: List[Field] = [TextField("title")]
        >>> prefixes: List[str] = ["blog:post:"]
        >>> ft.create(glide_client, "my_idx1", schema, FtCreateOptions(DataType.HASH, prefixes))
            'OK'  # Indicates successful creation of index named 'idx'
    """
    args: List[TEncodable] = [CommandNames.FT_CREATE, index_name]
    if options:
        args.extend(options.to_args())
    if schema:
        args.append(FtCreateKeywords.SCHEMA)
        for field in schema:
            args.extend(field.to_args())
    return cast(TOK, client.custom_command(args))


def dropindex(client: TGlideClient, index_name: TEncodable) -> TOK:
    """
    Drops an index. The index definition and associated content are deleted. Keys are unaffected.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name for the index to be dropped.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        For the following example to work, an index named 'idx' must be already created. If not created, you will get an error.
        >>> from glide import ft
        >>> index_name = "idx"
        >>> ft.dropindex(glide_client, index_name)
            'OK'  # Indicates successful deletion/dropping of index named 'idx'
    """
    args: List[TEncodable] = [CommandNames.FT_DROPINDEX, index_name]
    return cast(TOK, client.custom_command(args))


def list(client: TGlideClient) -> List[TEncodable]:
    """
    Lists all indexes.

    Args:
        client (TGlideClient): The client to execute the command.

    Returns:
        List[TEncodable]: An array of index names.

    Examples:
        >>> from glide import ft
        >>> ft.list(glide_client)
            [b"index1", b"index2"]
    """
    return cast(List[TEncodable], client.custom_command([CommandNames.FT_LIST]))


def search(
    client: TGlideClient,
    index_name: TEncodable,
    query: TEncodable,
    options: Optional[FtSearchOptions] = None,
) -> FtSearchResponse:
    """
    Uses the provided query expression to locate keys within an index. Once located, the count and/or the content of indexed
    fields within those keys can be returned.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name to search into.
        query (TEncodable): The text query to search. A text/tag predicate can carry a trailing ``=>{$weight:
            <value>}`` attribute to scale its contribution to the relevance score, e.g. ``@title:hello=>{$weight:
            2.0}`` (valkey-search 1.3.0, ``src/commands/filter_parser.cc`` -- confirmed empirically: this attaches
            to a predicate, not to a vector ``[KNN ...]`` clause, which uses ``=>[`` rather than ``=>{`` and
            therefore cannot carry this block). A KNN clause's own prefiltering strategy is instead controlled by
            ``HYBRID_POLICY BATCHES`` / ``ADHOC_BF`` as a parameter inside the ``[KNN k @field $vec ...]`` bracket
            itself (``src/query/search.cc``) -- confirmed this requires the overall query to be genuinely hybrid
            (a real predicate combined with the KNN clause, or ``INKEYS``; a bare ``*=>[KNN ...]`` is rejected with
            "hybrid query attributes were sent for a non-hybrid query"), so it is not usable on every KNN query the
            way this docstring originally assumed. GLIDE passes the whole query string through verbatim either
            way; neither mechanism requires client-side support, the same way ``$yield_distance_as`` on
            ``VECTOR_RANGE`` already works.
        options (Optional[FtSearchOptions]): The search options.

    Returns:
        FtSearchResponse: A two element array, where first element is count of documents in result set, and the second
            element, which has the format Mapping[TEncodable, Mapping[TEncodable, TEncodable]] is a mapping between document
            names and map of their attributes. When ``nocontent`` is set, the attribute maps will be empty.
        If count(option in `FtSearchOptions`) is set to true or limit(option in `FtSearchOptions`) is set to
            FtSearchLimit(0, 0), the command returns array with only one element - the count of the documents.
        When ``withsortkeys`` is set, each attribute map is paired with its sort key, passed through exactly as
            the server returns it (including its ``#``/``$`` type prefix, or ``None`` for a document missing the
            sort field) with no client-side reinterpretation.

    Examples:
        For the following example to work the following must already exist:
        - An index named "idx", with fields having identifiers as "a" and "b" and prefix as "{json:}"
        - A key named {json:}1 with value {"a":1, "b":2}

        >>> from glide import ft
        >>> ft.search(
        ...     glide_client,
        ...     "idx",
        ...     "*",
        ...     options=FtSeachOptions(
        ...         return_fields=[
        ...             ReturnField(field_identifier="first"),
        ...             ReturnField(field_identifier="second")
        ...         ]
        ...     )
        ... )
        [1, { b'json:1': { b'first': b'42', b'second': b'33' } }]
        # The first element, 1 is the number of keys returned in the search result. The second element is a map of
        # data queried per key.
    """
    args: List[TEncodable] = [CommandNames.FT_SEARCH, index_name, query]
    if options:
        args.extend(options.to_args())
    return cast(FtSearchResponse, client.custom_command(args))


def aliasadd(client: TGlideClient, alias: TEncodable, index_name: TEncodable) -> TOK:
    """
    Adds an alias for an index. The new alias name can be used anywhere that an index name is required.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT.ALIASADD
        outright with "unknown command" (waiting on https://github.com/valkey-io/valkey-search/pull/1184).
        This function remains part of the public API for parity with RediSearch clients, but any call
        currently raises a `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
        alias (TEncodable): The alias to be added to an index.
        index_name (TEncodable): The index name for which the alias has to be added.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        >>> from glide import ft
        >>> ft.aliasadd(glide_client, "myalias", "myindex")
            'OK'  # Indicates the successful addition of the alias named "myalias" for the index.
    """
    args: List[TEncodable] = [CommandNames.FT_ALIASADD, alias, index_name]
    return cast(TOK, client.custom_command(args))


def aliasdel(client: TGlideClient, alias: TEncodable) -> TOK:
    """
    Deletes an existing alias for an index.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT.ALIASDEL
        outright with "unknown command" (waiting on https://github.com/valkey-io/valkey-search/pull/1184).
        This function remains part of the public API for parity with RediSearch clients, but any call
        currently raises a `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
        alias (TEncodable): The existing alias to be deleted for an index.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        >>> from glide import ft
        >>> ft.aliasdel(glide_client, "myalias")
            'OK'  # Indicates the successful deletion of the alias named "myalias"
    """
    args: List[TEncodable] = [CommandNames.FT_ALIASDEL, alias]
    return cast(TOK, client.custom_command(args))


def aliasupdate(client: TGlideClient, alias: TEncodable, index_name: TEncodable) -> TOK:
    """
    Updates an existing alias to point to a different physical index. This command only affects future references to the alias.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT.ALIASUPDATE
        outright with "unknown command" (waiting on https://github.com/valkey-io/valkey-search/pull/1184).
        This function remains part of the public API for parity with RediSearch clients, but any call
        currently raises a `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
        alias (TEncodable): The alias name. This alias will now be pointed to a different index.
        index_name (TEncodable): The index name for which an existing alias has to updated.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        >>> from glide import ft
        >>> ft.aliasupdate(glide_client, "myalias", "myindex")
            'OK'  # Indicates the successful update of the alias to point to the index named "myindex"
    """
    args: List[TEncodable] = [CommandNames.FT_ALIASUPDATE, alias, index_name]
    return cast(TOK, client.custom_command(args))


def info(
    client: TGlideClient,
    index_name: TEncodable,
    scope: Optional["InfoScope"] = None,
    shard_scope: Optional["ShardScope"] = None,
    consistency: Optional["ConsistencyMode"] = None,
) -> FtInfoResponse:
    """
    Returns information about a given index.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name for which the information has to be returned.
        scope (Optional[InfoScope]): Controls which nodes provide index information (LOCAL, PRIMARY, CLUSTER).
        shard_scope (Optional[ShardScope]): Controls shard participation (ALLSHARDS, SOMESHARDS).
        consistency (Optional[ConsistencyMode]): Controls consistency requirements (CONSISTENT, INCONSISTENT).

    Returns:
        FtInfoResponse: Nested maps with info about the index. See example for more details.

    Note:
        `index_definition`'s `score_field` key (reporting `FtCreateOptions(score_field=...)`, see
        ``ft_create_options.py``) requires `search.emulate-release` >= 1.3.0 to appear at all
        (``ft_info_score_field`` compatibility flag) -- below that level the pair is omitted entirely,
        even if the index was created with `score_field` set. See upstream COMPATIBILITY.md's
        "Sunsetting of Incompatible Behavior" section.

    Examples:
        An index with name 'myIndex', 1 text field and 1 vector field is already created for gettting the output of this
        example.
        >>> from glide import ft
        >>> ft.info(glide_client, "myIndex")
            [
                b'index_name',
                b'myIndex',
                b'creation_timestamp', 1729531116945240,
                b'key_type', b'JSON',
                b'key_prefixes', [b'key-prefix'],
                b'fields', [
                    [
                        b'identifier', b'$.vec',
                        b'field_name', b'VEC',
                        b'type', b'VECTOR',
                        b'option', b'',
                        b'vector_params', [
                            b'algorithm',
                            b'HNSW',
                            b'data_type',
                            b'FLOAT32',
                            b'dimension',
                            2,
                            b'distance_metric',
                            b'L2',
                            b'initial_capacity',
                            1000,
                            b'current_capacity',
                            1000,
                            b'maximum_edges',
                            16,
                            b'ef_construction',
                            200,
                            b'ef_runtime',
                            10,
                            b'epsilon',
                            b'0.01'
                        ]
                    ],
                    [
                        b'identifier', b'$.text-field',
                        b'field_name', b'text-field',
                        b'type', b'TEXT',
                        b'option', b''
                    ]
                ],
                b'space_usage', 653351,
                b'fulltext_space_usage', 0,
                b'vector_space_usage', 653351,
                b'num_docs', 0,
                b'num_indexed_vectors', 0,
                b'current_lag', 0,
                b'index_status', b'AVAILABLE',
                b'index_degradation_percentage', 0
            ]
    """
    args: List[TEncodable] = [CommandNames.FT_INFO, index_name]
    if scope is not None:
        args.append(scope.value)
    if shard_scope is not None:
        args.append(shard_scope.value)
    if consistency is not None:
        args.append(consistency.value)
    return cast(FtInfoResponse, client.custom_command(args))


def explain(
    client: TGlideClient, index_name: TEncodable, query: TEncodable
) -> TEncodable:
    """
    Parse a query and return information about how that query was parsed.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT.EXPLAIN
        outright with "unknown command" (the command is not registered at all). This function remains
        part of the public API for parity with RediSearch clients, but any call currently raises a
        `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name for which the query is written.
        query (TEncodable): The search query, same as the query passed as an argument to FT.SEARCH.

    Returns:
        TEncodable: A string containing the parsed results representing the execution plan.

    Examples:
        >>> from glide import ft
        >>> ft.explain(glide_client, indexName="myIndex", query="@price:[0 10]")
            b'Field {\n  price\n  0\n  10\n}\n' # Parsed results.
    """
    args: List[TEncodable] = [CommandNames.FT_EXPLAIN, index_name, query]
    return cast(TEncodable, client.custom_command(args))


def explaincli(
    client: TGlideClient, index_name: TEncodable, query: TEncodable
) -> List[TEncodable]:
    """
    Same as the FT.EXPLAIN command except that the results are displayed in a different format. More useful with cli.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT.EXPLAINCLI
        outright with "unknown command" (the command is not registered at all). This function remains
        part of the public API for parity with RediSearch clients, but any call currently raises a
        `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name for which the query is written.
        query (TEncodable): The search query, same as the query passed as an argument to FT.SEARCH.

    Returns:
        List[TEncodable]: An array containing the execution plan.

    Examples:
        >>> from glide import ft
        >>> ft.explaincli(glide_client, indexName="myIndex", query="@price:[0 10]")
            [b'Field {', b'  price', b'  0', b'  10', b'}', b''] # Parsed results.
    """
    args: List[TEncodable] = [CommandNames.FT_EXPLAINCLI, index_name, query]
    return cast(List[TEncodable], client.custom_command(args))


def aggregate(
    client: TGlideClient,
    index_name: TEncodable,
    query: TEncodable,
    options: Optional[FtAggregateOptions] = None,
) -> FtAggregateResponse:
    """
    A superset of the FT.SEARCH command, it allows substantial additional processing of the keys selected by the query
    expression.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name for which the query is written.
        query (TEncodable): The search query, same as the query passed as an argument to FT.SEARCH.
        options (Optional[FtAggregateOptions]): The optional arguments for the command.

    Returns:
        FtAggregateResponse: An array containing a mapping of field name and associated value as returned after the last stage
            of the command.

    Examples:
        >>> from glide import ft
        >>> ft.aggregate(
        ...     glide_client,
        ...     "myIndex",
        ...     "*",
        ...     FtAggregateOptions(
        ...         loadFields=["__key"],
        ...         clauses=[GroupBy(["@condition"], [Reducer("COUNT", [], "bicycles")])]
        ...    )
        ... )
            [
                {
                    b'condition': b'refurbished',
                    b'bicycles': b'1'
                },
                {
                    b'condition': b'new',
                    b'bicycles': b'5'
                },
                {
                    b'condition': b'used',
                    b'bicycles': b'4'
                }
            ]

        # Paged with WITHCURSOR:
        >>> rows, cursor_id = ft.aggregate(glide_client, "myIndex", "*",
        ...     FtAggregateOptions(loadFields=["__key"], with_cursor=FtWithCursor(count=10)))
        >>> while cursor_id != 0:
        ...     rows, cursor_id = ft.cursor_read(glide_client, "myIndex", cursor_id)
    """
    args: List[TEncodable] = [CommandNames.FT_AGGREGATE, index_name, query]
    if options:
        args.extend(options.to_args())
    return cast(FtAggregateResponse, client.custom_command(args))


def cursor_read(
    client: TGlideClient,
    index_name: TEncodable,
    cursor_id: int,
    count: Optional[int] = None,
) -> FtAggregateResponse:
    """
    Reads the next page of results from a cursor opened by `ft.aggregate` (with
    ``FtAggregateOptions(with_cursor=...)``) or `ft.hybrid` (with
    ``FtHybridOptions(with_cursor=...)``).

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name the cursor was opened against.
        cursor_id (int): The cursor id, from the opening command's reply or a previous
            `cursor_read` call. ``0`` means the cursor is already exhausted; calling this with
            ``0`` is a caller error, not something this function special-cases.
        count (Optional[int]): Overrides how many rows this page returns, instead of the count
            the cursor was opened with.

    Returns:
        FtAggregateResponse: A two-element array of ``[rows, cursor_id]``, where ``rows`` is the
            same per-document mapping shape `ft.aggregate`/`ft.hybrid` return, and ``cursor_id``
            is ``0`` once the cursor is exhausted, or the id to pass to the next `cursor_read`
            call otherwise.

    Note:
        Always reads from the PRIMARY of the index's slot, regardless of the client's
        ``ReadFrom`` configuration -- see the cursor-affinity note on `aggregate`/`hybrid`. A
        cursor opened by FT.HYBRID is read the same way as one opened by FT.AGGREGATE; the server
        does not distinguish which command created it (confirmed live and in
        ``src/commands/ft_cursor.cc``).

    Examples:
        >>> from glide_sync import ft
        >>> rows, cursor_id = ft.aggregate(glide_client, "myIndex", "*",
        ...     FtAggregateOptions(loadFields=["__key"], with_cursor=FtWithCursor(count=10)))
        >>> while cursor_id != 0:
        ...     rows, cursor_id = ft.cursor_read(glide_client, "myIndex", cursor_id)
    """
    args: List[TEncodable] = [
        CommandNames.FT_CURSOR,
        FtCursorKeywords.READ,
        index_name,
        str(cursor_id),
    ]
    if count is not None:
        args.extend([FtCursorKeywords.COUNT, str(count)])
    return cast(FtAggregateResponse, client.custom_command(args))


def cursor_del(client: TGlideClient, index_name: TEncodable, cursor_id: int) -> TOK:
    """
    Deletes an open cursor, discarding it before it would otherwise be exhausted or time out.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name the cursor was opened against.
        cursor_id (int): The cursor id to delete.

    Returns:
        TOK: A simple "OK" response.

    Note:
        Always reads from the PRIMARY of the index's slot, regardless of the client's
        ``ReadFrom`` configuration -- see the cursor-affinity note on `aggregate`/`hybrid`.

    Examples:
        >>> from glide_sync import ft
        >>> rows, cursor_id = ft.aggregate(glide_client, "myIndex", "*",
        ...     FtAggregateOptions(loadFields=["__key"], with_cursor=FtWithCursor(count=1)))
        >>> ft.cursor_del(glide_client, "myIndex", cursor_id)
            'OK'
    """
    args: List[TEncodable] = [
        CommandNames.FT_CURSOR,
        FtCursorKeywords.DEL,
        index_name,
        str(cursor_id),
    ]
    return cast(TOK, client.custom_command(args))


def profile(
    client: TGlideClient, index_name: TEncodable, options: FtProfileOptions
) -> FtProfileResponse:
    """
    Runs a search or aggregation query and collects performance profiling information.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT.PROFILE
        outright with "unknown command" (the command is not registered at all). This
        function remains part of the public API for parity with RediSearch clients, but any call
        currently raises a `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name
        options (FtProfileOptions): Options for the command.

    Returns:
        FtProfileResponse: A two-element array. The first element contains results of query being profiled, the second element
            stores profiling information.

    Examples:
        >>> ftSearchOptions = FtSeachOptions(
        ...     return_fields=[
        ...         ReturnField(field_identifier="a", alias="a_new"),
        ...         ReturnField(field_identifier="b", alias="b_new")
        ...     ]
        ... )
        >>> ft.profile(
        ...     glide_client,
        ...     "myIndex",
        ...     FtProfileOptions.from_query_options(query="*", queryOptions=ftSearchOptions)
        ... )
            [
                [
                    2,
                    {
                        b'key1': {
                            b'a': b'11111',
                            b'b': b'2'
                        },
                        b'key2': {
                            b'a': b'22222',
                            b'b': b'2'
                        }
                    }
                ],
                {
                    b'all.count': 2,
                    b'sync.time': 1,
                    b'query.time': 7,
                    b'result.count': 2,
                    b'result.time': 0
                }
            ]
    """
    args: List[TEncodable] = [CommandNames.FT_PROFILE, index_name] + options.to_args()
    return cast(FtProfileResponse, client.custom_command(args))


def aliaslist(client: TGlideClient) -> Mapping[TEncodable, TEncodable]:
    """
    List the index aliases.

    Note:
        Not supported by valkey-search <= 1.3.0-rc1 -- confirmed live, the server rejects FT._ALIASLIST
        outright with "unknown command" (waiting on https://github.com/valkey-io/valkey-search/pull/1184).
        This function remains part of the public API for parity with RediSearch clients, but any call
        currently raises a `RequestError`.

    Args:
        client (TGlideClient): The client to execute the command.
    Returns:
        Mapping[TEncodable, TEncodable]: A map of index aliases for indices being aliased.
    Examples:
        >>> from glide import ft
        >>> ft._aliaslist(glide_client)
            {b'alias': b'index1', b'alias-bytes': b'index2'}
    """
    args: List[TEncodable] = [CommandNames.FT_ALIASLIST]
    return cast(Mapping, client.custom_command(args))


def hybrid(
    client: TGlideClient,
    index_name: TEncodable,
    search_arm: FtHybridSearchArm,
    vsim_arm: FtHybridVsimArm,
    options: Optional[FtHybridOptions] = None,
) -> FtAggregateResponse:
    """
    Combines a text/tag/numeric query (the SEARCH arm) with a vector similarity search (the VSIM
    arm) in a single query, fusing both arms' results into one ranked reply.

    Args:
        client (TGlideClient): The client to execute the command.
        index_name (TEncodable): The index name to query.
        search_arm (FtHybridSearchArm): The text/tag/numeric query arm. See `FtHybridSearchArm`.
        vsim_arm (FtHybridVsimArm): The vector similarity search arm. See `FtHybridVsimArm`.
        options (Optional[FtHybridOptions]): The optional arguments for the FT.HYBRID command,
            covering COMBINE, LOAD, FILTER/LIMIT/GROUPBY/SORTBY/APPLY clauses, WITHCURSOR,
            LOCALONLY, TIMEOUT, PARAMS and DIALECT. See `FtHybridOptions`.

    Returns:
        FtAggregateResponse: An array containing a mapping of field name and associated value for
            each matching document, after both arms' results have been fused and any clauses in
            `options` applied. With no LOAD clause in `options` (the default), each mapping
            contains just the document key and the fused score. When
            ``options.with_cursor`` is set, the reply shape instead matches FT.AGGREGATE's own
            WITHCURSOR shape: a two-element array of ``[rows, cursor_id]``, where ``rows`` is this
            same `FtAggregateResponse` shape and ``cursor_id`` is passed to a subsequent
            `glide.async_commands.ft.cursor_read`/`glide_sync.sync_commands.ft.cursor_read` call
            to fetch the next page (``0`` once exhausted).

    Note:
        A paged (``with_cursor``) FT.HYBRID query, and every subsequent FT.CURSOR READ/DEL against
        the cursor it opens, always reads from the PRIMARY of the index's slot, regardless of the
        client's ``ReadFrom`` configuration. This is because a cursor is a
        per-process, node-local resource on the server that opened it -- it is not shared or
        forwarded between nodes, so a read that lands on a different node (a replica a
        ``PREFER_REPLICA``/AZ-affinity client would otherwise have used) fails with "Cursor not
        found". A failover of the primary that was holding an open cursor loses that cursor, the
        same as any other cursor-discarding event on that node.

    Examples:
        For the following example to work, an index named "idx" must already exist, with a TEXT
        field "title" and a VECTOR field "vec".

        >>> from glide_sync import ft
        >>> ft.hybrid(
        ...     glide_client,
        ...     "idx",
        ...     FtHybridSearchArm("hello world"),
        ...     FtHybridVsimArm("vec", query_vector_bytes, k=10),
        ... )
            [{b'__key': b'd1', b'__score': b'0.014925'}, {b'__key': b'd2', b'__score': b'0.009523'}]

        # With RRF fusion, a LOAD clause and WITHCURSOR paging:
        >>> ft.hybrid(
        ...     glide_client,
        ...     "idx",
        ...     FtHybridSearchArm("hello world", yield_score_as="ss"),
        ...     FtHybridVsimArm("vec", query_vector_bytes, k=10, yield_score_as="vs"),
        ...     options=FtHybridOptions(
        ...         combine=FtHybridCombine.RRF(constant=60),
        ...         load_fields=["title"],
        ...         with_cursor=FtWithCursor(count=10),
        ...     ),
        ... )
            [[{b'title': b'hello world'}], 14058138254]
    """
    args: List[TEncodable] = [CommandNames.FT_HYBRID, index_name]
    args.extend(search_arm.to_args())
    generated_vsim_param: Optional[Mapping[TEncodable, TEncodable]] = None
    if vsim_arm.is_param_reference():
        args.extend(vsim_arm.to_args())
    else:
        generated_name = f"${uuid4().hex}"
        generated_vsim_param = {generated_name[1:]: vsim_arm.vector}
        args.extend(vsim_arm.to_args(generated_param_name=generated_name))
    if options and options.combine:
        args.extend(options.combine.to_args())
    args.extend((options or FtHybridOptions()).to_args(generated_vsim_param))
    return cast(FtAggregateResponse, client.custom_command(args))
