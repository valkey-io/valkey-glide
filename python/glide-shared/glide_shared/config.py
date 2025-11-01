# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum, IntEnum
from typing import Any, Callable, Dict, List, Optional, Set, Tuple, Union

from glide_shared.commands.core_options import PubSubMsg
from glide_shared.exceptions import ConfigurationError
from glide_shared.protobuf.connection_request_pb2 import (
    ConnectionRequest,
)
from glide_shared.protobuf.connection_request_pb2 import (
    ProtocolVersion as SentProtocolVersion,
)
from glide_shared.protobuf.connection_request_pb2 import ReadFrom as ProtobufReadFrom
from glide_shared.protobuf.connection_request_pb2 import (
    TlsMode,
)


class NodeAddress:
    """
    Represents the address and port of a node in the cluster.

    Attributes:
        host (str, optional): The server host. Defaults to "localhost".
        port (int, optional): The server port. Defaults to 6379.
    """

    def __init__(self, host: str = "localhost", port: int = 6379):
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
    AZ_AFFINITY = ProtobufReadFrom.AZAffinity
    """
    Spread the read requests between replicas in the same client's AZ (Aviliablity zone) in a round robin manner,
    falling back to other replicas or the primary if needed
    """
    AZ_AFFINITY_REPLICAS_AND_PRIMARY = ProtobufReadFrom.AZAffinityReplicasAndPrimary
    """
    Spread the read requests among nodes within the client's Availability Zone (AZ) in a round robin manner,
    prioritizing local replicas, then the local primary, and falling back to any replica or the primary if needed.
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
    """
    Represents the strategy used to determine how and when to reconnect, in case of connection failures.
    The time between attempts grows exponentially, to the formula rand(0 .. factor * (exponentBase ^ N)), where N
    is the number of failed attempts, and rand(...) applies a jitter of up to jitter_percent% to introduce randomness and reduce retry storms.
    Once the maximum value is reached, that will remain the time between retry attempts until a reconnect attempt is
    successful.
    The client will attempt to reconnect indefinitely.

    Attributes:
        num_of_retries (int): Number of retry attempts that the client should perform when disconnected from the server,
            where the time between retries increases. Once the retries have reached the maximum value, the time between
            retries will remain constant until a reconnect attempt is succesful.
        factor (int): The multiplier that will be applied to the waiting time between each retry.
            This value is specified in milliseconds.
        exponent_base (int): The exponent base configured for the strategy.
        jitter_percent (Optional[int]): The Jitter percent on the calculated duration. If not set, a default value will be used.
    """

    def __init__(
        self,
        num_of_retries: int,
        factor: int,
        exponent_base: int,
        jitter_percent: Optional[int] = None,
    ):
        self.num_of_retries = num_of_retries
        self.factor = factor
        self.exponent_base = exponent_base
        self.jitter_percent = jitter_percent


class ServiceType(Enum):
    """
    Represents the types of AWS services that can be used for IAM authentication.
    """

    ELASTICACHE = 0
    """Amazon ElastiCache service."""
    MEMORYDB = 1
    """Amazon MemoryDB service."""


class IamAuthConfig:
    """
    Configuration settings for IAM authentication.

    Attributes:
        cluster_name (str): The name of the ElastiCache/MemoryDB cluster.
        service (ServiceType): The type of service being used (ElastiCache or MemoryDB).
        region (str): The AWS region where the ElastiCache/MemoryDB cluster is located.
        refresh_interval_seconds (Optional[int]): Optional refresh interval in seconds for renewing IAM authentication tokens.
            If not provided, the core will use a default value of 300 seconds (5 min).
    """

    def __init__(
        self,
        cluster_name: str,
        service: ServiceType,
        region: str,
        refresh_interval_seconds: Optional[int] = None,
    ):
        self.cluster_name = cluster_name
        self.service = service
        self.region = region
        self.refresh_interval_seconds = refresh_interval_seconds


class ServerCredentials:
    """
    Represents the credentials for connecting to a server.

    Exactly one of the following authentication modes must be provided:
        - Password-based authentication: Use password (and optionally username)
        - IAM authentication: Use username (required) and iam_config

    These modes are mutually exclusive - you cannot use both simultaneously.

    Attributes:
        password (Optional[str]): The password that will be used for authenticating connections to the servers.
            Mutually exclusive with iam_config. Either password or iam_config must be provided.
        username (Optional[str]): The username that will be used for authenticating connections to the servers.
            If not supplied for password-based authentication, "default" will be used.
            Required for IAM authentication.
        iam_config (Optional[IamAuthConfig]): IAM authentication configuration. Mutually exclusive with password.
            Either password or iam_config must be provided.
            The client will automatically generate and refresh the authentication token based on the provided configuration.
    """

    def __init__(
        self,
        password: Optional[str] = None,
        username: Optional[str] = None,
        iam_config: Optional[IamAuthConfig] = None,
    ):
        # Validate mutual exclusivity
        if password is not None and iam_config is not None:
            raise ConfigurationError(
                "password and iam_config are mutually exclusive. Use either password-based or IAM authentication, not both."
            )

        # Validate IAM requires username
        if iam_config is not None and not username:
            raise ConfigurationError("username is required for IAM authentication.")

        # At least one authentication method must be provided
        if password is None and iam_config is None:
            raise ConfigurationError(
                "Either password or iam_config must be provided for authentication."
            )

        self.password = password
        self.username = username
        self.iam_config = iam_config

    def is_iam_auth(self) -> bool:
        """Returns True if this credential is configured for IAM authentication."""
        return self.iam_config is not None


class PeriodicChecksManualInterval:
    """
    Represents a manually configured interval for periodic checks.

    Attributes:
        duration_in_sec (int): The duration in seconds for the interval between periodic checks.
    """

    def __init__(self, duration_in_sec: int) -> None:
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


class TlsAdvancedConfiguration:
    """
    Represents advanced TLS configuration settings.

    Attributes:
        use_insecure_tls (Optional[bool]): Whether to bypass TLS certificate verification.

            - When set to True, the client skips certificate validation.
              This is useful when connecting to servers or clusters using self-signed certificates,
              or when DNS entries (e.g., CNAMEs) don't match certificate hostnames.

            - This setting is typically used in development or testing environments. **It is
              strongly discouraged in production**, as it introduces security risks such as man-in-the-middle attacks.

            - Only valid if TLS is already enabled in the base client configuration.
              Enabling it without TLS will result in a `ConfigurationError`.

            - Default: False (verification is enforced).

        root_pem_cacerts (Optional[bytes]): Custom root certificate data for TLS connections in PEM format.

            - When provided, these certificates will be used instead of the system's default trust store.
              This is useful for connecting to servers with self-signed certificates or corporate
              certificate authorities.

            - If set to an empty bytes object (non-None but length 0), a `ConfigurationError` will be raised.

            - If None (default), the system's default certificate trust store will be used (platform verifier).

            - The certificate data should be in PEM format as a bytes object.

            - Multiple certificates can be provided by concatenating them in PEM format.

            Example usage::

                # Load from file
                with open('/path/to/ca-cert.pem', 'rb') as f:
                    cert_data = f.read()
                tls_config = TlsAdvancedConfiguration(root_pem_cacerts=cert_data)

                # Or provide directly
                cert_data = b"-----BEGIN CERTIFICATE-----\\n...\\n-----END CERTIFICATE-----"
                tls_config = TlsAdvancedConfiguration(root_pem_cacerts=cert_data)
    """

    def __init__(
        self,
        use_insecure_tls: Optional[bool] = None,
        root_pem_cacerts: Optional[bytes] = None,
    ):
        self.use_insecure_tls = use_insecure_tls
        self.root_pem_cacerts = root_pem_cacerts


class AdvancedBaseClientConfiguration:
    """
    Represents the advanced configuration settings for a base Glide client.

    Attributes:
        connection_timeout (Optional[int]): The duration in milliseconds to wait for a TCP/TLS connection to complete.
            This applies both during initial client creation and any reconnection that may occur during request processing.
            **Note**: A high connection timeout may lead to prolonged blocking of the entire command pipeline.
            If not explicitly set, a default value of 2000 milliseconds will be used.
        tls_config (Optional[TlsAdvancedConfiguration]): The advanced TLS configuration settings.
            This allows for more granular control of TLS behavior, such as enabling an insecure mode
            that bypasses certificate validation.
    """

    def __init__(
        self,
        connection_timeout: Optional[int] = None,
        tls_config: Optional[TlsAdvancedConfiguration] = None,
    ):
        self.connection_timeout = connection_timeout
        self.tls_config = tls_config

    def _create_a_protobuf_conn_request(
        self, request: ConnectionRequest
    ) -> ConnectionRequest:
        if self.connection_timeout:
            request.connection_timeout = self.connection_timeout

        if self.tls_config:
            if self.tls_config.use_insecure_tls:
                # Validate that TLS is enabled before allowing insecure mode
                if request.tls_mode == TlsMode.NoTls:
                    raise ConfigurationError(
                        "use_insecure_tls cannot be enabled when use_tls is disabled."
                    )

                # Override the default SecureTls mode to InsecureTls when user explicitly requests it
                request.tls_mode = TlsMode.InsecureTls

            # Handle root certificates
            if self.tls_config.root_pem_cacerts is not None:
                if len(self.tls_config.root_pem_cacerts) == 0:
                    raise ConfigurationError(
                        "root_pem_cacerts cannot be an empty bytes object; use None to use platform verifier"
                    )
                request.root_certs.append(self.tls_config.root_pem_cacerts)

        return request


class BaseClientConfiguration:
    """
    Represents the configuration settings for a Glide client.

    Attributes:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
            If the server is in cluster mode the list can be partial, as the client will attempt to map out
            the cluster and find all nodes.
            If the server is in standalone mode, only nodes whose addresses were provided will be used by the
            client.
            For example::

                [
                    NodeAddress("sample-address-0001.use1.cache.amazonaws.com", 6379),
                    NodeAddress("sample-address-0002.use1.cache.amazonaws.com", 6379)
                ]

        use_tls (bool): True if communication with the cluster should use Transport Level Security.
            Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail.
            For advanced tls configuration, please use `AdvancedBaseClientConfiguration`.
        credentials (ServerCredentials): Credentials for authentication process.
            If none are set, the client will not authenticate itself with the server.
        read_from (ReadFrom): If not set, `PRIMARY` will be used.
        request_timeout (Optional[int]): The duration in milliseconds that the client should wait for a request to
            complete.
            This duration encompasses sending the request, awaiting for a response from the server, and any required
            reconnection or retries.
            If the specified timeout is exceeded for a pending request, it will result in a timeout error. If not
            explicitly set, a default value of 250 milliseconds will be used.
        reconnect_strategy (Optional[BackoffStrategy]): Strategy used to determine how and when to reconnect, in case of
            connection failures.
            If not set, a default backoff strategy will be used.
        database_id (Optional[int]): Index of the logical database to connect to.
            Must be a non-negative integer.If not set, the client will connect to database 0.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command
            during connection establishment.
        protocol (ProtocolVersion): Serialization protocol to be used. If not set, `RESP3` will be used.
        inflight_requests_limit (Optional[int]): The maximum number of concurrent requests allowed to be in-flight
            (sent but not yet completed).
            This limit is used to control the memory usage and prevent the client from overwhelming the server or getting
            stuck in case of a queue backlog.
            If not set, a default value will be used.
        client_az (Optional[str]): Availability Zone of the client.
            If ReadFrom strategy is AZAffinity, this setting ensures that readonly commands are directed to replicas
            within the specified AZ if exits.
            If ReadFrom strategy is AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed
            to nodes (first replicas then primary) within the specified AZ if they exist.
        advanced_config (Optional[AdvancedBaseClientConfiguration]): Advanced configuration settings for the client.

        lazy_connect (Optional[bool]): Enables lazy connection mode, where physical connections to the server(s)
            are deferred until the first command is sent. This can reduce startup latency and allow for client
            creation in disconnected environments.

            When set to `True`, the client will not attempt to connect to the specified nodes during
            initialization. Instead, connections will be established only when a command is actually executed.

            Note that the first command executed with lazy connections may experience additional latency
            as it needs to establish the connection first. During this initial connection, the standard
            request timeout does not apply yet - instead, the connection establishment is governed by
            `AdvancedBaseClientConfiguration.connection_timeout`. The request timeout (`request_timeout`)
            only begins counting after the connection has been successfully established. This behavior
            can effectively increase the total time needed for the first command to complete.

            This setting applies to both standalone and cluster modes. Note that if an operation is
            attempted and connection fails (e.g., unreachable nodes), errors will surface at that point.

            If not set, connections are established immediately during client creation (equivalent to `False`).
    """

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
        inflight_requests_limit: Optional[int] = None,
        client_az: Optional[str] = None,
        advanced_config: Optional[AdvancedBaseClientConfiguration] = None,
        lazy_connect: Optional[bool] = None,
    ):
        self.addresses = addresses
        self.use_tls = use_tls
        self.credentials = credentials
        self.read_from = read_from
        self.request_timeout = request_timeout
        self.reconnect_strategy = reconnect_strategy
        self.database_id = database_id
        self.client_name = client_name
        self.protocol = protocol
        self.inflight_requests_limit = inflight_requests_limit
        self.client_az = client_az
        self.advanced_config = advanced_config
        self.lazy_connect = lazy_connect

        if read_from == ReadFrom.AZ_AFFINITY and not client_az:
            raise ValueError(
                "client_az must be set when read_from is set to AZ_AFFINITY"
            )

        if read_from == ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY and not client_az:
            raise ValueError(
                "client_az must be set when read_from is set to AZ_AFFINITY_REPLICAS_AND_PRIMARY"
            )

    def _set_addresses_in_request(self, request: ConnectionRequest) -> None:
        """Set addresses in the protobuf request."""
        for address in self.addresses:
            address_info = request.addresses.add()
            address_info.host = address.host
            address_info.port = address.port

    def _set_reconnect_strategy_in_request(self, request: ConnectionRequest) -> None:
        """Set reconnect strategy in the protobuf request."""
        if not self.reconnect_strategy:
            return

        request.connection_retry_strategy.number_of_retries = (
            self.reconnect_strategy.num_of_retries
        )
        request.connection_retry_strategy.factor = self.reconnect_strategy.factor
        request.connection_retry_strategy.exponent_base = (
            self.reconnect_strategy.exponent_base
        )
        if self.reconnect_strategy.jitter_percent is not None:
            request.connection_retry_strategy.jitter_percent = (
                self.reconnect_strategy.jitter_percent
            )

    def _set_credentials_in_request(self, request: ConnectionRequest) -> None:
        """Set credentials in the protobuf request."""
        if not self.credentials:
            return

        if self.credentials.username:
            request.authentication_info.username = self.credentials.username

        if self.credentials.password:
            request.authentication_info.password = self.credentials.password

        # Set IAM credentials if present
        if self.credentials.iam_config:
            iam_config = self.credentials.iam_config
            request.authentication_info.iam_credentials.cluster_name = (
                iam_config.cluster_name
            )
            request.authentication_info.iam_credentials.region = iam_config.region

            # Map ServiceType enum to protobuf ServiceType
            if iam_config.service == ServiceType.ELASTICACHE:
                from glide_shared.protobuf.connection_request_pb2 import (
                    ServiceType as ProtobufServiceType,
                )

                request.authentication_info.iam_credentials.service_type = (
                    ProtobufServiceType.ELASTICACHE
                )
            elif iam_config.service == ServiceType.MEMORYDB:
                from glide_shared.protobuf.connection_request_pb2 import (
                    ServiceType as ProtobufServiceType,
                )

                request.authentication_info.iam_credentials.service_type = (
                    ProtobufServiceType.MEMORYDB
                )

            # Set optional refresh interval
            if iam_config.refresh_interval_seconds is not None:
                request.authentication_info.iam_credentials.refresh_interval_seconds = (
                    iam_config.refresh_interval_seconds
                )

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

        # Set basic configuration
        self._set_addresses_in_request(request)
        request.tls_mode = TlsMode.SecureTls if self.use_tls else TlsMode.NoTls
        request.read_from = self.read_from.value
        request.cluster_mode_enabled = cluster_mode
        request.protocol = self.protocol.value

        # Set optional configuration
        if self.request_timeout:
            request.request_timeout = self.request_timeout

        self._set_reconnect_strategy_in_request(request)
        self._set_credentials_in_request(request)

        if self.client_name:
            request.client_name = self.client_name
        if self.inflight_requests_limit:
            request.inflight_requests_limit = self.inflight_requests_limit
        if self.client_az:
            request.client_az = self.client_az
        if self.database_id is not None:
            request.database_id = self.database_id
        if self.advanced_config:
            self.advanced_config._create_a_protobuf_conn_request(request)
        if self.lazy_connect is not None:
            request.lazy_connect = self.lazy_connect

        return request

    def _is_pubsub_configured(self) -> bool:
        return False

    def _get_pubsub_callback_and_context(
        self,
    ) -> Tuple[Optional[Callable[[PubSubMsg, Any], None]], Any]:
        return None, None


class AdvancedGlideClientConfiguration(AdvancedBaseClientConfiguration):
    """
    Represents the advanced configuration settings for a Standalone Glide client.
    """

    def __init__(
        self,
        connection_timeout: Optional[int] = None,
        tls_config: Optional[TlsAdvancedConfiguration] = None,
    ):

        super().__init__(connection_timeout, tls_config)


class GlideClientConfiguration(BaseClientConfiguration):
    """
    Represents the configuration settings for a Standalone Glide client.

    Attributes:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
            Only nodes whose addresses were provided will be used by the client.
            For example::

                [
                    NodeAddress("sample-address-0001.use1.cache.amazonaws.com", 6379),
                    NodeAddress("sample-address-0002.use1.cache.amazonaws.com", 6379)
                ]

        use_tls (bool): True if communication with the cluster should use Transport Level Security.
                Please use `AdvancedGlideClusterClientConfiguration`.
        credentials (ServerCredentials): Credentials for authentication process.
                If none are set, the client will not authenticate itself with the server.
        read_from (ReadFrom): If not set, `PRIMARY` will be used.
        request_timeout (Optional[int]):  The duration in milliseconds that the client should wait for a request to complete.
                This duration encompasses sending the request, awaiting for a response from the server, and any required
                reconnection or retries.
                If the specified timeout is exceeded for a pending request, it will result in a timeout error.
                If not explicitly set, a default value of 250 milliseconds will be used.
        reconnect_strategy (Optional[BackoffStrategy]): Strategy used to determine how and when to reconnect, in case of
            connection failures.
            If not set, a default backoff strategy will be used.
        database_id (Optional[int]): Index of the logical database to connect to.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during
            connection establishment.
        protocol (ProtocolVersion): The version of the RESP protocol to communicate with the server.
        pubsub_subscriptions (Optional[GlideClientConfiguration.PubSubSubscriptions]): Pubsub subscriptions to be used for the
                client.
                Will be applied via SUBSCRIBE/PSUBSCRIBE commands during connection establishment.
        inflight_requests_limit (Optional[int]): The maximum number of concurrent requests allowed to be in-flight
            (sent but not yet completed).
            This limit is used to control the memory usage and prevent the client from overwhelming the server or getting
            stuck in case of a queue backlog.
            If not set, a default value will be used.
        client_az (Optional[str]): Availability Zone of the client.
            If ReadFrom strategy is AZAffinity, this setting ensures that readonly commands are directed to replicas within
            the specified AZ if exits.
            If ReadFrom strategy is AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed to
            nodes (first replicas then primary) within the specified AZ if they exist.
        advanced_config (Optional[AdvancedGlideClientConfiguration]): Advanced configuration settings for the client,
            see `AdvancedGlideClientConfiguration`.
    """

    class PubSubChannelModes(IntEnum):
        """
        Describes pubsub subsciption modes.
        See [valkey.io](https://valkey.io/docs/topics/pubsub/) for more details
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
            callback (Optional[Callable[[PubSubMsg, Any], None]]):
                Optional callback to accept the incomming messages.
            context (Any):
                Arbitrary context to pass to the callback.
        """

        channels_and_patterns: Dict[
            GlideClientConfiguration.PubSubChannelModes, Set[str]
        ]
        callback: Optional[Callable[[PubSubMsg, Any], None]]
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
        inflight_requests_limit: Optional[int] = None,
        client_az: Optional[str] = None,
        advanced_config: Optional[AdvancedGlideClientConfiguration] = None,
        lazy_connect: Optional[bool] = None,
    ):
        super().__init__(
            addresses=addresses,
            use_tls=use_tls,
            credentials=credentials,
            read_from=read_from,
            request_timeout=request_timeout,
            reconnect_strategy=reconnect_strategy,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
            inflight_requests_limit=inflight_requests_limit,
            client_az=client_az,
            advanced_config=advanced_config,
            lazy_connect=lazy_connect,
        )
        self.pubsub_subscriptions = pubsub_subscriptions

    def _create_a_protobuf_conn_request(
        self, cluster_mode: bool = False
    ) -> ConnectionRequest:
        assert cluster_mode is False
        request = super()._create_a_protobuf_conn_request(cluster_mode)

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
    ) -> Tuple[Optional[Callable[[PubSubMsg, Any], None]], Any]:
        if self.pubsub_subscriptions:
            return self.pubsub_subscriptions.callback, self.pubsub_subscriptions.context
        return None, None


class AdvancedGlideClusterClientConfiguration(AdvancedBaseClientConfiguration):
    """
    Represents the advanced configuration settings for a Glide Cluster client.

    Attributes:
        connection_timeout (Optional[int]): The duration in milliseconds to wait for a TCP/TLS connection to complete.
            This applies both during initial client creation and any reconnection that may occur during request processing.
            **Note**: A high connection timeout may lead to prolonged blocking of the entire command pipeline.
            If not explicitly set, a default value of 2000 milliseconds will be used.
        tls_config (Optional[TlsAdvancedConfiguration]): The advanced TLS configuration settings.
            This allows for more granular control of TLS behavior, such as enabling an insecure mode
            that bypasses certificate validation.
        refresh_topology_from_initial_nodes (bool): Enables refreshing the cluster topology using only the initial nodes.
            When this option is enabled, all topology updates (both the periodic checks and on-demand refreshes
            triggered by topology changes) will query only the initial nodes provided when creating the client, rather than using the internal cluster view.
    """

    def __init__(
        self,
        connection_timeout: Optional[int] = None,
        tls_config: Optional[TlsAdvancedConfiguration] = None,
        refresh_topology_from_initial_nodes: bool = False,
    ):
        super().__init__(connection_timeout, tls_config)
        self.refresh_topology_from_initial_nodes = refresh_topology_from_initial_nodes

    def _create_a_protobuf_conn_request(
        self, request: ConnectionRequest
    ) -> ConnectionRequest:
        super()._create_a_protobuf_conn_request(request)

        request.refresh_topology_from_initial_nodes = (
            self.refresh_topology_from_initial_nodes
        )
        return request


class GlideClusterClientConfiguration(BaseClientConfiguration):
    """
    Represents the configuration settings for a Cluster Glide client.

    Attributes:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
            The list can be partial, as the client will attempt to map out the cluster and find all nodes.
            For example::

                [
                    NodeAddress("sample-address-0001.use1.cache.amazonaws.com", 6379),
                ]

        use_tls (bool): True if communication with the cluster should use Transport Level Security.
                For advanced tls configuration, please use `AdvancedGlideClusterClientConfiguration`.
        credentials (ServerCredentials): Credentials for authentication process.
                If none are set, the client will not authenticate itself with the server.
        read_from (ReadFrom): If not set, `PRIMARY` will be used.
        request_timeout (Optional[int]):  The duration in milliseconds that the client should wait for a request to complete.
            This duration encompasses sending the request, awaiting for a response from the server, and any required
            reconnection or retries.
            If the specified timeout is exceeded for a pending request, it will result in a timeout error. If not explicitly
            set, a default value of 250 milliseconds will be used.
        reconnect_strategy (Optional[BackoffStrategy]): Strategy used to determine how and when to reconnect, in case of
            connection failures.
            If not set, a default backoff strategy will be used.
        database_id (Optional[int]): Index of the logical database to connect to.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during
            connection establishment.
        protocol (ProtocolVersion): The version of the RESP protocol to communicate with the server.
        periodic_checks (Union[PeriodicChecksStatus, PeriodicChecksManualInterval]): Configure the periodic topology checks.
            These checks evaluate changes in the cluster's topology, triggering a slot refresh when detected.
            Periodic checks ensure a quick and efficient process by querying a limited number of nodes.
            Defaults to PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS.
        pubsub_subscriptions (Optional[GlideClusterClientConfiguration.PubSubSubscriptions]): Pubsub subscriptions to be used
            for the client.
            Will be applied via SUBSCRIBE/PSUBSCRIBE/SSUBSCRIBE commands during connection establishment.
        inflight_requests_limit (Optional[int]): The maximum number of concurrent requests allowed to be in-flight
            (sent but not yet completed).
            This limit is used to control the memory usage and prevent the client from overwhelming the server or getting
            stuck in case of a queue backlog.
            If not set, a default value will be used.
        client_az (Optional[str]): Availability Zone of the client.
            If ReadFrom strategy is AZAffinity, this setting ensures that readonly commands are directed to replicas within
            the specified AZ if exits.
            If ReadFrom strategy is AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed to
            nodes (first replicas then primary) within the specified AZ if they exist.
        advanced_config (Optional[AdvancedGlideClusterClientConfiguration]) : Advanced configuration settings for the client,
            see `AdvancedGlideClusterClientConfiguration`.


    Note:
        Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff
        with fixed values is used.
    """

    class PubSubChannelModes(IntEnum):
        """
        Describes pubsub subsciption modes.
        See [valkey.io](https://valkey.io/docs/topics/pubsub/) for more details
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
            callback (Optional[Callable[[PubSubMsg, Any], None]]):
                Optional callback to accept the incoming messages.
            context (Any):
                Arbitrary context to pass to the callback.
        """

        channels_and_patterns: Dict[
            GlideClusterClientConfiguration.PubSubChannelModes, Set[str]
        ]
        callback: Optional[Callable[[PubSubMsg, Any], None]]
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
        periodic_checks: Union[
            PeriodicChecksStatus, PeriodicChecksManualInterval
        ] = PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS,
        pubsub_subscriptions: Optional[PubSubSubscriptions] = None,
        inflight_requests_limit: Optional[int] = None,
        client_az: Optional[str] = None,
        advanced_config: Optional[AdvancedGlideClusterClientConfiguration] = None,
        lazy_connect: Optional[bool] = None,
    ):
        super().__init__(
            addresses=addresses,
            use_tls=use_tls,
            credentials=credentials,
            read_from=read_from,
            request_timeout=request_timeout,
            reconnect_strategy=reconnect_strategy,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
            inflight_requests_limit=inflight_requests_limit,
            client_az=client_az,
            advanced_config=advanced_config,
            lazy_connect=lazy_connect,
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

        if self.lazy_connect is not None:
            request.lazy_connect = self.lazy_connect
        return request

    def _is_pubsub_configured(self) -> bool:
        return self.pubsub_subscriptions is not None

    def _get_pubsub_callback_and_context(
        self,
    ) -> Tuple[Optional[Callable[[PubSubMsg, Any], None]], Any]:
        if self.pubsub_subscriptions:
            return self.pubsub_subscriptions.callback, self.pubsub_subscriptions.context
        return None, None


def load_root_certificates_from_file(path: str) -> bytes:
    """
    Load PEM-encoded root certificates from a file.

    This is a convenience function for loading custom root certificates from disk
    to be used with TlsAdvancedConfiguration.

    Args:
        path (str): The file path to the PEM-encoded certificate file.

    Returns:
        bytes: The certificate data in PEM format.

    Raises:
        FileNotFoundError: If the certificate file does not exist.
        ConfigurationError: If the certificate file is empty.

    Example usage::

        from glide_shared.config import load_root_certificates_from_file, TlsAdvancedConfiguration

        # Load certificates from file
        certs = load_root_certificates_from_file('/path/to/ca-cert.pem')

        # Use in TLS configuration
        tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
    """
    try:
        with open(path, "rb") as f:
            data = f.read()
    except FileNotFoundError:
        raise FileNotFoundError(f"Certificate file not found: {path}")
    except Exception as e:
        raise ConfigurationError(f"Failed to read certificate file: {e}")

    if len(data) == 0:
        raise ConfigurationError(f"Certificate file is empty: {path}")

    return data
