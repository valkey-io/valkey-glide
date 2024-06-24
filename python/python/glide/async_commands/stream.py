# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
from __future__ import annotations

from abc import ABC, abstractmethod
from enum import Enum
from typing import List, Optional, Union


class StreamTrimOptions(ABC):
    """
    Abstract base class for stream trim options.
    """

    @abstractmethod
    def __init__(
        self,
        exact: bool,
        threshold: Union[str, int],
        method: str,
        limit: Optional[int] = None,
    ):
        """
        Initialize stream trim options.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (Union[str, int]): Threshold for trimming.
            method (str): Method for trimming (e.g., MINID, MAXLEN).
            limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
                Note: If `exact` is set to `True`, `limit` cannot be specified.
        """
        if exact and limit:
            raise ValueError(
                "If `exact` is set to `True`, `limit` cannot be specified."
            )
        self.exact = exact
        self.threshold = threshold
        self.method = method
        self.limit = limit

    def to_args(self) -> List[str]:
        """
        Convert options to arguments for Redis command.

        Returns:
            List[str]: List of arguments for Redis command.
        """
        option_args = [
            self.method,
            "=" if self.exact else "~",
            str(self.threshold),
        ]
        if self.limit is not None:
            option_args.extend(["LIMIT", str(self.limit)])
        return option_args


class TrimByMinId(StreamTrimOptions):
    """
    Stream trim option to trim by minimum ID.
    """

    def __init__(self, exact: bool, threshold: str, limit: Optional[int] = None):
        """
        Initialize trim option by minimum ID.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (str): Threshold for trimming by minimum ID.
            limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
                Note: If `exact` is set to `True`, `limit` cannot be specified.
        """
        super().__init__(exact, threshold, "MINID", limit)


class TrimByMaxLen(StreamTrimOptions):
    """
    Stream trim option to trim by maximum length.
    """

    def __init__(self, exact: bool, threshold: int, limit: Optional[int] = None):
        """
        Initialize trim option by maximum length.

        Args:
            exact (bool): If `true`, the stream will be trimmed exactly.
                Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
            threshold (int): Threshold for trimming by maximum length.
            limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
                Note: If `exact` is set to `True`, `limit` cannot be specified.
        """
        super().__init__(exact, threshold, "MAXLEN", limit)


class StreamAddOptions:
    """
    Options for adding entries to a stream.
    """

    def __init__(
        self,
        id: Optional[str] = None,
        make_stream: bool = True,
        trim: Optional[StreamTrimOptions] = None,
    ):
        """
        Initialize stream add options.

        Args:
            id (Optional[str]): ID for the new entry. If set, the new entry will be added with this ID. If not specified, '*' is used.
            make_stream (bool, optional): If set to False, a new stream won't be created if no stream matches the given key.
            trim (Optional[StreamTrimOptions]): If set, the add operation will also trim the older entries in the stream. See `StreamTrimOptions`.
        """
        self.id = id
        self.make_stream = make_stream
        self.trim = trim

    def to_args(self) -> List[str]:
        """
        Convert options to arguments for Redis command.

        Returns:
            List[str]: List of arguments for Redis command.
        """
        option_args = []
        if not self.make_stream:
            option_args.append("NOMKSTREAM")
        if self.trim:
            option_args.extend(self.trim.to_args())
        option_args.append(self.id if self.id else "*")

        return option_args


class StreamRangeBound(ABC):
    """
    Abstract Base Class used in the `XRANGE` and `XREVRANGE` commands to specify the starting and ending range bound for
    the stream search by stream ID.
    """

    @abstractmethod
    def to_arg(self) -> str:
        """
        Returns the stream range bound as a string argument to be used in the `XRANGE` or `XREVRANGE` commands.
        """
        pass


class MinId(StreamRangeBound):
    """
    Stream ID boundary used to specify the minimum stream entry ID. Can be used in the `XRANGE` or `XREVRANGE` commands
    to get the first stream ID.
    """

    MIN_RANGE_REDIS_API = "-"

    def to_arg(self) -> str:
        return self.MIN_RANGE_REDIS_API


class MaxId(StreamRangeBound):
    """
    Stream ID boundary used to specify the maximum stream entry ID. Can be used in the `XRANGE` or `XREVRANGE` commands
    to get the last stream ID.
    """

    MAX_RANGE_REDIS_API = "+"

    def to_arg(self) -> str:
        return self.MAX_RANGE_REDIS_API


class IdBound(StreamRangeBound):
    """
    Inclusive (closed) stream ID boundary used to specify a range of IDs to search. Stream ID bounds can be complete
    with a timestamp and sequence number separated by a dash ("-"), for example "1526985054069-0". Stream ID bounds can
    also be incomplete, with just a timestamp.
    """

    @staticmethod
    def from_timestamp(timestamp: int) -> IdBound:
        """
        Creates an incomplete stream ID boundary without the sequence number for a range search.

        Args:
            timestamp (int): The stream ID timestamp.
        """
        return IdBound(str(timestamp))

    def __init__(self, stream_id: str):
        """
        Creates a stream ID boundary for a range search.

        Args:
            stream_id (str): The stream ID.
        """
        self.stream_id = stream_id

    def to_arg(self) -> str:
        return self.stream_id


class ExclusiveIdBound(StreamRangeBound):
    """
    Exclusive (open) stream ID boundary used to specify a range of IDs to search. Stream ID bounds can be complete with
    a timestamp and sequence number separated by a dash ("-"), for example "1526985054069-0". Stream ID bounds can also
    be incomplete, with just a timestamp.
    """

    EXCLUSIVE_BOUND_REDIS_API = "("

    @staticmethod
    def from_timestamp(timestamp: int) -> ExclusiveIdBound:
        """
        Creates an incomplete stream ID boundary without the sequence number for a range search.

        Args:
            timestamp (int): The stream ID timestamp.
        """
        return ExclusiveIdBound(str(timestamp))

    def __init__(self, stream_id: str):
        """
        Creates a stream ID boundary for a range search.

        Args:
            stream_id (str): The stream ID.
        """
        self.stream_id = f"{self.EXCLUSIVE_BOUND_REDIS_API}{stream_id}"

    def to_arg(self) -> str:
        return self.stream_id


class StreamReadOptions:
    READ_COUNT_REDIS_API = "COUNT"
    READ_BLOCK_REDIS_API = "BLOCK"

    def __init__(self, block_ms: Optional[int] = None, count: Optional[int] = None):
        """
        Options for reading entries from streams. Can be used as an optional argument to `XREAD`.

        Args:
            block_ms (Optional[int]): If provided, the request will be blocked for the set amount of milliseconds or
                until the server has the required number of entries. Equivalent to `BLOCK` in the Redis API.
            count (Optional[int]): The maximum number of elements requested. Equivalent to `COUNT` in the Redis API.
        """
        self.block_ms = block_ms
        self.count = count

    def to_args(self) -> List[str]:
        """
        Returns the options as a list of string arguments to be used in the `XREAD` command.

        Returns:
            List[str]: The options as a list of arguments for the `XREAD` command.
        """
        args = []
        if self.block_ms is not None:
            args.extend([self.READ_BLOCK_REDIS_API, str(self.block_ms)])

        if self.count is not None:
            args.extend([self.READ_COUNT_REDIS_API, str(self.count)])

        return args
