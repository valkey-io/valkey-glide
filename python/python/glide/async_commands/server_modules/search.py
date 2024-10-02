from typing import Optional, List, cast
from glide.glide_client import TGlideClient
from glide.constants import TOK, TEncodable
from glide.protobuf.command_request_pb2 import RequestType
from glide.async_commands.server_modules.search_options import FtCreateOptions, FtSearchOptions, FieldInfo
from glide.async_commands.server_modules.search_constants import CommandNames, CreateParameters


async def create(
    client: TGlideClient,
    indexName: TEncodable,
    fields: List[FieldInfo] = [],
    options: Optional[FtCreateOptions] = None
) -> TOK:
    args: List[TEncodable] = [CommandNames.FT_CREATE, indexName]
    if fields and len(fields) > 0:
        args.append(CreateParameters.SCHEMA)
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
        options: Optional[FtSearchOptions]
):
    print("ft search")
