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
    Type for the vector field type.
    """

    FLOAT32 = "FLOAT32"
    """
    FLOAT32 type of vector: 4 bytes per element, IEEE 754 single precision.
    """
    FLOAT16 = "FLOAT16"
    """
    FLOAT16 type of vector: 2 bytes per element, IEEE 754 half precision.
    Added in valkey-search 1.3.0 (``src/indexes/vector_base.h``). Callers
    must pass already-encoded bytes, the same way ``FLOAT32`` works today --
    GLIDE does not encode vectors on the client's behalf. Python has no
    built-in float16 packer; ``numpy.float16`` (``numpy.array(values,
    dtype=numpy.float16).tobytes()``) or the ``struct`` module's ``e`` format
    code both produce the correct byte layout.
    """
    BFLOAT16 = "BFLOAT16"
    """
    BFLOAT16 type of vector: 2 bytes per element, truncated IEEE 754 single
    precision (the top 16 bits of a FLOAT32 -- sign, 8-bit exponent, 7-bit
    mantissa). Added in valkey-search 1.3.0 (``src/indexes/vector_base.h``).
    Callers must pass already-encoded bytes; there is no built-in Python
    packer for this format. Encode by taking the high 16 bits of each
    FLOAT32 element's big-endian representation, e.g. for a little-endian
    FLOAT32 ``struct.pack("<f", value)``, the BFLOAT16 bytes are
    ``struct.pack("<f", value)[2:4]``.

    The engine may refuse to create or load a BFLOAT16 index on a host
    whose CPU lacks a SIMD BF16 code path (``CheckSimsimdBf16Capability()``
    in ``src/indexes/vector_base.h``); this is a server-side capability
    check, not a GLIDE-side restriction.
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
        sortable: bool = False,
    ):
        """
        Initialize a new field instance.

        Args:
            name (TEncodable): The name of the field.
            type (FieldType): The type of the field.
            alias (Optional[TEncodable]): An alias for the field.
            sortable (bool): If set, the field value can be used for sorting. Applies to TEXT, TAG, and NUMERIC fields.
        """
        self.name = name
        self.type = type
        self.alias = alias
        self.sortable = sortable

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


def _append_sortable_unf_args(
    args: List[TEncodable], sortable: bool, unf: bool
) -> None:
    """Append SORTABLE and, immediately after it, UNF -- the only position
    valkey-search recognises UNF in (``src/commands/ft_create_parser.cc``:
    ``// UNF is only recognized directly after SORTABLE, as in Redis``).
    Shared by the TEXT/TAG/NUMERIC field classes (the field types the
    ``Field`` base class's own docstring says SORTABLE applies to).
    """
    if sortable:
        args.append(FtCreateKeywords.SORTABLE)
        if unf:
            args.append(FtCreateKeywords.UNF)


class TextField(Field):
    """
    Field contains any blob of data.
    """

    def __init__(
        self,
        name: TEncodable,
        alias: Optional[TEncodable] = None,
        nostem: bool = False,
        weight: Optional[float] = None,
        withsuffixtrie: bool = False,
        nosuffixtrie: bool = False,
        sortable: bool = False,
        unf: bool = False,
    ):
        """
        Initialize a new TextField instance.

        Args:
            name (TEncodable): The name of the text field.
            alias (Optional[TEncodable]): An alias for the field.
            nostem (bool): If set, disables stemming when indexing the field.
            weight (Optional[float]): Declares the importance of this field when calculating result accuracy.
                Default is 1.
            withsuffixtrie (bool): If set, keeps a suffix trie for the field to optimize contains and suffix queries.
                Mutually exclusive with ``nosuffixtrie``.
            nosuffixtrie (bool): If set, disables the suffix trie for the field.
                Mutually exclusive with ``withsuffixtrie``.
            sortable (bool): If set, the field value can be used for sorting.
            unf (bool): If set, keeps the sorted value in its original (un-normalized) form, e.g. preserving case
                for TEXT/TAG fields that would otherwise be lowercased for sorting (valkey-search 1.3.0,
                ``src/commands/ft_create_parser.cc``). Requires ``sortable=True``; raises ``ValueError`` otherwise.
        """
        if unf and not sortable:
            raise ValueError("unf requires sortable=True.")
        super().__init__(name, FieldType.TEXT, alias, sortable)
        self.nostem = nostem
        self.weight = weight
        self.withsuffixtrie = withsuffixtrie
        self.nosuffixtrie = nosuffixtrie
        self.unf = unf

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        if self.nostem:
            args.append(FtCreateKeywords.NOSTEM)
        if self.weight is not None:
            args.extend([FtCreateKeywords.WEIGHT, str(self.weight)])
        if self.withsuffixtrie:
            args.append(FtCreateKeywords.WITHSUFFIXTRIE)
        elif self.nosuffixtrie:
            args.append(FtCreateKeywords.NOSUFFIXTRIE)
        _append_sortable_unf_args(args, self.sortable, self.unf)
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
        sortable: bool = False,
        unf: bool = False,
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
            sortable (bool): If set, the field value can be used for sorting.
            unf (bool): If set, keeps the sorted value in its original (un-normalized) form, e.g. preserving case
                for TEXT/TAG fields that would otherwise be lowercased for sorting (valkey-search 1.3.0,
                ``src/commands/ft_create_parser.cc``). Requires ``sortable=True``; raises ``ValueError`` otherwise.
        """
        if unf and not sortable:
            raise ValueError("unf requires sortable=True.")
        super().__init__(name, FieldType.TAG, alias, sortable)
        self.separator = separator
        self.case_sensitive = case_sensitive
        self.unf = unf

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        if self.separator:
            args.extend([FtCreateKeywords.SEPARATOR, self.separator])
        if self.case_sensitive:
            args.append(FtCreateKeywords.CASESENSITIVE)
        _append_sortable_unf_args(args, self.sortable, self.unf)
        return args


class NumericField(Field):
    """
    Field contains a number.
    """

    def __init__(
        self,
        name: TEncodable,
        alias: Optional[TEncodable] = None,
        sortable: bool = False,
        unf: bool = False,
    ):
        """
        Initialize a new NumericField instance.

        Args:
            name (TEncodable): The name of the numeric field.
            alias (Optional[TEncodable]): An alias for the field.
            sortable (bool): If set, the field value can be used for sorting.
            unf (bool): If set, keeps the sorted value in its original (un-normalized) form (valkey-search 1.3.0,
                ``src/commands/ft_create_parser.cc``). NUMERIC fields are never normalized for sorting, so this has
                no observable effect beyond emitting the keyword, but it's accepted here for API symmetry with
                TEXT/TAG. Requires ``sortable=True``; raises ``ValueError`` otherwise.
        """
        if unf and not sortable:
            raise ValueError("unf requires sortable=True.")
        super().__init__(name, FieldType.NUMERIC, alias, sortable)
        self.unf = unf

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        _append_sortable_unf_args(args, self.sortable, self.unf)
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
            type (VectorType): Vector type: `FLOAT32`, `FLOAT16`, or `BFLOAT16`. Equivalent to `TYPE` on the module API.
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
            args.extend([FtCreateKeywords.DISTANCE_METRIC, self.distance_metric.value])
        if self.type:
            args.extend([FtCreateKeywords.TYPE, self.type.value])
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
            type (VectorType): Vector type: `FLOAT32`, `FLOAT16`, or `BFLOAT16`. Equivalent to `TYPE` on the module API.
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
            type (VectorType): Vector type: `FLOAT32`, `FLOAT16`, or `BFLOAT16`. Equivalent to `TYPE` on the module API.
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
        sortable: bool = False,
    ):
        """
        Initialize a new VectorField instance.

        Args:
            name (TEncodable): The name of the vector field.
            algorithm (VectorAlgorithm): The vector indexing algorithm.
            alias (Optional[TEncodable]): An alias for the field.
            attributes (VectorFieldAttributes): Additional attributes to be passed with the vector field after the
                algorithm name.
            sortable (bool): If set, the SORTABLE parameter is emitted. Accepted by the server for
                compatibility but has no effect on vector fields.
        """
        super().__init__(name, FieldType.VECTOR, alias, sortable)
        self.algorithm = algorithm
        self.attributes = attributes

    def to_args(self) -> List[TEncodable]:
        args = super().to_args()
        args.append(self.algorithm.value)
        if self.attributes:
            attribute_list = self.attributes.to_args()
            args.append(str(len(attribute_list)))
            args.extend(attribute_list)
        if self.sortable:
            args.append(FtCreateKeywords.SORTABLE)
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
        score: Optional[float] = None,
        language: Optional[TEncodable] = None,
        skipinitialscan: bool = False,
        minstemsize: Optional[int] = None,
        withoffsets: bool = False,
        nooffsets: bool = False,
        nostopwords: bool = False,
        stopwords: Optional[List[TEncodable]] = None,
        punctuation: Optional[TEncodable] = None,
        filter: Optional[TEncodable] = None,
        score_field: Optional[TEncodable] = None,
        no_highlight: bool = False,
    ):
        """
        Initialize the FT.CREATE optional fields.

        Args:
            data_type (Optional[DataType]): The index data type. If not defined a ``HASH`` index is created.
            prefixes (Optional[List[TEncodable]]): A list of prefixes of index definitions.
            score (Optional[float]): Default score for documents in the index. Default is 1.0.
            language (Optional[TEncodable]): Default language for documents in the index, used for stemming. Passed
                through verbatim -- GLIDE does not validate it client-side. valkey-search 1.3.0-rc1 recognises 12
                values (``src/indexes/text/languages/``): ``arabic``, ``dutch``, ``english``, ``french``, ``german``,
                ``indonesian``, ``italian``, ``portuguese``, ``russian``, ``spanish``, ``swedish``, ``turkish``. An
                unrecognised value is rejected by the server, not by GLIDE.
            skipinitialscan (bool): If set, does not scan and index existing documents on index creation.
            minstemsize (Optional[int]): Minimum word length to stem. Words shorter than this are not stemmed.
            withoffsets (bool): If set, stores term offsets for document fields. Mutually exclusive with ``nooffsets``.
            nooffsets (bool): If set, does not store term offsets. Mutually exclusive with ``withoffsets``.
            nostopwords (bool): If set, disables stop-word filtering. Mutually exclusive with ``stopwords``.
            stopwords (Optional[List[TEncodable]]): Custom list of stop words. Mutually exclusive with ``nostopwords``.
            punctuation (Optional[TEncodable]): Custom punctuation characters to use during tokenization.
            filter (Optional[TEncodable]): A filter expression limiting which documents the index applies to
                (valkey-search 1.3.0, ``src/commands/ft_create_parser.cc``). A document that doesn't match this
                expression is excluded from the index even if its key matches a configured prefix. The expression
                cannot be empty.
            score_field (Optional[TEncodable]): The name of a document field whose numeric value is used as the
                document's custom score, multiplying into the final relevance score (valkey-search 1.3.0,
                ``src/commands/ft_create_parser.cc``: "the name of the document field whose numeric value is used
                as the document's custom score. Only one per index."). The field must already exist in the
                document; it is not itself a schema definition. Confirmed live: a document with a higher value in
                this field ranks correspondingly higher under the same query. Separately, FT.INFO only reports this
                option back under `index_definition`'s `score_field` key when `search.emulate-release` is at least
                1.3.0 (``ft_info_score_field`` compatibility flag, ``src/index_schema.cc`` `RespondWithInfo()`) --
                below that level the pair is omitted entirely and `default_score` reports the literal string "1"
                regardless of this option. The scoring behavior itself (above) is unaffected by this flag; only
                FT.INFO's reporting of it is gated. See upstream COMPATIBILITY.md's "Sunsetting of Incompatible
                Behavior" section.
            no_highlight (bool): Emits NOHL. valkey-search does not support highlighting at all, so this is a
                genuine no-op on this engine (confirmed live and in source: ``src/commands/ft_create_parser.cc``
                "Highlighting is not supported, so NOHL is accepted and the match result deliberately discarded" --
                the token is accepted but not even stored for FT.INFO to report, unlike SORTABLE UNF which IS
                stored and reported despite also being a no-op). Implemented for wire compatibility with
                RediSearch clients.
        """
        self.data_type = data_type
        self.prefixes = prefixes
        self.score = score
        self.language = language
        self.skipinitialscan = skipinitialscan
        self.minstemsize = minstemsize
        self.withoffsets = withoffsets
        self.nooffsets = nooffsets
        self.nostopwords = nostopwords
        self.stopwords = stopwords
        self.punctuation = punctuation
        self.filter = filter
        self.score_field = score_field
        self.no_highlight = no_highlight

    def _validate_options(self) -> None:
        """Validate mutually exclusive options."""
        if self.withoffsets and self.nooffsets:
            raise ValueError("withoffsets and nooffsets are mutually exclusive.")
        if self.nostopwords and self.stopwords is not None:
            raise ValueError("nostopwords and stopwords are mutually exclusive.")
        if self.filter is not None and not self.filter:
            raise ValueError("filter cannot be empty.")

    def _append_offset_args(self, args: List[TEncodable]) -> None:
        """Append offset-related arguments."""
        if self.withoffsets:
            args.append(FtCreateKeywords.WITHOFFSETS)
        elif self.nooffsets:
            args.append(FtCreateKeywords.NOOFFSETS)

    def _append_stopword_args(self, args: List[TEncodable]) -> None:
        """Append stopword-related arguments."""
        if self.nostopwords:
            args.append(FtCreateKeywords.NOSTOPWORDS)
        elif self.stopwords is not None:
            args.append(FtCreateKeywords.STOPWORDS)
            args.append(str(len(self.stopwords)))
            args.extend(self.stopwords)

    def to_args(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.CREATE command.

        Returns:
            List[TEncodable]:
                List of FT.CREATE optional arguments.
        """
        self._validate_options()
        args: List[TEncodable] = []
        if self.data_type:
            args.extend([FtCreateKeywords.ON, self.data_type.value])
        if self.prefixes:
            args.append(FtCreateKeywords.PREFIX)
            args.append(str(len(self.prefixes)))
            args.extend(self.prefixes)
        if self.score is not None:
            args.extend([FtCreateKeywords.SCORE, str(self.score)])
        if self.language is not None:
            args.extend([FtCreateKeywords.LANGUAGE, self.language])
        if self.skipinitialscan:
            args.append(FtCreateKeywords.SKIPINITIALSCAN)
        if self.minstemsize is not None:
            args.extend([FtCreateKeywords.MINSTEMSIZE, str(self.minstemsize)])
        self._append_offset_args(args)
        self._append_stopword_args(args)
        if self.punctuation is not None:
            args.extend([FtCreateKeywords.PUNCTUATION, self.punctuation])
        if self.filter is not None:
            args.extend([FtCreateKeywords.FILTER, self.filter])
        if self.score_field is not None:
            args.extend([FtCreateKeywords.SCORE_FIELD, self.score_field])
        if self.no_highlight:
            args.append(FtCreateKeywords.NOHL)
        return args
