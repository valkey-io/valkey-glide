# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum, IntEnum
from typing import Any, Callable, Dict, List, Optional, Set, Tuple, Union

from glide.async_commands.core import CoreCommands
from glide.exceptions import ConfigurationError
from glide.protobuf.connection_request_pb2 import ConnectionRequest
from glide.protobuf.connection_request_pb2 import ProtocolVersion as SentProtocolVersion
from glide.protobuf.connection_request_pb2 import ReadFrom as ProtobufReadFrom
from glide.protobuf.connection_request_pb2 import TlsMode


class NodeAddress:
    def __init__(self, host: str = "localhost", port: int = 6379):
        """
        Represents the address and port of a node in the cluster.

        Args:
            host (str, optional): The server host. Defaults to "localhost".
            port (int, optional): The server port. Defaults to 6379.
        """
        self.host = host
        self.port = port


class ReadFrom(Enum):
    """
    Represents the client's read from strategy.
    """

    PRIMARY = ProtobufReadFrom.Primary
    """
    Always get from primary, in order to get the freshest data.
    """
    PREFER_REPLICA = ProtobufReadFrom.PreferReplica
    """
    Spread the requests between all replicas in a round robin manner.
    If no replica is available, route the requests to the primary.
    """


class ProtocolVersion(Enum):
    """
    Represents the communication protocol with the server.
    """

    RESP2 = SentProtocolVersion.RESP2
    """
    Communicate using RESP2.
    """
    RESP3 = SentProtocolVersion.RESP3
    """
    Communicate using RESP3.
    """


class BackoffStrategy:
    def __init__(self, num_of_retries: int, factor: int, exponent_base: int):
        """
        Represents the strategy used to determine how and when to reconnect, in case of connection failures.
        The time between attempts grows exponentially, to the formula rand(0 .. factor * (exponentBase ^ N)), where N
        is the number of failed attempts.
        Once the maximum value is reached, that will remain the time between retry attempts until a reconnect attempt is succesful.
        The client will attempt to reconnect indefinitely.

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


class ServerCredentials:
    def __init__(
        self,
        password: str,
        username: Optional[str] = None,
    ):
        """
        Represents the credentials for connecting to a server.

        Args:
            password (str): The password that will be used for authenticating connections to the servers.
            username (Optional[str]): The username that will be used for authenticating connections to the servers.
                If not supplied, "default" will be used.
        """
        self.password = password
        self.username = username


class PeriodicChecksManualInterval:
    def __init__(self, duration_in_sec: int) -> None:
        """
        Represents a manually configured interval for periodic checks.

        Args:
            duration_in_sec (int): The duration in seconds for the interval between periodic checks.
        """
        self.duration_in_sec = duration_in_sec


class PeriodicChecksStatus(Enum):
    """
    Represents the cluster's periodic checks status.
    To configure specific interval, see PeriodicChecksManualInterval.
    """

    ENABLED_DEFAULT_CONFIGS = 0
    """
    Enables the periodic checks with the default configurations.
    """
    DISABLED = 1
    """
    Disables the periodic checks.
    """


class BaseClientConfiguration:
    def __init__(
        self,
        addresses: List[NodeAddress],
        use_tls: bool = False,
        credentials: Optional[ServerCredentials] = None,
        read_from: ReadFrom = ReadFrom.PRIMARY,
        request_timeout: Optional[int] = None,
        client_name: Optional[str] = None,
        protocol: ProtocolVersion = ProtocolVersion.RESP3,
    ):
        """
        Represents the configuration settings for a Glide client.

        Args:
            addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
                    If the server is in cluster mode the list can be partial, as the client will attempt to map out
                    the cluster and find all nodes.
                    If the server is in standalone mode, only nodes whose addresses were provided will be used by the
                    client.
                    For example:
                    [
                        {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
                        {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}
                    ].
            use_tls (bool): True if communication with the cluster should use Transport Level Security.
                Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail
            credentials (ServerCredentials): Credentials for authentication process.
                    If none are set, the client will not authenticate itself with the server.
            read_from (ReadFrom): If not set, `PRIMARY` will be used.
            request_timeout (Optional[int]): The duration in milliseconds that the client should wait for a request to complete.
                This duration encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries.
                If the specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be used.
            client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during connection establishment.
        """
        self.addresses = addresses
        self.use_tls = use_tls
        self.credentials = credentials
        self.read_from = read_from
        self.request_timeout = request_timeout
        self.client_name = client_name
        self.protocol = protocol

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
        request.read_from = self.read_from.value
        if self.request_timeout:
            request.request_timeout = self.request_timeout
        request.cluster_mode_enabled = True if cluster_mode else False
        if self.credentials:
            if self.credentials.username:
                request.authentication_info.username = self.credentials.username
            request.authentication_info.password = self.credentials.password
        if self.client_name:
            request.client_name = self.client_name
        request.protocol = self.protocol.value

        return request

    def _is_pubsub_configured(self) -> bool:
        return False

    def _get_pubsub_callback_and_context(
        self,
    ) -> Tuple[Optional[Callable[[CoreCommands.PubSubMsg, Any], None]], Any]:
        return None, None


class GlideClientConfiguration(BaseClientConfiguration):
    """
    Represents the configuration settings for a Standalone Glide client.

    Args:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
                Only nodes whose addresses were provided will be used by the client.
                For example:
                [
                    {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
                    {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}
                ].
        use_tls (bool): True if communication with the cluster should use Transport Level Security.
        credentials (ServerCredentials): Credentials for authentication process.
                If none are set, the client will not authenticate itself with the server.
        read_from (ReadFrom): If not set, `PRIMARY` will be used.
        request_timeout (Optional[int]):  The duration in milliseconds that the client should wait for a request to complete.
                This duration encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries.
                If the specified timeout is exceeded for a pending request, it will result in a timeout error.
                If not set, a default value will be used.
        reconnect_strategy (Optional[BackoffStrategy]): Strategy used to determine how and when to reconnect, in case of
            connection failures.
            If not set, a default backoff strategy will be used.
        database_id (Optional[int]): index of the logical database to connect to.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during connection establishment.
        protocol (ProtocolVersion): The version of the RESP protocol to communicate with the server.
        pubsub_subscriptions (Optional[GlideClientConfiguration.PubSubSubscriptions]): Pubsub subscriptions to be used for the client.
                Will be applied via SUBSCRIBE/PSUBSCRIBE commands during connection establishment.
    """

    class PubSubChannelModes(IntEnum):
        """
        Describes pubsub subsciption modes.
        See https://valkey.io/docs/topics/pubsub/ for more details
        """

        Exact = 0
        """ Use exact channel names """
        Pattern = 1
        """ Use channel name patterns """

    @dataclass
    class PubSubSubscriptions:
        """Describes pubsub configuration for standalone mode client.

        Attributes:
            channels_and_patterns (Dict[GlideClientConfiguration.PubSubChannelModes, Set[str]]):
                Channels and patterns by modes.
            callback (Optional[Callable[[CoreCommands.PubSubMsg, Any], None]]):
                Optional callback to accept the incomming messages.
            context (Any):
                Arbitrary context to pass to the callback.
        """

        channels_and_patterns: Dict[
            GlideClientConfiguration.PubSubChannelModes, Set[str]
        ]
        callback: Optional[Callable[[CoreCommands.PubSubMsg, Any], None]]
        context: Any

    def __init__(
        self,
        addresses: List[NodeAddress],
        use_tls: bool = False,
        credentials: Optional[ServerCredentials] = None,
        read_from: ReadFrom = ReadFrom.PRIMARY,
        request_timeout: Optional[int] = None,
        reconnect_strategy: Optional[BackoffStrategy] = None,
        database_id: Optional[int] = None,
        client_name: Optional[str] = None,
        protocol: ProtocolVersion = ProtocolVersion.RESP3,
        pubsub_subscriptions: Optional[PubSubSubscriptions] = None,
    ):
        super().__init__(
            addresses=addresses,
            use_tls=use_tls,
            credentials=credentials,
            read_from=read_from,
            request_timeout=request_timeout,
            client_name=client_name,
            protocol=protocol,
        )
        self.reconnect_strategy = reconnect_strategy
        self.database_id = database_id
        self.pubsub_subscriptions = pubsub_subscriptions

    def _create_a_protobuf_conn_request(
        self, cluster_mode: bool = False
    ) -> ConnectionRequest:
        assert cluster_mode is False
        request = super()._create_a_protobuf_conn_request(cluster_mode)
        if self.reconnect_strategy:
            request.connection_retry_strategy.number_of_retries = (
                self.reconnect_strategy.num_of_retries
            )
            request.connection_retry_strategy.factor = self.reconnect_strategy.factor
            request.connection_retry_strategy.exponent_base = (
                self.reconnect_strategy.exponent_base
            )
        if self.database_id:
            request.database_id = self.database_id

        if self.pubsub_subscriptions:
            if self.protocol == ProtocolVersion.RESP2:
                raise ConfigurationError(
                    "PubSub subscriptions require RESP3 protocol, but RESP2 was configured."
                )
            if (
                self.pubsub_subscriptions.context is not None
                and not self.pubsub_subscriptions.callback
            ):
                raise ConfigurationError(
                    "PubSub subscriptions with a context require a callback function to be configured."
                )
            for (
                channel_type,
                channels_patterns,
            ) in self.pubsub_subscriptions.channels_and_patterns.items():
                entry = request.pubsub_subscriptions.channels_or_patterns_by_type[
                    int(channel_type)
                ]
                for channel_pattern in channels_patterns:
                    entry.channels_or_patterns.append(str.encode(channel_pattern))

        return request

    def _is_pubsub_configured(self) -> bool:
        return self.pubsub_subscriptions is not None

    def _get_pubsub_callback_and_context(
        self,
    ) -> Tuple[Optional[Callable[[CoreCommands.PubSubMsg, Any], None]], Any]:
        if self.pubsub_subscriptions:
            return self.pubsub_subscriptions.callback, self.pubsub_subscriptions.context
        return None, None


class GlideClusterClientConfiguration(BaseClientConfiguration):
    """
    Represents the configuration settings for a Cluster Glide client.

    Args:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
                The list can be partial, as the client will attempt to map out the cluster and find all nodes.
                For example:
                [
                    {address:configuration-endpoint.use1.cache.amazonaws.com, port:6379}
                ].
        use_tls (bool): True if communication with the cluster should use Transport Level Security.
        credentials (ServerCredentials): Credentials for authentication process.
                If none are set, the client will not authenticate itself with the server.
        read_from (ReadFrom): If not set, `PRIMARY` will be used.
        request_timeout (Optional[int]):  The duration in milliseconds that the client should wait for a request to complete.
            This duration encompasses sending the request, awaiting for a response from the server, and any required reconnections or retries.
            If the specified timeout is exceeded for a pending request, it will result in a timeout error. If not set, a default value will be used.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during connection establishment.
        protocol (ProtocolVersion): The version of the RESP protocol to communicate with the server.
        periodic_checks (Union[PeriodicChecksStatus, PeriodicChecksManualInterval]): Configure the periodic topology checks.
            These checks evaluate changes in the cluster's topology, triggering a slot refresh when detected.
            Periodic checks ensure a quick and efficient process by querying a limited number of nodes.
            Defaults to PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS.
        pubsub_subscriptions (Optional[GlideClusterClientConfiguration.PubSubSubscriptions]): Pubsub subscriptions to be used for the client.
            Will be applied via SUBSCRIBE/PSUBSCRIBE/SSUBSCRIBE commands during connection establishment.

    Notes:
        Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff
        with fixed values is used.
    """

    class PubSubChannelModes(IntEnum):
        """
        Describes pubsub subsciption modes.
        See https://valkey.io/docs/topics/pubsub/ for more details
        """

        Exact = 0
        """ Use exact channel names """
        Pattern = 1
        """ Use channel name patterns """
        Sharded = 2
        """ Use sharded pubsub. Available since Valkey version 7.0. """

    @dataclass
    class PubSubSubscriptions:
        """Describes pubsub configuration for cluster mode client.

        Attributes:
            channels_and_patterns (Dict[GlideClusterClientConfiguration.PubSubChannelModes, Set[str]]):
                Channels and patterns by modes.
            callback (Optional[Callable[[CoreCommands.PubSubMsg, Any], None]]):
                Optional callback to accept the incoming messages.
            context (Any):
                Arbitrary context to pass to the callback.
        """

        channels_and_patterns: Dict[
            GlideClusterClientConfiguration.PubSubChannelModes, Set[str]
        ]
        callback: Optional[Callable[[CoreCommands.PubSubMsg, Any], None]]
        context: Any

    def __init__(
        self,
        addresses: List[NodeAddress],
        use_tls: bool = False,
        credentials: Optional[ServerCredentials] = None,
        read_from: ReadFrom = ReadFrom.PRIMARY,
        request_timeout: Optional[int] = None,
        client_name: Optional[str] = None,
        protocol: ProtocolVersion = ProtocolVersion.RESP3,
        periodic_checks: Union[
            PeriodicChecksStatus, PeriodicChecksManualInterval
        ] = PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS,
        pubsub_subscriptions: Optional[PubSubSubscriptions] = None,
    ):
        super().__init__(
            addresses=addresses,
            use_tls=use_tls,
            credentials=credentials,
            read_from=read_from,
            request_timeout=request_timeout,
            client_name=client_name,
            protocol=protocol,
        )
        self.periodic_checks = periodic_checks
        self.pubsub_subscriptions = pubsub_subscriptions

    def _create_a_protobuf_conn_request(
        self, cluster_mode: bool = False
    ) -> ConnectionRequest:
        assert cluster_mode is True
        request = super()._create_a_protobuf_conn_request(cluster_mode)
        if type(self.periodic_checks) is PeriodicChecksManualInterval:
            request.periodic_checks_manual_interval.duration_in_sec = (
                self.periodic_checks.duration_in_sec
            )
        elif self.periodic_checks == PeriodicChecksStatus.DISABLED:
            request.periodic_checks_disabled.SetInParent()

        if self.pubsub_subscriptions:
            if self.protocol == ProtocolVersion.RESP2:
                raise ConfigurationError(
                    "PubSub subscriptions require RESP3 protocol, but RESP2 was configured."
                )
            if (
                self.pubsub_subscriptions.context is not None
                and not self.pubsub_subscriptions.callback
            ):
                raise ConfigurationError(
                    "PubSub subscriptions with a context require a callback function to be configured."
                )
            for (
                channel_type,
                channels_patterns,
            ) in self.pubsub_subscriptions.channels_and_patterns.items():
                entry = request.pubsub_subscriptions.channels_or_patterns_by_type[
                    int(channel_type)
                ]
                for channel_pattern in channels_patterns:
                    entry.channels_or_patterns.append(str.encode(channel_pattern))

        return request

    def _is_pubsub_configured(self) -> bool:
        return self.pubsub_subscriptions is not None

    def _get_pubsub_callback_and_context(
        self,
    ) -> Tuple[Optional[Callable[[CoreCommands.PubSubMsg, Any], None]], Any]:
        if self.pubsub_subscriptions:
            return self.pubsub_subscriptions.callback, self.pubsub_subscriptions.context
        return None, None
