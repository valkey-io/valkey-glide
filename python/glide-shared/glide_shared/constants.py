# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Any, Dict, List, Literal, Mapping, Optional, Set, TypeVar, Union

from glide_shared.protobuf.command_request_pb2 import CommandRequest
from glide_shared.protobuf.connection_request_pb2 import ConnectionRequest
from glide_shared.routes import ByAddressRoute, RandomNode, SlotIdRoute, SlotKeyRoute

OK: str = "OK"
DEFAULT_READ_BYTES_SIZE: int = pow(2, 16)
# Typing
T = TypeVar("T")
TOK = Literal["OK"]
TResult = Union[
    TOK,
    str,
    List[List[str]],
    int,
    None,
    Dict[str, T],
    Mapping[str, "TResult"],
    float,
    Set[T],
    List[T],
    bytes,
    Dict[bytes, "TResult"],
    Mapping[bytes, "TResult"],
]
TRequest = Union[CommandRequest, ConnectionRequest]
# When routing to a single node, response will be T
# Otherwise, response will be : {Address : response , ... } with type of Dict[str, T].
TClusterResponse = Union[T, Dict[bytes, T]]
TSingleNodeRoute = Union[RandomNode, SlotKeyRoute, SlotIdRoute, ByAddressRoute]
# When specifying legacy path (path doesn't start with `$`), response will be T
# Otherwise, (when specifying JSONPath), response will be List[Optional[T]].
#
# TJsonResponse is designed to handle scenarios where some paths may not contain valid values, especially with JSONPath
# targeting multiple paths.
# In such cases, the response may include None values, represented as `Optional[T]` in the list.
# This type provides flexibility for commands where a subset of the paths may return None.
#
# For more information, see: https://redis.io/docs/data-types/json/path/ .
TJsonResponse = Union[T, List[Optional[T]]]

# When specifying legacy path (path doesn't start with `$`), response will be T
# Otherwise, (when specifying JSONPath), response will be List[T].
# This type represents the response format for commands that apply to every path and every type in a JSON document.
# It covers both singular and multiple paths, ensuring that the command returns valid results for each matched path
# without None values.
#
# TJsonUniversalResponse is considered "universal" because it applies to every matched path and
# guarantees valid, non-null results across all paths, covering both singular and multiple paths.
# This type is used for commands that return results from all matched paths, ensuring that each
# path contains meaningful values without None entries (unless it's part of the commands response).
# It is typically used in scenarios where each target is expected to yield a valid response. For commands that are valid
# for all target types.
#
# For more information, see: https://redis.io/docs/data-types/json/path/ .
TJsonUniversalResponse = Union[T, List[T]]
TEncodable = Union[str, bytes]
TFunctionListResponse = List[
    Mapping[
        bytes,
        Union[bytes, List[Mapping[bytes, Union[bytes, Set[bytes]]]]],
    ]
]
# Response for function stats command on a single node.
# The response holds a map with 2 keys: Current running function / script and information about it, and the engines and
# the information about it.
TFunctionStatsSingleNodeResponse = Mapping[
    bytes,
    Union[
        None,
        Mapping[
            bytes,
            Union[Mapping[bytes, Mapping[bytes, int]], bytes, int, List[bytes]],
        ],
    ],
]
# Full response for function stats command across multiple nodes.
# It maps node address to the per-node response.
TFunctionStatsFullResponse = Mapping[
    bytes,
    TFunctionStatsSingleNodeResponse,
]


TXInfoStreamResponse = Mapping[
    bytes, Union[bytes, int, Mapping[bytes, Optional[List[List[bytes]]]]]
]
TXInfoStreamFullResponse = Mapping[
    bytes,
    Union[
        bytes,
        int,
        Mapping[bytes, List[List[bytes]]],
        List[Mapping[bytes, Union[bytes, int, List[List[Union[bytes, int]]]]]],
    ],
]

FtInfoResponse = Mapping[
    TEncodable,
    Union[
        TEncodable,
        int,
        List[TEncodable],
        List[
            Mapping[
                TEncodable,
                Union[TEncodable, Mapping[TEncodable, Union[TEncodable, int]]],
            ]
        ],
    ],
]

FtSearchResponse = List[
    Union[int, Mapping[TEncodable, Mapping[TEncodable, TEncodable]]]
]

FtAggregateResponse = List[Mapping[TEncodable, Any]]

FtProfileResponse = List[
    Union[FtSearchResponse, FtAggregateResponse, Mapping[str, int]]
]
