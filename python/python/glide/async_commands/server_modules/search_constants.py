class CommandNames:
    """
    Command name constants for vector search.
    """

    FT_CREATE = "FT.CREATE"
    FT_SEARCH = "FT.SEARCH"
    INFO = "INFO"
    FT_DROPINDEX = "FT.DROPINDEX"

class FtCreateKeywords:
    """
    Keywords used in the [FT.CREATE] command statment.
    """

    SCHEMA = "SCHEMA"
    AS = "AS"
    SORTABLE = "SORTABLE"
    UNF = "UNF"
    NO_INDEX = "NOINDEX"
    ON = "ON"
    PREFIX = "PREFIX"

class FtSearchKeywords:
    """
    Keywords used in the [FT.SEARCH] command statement.
    """

    RETURN = "RETURN"
    AS = "AS"
    LIMIT = "LIMIT"
    TIMEOUT = "TIMEOUT"
    PARAMS = "PARAMS"
    COUNT = "COUNT"

class FtDropIndexKeywords:
    """
    Keywords used in the [FT.DROPINDEX] command statment.
    """

    DELETE_DOCUMENT = "dd"
