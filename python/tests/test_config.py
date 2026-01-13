# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide_shared.config import (
    AdvancedBaseClientConfiguration,
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

    # Test broader range of database IDs
    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=100)
    assert config.database_id == 100

    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=1000)
    assert config.database_id == 1000

    # None should be allowed (defaults to 0)
    config = BaseClientConfiguration([NodeAddress("127.0.0.1")], database_id=None)
    assert config.database_id is None


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


def test_refresh_topology_from_initial_nodes_in_cluster_config():
    """Test refresh_topology_from_initial_nodes configuration in GlideClusterClientConfiguration."""
    config = GlideClusterClientConfiguration(
        [NodeAddress("127.0.0.1")],
        advanced_config=AdvancedGlideClusterClientConfiguration(
            refresh_topology_from_initial_nodes=True
        ),
    )
    request = config._create_a_protobuf_conn_request(cluster_mode=True)
    assert request.refresh_topology_from_initial_nodes is True


# Test constants
TEST_ADDRESSES = [NodeAddress("127.0.0.1")]
TEST_CERT_DATA_1 = b"-----BEGIN CERTIFICATE-----\nMIIC1...\n-----END CERTIFICATE-----"
TEST_CERT_DATA_2 = b"-----BEGIN CERTIFICATE-----\nMIIC2...\n-----END CERTIFICATE-----"
TEST_CLIENT_CERT_DATA = (
    b"-----BEGIN CERTIFICATE-----\nMIIC3...\n-----END CERTIFICATE-----"
)
TEST_CLIENT_KEY_DATA = (
    b"-----BEGIN PRIVATE KEY-----\nMIIC4...\n-----END PRIVATE KEY-----"
)


def _build_standalone_config(tls_config=None):
    """Helper to build standalone client configuration."""
    return GlideClientConfiguration(
        TEST_ADDRESSES,
        use_tls=True,
        advanced_config=AdvancedGlideClientConfiguration(tls_config=tls_config),
    )


def _build_cluster_config(tls_config=None):
    """Helper to build cluster client configuration."""
    return GlideClusterClientConfiguration(
        TEST_ADDRESSES,
        use_tls=True,
        advanced_config=AdvancedGlideClusterClientConfiguration(tls_config=tls_config),
    )


# TLS Root Certificate Configuration Tests
def test_tls_root_certificates_with_custom_certs():
    """Test TLS configuration with custom root certificates."""
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=TEST_CERT_DATA_1)

    # Test standalone client
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.SecureTls
    assert len(request.root_certs) == 1
    assert request.root_certs[0] == TEST_CERT_DATA_1

    # Test cluster client
    cluster_config = _build_cluster_config(tls_config)
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(cluster_request, ConnectionRequest)
    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert len(cluster_request.root_certs) == 1
    assert cluster_request.root_certs[0] == TEST_CERT_DATA_1


def test_tls_root_certificates_with_none():
    """Test TLS configuration with None root certificates (uses platform verifier)."""
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=None)

    # Test standalone client
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.SecureTls
    assert len(request.root_certs) == 0  # Should not be set

    # Test cluster client
    cluster_config = _build_cluster_config(tls_config)
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(cluster_request, ConnectionRequest)
    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert len(cluster_request.root_certs) == 0  # Should not be set


def test_tls_root_certificates_with_empty_bytes():
    """Test that empty bytes (non-None but length 0) raises ConfigurationError."""
    empty_certs = b""
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=empty_certs)

    # Test standalone client
    config = _build_standalone_config(tls_config)
    with pytest.raises(ConfigurationError) as exc_info:
        config._create_a_protobuf_conn_request()
    assert "root_pem_cacerts cannot be an empty bytes object" in str(exc_info.value)

    # Test cluster client
    cluster_config = _build_cluster_config(tls_config)
    with pytest.raises(ConfigurationError) as exc_info:
        cluster_config._create_a_protobuf_conn_request(cluster_mode=True)
    assert "root_pem_cacerts cannot be an empty bytes object" in str(exc_info.value)


def test_tls_root_certificates_without_advanced_config():
    """Test that TLS works without advanced config (uses platform verifier)."""
    # Test standalone client
    config = GlideClientConfiguration(
        TEST_ADDRESSES,
        use_tls=True,
    )
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.SecureTls
    assert len(request.root_certs) == 0  # Should use platform verifier

    # Test cluster client
    cluster_config = GlideClusterClientConfiguration(
        TEST_ADDRESSES,
        use_tls=True,
    )
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(cluster_request, ConnectionRequest)
    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert len(cluster_request.root_certs) == 0  # Should use platform verifier


def test_tls_root_certificates_with_multiple_certs():
    """Test TLS configuration with multiple certificates (concatenated PEM)."""
    multi_cert_data = TEST_CERT_DATA_1 + TEST_CERT_DATA_2

    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=multi_cert_data)

    # Test standalone client
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.SecureTls
    assert len(request.root_certs) == 1
    assert request.root_certs[0] == multi_cert_data

    # Test cluster client
    cluster_config = _build_cluster_config(tls_config)
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(cluster_request, ConnectionRequest)
    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert len(cluster_request.root_certs) == 1
    assert cluster_request.root_certs[0] == multi_cert_data


def test_tls_root_certificates_with_insecure_tls():
    """Test that root certificates can be combined with insecure TLS."""
    tls_config = TlsAdvancedConfiguration(
        use_insecure_tls=True, root_pem_cacerts=TEST_CERT_DATA_1
    )

    # Test standalone client
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.InsecureTls
    assert len(request.root_certs) == 1
    assert request.root_certs[0] == TEST_CERT_DATA_1


def test_load_root_certificates_from_file_success(tmp_path):
    """Test loading certificates from a file successfully."""
    from glide_shared.config import load_root_certificates_from_file

    # Create a temporary certificate file
    cert_path = tmp_path / "test-cert.pem"
    cert_content = b"-----BEGIN CERTIFICATE-----\nMIIC...\n-----END CERTIFICATE-----"
    cert_path.write_bytes(cert_content)

    # Load the certificate
    loaded_cert = load_root_certificates_from_file(str(cert_path))
    assert loaded_cert == cert_content


def test_load_root_certificates_from_file_not_found():
    """Test loading certificates from a non-existent file."""
    from glide_shared.config import load_root_certificates_from_file

    with pytest.raises(FileNotFoundError) as exc_info:
        load_root_certificates_from_file("/nonexistent/path/cert.pem")
    assert "Certificate file not found" in str(exc_info.value)


def test_load_root_certificates_from_file_empty(tmp_path):
    """Test loading certificates from an empty file."""
    from glide_shared.config import load_root_certificates_from_file

    # Create an empty certificate file
    cert_path = tmp_path / "empty-cert.pem"
    cert_path.write_bytes(b"")

    with pytest.raises(ConfigurationError) as exc_info:
        load_root_certificates_from_file(str(cert_path))
    assert "Certificate file is empty" in str(exc_info.value)


def test_load_root_certificates_from_file_multiple_certs(tmp_path):
    """Test loading multiple certificates from a file."""
    from glide_shared.config import load_root_certificates_from_file

    # Create a file with multiple certificates
    cert_path = tmp_path / "multi-cert.pem"
    multi_cert_content = TEST_CERT_DATA_1 + TEST_CERT_DATA_2
    cert_path.write_bytes(multi_cert_content)

    # Load the certificates
    loaded_certs = load_root_certificates_from_file(str(cert_path))
    assert loaded_certs == multi_cert_content


def test_load_root_certificates_integration(tmp_path):
    """Integration test: Load certificate and use it in configuration."""
    from glide_shared.config import load_root_certificates_from_file

    # Create a temporary certificate file
    cert_path = tmp_path / "ca-cert.pem"
    cert_path.write_bytes(TEST_CERT_DATA_1)

    # Load certificate
    certs = load_root_certificates_from_file(str(cert_path))

    # Use in standalone configuration
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert request.tls_mode == TlsMode.SecureTls
    assert len(request.root_certs) == 1
    assert request.root_certs[0] == TEST_CERT_DATA_1

    # Use in cluster configuration
    cluster_tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
    cluster_config = _build_cluster_config(cluster_tls_config)
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert len(cluster_request.root_certs) == 1
    assert cluster_request.root_certs[0] == TEST_CERT_DATA_1


def test_tls_client_auth():
    """Test TLS configuration with custom client certificates."""
    tls_config = TlsAdvancedConfiguration(
        client_cert_pem=TEST_CLIENT_CERT_DATA,
        client_key_pem=TEST_CLIENT_KEY_DATA,
    )

    # Test standalone client
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.SecureTls
    assert request.client_cert == TEST_CLIENT_CERT_DATA
    assert request.client_key == TEST_CLIENT_KEY_DATA

    # Test cluster client
    cluster_config = _build_cluster_config(tls_config)
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(cluster_request, ConnectionRequest)
    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert cluster_request.client_cert == TEST_CLIENT_CERT_DATA
    assert cluster_request.client_key == TEST_CLIENT_KEY_DATA


def test_tls_client_auth_none():
    """Test TLS configuration with custom client certificates."""
    tls_config = TlsAdvancedConfiguration(
        client_cert_pem=None,
        client_key_pem=None,
    )

    # Test standalone client
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert isinstance(request, ConnectionRequest)
    assert request.tls_mode == TlsMode.SecureTls
    assert request.client_cert == b""
    assert request.client_key == b""

    # Test cluster client
    cluster_config = _build_cluster_config(tls_config)
    cluster_request = cluster_config._create_a_protobuf_conn_request(cluster_mode=True)

    assert isinstance(cluster_request, ConnectionRequest)
    assert cluster_request.tls_mode == TlsMode.SecureTls
    assert cluster_request.client_cert == b""
    assert cluster_request.client_key == b""


def test_load_client_certificate_from_file_success(tmp_path):
    """Test loading certificates from a file successfully."""
    from glide_shared.config import load_client_certificate_from_file

    # Create a temporary certificate file
    cert_path = tmp_path / "test-cert.pem"
    cert_content = TEST_CLIENT_CERT_DATA
    cert_path.write_bytes(cert_content)

    # Load the certificate
    loaded_cert = load_client_certificate_from_file(str(cert_path))
    assert loaded_cert == cert_content


def test_load_client_certificate_from_file_not_found():
    """Test loading certificates from a non-existent file."""
    from glide_shared.config import load_client_certificate_from_file

    with pytest.raises(FileNotFoundError) as exc_info:
        load_client_certificate_from_file("/nonexistent/path/cert.pem")
    assert "Client certificate file not found" in str(exc_info.value)


def test_load_client_certificate_from_file_empty(tmp_path):
    """Test loading certificates from an empty file."""
    from glide_shared.config import load_client_certificate_from_file

    # Create an empty certificate file
    cert_path = tmp_path / "empty-cert.pem"
    cert_path.write_bytes(b"")

    with pytest.raises(ConfigurationError) as exc_info:
        load_client_certificate_from_file(str(cert_path))
    assert "Client certificate file is empty" in str(exc_info.value)


def test_load_client_key_from_file_success(tmp_path):
    """Test loading certificates from a file successfully."""
    from glide_shared.config import load_client_key_from_file

    # Create a temporary key file
    cert_path = tmp_path / "test-key.pem"
    cert_content = TEST_CLIENT_KEY_DATA
    cert_path.write_bytes(cert_content)

    # Load the key
    loaded_cert = load_client_key_from_file(str(cert_path))
    assert loaded_cert == cert_content


def test_load_client_key_from_file_not_found():
    """Test loading certificates from a non-existent file."""
    from glide_shared.config import load_client_key_from_file

    with pytest.raises(FileNotFoundError) as exc_info:
        load_client_key_from_file("/nonexistent/path/key.pem")
    assert "Client key file not found" in str(exc_info.value)


def test_load_client_key_from_file_empty(tmp_path):
    """Test loading certificates from an empty file."""
    from glide_shared.config import load_client_key_from_file

    # Create an empty key file
    cert_path = tmp_path / "empty-key.pem"
    cert_path.write_bytes(b"")

    with pytest.raises(ConfigurationError) as exc_info:
        load_client_key_from_file(str(cert_path))
    assert "Client key file is empty" in str(exc_info.value)


def test_tls_configuration_client_cert_key_consistency():
    config = AdvancedBaseClientConfiguration(
        tls_config=TlsAdvancedConfiguration(),
    )
    request = ConnectionRequest()
    # Do not raise if both client_cert_pem and client_key_pem are not provided.
    config._create_a_protobuf_conn_request(request)

    config.tls_config.client_cert_pem = b"nonempty"
    config.tls_config.client_key_pem = None
    with pytest.raises(ConfigurationError) as exc_info:
        config._create_a_protobuf_conn_request(request)
    assert "client_cert_pem is provided but client_key_pem not provided" in str(
        exc_info.value
    )

    config.tls_config.client_cert_pem = None
    config.tls_config.client_key_pem = b"nonempty"
    with pytest.raises(ConfigurationError) as exc_info:
        config._create_a_protobuf_conn_request(request)
    assert "client_key_pem is provided but client_cert_pem not provided" in str(
        exc_info.value
    )


def test_tcp_nodelay_default_value():
    """Test that tcp_nodelay defaults to None (not set)."""
    standalone_config = AdvancedGlideClientConfiguration()
    assert standalone_config.tcp_nodelay is None

    cluster_config = AdvancedGlideClusterClientConfiguration()
    assert cluster_config.tcp_nodelay is None


def test_tcp_nodelay_in_protobuf_request():
    """Test that tcp_nodelay is correctly set in protobuf request."""
    # Test with True
    config_true = GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
        advanced_config=AdvancedGlideClientConfiguration(tcp_nodelay=True),
    )
    request_true = config_true._create_a_protobuf_conn_request()
    assert request_true.tcp_nodelay is True

    # Test with False
    config_false = GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
        advanced_config=AdvancedGlideClientConfiguration(tcp_nodelay=False),
    )
    request_false = config_false._create_a_protobuf_conn_request()
    assert request_false.tcp_nodelay is False

    # Test default (None - not set in protobuf)
    config_default = GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
        advanced_config=AdvancedGlideClientConfiguration(),
    )
    request_default = config_default._create_a_protobuf_conn_request()
    assert not request_default.HasField("tcp_nodelay")
