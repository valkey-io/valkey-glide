# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from glide.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
    BaseClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    PeriodicChecksManualInterval,
    PeriodicChecksStatus,
    ReadFrom,
)
from glide.protobuf.connection_request_pb2 import ConnectionRequest
from glide.protobuf.connection_request_pb2 import ReadFrom as ProtobufReadFrom
from glide.protobuf.connection_request_pb2 import TlsMode


def test_default_client_config():
    config = BaseClientConfiguration([])
    assert len(config.addresses) == 0
    assert config.read_from.value == ProtobufReadFrom.Primary
    assert config.use_tls is False
    assert config.client_name is None


def test_convert_to_protobuf():
    config = BaseClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=True,
        read_from=ReadFrom.PREFER_REPLICA,
        client_name="TEST_CLIENT_NAME",
    )
    request = config._create_a_protobuf_conn_request()
    assert isinstance(request, ConnectionRequest)
    assert request.addresses[0].host == "127.0.0.1"
    assert request.addresses[0].port == 6379
    assert request.tls_mode is TlsMode.SecureTls
    assert request.read_from == ProtobufReadFrom.PreferReplica
    assert request.client_name == "TEST_CLIENT_NAME"


def test_periodic_checks_interval_to_protobuf():
    config = GlideClusterClientConfiguration(
        [NodeAddress("127.0.0.1")],
    )
    request = config._create_a_protobuf_conn_request(cluster_mode=True)
    assert not request.HasField("periodic_checks_disabled")
    assert not request.HasField("periodic_checks_manual_interval")

    config.periodic_checks = PeriodicChecksStatus.DISABLED
    request = config._create_a_protobuf_conn_request(cluster_mode=True)
    assert request.HasField("periodic_checks_disabled")

    config.periodic_checks = PeriodicChecksManualInterval(30)
    request = config._create_a_protobuf_conn_request(cluster_mode=True)
    assert request.periodic_checks_manual_interval.duration_in_sec == 30


def test_convert_config_with_azaffinity_to_protobuf():
    az = "us-east-1a"
    config = BaseClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=True,
        read_from=ReadFrom.AZ_AFFINITY,
        client_az=az,
    )
    request = config._create_a_protobuf_conn_request()
    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode is TlsMode.SecureTls
    assert request.read_from == ProtobufReadFrom.AZAffinity
    assert request.client_az == az


def test_convert_config_with_azaffinity_replicas_and_primary_to_protobuf():
    az = "us-east-1a"
    config = BaseClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=True,
        read_from=ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY,
        client_az=az,
    )
    request = config._create_a_protobuf_conn_request()
    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode is TlsMode.SecureTls
    assert request.read_from == ProtobufReadFrom.AZAffinityReplicasAndPrimary
    assert request.client_az == az


def test_connection_timeout_in_protobuf_request():
    connection_timeout = 5000  # in milliseconds
    config = GlideClientConfiguration(
        [NodeAddress("127.0.0.1")],
        advanced_config=AdvancedGlideClientConfiguration(connection_timeout),
    )
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.connection_timeout == connection_timeout

    config = GlideClusterClientConfiguration(
        [NodeAddress("127.0.0.1")],
        advanced_config=AdvancedGlideClusterClientConfiguration(connection_timeout),
    )
    request = config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(request, ConnectionRequest)
    assert request.connection_timeout == connection_timeout


def test_reconnect_strategy_in_protobuf_request():
    reconnect_strategy = BackoffStrategy(7, 69, 3, 18)
    config = GlideClientConfiguration(
        [NodeAddress("127.0.0.1")],
        reconnect_strategy=reconnect_strategy,
    )
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert (
        request.connection_retry_strategy.number_of_retries
        == reconnect_strategy.num_of_retries
    )
    assert request.connection_retry_strategy.factor == reconnect_strategy.factor
    assert (
        request.connection_retry_strategy.exponent_base
        == reconnect_strategy.exponent_base
    )
    assert (
        request.connection_retry_strategy.jitter_percent
        == reconnect_strategy.jitter_percent
    )

    config = GlideClusterClientConfiguration(
        [NodeAddress("127.0.0.1")],
        reconnect_strategy=reconnect_strategy,
    )
    request = config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(request, ConnectionRequest)
    assert (
        request.connection_retry_strategy.number_of_retries
        == reconnect_strategy.num_of_retries
    )
    assert request.connection_retry_strategy.factor == reconnect_strategy.factor
    assert (
        request.connection_retry_strategy.exponent_base
        == reconnect_strategy.exponent_base
    )
    assert (
        request.connection_retry_strategy.jitter_percent
        == reconnect_strategy.jitter_percent
    )
