# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from __future__ import annotations

from typing import List, Optional, Union

from glide_shared.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
)
from glide_shared.config import (
    GlideClientConfiguration as SharedGlideClientConfiguration,
)
from glide_shared.config import (
    GlideClusterClientConfiguration as SharedGlideClusterClientConfiguration,
)
from glide_shared.config import (
    NodeAddress,
    PeriodicChecksManualInterval,
    PeriodicChecksStatus,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
)


class GlideClientConfiguration(SharedGlideClientConfiguration):
    """
    Represents the configuration settings for a Standalone Sync Glide client.

    Attributes:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
            Only nodes whose addresses were provided will be used by the client.
            For example:
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
        database_id (Optional[int]): index of the logical database to connect to.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during
            connection establishment.
        protocol (ProtocolVersion): The version of the RESP protocol to communicate with the server.
        client_az (Optional[str]): Availability Zone of the client.
            If ReadFrom strategy is AZAffinity, this setting ensures that readonly commands are directed to replicas within
            the specified AZ if exits.
            If ReadFrom strategy is AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed to
            nodes (first replicas then primary) within the specified AZ if they exist.
        advanced_config (Optional[AdvancedGlideClientConfiguration]): Advanced configuration settings for the client,
            see `AdvancedGlideClientConfiguration`.
        pubsub_subscriptions (Optional[GlideClientConfiguration.PubSubSubscriptions]): Pubsub subscriptions to be used for the
            client.
            Will be applied via SUBSCRIBE/PSUBSCRIBE commands during connection establishment.

        Note:
            PubSub and inflight_requests_limit are not yet supported for the sync client.
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
        client_az: Optional[str] = None,
        advanced_config: Optional[AdvancedGlideClientConfiguration] = None,
        lazy_connect: Optional[bool] = None,
        pubsub_subscriptions: Optional[
            GlideClientConfiguration.PubSubSubscriptions
        ] = None,
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
            pubsub_subscriptions=pubsub_subscriptions,
            inflight_requests_limit=None,
            client_az=client_az,
            advanced_config=advanced_config,
            lazy_connect=lazy_connect,
        )


class GlideClusterClientConfiguration(SharedGlideClusterClientConfiguration):
    """
    Represents the configuration settings for a Cluster Sync Glide client.

    Attributes:
        addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
            The list can be partial, as the client will attempt to map out the cluster and find all nodes.
            For example:
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
            If not set, the client will connect to database 0.
        client_name (Optional[str]): Client name to be used for the client. Will be used with CLIENT SETNAME command during
            connection establishment.
        protocol (ProtocolVersion): The version of the RESP protocol to communicate with the server.
        periodic_checks (Union[PeriodicChecksStatus, PeriodicChecksManualInterval]): Configure the periodic topology checks.
            These checks evaluate changes in the cluster's topology, triggering a slot refresh when detected.
            Periodic checks ensure a quick and efficient process by querying a limited number of nodes.
            Defaults to PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS.
        client_az (Optional[str]): Availability Zone of the client.
            If ReadFrom strategy is AZAffinity, this setting ensures that readonly commands are directed to replicas within
            the specified AZ if exits.
            If ReadFrom strategy is AZAffinityReplicasAndPrimary, this setting ensures that readonly commands are directed to
            nodes (first replicas then primary) within the specified AZ if they exist.
        advanced_config (Optional[AdvancedGlideClusterClientConfiguration]) : Advanced configuration settings for the client,
            see `AdvancedGlideClusterClientConfiguration`.
        pubsub_subscriptions (Optional[GlideClusterClientConfiguration.PubSubSubscriptions]): Pubsub subscriptions to be used
            for the client.
            Will be applied via SUBSCRIBE/PSUBSCRIBE/SSUBSCRIBE commands during connection establishment.


    Notes:
        Currently, the reconnection strategy in cluster mode is not configurable, and exponential backoff
        with fixed values is used.
        In addition, PubSub and inflight_requests_limit are not yet supported for the sync client.
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
        periodic_checks: Union[
            PeriodicChecksStatus, PeriodicChecksManualInterval
        ] = PeriodicChecksStatus.ENABLED_DEFAULT_CONFIGS,
        client_az: Optional[str] = None,
        advanced_config: Optional[AdvancedGlideClusterClientConfiguration] = None,
        lazy_connect: Optional[bool] = None,
        pubsub_subscriptions: Optional[
            GlideClusterClientConfiguration.PubSubSubscriptions
        ] = None,
    ):
        super().__init__(
            addresses=addresses,
            use_tls=use_tls,
            credentials=credentials,
            read_from=read_from,
            request_timeout=request_timeout,
            reconnect_strategy=reconnect_strategy,
            database_id=database_id,
            periodic_checks=periodic_checks,
            pubsub_subscriptions=pubsub_subscriptions,
            client_name=client_name,
            protocol=protocol,
            inflight_requests_limit=None,
            client_az=client_az,
            advanced_config=advanced_config,
            lazy_connect=lazy_connect,
        )
