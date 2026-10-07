# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import inspect
import threading
import time
import warnings
import weakref
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

pytestmark = pytest.mark.serverless


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
        self.refresh_iam_token = MagicMock()
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
    with anyio.fail_after(2):
        while (
            fake_lib.free_connection_response.call_count != 1
            or fake_lib.close_client.call_count != 1
        ):
            await anyio.sleep(0)
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
    monkeypatch.setattr(
        sync_client_module, "_SYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    return sync_client_module, ffi, fake_lib, None


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


def test_sync_registers_only_one_process_global_fork_hook(monkeypatch):
    import glide_sync.glide_client as sync_client_module

    register_at_fork = MagicMock()
    monkeypatch.setattr(sync_client_module.os, "register_at_fork", register_at_fork)
    monkeypatch.setattr(sync_client_module, "_fork_hook_registered", False)

    sync_client_module._register_global_fork_hook()
    sync_client_module._register_global_fork_hook()

    register_at_fork.assert_called_once_with(
        after_in_child=sync_client_module._after_fork_in_child
    )


def test_sync_fork_recreation_is_transactional_with_global_hook(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)

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
    sync_client_module._after_fork_in_child()

    assert not client._is_closed
    assert client._core_client == child_pointer
    assert client._core_client != original_pointer
    assert (
        client._credential_provider_callback_ref
        is fake_lib.create_client.call_args.args[5]
    )
    assert client._credential_provider_callback_ref is not original_callback
    assert fake_lib.create_client.call_count == 2
    assert not hasattr(client, "_fork_hook_registered")
    assert not hasattr(client, "_register_at_fork")
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
            "run_sync",
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


def test_sync_close_waits_for_active_native_call_and_rejects_late_call(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client = sync_client_module.GlideClient.create(
        _direct_client_config(lambda: AwsCredentials("access", "secret"))
    )
    callback_ref = client._credential_provider_callback_ref
    native_entered = threading.Event()
    release_native = threading.Event()
    call_errors = []

    def blocking_refresh(*args):
        native_entered.set()
        assert release_native.wait(timeout=5)
        raise RuntimeError("native call released")

    fake_lib.refresh_iam_token.side_effect = blocking_refresh

    def call_refresh():
        try:
            client._refresh_iam_token()
        except BaseException as error:
            call_errors.append(error)

    call_thread = threading.Thread(target=call_refresh)
    close_thread = threading.Thread(target=client.close)
    call_thread.start()
    assert native_entered.wait(timeout=2)
    close_thread.start()

    deadline = time.monotonic() + 2
    while not client._is_closed and time.monotonic() < deadline:
        time.sleep(0.001)
    assert client._is_closed
    assert close_thread.is_alive()
    assert client._credential_provider_callback_ref is callback_ref

    with pytest.raises(ClosingError, match="client is closed"):
        client._refresh_iam_token()
    fake_lib.refresh_iam_token.assert_called_once()

    release_native.set()
    call_thread.join(timeout=2)
    close_thread.join(timeout=2)
    assert not call_thread.is_alive()
    assert not close_thread.is_alive()
    assert len(call_errors) == 1
    assert isinstance(call_errors[0], RuntimeError)
    assert client._credential_provider_callback_ref is None
    fake_lib.close_client.assert_called_once()

    client.close()
    fake_lib.close_client.assert_called_once()


def test_sync_close_after_failed_creation_is_safe(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    fake_lib._response.conn_ptr = ffi.NULL
    captured = []
    original_init = sync_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        captured.append(self)

    monkeypatch.setattr(sync_client_module.GlideClient, "__init__", capture_instance)
    with pytest.raises(ClosingError):
        sync_client_module.GlideClient.create(_direct_client_config())

    assert len(captured) == 1
    captured[0].close()
    captured[0].close()
    assert captured[0]._is_closed
    assert captured[0]._core_client == ffi.NULL
    fake_lib.close_client.assert_not_called()


def test_sync_fork_registry_does_not_retain_unclosed_client(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient(_direct_client_config())
    registry.add(client)
    client_ref = weakref.ref(client)
    assert not client._is_closed
    assert client in registry

    del client
    gc.collect()

    assert client_ref() is None
    assert not registry
    fake_lib.create_client.assert_not_called()
    fake_lib.close_client.assert_not_called()


def test_sync_close_discards_client_from_fork_registry(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    assert client in registry

    client.close()

    assert client not in registry
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_global_fork_hook_continues_after_one_client_fails(monkeypatch):
    sync_client_module, _, _, _ = _patch_sync_client(monkeypatch)
    first = sync_client_module.GlideClient.create(_direct_client_config())
    second = sync_client_module.GlideClient.create(_direct_client_config())
    first_recreate = MagicMock(side_effect=RuntimeError("child recreation failed"))
    first_fail_closed = MagicMock()
    second_recreate = MagicMock()
    monkeypatch.setattr(first, "_recreate_core_client_after_fork", first_recreate)
    monkeypatch.setattr(first, "_fail_closed_after_fork", first_fail_closed)
    monkeypatch.setattr(second, "_recreate_core_client_after_fork", second_recreate)

    sync_client_module._after_fork_in_child()

    first_recreate.assert_called_once_with()
    first_fail_closed.assert_called_once_with()
    second_recreate.assert_called_once_with()
    first.close()
    second.close()


async def _run_async_close_during_provider(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(
        async_client_module.BaseClient, "_setup_pipe", lambda self: None
    )

    provider_entered = anyio.Event()
    release_provider = anyio.Event()

    async def provider():
        provider_entered.set()
        await release_provider.wait()
        return AwsCredentials("access", "secret")

    client = await async_client_module.GlideClient.create(
        _direct_client_config(provider)
    )
    callback_ref = client._credential_provider_callback_ref
    callback = fake_lib.create_client.call_args.args[5]

    def native_close(pointer):
        assert pointer == fake_lib._response.conn_ptr
        assert _invoke_callback(callback)[0] == 1

    fake_lib.close_client.side_effect = native_close
    close_results = []

    async def close_client():
        await client.close()
        close_results.append(True)

    with anyio.fail_after(2):
        async with anyio.create_task_group() as task_group:
            task_group.start_soon(close_client)
            task_group.start_soon(close_client)
            await provider_entered.wait()
            assert client._credential_provider_callback_ref is callback_ref
            release_provider.set()

    assert close_results == [True, True]
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert client._core_client is None
    assert client._credential_provider_callback_ref is None


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_async_close_keeps_owner_runtime_available_for_provider(monkeypatch, backend):
    anyio.run(_run_async_close_during_provider, monkeypatch, backend=backend)


async def _run_post_scheduling_timeout_cleanup(monkeypatch):
    import glide_shared.ffi_helpers as ffi_helpers

    monkeypatch.setattr(ffi_helpers, "_CREDENTIAL_INNER_TIMEOUT_SECONDS", 0.05)
    monkeypatch.setattr(ffi_helpers, "_CREDENTIAL_BRIDGE_TIMEOUT_SECONDS", 0.5)
    owner_thread = threading.get_ident()
    final_observation = []

    async def credential_result():
        try:
            await anyio.sleep_forever()
        finally:
            final_observation.append(
                (threading.get_ident(), sniffio.current_async_library())
            )

    def provider():
        return credential_result()

    if sniffio.current_async_library() == "asyncio":
        event_loop = asyncio.get_running_loop()
        trio_token = None
        unhandled = []
        old_handler = event_loop.get_exception_handler()
        event_loop.set_exception_handler(
            lambda loop, context: unhandled.append(context)
        )
    else:
        import trio

        event_loop = None
        trio_token = trio.lowlevel.current_trio_token()
        unhandled = []
        old_handler = None

    callback = create_credential_provider_callback(
        GlideFFI.ffi,
        provider,
        event_loop=event_loop,
        trio_token=trio_token,
        allow_async=True,
    )
    try:
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            assert (await anyio.to_thread.run_sync(_invoke_callback, callback))[0] == 0
            gc.collect()
            await anyio.sleep(0)
    finally:
        if event_loop is not None:
            event_loop.set_exception_handler(old_handler)

    assert final_observation == [(owner_thread, sniffio.current_async_library())]
    assert not unhandled
    assert not any("was never awaited" in str(item.message) for item in caught)
    assert not any(
        "exception was never retrieved" in str(item.message) for item in caught
    )


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_post_scheduling_timeout_finalizes_on_owner_runtime(monkeypatch, backend):
    anyio.run(_run_post_scheduling_timeout_cleanup, monkeypatch, backend=backend)


async def _wait_for_mock_calls(mock, count):
    with anyio.fail_after(2):
        while mock.call_count != count:
            await anyio.sleep(0)


def _patch_async_client(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(
        async_client_module.BaseClient, "_setup_pipe", lambda self: None
    )
    return async_client_module, ffi, fake_lib


def test_raw_asyncio_task_cancel_during_create_closes_late_success(monkeypatch):
    async_client_module, ffi, fake_lib = _patch_async_client(monkeypatch)
    native_entered = threading.Event()
    release_native = threading.Event()
    captured_args = []

    def native_create(*args):
        captured_args.append(args)
        native_entered.set()
        assert release_native.wait(timeout=2)
        return fake_lib._response

    fake_lib.create_client.side_effect = native_create

    async def run():
        task = asyncio.create_task(
            async_client_module.GlideClient.create(
                _direct_client_config(lambda: AwsCredentials("access", "secret"))
            )
        )
        with anyio.fail_after(2):
            while not native_entered.is_set():
                await anyio.sleep(0)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task

        # The native worker still strongly owns all callbacks after the caller
        # has returned. Releasing create produces one response free and one
        # abandoned-client close on that worker.
        assert captured_args[0][5] != ffi.NULL
        release_native.set()
        await _wait_for_mock_calls(fake_lib.free_connection_response, 1)
        await _wait_for_mock_calls(fake_lib.close_client, 1)

    asyncio.run(run())
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_raw_asyncio_create_success_adopts_without_worker_close(monkeypatch):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)

    async def run():
        client = await asyncio.create_task(
            async_client_module.GlideClient.create(_direct_client_config())
        )
        assert fake_lib.free_connection_response.call_count == 1
        fake_lib.close_client.assert_not_called()
        await client.close()

    asyncio.run(run())
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_raw_asyncio_create_cancel_result_races_have_one_owner(monkeypatch):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)
    iterations = 20

    async def run():
        for index in range(iterations):
            native_entered = threading.Event()
            release_native = threading.Event()

            def native_create(*args):
                native_entered.set()
                assert release_native.wait(timeout=2)
                return fake_lib._response

            fake_lib.create_client.side_effect = native_create
            task = asyncio.create_task(
                async_client_module.GlideClient.create(_direct_client_config())
            )
            with anyio.fail_after(2):
                while not native_entered.is_set():
                    await anyio.sleep(0)

            loop = asyncio.get_running_loop()
            if index % 2:
                loop.call_soon(task.cancel)
                loop.call_soon(release_native.set)
            else:
                loop.call_soon(release_native.set)
                loop.call_soon(task.cancel)

            try:
                client = await task
            except asyncio.CancelledError:
                pass
            else:
                await client.close()

            await _wait_for_mock_calls(fake_lib.free_connection_response, index + 1)
            await _wait_for_mock_calls(fake_lib.close_client, index + 1)

    asyncio.run(run())
    assert fake_lib.free_connection_response.call_count == iterations
    assert fake_lib.close_client.call_count == iterations


def test_raw_asyncio_task_cancel_during_close_keeps_worker_ownership(monkeypatch):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)
    native_entered = threading.Event()
    release_native = threading.Event()

    async def run():
        client = await async_client_module.GlideClient.create(
            _direct_client_config(lambda: AwsCredentials("access", "secret"))
        )
        callback_ref = client._credential_provider_callback_ref

        def native_close(pointer):
            native_entered.set()
            assert release_native.wait(timeout=2)

        fake_lib.close_client.side_effect = native_close
        task = asyncio.create_task(client.close())
        with anyio.fail_after(2):
            while not native_entered.is_set():
                await anyio.sleep(0)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert client._credential_provider_callback_ref is callback_ref

        release_native.set()
        await _wait_for_mock_calls(fake_lib.close_client, 1)
        with anyio.fail_after(2):
            while client._credential_provider_callback_ref is not None:
                await anyio.sleep(0)
        await client.close()

    asyncio.run(run())
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_async_close_worker_finishes_after_owner_loop_closes(monkeypatch):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)
    native_entered = threading.Event()
    release_native = threading.Event()
    native_finished = threading.Event()

    def native_close(pointer):
        native_entered.set()
        assert release_native.wait(timeout=2)
        native_finished.set()

    async def start_cancelled_close():
        client = await async_client_module.GlideClient.create(
            _direct_client_config(lambda: AwsCredentials("access", "secret"))
        )
        fake_lib.close_client.side_effect = native_close
        task = asyncio.create_task(client.close())
        while not native_entered.is_set():
            await asyncio.sleep(0)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        return client

    loop = asyncio.new_event_loop()
    try:
        client = loop.run_until_complete(start_cancelled_close())
    finally:
        loop.close()

    assert client._credential_provider_callback_ref is not None
    release_native.set()
    assert native_finished.wait(timeout=2)
    assert client._close_state.done.wait(timeout=2)
    assert client._credential_provider_callback_ref is None
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_async_native_close_error_is_shared_without_retry(monkeypatch, backend):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)

    async def run():
        client = await async_client_module.GlideClient.create(_direct_client_config())
        fake_lib.close_client.side_effect = RuntimeError("native close failed")
        with pytest.raises(RuntimeError, match="native close failed"):
            await client.close()
        with pytest.raises(RuntimeError, match="native close failed"):
            await client.close()

    anyio.run(run, backend=backend)
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_provider_reentrant_close_fails_fast(monkeypatch, caplog):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    captured = []
    original_init = sync_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        captured.append(self)

    def provider():
        captured[0].close()
        return AwsCredentials("access", "secret")

    def native_create(*args):
        assert _invoke_callback(args[5])[0] == 0
        return fake_lib._response

    monkeypatch.setattr(sync_client_module.GlideClient, "__init__", capture_instance)
    fake_lib.create_client.side_effect = native_create
    with caplog.at_level("WARNING"):
        client = sync_client_module.GlideClient.create(_direct_client_config(provider))

    assert "Cannot close a client from its own credential provider" in caplog.text
    assert not client._is_closed
    client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


async def _run_async_provider_reentrant_close(monkeypatch, caplog):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)
    captured = []
    callback_status = []
    original_init = async_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        captured.append(self)

    async def provider():
        await anyio.sleep(0)
        await captured[0].close()
        return AwsCredentials("access", "secret")

    def native_create(*args):
        callback_status.append(_invoke_callback(args[5])[0])
        return fake_lib._response

    monkeypatch.setattr(async_client_module.GlideClient, "__init__", capture_instance)
    fake_lib.create_client.side_effect = native_create
    with caplog.at_level("WARNING"):
        with anyio.fail_after(2):
            client = await async_client_module.GlideClient.create(
                _direct_client_config(provider)
            )

    assert callback_status == [0]
    assert "Cannot close a client from its own credential provider" in caplog.text
    assert not client._is_closed
    await client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_async_provider_reentrant_close_fails_fast(monkeypatch, caplog, backend):
    anyio.run(
        _run_async_provider_reentrant_close,
        monkeypatch,
        caplog,
        backend=backend,
    )


def test_sync_refresh_provider_reentrant_close_does_not_wait_for_active_call(
    monkeypatch, caplog
):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client_holder = []

    def provider():
        if client_holder:
            client_holder[0].close()
        return AwsCredentials("access", "secret")

    client = sync_client_module.GlideClient.create(_direct_client_config(provider))
    client_holder.append(client)
    callback = fake_lib.create_client.call_args.args[5]

    def native_refresh(*args):
        assert _invoke_callback(callback)[0] == 0
        raise RuntimeError("refresh stopped after provider failure")

    fake_lib.refresh_iam_token.side_effect = native_refresh
    started = time.monotonic()
    with caplog.at_level("WARNING"):
        with pytest.raises(RuntimeError, match="refresh stopped"):
            client._refresh_iam_token()
    assert time.monotonic() - started < 1
    assert client._active_native_calls == 0
    assert not client._is_closed
    assert "Cannot close a client from its own credential provider" in caplog.text

    client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
