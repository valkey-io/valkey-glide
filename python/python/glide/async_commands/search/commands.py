from typing import List, Optional, cast

from glide.async_commands.search.optional_params import (
    FieldFlags,
    Frequencies,
    Highlights,
    InitialScan,
    Offset,
)
from glide.constants import TOK
from glide.protobuf.redis_request_pb2 import RequestType
from glide.redis_client import BaseRedisClient

from .field import Field
from .index import Index


class Search:
    """Class for RediSearch commands."""

    async def create_index(
        self,
        client: BaseRedisClient,
        index: Index,
        schema: List[Field],
        max_text_fields: bool = False,
        temporary_seconds: Optional[int] = None,
        offset: Offset = Offset.STORE_OFFSET,
        highlight: Highlights = Highlights.USE_HIGHLIGHTS,
        field_flags: FieldFlags = FieldFlags.USE_FIELDS,
        frequencies: Frequencies = Frequencies.SAVE_FREQUENCIES,
        stop_words: Optional[List[str]] = None,
        initial_scan: InitialScan = InitialScan.SCAN_INDEX,
    ) -> TOK:
        """
        Creates a new search index with the specified configuration.
        See https://redis.io/commands/ft.create/ for more details.

        Args:
            client (BaseRedisClient): The Redis client to execute the command.
            index (Index): The definition of the index, including its name and type.
            schema (List[Field]): The schema of the index, specifying the fields and their types.
            max_text_fields (bool): Forces RediSearch to encode indexes as if there were more than 32 text attributes.
                Additional attributes (beyond 32) can be added using FT.ALTER.
            temporary_seconds (Optional[int]): Creates a temporary index that automatically expires after a set period of inactivity, measured in seconds.
                The index's internal idle timer resets each time a search or addition operation occurs.
            offset (Offset): Whether to store term offsets for documents.
            highlight (Highlights): Whether to disable / enable highlighting support.
            field_flags (FieldFlags): Whether to store attribute bits for each term.
            frequencies (Frequencies): Whether to save the term frequencies in the index.
            stop_words (Optional[List[str]]): Sets the index with a custom stopword list, to be ignored during indexing and search time.
                If not set, FT.CREATE takes the default list of stopwords.
            initial_scan (InitialScan): Whether to scan and index.

        Returns:
            A simple OK response.

        Examples:
            >>> index = Index("my_index", "HASH")
            >>> schema = [TextField("name"), NumericField("age")]
            >>> await search_commands.create_index(
                    redis_client,
                    index,
                    schema,
                    max_text_fields=True,
                    temporary_seconds=3600,
                    field_flags=FieldFlags.NO_FIELDS,
                    stopwords=["the", "and", "is"]
                )
                "OK"
        """
        args = index.get_index_atr()
        if max_text_fields:
            args.append("MAXTEXTFIELDS")
        if temporary_seconds is not None:
            args.extend(["TEMPORARY", str(temporary_seconds)])
        if offset.value:
            args.append("NOOFFSETS")
        if highlight.value:
            args.append("NOHL")
        if field_flags:
            args.append("NOFIELDS")
        if frequencies:
            args.append("NOFREQS")
        if stop_words and len(stop_words) > 0:
            args.extend(["STOPWORDS", str(len(stop_words)), *stop_words])
        if initial_scan:
            args.append("SKIPINITIALSCAN")
        args.append("SCHEMA")
        for field in schema:
            args.extend(field.get_field_args())

        return cast(TOK, await client._execute_command(RequestType.CreateIndex, args))
