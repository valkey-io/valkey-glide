# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import List, Optional

from glide_shared.constants import TEncodable


class JsonArrIndexOptions:
    """
    Options for the `JSON.ARRINDEX` command.

    Args:
        start (int): The inclusive start index from which the search begins. Defaults to None.
        end (Optional[int]): The exclusive end index where the search stops. Defaults to None.

    Note:
        - If `start` is greater than `end`, the command returns `-1` to indicate that the value was not found.
        - Indices that exceed the array bounds are automatically adjusted to the nearest valid position.
    """

    def __init__(self, start: int, end: Optional[int] = None):
        self.start = start
        self.end = end

    def to_args(self) -> List[str]:
        """
        Get the options as a list of arguments for the JSON.ARRINDEX command.

        Returns:
            List[str]: A list containing the start and end indices if specified.
        """
        args = [str(self.start)]
        if self.end is not None:
            args.append(str(self.end))
        return args


class JsonArrPopOptions:
    """
    Options for the JSON.ARRPOP command.

    Args:
        path (TEncodable): The path within the JSON document.
        index (Optional[int]): The index of the element to pop. If not specified, will pop the last element.
            Out of boundary indexes are rounded to their respective array boundaries. Defaults to None.
    """

    def __init__(self, path: TEncodable, index: Optional[int] = None):
        self.path = path
        self.index = index

    def to_args(self) -> List[TEncodable]:
        """
        Get the options as a list of arguments for the `JSON.ARRPOP` command.

        Returns:
            List[TEncodable]: A list containing the path and, if specified, the index.
        """
        args = [self.path]
        if self.index is not None:
            args.append(str(self.index))
        return args


class JsonGetOptions:
    """
    Represents options for formatting JSON data, to be used in  the [JSON.GET](https://valkey.io/commands/json.get/) command.

    Args:
        indent (Optional[str]): Sets an indentation string for nested levels. Defaults to None.
        newline (Optional[str]): Sets a string that's printed at the end of each line. Defaults to None.
        space (Optional[str]): Sets a string that's put between a key and a value. Defaults to None.
    """

    def __init__(
        self,
        indent: Optional[str] = None,
        newline: Optional[str] = None,
        space: Optional[str] = None,
    ):
        self.indent = indent
        self.new_line = newline
        self.space = space

    def get_options(self) -> List[str]:
        args = []
        if self.indent:
            args.extend(["INDENT", self.indent])
        if self.new_line:
            args.extend(["NEWLINE", self.new_line])
        if self.space:
            args.extend(["SPACE", self.space])
        return args
