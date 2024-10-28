# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from enum import Enum
from typing import List, Mapping, Optional

from glide.async_commands.server_modules.ft_options.ft_constants import (
    FtAggregateKeywords,
)
from glide.constants import TEncodable


class FtAggregateClause(ABC):
    """
    Abstract base class for the FT.AGGREGATE command clauses.
    """

    @abstractmethod
    def __init__(self):
        """
        Initialize a new FtAggregateClause instance.
        """
        pass

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the clause of the FT.AGGREGATE command.

        Returns:
            List[TEncodable]: A list of arguments for the clause of the FT.AGGREGATE command.
        """
        args: List[TEncodable] = []
        return args


class FtAggregateLimit(FtAggregateClause):
    """
    A clause for limiting the number of retained records.
    """

    def __init__(self, offset: int, count: int):
        """
        Initialize a new FtAggregateLimit instance.

        Args:
            offset (int): Starting point from which the records have to be retained.
            count (int): The total number of records to be retained.
        """
        super().__init__()
        self.offset = offset
        self.count = count

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the Limit clause.

        Returns:
            List[TEncodable]: A list of Limit clause arguments.
        """
        args = super().toArgs()
        args.extend([FtAggregateKeywords.LIMIT, str(self.offset), str(self.count)])
        return args


class Filter(FtAggregateClause):
    """
    A clause for filtering the results using predicate expression relating to values in each result. It is applied post query and relate to the current state of the pipeline.
    """

    def __init__(self, expression: TEncodable):
        """
        Initialize a new Filter instance.

        Args:
            expression (TEncodable): The expression to filter the results.
        """
        super().__init__()
        self.expression = expression

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the Filter clause.

        Returns:
            List[TEncodable]: A list arguments for the filter clause.
        """
        args = super().toArgs()
        args.extend([FtAggregateKeywords.FILTER, self.expression])
        return args


class Reducer:
    """
    A clause for reducing the matching results in each group using a reduction function. The matching results are reduced into a single record.
    """

    def __init__(
        self,
        function: TEncodable,
        args: List[TEncodable],
        name: Optional[TEncodable] = None,
    ):
        """
        Initialize a new Reducer instance.

        Args:
            function (TEncodable): The reduction function names for the respective group.
            args (List[TEncodable]): The list of arguments for the reducer.
            name (Optional[TEncodable]): User defined property name for the reducer.
        """
        self.function = function
        self.args = args
        self.name = name

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the Reducer.

        Returns:
            List[TEncodable]: A list of arguments for the reducer.
        """
        args: List[TEncodable] = [FtAggregateKeywords.REDUCE, self.function]
        args.append(str(len(self.args)))
        if self.args:
            args.extend(self.args)
        if self.name:
            args.extend([FtAggregateKeywords.AS, self.name])
        return args


class GroupBy(FtAggregateClause):
    """
    A clause for grouping the results in the pipeline based on one or more properties.
    """

    def __init__(
        self, properties: List[TEncodable], reducers: Optional[List[Reducer]] = None
    ):
        """
        Initialize a new GroupBy instance.

        Args:
            properties (List[TEncodable]): The list of properties to be used for grouping the results in the pipeline.
            reducers (Optional[List[Reducer]]): The list of functions that handles the group entries by performing multiple aggregate operations.
        """
        super().__init__()
        self.properties = properties
        self.reducers = reducers

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the GroupBy clause.

        Returns:
            List[TEncodable]: A list arguments for GroupBy clause.
        """

        args = super().toArgs()
        args.append(FtAggregateKeywords.GROUPBY)
        if self.properties:
            args.append(str(len(self.properties)))
            args.extend(self.properties)
        if self.reducers:
            for reducer in self.reducers:
                args.extend(reducer.toArgs())
        return args


class SortOrder(Enum):
    """
    All possible values for the sort order for the SortBy clause.
    """

    ASC = "ASC"
    """
    For sorting the results in ascending order.
    """
    DESC = "DESC"
    """
    For sorting the results in descending order.
    """


class SortByProperty:
    """
    This class represents the a single property for the SortBy clause.
    """

    def __init__(self, property: TEncodable, order: SortOrder):
        """
        Initialize a new SortByProperty instance.

        Args:
            property (TEncodable): The sorting parameter.
            order (SortOrder): The order for the sorting. This option can be added for each property.
        """
        self.property = property
        self.order = order

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the SortBy clause property.

        Returns:
            List[TEncodable]: A list of arguments for the SortBy clause property.
        """
        args: List[TEncodable] = []
        args.append(self.property)
        args.append(self.order.value)
        return args


class SortBy(FtAggregateClause):
    """
    A clause for sorting the pipeline up until the point of SORTBY, using a list of properties.
    """

    def __init__(self, properties: List[SortByProperty], max: Optional[int] = None):
        """
        Initialize a new SortBy instance.

        Args:
            properties (List[SortByProperty]): A list of sorting parameters for the sort operation.
            max: (Optional[int]): The MAX value for optimizing the sorting, by sorting only for the n-largest elements.
        """
        super().__init__()
        self.properties = properties
        self.max = max

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the SortBy clause.

        Returns:
            List[TEncodable]: A list of arguments for the SortBy clause.
        """
        args = super().toArgs()
        args.append(FtAggregateKeywords.SORTBY)
        if self.properties:
            args.append(str(len(self.properties) * 2))
            for property in self.properties:
                args.extend(property.toArgs())
        if self.max:
            args.extend([FtAggregateKeywords.MAX, str(self.max)])
        return args


class Apply(FtAggregateClause):
    """
    A clause for applying a 1-to-1 transformation on one or more properties and stores the result as a new property down the pipeline or replaces any property using this transformation.
    """

    def __init__(self, expression: TEncodable, name: TEncodable):
        """
        Initialize a new Apply instance.

        Args:
            expression (TEncodable): The expression to be transformed.
            name (TEncodable): The new property name to store the result of apply. This name can be referenced by further APPLY/SORTBY/GROUPBY/REDUCE operations down the pipeline.
        """
        super().__init__()
        self.expression = expression
        self.name = name

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments for the Apply clause.

        Returns:
            List[TEncodable]: A list of arguments for the Apply clause.
        """
        args = super().toArgs()
        args.extend(
            [
                FtAggregateKeywords.APPLY,
                self.expression,
                FtAggregateKeywords.AS,
                self.name,
            ]
        )
        return args


class FtAggregateOptions:
    """
    This class represents the optional arguments for the FT.AGGREGATE command.
    """

    def __init__(
        self,
        loadAll: Optional[bool] = False,
        loadFields: Optional[List[TEncodable]] = [],
        timeout: Optional[int] = None,
        params: Optional[Mapping[TEncodable, TEncodable]] = {},
        clauses: Optional[List[FtAggregateClause]] = [],
    ):
        """
        Initialize a new FtAggregateOptions instance.

        Args:
            loadAll (Optional[bool]): An option to load all fields declared in the index.
            loadFields (Optional[List[TEncodable]]): An option to load only the fields passed in this list.
            timeout (Optional[int]): Overrides the timeout parameter of the module.
            params (Optional[Mapping[TEncodable, TEncodable]]): The key/value pairs can be referenced from within the query expression.
            clauses (Optional[List[FtAggregateClause]]): FILTER, LIMIT, GROUPBY, SORTBY and APPLY clauses, that can be repeated multiple times in any order and be freely intermixed. They are applied in the order specified, with the output of one clause feeding the input of the next clause.
        """
        self.loadAll = loadAll
        self.loadFields = loadFields
        self.timeout = timeout
        self.params = params
        self.clauses = clauses

    def toArgs(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.AGGREGATE command.

        Returns:
            List[TEncodable]: A list of optional arguments for the FT.AGGREGATE command.
        """
        args: List[TEncodable] = []
        if self.loadAll:
            args.extend([FtAggregateKeywords.LOAD, "*"])
        elif self.loadFields:
            args.extend([FtAggregateKeywords.LOAD, str(len(self.loadFields))])
            args.extend(self.loadFields)
        if self.timeout:
            args.extend([FtAggregateKeywords.TIMEOUT, str(self.timeout)])
        if self.params:
            args.extend([FtAggregateKeywords.PARAMS, str(len(self.params) * 2)])
            for [name, value] in self.params.items():
                args.extend([name, value])
        if self.clauses:
            for clause in self.clauses:
                args.extend(clause.toArgs())
        return args
