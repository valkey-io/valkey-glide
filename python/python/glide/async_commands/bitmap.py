# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
from enum import Enum
from typing import List, Optional


class BitmapIndexType(Enum):
    """
    Enumeration specifying if index arguments are BYTE indexes or BIT indexes. Can be specified in `OffsetOptions`,
    which is an optional argument to the `BITCOUNT` command.

    Since: Redis version 7.0.0.
    """

    BYTE = "BYTE"
    """
    Specifies that indexes provided to `OffsetOptions` are byte indexes.
    """
    BIT = "BIT"
    """
    Specifies that indexes provided to `OffsetOptions` are bit indexes.
    """


class OffsetOptions:
    def __init__(
        self, start: int, end: int, index_type: Optional[BitmapIndexType] = None
    ):
        """
        Represents offsets specifying a string interval to analyze in the `BITCOUNT` command. The offsets are
        zero-based indexes, with `0` being the first index of the string, `1` being the next index and so on.
        The offsets can also be negative numbers indicating offsets starting at the end of the string, with `-1` being
        the last index of the string, `-2` being the penultimate, and so on.

        Args:
            start (int): The starting offset index.
            end (int): The ending offset index.
            index_type (Optional[BitmapIndexType]): The index offset type. This option can only be specified if you are
                using Redis version 7.0.0 or above. Could be either `BitmapIndexType.BYTE` or `BitmapIndexType.BIT`.
                If no index type is provided, the indexes will be assumed to be byte indexes.
        """
        self.start = start
        self.end = end
        self.index_type = index_type

    def to_args(self) -> List[str]:
        args = [str(self.start), str(self.end)]
        if self.index_type is not None:
            args.append(self.index_type.value)

        return args


class BitwiseOperation(Enum):
    """
    Enumeration defining the bitwise operation to use in the `BITOP` command. Specifies the bitwise operation to
    perform between the passed in keys.
    """

    AND = "AND"
    OR = "OR"
    XOR = "XOR"
    NOT = "NOT"
