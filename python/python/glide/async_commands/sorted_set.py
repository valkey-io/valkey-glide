# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import List, Optional, Union


class InfBound(Enum):
    """
    Enumeration representing numeric and lexicographic positive and negative infinity bounds for sorted set.
    """

    POS_INF = {"score_arg": "+inf", "lex_arg": "+"}
    """
    Positive infinity bound for sorted set.
        score_arg: represents numeric positive infinity (+inf).
        lex_arg: represents lexicographic positive infinity (+).
    """
    NEG_INF = {"score_arg": "-inf", "lex_arg": "-"}
    """
    Negative infinity bound for sorted set.
        score_arg: represents numeric negative infinity (-inf).
        lex_arg: represents lexicographic negative infinity (-).
    """


class ScoreBoundary:
    """
    Represents a specific numeric score boundary in a sorted set.

    Args:
        value (float): The score value.
        is_inclusive (bool): Whether the score value is inclusive. Defaults to True.
    """

    def __init__(self, value: float, is_inclusive: bool = True):
        # Convert the score boundary to the Redis protocol format
        self.value = str(value) if is_inclusive else f"({value}"


class LexBoundary:
    """
    Represents a specific lexicographic boundary in a sorted set.

    Args:
        value (str): The lex value.
        is_inclusive (bool): Whether the score value is inclusive. Defaults to True.
    """

    def __init__(self, value: str, is_inclusive: bool = True):
        # Convert the lexicographic boundary to the Redis protocol format
        self.value = f"[{value}" if is_inclusive else f"({value}"


class Limit:
    """
    Represents a limit argument for a range query in a sorted set to be used in [ZRANGE](https://redis.io/commands/zrange) command.

    The optional LIMIT argument can be used to obtain a sub-range from the matching elements
        (similar to SELECT LIMIT offset, count in SQL).
    Args:
        offset (int): The offset from the start of the range.
        count (int): The number of elements to include in the range.
            A negative count returns all elements from the offset.
    """

    def __init__(self, offset: int, count: int):
        self.offset = offset
        self.count = count


class RangeByIndex:
    """
    Represents a range by index (rank) in a sorted set.

    The `start` and `stop` arguments represent zero-based indexes.

    Args:
        start (int): The start index of the range.
        stop (int): The stop index of the range.
    """

    def __init__(self, start: int, stop: int):
        self.start = start
        self.stop = stop


class RangeByScore:
    """
    Represents a range by score in a sorted set.

    The `start` and `stop` arguments represent score boundaries.

    Args:
        start (Union[InfBound, ScoreBoundary]): The start score boundary.
        stop (Union[InfBound, ScoreBoundary]): The stop score boundary.
        limit (Optional[Limit]): The limit argument for a range query. Defaults to None. See `Limit` class for more information.
    """

    def __init__(
        self,
        start: Union[InfBound, ScoreBoundary],
        stop: Union[InfBound, ScoreBoundary],
        limit: Optional[Limit] = None,
    ):
        self.start = (
            start.value["score_arg"] if type(start) == InfBound else start.value
        )
        self.stop = stop.value["score_arg"] if type(stop) == InfBound else stop.value
        self.limit = limit


class RangeByLex:
    """
    Represents a range by lexicographical order in a sorted set.

    The `start` and `stop` arguments represent lexicographical boundaries.

    Args:
        start (Union[InfBound, LexBoundary]): The start lexicographic boundary.
        stop (Union[InfBound, LexBoundary]): The stop lexicographic boundary.
        limit (Optional[Limit]): The limit argument for a range query. Defaults to None. See `Limit` class for more information.
    """

    def __init__(
        self,
        start: Union[InfBound, LexBoundary],
        stop: Union[InfBound, LexBoundary],
        limit: Optional[Limit] = None,
    ):
        self.start = start.value["lex_arg"] if type(start) == InfBound else start.value
        self.stop = stop.value["lex_arg"] if type(stop) == InfBound else stop.value
        self.limit = limit


def _create_zrange_args(
    key: str,
    range_query: Union[RangeByLex, RangeByScore, RangeByIndex],
    reverse: bool,
    with_scores: bool,
) -> List[str]:
    args = [key, str(range_query.start), str(range_query.stop)]

    if isinstance(range_query, RangeByScore):
        args.append("BYSCORE")
    elif isinstance(range_query, RangeByLex):
        args.append("BYLEX")
    if reverse:
        args.append("REV")
    if hasattr(range_query, "limit") and range_query.limit is not None:
        args.extend(
            [
                "LIMIT",
                str(range_query.limit.offset),
                str(range_query.limit.count),
            ]
        )
    if with_scores:
        args.append("WITHSCORES")

    return args
