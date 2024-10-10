# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from enum import Enum
from typing import List, Optional

from glide.async_commands.server_modules.ft_constants import FtCreateKeywords
from glide.constants import TEncodable


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
    VECTOR = "VECTOR"
    """
    If the field is a vector field that supports vector search.
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
    """

    @abstractmethod
    def __init__(
        self,
        name: TEncodable,
        type: FieldType,
        alias: Optional[str] = None,
    ):
        """
        Initialize a new field instance.

        Args:
            name (TEncodable): The name of the field.
            type (FieldType): The type of the field.
            alias (Optional[str]): An alias for the field.
        """
        self.name = name
        self.type = type
        self.alias = alias

    @abstractmethod
    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments representing the field.

        Returns:
            List[TEncodable]: A list of field arguments.
        """
        args = [self.name]
        if self.alias:
            args.extend([FtCreateKeywords.AS, self.alias])
        args.append(self.type.value)
        return args


class TextField(Field):
    """
    Class for defining text fields in a schema.
    """

    def __init__(self, name: TEncodable, alias: Optional[str] = None):
        """
        Initialize a new TextField instance.

        Args:
            name (TEncodable): The name of the text field.
            alias (Optional[str]): An alias for the field.
        """
        super().__init__(name, FieldType.TEXT, alias)

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments representing the text field.

        Returns:
            List[TEncodable]: A list of text field arguments.
        """
        args = super().toArgs()
        return args


class TagField(Field):
    """
    Class for defining tag fields in a schema.
    """

    def __init__(
        self,
        name: TEncodable,
        alias: Optional[str] = None,
        separator: Optional[str] = None,
        case_sensitive: bool = False,
    ):
        """
        Initialize a new TagField instance.

        Args:
            name (TEncodable): The name of the tag field.
            alias (Optional[str]): An alias for the field.
            separator (Optional[str]): Specify how text in the attribute is split into individual tags. Must be a single character.
            case_sensitive (bool): Preserve the original letter cases of tags. If set to False, characters are converted to lowercase by default.
        """
        super().__init__(name, FieldType.TAG, alias)
        self.separator = separator
        self.case_sensitive = case_sensitive

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments representing the tag field.

        Returns:
            List[TEncodable]: A list of tag field arguments.
        """
        args = super().toArgs()
        if self.separator:
            args.extend([FtCreateKeywords.SEPARATOR, self.separator])
        if self.case_sensitive:
            args.append(FtCreateKeywords.CASESENSITIVE)
        return args


class NumericField(Field):
    """
    Class for defining the numeric fields in a schema.
    """

    def __init__(self, name: TEncodable, alias: Optional[str] = None):
        """
        Initialize a new NumericField instance.

        Args:
            name (TEncodable): The name of the numeric field.
            alias (Optional[str]): An alias for the field.
        """
        super().__init__(name, FieldType.NUMERIC, alias)

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments representing the numeric field.

        Returns:
            List[TEncodable]: A list of numeric field arguments.
        """
        args = super().toArgs()
        return args


class VectorFieldAttributes(ABC):
    """
    Abstract base class for defining vector field attributes to be used after the vector algorithm name.
    """

    @abstractmethod
    def __init__(self, dim: int, distance_metric: DistanceMetricType, type: VectorType):
        """
        Initialize a new vector field attributes instance.

        Args:
            dim (int): Number of dimensions in the vector.
            distance_metric (DistanceMetricType): The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
            type (VectorType): Vector type. The only supported type is FLOAT32.
        """
        self.dim = dim
        self.distance_metric = distance_metric
        self.type = type

    @abstractmethod
    def toArgs(self) -> List[str]:
        """
        Get the arguments to be used for the algorithm of the vector field.

        Returns:
            List[str]: A list of arguments.
        """
        args = []
        if self.dim:
            args.extend([FtCreateKeywords.DIM, str(self.dim)])
        if self.distance_metric:
            args.extend([FtCreateKeywords.DISTANCE_METRIC, self.distance_metric.name])
        if self.type:
            args.extend([FtCreateKeywords.TYPE, self.type.name])
        return args


class VectorFieldAttributesFlat(VectorFieldAttributes):
    """
    Get the arguments to be used for the FLAT algorithm of the vector field.
    """

    def __init__(
        self,
        dim: int,
        distance_metric: DistanceMetricType,
        type: VectorType,
        initial_cap: Optional[int] = None,
    ):
        """
        Initialize a new flat vector field attributes instance.

        Args:
            dim (int): Number of dimensions in the vector.
            distance_metric (DistanceMetricType): The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
            type (VectorType): Vector type. The only supported type is FLOAT32.
            initial_cap (Optional[int]): Initial vector capacity in the index affecting memory allocation size of the index. Defaults to 1024.
        """
        super().__init__(dim, distance_metric, type)
        self.initial_cap = initial_cap

    def toArgs(self) -> List[str]:
        """
        Get the arguments representing the vector field created with FLAT algorithm.

        Returns:
            List[str]: A list of FLAT algorithm type vector arguments.
        """
        args = super().toArgs()
        if self.initial_cap:
            args.extend([FtCreateKeywords.INITIAL_CAP, str(self.initial_cap)])
        return args


class VectorFieldAttributesHnsw(VectorFieldAttributes):
    """
    Get the arguments to be used for the HNSW algorithm of the vector field.
    """

    def __init__(
        self,
        dim: int,
        distance_metric: DistanceMetricType,
        type: VectorType,
        initial_cap: Optional[int] = None,
        m: Optional[int] = None,
        ef_contruction: Optional[int] = None,
        ef_runtime: Optional[int] = None,
    ):
        """
        Initialize a new TagField instance.

        Args:
            dim (int): Number of dimensions in the vector.
            distance_metric (DistanceMetricType): The distance metric used in vector type field. Can be one of [L2 | IP | COSINE].
            type (VectorType): Vector type. The only supported type is FLOAT32.
            initial_cap (Optional[int]): Initial vector capacity in the index affecting memory allocation size of the index. Defaults to 1024.
            m (Optional[int]): Number of maximum allowed outgoing edges for each node in the graph in each layer. Default is 16, maximum is 512.
            ef_contruction (Optional[int]): Controls the number of vectors examined during index construction. Default value is 200, Maximum value is 4096.
            ef_runtime (Optional[int]): Controls the number of vectors examined during query operations. Default value is 10, Maximum value is 4096.
        """
        super().__init__(dim, distance_metric, type)
        self.initial_cap = initial_cap
        self.m = m
        self.ef_contruction = ef_contruction
        self.ef_runtime = ef_runtime

    def toArgs(self) -> List[str]:
        """
        Get the arguments representing the vector field created with HSNW algorithm.

        Returns:
            List[str]: A list of HNSW algorithm type vector arguments.
        """
        args = super().toArgs()
        if self.initial_cap:
            args.extend([FtCreateKeywords.INITIAL_CAP, str(self.initial_cap)])
        if self.m:
            args.extend([FtCreateKeywords.M, str(self.m)])
        if self.ef_contruction:
            args.extend([FtCreateKeywords.EF_CONSTRUCTION, str(self.ef_contruction)])
        if self.ef_runtime:
            args.extend([FtCreateKeywords.EF_RUNTIME, str(self.ef_runtime)])
        return args


class VectorField(Field):
    """
    Class for defining vector field in a schema.
    """

    def __init__(
        self,
        name: TEncodable,
        algorithm: VectorAlgorithm,
        attributes: VectorFieldAttributes,
        alias: Optional[str] = None,
    ):
        """
        Initialize a new VectorField instance.

        Args:
            name (TEncodable): The name of the vector field.
            algorithm (VectorAlgorithm): The vector indexing algorithm.
            alias (Optional[str]): An alias for the field.
            attributes (VectorFieldAttributes): Additional attributes to be passed with the vector field after the algorithm name.
        """
        super().__init__(name, FieldType.VECTOR, alias)
        self.algorithm = algorithm
        self.attributes = attributes

    def toArgs(self) -> List[TEncodable]:
        """
        Get the arguments representing the vector field.

        Returns:
            List[TEncodable]: A list of vector field arguments.
        """
        args = super().toArgs()
        args.append(self.algorithm.value)
        if self.attributes:
            attribute_list = self.attributes.toArgs()
            args.append(str(len(attribute_list)))
            args.extend(attribute_list)
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
    This class represents the input options to be used in the [FT.CREATE] command.
    All fields in this class are optional inputs for [FT.CREATE].
    """

    def __init__(
        self,
        data_type: Optional[DataType] = None,
        prefixes: Optional[List[str]] = None,
    ):
        """
        Initialize the [FT.CREATE] optional fields.

        Args:
            data_type (Optional[DataType]): The type of data to be indexed using [FT.CREATE].
            prefixes (Optional[List[str]]): The prefix of the key to be indexed.
        """
        self.data_type = data_type
        self.prefixes = prefixes

    def toArgs(self) -> List[str]:
        """
        Get the optional arguments for the [FT.CREATE] command.

        Returns:
            List[str]:
                List of [FT.CREATE] optional agruments.
        """
        args = []
        if self.data_type:
            args.append(FtCreateKeywords.ON)
            args.append(self.data_type.name)
        if self.prefixes:
            args.append(FtCreateKeywords.PREFIX)
            args.append(str(len(self.prefixes)))
            for prefix in self.prefixes:
                args.append(prefix)
        return args
