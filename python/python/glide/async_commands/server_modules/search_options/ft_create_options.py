from abc import ABC, abstractmethod
from typing import Dict, Optional, List
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtCreateKeywords

class FieldType(Enum):
    """
    All possible options for the data type of identifier field values used in the index.
    """

    TEXT = "TEXT"
    """
    If the field contains any blob of data.
    """
    TAG = "TAG"
    """
    If the field contains a tag field.     
    """
    NUMERIC = "NUMERIC"
    """
    If the field contains a number.
    """
    GEO = "GEO"
    """
    If the field contains a coordinate(latitude and longitude separated by a comma).
    """
    VECTOR = "VECTOR"
    """
    If the field is a vector field that supports vector search.
    """

class SortableOptions(Enum):
    """
    Options to make an index field as either sortable or not sortable.
    """

    IS_SORTABLE = "IS_SORTABLE"
    """
    If the index field is sortable.
    """
    IS_SORTABLE_UNF = "IS_SORTABLE_UNF"
    """

    """

# class UNNORMALIZED(Enum):
#     """
#     Options for making a sortable index field as either normalized or not normalized.
#     """

#     NOT_UNNORMALIZED = 1
#     """
#     If a sortable field has to remain normalized(The default behaviour)
#     """
#     IS_UNNORMALIZED = 2
#     """
#     If a sortable field has to be changed to unnormalized.
#     """

# class NoIndexOptions(Enum):
#     """
#     Options for either making a attribute field indexed or not indexed.
#     """

#     IS_INDEX = "IS_INDEX"
#     """
#     If the field is indexed. This is the default behaviour for all fields that are part of the index schema.
#     """
#     NO_INDEX = "NO_INDEX"
#     """
#     If the field is not indexed. This will override the default behaviour and make the field not indexed.
#     """


# class CaseSensitive(Enum):
#     """
#     The option to select if case sensitive or not case sensitive. Used only in TAG type in [FT.CREATE].
#     """

#     IS_CASE_SENSITIVE = 1,
#     """
#     This option will select case sensitive option for the TAG type field options.
#     """

#     NOT_CASE_SENSITIVE = 2
#     """
#     This option will not select case sensitive option for TAG type field options.
#     """

class VectorAlgorithm(Enum):
    """
    Algorithm for vector type fields used for vector similarity search.
    """

    HNSW = "HNSW"
    """
    Hierarchical Navigable Small World algorithm.
    """
    FLAT = "FLAT"
    """
    Flat algorithm or the brute force algorithm.
    """

class DistanceMetricType(Enum):
    """
    The metric options for the distance in vector type field.
    """

    L2 = "L2"
    """
    Euclidean distance
    """
    IP = "IP"
    """
    Inner product
    """
    COSINE = "COSINE"
    """
    Cosine distance
    """

class VectorType(Enum):
    """
    Vector type for the vector field type.
    """

    FLOAT32 = "FLOAT32"
    """
    FLOAT32 type of vector. The only supported type.
    """

class Field(ABC):
    """
    Abstract base class for defining fields in a schema.

    Args:
        name (str): The name of the field.
        type (FieldType): The type of the field.
        alias (Optional[str]): An alias for the field.
    """

    @abstractmethod
    def __init__(
        self,
        name: str,
        type: FieldType,
        alias: Optional[str] = None,
    ):
        """
        Initialize a new field instance.
        """
        self.name = name
        self.type = type
        self.alias = alias


    @abstractmethod
    def getFieldArgs(self) -> List[str]:
        """
        Get the arguments representing the field.

        Returns:
            List[str]: A list of field arguments.
        """
        args = [self.name]
        if self.alias:
            args.extend([FtCreateKeywords.AS, self.alias])
        args.append(self.type.value)
        return args
    
class SortableIndexableField(Field):
    """
    Abstract base class for defining sortable and indexable fields in a schema.
    Args:
        name (str): The name of the field.
        type (FieldType): The type of the field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SORTABLE_OPTIONS]): If  sets the field as sortable with the specified sortable option.
        noIndex (bool): Indicate whether the field should be excluded from indexing.
    """

    @abstractmethod
    def __init__(
        self,
        name: str,
        type: FieldType,
        alias: Optional[str] = None,
        sortable: Optional[SortableOptions] = None,
        noIndex: bool = False,
    ):
        """Initialize a new SortableField instance."""
        super().__init__(name, type, alias)
        self.sortable = sortable
        self.noIndex = noIndex

    def getFieldArgs(self) -> List[str]:
        """
        Get the arguments representing the field.

        Returns:
            List[str]: A list of field arguments.
        """
        args = super().getFieldArgs()
        if self.sortable == SortableOptions.IS_SORTABLE or self.sortable == SortableOptions.IS_SORTABLE_UNF:
            args.append(FtCreateKeywords.SORTABLE)
            if self.sortable == SortableOptions.IS_SORTABLE_UNF:
                args.append(FtCreateKeywords.UNF)
        if self.noIndex:
            args.append(FtCreateKeywords.NO_INDEX)
        return args

class TextField(SortableIndexableField):
    """
    Class for defining text fields in a schema.

    Args:
        name (str): The name of the text field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        noIndex (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOptions] = None,
        noIndex: bool = False,
    ):
        """
        Initialize a new TextField instance.
        """
        super().__init__(name, FieldType.TEXT, alias, sortable, noIndex)

    def getFieldArgs(self) -> List[str]:
        """
        Get the arguments representing the text field.

        Returns:
            List[str]: A list of text field arguments.
        """
        args = super().getFieldArgs()
        return args

class TagField(SortableIndexableField):
    """
    Class for defining tag fields in a schema.

    Args:
        name (str): The name of the tag field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOptions]): If not None, sets the field as sortable with the specified sortable option.
        noIndex (bool): Indicate whether the field should be excluded from indexing.
        separator (Optional[str]): Specify how text in the attribute is split into individual tags. Must be a single character.
        caseSensitive (bool): Preserve the original letter cases of tags.
            If set to False, characters are converted to lowercase by default.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOptions] = None,
        noIndex: bool = False,
        separator: Optional[str] = None,
        caseSensitive: bool = False,
    ):
        """
        Initialize a new TagField instance.
        """
        super().__init__(name, FieldType.TAG, alias, sortable, noIndex)
        self.sortable = sortable
        self.noIndex = noIndex
        self.separator = separator
        self.caseSensitive = caseSensitive

    def getFieldArgs(self) -> List[str]:
        """
        Get the arguments representing the tag field.

        Returns:
            List[str]: A list of tag field arguments.
        """
        args = super().getFieldArgs()
        if self.separator:
            args.extend([FtCreateKeywords.SEPARATOR, self.separator])
        if self.caseSensitive:
            args.append(FtCreateKeywords.CASESENSITIVE)
        return args

class NumericField(SortableIndexableField):
    """
    Class for defining numeric fields in a schema.

    Args:
        name (str): The name of the numeric field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): If not None, sets the field as sortable with the specified sortable option.
        no_index (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOptions] = None,
        noIndex: bool = False,
    ):
        """
        Initialize a new NumericField instance.
        """
        super().__init__(name, FieldType.NUMERIC, alias, sortable, noIndex)


class GeoField(SortableIndexableField):
    """
    Class for defining geo fields in a schema.

    Args:
        name (str): The name of the geo field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOptions]): If not None, sets the field as sortable with the specified sortable option.
        noIndex (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOptions] = None,
        noIndex: bool = False,
    ):
        """
        Initialize a new GeoField instance.
        """
        super().__init__(name, FieldType.GEO.value, alias, sortable, noIndex)


class VectorFieldAttributes(ABC):
    """
    Abstract base class for defining vector field attributes

    Args:
    """

    @abstractmethod
    def __init__(
        self,
        dim: int,
        distanceMetric: DistanceMetricType,
        type: VectorType
    ):
        """
        Initialize a new vector field attributes instance.
        """
        self.dim = dim
        self.distanceMetric = distanceMetric
        self.type = type

    @abstractmethod
    def getVectorFieldAttributes(self) -> List[str]:
        """
        Get the arguments representing the field.

        Returns:
            List[str]: A list of field arguments.
        """
        args = []
        if self.dim:
            args.extend([FtCreateKeywords.DIM, str(self.dim)])
        if self.distanceMetric:
            args.extend([FtCreateKeywords.DISTANCE_METRIC, self.distanceMetric.name])
        if self.type:
            args.extend([FtCreateKeywords.TYPE, self.type.name])
        return args

class VectorFieldAttributesFlat(VectorFieldAttributes):
    def __init__(
        self,
        dim: int,
        distanceMetric: DistanceMetricType,
        type: VectorType,
        initialCap: Optional[int] = None
    ):
        """
        Initialize a new TagField instance.
        """
        super().__init__(dim, distanceMetric, type)
        self.initialCap = initialCap

    def getVectorFieldAttributes(self) -> List[str]:
        """
        Get the arguments representing the vector field created with FLAT algorithm.

        Returns:
            List[str]: A list of FLAT algorithm type vector arguments.
        """
        args = super().getVectorFieldAttributes()
        if self.initialCap:
            args.extend([FtCreateKeywords.INITIAL_CAP, str(self.initialCap)])
        return args
    
class VectorFieldAttributesHnsw(VectorFieldAttributes):
    def __init__(
        self,
        dim: int,
        distanceMetric: DistanceMetricType,
        type: VectorType,
        initialCap: Optional[int] = None,
        m: Optional[int] = None,
        efContruction: Optional[int] = None,
        efRuntime: Optional[int] = None
    ):
        """
        Initialize a new TagField instance.
        """
        super().__init__(dim, distanceMetric, type)
        self.initialCap = initialCap
        self.m = m
        self.efContruction = efContruction
        self.efRuntime = efRuntime

    def getVectorFieldAttributes(self) -> List[str]:
        """
        Get the arguments representing the vector field created with HSNW algorithm.

        Returns:
            List[str]: A list of HNSW algorithm type vector arguments.
        """
        args = super().getVectorFieldAttributes()
        if self.initialCap:
            args.extend([FtCreateKeywords.INITIAL_CAP, str(self.initialCap)])
        if self.m:
            args.extend([FtCreateKeywords.M, str(self.m)])
        if self.efContruction:
            args.extend([FtCreateKeywords.EF_CONSTRUCTION, str(self.efContruction)])
        if self.efRuntime:
            args.extend([FtCreateKeywords.EF_RUNTIME, str(self.efRuntime)])                        
        return args


class VectorField(Field):
    """
    Class for defining vector fields in a schema.

    Args:
        name (str): The name of the vector field.
        algorithm (str): The vector indexing algorithm.
        alias (Optional[str]): An alias for the field.
    """

    def __init__(
        self,
        name: str,
        algorithm: VectorAlgorithm,
        attributes: VectorFieldAttributes,
        alias: Optional[str] = None
    ):
        """
        Initialize a new VectorField instance.
        """
        super().__init__(name, FieldType.VECTOR, alias)
        self.algorithm = algorithm
        self.attributes = attributes

    def getFieldArgs(self) -> List[str]:
        """
        Get the arguments representing the vector field.

        Returns:
            List[str]: A list of vector field arguments.
        """
        args = super().getFieldArgs()
        args.append(self.algorithm.value)
        if self.attributes:
            attributeList = self.attributes.getVectorFieldAttributes()
            args.append(str(len(attributeList)))
            args.extend(attributeList)
        return args

class DataType(Enum):
    """
    Options for the type of data for which the index is being created.
    """

    HASH = 1
    """
    If the created index will index HASH data.
    """
    JSON = 2
    """
    If the created index will index JSON document data.
    """



# class FieldInfo:
#     """
#     This class represents the arguments after the SCHEMA keyword to create an index with the identifier 
#     referencing the actual name of field in the JSON/HASH.
    
#     All the arguments associated with a single field after the SCHEMA keyword are encapsulated in this class.
#     [FT.CREATE] uses a List of this class to represent information for multiple fields to create multiple indexes.

#     Args:
#         identifier (str): The name of the actual field in the JSON/HASH, whose value has to be indexed.
#         type (FieldType): The data type of the field being indexed.
#         alias (Optional[str]): The alias to be used for the indexed field identifiers.
#         sortable (SORTABLE): This option will allow the user to sort the results by the value stored in the indexed attribute.
#         unnormalized (UNNORMALIZED): This option will allow the user to disable the default behaviour of normalizing the attribute values stored for sorting.
#         noIndex (NO_INDEX): This option will allow the user to disable indexing for a field.
#     """
#     def __init__(
#         self,
#         identifier: str,
#         fieldTypeInfo: FieldTypeInfo,
#         alias: Optional[str] = None,
#         sortable: SORTABLE = SORTABLE.NOT_SORTABLE,
#         unnormalized: UNNORMALIZED = UNNORMALIZED.NOT_UNNORMALIZED,
#         noIndex: NO_INDEX = NO_INDEX.NOT_NO_INDEX
#     ):
#         """
#         Initialize the attributes for the fields part of the index SCHEMA.
#         """
#         self.identifier = identifier
#         self.alias = alias
#         self.fieldTypeInfo = fieldTypeInfo
#         self.sortable = sortable
#         self.unnormalized = unnormalized
#         self.noIndex = noIndex

#     def getFieldInfo(self) -> List[str]:
#         """
#         Get the arguments for a single field for the index SCHEMA created using [FT.CREATE]

#         Returns:
#             List[str]:
#                 List of agruments for a index field.
#         """
#         args = []
#         if self.identifier:
#             args.append(self.identifier)
#         if self.alias:
#             args.append(FtCreateKeywords.AS)
#             args.append(self.alias)
#         if self.fieldTypeInfo:
#             args = args + self.fieldTypeInfo.getFieldTypeInfo()
#         if self.sortable == SORTABLE.IS_SORTABLE:
#             args.append(FtCreateKeywords.SORTABLE)
#             if self.unnormalized == UNNORMALIZED.IS_UNNORMALIZED:
#                 args.append(FtCreateKeywords.UNF)
#         if self.noIndex == NO_INDEX.IS_NO_INDEX:
#             args.append(FtCreateKeywords.NO_INDEX)
#         print("args in field info+++++++")
#         print(args)
#         return args

        
class FtCreateOptions:
    """
    This class represents the input options to be used in the [FT.CREATE](https://valkey.io/commands/ftcreate/) command.
    All fields in this class are optional inputs for [FT.CREATE].

    Args:
        dataType (Optional[DataType]): The type of data to be indexed using [FT.CREATE].
        prefixes (Optional[List[str]]): The prefix of the key to be indexed.
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
