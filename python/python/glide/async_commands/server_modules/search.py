# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""
module for `vector search` commands.
"""

from typing import Optional, List, cast
from glide.glide_client import TGlideClient
from glide.constants import TOK, TEncodable
from glide.async_commands.server_modules.search_options.ft_create_options import FtCreateOptions , FieldInfo
from glide.async_commands.server_modules.search_constants import CommandNames, FtCreateKeywords

async def create(
    client: TGlideClient,
    indexName: TEncodable,
    fields: List[FieldInfo] = [],
    options: Optional[FtCreateOptions] = None
) -> TOK:
    """
    Creates an index and initiates a backfill of that index.

    See https://valkey.io/commands/ft.create/ for more details.

        Args:
        client (TGlideClient): The client to execute the command.
        indexName (TEncodable): The index name for the index to be created
        fields (List[FieldInfo]): The fields for the index schema.
        options (Optional[FtCreateOptions]): Optional arguments for the [FT.CREATE] command.

    Returns:
        TOK: If the index is successfully created, returns OK.

    Examples:
        >>> from glide.async_commands.server_modules import search
        >>> fields: List[FieldInfo] = []
        >>> fieldInfo1: FieldInfo = FieldInfo("title", fieldTypeInfo=FieldTypeInfo(fieldType=FieldType.TEXT), sortable = SORTABLE.IS_SORTABLE)
        >>> fields.append(fieldInfo1)
        >>> prefixes: List[str] = []
        >>> prefixes.append("blog:post:")
        >>> index = "idx"
        >>> result = await search.create(glide_client, index, fields, FtCreateOptions(DataType.HASH, prefixes))
            'OK'  # Indicates successful creation of index named 'idx'
    """
    args: List[TEncodable] = [CommandNames.FT_CREATE, indexName]
    if options:
        args = args + options.getCreateOptions()
    if fields and len(fields) > 0:
        args.append(FtCreateKeywords.SCHEMA)
        for fieldInfo in fields:
            args = args + fieldInfo.getFieldInfo()
    return cast(TOK, await client.custom_command(args))
