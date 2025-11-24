# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from enum import Enum
from typing import List, Optional, Union, cast

from glide_shared.commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateOptions,
)
from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtProfileKeywords,
)
from glide_shared.commands.server_modules.ft_options.ft_search_options import (
    FtSearchOptions,
)
from glide_shared.constants import TEncodable


class QueryType(Enum):
    """
    This class represents the query type being profiled.
    """

    AGGREGATE = "AGGREGATE"
    """
    If the query being profiled is for the FT.AGGREGATE command.
    """
    SEARCH = "SEARCH"
    """
    If the query being profiled is for the FT.SEARCH command.
    """


class FtProfileOptions:
    """
    This class represents the arguments/options for the FT.PROFILE command.
    """

    def __init__(
        self,
        query: TEncodable,
        query_type: QueryType,
        query_options: Optional[Union[FtSearchOptions, FtAggregateOptions]] = None,
        limited: Optional[bool] = False,
    ):
        """
        Initialize a new FtProfileOptions instance.

        Args:
            query (TEncodable): The query that is being profiled. This is the query argument from the
                FT.AGGREGATE/FT.SEARCH command.
            query_type (Optional[QueryType]): The type of query to be profiled.
            query_options (Optional[Union[FtSearchOptions, FtAggregateOptions]]): The arguments/options for the
                FT.AGGREGATE/FT.SEARCH command being profiled.
            limited (Optional[bool]): To provide some brief version of the output, otherwise a full verbose output is provided.
        """
        self.query = query
        self.query_type = query_type
        self.query_options = query_options
        self.limited = limited

    @classmethod
    def from_query_options(
        cls,
        query: TEncodable,
        query_options: Union[FtSearchOptions, FtAggregateOptions],
        limited: Optional[bool] = False,
    ):
        """
        A class method to create FtProfileOptions with FT.SEARCH/FT.AGGREGATE options.

        Args:
            query (TEncodable): The query that is being profiled. This is the query argument from the
                FT.AGGREGATE/FT.SEARCH command.
            query_options (Optional[Union[FtSearchOptions, FtAggregateOptions]]): The arguments/options for the
                FT.AGGREGATE/FT.SEARCH command being profiled.
            limited (Optional[bool]): To provide some brief version of the output, otherwise a full verbose output is provided.
        """
        query_type: QueryType = QueryType.SEARCH
        if isinstance(query_options, FtAggregateOptions):
            query_type = QueryType.AGGREGATE
        return cls(query, query_type, query_options, limited)

    @classmethod
    def from_query_type(
        cls, query: TEncodable, query_type: QueryType, limited: Optional[bool] = False
    ):
        """
        A class method to create FtProfileOptions with QueryType.

        Args:
            query (TEncodable): The query that is being profiled. This is the query argument from the
                FT.AGGREGATE/FT.SEARCH command.
            query_type (QueryType): The type of query to be profiled.
            limited (Optional[bool]): To provide some brief version of the output, otherwise a full verbose output is provided.
        """
        return cls(query, query_type, None, limited)

    def to_args(self) -> List[TEncodable]:
        """
        Get the remaining arguments for the FT.PROFILE command.

        Returns:
            List[TEncodable]: A list of remaining arguments for the FT.PROFILE command.
        """
        args: List[TEncodable] = [self.query_type.value]
        if self.limited:
            args.append(FtProfileKeywords.LIMITED)
        args.extend([FtProfileKeywords.QUERY, self.query])
        if self.query_options:
            if isinstance(self.query_options, FtAggregateOptions):
                args.extend(cast(FtAggregateOptions, self.query_options).to_args())
            else:
                args.extend(cast(FtSearchOptions, self.query_options).to_args())
        return args
