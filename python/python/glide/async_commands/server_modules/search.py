# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""
module for `vector search` commands.
"""

from typing import Optional, List, cast
from glide.glide_client import TGlideClient
from glide.constants import TOK, TEncodable
from glide.async_commands.server_modules.search_options.ft_create_options import FtCreateOptions , Field
from glide.async_commands.server_modules.search_constants import CommandNames, FtCreateKeywords

async def create(
    client: TGlideClient,
    indexName: TEncodable,
    schema: List[Field] = [],
    options: Optional[FtCreateOptions] = None
) -> TOK:
    """
    Creates an index and initiates a backfill of that index.

    See https://valkey.io/commands/ft.create/ for more details.

        Args:
        client (TGlideClient): The client to execute the command.
        indexName (TEncodable): The index name for the index to be created
        schema (List[Field]): The fields for the index schema.
        options (Optional[FtCreateOptions]): Optional arguments for the [FT.CREATE] command.

    Returns:
        TOK: If the index is successfully created, returns OK.

    Examples:
        >>> from glide.async_commands.server_modules import search
        >>> schema: List[Field] = []
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
        args.extend(options.getCreateOptions())
        print(args)
    if schema and len(schema) > 0:
        args.append(FtCreateKeywords.SCHEMA)
        for field in schema:
            print("++++++++")
            print(field.getFieldArgs())
            args.extend(field.getFieldArgs())
    print(args)
    return cast(TOK, await client.custom_command(args))
