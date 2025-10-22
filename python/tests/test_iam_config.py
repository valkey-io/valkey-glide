# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide_shared.config import IamAuthConfig, ServerCredentials, ServiceType
from glide_shared.exceptions import ConfigurationError


class TestIamAuthConfig:
    def test_iam_auth_config_elasticache(self):
        """Test IAM config creation for ElastiCache."""
        iam_config = IamAuthConfig(
            cluster_name="my-cluster",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
        )

        assert iam_config.cluster_name == "my-cluster"
        assert iam_config.service == ServiceType.ELASTICACHE
        assert iam_config.region == "us-east-1"
        assert iam_config.refresh_interval_seconds is None  # Core will use default

    def test_iam_auth_config_memorydb(self):
        """Test IAM config creation for MemoryDB."""
        iam_config = IamAuthConfig(
            cluster_name="my-cluster",
            service=ServiceType.MEMORYDB,
            region="us-west-2",
        )

        assert iam_config.service == ServiceType.MEMORYDB
        assert iam_config.region == "us-west-2"

    def test_iam_auth_config_custom_refresh(self):
        """Test IAM config with custom refresh interval."""
        iam_config = IamAuthConfig(
            cluster_name="my-cluster",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
            refresh_interval_seconds=600,
        )

        assert iam_config.refresh_interval_seconds == 600


class TestServerCredentialsWithIam:
    def test_server_credentials_with_iam(self):
        """Test creating server credentials with IAM config."""
        iam_config = IamAuthConfig(
            cluster_name="my-cluster",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
        )

        credentials = ServerCredentials(username="myUser", iam_config=iam_config)

        assert credentials.username == "myUser"
        assert credentials.password is None
        assert credentials.iam_config is not None
        assert credentials.is_iam_auth() is True

    def test_server_credentials_password_only(self):
        """Test creating server credentials with password only."""
        credentials = ServerCredentials(password="myPassword")

        assert credentials.password == "myPassword"
        assert credentials.username is None
        assert credentials.iam_config is None
        assert credentials.is_iam_auth() is False

    def test_server_credentials_password_and_username(self):
        """Test creating server credentials with password and username."""
        credentials = ServerCredentials(password="myPassword", username="myUser")

        assert credentials.password == "myPassword"
        assert credentials.username == "myUser"
        assert credentials.iam_config is None
        assert credentials.is_iam_auth() is False

    def test_server_credentials_mutual_exclusivity(self):
        """Test that password and IAM config are mutually exclusive."""
        iam_config = IamAuthConfig(
            cluster_name="my-cluster",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
        )

        with pytest.raises(ConfigurationError) as exc_info:
            ServerCredentials(
                password="myPassword", username="myUser", iam_config=iam_config
            )

        assert "mutually exclusive" in str(exc_info.value)

    def test_server_credentials_iam_requires_username(self):
        """Test that IAM config requires username."""
        iam_config = IamAuthConfig(
            cluster_name="my-cluster",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
        )

        with pytest.raises(ConfigurationError) as exc_info:
            ServerCredentials(iam_config=iam_config)

        assert "username is required" in str(exc_info.value)

    def test_server_credentials_requires_auth_method(self):
        """Test that at least one authentication method is required."""
        with pytest.raises(ConfigurationError) as exc_info:
            ServerCredentials()

        assert "Either password or iam_config must be provided" in str(exc_info.value)

    def test_server_credentials_username_only_fails(self):
        """Test that username alone is not sufficient."""
        with pytest.raises(ConfigurationError) as exc_info:
            ServerCredentials(username="myUser")

        assert "Either password or iam_config must be provided" in str(exc_info.value)
