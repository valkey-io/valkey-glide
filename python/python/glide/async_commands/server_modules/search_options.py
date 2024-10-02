from typing import Optional, List, Mapping
from enum import Enum
from glide.constants import TEncodable

class FieldType(Enum):
    TEXT = 1
    TAG = 2
    NUMERIC = 3
    GEO = 4
    VECTOR = 5
    GEOSHAPE = 6

class DataType(Enum):
    HASH = 1
    JSON = 2

class FieldInfo:
    def __init__(
        self,
        identifier: str,
        type: FieldType,
        alias: Optional[str] = None,
        isSortable: Optional[bool] = None,
        isUnnormalized: Optional[bool] = None,
        isNoIndex: Optional[bool] = None
    ):
        self.identifier = identifier
        self.alias = alias
        self.type = type
        self.isSortable = isSortable
        self.isUnnormalized = isUnnormalized
        self.isNoIndex = isNoIndex

    def getFieldInfo(self) -> List[str]:
        args = []
        if self.identifier:
            args.append(self.identifier)
        if self.alias:
            args.append("AS")
            args.append(self.alias)
        if self.type:
            args.append(self.type.name)
        if self.isSortable:
            args.append("SORTABLE")
            if self.isUnnormalized:
                args.append("UNF")
        if self.isNoIndex:
            args.append("NOINDEX")
        return args

        
class FtCreateOptions:
    def __init__(
        self,
        dataType: Optional[DataType] = None,
        prefixes: Optional[List[str]] = None,
    ):
        self.dataType = dataType
        self.prefixes = prefixes

    def getCreateOptions(self) -> List[str]:
        args = []
        if self.dataType:
            args.append("ON")
            args.append(self.dataType.name)
        if self.prefixes:
            args.append("PREFIX")
            args.append(str(len(self.prefixes)))
            for prefix in self.prefixes:
                args.append(prefix)
        return args

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

class FtSearchOptions:
    def __init__(
        self,
        returnFields: List[ReturnFieldInfo] = [],
        timeout: Optional[int] = None,
        params: Mapping[TEncodable, TEncodable] = {},
        limit: Optional[LimitInfo] = None,
        count: Optional[bool] = None
    ):
        self.returnFields = returnFields
        self.timeout = timeout
        self.params = params
        self.limit = limit
        self.count = count
