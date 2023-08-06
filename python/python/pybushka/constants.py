from typing import List, Type, Union

from pybushka.protobuf.connection_request_pb2 import ConnectionRequest
from pybushka.protobuf.redis_request_pb2 import RedisRequest, RequestType

OK: str = "OK"
DEFAULT_READ_BYTES_SIZE: int = pow(2, 16)
# Typing

TRedisRequest = Type["RedisRequest"]
TConnectionRequest = Type["ConnectionRequest"]
TResult = Union[OK, str, List[str], List[List[str]], None]
TRequest = Union[TRedisRequest, TConnectionRequest]
TRequestType = Type["RequestType"]
