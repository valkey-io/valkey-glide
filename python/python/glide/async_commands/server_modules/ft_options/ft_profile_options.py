# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from enum import Enum
from typing import List, Optional, Union, cast

from glide.async_commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateOptions,
)
from glide.async_commands.server_modules.ft_options.ft_constants import (
    FtProfileKeywords,
)
from glide.async_commands.server_modules.ft_options.ft_search_options import (
    FtSeachOptions,
)
from glide.constants import TEncodable


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
        queryType: QueryType,
        queryOptions: Optional[Union[FtSeachOptions, FtAggregateOptions]] = None,
        limited: Optional[bool] = False,
    ):
        """
        Initialize a new FtProfileOptions instance.

        Args:
            query (TEncodable): The query that is being profiled. This is the query argument from the FT.AGGREGATE/FT.SEARCH command.
            queryType (Optional[QueryType]): The type of query to be profiled.
            queryOptions (Optional[Union[FtSeachOptions, FtAggregateOptions]]): The arguments/options for the FT.AGGREGATE/FT.SEARCH command being profiled.
            limited (Optional[bool]): To provide some brief version of the output, otherwise a full verbose output is provided.
        """
        self.query = query
        self.queryType = queryType
        self.queryOptions = queryOptions
        self.limited = limited

    @classmethod
    def from_query_options(
        cls,
        query: TEncodable,
        queryOptions: Union[FtSeachOptions, FtAggregateOptions],
        limited: Optional[bool] = False,
    ):
        """
        A class method to create FtProfileOptions with FT.SEARCH/FT.AGGREGATE options.

        Args:
            query (TEncodable): The query that is being profiled. This is the query argument from the FT.AGGREGATE/FT.SEARCH command.
            queryOptions (Optional[Union[FtSeachOptions, FtAggregateOptions]]): The arguments/options for the FT.AGGREGATE/FT.SEARCH command being profiled.
            limited (Optional[bool]): To provide some brief version of the output, otherwise a full verbose output is provided.
        """
        queryType: QueryType = QueryType.SEARCH
        if type(queryOptions) == FtAggregateOptions:
            queryType = QueryType.AGGREGATE
        return cls(query, queryType, queryOptions, limited)

    @classmethod
    def from_query_type(
        cls, query: TEncodable, queryType: QueryType, limited: Optional[bool] = False
    ):
        """
        A class method to create FtProfileOptions with QueryType.

        Args:
            query (TEncodable): The query that is being profiled. This is the query argument from the FT.AGGREGATE/FT.SEARCH command.
            queryType (QueryType): The type of query to be profiled.
            limited (Optional[bool]): To provide some brief version of the output, otherwise a full verbose output is provided.
        """
        return cls(query, queryType, None, limited)

    def to_args(self) -> List[TEncodable]:
        """
        Get the remaining arguments for the FT.PROFILE command.

        Returns:
            List[TEncodable]: A list of remaining arguments for the FT.PROFILE command.
        """
        args: List[TEncodable] = [self.queryType.value]
        if self.limited:
            args.append(FtProfileKeywords.LIMITED)
        args.extend([FtProfileKeywords.QUERY, self.query])
        if self.queryOptions:
            if type(self.queryOptions) == FtAggregateOptions:
                args.extend(cast(FtAggregateOptions, self.queryOptions).to_args())
            else:
                args.extend(cast(FtSeachOptions, self.queryOptions).toArgs())
        return args
