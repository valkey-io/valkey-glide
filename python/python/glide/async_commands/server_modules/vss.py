from typing import List, cast
from glide.glide_client import TGlideClient
from glide.constants import TOK, TEncodable
from glide.protobuf.command_request_pb2 import RequestType
from glide.async_commands.server_modules.vss_options import FtCreateOptions, FtSearchOptions

async def create(
    client: TGlideClient,
    indexName: TEncodable,
    options: FtCreateOptions
) -> TOK:
    args: List[TEncodable] = [indexName]
    args.extend(options.getCreateOptions())
    return cast(TOK, await client._execute_command(RequestType.FtCreate, args))

async def info(
    client: TGlideClient,
    indexName: TEncodable,
):
    args: List[TEncodable] = [indexName]
    return cast(List[TEncodable], await client._execute_command(RequestType.FtInfo, args))
