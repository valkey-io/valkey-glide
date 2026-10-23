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
    FT_HYBRID = "FT.HYBRID"
    FT_CURSOR = "FT.CURSOR"


class FtCreateKeywords:
    """
    Keywords used in the FT.CREATE command.
    """

    SCHEMA = "SCHEMA"
    AS = "AS"
    SORTABLE = "SORTABLE"
    UNF = "UNF"
    FILTER = "FILTER"
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
    SCORE = "SCORE"
    SCORE_FIELD = "SCORE_FIELD"
    LANGUAGE = "LANGUAGE"
    SKIPINITIALSCAN = "SKIPINITIALSCAN"
    MINSTEMSIZE = "MINSTEMSIZE"
    WITHOFFSETS = "WITHOFFSETS"
    NOOFFSETS = "NOOFFSETS"
    NOSTOPWORDS = "NOSTOPWORDS"
    STOPWORDS = "STOPWORDS"
    PUNCTUATION = "PUNCTUATION"
    NOSTEM = "NOSTEM"
    WITHSUFFIXTRIE = "WITHSUFFIXTRIE"
    NOSUFFIXTRIE = "NOSUFFIXTRIE"
    WEIGHT = "WEIGHT"
    NOHL = "NOHL"


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
    NOCONTENT = "NOCONTENT"
    DIALECT = "DIALECT"
    VERBATIM = "VERBATIM"
    INORDER = "INORDER"
    SLOP = "SLOP"
    SORTBY = "SORTBY"
    ASC = "ASC"
    DESC = "DESC"
    WITHSORTKEYS = "WITHSORTKEYS"
    WITHSCORES = "WITHSCORES"
    SCORER = "SCORER"
    ALLSHARDS = "ALLSHARDS"
    SOMESHARDS = "SOMESHARDS"
    CONSISTENT = "CONSISTENT"
    INCONSISTENT = "INCONSISTENT"
    INKEYS = "INKEYS"


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
    VERBATIM = "VERBATIM"
    INORDER = "INORDER"
    SLOP = "SLOP"
    DIALECT = "DIALECT"
    BY = "BY"
    ADDSCORES = "ADDSCORES"
    SCORER = "SCORER"
    WITHCURSOR = "WITHCURSOR"
    COUNT = "COUNT"
    MAXIDLE = "MAXIDLE"


class FtAggregateReducerKeywords:
    """
    Reducer function names used with the REDUCE clause of the FT.AGGREGATE command.
    """

    TOLIST = "TOLIST"
    FIRST_VALUE = "FIRST_VALUE"
    RANDOM_SAMPLE = "RANDOM_SAMPLE"


class FtHybridKeywords:
    """
    Keywords used in the FT.HYBRID command.
    """

    SEARCH = "SEARCH"
    VSIM = "VSIM"
    SCORER = "SCORER"
    YIELD_SCORE_AS = "YIELD_SCORE_AS"
    KNN = "KNN"
    RANGE = "RANGE"
    K = "K"
    EF_RUNTIME = "EF_RUNTIME"
    SHARD_K_RATIO = "SHARD_K_RATIO"
    FILTER = "FILTER"
    POLICY = "POLICY"
    BATCH_SIZE = "BATCH_SIZE"
    COMBINE = "COMBINE"
    RRF = "RRF"
    LINEAR = "LINEAR"
    FUNCTION = "FUNCTION"
    CONSTANT = "CONSTANT"
    WINDOW = "WINDOW"
    ALPHA = "ALPHA"
    BETA = "BETA"
    EXPR = "EXPR"
    LOCALONLY = "LOCALONLY"
    WITHCURSOR = "WITHCURSOR"
    COUNT = "COUNT"
    MAXIDLE = "MAXIDLE"
    PARAMS = "PARAMS"
    DIALECT = "DIALECT"
    TIMEOUT = "TIMEOUT"


class FtProfileKeywords:
    """
    Keywords used in the FT.PROFILE command.
    """

    QUERY = "QUERY"
    LIMITED = "LIMITED"


class FtCursorKeywords:
    """
    Keywords used in the FT.CURSOR command.
    """

    READ = "READ"
    DEL = "DEL"
    COUNT = "COUNT"
