# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""
module for `vector search` commands.
"""

from typing import List, Mapping, Optional, Union, cast

from glide.async_commands.server_modules.ft_options.ft_constants import (
    CommandNames,
    FtCreateKeywords,
)
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    Field,
    FtCreateOptions,
)
from glide.async_commands.server_modules.ft_options.ft_search_options import (
    FtSeachOptions,
)
from glide.constants import TOK, TEncodable
from glide.glide_client import TGlideClient


async def create(
    client: TGlideClient,
    indexName: TEncodable,
    schema: List[Field],
    options: Optional[FtCreateOptions] = None,
) -> TOK:
    """
    Creates an index and initiates a backfill of that index.

    Args:
        client (TGlideClient): The client to execute the command.
        indexName (TEncodable): The index name for the index to be created
        schema (List[Field]): The fields of the index schema, specifying the fields and their types.
        options (Optional[FtCreateOptions]): Optional arguments for the FT.CREATE command. See `FtCreateOptions`.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        >>> from glide.async_commands.server_modules import ft
        >>> schema: List[Field] = []
        >>> field: TextField = TextField("title")
        >>> schema.append(field)
        >>> prefixes: List[str] = []
        >>> prefixes.append("blog:post:")
        >>> index = "idx"
        >>> result = await ft.create(glide_client, index, schema, FtCreateOptions(DataType.HASH, prefixes))
            'OK'  # Indicates successful creation of index named 'idx'
    """
    args: List[TEncodable] = [CommandNames.FT_CREATE, indexName]
    if options:
        args.extend(options.toArgs())
    if schema:
        args.append(FtCreateKeywords.SCHEMA)
        for field in schema:
            args.extend(field.toArgs())
    return cast(TOK, await client.custom_command(args))


async def dropindex(client: TGlideClient, indexName: TEncodable) -> TOK:
    """
    Drops an index. The index definition and associated content are deleted. Keys are unaffected.

    Args:
        client (TGlideClient): The client to execute the command.
        indexName (TEncodable): The index name for the index to be dropped.

    Returns:
        TOK: A simple "OK" response.

    Examples:
        For the following example to work, an index named 'idx' must be already created. If not created, you will get an error.
        >>> from glide.async_commands.server_modules import ft
        >>> indexName = "idx"
        >>> result = await ft.dropindex(glide_client, indexName)
            'OK'  # Indicates successful deletion/dropping of index named 'idx'
    """
    args: List[TEncodable] = [CommandNames.FT_DROPINDEX, indexName]
    return cast(TOK, await client.custom_command(args))


async def search(
    client: TGlideClient,
    indexName: TEncodable,
    query: TEncodable,
    options: Optional[FtSeachOptions],
) -> List[Union[int, Mapping[TEncodable, Mapping[TEncodable, TEncodable]]]]:
    """
    Uses the provided query expression to locate keys within an index.

    Args:
        client (TGlideClient): The client to execute the command.
        indexName (TEncodable): The index name for the index to be searched.
        query (TEncodable): The query expression to use for the search on the index.
        options (Optional[FtSeachOptions]): Optional arguments for the FT.SEARCH command. See `FtSearchOptions`.

    Returns:
        List[Union[int, Mapping[TEncodable, Mapping[TEncodable]]]]:

    Examples:
    """
    args: List[TEncodable] = [CommandNames.FT_SEARCH, indexName, query]
    if options:
        args.extend(options.toArgs())
    args.extend(["DIALECT", "2"])
    print("args======")
    print(args)
    return cast(
        List[Union[int, Mapping[TEncodable, List[Mapping[TEncodable, TEncodable]]]]],
        await client.custom_command(args),
    )
