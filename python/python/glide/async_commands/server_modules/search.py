from typing import Optional, List, cast
from glide.glide_client import TGlideClient
from glide.constants import TOK, TEncodable
from glide.async_commands.server_modules.search_options.ft_create import FtCreateOptions , FieldInfo
from glide.async_commands.server_modules.search_constants import CommandNames, FtCreateKeywords
from glide.async_commands.server_modules.search_options.ft_search import FtSearchOptions
from glide.async_commands.server_modules.search_options.ft_dropindex import FtDropIndexOptions


async def create(
    client: TGlideClient,
    indexName: TEncodable,
    fields: List[FieldInfo] = [],
    options: Optional[FtCreateOptions] = None
) -> TOK:
    args: List[TEncodable] = [CommandNames.FT_CREATE, indexName]
    if fields and len(fields) > 0:
        args.append(FtCreateKeywords.SCHEMA)
        for fieldInfo in fields:
            args = args + fieldInfo.getFieldInfo()
    if options:
        args.extend(options.getCreateOptions())
    return cast(TOK, await client.custom_command(args))

async def info(
    client: TGlideClient,
    indexName: TEncodable,
):
    args: List[TEncodable] = [CommandNames.INFO, indexName]
    return cast(List[TEncodable], await client.custom_command(args))

async def search(
    client: TGlideClient,
    indexName: TEncodable,
    query: TEncodable,
    options: Optional[FtSearchOptions] = None
):
    args: List[TEncodable] = [CommandNames.FT_SEARCH, indexName]
    if query:
        args.append(query)
    if options:
        args.append(options)
    return cast(TOK, await client.custom_command(args))

async def dropIndex(
    client: TGlideClient,
    indexName: TEncodable,
    options: Optional[FtDropIndexOptions] = None
) -> TOK:
    args: List[TEncodable] = [CommandNames.FT_DROPINDEX, indexName]
    if options:
        args.extend(options.get())

    return cast(TOK, await client.custom_command(args))
