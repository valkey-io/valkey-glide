# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from abc import ABC, abstractmethod
from enum import Enum
from typing import List, Optional

from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtCreateKeywords,
)
from glide_shared.constants import TEncodable


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
    Distance metrics to measure the degree of similarity between two vectors.

    The above metrics calculate distance between two vectors, where the smaller the value is, the
    closer the two vectors are in the vector space.
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
    Abstract base class for a vector search field.
    """

    @abstractmethod
    def __init__(
        self,
        name: TEncodable,
        type: FieldType,
        alias: Optional[TEncodable] = None,
    ):
        """
        Initialize a new field instance.

        Args:
            name (TEncodable): The name of the field.
            type (FieldType): The type of the field.
            alias (Optional[TEncodable]): An alias for the field.
        """
        self.name = name
        self.type = type
        self.alias = alias

    @abstractmethod
    def to_args(self) -> List[TEncodable]:
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
    Field contains any blob of data.
    """

    def __init__(self, name: TEncodable, alias: Optional[TEncodable] = None):
        """
        Initialize a new TextField instance.

        Args:
            name (TEncodable): The name of the text field.
            alias (Optional[TEncodable]): An alias for the field.
        """
        super().__init__(name, FieldType.TEXT, alias)

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        return args


class TagField(Field):
    """
    Tag fields are similar to full-text fields, but they interpret the text as a simple list of
    tags delimited by a separator character.

    For `HASH fields, separator default is a comma `,`. For `JSON` fields, there is no
    default separator; you must declare one explicitly if needed.
    """

    def __init__(
        self,
        name: TEncodable,
        alias: Optional[TEncodable] = None,
        separator: Optional[TEncodable] = None,
        case_sensitive: bool = False,
    ):
        """
        Initialize a new TagField instance.

        Args:
            name (TEncodable): The name of the tag field.
            alias (Optional[TEncodable]): An alias for the field.
            separator (Optional[TEncodable]): Specify how text in the attribute is split into individual tags. Must be a
                single character.
            case_sensitive (bool): Preserve the original letter cases of tags. If set to False, characters are converted to
                lowercase by default.
        """
        super().__init__(name, FieldType.TAG, alias)
        self.separator = separator
        self.case_sensitive = case_sensitive

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        if self.separator:
            args.extend([FtCreateKeywords.SEPARATOR, self.separator])
        if self.case_sensitive:
            args.append(FtCreateKeywords.CASESENSITIVE)
        return args


class NumericField(Field):
    """
    Field contains a number.
    """

    def __init__(self, name: TEncodable, alias: Optional[TEncodable] = None):
        """
        Initialize a new NumericField instance.

        Args:
            name (TEncodable): The name of the numeric field.
            alias (Optional[TEncodable]): An alias for the field.
        """
        super().__init__(name, FieldType.NUMERIC, alias)

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        return args


class VectorFieldAttributes(ABC):
    """
    Abstract base class for defining vector field attributes to be used after the vector algorithm name.
    """

    @abstractmethod
    def __init__(
        self, dimensions: int, distance_metric: DistanceMetricType, type: VectorType
    ):
        """
        Initialize a new vector field attributes instance.

        Args:
            dimensions (int): Number of dimensions in the vector. Equivalent to `DIM` on the module API.
            distance_metric (DistanceMetricType): The distance metric used in vector type field. Can be one of
                `[L2 | IP | COSINE]`. Equivalent to `DISTANCE_METRIC` on the module API.
            type (VectorType): Vector type. The only supported type is `FLOAT32`. Equivalent to `TYPE` on the module API.
        """
        self.dimensions = dimensions
        self.distance_metric = distance_metric
        self.type = type

    @abstractmethod
    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments to be used for the algorithm of the vector field.

        Returns:
            List[TEncodable]: A list of arguments.
        """
        args: List[TEncodable] = []
        if self.dimensions:
            args.extend([FtCreateKeywords.DIM, str(self.dimensions)])
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
        dimensions: int,
        distance_metric: DistanceMetricType,
        type: VectorType,
        initial_cap: Optional[int] = None,
    ):
        """
        Initialize a new flat vector field attributes instance.

        Args:
            dimensions (int): Number of dimensions in the vector. Equivalent to `DIM` on the module API.
            distance_metric (DistanceMetricType): The distance metric used in vector type field. Can be one of
                `[L2 | IP | COSINE]`. Equivalent to `DISTANCE_METRIC` on the module API.
            type (VectorType): Vector type. The only supported type is `FLOAT32`. Equivalent to `TYPE` on the module API.
            initial_cap (Optional[int]): Initial vector capacity in the index affecting memory allocation size of the index.
                Defaults to `1024`. Equivalent to `INITIAL_CAP` on the module API.
        """
        super().__init__(dimensions, distance_metric, type)
        self.initial_cap = initial_cap

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        if self.initial_cap:
            args.extend([FtCreateKeywords.INITIAL_CAP, str(self.initial_cap)])
        return args


class VectorFieldAttributesHnsw(VectorFieldAttributes):
    """
    Get the arguments to be used for the HNSW algorithm of the vector field.
    """

    def __init__(
        self,
        dimensions: int,
        distance_metric: DistanceMetricType,
        type: VectorType,
        initial_cap: Optional[int] = None,
        number_of_edges: Optional[int] = None,
        vectors_examined_on_construction: Optional[int] = None,
        vectors_examined_on_runtime: Optional[int] = None,
    ):
        """
        Initialize a new HNSW vector field attributes instance.

        Args:
            dimensions (int): Number of dimensions in the vector. Equivalent to `DIM` on the module API.
            distance_metric (DistanceMetricType): The distance metric used in vector type field. Can be one of
                `[L2 | IP | COSINE]`. Equivalent to `DISTANCE_METRIC` on the module API.
            type (VectorType): Vector type. The only supported type is `FLOAT32`. Equivalent to `TYPE` on the module API.
            initial_cap (Optional[int]): Initial vector capacity in the index affecting memory allocation size of the index.
                Defaults to `1024`. Equivalent to `INITIAL_CAP` on the module API.
            number_of_edges (Optional[int]): Number of maximum allowed outgoing edges for each node in the graph in each layer.
                Default is `16`, maximum is `512`. Equivalent to `M` on the module API.
            vectors_examined_on_construction (Optional[int]): Controls the number of vectors examined during index
                construction. Default value is `200`, Maximum value is `4096`. Equivalent to `EF_CONSTRUCTION` on the
                module API.
            vectors_examined_on_runtime (Optional[int]): Controls the number of vectors examined during query operations.
                Default value is `10`, Maximum value is `4096`. Equivalent to `EF_RUNTIME` on the module API.
        """
        super().__init__(dimensions, distance_metric, type)
        self.initial_cap = initial_cap
        self.number_of_edges = number_of_edges
        self.vectors_examined_on_construction = vectors_examined_on_construction
        self.vectors_examined_on_runtime = vectors_examined_on_runtime

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        if self.initial_cap:
            args.extend([FtCreateKeywords.INITIAL_CAP, str(self.initial_cap)])
        if self.number_of_edges:
            args.extend([FtCreateKeywords.M, str(self.number_of_edges)])
        if self.vectors_examined_on_construction:
            args.extend(
                [
                    FtCreateKeywords.EF_CONSTRUCTION,
                    str(self.vectors_examined_on_construction),
                ]
            )
        if self.vectors_examined_on_runtime:
            args.extend(
                [FtCreateKeywords.EF_RUNTIME, str(self.vectors_examined_on_runtime)]
            )
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
        alias: Optional[TEncodable] = None,
    ):
        """
        Initialize a new VectorField instance.

        Args:
            name (TEncodable): The name of the vector field.
            algorithm (VectorAlgorithm): The vector indexing algorithm.
            alias (Optional[TEncodable]): An alias for the field.
            attributes (VectorFieldAttributes): Additional attributes to be passed with the vector field after the
                algorithm name.
        """
        super().__init__(name, FieldType.VECTOR, alias)
        self.algorithm = algorithm
        self.attributes = attributes

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        args.append(self.algorithm.value)
        if self.attributes:
            attribute_list = self.attributes.to_args()
            args.append(str(len(attribute_list)))
            args.extend(attribute_list)
        return args


class DataType(Enum):
    """
    Type of the index dataset.
    """

    HASH = "HASH"
    """
    Data stored in hashes, so field identifiers are field names within the hashes.
    """
    JSON = "JSON"
    """
    Data stored as a JSON document, so field identifiers are JSON Path expressions.
    """


class FtCreateOptions:
    """
    This class represents the input options to be used in the FT.CREATE command.
    All fields in this class are optional inputs for FT.CREATE.
    """

    def __init__(
        self,
        data_type: Optional[DataType] = None,
        prefixes: Optional[List[TEncodable]] = None,
    ):
        """
        Initialize the FT.CREATE optional fields.

        Args:
            data_type (Optional[DataType]): The index data type. If not defined a `HASH` index is created.
            prefixes (Optional[List[TEncodable]]): A list of prefixes of index definitions.
        """
        self.data_type = data_type
        self.prefixes = prefixes

    def to_args(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.CREATE command.

        Returns:
            List[TEncodable]:
                List of FT.CREATE optional agruments.
        """
        args: List[TEncodable] = []
        if self.data_type:
            args.append(FtCreateKeywords.ON)
            args.append(self.data_type.value)
        if self.prefixes:
            args.append(FtCreateKeywords.PREFIX)
            args.append(str(len(self.prefixes)))
            for prefix in self.prefixes:
                args.append(prefix)
        return args
