from enum import Enum
from typing import Dict, List, Optional


class SortableOption(Enum):
    """Sortable options: options for fields with SORTABLE argument.
    - SORTABLE - Sets the field as sortable.
    - SORTABLE_UNF - Sets the field as sortable, disables the normalization and keep the original form of the field value.
    """

    SORTABLE = "SORTABLE"
    SORTABLE_UNF = "SORTABLE UNF"


class PhoneticType(Enum):
    """Phonetic type: Activate phonetic matching for text searches, specifies the phonetic algorithm and language used.
    - DM_ENGLISH - Double metaphone for English
    - DM_FRENCH - Double metaphone for French
    - DM_PORTUGUESE - Double metaphone for Portuguese
    - DM_SPANISH - Double metaphone for Spanish
    """

    DM_ENGLISH = "dm:en"
    DM_FRENCH = "dm:fn"
    DM_PORTUGUESE = "dm:pt"
    DM_SPANISH = "dm:es"


class Field:
    """
    Base class for defining fields in a schema.

    Args:
        name (str): The name of the field.
        alias (Optional[str]): An alias for the field.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
    ):
        """Initialize a new Field instance."""
        self.name = name
        self.alias = alias

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the field.

        Returns:
            List[str]: A list of field arguments.
        """
        args = [self.name]
        if self.alias:
            args.extend(["AS", self.alias])
        return args


class TextField(Field):
    """
    Class for defining text fields in a schema.

    Args:
        name (str): The name of the text field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): Whether the field is sortable, options are: SORTABLE and SORTABLE_UNF.
        no_stem (bool): Whether to disables stemming when indexing field values.
        no_index (bool): Indicate whether the field should be excluded from indexing.
        phonetic (Optional[PhoneticType]): Activate phonetic matching for text searches, specifies the phonetic algorithm and language used (e.g., English: dm:en).
        weight (Optional[float]): Specify attribute importance of this attribute when calculating result accuracy. Default it 1.0.
        with_suffix_trie (bool): Improve query performance by utilizing a suffix trie for contains (foo) and suffix (*foo) queries.
            This feature avoids brute-force searches on the trie.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_stem: bool = False,
        no_index: bool = False,
        phonetic: Optional[PhoneticType] = None,
        weight: Optional[float] = 1.0,
        with_suffix_trie: bool = False,
    ):
        """Initialize a new TextField instance."""
        super().__init__(name, alias)
        self.sortable = sortable
        self.no_stem = no_stem
        self.no_index = no_index
        self.phonetic = phonetic
        self.weight = weight
        self.with_suffix_trie = with_suffix_trie

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the text field.

        Returns:
            List[str]: A list of text field arguments.
        """
        args = super().get_field_args()
        args.append("TEXT")
        if not self.sortable and self.no_index:
            raise ValueError("Cannot be both non-storable and non-indexed.")
        if self.sortable:
            args.extend(self.sortable.value.split(" "))
        if self.no_stem:
            args.append("NOSTEM")
        if self.phonetic:
            args.extend(["PHONETIC", self.phonetic.value])
        if self.weight:
            args.extend(["WEIGHT", str(self.weight)])
        if self.with_suffix_trie:
            args.append("WITHSUFFIXTRIE")
        if self.no_index:
            args.append("NOINDEX")
        return args


class TagField(Field):
    """
    Class for defining tag fields in a schema.

    Args:
        name (str): The name of the tag field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): Whether the field is sortable, options are: SORTABLE and SORTABLE_UNF.
        no_index (bool): Indicate whether the field should be excluded from indexing.
        separator (Optional[str]): Specify how text in the attribute is split into individual tags. The default is ,.
            Must be a single character.
        case_sensitive (bool): Preserve the original letter cases of tags.
            If not specified, characters are converted to lowercase by default.
        with_suffix_trie (bool): Improve query performance by utilizing a suffix trie for contains (foo) and suffix (*foo) queries.
            This feature avoids brute-force searches on the trie.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
        separator: Optional[str] = None,
        case_sensitive: bool = False,
        with_suffix_trie: bool = False,
    ):
        """Initialize a new TagField instance."""
        super().__init__(name, alias)
        self.sortable = sortable
        self.no_index = no_index
        self.separator = separator
        self.case_sensitive = case_sensitive
        self.with_suffix_trie = with_suffix_trie

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the tag field.

        Returns:
            List[str]: A list of tag field arguments.
        """
        args = super().get_field_args()
        args.append("TAG")
        if not self.sortable and self.no_index:
            raise ValueError("Cannot be both non-storable and non-indexed.")
        if self.sortable:
            args.extend(self.sortable.value.split(" "))
        if self.no_index:
            args.append("NOINDEX")
        if self.separator:
            args.extend(["SEPARATOR", self.separator])
        if self.case_sensitive:
            args.append("CASESENSITIVE")
        if self.with_suffix_trie:
            args.append("WITHSUFFIXTRIE")

        return args


class NumericField(Field):
    """
    Class for defining numeric fields in a schema.

    Args:
        name (str): The name of the numeric field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): Whether the field is sortable, options are: SORTABLE and SORTABLE_UNF.
        no_index (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
    ):
        """Initialize a new NumericField instance."""
        super().__init__(name, alias)
        self.sortable = sortable
        self.no_index = no_index

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the numeric field.

        Returns:
            List[str]: A list of numeric field arguments.
        """
        args = super().get_field_args()
        args.append("NUMERIC")
        if not self.sortable and self.no_index:
            raise ValueError("Cannot be both non-storable and non-indexed.")
        if self.sortable:
            args.extend(self.sortable.value.split(" "))
        if self.no_index:
            args.append("NOINDEX")

        return args


class GeoField(Field):
    """
    Class for defining geo fields in a schema.

    Args:
        name (str): The name of the geo field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): Whether the field is sortable, options are: SORTABLE and SORTABLE_UNF.
        no_index (bool): Indicate whether the field should be excluded from indexing.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
    ):
        """Initialize a new GeoField instance."""
        super().__init__(name, alias)
        self.sortable = sortable
        self.no_index = no_index

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the geo field.

        Returns:
            List[str]: A list of geo field arguments.
        """
        args = super().get_field_args()
        args.append("GEO")
        if not self.sortable and self.no_index:
            raise ValueError("Cannot be both non-storable and non-indexed.")
        if self.sortable:
            args.extend(self.sortable.value.split(" "))
        if self.no_index:
            args.append("NOINDEX")

        return args


class VectorField(Field):
    """
    Class for defining vector fields in a schema.

    Args:
        name (str): The name of the vector field.
        algorithm (str): The vector indexing algorithm.
        alias (Optional[str]): An alias for the field.
        attributes (Optional[Dict[str, str]]): Additional attributes for the vector field.
    """

    def __init__(
        self,
        name: str,
        algorithm: str,
        alias: Optional[str] = None,
        attributes: Optional[Dict[str, str]] = {},
    ):
        """Initialize a new VectorField instance."""
        super().__init__(name, alias)
        self.algorithm = algorithm.upper()
        self.attributes = attributes

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the vector field.

        Returns:
            List[str]: A list of vector field arguments.
        """
        args = super().get_field_args()
        args.append("VECTOR")
        if self.algorithm not in ["FLAT", "HNSW"]:
            raise ValueError(
                "Invalid algorithm for vector field. Supported algorithms: FLAT, HNSW"
            )

        args.append(self.algorithm)
        if self.attributes:
            args.append(str(2 * len(self.attributes)))
            for key, value in self.attributes.items():
                args.extend([key, value])

        return args


class GeoShapeField(Field):
    """
    Class for defining geo shape fields in a schema.

    Args:
        name (str): The name of the geo shape field.
        alias (Optional[str]): An alias for the field.
        sortable (Optional[SortableOption]): Whether the field is sortable, options are: SORTABLE and SORTABLE_UNF.
        no_index (bool): Indicate whether the field should be excluded from indexing.
        coordinate_system (Optional[str]): The coordinate system for the geo shape field.
    """

    def __init__(
        self,
        name: str,
        alias: Optional[str] = None,
        sortable: Optional[SortableOption] = None,
        no_index: bool = False,
        coordinate_system: Optional[str] = "SPHERICAL",
    ):
        """Initialize a new GeoShapeField instance."""
        super().__init__(name, alias)
        self.sortable = sortable
        self.no_index = no_index
        self.coordinate_system = coordinate_system

    def get_field_args(self) -> List[str]:
        """
        Get the arguments representing the geo shape field.

        Returns:
            List[str]: A list of geo shape field arguments.
        """
        args = super().get_field_args()
        args.append("GEOSHAPE")
        if not self.sortable and self.no_index:
            raise ValueError("Cannot be both non-storable and non-indexed.")
        if self.sortable:
            args.extend(self.sortable.value.split(" "))
        if self.no_index:
            args.append("NOINDEX")

        if self.coordinate_system:
            args.append(self.coordinate_system)

        return args
