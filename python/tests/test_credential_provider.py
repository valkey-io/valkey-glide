# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import inspect
import threading
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
from glide_shared.exceptions import ClosingError, ConfigurationError
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
        self.create_monitor_client = MagicMock()
        self.free_connection_response = MagicMock()
        self.close_client = MagicMock()
        self.close_monitor_client = MagicMock()
        self.command_with_buffer = MagicMock()
        self._response = ffi.new(
            "ConnectionResponse*",
            {
                "conn_ptr": ffi.cast("void*", 1),
                "connection_error_message": ffi.NULL,
            },
        )
        self.create_client.return_value = self._response
        self.create_monitor_client.return_value = self._response


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
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    await client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert client._credential_provider_callback_ref is None


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
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert client._credential_provider_callback_ref is None


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


def _direct_client_config(provider=None):
    return GlideClientConfiguration(
        addresses=[NodeAddress()],
        credentials=ServerCredentials(
            username="user", iam_config=_iam_config(provider)
        ),
    )


async def _run_cancelled_async_create(monkeypatch, cancel_timing):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    native_entered = threading.Event()
    release_native = threading.Event()
    if cancel_timing == "before":
        release_native.set()

    def native_create(*args):
        native_entered.set()
        assert release_native.wait(timeout=5)
        return fake_lib._response

    fake_lib.create_client.side_effect = native_create
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(
        async_client_module.BaseClient, "_setup_pipe", lambda self: None
    )

    instances = []
    original_init = async_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        instances.append(self)

    monkeypatch.setattr(async_client_module.GlideClient, "__init__", capture_instance)
    cancelled = False
    cancel_scope = None

    async def create_client():
        nonlocal cancelled, cancel_scope
        with anyio.CancelScope() as scope:
            cancel_scope = scope
            if cancel_timing == "before":
                scope.cancel()
            try:
                await async_client_module.GlideClient.create(
                    _direct_client_config(lambda: AwsCredentials("access", "secret"))
                )
            except anyio.get_cancelled_exc_class():
                cancelled = True

    if cancel_timing == "before":
        await create_client()
    else:
        async with anyio.create_task_group() as task_group:
            task_group.start_soon(create_client)
            while not native_entered.is_set() or cancel_scope is None:
                await anyio.sleep(0)
            cancel_scope.cancel()
            release_native.set()

    assert cancelled
    assert len(instances) == 1
    instance = instances[0]
    assert instance._is_closed
    assert instance._core_client is None
    assert instance._credential_provider_callback_ref is None
    assert instance._address_resolver_callback_ref is None
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
@pytest.mark.parametrize("cancel_timing", ["before", "during"])
def test_async_create_cancellation_frees_native_ownership_once(
    monkeypatch, backend, cancel_timing
):
    anyio.run(
        _run_cancelled_async_create,
        monkeypatch,
        cancel_timing,
        backend=backend,
    )


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_async_create_native_error_frees_response_and_callbacks(monkeypatch, backend):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    error_message = ffi.new("char[]", b"native creation failed")
    fake_lib._response.conn_ptr = ffi.NULL
    fake_lib._response.connection_error_message = error_message
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(
        async_client_module.BaseClient, "_setup_pipe", lambda self: None
    )

    instances = []
    original_init = async_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        instances.append(self)

    monkeypatch.setattr(async_client_module.GlideClient, "__init__", capture_instance)

    async def create_failing_client():
        with pytest.raises(ClosingError, match="native creation failed"):
            await async_client_module.GlideClient.create(
                _direct_client_config(lambda: AwsCredentials("access", "secret"))
            )

    anyio.run(create_failing_client, backend=backend)

    assert len(instances) == 1
    assert instances[0]._core_client is None
    assert instances[0]._credential_provider_callback_ref is None
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    fake_lib.close_client.assert_not_called()


@pytest.mark.parametrize("client_kind", ["async", "sync"])
def test_monitor_rejects_custom_provider_before_serialization_or_native_call(
    monkeypatch, client_kind
):
    provider = MagicMock(return_value=AwsCredentials("access", "secret"))
    config = _direct_client_config(provider)
    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)

    if client_kind == "async":
        import glide.monitor_client as monitor_module

        serialize = MagicMock(side_effect=AssertionError("serialized"))
        monkeypatch.setattr(
            monitor_module, "_create_async_connection_request", serialize
        )
        monkeypatch.setattr(
            monitor_module, "GlideFFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
        )

        async def create_monitor():
            with pytest.raises(ConfigurationError, match="does not support custom IAM"):
                await monitor_module.MonitorClient.create(config)

        anyio.run(create_monitor, backend="asyncio")
    else:
        import glide_sync.monitor_client as monitor_module

        serialize = MagicMock(side_effect=AssertionError("serialized"))
        monkeypatch.setattr(
            monitor_module, "_create_sync_connection_request", serialize
        )
        monkeypatch.setattr(
            monitor_module, "GlideFFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
        )
        with pytest.raises(ConfigurationError, match="does not support custom IAM"):
            monitor_module.MonitorClient.create(config)

    serialize.assert_not_called()
    fake_lib.create_monitor_client.assert_not_called()
    provider.assert_not_called()


def _patch_sync_client(monkeypatch):
    import glide_sync.glide_client as sync_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    register_at_fork = MagicMock()
    monkeypatch.setattr(
        sync_client_module, "_SYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(sync_client_module.os, "register_at_fork", register_at_fork)
    return sync_client_module, ffi, fake_lib, register_at_fork


@pytest.mark.parametrize("client_kind", ["async", "sync"])
def test_monitor_allows_iam_without_custom_provider(monkeypatch, client_kind):
    config = _direct_client_config()
    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)

    if client_kind == "async":
        import glide.monitor_client as monitor_module

        monkeypatch.setattr(
            monitor_module, "GlideFFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
        )

        async def create_monitor():
            monitor = await monitor_module.MonitorClient.create(config)
            await monitor.stop()

        anyio.run(create_monitor, backend="asyncio")
    else:
        import glide_sync.monitor_client as monitor_module

        monkeypatch.setattr(
            monitor_module, "GlideFFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
        )
        monitor = monitor_module.MonitorClient.create(config)
        monitor.close()

    fake_lib.create_monitor_client.assert_called_once()
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    fake_lib.close_monitor_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_fork_recreation_is_transactional_and_reuses_registered_hook(monkeypatch):
    sync_client_module, ffi, fake_lib, register_at_fork = _patch_sync_client(
        monkeypatch
    )

    def provider():
        return AwsCredentials("access", "secret")

    client = sync_client_module.GlideClient.create(_direct_client_config(provider))
    original_pointer = client._core_client
    original_callback = client._credential_provider_callback_ref
    child_pointer = ffi.cast("void*", 2)
    fake_lib._response.conn_ptr = child_pointer

    def recreate(*args):
        assert client._core_client == ffi.NULL
        return fake_lib._response

    fake_lib.create_client.side_effect = recreate
    client._recreate_core_client_after_fork()

    assert not client._is_closed
    assert client._core_client == child_pointer
    assert client._core_client != original_pointer
    assert (
        client._credential_provider_callback_ref
        is fake_lib.create_client.call_args.args[5]
    )
    assert client._credential_provider_callback_ref is not original_callback
    assert fake_lib.create_client.call_count == 2
    register_at_fork.assert_called_once_with(
        after_in_child=client._recreate_core_client_after_fork
    )
    fake_lib.close_client.assert_not_called()
    client.close()
    fake_lib.close_client.assert_called_once_with(child_pointer)


def test_sync_fork_native_failure_fails_closed_without_stale_dispatch(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    stale_pointer = client._core_client
    error_message = ffi.new("char[]", b"child native failure")
    fake_lib._response.conn_ptr = ffi.NULL
    fake_lib._response.connection_error_message = error_message

    client._recreate_core_client_after_fork()

    assert client._is_closed
    assert client._core_client == ffi.NULL
    assert client._pubsub_callback_ref is None
    assert client._address_resolver_callback_ref is None
    assert client._credential_provider_callback_ref is None
    assert stale_pointer != ffi.NULL
    fake_lib.close_client.assert_not_called()
    with pytest.raises(ClosingError, match="client is closed"):
        client.get("key")
    fake_lib.command_with_buffer.assert_not_called()


def test_sync_fork_rejects_provider_mutated_to_async_and_fails_closed(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    iam_config = _iam_config(lambda: AwsCredentials("access", "secret"))
    client = sync_client_module.GlideClient.create(
        GlideClientConfiguration(
            addresses=[NodeAddress()],
            credentials=ServerCredentials(username="user", iam_config=iam_config),
        )
    )

    async def async_provider():
        return AwsCredentials("access", "secret")

    iam_config.credential_provider = async_provider
    client._recreate_core_client_after_fork()

    assert client._is_closed
    assert client._core_client == ffi.NULL
    assert client._credential_provider_callback_ref is None
    assert fake_lib.create_client.call_count == 1
    fake_lib.close_client.assert_not_called()


def test_sync_creation_uses_current_provider_after_async_to_sync_mutation(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)

    async def async_provider():
        return AwsCredentials("async", "secret")

    def sync_provider():
        return AwsCredentials("sync", "secret")

    iam_config = _iam_config(async_provider)
    assert iam_config._credential_provider_is_async
    iam_config.credential_provider = sync_provider

    client = sync_client_module.GlideClient.create(
        GlideClientConfiguration(
            addresses=[NodeAddress()],
            credentials=ServerCredentials(username="user", iam_config=iam_config),
        )
    )
    callback_arg = fake_lib.create_client.call_args.args[5]
    assert callback_arg != client._ffi.NULL
    assert client._credential_provider_callback_ref is callback_arg
    client.close()


def test_sync_creation_rejects_current_provider_after_sync_to_async_mutation(
    monkeypatch,
):
    sync_client_module, _, fake_lib, register_at_fork = _patch_sync_client(monkeypatch)
    iam_config = _iam_config(lambda: AwsCredentials("sync", "secret"))
    assert not iam_config._credential_provider_is_async

    async def async_provider():
        return AwsCredentials("async", "secret")

    iam_config.credential_provider = async_provider
    config = GlideClientConfiguration(
        addresses=[NodeAddress()],
        credentials=ServerCredentials(username="user", iam_config=iam_config),
    )
    with pytest.raises(ValueError, match="does not support async"):
        sync_client_module.GlideClient.create(config)

    fake_lib.create_client.assert_not_called()
    register_at_fork.assert_not_called()


@pytest.mark.parametrize("scheduler", ["asyncio", "trio"])
def test_awaitable_is_closed_when_async_scheduling_fails(monkeypatch, scheduler):
    created = []

    async def credential_result():
        return AwsCredentials("access", "secret")

    def provider():
        awaitable = credential_result()
        created.append(awaitable)
        return awaitable

    callback_kwargs = {"allow_async": True}
    if scheduler == "asyncio":

        class OpenLoop:
            def is_closed(self):
                return False

        callback_kwargs["event_loop"] = OpenLoop()
        monkeypatch.setattr(
            asyncio,
            "run_coroutine_threadsafe",
            MagicMock(side_effect=RuntimeError("loop closed during scheduling")),
        )
    else:
        import trio

        callback_kwargs["trio_token"] = object()
        monkeypatch.setattr(
            trio.from_thread,
            "run",
            MagicMock(side_effect=trio.RunFinishedError("run finished")),
        )

    callback = create_credential_provider_callback(
        GlideFFI.ffi, provider, **callback_kwargs
    )
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        assert _invoke_callback(callback)[0] == 0
        gc.collect()

    assert len(created) == 1
    assert inspect.getcoroutinestate(created[0]) == inspect.CORO_CLOSED
    assert not any("was never awaited" in str(item.message) for item in caught)
