from typing import Optional, List, Mapping
from glide.constants import TEncodable
from enum import Enum

class ReturnFieldInfo:
    def __init__(
        self,
        identifier: str,
        alias: Optional[str]
    ):
        self.identifier = identifier
        self.alias = alias

class LimitInfo:
    def __init__(
        self,
        offset: int,
        count: int 
    ):
        self.offset = offset
        self.cout = count

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
