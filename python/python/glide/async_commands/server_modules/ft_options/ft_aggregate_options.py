# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from typing import List, Mapping, Optional
from glide.constants import TEncodable
from glide.async_commands.server_modules.ft_options.ft_constants import FtAggregateKeywords
from enum import Enum

class FtAggregateClause(ABC):
    @abstractmethod
    def __init__(self):
        pass

    def toArgs(self) -> List[TEncodable]:
        args: List[TEncodable] = []
        return args

class Limit(FtAggregateClause):
    def __init__(
        self,
        offset: int,
        count: int
    ):
        super().__init__()
        self.offset = offset
        self.count = count
    
    def toArgs(self) -> List[TEncodable]:
        args = super().toArgs()
        args.extend([FtAggregateKeywords.LIMIT, str(self.offset), str(self.count)])
        return args

class Filter(FtAggregateClause):
    def __init__(
        self,
        expression: TEncodable
    ):
        super().__init__()
        self.expression = expression

    def toArgs(self) -> List[TEncodable]:
        args = super().toArgs()
        args.extend([FtAggregateKeywords.FILTER, self.expression])
        return args
    

class Reducer():
    def __init__(
        self,
        function: TEncodable,
        args: List[TEncodable],
        name: Optional[TEncodable] = None
    ):
        self.function = function
        self.args = args
        self.name = name
    
    def toArgs(self) -> List[TEncodable]:
        args: List[TEncodable] = [FtAggregateKeywords.REDUCE, self.function]
        if self.args:
            args.append(len(self.args))
            args.extend(self.args)
        if self.name:
            args.extend(FtAggregateKeywords.AS, self.name)
        return args
        
        
class GroupBy(FtAggregateClause):
    def __init__(
        self,
        properties: List[TEncodable],
        reducers: Optional[List[Reducer]] = None
    ):
        super().__init__()
        self.properties = properties
        self.reducers = reducers
    
    def toArgs(self) -> List[TEncodable]:
        args = super().toArgs()
        args.append(FtAggregateKeywords.GROUPBY)
        if self.properties:
            args.append(len(self.properties))
            args.extend(self.properties)
        if self.reducers:
            for reducer in self.reducers:
                args.extend(reducer.toArgs())
        return args


class SortOrder(Enum):
    ASC = "ASC"
    DESC = "DESC"


class SortByProperty():
    def __init__(
        self,
        property: TEncodable,
        order: SortOrder
    ):
        self.property = property
        self.order = order
    
    def toArgs(self) -> List[TEncodable]:
        args: List[TEncodable] = []
        args.append(self.property)
        args.append(self.order.value)
        return args
        

class SortBy(FtAggregateClause):
    def __init__(
        self,
        properties: List[SortByProperty],
        max: Optional[int] = None
    ):
        super().__init__()
        self.properties = properties
        self.max = max
    
    def toArgs(self) -> List[TEncodable]:
        args = super().toArgs()
        args.append(FtAggregateKeywords.SORTBY)
        if self.properties:
            args.append(str(len(self.properties)*2))
            for property in self.properties:
                args.extend(property.toArgs())
        if self.max:
            args.extend([FtAggregateKeywords.MAX, str(self.max)])
        return args


class Apply(FtAggregateClause):
    def __init__(
        self,
        expression: TEncodable,
        name: TEncodable
    ):
        super().__init__()
        self.expression = expression
        self.name = name

    def toArgs(self) -> List[TEncodable]:
        args = super().toArgs()
        args.extend([FtAggregateKeywords.APPLY, self.expression, FtAggregateKeywords.AS, self.name])
        return args


class FtAggregateOptions:
    def __init__(
        self,
        loadAll: Optional[bool] = False,
        loadFields: Optional[List[TEncodable]] = [],
        timeout: Optional[int] = None,
        params: Optional[Mapping[TEncodable, TEncodable]] = {},
        clauses: Optional[List[FtAggregateClause]] = []
    ):
        self.loadAll = loadAll
        self.loadFields = loadFields
        self.timeout = timeout
        self.params = params
        self.clauses = clauses

    def toArgs(self) -> List[TEncodable]:
        args: List[TEncodable] = []
        if self.loadAll:
            args.extend([FtAggregateKeywords.LOAD, "*"])
        elif self.loadFields:
            args.extend([FtAggregateKeywords.LOAD, str(len(self.loadFields))])
            args.extend(self.loadFields)
        if self.timeout:
            args.extend([FtAggregateKeywords.TIMEOUT, str(self.timeout)])
        if self.params:
            args.extend([FtAggregateKeywords.PARAMS, str(len(self.params)*2)])
            for [name, value] in self.params.items():
                args.extend([name, value])
        if self.clauses:
            for clause in self.clauses:
                args.extend(clause.toArgs())
        return args
