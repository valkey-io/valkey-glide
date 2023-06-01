from enum import Enum
from typing import Callable, List, Optional, Tuple

from pybushka.constants import TConnectionRequest
from pybushka.protobuf.connection_request_pb2 import (
    ConnectionRequest,
    ReadFromReplicaStrategy,
    TlsMode,
)


class AddressInfo:
    def __init__(self, host: str = "localhost", port: int = 6379):
        self.host = host
        self.port = port


class ReadFromReplica(Enum):
    ALWAYS_FROM_MASTER = ReadFromReplicaStrategy.AlwaysFromPrimary
    ROUND_ROBIN = ReadFromReplicaStrategy.RoundRobin
    LOWEST_LATENCY = ReadFromReplicaStrategy.LowestLatency
    AZ_AFFINTY = ReadFromReplicaStrategy.AZAffinity


class BackoffStrategy:
    def __init__(
        self, num_of_retries: int = 3, factor: int = 5, exponent_base: int = 10
    ):
        self.num_of_retries = num_of_retries
        self.factor = factor
        self.exponent_base = exponent_base


class AuthenticationOptions:
    def __init__(
        self,
        username: Optional[str] = None,
        password: Optional[str] = None,
        credentials_provider: Optional[Callable[..., Tuple[str, str]]] = None,
    ):
        if (username or password) and credentials_provider:
            raise Exception(
                """Please specify only one of the followings:
                            1. [username] and password
                            2. credentials provider
                            """
            )
        self.username = username
        self.password = password
        self.credentials_provider = credentials_provider


class ClientConfiguration:
    """
    Args:
        addresses (Optional[List[AddressInfo]]): DNS Addresses and ports of known nodes in the cluster.
                If the server has Cluster Mode Enabled the list can be partial, as the client will attempt to map out
                the cluster and find all nodes.
                If the server has Cluster Mode Disabled, only nodes whose addresses were provided will be used by the
                client.
                For example:
                [
                    {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
                    {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}
                ].
                If none are set, a default address localhost:6389 will be set.
        use_tls (bool): True if communication with the cluster should use Transport Level Security.
        credentials (AuthenticationOptions): Credentials for authentication process.
                If none are set, the client will not authenticate itself with the server.
        read_from_replica (ReadFromReplicaStrategy): If not set, `ALWAYS_FROM_MASTER` will be used.
            connection_timeout (Optional[int]): Number of milliseconds that the client should wait for connection before
            determining that the connection has been severed. If not set, a default value will be used.
        response_timeout (Optional[int]): Number of milliseconds that the client should wait for response before
            determining that the connection has been severed. If not set, a default value will be used.
        connection_backoff (Optional[int]): Strategy used to determine how and when to retry connecting, in case of
            connection failures. The time between attempts grows exponentially, to the formula:
            rand(0 .. factor * (exponentBase ^ N)), where N is the number of failed attempts. If not set, a default
            backoff strategy will be used.
    """

    def __init__(
        self,
        addresses: Optional[List[AddressInfo]] = None,
        use_tls: bool = False,
        credentials: Optional[AuthenticationOptions] = None,
        read_from_replica: ReadFromReplica = ReadFromReplica.ALWAYS_FROM_MASTER,
        connection_timeout: int = 200,
        response_timeout: int = 100,
        connection_backoff: Optional[BackoffStrategy] = None,
    ):
        self.addresses = addresses or [AddressInfo()]
        self.use_tls = use_tls
        self.credentials = credentials
        self.read_from_replica = read_from_replica
        self.connection_timeout = connection_timeout
        self.response_timeout = response_timeout
        self.connection_backoff = connection_backoff

    def convert_to_protobuf_request(
        self, cluster_mode: bool = False
    ) -> TConnectionRequest:
        request = ConnectionRequest()
        for address in self.addresses:
            address_info = request.addresses.add()
            address_info.host = address.host
            address_info.port = address.port
        request.tls_mode = TlsMode.SecureTls if self.use_tls else TlsMode.NoTls
        request.read_from_replica_strategy = self.read_from_replica.value
        request.response_timeout = self.response_timeout
        request.connection_timeout = self.connection_timeout
        if self.connection_backoff:
            request.connection_retry_strategy.number_of_retries = (
                self.connection_backoff.num_of_retries
            )
            request.connection_retry_strategy.factor = self.connection_backoff.factor
            request.connection_retry_strategy.exponent_base = (
                self.connection_backoff.exponent_base
            )
        request.cluster_mode_enabled = True if cluster_mode else False
        return request
