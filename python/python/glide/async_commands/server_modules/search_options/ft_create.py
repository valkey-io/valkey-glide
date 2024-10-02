from typing import Optional, List
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtCreateKeywords


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

class SORTABLE(Enum):
    NOT_SORTABLE = 1
    IS_SORTABLE = 2

class UNNORMALIZED(Enum):
    NOT_UNNORMALIZED = 1
    IS_UNNORMALIZED = 2

class NO_INDEX(Enum):
    NOT_NO_INDEX = 1
    IS_NO_INDEX = 2

class FieldInfo:
    def __init__(
        self,
        identifier: str,
        type: FieldType,
        alias: Optional[str] = None,
        isSortable: SORTABLE = SORTABLE.NOT_SORTABLE,
        isUnnormalized: UNNORMALIZED = UNNORMALIZED.NOT_UNNORMALIZED,
        isNoIndex: NO_INDEX = NO_INDEX.NOT_NO_INDEX
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
            args.append(FtCreateKeywords.AS)
            args.append(self.alias)
        if self.type:
            args.append(self.type.name)
        if self.isSortable:
            args.append(FtCreateKeywords.SORTABLE)
            if self.isUnnormalized:
                args.append(FtCreateKeywords.UNF)
        if self.isNoIndex:
            args.append(FtCreateKeywords.NO_INDEX)
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
            args.append(FtCreateKeywords.ON)
            args.append(self.dataType.name)
        if self.prefixes:
            args.append(FtCreateKeywords.PREFIX)
            args.append(str(len(self.prefixes)))
            for prefix in self.prefixes:
                args.append(prefix)
        return args
