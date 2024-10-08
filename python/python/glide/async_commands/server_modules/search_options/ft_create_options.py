from typing import Optional, List
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtCreateKeywords


class FieldType(Enum):
    """
    All possible options for the data type of identifier field values used in the index.
    """

    TEXT = 1
    """
    If the field contains any blob of data.
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
    If the field is a vector field that supports vector search.
    """
    GEOSHAPE = 6
    """
    If the field contains the values of either SPHERICAL or FLAT coordinate system.
    """

class CaseSensitive(Enum):
    """
    The option to select if case sensitive or not case sensitive. Used only in TAG type in [FT.CREATE].
    """

    IS_CASE_SENSITIVE = 1,
    """
    This option will select case sensitive option for the TAG type field options.
    """

    NOT_CASE_SENSITIVE = 2
    """
    This option will not select case sensitive option for TAG type field options.
    """

class VectorTypeAlgorithm(Enum):
    """
    Algorithm for vector type fields stored in the index.
    """

    HNSW = 1,
    """
    Hierarchical Navigable Small World algorithm.
    """
    FLAT = 2
    """
    Flat algorithm or the brute force algorithm.
    """

class DistanceMetricType(Enum):
    """
    The metric options for the distance in vector type field.
    """

    L2 = 1
    """
    Euclidean distance
    """
    IP = 2
    """
    Inner product
    """
    COSINE = 3
    """
    Cosine distance
    """

class VectorType(Enum):
    """
    Vector type for the vector field type.
    """

    FLOAT32 = 1
    """
    FLOAT32 type of vector. The only supported type.
    """

class VectorTypeFlatAttributes:
    """
    This class represents the additional attributes for the vector type field with FLAT algorithm.
    """

    def __init__(
        self,
        dim: int,
        distanceMetric: DistanceMetricType,
        type: VectorType,
        initialCap: Optional[int]
    ):
        """
        Initialize the attributes for the vector field type with FLAT algorithm.
        """
        self.dim = dim
        self.distanceMetric = distanceMetric
        self.type = type
        self.initialCap = initialCap

    def getVectorTypeFlatAttributes(self):
        """
        Get the additional arguments for vector field type with FLAT algorithm.

        Returns:
            List[str]:
                List of additional arguments.
        """
        args = []
        if self.dim:
            args.append(FtCreateKeywords.DIM)
            args.append(str(self.dim))
        if self.distanceMetric:
            args.append(FtCreateKeywords.DISTANCE_METRIC)
            args.append(self.distanceMetric.name)
        if self.type:
            args.append(FtCreateKeywords.TYPE)
            args.append(self.type.name)
        if self.initialCap:
            args.append(FtCreateKeywords.INITIAL_CAP)
            args.append(str(self.initialCap))
        return args
    
class VectorTypeHnswAttributes:
    """
    This class represents the additional attributes for the vector type field with HSWN algorithm.
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
        Initialize the attributes for the vector field type with HNSW algorithm.
        """
        self.dim = dim
        self.distanceMetric = distanceMetric
        self.type = type
        self.initialCap = initialCap
        self.m = m
        self.efContruction = efContruction
        self.efRuntime = efRuntime
    
    def getVectorTypeHnswAttributes(self):
        """
        Get the additional arguments for vector field type with HSWN algorithm.

        Returns:
            List[str]:
                List of additional arguments.
        """
        args = []
        if self.dim:
            args.append(FtCreateKeywords.DIM)
            args.append(str(self.dim))
        if self.distanceMetric:
            args.append(FtCreateKeywords.DISTANCE_METRIC)
            args.append(self.distanceMetric.name)
        if self.type:
            args.append(FtCreateKeywords.TYPE)
            args.append(self.type.name)
        if self.initialCap:
            args.append(FtCreateKeywords.INITIAL_CAP)
            args.append(str(self.initialCap))
        if self.m:
            args.append(FtCreateKeywords.M)
            args.append(str(self.m))
        if self.efContruction:
            args.append(FtCreateKeywords.EF_CONSTRUCTION)
            args.append(str(self.efContruction))
        if self.efRuntime:
            args.append(FtCreateKeywords.EF_RUNTIME)
            args.append(str(self.efRuntime))
        return args
        

class TagTypeOptions:
    """
    This class represents the additional arguments to be passed after the TAG type in the [FT.CREATE] command.
    """
    def __init__(
        self,
        separator: Optional[str],
        caseSensitive: CaseSensitive = CaseSensitive.NOT_CASE_SENSITIVE
    ):
        """
        Initialize the arguments for the vector field type with HNSW algorithm.
        """
        self.separator = separator
        self.caseSensitive = caseSensitive

    def getTagTypeOptions(self) -> List[str]:
        """
        Get the additional arguments for TAG type.

        Returns:
            List[str]:
                List of additional arguments.
        """
        args = []
        if self.separator:
            args.append(FtCreateKeywords.SEPARATOR)
            args.append(self.separator)
        if self.caseSensitive:
            args.append(FtCreateKeywords.CASESENSITIVE)
        return args

class VectorTypeOptions:
    """
    This class represents the additional arguments to be passed after the VECTOR type in the [FT.CREATE] command.
    """

    def __init__(
        self,
        algorithm: Optional[VectorTypeAlgorithm],
        vectorTypeFlatAttributes: Optional[VectorTypeFlatAttributes] = None,
        vectorTypeHnswAttributes: Optional[VectorTypeHnswAttributes] = None
        
    ):
        """
        Initialize the additional arguments.
        """
        self.algorithm = algorithm
        self.vectorTypeFlatAttributes = vectorTypeFlatAttributes
        self.vectorTypeHnswAttributes = vectorTypeHnswAttributes
     
    def getVectorTypeOptions(self) -> List[str]:
        """
        Get the additional arguments for VECTOR type.

        Returns:
            List[str]:
                List of additional arguments.
        """
        args = []
        if self.algorithm:
            args.append(self.algorithm.name)
        if self.algorithm == VectorTypeAlgorithm.FLAT and self.vectorTypeFlatAttributes:
            flatArgs = self.vectorTypeFlatAttributes.getVectorTypeFlatAttributes()
            args.append(str(len(flatArgs)))
            args = args + flatArgs
        if self.algorithm == VectorTypeAlgorithm.HNSW and self.vectorTypeHnswAttributes:
            hnswArgs = self.vectorTypeHnswAttributes.getVectorTypeHnswAttributes()
            args.append(str(len(hnswArgs)))
            args = args + hnswArgs
        return args

class FieldTypeInfo:
    """
    This class represents a field type for [FT.CREATE] and the optional arguments associated with the field type.
    """
    def __init__(
        self,
        fieldType: FieldType,
        tagTypeOptions: Optional[TagTypeOptions] = None,
        vectorTypeOptions: Optional[VectorTypeOptions] = None
    ):
        """
        Initialize the field type and the optional arguments.
        """
        self.fieldType = fieldType
        self.tagTypeOptions = tagTypeOptions
        self.vectorTypeOptions = vectorTypeOptions

    def getFieldTypeInfo(self) -> List[str]:
        """
        Get the arguments with the field type and optional arguments for the field type.

        Returns:
            List[str]:
                List of additional arguments.
        """
        args = []
        if self.fieldType:
            args.append(self.fieldType.name)
        if self.fieldType == FieldType.TAG and self.tagTypeOptions:
            args = args + self.tagTypeOptions.getTagTypeOptions()
        if self.fieldType == FieldType.VECTOR and self.vectorTypeOptions:
            args = args + self.vectorTypeOptions.getVectorTypeOptions()
        print("+++++++++++++")
        print(args)
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
        fieldTypeInfo: FieldTypeInfo,
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
        self.fieldTypeInfo = fieldTypeInfo
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
        if self.fieldTypeInfo:
            args = args + self.fieldTypeInfo.getFieldTypeInfo()
        if self.sortable == SORTABLE.IS_SORTABLE:
            args.append(FtCreateKeywords.SORTABLE)
            if self.unnormalized == UNNORMALIZED.IS_UNNORMALIZED:
                args.append(FtCreateKeywords.UNF)
        if self.noIndex == NO_INDEX.IS_NO_INDEX:
            args.append(FtCreateKeywords.NO_INDEX)
        print("args in field info+++++++")
        print(args)
        return args

        
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
