# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from .commands.batch import (
    Batch,
    ClusterBatch,
    ClusterTransaction,
    TBatch,
    Transaction,
)
from .commands.bitmap import (
    BitEncoding,
    BitFieldGet,
    BitFieldIncrBy,
    BitFieldOffset,
    BitFieldOverflow,
    BitFieldSet,
    BitFieldSubCommands,
    BitmapIndexType,
    BitOffset,
    BitOffsetMultiplier,
    BitOverflowControl,
    BitwiseOperation,
    OffsetOptions,
    SignedEncoding,
    UnsignedEncoding,
)
from .commands.command_args import Limit, ListDirection, ObjectType, OrderBy
from .commands.core_options import (
    ConditionalChange,
    ExpireOptions,
    ExpiryGetEx,
    ExpirySet,
    ExpiryType,
    ExpiryTypeGetEx,
    FlushMode,
    FunctionRestorePolicy,
    InfoSection,
    InsertPosition,
    OnlyIfEqual,
    PubSubMsg,
    UpdateOptions,
)
from .commands.server_modules import ft, glide_json, json_batch
from .commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateApply,
    FtAggregateClause,
    FtAggregateFilter,
    FtAggregateGroupBy,
    FtAggregateLimit,
    FtAggregateOptions,
    FtAggregateReducer,
    FtAggregateSortBy,
    FtAggregateSortProperty,
)
from .commands.server_modules.ft_options.ft_create_options import (
    DataType,
    DistanceMetricType,
    Field,
    FieldType,
    FtCreateOptions,
    NumericField,
    TagField,
    TextField,
    VectorAlgorithm,
    VectorField,
    VectorFieldAttributes,
    VectorFieldAttributesFlat,
    VectorFieldAttributesHnsw,
    VectorType,
)
from .commands.server_modules.ft_options.ft_profile_options import (
    FtProfileOptions,
    QueryType,
)
from .commands.server_modules.ft_options.ft_search_options import (
    FtSearchLimit,
    FtSearchOptions,
    ReturnField,
)
from .commands.server_modules.glide_json import (
    JsonArrIndexOptions,
    JsonArrPopOptions,
    JsonGetOptions,
)
from .commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeoSearchCount,
    GeospatialData,
    GeoUnit,
    InfBound,
    LexBoundary,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
    ScoreFilter,
)
from .commands.stream import (
    ExclusiveIdBound,
    IdBound,
    MaxId,
    MinId,
    StreamAddOptions,
    StreamClaimOptions,
    StreamGroupOptions,
    StreamPendingOptions,
    StreamRangeBound,
    StreamReadGroupOptions,
    StreamReadOptions,
    StreamTrimOptions,
    TrimByMaxLen,
    TrimByMinId,
)
from glide.shared.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    PeriodicChecksManualInterval,
    PeriodicChecksStatus,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
)
from .protobuf.command_request_pb2 import RequestType
from glide.shared.constants import (
    OK,
    TOK,
    FtAggregateResponse,
    FtInfoResponse,
    FtProfileResponse,
    FtSearchResponse,
    TClusterResponse,
    TEncodable,
    TFunctionListResponse,
    TFunctionStatsFullResponse,
    TFunctionStatsSingleNodeResponse,
    TJsonResponse,
    TJsonUniversalResponse,
    TResult,
    TSingleNodeRoute,
    TXInfoStreamFullResponse,
    TXInfoStreamResponse,
)
from glide.shared.exceptions import (
    ClosingError,
    ConfigurationError,
    ConnectionError,
    ExecAbortError,
    GlideError,
    RequestError,
    TimeoutError,
)
from glide.glide_async.python.glide.logger import Level as LogLevel
from glide.glide_async.python.glide.logger import Logger
from glide.shared.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
)

from glide.glide_async.python.glide.glide import ClusterScanCursor, Script


# try:
#     from glide.glide_async.python.glide import TGlideClient
#     from glide.glide_async.python.glide import GlideClient as AsyncGlideClient
#     from glide.glide_async.python.glide import GlideClusterClient as AsyncGlideClusterClient
#     GlideClient = AsyncGlideClient
#     GlideClusterClient = AsyncGlideClusterClient
# except ImportError:
#     try:
#         from glide.glide_sync.glide_sync import TGlideClient
#         from glide.glide_sync.glide_sync import GlideClient as SyncGlideClient
#         from glide.glide_sync.glide_sync import GlideClusterClient as SyncGlideClusterClient
#         GlideClient = SyncGlideClient
#         GlideClusterClient = SyncGlideClusterClient
#     except ImportError as e:
#         raise ImportError(
#             f"GlideClient not available — please install with either "
#             "`valkey-glide[async]` or `valkey-glide[sync]`.\n{e}"
#         )

# # Optional named exports if both are installed
# try:
#     from glide.glide_sync.glide_sync import TGlideClient
#     from glide.glide_sync.glide_sync import GlideClient as SyncGlideClient
#     from glide.glide_sync.glide_sync import GlideClusterClient as SyncGlideClusterClient
# except ImportError:
#     SyncGlideClient = None
#     SyncGlideClusterClient = None

# try:
#     from glide.glide_async.python.glide import TGlideClient
#     from glide.glide_async.python.glide import GlideClient as AsyncGlideClient
#     from glide.glide_async.python.glide import GlideClusterClient as AsyncGlideClusterClient
# except ImportError:
#     AsyncGlideClient = None
#     AsyncGlideClusterClient = None

__all__ = [
    # Client
    # "GlideClient",
    # "GlideClusterClient",
    "Batch",
    "ClusterBatch",
    "ClusterTransaction",
    "Transaction",
    # "TGlideClient",
    "TBatch",
    # Config
    "AdvancedGlideClientConfiguration",
    "AdvancedGlideClusterClientConfiguration",
    "GlideClientConfiguration",
    "GlideClusterClientConfiguration",
    "BackoffStrategy",
    "ReadFrom",
    "ServerCredentials",
    "NodeAddress",
    "ProtocolVersion",
    "PeriodicChecksManualInterval",
    "PeriodicChecksStatus",
    # Response
    "OK",
    "TClusterResponse",
    "TEncodable",
    "TFunctionListResponse",
    "TFunctionStatsFullResponse",
    "TFunctionStatsSingleNodeResponse",
    "TJsonResponse",
    "TJsonUniversalResponse",
    "TOK",
    "TResult",
    "TXInfoStreamFullResponse",
    "TXInfoStreamResponse",
    "FtAggregateResponse",
    "FtInfoResponse",
    "FtProfileResponse",
    "FtSearchResponse",
    # Commands
    "BitEncoding",
    "BitFieldGet",
    "BitFieldIncrBy",
    "BitFieldOffset",
    "BitFieldOverflow",
    "BitFieldSet",
    "BitFieldSubCommands",
    "BitmapIndexType",
    "BitOffset",
    "BitOffsetMultiplier",
    "BitOverflowControl",
    "BitwiseOperation",
    "OffsetOptions",
    "SignedEncoding",
    "UnsignedEncoding",
    "Script",
    "ScoreBoundary",
    "ConditionalChange",
    "OnlyIfEqual",
    "ExpireOptions",
    "ExpiryGetEx",
    "ExpirySet",
    "ExpiryType",
    "ExpiryTypeGetEx",
    "FlushMode",
    "FunctionRestorePolicy",
    "GeoSearchByBox",
    "GeoSearchByRadius",
    "GeoSearchCount",
    "GeoUnit",
    "GeospatialData",
    "AggregationType",
    "InfBound",
    "InfoSection",
    "InsertPosition",
    "ft",
    "LexBoundary",
    "Limit",
    "ListDirection",
    "RangeByIndex",
    "RangeByLex",
    "RangeByScore",
    "ScoreFilter",
    "ObjectType",
    "OrderBy",
    "ExclusiveIdBound",
    "IdBound",
    "MaxId",
    "MinId",
    "StreamAddOptions",
    "StreamClaimOptions",
    "StreamGroupOptions",
    "StreamPendingOptions",
    "StreamReadGroupOptions",
    "StreamRangeBound",
    "StreamReadOptions",
    "StreamTrimOptions",
    "TrimByMaxLen",
    "TrimByMinId",
    "UpdateOptions",
    "ClusterScanCursor",
    # PubSub
    "PubSubMsg",
    # Json
    "glide_json",
    "json_batch",
    "JsonGetOptions",
    "JsonArrIndexOptions",
    "JsonArrPopOptions",
    # Logger
    "Logger",
    "LogLevel",
    # Routes
    "Route",
    "SlotType",
    "AllNodes",
    "AllPrimaries",
    "ByAddressRoute",
    "RandomNode",
    "SlotKeyRoute",
    "SlotIdRoute",
    "TSingleNodeRoute",
    # Exceptions
    "ClosingError",
    "ConfigurationError",
    "ConnectionError",
    "ExecAbortError",
    "GlideError",
    "RequestError",
    "TimeoutError",
    # Ft
    "DataType",
    "DistanceMetricType",
    "Field",
    "FieldType",
    "FtCreateOptions",
    "NumericField",
    "TagField",
    "TextField",
    "VectorAlgorithm",
    "VectorField",
    "VectorFieldAttributes",
    "VectorFieldAttributesFlat",
    "VectorFieldAttributesHnsw",
    "VectorType",
    "FtSearchLimit",
    "ReturnField",
    "FtSearchOptions",
    "FtAggregateApply",
    "FtAggregateFilter",
    "FtAggregateClause",
    "FtAggregateLimit",
    "FtAggregateOptions",
    "FtAggregateGroupBy",
    "FtAggregateReducer",
    "FtAggregateSortBy",
    "FtAggregateSortProperty",
    "FtProfileOptions",
    "QueryType",
    # protobuf
    "RequestType",
]
