# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import warnings
from types import SimpleNamespace
from unittest.mock import MagicMock

import anyio
import pytest
import sniffio
from glide_shared._glide_ffi import GlideFFI
from glide_shared.config import (
    AwsCredentials,
    GlideClientConfiguration,
    IamAuthConfig,
    NodeAddress,
    ServerCredentials,
    ServiceType,
    _is_async_callable,
)
from glide_shared.ffi_helpers import create_credential_provider_callback


def _invoke_callback(callback, capacities=(64, 64, 64), fill=0xA5):
    ffi = GlideFFI.ffi
    buffers = [ffi.new("uint8_t[]", max(capacity, 1)) for capacity in capacities]
    for buffer, capacity in zip(buffers, capacities):
        if capacity:
            ffi.buffer(buffer, capacity)[:] = bytes([fill]) * capacity
    lengths = [ffi.new("size_t*", 777 + index) for index in range(3)]
    expiry = ffi.new("int64_t*", 888)
    status = callback(
        17,
        buffers[0],
        capacities[0],
        lengths[0],
        buffers[1],
        capacities[1],
        lengths[1],
        buffers[2],
        capacities[2],
        lengths[2],
        expiry,
    )
    return (
        status,
        tuple(length[0] for length in lengths),
        expiry[0],
        tuple(
            bytes(ffi.buffer(buffer, capacity))
            for buffer, capacity in zip(buffers, capacities)
        ),
    )


def _iam_config(provider=None):
    return IamAuthConfig(
        cluster_name="cluster",
        service=ServiceType.ELASTICACHE,
        region="us-east-1",
        credential_provider=provider,
    )


class TestAwsCredentials:
    def test_required_fields_and_optional_values(self):
        credentials = AwsCredentials(
            "access",
            "secret",
            session_token="token",
            expires_at_epoch_millis=2**63 - 1,
        )
        assert credentials.access_key_id == "access"
        assert credentials.secret_access_key == "secret"
        assert credentials.session_token == "token"
        assert credentials.expires_at_epoch_millis == 2**63 - 1

    @pytest.mark.parametrize("value", ["", " ", "\t\n", None, b"access"])
    def test_access_key_must_be_nonblank_string(self, value):
        with pytest.raises(ValueError, match="access_key_id"):
            AwsCredentials(value, "secret")  # type: ignore[arg-type]

    @pytest.mark.parametrize("value", ["", " ", "\t\n", None, b"secret"])
    def test_secret_key_must_be_nonblank_string(self, value):
        with pytest.raises(ValueError, match="secret_access_key"):
            AwsCredentials("access", value)  # type: ignore[arg-type]

    @pytest.mark.parametrize("value", [-1, 2**63, 1.5, True, "1"])
    def test_expiry_must_fit_nonnegative_int64(self, value):
        with pytest.raises(ValueError, match="signed 64-bit"):
            AwsCredentials("access", "secret", expires_at_epoch_millis=value)  # type: ignore[arg-type]


class TestCredentialProviderConfigAndExports:
    def test_sync_async_and_async_callable_object_detection(self):
        def sync_provider():
            return AwsCredentials("access", "secret")

        async def async_provider():
            return AwsCredentials("access", "secret")

        class AsyncProvider:
            async def __call__(self):
                return AwsCredentials("access", "secret")

        assert not _is_async_callable(sync_provider)
        assert _is_async_callable(async_provider)
        assert _is_async_callable(AsyncProvider())
        assert not _iam_config(sync_provider)._credential_provider_is_async
        assert _iam_config(async_provider)._credential_provider_is_async
        assert _iam_config(AsyncProvider())._credential_provider_is_async

    def test_non_callable_provider_is_rejected(self):
        with pytest.raises(ValueError, match="callable"):
            _iam_config("not callable")  # type: ignore[arg-type]

    def test_public_packages_export_credential_api(self):
        import glide
        import glide_shared
        import glide_sync

        for package in (glide, glide_shared, glide_sync):
            assert package.AwsCredentials is AwsCredentials
            assert "AwsCredentials" in package.__all__
            assert "GlideCredentialProvider" in package.__all__


class TestCredentialProviderCallback:
    def test_typed_null_for_missing_provider(self):
        ffi = GlideFFI.ffi
        callback = create_credential_provider_callback(ffi, None)
        assert callback == ffi.NULL
        assert ffi.typeof(callback) == ffi.typeof("CredentialProviderCallback")

    def test_success_writes_exact_utf8_sizes_and_optional_fields(self):
        credentials = AwsCredentials(
            "accéss", "secret", session_token=None, expires_at_epoch_millis=None
        )
        callback = create_credential_provider_callback(
            GlideFFI.ffi, lambda: credentials
        )
        status, lengths, expiry, buffers = _invoke_callback(callback)

        assert status == 1
        assert lengths == (len("accéss".encode()), 6, 0)
        assert expiry == 0
        assert buffers[0][: lengths[0]] == "accéss".encode()
        assert buffers[1][: lengths[1]] == b"secret"
        assert buffers[2] == b"\xa5" * 64

    def test_insufficient_capacity_sets_lengths_without_writes_or_expiry(self):
        token = "t" * 8192
        callback = create_credential_provider_callback(
            GlideFFI.ffi,
            lambda: AwsCredentials("access", "secret", token, 123456),
        )
        status, lengths, expiry, buffers = _invoke_callback(
            callback, capacities=(4, 5, 2048)
        )

        assert status == 2
        assert lengths == (6, 6, 8192)
        assert expiry == 888
        assert buffers == (b"\xa5" * 4, b"\xa5" * 5, b"\xa5" * 2048)

    def test_large_negotiation_is_stateless_and_calls_provider_twice(self):
        calls = 0

        def provider():
            nonlocal calls
            calls += 1
            return AwsCredentials("access", "secret", "t" * 8192)

        callback = create_credential_provider_callback(GlideFFI.ffi, provider)
        assert _invoke_callback(callback, capacities=(2048, 2048, 2048))[0] == 2
        status, lengths, _, buffers = _invoke_callback(
            callback, capacities=(1024 * 1024,) * 3
        )
        assert status == 1
        assert calls == 2
        assert lengths == (6, 6, 8192)
        assert buffers[2][:8192] == b"t" * 8192

    @pytest.mark.parametrize(
        "mutator",
        [
            lambda credentials: setattr(credentials, "access_key_id", " "),
            lambda credentials: setattr(credentials, "secret_access_key", ""),
            lambda credentials: setattr(credentials, "expires_at_epoch_millis", 2**63),
            lambda credentials: setattr(
                credentials, "session_token", "t" * (1024 * 1024)
            ),
        ],
    )
    def test_invalid_mutated_result_fails_without_writes(self, mutator):
        credentials = AwsCredentials("access", "secret")
        mutator(credentials)
        callback = create_credential_provider_callback(
            GlideFFI.ffi, lambda: credentials
        )
        status, lengths, expiry, buffers = _invoke_callback(callback)
        assert status == 0
        assert lengths == (777, 778, 779)
        assert expiry == 888
        assert buffers == (b"\xa5" * 64,) * 3

    def test_provider_error_and_wrong_result_type_fail(self):
        def failing_provider():
            raise RuntimeError("provider unavailable")

        for provider in (failing_provider, lambda: object()):
            callback = create_credential_provider_callback(GlideFFI.ffi, provider)
            assert _invoke_callback(callback)[0] == 0

    def test_sync_callback_disposes_awaitable_result(self):
        async def result():
            return AwsCredentials("access", "secret")

        def provider():
            return result()

        callback = create_credential_provider_callback(GlideFFI.ffi, provider)
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            assert _invoke_callback(callback)[0] == 0
            gc.collect()
        assert not any("was never awaited" in str(item.message) for item in caught)


@pytest.mark.anyio
async def test_async_callback_bridges_async_callable_and_nominal_sync_awaitable():
    ffi = GlideFFI.ffi
    if sniffio.current_async_library() == "asyncio":
        event_loop = asyncio.get_running_loop()
        trio_token = None
    else:
        import trio

        event_loop = None
        trio_token = trio.lowlevel.current_trio_token()

    async def async_provider():
        await anyio.sleep(0)
        return AwsCredentials("async-access", "async-secret")

    def nominally_sync_provider():
        return async_provider()

    for provider in (async_provider, nominally_sync_provider):
        callback = create_credential_provider_callback(
            ffi,
            provider,
            event_loop=event_loop,
            trio_token=trio_token,
            allow_async=True,
        )
        status, lengths, _, buffers = await anyio.to_thread.run_sync(
            _invoke_callback, callback
        )
        assert status == 1
        assert buffers[0][: lengths[0]] == b"async-access"
        assert buffers[1][: lengths[1]] == b"async-secret"


class _FakeNativeLibrary:
    def __init__(self, ffi, native_library):
        self.noop_success_callback = native_library.noop_success_callback
        self.noop_failure_callback = native_library.noop_failure_callback
        self.create_client = MagicMock()
        self.free_connection_response = MagicMock()
        self.close_client = MagicMock()
        self._response = ffi.new(
            "ConnectionResponse*",
            {
                "conn_ptr": ffi.cast("void*", 1),
                "connection_error_message": ffi.NULL,
            },
        )
        self.create_client.return_value = self._response


@pytest.mark.anyio
async def test_async_direct_client_passes_and_retains_callback(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(
        async_client_module.BaseClient, "_setup_pipe", lambda self: None
    )

    def provider():
        return AwsCredentials("access", "secret")

    config = GlideClientConfiguration(
        addresses=[NodeAddress()],
        credentials=ServerCredentials(
            username="user", iam_config=_iam_config(provider)
        ),
    )
    client = await async_client_module.GlideClient.create(config)
    callback_arg = fake_lib.create_client.call_args.args[5]
    assert callback_arg != ffi.NULL
    assert client._credential_provider_callback_ref is callback_arg
    await client.close()


@pytest.mark.anyio
async def test_async_direct_client_passes_typed_null_without_provider(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(
        async_client_module.BaseClient, "_setup_pipe", lambda self: None
    )

    client = await async_client_module.GlideClient.create(
        GlideClientConfiguration(addresses=[NodeAddress()])
    )
    callback_arg = fake_lib.create_client.call_args.args[5]
    assert callback_arg == ffi.NULL
    assert ffi.typeof(callback_arg) == ffi.typeof("CredentialProviderCallback")
    await client.close()


def test_sync_direct_rejects_async_callable_before_native_creation(monkeypatch):
    import glide_sync.glide_client as sync_client_module

    async def provider():
        return AwsCredentials("access", "secret")

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        sync_client_module, "_SYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    config = GlideClientConfiguration(
        addresses=[NodeAddress()],
        credentials=ServerCredentials(
            username="user", iam_config=_iam_config(provider)
        ),
    )
    with pytest.raises(ValueError, match="does not support async"):
        sync_client_module.GlideClient.create(config)
    fake_lib.create_client.assert_not_called()


def test_sync_direct_passes_and_retains_callback(monkeypatch):
    import glide_sync.glide_client as sync_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        sync_client_module, "_SYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )

    def provider():
        return AwsCredentials("access", "secret")

    config = GlideClientConfiguration(
        addresses=[NodeAddress()],
        credentials=ServerCredentials(
            username="user", iam_config=_iam_config(provider)
        ),
    )
    client = sync_client_module.GlideClient.create(config)
    callback_arg = fake_lib.create_client.call_args.args[5]
    assert callback_arg != ffi.NULL
    assert client._credential_provider_callback_ref is callback_arg
    client.close()


def test_sync_direct_passes_typed_null_without_provider(monkeypatch):
    import glide_sync.glide_client as sync_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        sync_client_module, "_SYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    client = sync_client_module.GlideClient.create(
        GlideClientConfiguration(addresses=[NodeAddress()])
    )
    callback_arg = fake_lib.create_client.call_args.args[5]
    assert callback_arg == ffi.NULL
    assert ffi.typeof(callback_arg) == ffi.typeof("CredentialProviderCallback")
    client.close()
