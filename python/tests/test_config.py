# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os

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
    # No cert/key: construction succeeds.
    AdvancedBaseClientConfiguration(tls_config=TlsAdvancedConfiguration())

    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(client_cert_pem=b"nonempty", client_key_pem=None)
    assert "client_cert_pem is provided but client_key_pem is not provided" in str(
        exc_info.value
    )

    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(client_cert_pem=None, client_key_pem=b"nonempty")
    assert "client_key_pem is provided but client_cert_pem is not provided" in str(
        exc_info.value
    )


# -------- Path-based mTLS with automatic client cert/key reload --------


def _write_cert_key(tmp_path):
    cert_path = tmp_path / "client-cert.pem"
    key_path = tmp_path / "client-key.pem"
    cert_path.write_bytes(TEST_CLIENT_CERT_DATA)
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    return cert_path, key_path


def test_tls_path_based_mtls_defaults_to_core_reload(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration(
        client_cert_path=str(cert_path),
        client_key_path=str(key_path),
    )

    for build in (_build_standalone_config, _build_cluster_config):
        config = build(tls_config)
        request = (
            config._create_a_protobuf_conn_request(cluster_mode=True)
            if build is _build_cluster_config
            else config._create_a_protobuf_conn_request()
        )
        assert request.client_cert_path == str(cert_path)
        assert request.client_key_path == str(key_path)
        assert request.HasField("cert_reload")
        assert request.cert_reload.enabled is True
        assert not request.cert_reload.HasField("interval_seconds")
        # Path-based must NOT populate byte-based fields.
        assert request.client_cert == b""
        assert request.client_key == b""


def test_tls_path_based_mtls_with_explicit_interval(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration(
        client_cert_path=str(cert_path),
        client_key_path=str(key_path),
        cert_reload_interval_seconds=120,
    )
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert request.HasField("cert_reload")
    assert request.cert_reload.enabled is True
    assert request.cert_reload.HasField("interval_seconds")
    assert request.cert_reload.interval_seconds == 120


def test_tls_path_based_mtls_accepts_pathlib_and_stores_str(tmp_path):
    from pathlib import Path

    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration(
        client_cert_path=Path(cert_path),
        client_key_path=Path(key_path),
    )
    assert isinstance(tls_config.client_cert_path, str)
    assert isinstance(tls_config.client_key_path, str)
    assert tls_config.client_cert_path == str(cert_path)
    assert tls_config.client_key_path == str(key_path)


def test_tls_path_based_mtls_missing_key_path(tmp_path):
    cert_path, _ = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(client_cert_path=str(cert_path))
    assert "must be provided together" in str(exc_info.value)


def test_tls_path_based_mtls_missing_cert_path(tmp_path):
    _, key_path = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(client_key_path=str(key_path))
    assert "must be provided together" in str(exc_info.value)


def test_tls_path_based_and_byte_based_mutually_exclusive(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(
            client_cert_pem=TEST_CLIENT_CERT_DATA,
            client_key_pem=TEST_CLIENT_KEY_DATA,
            client_cert_path=str(cert_path),
            client_key_path=str(key_path),
        )
    assert "mutually exclusive" in str(exc_info.value)


def test_tls_cert_reload_interval_requires_paths():
    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(cert_reload_interval_seconds=60)
    assert "may only be set when path-based mTLS is configured" in str(exc_info.value)


def test_tls_cert_reload_interval_rejects_zero_and_negative(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    for bad in (0, -1, -100):
        with pytest.raises(ConfigurationError):
            TlsAdvancedConfiguration(
                client_cert_path=str(cert_path),
                client_key_path=str(key_path),
                cert_reload_interval_seconds=bad,
            )


def test_tls_cert_reload_interval_rejects_bool_and_float(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    for bad in (True, False, 1.0, 30.5):
        with pytest.raises(ConfigurationError):
            TlsAdvancedConfiguration(
                client_cert_path=str(cert_path),
                client_key_path=str(key_path),
                cert_reload_interval_seconds=bad,
            )


def test_tls_cert_reload_interval_rejects_values_exceeding_uint32(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    # 2**32 is exactly one past the uint32 max and must be rejected;
    # 2**63 - 1 is grossly too large and must also be rejected.
    for bad in (2**32, 2**63 - 1):
        with pytest.raises(ConfigurationError) as exc_info:
            TlsAdvancedConfiguration(
                client_cert_path=str(cert_path),
                client_key_path=str(key_path),
                cert_reload_interval_seconds=bad,
            )
        assert "unsigned 32-bit" in str(exc_info.value)

    # 2**32 - 1 is the maximum valid uint32 value and must be accepted.
    tls_config = TlsAdvancedConfiguration(
        client_cert_path=str(cert_path),
        client_key_path=str(key_path),
        cert_reload_interval_seconds=2**32 - 1,
    )
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()
    assert request.HasField("cert_reload")
    assert request.cert_reload.enabled is True
    assert request.cert_reload.HasField("interval_seconds")
    assert request.cert_reload.interval_seconds == 2**32 - 1


@pytest.mark.skipif(
    os.name == "nt" or os.geteuid() == 0,
    reason="chmod 0o000 does not restrict root or Windows",
)
def test_tls_path_based_mtls_unreadable_file_rejected(tmp_path):
    cert_path = tmp_path / "client-cert.pem"
    key_path = tmp_path / "client-key.pem"
    cert_path.write_bytes(TEST_CLIENT_CERT_DATA)
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    os.chmod(cert_path, 0o000)
    try:
        with pytest.raises(ConfigurationError) as exc_info:
            TlsAdvancedConfiguration(
                client_cert_path=str(cert_path),
                client_key_path=str(key_path),
            )
        assert "not readable" in str(exc_info.value) or "PermissionError" in str(
            exc_info.value
        )
    finally:
        os.chmod(cert_path, 0o600)


def test_tls_path_based_mtls_nonexistent_path_raises_file_not_found(tmp_path):
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    missing_cert = tmp_path / "does-not-exist.pem"
    with pytest.raises(FileNotFoundError) as exc_info:
        TlsAdvancedConfiguration(
            client_cert_path=str(missing_cert),
            client_key_path=str(key_path),
        )
    assert str(missing_cert) in str(exc_info.value)


def test_tls_path_based_mtls_directory_as_path_rejected(tmp_path):
    """Passing an existing directory raises `not a regular file`,
    distinct from the missing-file `FileNotFoundError`."""
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    dir_as_cert = tmp_path / "certs"
    dir_as_cert.mkdir()
    with pytest.raises(ConfigurationError, match="not a regular file"):
        TlsAdvancedConfiguration(
            client_cert_path=str(dir_as_cert),
            client_key_path=str(key_path),
        )


def test_tls_path_based_mtls_empty_string_path_rejected(tmp_path):
    cert_path, _ = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError):
        TlsAdvancedConfiguration(
            client_cert_path=str(cert_path),
            client_key_path="",
        )


def test_tls_path_based_mtls_empty_file_rejected(tmp_path):
    cert_path = tmp_path / "client-cert.pem"
    cert_path.write_bytes(b"")
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    with pytest.raises(ConfigurationError) as exc_info:
        TlsAdvancedConfiguration(
            client_cert_path=str(cert_path),
            client_key_path=str(key_path),
        )
    assert "empty" in str(exc_info.value)


def test_tls_with_mtls_pem_factory_ok():
    tls_config = TlsAdvancedConfiguration.with_mtls_pem(
        TEST_CLIENT_CERT_DATA,
        TEST_CLIENT_KEY_DATA,
        root_pem_cacerts=TEST_CERT_DATA_1,
    )
    assert tls_config.client_cert_pem == TEST_CLIENT_CERT_DATA
    assert tls_config.client_key_pem == TEST_CLIENT_KEY_DATA
    assert tls_config.root_pem_cacerts == TEST_CERT_DATA_1
    assert tls_config.client_cert_path is None
    assert tls_config.client_key_path is None
    assert tls_config.cert_reload_interval_seconds is None

    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()
    assert request.client_cert == TEST_CLIENT_CERT_DATA
    assert request.client_key == TEST_CLIENT_KEY_DATA
    assert not request.HasField("cert_reload")


def test_tls_with_mtls_reload_factory_ok(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration.with_mtls_reload(
        cert_path,
        key_path,
        cert_reload_interval_seconds=90,
        root_pem_cacerts=TEST_CERT_DATA_1,
    )
    assert tls_config.client_cert_path == str(cert_path)
    assert tls_config.client_key_path == str(key_path)
    assert tls_config.cert_reload_interval_seconds == 90
    assert tls_config.root_pem_cacerts == TEST_CERT_DATA_1
    assert tls_config.client_cert_pem is None
    assert tls_config.client_key_pem is None

    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()
    assert request.client_cert_path == str(cert_path)
    assert request.client_key_path == str(key_path)
    assert request.cert_reload.enabled is True
    assert request.cert_reload.interval_seconds == 90


def test_tls_with_mtls_reload_defaults_to_core_reload(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration.with_mtls_reload(cert_path, key_path)
    assert tls_config.cert_reload_interval_seconds is None

    request = _build_standalone_config(tls_config)._create_a_protobuf_conn_request()
    assert request.cert_reload.enabled is True
    assert not request.cert_reload.HasField("interval_seconds")


def test_tls_with_mtls_reload_missing_file(tmp_path):
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    missing_cert = tmp_path / "does-not-exist.pem"
    with pytest.raises(FileNotFoundError):
        TlsAdvancedConfiguration.with_mtls_reload(missing_cert, key_path)


def test_load_client_certificate_and_key_from_file_success(tmp_path):
    from glide_shared.config import load_client_certificate_and_key_from_file

    cert_path = tmp_path / "client-cert.pem"
    key_path = tmp_path / "client-key.pem"
    cert_path.write_bytes(TEST_CLIENT_CERT_DATA)
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)

    cert, key = load_client_certificate_and_key_from_file(cert_path, key_path)
    assert cert == TEST_CLIENT_CERT_DATA
    assert key == TEST_CLIENT_KEY_DATA


def test_load_client_certificate_and_key_from_file_missing_cert(tmp_path):
    from glide_shared.config import load_client_certificate_and_key_from_file

    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    with pytest.raises(FileNotFoundError) as exc_info:
        load_client_certificate_and_key_from_file(
            tmp_path / "missing-cert.pem", key_path
        )
    assert "Client certificate file not found" in str(exc_info.value)


def test_load_client_certificate_and_key_from_file_empty_key(tmp_path):
    from glide_shared.config import load_client_certificate_and_key_from_file

    cert_path = tmp_path / "client-cert.pem"
    cert_path.write_bytes(TEST_CLIENT_CERT_DATA)
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(b"")
    with pytest.raises(ConfigurationError) as exc_info:
        load_client_certificate_and_key_from_file(cert_path, key_path)
    assert "Client key file is empty" in str(exc_info.value)


def test_tls_byte_based_still_emits_no_reload_config():
    """Byte-based mTLS remains static: no cert_reload field on the request."""
    tls_config = TlsAdvancedConfiguration(
        client_cert_pem=TEST_CLIENT_CERT_DATA,
        client_key_pem=TEST_CLIENT_KEY_DATA,
    )
    config = _build_standalone_config(tls_config)
    request = config._create_a_protobuf_conn_request()

    assert request.client_cert == TEST_CLIENT_CERT_DATA
    assert request.client_key == TEST_CLIENT_KEY_DATA
    assert not request.HasField("cert_reload")
    assert request.client_cert_path == ""
    assert request.client_key_path == ""


# -------- wire-time revalidation guards against post-construction mutation --------


def test_tls_wire_time_revalidation_rejects_empty_cert_after_mutation():
    """Mutating client_cert_pem to empty bytes after construction is
    caught at wire-emit time; the request is not built with a silent
    "no mTLS" downgrade."""
    tls_config = TlsAdvancedConfiguration(
        client_cert_pem=TEST_CLIENT_CERT_DATA,
        client_key_pem=TEST_CLIENT_KEY_DATA,
    )
    tls_config.client_cert_pem = b""

    config = _build_standalone_config(tls_config)
    with pytest.raises(ConfigurationError, match="client_cert_pem must not be empty"):
        config._create_a_protobuf_conn_request()


def test_tls_wire_time_revalidation_rejects_mixed_after_mutation(tmp_path):
    """Mutating a byte-based config to add paths after construction is
    caught at wire-emit time."""
    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration(
        client_cert_pem=TEST_CLIENT_CERT_DATA,
        client_key_pem=TEST_CLIENT_KEY_DATA,
    )
    tls_config.client_cert_path = str(cert_path)
    tls_config.client_key_path = str(key_path)

    config = _build_standalone_config(tls_config)
    with pytest.raises(ConfigurationError, match="mutually exclusive"):
        config._create_a_protobuf_conn_request()


# -------- with_mtls_pem factory: negative cases + forwarding --------


def test_tls_with_mtls_pem_factory_empty_cert():
    with pytest.raises(ConfigurationError, match="client_cert_pem"):
        TlsAdvancedConfiguration.with_mtls_pem(b"", TEST_CLIENT_KEY_DATA)


def test_tls_with_mtls_pem_factory_empty_key():
    with pytest.raises(ConfigurationError, match="client_key_pem"):
        TlsAdvancedConfiguration.with_mtls_pem(TEST_CLIENT_CERT_DATA, b"")


def test_tls_with_mtls_pem_factory_forwards_use_insecure_tls():
    tls_config = TlsAdvancedConfiguration.with_mtls_pem(
        TEST_CLIENT_CERT_DATA,
        TEST_CLIENT_KEY_DATA,
        use_insecure_tls=True,
    )
    assert tls_config.use_insecure_tls is True

    request = _build_standalone_config(tls_config)._create_a_protobuf_conn_request()
    assert request.tls_mode == TlsMode.InsecureTls
    assert request.client_cert == TEST_CLIENT_CERT_DATA
    assert request.client_key == TEST_CLIENT_KEY_DATA


# -------- with_mtls_reload factory: negative cases + forwarding --------


def test_tls_with_mtls_reload_factory_zero_interval_rejected(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    with pytest.raises(
        ConfigurationError, match="cert_reload_interval_seconds.*positive"
    ):
        TlsAdvancedConfiguration.with_mtls_reload(
            cert_path,
            key_path,
            cert_reload_interval_seconds=0,
        )


def test_tls_with_mtls_reload_factory_negative_interval_rejected(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    with pytest.raises(
        ConfigurationError, match="cert_reload_interval_seconds.*positive"
    ):
        TlsAdvancedConfiguration.with_mtls_reload(
            cert_path,
            key_path,
            cert_reload_interval_seconds=-1,
        )


def test_tls_with_mtls_reload_factory_bool_interval_rejected(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError, match="cert_reload_interval_seconds"):
        TlsAdvancedConfiguration.with_mtls_reload(
            cert_path,
            key_path,
            cert_reload_interval_seconds=True,
        )


def test_tls_with_mtls_reload_factory_float_interval_rejected(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError, match="cert_reload_interval_seconds"):
        TlsAdvancedConfiguration.with_mtls_reload(
            cert_path,
            key_path,
            cert_reload_interval_seconds=1.5,
        )


def test_tls_with_mtls_reload_factory_uint32_overflow_rejected(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError, match="unsigned 32-bit"):
        TlsAdvancedConfiguration.with_mtls_reload(
            cert_path,
            key_path,
            cert_reload_interval_seconds=2**32,
        )


def test_tls_with_mtls_reload_factory_empty_cert_path(tmp_path):
    _, key_path = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError, match="client_cert_path"):
        TlsAdvancedConfiguration.with_mtls_reload("", key_path)


def test_tls_with_mtls_reload_factory_empty_key_path(tmp_path):
    cert_path, _ = _write_cert_key(tmp_path)
    with pytest.raises(ConfigurationError, match="client_key_path"):
        TlsAdvancedConfiguration.with_mtls_reload(cert_path, "")


def test_tls_with_mtls_reload_factory_accepts_pathlib_path(tmp_path):
    from pathlib import Path

    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration.with_mtls_reload(
        Path(cert_path), Path(key_path)
    )
    assert isinstance(tls_config.client_cert_path, str)
    assert isinstance(tls_config.client_key_path, str)
    assert tls_config.client_cert_path == str(cert_path)
    assert tls_config.client_key_path == str(key_path)

    request = _build_standalone_config(tls_config)._create_a_protobuf_conn_request()
    assert request.client_cert_path == str(cert_path)
    assert request.client_key_path == str(key_path)
    assert request.cert_reload.enabled is True


def test_tls_with_mtls_reload_factory_forwards_root_pem_cacerts(tmp_path):
    cert_path, key_path = _write_cert_key(tmp_path)
    tls_config = TlsAdvancedConfiguration.with_mtls_reload(
        cert_path,
        key_path,
        root_pem_cacerts=TEST_CERT_DATA_1,
    )
    assert tls_config.root_pem_cacerts == TEST_CERT_DATA_1

    request = _build_standalone_config(tls_config)._create_a_protobuf_conn_request()
    assert len(request.root_certs) == 1
    assert request.root_certs[0] == TEST_CERT_DATA_1


# -------- load_client_certificate_and_key_from_file: negative cases --------


def test_load_client_certificate_and_key_from_file_missing_key(tmp_path):
    from glide_shared.config import load_client_certificate_and_key_from_file

    cert_path = tmp_path / "client-cert.pem"
    cert_path.write_bytes(TEST_CLIENT_CERT_DATA)
    missing_key = tmp_path / "missing-key.pem"
    with pytest.raises(FileNotFoundError) as exc_info:
        load_client_certificate_and_key_from_file(cert_path, missing_key)
    assert "Client key file not found" in str(exc_info.value)
    assert str(missing_key) in str(exc_info.value)


def test_load_client_certificate_and_key_from_file_empty_cert(tmp_path):
    from glide_shared.config import load_client_certificate_and_key_from_file

    cert_path = tmp_path / "client-cert.pem"
    cert_path.write_bytes(b"")
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    with pytest.raises(ConfigurationError) as exc_info:
        load_client_certificate_and_key_from_file(cert_path, key_path)
    assert "Client certificate file is empty" in str(exc_info.value)
    assert str(cert_path) in str(exc_info.value)


@pytest.mark.skipif(
    os.name == "nt" or os.geteuid() == 0,
    reason="chmod 0o000 does not restrict root or Windows",
)
def test_load_client_certificate_and_key_from_file_unreadable_key(tmp_path):
    from glide_shared.config import load_client_certificate_and_key_from_file

    cert_path = tmp_path / "client-cert.pem"
    cert_path.write_bytes(TEST_CLIENT_CERT_DATA)
    key_path = tmp_path / "client-key.pem"
    key_path.write_bytes(TEST_CLIENT_KEY_DATA)
    os.chmod(key_path, 0o000)
    try:
        with pytest.raises(ConfigurationError) as exc_info:
            load_client_certificate_and_key_from_file(cert_path, key_path)
        assert "client key" in str(exc_info.value).lower()
        assert str(key_path) in str(exc_info.value)
    finally:
        os.chmod(key_path, 0o600)


# -------- recovered pre-reshape coverage: empty byte PEM inputs --------


def test_tls_client_cert_pem_empty_bytes_rejected():
    """Empty client_cert_pem bytes must be rejected at construction."""
    with pytest.raises(ConfigurationError, match="client_cert_pem must not be empty"):
        TlsAdvancedConfiguration(
            client_cert_pem=b"",
            client_key_pem=TEST_CLIENT_KEY_DATA,
        )


def test_tls_client_key_pem_empty_bytes_rejected():
    """Empty client_key_pem bytes must be rejected at construction."""
    with pytest.raises(ConfigurationError, match="client_key_pem must not be empty"):
        TlsAdvancedConfiguration(
            client_cert_pem=TEST_CLIENT_CERT_DATA,
            client_key_pem=b"",
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
