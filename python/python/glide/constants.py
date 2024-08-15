# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Dict, List, Literal, Mapping, Optional, Set, TypeVar, Union

from glide.protobuf.command_request_pb2 import CommandRequest
from glide.protobuf.connection_request_pb2 import ConnectionRequest
from glide.routes import ByAddressRoute, RandomNode, SlotIdRoute, SlotKeyRoute

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
# For more information, see: https://redis.io/docs/data-types/json/path/ .
TJsonResponse = Union[T, List[Optional[T]]]
TEncodable = Union[str, bytes]
TFunctionListResponse = List[
    Mapping[
        bytes,
        Union[bytes, List[Mapping[bytes, Union[bytes, Set[bytes]]]]],
    ]
]
# Response for function stats command on a single node.
# The response holds a map with 2 keys: Current running function / script and information about it, and the engines and the information about it.
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
