# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide_shared.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
    BaseClientConfiguration,
    ConfigurationError,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    PeriodicChecksManualInterval,
    PeriodicChecksStatus,
    ReadFrom,
    TlsAdvancedConfiguration,
)
from glide_shared.protobuf.connection_request_pb2 import ConnectionRequest
from glide_shared.protobuf.connection_request_pb2 import ReadFrom as ProtobufReadFrom
from glide_shared.protobuf.connection_request_pb2 import TlsMode


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


def test_tls_insecure_in_protobuf_request():
    tls_conf = TlsAdvancedConfiguration(use_insecure_tls=True)

    config = GlideClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=False,
        advanced_config=AdvancedGlideClientConfiguration(tls_config=tls_conf),
    )
    with pytest.raises(ConfigurationError):
        config._create_a_protobuf_conn_request()

    config = GlideClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=True,
        advanced_config=AdvancedGlideClientConfiguration(tls_config=tls_conf),
    )
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode is TlsMode.InsecureTls

    config = GlideClusterClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=False,
        advanced_config=AdvancedGlideClusterClientConfiguration(tls_config=tls_conf),
    )
    with pytest.raises(ConfigurationError):
        config._create_a_protobuf_conn_request(cluster_mode=True)

    config = GlideClusterClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=True,
        advanced_config=AdvancedGlideClusterClientConfiguration(tls_config=tls_conf),
    )
    request = config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode is TlsMode.InsecureTls


# Database ID configuration tests
def test_database_id_validation_in_base_config():
    """Test database_id validation in BaseClientConfiguration."""
    # Valid database_id values
    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=0)
    assert config.database_id == 0

    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=5)
    assert config.database_id == 5

    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=15)
    assert config.database_id == 15

    # None should be allowed (defaults to 0)
    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=None)
    assert config.database_id is None

    # Invalid database_id values
    with pytest.raises(ValueError, match="database_id must be non-negative"):
        BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=-1)

    with pytest.raises(
        ValueError, match="database_id must be less than or equal to 15"
    ):
        BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=16)

    with pytest.raises(ValueError, match="database_id must be an integer"):
        BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id="5")


def test_database_id_in_standalone_config():
    """Test database_id configuration in GlideClientConfiguration."""
    config = GlideClientConfiguration([NodeAddress("127.0.0.1")], database_id=5)
    assert config.database_id == 5

    request = config._create_a_protobuf_conn_request()
    assert request.database_id == 5
    assert request.cluster_mode_enabled is False


def test_database_id_in_cluster_config():
    """Test database_id configuration in GlideClusterClientConfiguration."""
    config = GlideClusterClientConfiguration([NodeAddress("127.0.0.1")], database_id=3)
    assert config.database_id == 3

    request = config._create_a_protobuf_conn_request(cluster_mode=True)
    assert request.database_id == 3
    assert request.cluster_mode_enabled is True


def test_database_id_default_behavior():
    """Test default database_id behavior (None/0)."""
    # Standalone config without database_id
    config = GlideClientConfiguration([NodeAddress("127.0.0.1")])
    assert config.database_id is None

    request = config._create_a_protobuf_conn_request()
    # When database_id is None, it should be 0 in protobuf (default value)
    assert request.database_id == 0

    # Cluster config without database_id
    config = GlideClusterClientConfiguration([NodeAddress("127.0.0.1")])
    assert config.database_id is None

    request = config._create_a_protobuf_conn_request(cluster_mode=True)
    # When database_id is None, it should be 0 in protobuf (default value)
    assert request.database_id == 0


def test_database_id_protobuf_inclusion():
    """Test that database_id is properly included in protobuf when set."""
    # Test with database_id = 0 (should be included)
    config = GlideClientConfiguration([NodeAddress("127.0.0.1")], database_id=0)
    request = config._create_a_protobuf_conn_request()
    assert request.database_id == 0

    # Test with database_id = 5 (should be included)
    config = GlideClientConfiguration([NodeAddress("127.0.0.1")], database_id=5)
    request = config._create_a_protobuf_conn_request()
    assert request.database_id == 5

    # Test with database_id = None (should default to 0)
    config = GlideClientConfiguration([NodeAddress("127.0.0.1")])
    request = config._create_a_protobuf_conn_request()
    assert request.database_id == 0
