from enum import Enum
from typing import List, Optional

from pybushka.protobuf.connection_request_pb2 import (
    ConnectionRequest,
    ReadFromReplicaStrategy,
    TlsMode,
)


class AddressInfo:
    def __init__(self, host: str = "localhost", port: int = 6379):
        """
        Represents the address and port of a node in the cluster.

        Args:
            host (str, optional): The server host. Defaults to "localhost".
            port (int, optional): The server port. Defaults to 6379.
        """
        self.host = host
        self.port = port


class ReadFromReplica(Enum):
    """
    Represents the client's read from replica strategy.
    """

    ALWAYS_FROM_MASTER = ReadFromReplicaStrategy.AlwaysFromPrimary
    """
    Always get from primary, in order to get the freshest data.
    """
    ROUND_ROBIN = ReadFromReplicaStrategy.RoundRobin
    """
    Spread the requests between all replicas evenly.
    """


class BackoffStrategy:
    def __init__(self, num_of_retries: int, factor: int, exponent_base: int):
        """
        Represents the strategy used to determine how and when to reconnect, in case of connection failures.
        The time between attempts grows exponentially, to the formula rand(0 .. factor * (exponentBase ^ N)), where N
        is the number of failed attempts.
        The client will attempt to reconnect indefinitely. Once the maximum value is reached, that will remain the time
        between retry attempts until a reconnect attempt is succesful.

        Args:
            num_of_retries (int): Number of retry attempts that the client should perform when disconnected from the server,
                where the time between retries increases. Once the retries have reached the maximum value, the time between
                retries will remain constant until a reconnect attempt is succesful.
            factor (int): The multiplier that will be applied to the waiting time between each retry.
            exponent_base (int): The exponent base configured for the strategy.
        """
        self.num_of_retries = num_of_retries
        self.factor = factor
        self.exponent_base = exponent_base


class AuthenticationOptions:
    def __init__(
        self,
        password: str,
        username: Optional[str] = None,
    ):
        """
        Represents the authentication options for connecting to a Redis server.

        Args:
            password (str): The password that will be passed to the cluster's Access Control Layer.
            username (Optional[str]): The username that will be passed to the cluster's Access Control Layer.
                If not supplied, "default" will be used.
        """
        self.password = password
        self.username = username


class ClientConfiguration:
    def __init__(
        self,
        addresses: Optional[List[AddressInfo]] = None,
        use_tls: bool = False,
        credentials: Optional[AuthenticationOptions] = None,
        read_from_replica: ReadFromReplica = ReadFromReplica.ALWAYS_FROM_MASTER,
        client_creation_timeout: Optional[int] = None,
        response_timeout: Optional[int] = None,
    ):
        """
        Represents the configuration settings for a Redis client.

        Args:
            addresses (Optional[List[AddressInfo]]): DNS Addresses and ports of known nodes in the cluster.
                    If the server is in cluster mode the list can be partial, as the client will attempt to map out
                    the cluster and find all nodes.
                    If the server is in standalone mode, only nodes whose addresses were provided will be used by the
                    client.
                    For example:
                    [
                        {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
                        {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}
                    ].
                    If none are set, a default address localhost:6379 will be used.
            use_tls (bool): True if communication with the cluster should use Transport Level Security.
            credentials (AuthenticationOptions): Credentials for authentication process.
                    If none are set, the client will not authenticate itself with the server.
            read_from_replica (ReadFromReplicaStrategy): If not set, `ALWAYS_FROM_MASTER` will be used.
            client_creation_timeout (Optional[int]): Number of milliseconds that the client should wait for the
                initial connection attempts before determining that the connection has been severed. If not set, a
                default value will be used.
            response_timeout (Optional[int]): Number of milliseconds that the client should wait for response before
                determining that the connection has been severed. If not set, a default value will be used.
        """
        self.addresses = addresses or [AddressInfo()]
        self.use_tls = use_tls
        self.credentials = credentials
        self.read_from_replica = read_from_replica
        self.client_creation_timeout = client_creation_timeout
        self.response_timeout = response_timeout

    def _create_a_protobuf_conn_request(
        self, cluster_mode: bool = False
    ) -> ConnectionRequest:
        """
        Generates a Protobuf ConnectionRequest using the values from this ClientConfiguration.

        Args:
            cluster_mode (bool, optional): The cluster mode of the client. Defaults to False.

        Returns:
            ConnectionRequest: Protobuf ConnectionRequest.
        """
        request = ConnectionRequest()
        for address in self.addresses:
            address_info = request.addresses.add()
            address_info.host = address.host
            address_info.port = address.port
        request.tls_mode = TlsMode.SecureTls if self.use_tls else TlsMode.NoTls
        request.read_from_replica_strategy = self.read_from_replica.value
        if self.response_timeout:
            request.response_timeout = self.response_timeout
        if self.client_creation_timeout:
            request.client_creation_timeout = self.client_creation_timeout
        request.cluster_mode_enabled = True if cluster_mode else False
        if self.credentials:
            if self.credentials.username:
                request.authentication_info.username = self.credentials.username
            request.authentication_info.password = self.credentials.password

        return request


class StandaloneClientConfiguration(ClientConfiguration):
    """
    Represents the configuration settings for a Redis client.

    Args:
        addresses (Optional[List[AddressInfo]]): DNS Addresses and ports of known nodes in the cluster.
                If the server is in cluster mode the list can be partial, as the client will attempt to map out
                the cluster and find all nodes.
                If the server is in standalone mode, only nodes whose addresses were provided will be used by the
                client.
                For example:
                [
                    {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
                    {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}
                ].
                If none are set, a default address localhost:6379 will be used.
        use_tls (bool): True if communication with the cluster should use Transport Level Security.
        credentials (AuthenticationOptions): Credentials for authentication process.
                If none are set, the client will not authenticate itself with the server.
        read_from_replica (ReadFromReplicaStrategy): If not set, `ALWAYS_FROM_MASTER` will be used.
        client_creation_timeout (Optional[int]): Number of milliseconds that the client should wait for the
            initial connection attempts before determining that the connection has been severed. If not set, a
            default value will be used.
        response_timeout (Optional[int]): Number of milliseconds that the client should wait for response before
            determining that the connection has been severed. If not set, a default value will be used.
        connection_backoff (Optional[BackoffStrategy]): Strategy used to determine how and when to reconnect, in case of
            connection failures.
            If not set, a default backoff strategy will be used.
            At the moment this setting isn't supported in cluster mode, only in standalone mode - a constant value is used
            in cluster mode.
        database_id (Optional[Int]): index of the logical database to connect to.
    """

    def __init__(
        self,
        addresses: Optional[List[AddressInfo]] = None,
        use_tls: bool = False,
        credentials: Optional[AuthenticationOptions] = None,
        read_from_replica: ReadFromReplica = ReadFromReplica.ALWAYS_FROM_MASTER,
        client_creation_timeout: Optional[int] = None,
        response_timeout: Optional[int] = None,
        connection_backoff: Optional[BackoffStrategy] = None,
        database_id: Optional[int] = None,
    ):
        super().__init__(
            addresses=addresses,
            use_tls=use_tls,
            credentials=credentials,
            read_from_replica=read_from_replica,
            client_creation_timeout=client_creation_timeout,
            response_timeout=response_timeout,
        )
        self.connection_backoff = connection_backoff
        self.database_id = database_id

    def _create_a_protobuf_conn_request(
        self, cluster_mode: bool = False
    ) -> ConnectionRequest:
        assert cluster_mode is False
        request = super()._create_a_protobuf_conn_request(False)
        if self.connection_backoff:
            request.connection_retry_strategy.number_of_retries = (
                self.connection_backoff.num_of_retries
            )
            request.connection_retry_strategy.factor = self.connection_backoff.factor
            request.connection_retry_strategy.exponent_base = (
                self.connection_backoff.exponent_base
            )
        if self.database_id:
            request.database_id = self.database_id

        return request
