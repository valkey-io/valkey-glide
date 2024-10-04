from typing import Optional, List
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtCreateKeywords


class FieldType(Enum):
    """
    All possible options for the data type of identifier field values used in the index.
    """

    TEXT = 1
    """
    If the field contains a blob of data.
    """
    TAG = 2
    """
    If the field contains a tag field.
    """
    NUMERIC = 3
    """
    If the field contains a number.
    """
    GEO = 4
    """
    If the field contains a coordinate(latitude and longitude separated by a comma).
    """
    VECTOR = 5
    """
    If the field contains the values of either SPHERICAL or FLAT coordinate system.
    """
    GEOSHAPE = 6

class DataType(Enum):
    """
    The type of data for which the index is being created.
    """

    HASH = 1
    """
    If the created index will index HASH data.
    """
    JSON = 2
    """
    If the created index will index JSON document data.
    """

class SORTABLE(Enum):
    """
    Options to make an index field as either sortable or not sortable.
    """

    NOT_SORTABLE = 1
    """
    If the index field is not sortable.
    """
    IS_SORTABLE = 2
    """
    If the index field is sortable.
    """

class UNNORMALIZED(Enum):
    """
    Options for making a sortable index field as either normalized or not normalized.
    """

    NOT_UNNORMALIZED = 1
    """
    If a sortable field has to remain normalized(The default behaviour)
    """
    IS_UNNORMALIZED = 2
    """
    If a sortable field has to be changed to unnormalized.
    """

class NO_INDEX(Enum):
    """
    Options for either making a attribute field indexed or not indexed.
    """

    NOT_NO_INDEX = 1
    """
    If the field is indexed. This is the default behaviour for all fields that are part of the index schema.
    """
    IS_NO_INDEX = 2
    """
    If the field is not indexed. This will override the default behaviour and make the field not indexed.
    """

class FieldInfo:
    """
    This class represents the arguments after the SCHEMA keyword to create an index with the identifier 
    referencing the actual name of field in the JSON/HASH.
    
    All the arguments associated with a single field after the SCHEMA keyword are encapsulated in this class.
    [FT.CREATE] uses a List of this class to represent information for multiple fields to create multiple indexes.

    Args:
        identifier (str): The name of the actual field in the JSON/HASH, whose value has to be indexed.
        type (FieldType): The data type of the field being indexed.
        alias (Optional[str]): The alias to be used for the indexed field identifiers.
        sortable (SORTABLE): This option will allow the user to sort the results by the value stored in the indexed attribute.
        unnormalized (UNNORMALIZED): This option will allow the user to disable the default behaviour of normalizing the attribute values stored for sorting.
        noIndex (NO_INDEX): This option will allow the user to disable indexing for a field.
    """
    def __init__(
        self,
        identifier: str,
        type: FieldType,
        alias: Optional[str] = None,
        sortable: SORTABLE = SORTABLE.NOT_SORTABLE,
        unnormalized: UNNORMALIZED = UNNORMALIZED.NOT_UNNORMALIZED,
        noIndex: NO_INDEX = NO_INDEX.NOT_NO_INDEX
    ):
        """
        Initialize the attributes for the fields part of the index SCHEMA.
        """
        self.identifier = identifier
        self.alias = alias
        self.type = type
        self.sortable = sortable
        self.unnormalized = unnormalized
        self.noIndex = noIndex

    def getFieldInfo(self) -> List[str]:
        """
        Get the arguments for a single field for the index SCHEMA created using [FT.CREATE]

        Returns:
            List[str]:
                List of agruments for a index field.
        """
        args = []
        if self.identifier:
            args.append(self.identifier)
        if self.alias:
            args.append(FtCreateKeywords.AS)
            args.append(self.alias)
        if self.type:
            args.append(self.type.name)
        if self.sortable == SORTABLE.IS_SORTABLE:
            args.append(FtCreateKeywords.SORTABLE)
            if self.unnormalized == UNNORMALIZED.IS_UNNORMALIZED:
                args.append(FtCreateKeywords.UNF)
        if self.noIndex == NO_INDEX.IS_NO_INDEX:
            args.append(FtCreateKeywords.NO_INDEX)
        return args

        
class FtCreateOptions:
    """
    This class represents the input options to be used in the [FT.CREATE](https://valkey.io/commands/ftcreate/) command.
    All fields in this class are optional inputs for [FT.CREATE].

    Args:
        dataType (Optional[DataType]): The type of data to be indexed using the index created using [FT.CREATE]. If not specfied, the default data type is HASH.
        prefixes (Optional[List[str]]): The prefix of the key to be indexed. If not specified, all keys created in the data base will be indexed.
    """
    def __init__(
        self,
        dataType: Optional[DataType] = None,
        prefixes: Optional[List[str]] = None,
    ):
        """
        Initialize the [FT.CREATE] optional fields.
        """
        self.dataType = dataType
        self.prefixes = prefixes

    def getCreateOptions(self) -> List[str]:
        """
        Get the optional arguments for the [FT.CREATE] command.

        Returns:
            List[str]:
                List of [FT.CREATE] optional agruments.
        """
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
