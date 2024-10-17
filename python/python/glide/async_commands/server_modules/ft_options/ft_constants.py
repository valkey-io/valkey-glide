# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


class CommandNames:
    """
    Command name constants for vector search.
    """

    FT_CREATE = "FT.CREATE"
    FT_DROPINDEX = "FT.DROPINDEX"
    FT_ALIASADD = "FT.ALIASADD"
    FT_ALIASDEL = "FT.ALIASDEL"
    FT_ALIASUPDATE = "FT.ALIASUPDATE"


class FtCreateKeywords:
    """
    Keywords used in the FT.CREATE command statment.
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
