from typing import List, Literal, Union

from pybushka.protobuf.connection_request_pb2 import ConnectionRequest
from pybushka.protobuf.redis_request_pb2 import RedisRequest

OK: str = "OK"
DEFAULT_READ_BYTES_SIZE: int = pow(2, 16)
# Typing

TOK = Literal["OK"]
TResult = Union[TOK, str, List[str], List[List[str]], int, None]
TRequest = Union[RedisRequest, ConnectionRequest]
