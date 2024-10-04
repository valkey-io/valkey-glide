# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""module for `RedisJSON` commands.

    Examples:

        >>> from glide import json as redisJson
        >>> import json
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = json.dumps(value) # Convert Python dictionary to JSON string using json.dumps()
        >>> await redisJson.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> json_get = await redisJson.get(client, "doc", "$") # Returns the value at path '$' in the JSON document stored at `doc` as JSON string.
        >>> print(json_get)
            b"[{\"a\":1.0,\"b\":2}]" 
        >>> json.loads(str(json_get))
            [{"a": 1.0, "b" :2}] # JSON object retrieved from the key `doc` using json.loads()


        >>> from glide.async_commands.server_modules import search
        >>> from glide.glide_client import TGlideClient
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = json.dumps(value) # Convert Python dictionary to JSON string using json.dumps()
        >>> await redisJson.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> json_get = await redisJson.get(client, "doc", "$") # Returns the value at path '$' in the JSON document stored at `doc` as JSON string.
        >>> print(json_get)
            b"[{\"a\":1.0,\"b\":2}]" 
        >>> json.loads(str(json_get))
            [{"a": 1.0, "b" :2}] # JSON object retrieved from the key `doc` using json.loads()
        """

from typing import Optional, List, cast
from glide.glide_client import TGlideClient
from glide.constants import TOK, TEncodable
from glide.async_commands.server_modules.search_options.ft_create_options import FtCreateOptions , FieldInfo
from glide.async_commands.server_modules.search_constants import CommandNames, FtCreateKeywords
from glide.async_commands.server_modules.search_options.ft_search_options import FtSearchOptions
from glide.async_commands.server_modules.search_options.ft_dropindex_options import FtDropIndexOptions


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
) -> List[TEncodable]:
    """

    """
    args: List[TEncodable] = [CommandNames.INFO, indexName]
    return cast(List[TEncodable], await client.custom_command(args))

async def search(
    client: TGlideClient,
    indexName: TEncodable,
    query: TEncodable,
    options: Optional[FtSearchOptions] = None
) -> TOK:
    args: List[TEncodable] = [CommandNames.FT_SEARCH, indexName]
    if query:
        args.append(query)
    if options:
        args.extend(options.getSearchOptions())
    return cast(TOK, await client.custom_command(args))

async def dropIndex(
    client: TGlideClient,
    indexName: TEncodable,
    options: Optional[FtDropIndexOptions] = None
) -> TOK:
    args: List[TEncodable] = [CommandNames.FT_DROPINDEX, indexName]
    if options:
        args.extend(options.getDropIndexOptions())
    return cast(TOK, await client.custom_command(args))
