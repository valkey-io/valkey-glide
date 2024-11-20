# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


class CommandNames:
    """
    Command name constants for vector search.
    """

    FT_CREATE = "FT.CREATE"
    FT_DROPINDEX = "FT.DROPINDEX"
    FT_LIST = "FT._LIST"
    FT_SEARCH = "FT.SEARCH"
    FT_INFO = "FT.INFO"
    FT_ALIASADD = "FT.ALIASADD"
    FT_ALIASDEL = "FT.ALIASDEL"
    FT_ALIASUPDATE = "FT.ALIASUPDATE"
    FT_EXPLAIN = "FT.EXPLAIN"
    FT_EXPLAINCLI = "FT.EXPLAINCLI"
    FT_AGGREGATE = "FT.AGGREGATE"
    FT_PROFILE = "FT.PROFILE"
    FT_ALIASLIST = "FT._ALIASLIST"


class FtCreateKeywords:
    """
    Keywords used in the FT.CREATE command.
    """

    SCHEMA = "SCHEMA"
    AS = "AS"
    SORTABLE = "SORTABLE"
    UNF = "UNF"
    NO_INDEX = "NOINDEX"
    ON = "ON"
    PREFIX = "PREFIX"
    SEPARATOR = "SEPARATOR"
    CASESENSITIVE = "CASESENSITIVE"
    DIM = "DIM"
    DISTANCE_METRIC = "DISTANCE_METRIC"
    TYPE = "TYPE"
    INITIAL_CAP = "INITIAL_CAP"
    M = "M"
    EF_CONSTRUCTION = "EF_CONSTRUCTION"
    EF_RUNTIME = "EF_RUNTIME"


class FtSearchKeywords:
    """
    Keywords used in the FT.SEARCH command.
    """

    RETURN = "RETURN"
    TIMEOUT = "TIMEOUT"
    PARAMS = "PARAMS"
    LIMIT = "LIMIT"
    COUNT = "COUNT"
    AS = "AS"


class FtAggregateKeywords:
    """
    Keywords used in the FT.AGGREGATE command.
    """

    LIMIT = "LIMIT"
    FILTER = "FILTER"
    GROUPBY = "GROUPBY"
    REDUCE = "REDUCE"
    AS = "AS"
    SORTBY = "SORTBY"
    MAX = "MAX"
    APPLY = "APPLY"
    LOAD = "LOAD"
    TIMEOUT = "TIMEOUT"
    PARAMS = "PARAMS"


class FtProfileKeywords:
    """
    Keywords used in the FT.PROFILE command.
    """

    QUERY = "QUERY"
    LIMITED = "LIMITED"
