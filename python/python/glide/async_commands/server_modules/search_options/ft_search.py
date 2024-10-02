from typing import Optional, List, Mapping
from glide.constants import TEncodable
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtSearchKeywords

class ReturnFieldInfo:
    def __init__(
        self,
        identifier: str,
        alias: Optional[str]
    ):
        self.identifier = identifier
        self.alias = alias
    
    def getReturnInfo(self) -> List[str]:
        args = []
        if self.identifier:
            args.append(self.identifier)
            if self.alias:
                args.append(FtSearchKeywords.AS)
                args.append(self.alias)
        return args


class LimitInfo:
    def __init__(
        self,
        offset: int,
        count: int 
    ):
        self.offset = offset
        self.cout = count

    def getLimitInfo(self) -> List[str]:
        args = []
        args.append(FtSearchKeywords.LIMIT)
        if self.offset:
            args.append(self.offset)
        if self.count:
            args.append(self.count)
        return args

class Count(Enum):
    IS_COUNT = 1
    IS_NOT_COUNT = 2

class FtSearchOptions:
    def __init__(
        self,
        returnFields: List[ReturnFieldInfo] = [],
        timeout: Optional[int] = None,
        params: Mapping[TEncodable, TEncodable] = {},
        limit: Optional[LimitInfo] = None,
        count: Count = Count.IS_NOT_COUNT
    ):
        self.returnFields = returnFields
        self.timeout = timeout
        self.params = params
        self.limit = limit
        self.count = count

    def getSearchOptions(self) -> List[str]:
        args = []
        if self.returnFields and len(self.returnFields) > 0:
            args.append(FtSearchKeywords.RETURN)
            args.append(len(self.returnFields))
            for returnField in self.returnFields:
                args = args + returnField.getReturnInfo()
        if self.timeout:
            args.append(FtSearchKeywords.TIMEOUT)
            args.append(self.timeout)
        if self.params and len(self.params):
            args.append(FtSearchKeywords.PARAMS)
            args.append(len(self.params))
            for name, value in self.params.items():
                args.append(name)
                args.append(value)
        if self.limit:
            args = args + self.limit.getLimitInfo()
        if self.count == Count.IS_COUNT:
            args.append(FtSearchKeywords.COUNT)
        return args
