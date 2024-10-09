from abc import ABC, abstractmethod
from typing import Dict, Optional, List
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtCreateKeywords

class FieldType(Enum):
    """
    All possible values for the data type of field identifier for the SCHEMA option.
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
    Options for fields with SORTABLE argument.
    """

    IS_SORTABLE = "IS_SORTABLE"
    """
    If the index field is sortable.
    """
    IS_SORTABLE_UNF = "IS_SORTABLE_UNF"
    """
    If the index field is sortable and unnormalized. This disables the normalization and keep the original form of the field value.
    """

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
    Type type for the vector field type.
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
        sortable (Optional[SORTABLE_OPTIONS]): If not None, sets the field as sortable with the specified sortable option.
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
        Get the arguments representing the SortableIndexableField.

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
        caseSensitive (bool): Preserve the original letter cases of tags. If set to False, characters are converted to lowercase by default.
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
    Class for defining the numeric fields in a schema.

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
    Class for defining GEO fields in a schema.

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
    Abstract base class for defining vector field attributes to be used after the vector algorithm name.

    Args:
        dim (int): Number of dimensions in the vector.
        distanceMetric (DistanceMetricType): The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
        type (VectorType): Vector type. The only supported type is FLOAT32.
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
        Get the arguments to be used for the algorithm of the vector field.

        Returns:
            List[str]: A list of arguments.
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
    """
    Get the arguments to be used for the FLAT algorithm of the vector field.

    Args:
        dim (int): Number of dimensions in the vector.
        distanceMetric (DistanceMetricType): The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
        type (VectorType): Vector type. The only supported type is FLOAT32.
        initialCap (Optional[int]): Initial vector capacity in the index affecting memory allocation size of the index. Defaults to 1024.
    """
    def __init__(
        self,
        dim: int,
        distanceMetric: DistanceMetricType,
        type: VectorType,
        initialCap: Optional[int] = None
    ):
        """
        Initialize a new flat vector field attributes instance.
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
    """
    Get the arguments to be used for the HNSW algorithm of the vector field.

    Args:
        dim (int): Number of dimensions in the vector.
        distanceMetric (DistanceMetricType): The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
        type (VectorType): Vector type. The only supported type is FLOAT32.
        initialCap (Optional[int]): Initial vector capacity in the index affecting memory allocation size of the index. Defaults to 1024.
        m (Optional[int]): Number of maximum allowed outgoing edges for each node in the graph in each layer. Default is 16, maximum is 512.
        efContruction (Optional[int]): Controls the number of vectors examined during index construction. Default value is 200, Maximum value is 4096.
        efRuntime (Optional[int]): Controls the number of vectors examined during query operations. Default value is 10, Maximum value is 4096.
    """
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
    Class for defining vector field in a schema.

    Args:
        name (str): The name of the vector field.
        algorithm (VectorAlgorithm): The vector indexing algorithm.
        alias (Optional[str]): An alias for the field.
        attributes (VectorFieldAttributes): Additional attributes to be passed with the vector field after the algorithm name.
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
