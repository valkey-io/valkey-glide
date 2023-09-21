from typing import Dict, List, Literal, TypeVar, Union

from pybushka.protobuf.connection_request_pb2 import ConnectionRequest
from pybushka.protobuf.redis_request_pb2 import RedisRequest

OK: str = "OK"
DEFAULT_READ_BYTES_SIZE: int = pow(2, 16)
# Typing
T = TypeVar("T")
TOK = Literal["OK"]
TResult = Union[TOK, str, List[str], List[List[str]], int, None]
TRequest = Union[RedisRequest, ConnectionRequest]
# When routing to a single node, response will be T
# Otherwise, response will be : {Address : response , ... } with type of Dict[str, T].
TClusterResponse = Union[T, Dict[str, T]]
