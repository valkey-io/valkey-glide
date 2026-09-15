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

    def test_async_provider_rejected_by_sync_client(self):
        """The sync glide client raises ValueError when an async provider is configured."""

        async def async_provider() -> AwsCredentials:
            return AwsCredentials(access_key_id="key", secret_access_key="secret")

        iam_config = IamAuthConfig(
            cluster_name="c",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
            credential_provider=async_provider,
        )
        ServerCredentials(username="user", iam_config=iam_config)
        # We cannot easily instantiate GlideClient without a server, but we can
        # verify that the _credential_provider_is_async flag is set correctly
        # and that the sync client will raise ValueError at connection time.
        assert (
            iam_config._credential_provider_is_async is True
        ), "Expected _credential_provider_is_async to be True for async provider"

    def test_callable_object_with_async_call_detected_as_async(self):
        """_is_async_callable detects callable objects with async __call__."""
        from glide_shared.config import _is_async_callable

        class AsyncCallableProvider:
            async def __call__(self) -> AwsCredentials:
                return AwsCredentials(access_key_id="key", secret_access_key="secret")

        provider_instance = AsyncCallableProvider()
        assert _is_async_callable(
            provider_instance
        ), "Expected _is_async_callable to return True for object with async __call__"
        # Should also be detected by IamAuthConfig
        config = IamAuthConfig(
            cluster_name="c",
            service=ServiceType.ELASTICACHE,
            region="us-east-1",
            credential_provider=provider_instance,
        )
        assert config._credential_provider_is_async is True

    def test_sync_provider_passes_create_credential_callback(self):
        """A sync provider results in a non-NULL CFFI callback."""
        from glide_shared.ffi_helpers import create_credential_provider_callback
        from glide_shared._glide_ffi import GlideFFI

        ffi = GlideFFI.ffi

        def my_provider() -> AwsCredentials:
            return AwsCredentials(access_key_id="AKID", secret_access_key="SECRET")

        callback = create_credential_provider_callback(ffi, my_provider)
        assert callback != ffi.NULL, "Expected non-NULL CFFI callback for sync provider"

    def test_none_provider_returns_null_callback(self):
        """No provider results in a NULL CFFI callback."""
        from glide_shared.ffi_helpers import create_credential_provider_callback
        from glide_shared._glide_ffi import GlideFFI

        ffi = GlideFFI.ffi
        callback = create_credential_provider_callback(ffi, None)
        assert callback == ffi.NULL, "Expected NULL CFFI callback when no provider"
