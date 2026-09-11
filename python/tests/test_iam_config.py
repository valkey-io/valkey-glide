# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide_shared.config import (
    AwsCredentials,
    IamAuthConfig,
    ServerCredentials,
    ServiceType,
)
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


class TestAwsCredentials:
    def test_valid_long_term_credentials(self):
        creds = AwsCredentials(
            access_key_id="AKIAIOSFODNN7EXAMPLE",
            secret_access_key="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        )
        assert creds.access_key_id == "AKIAIOSFODNN7EXAMPLE"
        assert creds.session_token is None
        assert creds.expires_at_epoch_millis is None

    def test_valid_session_credentials(self):
        creds = AwsCredentials(
            access_key_id="ASIA...",
            secret_access_key="secret",
            session_token="token",
            expires_at_epoch_millis=9999999999000,
        )
        assert creds.session_token == "token"
        assert creds.expires_at_epoch_millis == 9999999999000

    def test_blank_access_key_id_raises(self):
        with pytest.raises(ValueError, match="access_key_id"):
            AwsCredentials(access_key_id="", secret_access_key="secret")

    def test_whitespace_access_key_id_raises(self):
        with pytest.raises(ValueError, match="access_key_id"):
            AwsCredentials(access_key_id="   ", secret_access_key="secret")

    def test_blank_secret_raises(self):
        with pytest.raises(ValueError, match="secret_access_key"):
            AwsCredentials(access_key_id="key", secret_access_key="")

    def test_negative_expires_at_raises(self):
        with pytest.raises(ValueError, match="expires_at_epoch_millis"):
            AwsCredentials(
                access_key_id="key",
                secret_access_key="secret",
                expires_at_epoch_millis=-1,
            )


class TestGlideCredentialProvider:
    def test_valid_provider_accepted(self):
        def my_provider() -> AwsCredentials:
            return AwsCredentials(access_key_id="key", secret_access_key="secret")

        config = IamAuthConfig(
            cluster_name="c",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
            credential_provider=my_provider,
        )
        assert config.credential_provider is my_provider

    def test_async_provider_accepted_in_config(self):
        """Async providers are accepted at config time; the async client bridges them."""

        async def async_provider() -> AwsCredentials:
            return AwsCredentials(access_key_id="key", secret_access_key="secret")

        # Should NOT raise -- async providers are now supported in the async client
        config = IamAuthConfig(
            cluster_name="c",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
            credential_provider=async_provider,
        )
        assert config.credential_provider is async_provider

    def test_non_callable_raises(self):
        with pytest.raises(ValueError, match="callable"):
            IamAuthConfig(
                cluster_name="c",
                service=ServiceType.ELASTICACHE,
                region="us-east-1",
                credential_provider="not_a_function",  # type: ignore
            )
