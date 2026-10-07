# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import inspect
import os
import selectors
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
from glide_shared.exceptions import ClosingError, ConfigurationError, RequestError
from glide_shared.ffi_helpers import create_credential_provider_callback
from glide_shared.protobuf.command_request_pb2 import RequestType

from tests.utils.utils import run_sync_func_with_timeout_in_thread

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


def _invoke_resolver_callback(callback, host=b"example.test", port=6379):
    ffi = GlideFFI.ffi
    host_buf = ffi.new("char[]", host)
    resolved_host_buf = ffi.new("char[]", 256)
    resolved_host_len = ffi.new("size_t*", 999)
    resolved_port = callback(
        17,
        host_buf,
        len(host),
        port,
        resolved_host_buf,
        256,
        resolved_host_len,
    )
    return resolved_port, resolved_host_len[0]


def _invoke_pubsub_callback(callback):
    ffi = GlideFFI.ffi
    message = ffi.new("uint8_t[]", b"message")
    channel = ffi.new("uint8_t[]", b"channel")
    callback(
        17,
        3,
        message,
        len(b"message"),
        channel,
        len(b"channel"),
        ffi.NULL,
        0,
    )


class _BackReferencingProvider:
    def __init__(self):
        self.client = None

    def __call__(self):
        return AwsCredentials("access", "secret")


class _BackReferencingResolver:
    def __init__(self):
        self.client = None

    def __call__(self, host, port):
        return host, port


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
        callback, callback_owner = create_credential_provider_callback(ffi, None)
        assert callback_owner is None
        assert callback == ffi.NULL
        assert ffi.typeof(callback) == ffi.typeof("CredentialProviderCallback")

    def test_success_writes_exact_utf8_sizes_and_optional_fields(self):
        credentials = AwsCredentials(
            "accéss", "secret", session_token=None, expires_at_epoch_millis=None
        )
        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi, lambda: credentials
        )
        assert callback_owner is not None
        status, lengths, expiry, buffers = _invoke_callback(callback)

        assert status == 1
        assert lengths == (len("accéss".encode()), 6, 0)
        assert expiry == 0
        assert buffers[0][: lengths[0]] == "accéss".encode()
        assert buffers[1][: lengths[1]] == b"secret"
        assert buffers[2] == b"\xa5" * 64

    def test_insufficient_capacity_sets_lengths_without_writes_or_expiry(self):
        token = "t" * 8192
        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi,
            lambda: AwsCredentials("access", "secret", token, 123456),
        )
        assert callback_owner is not None
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

        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi, provider
        )
        assert callback_owner is not None
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
        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi, lambda: credentials
        )
        assert callback_owner is not None
        status, lengths, expiry, buffers = _invoke_callback(callback)
        assert status == 0
        assert lengths == (777, 778, 779)
        assert expiry == 888
        assert buffers == (b"\xa5" * 64,) * 3

    def test_provider_error_and_wrong_result_type_fail(self):
        def failing_provider():
            raise RuntimeError("provider unavailable")

        for provider in (failing_provider, lambda: object()):
            callback, callback_owner = create_credential_provider_callback(
                GlideFFI.ffi, provider
            )
            assert callback_owner is not None
            assert _invoke_callback(callback)[0] == 0

    def test_sync_callback_disposes_awaitable_result(self):
        async def result():
            return AwsCredentials("access", "secret")

        def provider():
            return result()

        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi, provider
        )
        assert callback_owner is not None
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
        callback, callback_owner = create_credential_provider_callback(
            ffi,
            provider,
            event_loop=event_loop,
            trio_token=trio_token,
            allow_async=True,
        )
        assert callback_owner is not None
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
        self.free_response_arena = MagicMock()
        self.free_pipe_error_string = MagicMock()
        self.free_pubsub_pointer_payload = MagicMock()
        self.close_client = MagicMock()
        self.retain_client = MagicMock(return_value=True)
        self.release_client = MagicMock()
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
    finalizer = client._native_finalizer
    assert finalizer is not None and finalizer.alive
    assert not finalizer.atexit
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    await client.close()
    assert not finalizer.alive
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert client._credential_provider_callback_ref is None
    client_ref = weakref.ref(client)
    del client
    gc.collect()
    assert client_ref() is None
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


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
    finalizer = client._native_finalizer
    assert finalizer is not None and finalizer.alive
    assert not finalizer.atexit
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    client.close()
    assert not finalizer.alive
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert client._credential_provider_callback_ref is None
    client_ref = weakref.ref(client)
    del client
    gc.collect()
    assert client_ref() is None
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


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


def _direct_client_config(provider=None, address_resolver=None):
    return GlideClientConfiguration(
        addresses=[NodeAddress()],
        address_resolver=address_resolver,
        credentials=ServerCredentials(
            username="user", iam_config=_iam_config(provider)
        ),
    )


async def _run_async_callable_cycle_gc(monkeypatch):
    async_client_module, _, fake_lib = _patch_async_client(monkeypatch)
    provider = _BackReferencingProvider()
    resolver = _BackReferencingResolver()
    client = await async_client_module.GlideClient.create(
        _direct_client_config(provider, resolver)
    )
    provider.client = client
    resolver.client = client

    resolver_callback = fake_lib.create_client.call_args.args[4]
    provider_callback = fake_lib.create_client.call_args.args[5]
    client_ref = weakref.ref(client)
    provider_ref = weakref.ref(provider)
    resolver_ref = weakref.ref(resolver)
    finalizer = client._native_finalizer
    close_callback_results = []

    def native_close(pointer):
        close_callback_results.append(
            (
                _invoke_callback(provider_callback)[0],
                _invoke_resolver_callback(resolver_callback)[0],
            )
        )

    fake_lib.close_client.side_effect = native_close
    fake_lib.create_client.reset_mock()
    del client, provider, resolver
    for _ in range(100):
        gc.collect()
        if client_ref() is None:
            break
        time.sleep(0.001)

    assert client_ref() is None
    assert provider_ref() is None
    assert resolver_ref() is None
    assert finalizer is not None and not finalizer.alive
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert close_callback_results == [(0, 0)]
    assert _invoke_callback(provider_callback)[0] == 0
    assert _invoke_resolver_callback(resolver_callback)[0] == 0


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_async_finalizer_trampolines_do_not_retain_callable_cycles(
    monkeypatch, backend
):
    anyio.run(_run_async_callable_cycle_gc, monkeypatch, backend=backend)


def test_sync_finalizer_trampolines_do_not_retain_callable_cycles(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    provider = _BackReferencingProvider()
    resolver = _BackReferencingResolver()
    client = sync_client_module.GlideClient.create(
        _direct_client_config(provider, resolver)
    )
    provider.client = client
    resolver.client = client

    resolver_callback = fake_lib.create_client.call_args.args[4]
    provider_callback = fake_lib.create_client.call_args.args[5]
    client_ref = weakref.ref(client)
    provider_ref = weakref.ref(provider)
    resolver_ref = weakref.ref(resolver)
    finalizer = client._native_finalizer
    close_callback_results = []
    native_close_done = threading.Event()

    def native_close(pointer):
        try:
            close_callback_results.append(
                (
                    _invoke_callback(provider_callback)[0],
                    _invoke_resolver_callback(resolver_callback)[0],
                )
            )
        finally:
            native_close_done.set()

    fake_lib.close_client.side_effect = native_close
    fake_lib.create_client.reset_mock()
    del client, provider, resolver
    for _ in range(100):
        gc.collect()
        if client_ref() is None:
            break
        time.sleep(0.001)

    assert client_ref() is None
    assert provider_ref() is None
    assert resolver_ref() is None
    assert finalizer is not None and not finalizer.alive
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert native_close_done.wait(timeout=2)
    assert close_callback_results == [(0, 0)]
    assert _invoke_callback(provider_callback)[0] == 0
    assert _invoke_resolver_callback(resolver_callback)[0] == 0


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
    assert instances[0]._native_owner is None
    assert instances[0]._native_finalizer is None
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


def test_sync_child_hook_only_invalidates_inherited_state(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    provider = MagicMock(return_value=AwsCredentials("access", "secret"))
    resolver = MagicMock(side_effect=lambda host, port: (host, port))
    client = sync_client_module.GlideClient.create(
        _direct_client_config(provider, resolver)
    )
    original_pointer = client._core_client
    original_pubsub_lock = client._pubsub_lock
    original_client_lock = client._client_lock
    parent_owner = client._native_owner
    parent_finalizer = client._native_finalizer
    assert parent_owner is not None
    assert parent_finalizer is not None and parent_finalizer.alive

    # A vanished parent thread may have held this GLIDE-owned lock. The child
    # path must replace rather than acquire it.
    inherited_owner_lock = parent_owner._lock
    inherited_owner_lock.acquire()
    fake_lib.create_client.reset_mock()
    try:
        sync_client_module._after_fork_in_child()
    finally:
        inherited_owner_lock.release()

    assert original_pointer != ffi.NULL
    assert not client._is_closed
    assert client._needs_recreate_after_fork
    assert not client._recreating_after_fork
    assert client._core_client == ffi.NULL
    assert client._conn_req_bytes == b""
    assert client._pubsub_callback_ref is None
    assert client._address_resolver_callback_ref is None
    assert client._address_resolver_callback_owner is None
    assert client._credential_provider_callback_ref is None
    assert client._credential_provider_callback_owner is None
    assert client._pubsub_queue == []
    assert client._pubsub_lock is not original_pubsub_lock
    assert client._client_lock is not original_client_lock
    assert client._active_native_calls == 0
    assert client._native_owner is None
    assert client._native_finalizer is None
    assert not parent_finalizer.alive
    assert parent_owner._core_client is None
    assert parent_owner._callback_refs == ()
    assert parent_owner._lock is not inherited_owner_lock
    fake_lib.create_client.assert_not_called()
    fake_lib.close_client.assert_not_called()
    provider.assert_not_called()
    resolver.assert_not_called()

    assert client.try_get_pubsub_message() is None
    fake_lib.create_client.assert_not_called()
    client.close()


def test_sync_child_hook_clears_closed_active_call_state(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    owner = client._native_owner
    finalizer = client._native_finalizer
    callback_ref = client._pubsub_callback_ref
    assert owner is not None
    assert finalizer is not None and finalizer.alive

    client._begin_native_call()
    client.close()

    assert client not in registry
    assert client._is_closed
    assert client._close_complete
    assert client._active_native_calls == 1
    assert client._pubsub_callback_ref is callback_ref
    assert not finalizer.alive
    assert owner._core_client is None
    assert owner._callback_refs == ()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    fake_lib.close_client.reset_mock()
    registry.add(client)

    sync_client_module._after_fork_in_child()

    assert client not in registry
    assert client._is_closed
    assert client._close_complete
    assert client._active_native_calls == 0
    assert client._active_native_callbacks == 0
    assert client._pubsub_callback_ref is None
    fake_lib.close_client.assert_not_called()


def test_sync_first_child_native_call_recreates_and_dispatches(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    provider = MagicMock(return_value=AwsCredentials("access", "secret"))
    resolver = MagicMock(side_effect=lambda host, port: (host, port))
    client = sync_client_module.GlideClient.create(
        _direct_client_config(provider, resolver)
    )
    parent_finalizer = client._native_finalizer
    sync_client_module._after_fork_in_child()
    child_pointer = ffi.cast("void*", 2)
    fake_lib._response.conn_ptr = child_pointer
    fake_lib.create_client.reset_mock()
    fake_lib.refresh_iam_token.reset_mock()
    monkeypatch.setattr(client, "_handle_cmd_result", MagicMock(return_value="OK"))

    def recreate(*args):
        assert client._core_client == ffi.NULL
        assert _invoke_resolver_callback(args[4]) == (6379, len(b"example.test"))
        assert _invoke_callback(args[5])[0] == 1
        return fake_lib._response

    fake_lib.create_client.side_effect = recreate

    assert client._refresh_iam_token() == "OK"

    assert not client._is_closed
    assert not client._needs_recreate_after_fork
    assert client._core_client == child_pointer
    assert parent_finalizer is not None and not parent_finalizer.alive
    assert client._native_finalizer is not None and client._native_finalizer.alive
    fake_lib.create_client.assert_called_once()
    provider.assert_called_once_with()
    resolver.assert_called_once_with("example.test", 6379)
    fake_lib.refresh_iam_token.assert_called_once_with(child_pointer, 0)
    fake_lib.close_client.assert_not_called()
    client.close()
    fake_lib.close_client.assert_called_once_with(child_pointer)


def test_sync_concurrent_first_child_calls_recreate_once(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    sync_client_module._after_fork_in_child()
    child_pointer = ffi.cast("void*", 2)
    fake_lib._response.conn_ptr = child_pointer
    fake_lib.create_client.reset_mock()
    fake_lib.refresh_iam_token.reset_mock()
    monkeypatch.setattr(client, "_handle_cmd_result", MagicMock(return_value="OK"))
    create_entered = threading.Event()
    release_create = threading.Event()

    def recreate(*args):
        create_entered.set()
        assert release_create.wait(timeout=2)
        return fake_lib._response

    fake_lib.create_client.side_effect = recreate
    results = []
    errors = []

    def dispatch():
        try:
            results.append(client._refresh_iam_token())
        except BaseException as error:
            errors.append(error)

    threads = [threading.Thread(target=dispatch) for _ in range(2)]
    for thread in threads:
        thread.start()
    assert create_entered.wait(timeout=1)
    assert fake_lib.create_client.call_count == 1
    release_create.set()
    for thread in threads:
        thread.join(timeout=2)
        assert not thread.is_alive()

    assert errors == []
    assert results == ["OK", "OK"]
    assert fake_lib.create_client.call_count == 1
    assert fake_lib.refresh_iam_token.call_count == 2
    assert all(
        call.args == (child_pointer, 0)
        for call in fake_lib.refresh_iam_token.call_args_list
    )
    client.close()


def test_sync_lazy_fork_recreation_failure_fails_closed_without_retry(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    stale_pointer = client._core_client
    parent_finalizer = client._native_finalizer
    assert parent_finalizer is not None and parent_finalizer.alive
    sync_client_module._after_fork_in_child()
    error_message = ffi.new("char[]", b"child native failure")
    fake_lib._response.conn_ptr = ffi.NULL
    fake_lib._response.connection_error_message = error_message
    fake_lib.create_client.reset_mock()

    with pytest.raises(ClosingError, match="Unable to recreate client after fork"):
        client.get("key")

    assert client._is_closed
    assert not client._needs_recreate_after_fork
    assert client._core_client == ffi.NULL
    assert client._pubsub_callback_ref is None
    assert client._address_resolver_callback_ref is None
    assert client._credential_provider_callback_ref is None
    assert not parent_finalizer.alive
    assert client._native_owner is None
    assert client._native_finalizer is None
    assert stale_pointer != ffi.NULL
    assert fake_lib.create_client.call_count == 1
    fake_lib.close_client.assert_not_called()
    with pytest.raises(ClosingError, match="client is closed"):
        client.get("key")
    assert fake_lib.create_client.call_count == 1
    fake_lib.command_with_buffer.assert_not_called()


def test_sync_lazy_fork_rejects_provider_mutated_to_async(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    iam_config = _iam_config(lambda: AwsCredentials("access", "secret"))
    client = sync_client_module.GlideClient.create(
        GlideClientConfiguration(
            addresses=[NodeAddress()],
            credentials=ServerCredentials(username="user", iam_config=iam_config),
        )
    )

    async def async_provider():
        return AwsCredentials("access", "secret")

    sync_client_module._after_fork_in_child()
    iam_config.credential_provider = async_provider
    fake_lib.create_client.reset_mock()

    with pytest.raises(ClosingError, match="does not support async"):
        client.get("key")

    assert client._is_closed
    assert client._core_client == ffi.NULL
    assert client._credential_provider_callback_ref is None
    fake_lib.create_client.assert_not_called()
    fake_lib.close_client.assert_not_called()


def test_sync_close_before_first_child_call_does_not_touch_native(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    sync_client_module._after_fork_in_child()
    fake_lib.create_client.reset_mock()
    fake_lib.close_client.reset_mock()

    client.close()

    assert client._is_closed
    assert client._close_complete
    assert not client._needs_recreate_after_fork
    assert client._core_client == ffi.NULL
    fake_lib.create_client.assert_not_called()
    fake_lib.close_client.assert_not_called()


def test_sync_closed_inherited_client_remains_closed(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    # Model a closed client still present in the weak registry so the child hook
    # must preserve, rather than reopen, its lifecycle state.
    client.close()
    registry.add(client)
    fake_lib.create_client.reset_mock()
    fake_lib.close_client.reset_mock()

    sync_client_module._after_fork_in_child()

    assert client._is_closed
    assert client._close_complete
    assert not client._needs_recreate_after_fork
    assert client._core_client == ffi.NULL
    fake_lib.create_client.assert_not_called()
    fake_lib.close_client.assert_not_called()
    with pytest.raises(ClosingError, match="client is closed"):
        client.get("key")


@pytest.mark.skipif(not hasattr(os, "fork"), reason="requires os.fork")
def test_sync_real_fork_hook_does_not_run_locked_provider_or_native_create(
    monkeypatch,
):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    provider_lock = threading.Lock()
    provider_calls = 0
    create_calls = 0

    def provider():
        nonlocal provider_calls
        provider_calls += 1
        with provider_lock:
            return AwsCredentials("access", "secret")

    def native_create(*args):
        nonlocal create_calls
        create_calls += 1
        assert _invoke_callback(args[5])[0] == 1
        return fake_lib._response

    fake_lib.create_client.side_effect = native_create
    client = sync_client_module.GlideClient.create(_direct_client_config(provider))
    assert provider_calls == 1
    assert create_calls == 1
    lock_held = threading.Event()
    release_lock = threading.Event()

    def hold_provider_lock():
        with provider_lock:
            lock_held.set()
            release_lock.wait()

    holder = threading.Thread(target=hold_provider_lock)
    holder.start()
    assert lock_held.wait(timeout=1)
    read_fd, write_fd = os.pipe()
    try:
        import fcntl

        high_read_fd = fcntl.fcntl(read_fd, fcntl.F_DUPFD, 1024)
    except (ImportError, OSError):
        # Some platforms cap descriptors below FD_SETSIZE. The selector path is
        # still exercised there, while Linux CI normally takes this branch.
        pass
    else:
        os.close(read_fd)
        read_fd = high_read_fd
        assert read_fd >= 1024

    pid = os.fork()
    if pid == 0:
        os.close(read_fd)
        try:
            state = (
                provider_calls,
                create_calls,
                client._core_client == ffi.NULL,
                client._needs_recreate_after_fork,
            )
            os.write(write_fd, repr(state).encode())
        finally:
            os.close(write_fd)
            os._exit(0)

    os.close(write_fd)
    reaped = False
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(read_fd, selectors.EVENT_READ)
            ready = selector.select(timeout=2)
        if not ready:
            os.kill(pid, 9)
            os.waitpid(pid, 0)
            reaped = True
            pytest.fail("fork child hook blocked on inherited provider state")
        child_state = os.read(read_fd, 256)
        _, status = os.waitpid(pid, 0)
        reaped = True
        assert os.WIFEXITED(status) and os.WEXITSTATUS(status) == 0
        assert child_state == b"(1, 1, True, True)"
    finally:
        if not reaped:
            try:
                os.kill(pid, 9)
            except ProcessLookupError:
                pass
            os.waitpid(pid, 0)
        os.close(read_fd)
        release_lock.set()
        holder.join(timeout=1)
        assert not holder.is_alive()
        client.close()


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

        callback_kwargs["trio_token"] = SimpleNamespace(
            run_sync_soon=MagicMock(side_effect=trio.RunFinishedError("run finished"))
        )

    callback, callback_owner = create_credential_provider_callback(
        GlideFFI.ffi, provider, **callback_kwargs
    )
    assert callback_owner is not None
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        assert _invoke_callback(callback)[0] == 0
        gc.collect()

    assert len(created) == 1
    assert inspect.getcoroutinestate(created[0]) == inspect.CORO_CLOSED
    assert not any("was never awaited" in str(item.message) for item in caught)


def test_trio_stalled_before_schedule_times_out_and_disposes_on_owner(monkeypatch):
    import glide_shared.ffi_helpers as ffi_helpers
    import trio

    monkeypatch.setattr(ffi_helpers, "_CREDENTIAL_INNER_TIMEOUT_SECONDS", 1)
    monkeypatch.setattr(ffi_helpers, "_CREDENTIAL_BRIDGE_TIMEOUT_SECONDS", 0.05)
    callback_done = threading.Event()
    schedule_queued = threading.Event()
    results = []
    observations = []

    class TrackedAwaitable:
        def __await__(self):
            observations.append(("started", threading.get_ident()))
            return iter(())

        def close(self):
            observations.append(("closed", threading.get_ident()))

    async def run_test():
        owner_thread = threading.get_ident()
        token = trio.lowlevel.current_trio_token()

        class ObservedToken:
            def run_sync_soon(self, fn, *args, **kwargs):
                schedule_queued.set()
                token.run_sync_soon(fn, *args, **kwargs)

        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi,
            TrackedAwaitable,
            trio_token=ObservedToken(),
            allow_async=True,
        )
        assert callback_owner is not None

        def invoke():
            started = time.monotonic()
            results.append((_invoke_callback(callback)[0], time.monotonic() - started))
            callback_done.set()

        callback_thread = threading.Thread(target=invoke)
        callback_thread.start()
        # Deliberately block the Trio owner thread before its queued callback can
        # run. The foreign callback must still reach its own bridge deadline.
        assert schedule_queued.wait(timeout=1)
        assert callback_done.wait(timeout=0.5)
        callback_thread.join(timeout=0.1)
        assert not callback_thread.is_alive()
        assert observations == []

        with trio.fail_after(1):
            while not observations:
                await trio.lowlevel.checkpoint()
        assert observations == [("closed", owner_thread)]

    trio.run(run_test)
    assert results[0][0] == 0
    assert results[0][1] < 0.5


def test_trio_stalled_after_provider_start_times_out_and_cancels_on_owner(monkeypatch):
    import glide_shared.ffi_helpers as ffi_helpers
    import trio

    monkeypatch.setattr(ffi_helpers, "_CREDENTIAL_INNER_TIMEOUT_SECONDS", 1)
    monkeypatch.setattr(ffi_helpers, "_CREDENTIAL_BRIDGE_TIMEOUT_SECONDS", 0.05)
    callback_done = threading.Event()
    provider_entered = threading.Event()
    results = []
    final_observation = []

    async def run_test():
        owner_thread = threading.get_ident()
        finalized = trio.Event()

        async def provider():
            provider_entered.set()
            try:
                await trio.sleep_forever()
            finally:
                final_observation.append(threading.get_ident())
                finalized.set()

        callback, callback_owner = create_credential_provider_callback(
            GlideFFI.ffi,
            provider,
            trio_token=trio.lowlevel.current_trio_token(),
            allow_async=True,
        )
        assert callback_owner is not None

        def invoke():
            started = time.monotonic()
            results.append((_invoke_callback(callback)[0], time.monotonic() - started))
            callback_done.set()

        callback_thread = threading.Thread(target=invoke)
        callback_thread.start()
        with trio.fail_after(1):
            while not provider_entered.is_set():
                await trio.lowlevel.checkpoint()

        # Stall after provider work starts. Timeout queues owner-side
        # cancellation nonblockingly instead of parking this foreign thread.
        assert callback_done.wait(timeout=0.5)
        callback_thread.join(timeout=0.1)
        assert not callback_thread.is_alive()
        with trio.fail_after(1):
            await finalized.wait()
        assert final_observation == [owner_thread]

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        trio.run(run_test)
        gc.collect()
    assert results[0][0] == 0
    assert results[0][1] < 0.5
    assert not any("was never awaited" in str(item.message) for item in caught)


def test_sync_timeout_helper_rejects_immediate_request_error():
    on_timeout = MagicMock()

    def fail_immediately():
        raise RequestError("immediate failure")

    with pytest.raises(RequestError, match="immediate failure"):
        run_sync_func_with_timeout_in_thread(
            fail_immediately,
            timeout=0.1,
            on_timeout=on_timeout,
        )
    on_timeout.assert_not_called()


def test_sync_timeout_helper_joins_worker_after_timeout_close():
    release_worker = threading.Event()
    worker_finished = threading.Event()

    def block_until_close():
        try:
            assert release_worker.wait(timeout=2)
        finally:
            worker_finished.set()

    with pytest.raises(TimeoutError, match="did not return"):
        run_sync_func_with_timeout_in_thread(
            block_until_close,
            timeout=0.05,
            on_timeout=release_worker.set,
        )
    assert worker_finished.is_set()


def test_sync_guard_releases_lease_before_dispatch_exception(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client = sync_client_module.GlideClient.create(_direct_client_config())

    with pytest.raises(TypeError, match="writable"):
        client._execute_command(
            RequestType.Get,
            ["key"],
            response_buffer=memoryview(b"readonly"),
        )

    fake_lib.retain_client.assert_called_once_with(fake_lib._response.conn_ptr)
    fake_lib.release_client.assert_called_once_with(fake_lib._response.conn_ptr)
    fake_lib.command_with_buffer.assert_not_called()
    assert client._active_native_calls == 0
    client.close()


def test_sync_close_interrupts_active_native_call_and_releases_lease_once(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    client = sync_client_module.GlideClient.create(
        _direct_client_config(lambda: AwsCredentials("access", "secret"))
    )
    callback_ref = client._credential_provider_callback_ref
    finalizer = client._native_finalizer
    native_entered = threading.Event()
    native_close_called = threading.Event()
    allow_active_return = threading.Event()
    call_errors = []

    def blocking_refresh(*args):
        native_entered.set()
        assert native_close_called.wait(timeout=5)
        assert allow_active_return.wait(timeout=5)
        assert client._credential_provider_callback_ref is callback_ref
        raise RuntimeError("native call released")

    def native_close(*args):
        native_close_called.set()

    fake_lib.refresh_iam_token.side_effect = blocking_refresh
    fake_lib.close_client.side_effect = native_close

    def call_refresh():
        try:
            client._refresh_iam_token()
        except BaseException as error:
            call_errors.append(error)

    call_thread = threading.Thread(target=call_refresh)
    call_thread.start()
    assert native_entered.wait(timeout=2)
    fake_lib.retain_client.assert_called_once_with(fake_lib._response.conn_ptr)

    # Owner close signals shutdown immediately even while the call holds its
    # lease. Callback references remain until that leased call drains.
    client.close()
    assert native_close_called.is_set()
    assert client._is_closed
    assert client._core_client == ffi.NULL
    assert client._native_owner is None
    assert finalizer is not None and not finalizer.alive
    assert client._close_complete
    assert client._active_native_calls == 1
    assert client._credential_provider_callback_ref is callback_ref

    with pytest.raises(ClosingError, match="client is closed"):
        client._refresh_iam_token()
    fake_lib.refresh_iam_token.assert_called_once()

    allow_active_return.set()
    call_thread.join(timeout=2)
    assert not call_thread.is_alive()
    assert len(call_errors) == 1
    assert isinstance(call_errors[0], RuntimeError)
    assert client._active_native_calls == 0
    assert client._credential_provider_callback_ref is None
    fake_lib.release_client.assert_called_once_with(fake_lib._response.conn_ptr)
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_concurrent_close_returns_while_native_close_finishes(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    callback_ref = client._pubsub_callback_ref
    native_close_entered = threading.Event()
    release_native_close = threading.Event()

    def blocking_close(*args):
        native_close_entered.set()
        assert release_native_close.wait(timeout=5)

    fake_lib.close_client.side_effect = blocking_close
    first_close = threading.Thread(target=client.close)
    first_close.start()
    assert native_close_entered.wait(timeout=2)

    second_close = threading.Thread(target=client.close)
    second_close.start()
    second_close.join(timeout=1)
    assert not second_close.is_alive()
    assert first_close.is_alive()
    assert not client._close_complete
    assert client._pubsub_callback_ref is callback_ref

    release_native_close.set()
    first_close.join(timeout=2)
    assert not first_close.is_alive()
    assert client._close_complete
    assert client._pubsub_callback_ref is None
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


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
    assert captured[0]._native_owner is None
    assert captured[0]._native_finalizer is None
    fake_lib.close_client.assert_not_called()


def test_sync_fork_registry_does_not_retain_unclosed_client(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(
        _direct_client_config(
            lambda: AwsCredentials("access", "secret"),
            lambda host, port: (host, port),
        )
    )
    callback_refs = [
        weakref.ref(callback)
        for callback in fake_lib.create_client.call_args.args[3:6]
        if callback != ffi.NULL
    ]
    client_ref = weakref.ref(client)
    finalizer = client._native_finalizer
    close_observations = []

    def native_close(pointer):
        close_observations.append(
            (
                pointer,
                client_ref() is None,
                all(ref() is not None for ref in callback_refs),
            )
        )

    fake_lib.close_client.side_effect = native_close
    # MagicMock records every CFFI argument strongly; emulate native return by
    # releasing those test-only references before dropping the application one.
    fake_lib.create_client.reset_mock()
    assert client in registry

    del client
    gc.collect()

    assert client_ref() is None
    assert not registry
    assert finalizer is not None and not finalizer.alive
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert close_observations == [(fake_lib._response.conn_ptr, True, True)]
    gc.collect()
    assert all(ref() is None for ref in callback_refs)


def test_sync_close_discards_client_from_fork_registry(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    client = sync_client_module.GlideClient.create(_direct_client_config())
    assert client in registry

    client.close()

    assert client not in registry
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_global_fork_hook_bypasses_instance_overrides(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    registry = weakref.WeakSet()
    monkeypatch.setattr(sync_client_module, "_live_sync_clients", registry)
    first = sync_client_module.GlideClient.create(_direct_client_config())
    second = sync_client_module.GlideClient.create(_direct_client_config())
    instance_override = MagicMock(side_effect=RuntimeError("must not run"))
    monkeypatch.setattr(first, "_invalidate_after_fork", instance_override)
    fake_lib.create_client.reset_mock()

    sync_client_module._after_fork_in_child()

    instance_override.assert_not_called()
    assert first._core_client == ffi.NULL
    assert second._core_client == ffi.NULL
    assert first._needs_recreate_after_fork
    assert second._needs_recreate_after_fork
    fake_lib.create_client.assert_not_called()
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

    callback, callback_owner = create_credential_provider_callback(
        GlideFFI.ffi,
        provider,
        event_loop=event_loop,
        trio_token=trio_token,
        allow_async=True,
    )
    assert callback_owner is not None
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


async def _run_async_unclosed_client_gc(monkeypatch):
    async_client_module, ffi, fake_lib = _patch_async_client(monkeypatch)
    registry = weakref.WeakValueDictionary()
    monkeypatch.setattr(async_client_module, "_client_registry", registry)

    def register_only(client):
        registry[client._pipe_client_id] = client

    monkeypatch.setattr(async_client_module.BaseClient, "_setup_pipe", register_only)
    client = await async_client_module.GlideClient.create(
        _direct_client_config(
            lambda: AwsCredentials("access", "secret"),
            lambda host, port: (host, port),
        )
    )
    callback_refs = [
        weakref.ref(callback)
        for callback in fake_lib.create_client.call_args.args[3:6]
        if callback != ffi.NULL
    ]
    client_ref = weakref.ref(client)
    finalizer = client._native_finalizer
    client_id = client._pipe_client_id
    close_observations = []

    def native_close(pointer):
        close_observations.append(
            (
                pointer,
                client_ref() is None,
                all(ref() is not None for ref in callback_refs),
            )
        )

    fake_lib.close_client.side_effect = native_close
    fake_lib.create_client.reset_mock()
    assert registry[client_id] is client

    del client
    gc.collect()

    assert client_ref() is None
    assert client_id not in registry
    assert finalizer is not None and not finalizer.alive
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)
    assert close_observations == [(fake_lib._response.conn_ptr, True, True)]
    for _ in range(100):
        gc.collect()
        if all(ref() is None for ref in callback_refs):
            break
        await anyio.sleep(0)
    assert all(ref() is None for ref in callback_refs)


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
def test_async_registry_does_not_retain_unclosed_client(monkeypatch, backend):
    anyio.run(_run_async_unclosed_client_gc, monkeypatch, backend=backend)


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


def test_sync_initial_resolver_reentrant_close_fails_fast(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    captured = []
    close_errors = []
    original_init = sync_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        captured.append(self)

    def resolver(host, port):
        try:
            captured[0].close()
        except RuntimeError as error:
            close_errors.append(str(error))
        return host, port

    def native_create(*args):
        assert _invoke_resolver_callback(args[4]) == (
            6379,
            len(b"example.test"),
        )
        return fake_lib._response

    monkeypatch.setattr(sync_client_module.GlideClient, "__init__", capture_instance)
    fake_lib.create_client.side_effect = native_create
    client = sync_client_module.GlideClient.create(
        _direct_client_config(address_resolver=resolver)
    )

    assert close_errors == ["Cannot close a client from its own native callback"]
    assert not client._is_closed
    assert client._core_client == fake_lib._response.conn_ptr
    assert client._native_finalizer is not None and client._native_finalizer.alive
    client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_lazy_fork_resolver_reentrant_close_fails_fast(monkeypatch):
    sync_client_module, ffi, fake_lib, _ = _patch_sync_client(monkeypatch)
    client_holder = []
    close_errors = []

    def resolver(host, port):
        if client_holder:
            try:
                client_holder[0].close()
            except RuntimeError as error:
                close_errors.append(str(error))
        return host, port

    client = sync_client_module.GlideClient.create(
        _direct_client_config(address_resolver=resolver)
    )
    client_holder.append(client)
    sync_client_module._after_fork_in_child()
    child_pointer = ffi.cast("void*", 2)
    fake_lib._response.conn_ptr = child_pointer
    fake_lib.create_client.reset_mock()

    def recreate(*args):
        assert _invoke_resolver_callback(args[4]) == (
            6379,
            len(b"example.test"),
        )
        return fake_lib._response

    fake_lib.create_client.side_effect = recreate
    client._begin_native_call()
    client._end_native_call()

    assert close_errors == ["Cannot close a client from its own native callback"]
    assert not client._is_closed
    assert not client._needs_recreate_after_fork
    assert client._core_client == child_pointer
    client.close()
    fake_lib.close_client.assert_called_once_with(child_pointer)


def test_sync_pubsub_callback_reentrant_close_fails_fast(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client_holder = []
    close_errors = []

    def user_callback(message, context):
        try:
            client_holder[0].close()
        except RuntimeError as error:
            close_errors.append(str(error))

    config = _direct_client_config()
    config.pubsub_subscriptions = GlideClientConfiguration.PubSubSubscriptions(
        {
            GlideClientConfiguration.PubSubChannelModes.Exact: {"channel"},
        },
        user_callback,
        None,
    )
    client = sync_client_module.GlideClient.create(config)
    client_holder.append(client)
    pubsub_callback = fake_lib.create_client.call_args.args[3]

    _invoke_pubsub_callback(pubsub_callback)

    assert close_errors == ["Cannot close a client from its own native callback"]
    assert not client._is_closed
    client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_pubsub_callback_allows_unrelated_thread_close(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client_holder = []
    close_threads = []
    native_closed = threading.Event()
    fake_lib.close_client.side_effect = lambda pointer: native_closed.set()

    def user_callback(message, context):
        close_thread = threading.Thread(target=client_holder[0].close)
        close_threads.append(close_thread)
        close_thread.start()
        close_thread.join(timeout=2)
        assert not close_thread.is_alive()

    config = _direct_client_config()
    config.pubsub_subscriptions = GlideClientConfiguration.PubSubSubscriptions(
        {
            GlideClientConfiguration.PubSubChannelModes.Exact: {"channel"},
        },
        user_callback,
        None,
    )
    client = sync_client_module.GlideClient.create(config)
    client_holder.append(client)
    pubsub_callback = fake_lib.create_client.call_args.args[3]

    _invoke_pubsub_callback(pubsub_callback)

    assert len(close_threads) == 1
    assert native_closed.wait(timeout=2)
    assert client._active_native_callbacks == 0
    assert client._is_closed
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_provider_callback_can_spawn_and_join_close_thread(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client_holder = []
    close_threads = []
    native_closed = threading.Event()
    fake_lib.close_client.side_effect = lambda pointer: native_closed.set()

    def provider():
        if client_holder:
            close_thread = threading.Thread(target=client_holder[0].close)
            close_threads.append(close_thread)
            close_thread.start()
            close_thread.join(timeout=2)
            assert not close_thread.is_alive()
        return AwsCredentials("access", "secret")

    client = sync_client_module.GlideClient.create(_direct_client_config(provider))
    client_holder.append(client)
    provider_callback = fake_lib.create_client.call_args.args[5]

    assert _invoke_callback(provider_callback)[0] == 1
    assert len(close_threads) == 1
    assert native_closed.wait(timeout=2)
    assert client._active_native_callbacks == 0
    assert client._is_closed
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_resolver_callback_can_spawn_and_join_close_thread(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    client_holder = []
    close_threads = []
    native_closed = threading.Event()
    fake_lib.close_client.side_effect = lambda pointer: native_closed.set()

    def resolver(host, port):
        if client_holder:
            close_thread = threading.Thread(target=client_holder[0].close)
            close_threads.append(close_thread)
            close_thread.start()
            close_thread.join(timeout=2)
            assert not close_thread.is_alive()
        return host, port

    client = sync_client_module.GlideClient.create(
        _direct_client_config(address_resolver=resolver)
    )
    client_holder.append(client)
    resolver_callback = fake_lib.create_client.call_args.args[4]

    assert _invoke_resolver_callback(resolver_callback) == (
        6379,
        len(b"example.test"),
    )
    assert len(close_threads) == 1
    assert native_closed.wait(timeout=2)
    assert client._active_native_callbacks == 0
    assert client._is_closed
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_sync_close_before_callback_begin_suppresses_user_code(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    provider = MagicMock(return_value=AwsCredentials("access", "secret"))
    resolver = MagicMock(side_effect=lambda host, port: (host, port))
    user_callback = MagicMock()
    config = _direct_client_config(provider, resolver)
    config.pubsub_subscriptions = GlideClientConfiguration.PubSubSubscriptions(
        {GlideClientConfiguration.PubSubChannelModes.Exact: {"channel"}},
        user_callback,
        None,
    )
    client = sync_client_module.GlideClient.create(config)
    pubsub_callback, resolver_callback, provider_callback = (
        fake_lib.create_client.call_args.args[3:6]
    )

    client.close()

    assert _invoke_callback(provider_callback)[0] == 0
    assert _invoke_resolver_callback(resolver_callback)[0] == 0
    _invoke_pubsub_callback(pubsub_callback)
    provider.assert_not_called()
    resolver.assert_not_called()
    user_callback.assert_not_called()
    assert client._active_native_callbacks == 0


def test_sync_initial_resolver_close_abandons_late_native_pointer(monkeypatch):
    sync_client_module, _, fake_lib, _ = _patch_sync_client(monkeypatch)
    captured = []
    close_threads = []
    original_init = sync_client_module.GlideClient.__init__

    def capture_instance(self, config):
        original_init(self, config)
        captured.append(self)

    def resolver(host, port):
        close_thread = threading.Thread(target=captured[0].close)
        close_threads.append(close_thread)
        close_thread.start()
        close_thread.join(timeout=2)
        assert not close_thread.is_alive()
        return host, port

    def native_create(*args):
        assert _invoke_resolver_callback(args[4])[0] == 6379
        return fake_lib._response

    monkeypatch.setattr(sync_client_module.GlideClient, "__init__", capture_instance)
    fake_lib.create_client.side_effect = native_create

    with pytest.raises(ClosingError, match="closed during native creation"):
        sync_client_module.GlideClient.create(
            _direct_client_config(address_resolver=resolver)
        )

    assert len(close_threads) == 1
    assert captured[0]._is_closed
    assert captured[0]._core_client == captured[0]._ffi.NULL
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

    assert "Cannot close a client from its own native callback" in caplog.text
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
    assert "Cannot close a client from its own native callback" in caplog.text

    client.close()
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)


def test_async_create_worker_abandons_after_owner_loop_disappears(monkeypatch):
    async_client_module, ffi, fake_lib = _patch_async_client(monkeypatch)
    monkeypatch.setattr(
        async_client_module,
        "_NATIVE_CREATE_ADOPTION_DECISION_TIMEOUT_SECONDS",
        0.02,
    )
    native_entered = threading.Event()
    release_native = threading.Event()
    states = []
    callback_refs = []
    original_state = async_client_module._NativeCreateState

    class CapturingCreateState(original_state):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            states.append(self)

    def native_create(*args):
        callback_refs.append(weakref.ref(args[5]))
        native_entered.set()
        assert release_native.wait(timeout=2)
        return fake_lib._response

    monkeypatch.setattr(async_client_module, "_NativeCreateState", CapturingCreateState)
    fake_lib.create_client.side_effect = native_create

    async def start_create():
        task = asyncio.create_task(
            async_client_module.GlideClient.create(
                _direct_client_config(lambda: AwsCredentials("access", "secret"))
            )
        )
        with anyio.fail_after(2):
            while not native_entered.is_set():
                await anyio.sleep(0)
        return task

    loop = asyncio.new_event_loop()
    loop.set_exception_handler(lambda unused_loop, unused_context: None)
    try:
        task = loop.run_until_complete(start_create())
    finally:
        loop.close()

    release_native.set()
    assert len(states) == 1
    state = states[0]
    assert state.cleanup_complete.wait(timeout=2)
    state._thread.join(timeout=2)
    assert not state._thread.is_alive()
    assert state._abandoned and not state._adopted
    assert state._callback_refs == ()
    assert state._create_args == ()
    fake_lib.free_connection_response.assert_called_once_with(fake_lib._response)
    fake_lib.close_client.assert_called_once_with(fake_lib._response.conn_ptr)

    # Drop the task only after proving that the worker needs no event-loop
    # progress to make its timeout decision and release its ownership.
    fake_lib.create_client.reset_mock()
    assert callback_refs and callback_refs[0]() is not None
    del task
    gc.collect()


def test_async_create_adoption_timeout_races_have_one_owner(monkeypatch):
    async_client_module, ffi, fake_lib = _patch_async_client(monkeypatch)
    delays = [0.0, 0.02] + [0.005] * 30

    async def run():
        for index, delay in enumerate(delays):
            state = async_client_module._NativeCreateState(
                ffi,
                fake_lib,
                (),
                (object(),),
                adoption_decision_timeout_seconds=0.005,
            )
            state._start()
            if delay:
                await anyio.sleep(delay)
            try:
                core_client = await state._wait_and_adopt()
            except RuntimeError as error:
                assert "abandoned" in str(error)
            else:
                # The test caller models transfer into _NativeClientOwner.
                fake_lib.close_client(core_client)
            with anyio.fail_after(2):
                await anyio.to_thread.run_sync(state.cleanup_complete.wait)
            assert state._adopted != state._abandoned
            assert fake_lib.free_connection_response.call_count == index + 1
            assert fake_lib.close_client.call_count == index + 1

    anyio.run(run, backend="asyncio")
    assert fake_lib.free_connection_response.call_count == len(delays)
    assert fake_lib.close_client.call_count == len(delays)


def test_orphan_pipe_frames_free_without_any_registered_client(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    registry = weakref.WeakValueDictionary()

    class LastClient:
        pass

    last_client = LastClient()
    registry[99] = last_client
    del last_client
    gc.collect()
    assert not registry

    monkeypatch.setattr(async_client_module, "_client_registry", registry)
    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(async_client_module, "_async_pipe_read_fd", 123)
    monkeypatch.setattr(async_client_module, "_pipe_remainder", b"")

    success_arena = 0x2000
    error_pointer = 0x3000
    error_frame = (7 << 56) | error_pointer
    pubsub_pointer = 0x4000
    pubsub_length = 11
    inline_payload = b"inline"
    frames = b"".join(
        (
            async_client_module._FRAME_STRUCT.pack(1, 1, 0x1000, success_arena),
            async_client_module._FRAME_STRUCT.pack(1, 2, 0, error_frame),
            async_client_module._FRAME_STRUCT.pack(
                1,
                async_client_module._PUBSUB_SENTINEL,
                pubsub_pointer,
                (1 << 63) | pubsub_length,
            ),
            async_client_module._FRAME_STRUCT.pack(
                1,
                async_client_module._PUBSUB_SENTINEL,
                len(inline_payload),
                0,
            ),
            inline_payload,
        )
    )
    monkeypatch.setattr(async_client_module.os, "read", lambda fd, size: frames)

    async_client_module._on_async_pipe_readable()

    fake_lib.free_response_arena.assert_called_once_with(
        ffi.cast("void*", success_arena)
    )
    fake_lib.free_pipe_error_string.assert_called_once_with(
        ffi.cast("char*", error_pointer)
    )
    fake_lib.free_pubsub_pointer_payload.assert_called_once_with(
        ffi.cast("uint8_t*", pubsub_pointer), pubsub_length
    )
    assert async_client_module._pipe_remainder == b""


def test_stale_pipe_drain_frees_mixed_frames_across_partial_reads(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    success_arena = 0x2100
    error_pointer = 0x3100
    pubsub_pointer = 0x4100
    pubsub_length = 17
    inline_payload = b"inline-pubsub-payload"
    frames = b"".join(
        (
            async_client_module._FRAME_STRUCT.pack(1, 1, 0x1100, success_arena),
            async_client_module._FRAME_STRUCT.pack(1, 2, 0, (9 << 56) | error_pointer),
            async_client_module._FRAME_STRUCT.pack(
                1,
                async_client_module._PUBSUB_SENTINEL,
                pubsub_pointer,
                (1 << 63) | pubsub_length,
            ),
            async_client_module._FRAME_STRUCT.pack(
                1,
                async_client_module._PUBSUB_SENTINEL,
                len(inline_payload),
                0,
            ),
            inline_payload,
        )
    )
    boundaries = (5, 41, 79, 123, len(frames) - 3)
    chunks = []
    start = 0
    for end in boundaries:
        chunks.append(frames[start:end])
        start = end
    chunks.append(frames[start:])

    def read_chunk(unused_fd, unused_size):
        if chunks:
            return chunks.pop(0)
        raise BlockingIOError

    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(async_client_module, "_async_pipe_read_fd", 123)
    monkeypatch.setattr(async_client_module, "_pipe_remainder", b"")
    monkeypatch.setattr(async_client_module, "_pipe_remainder_is_stale", False)
    monkeypatch.setattr(async_client_module.os, "read", read_chunk)

    async_client_module._drain_stale_pipe_frames()

    fake_lib.free_response_arena.assert_called_once_with(
        ffi.cast("void*", success_arena)
    )
    fake_lib.free_pipe_error_string.assert_called_once_with(
        ffi.cast("char*", error_pointer)
    )
    fake_lib.free_pubsub_pointer_payload.assert_called_once_with(
        ffi.cast("uint8_t*", pubsub_pointer), pubsub_length
    )
    assert async_client_module._pipe_remainder == b""
    assert not async_client_module._pipe_remainder_is_stale


def test_stale_inline_remainder_is_not_dispatched_or_reparsed_on_new_loop(monkeypatch):
    import glide.glide_client as async_client_module

    ffi = GlideFFI.ffi
    fake_lib = _FakeNativeLibrary(ffi, GlideFFI.lib)
    inline_handler = MagicMock()
    inline_payload = b"payload-split-at-eagain"
    stale_frame = async_client_module._FRAME_STRUCT.pack(
        77,
        async_client_module._PUBSUB_SENTINEL,
        len(inline_payload),
        0,
    )
    stale_prefix = stale_frame[:11]
    drain_chunks = [stale_frame[11:] + inline_payload[:4]]

    def drain_read(unused_fd, unused_size):
        if drain_chunks:
            return drain_chunks.pop(0)
        raise BlockingIOError

    monkeypatch.setattr(
        async_client_module, "_ASYNC_FFI", SimpleNamespace(ffi=ffi, lib=fake_lib)
    )
    monkeypatch.setattr(async_client_module, "_async_pipe_read_fd", 123)
    monkeypatch.setattr(async_client_module, "_pipe_remainder", stale_prefix)
    monkeypatch.setattr(async_client_module, "_pipe_remainder_is_stale", False)
    monkeypatch.setattr(async_client_module, "_client_registry", {77: object()})
    monkeypatch.setattr(async_client_module, "_handle_inline_pubsub", inline_handler)
    monkeypatch.setattr(async_client_module.os, "read", drain_read)

    async_client_module._drain_stale_pipe_frames()

    assert async_client_module._pipe_remainder == stale_frame + inline_payload[:4]
    assert async_client_module._pipe_remainder_is_stale
    fake_lib.free_response_arena.assert_not_called()
    fake_lib.free_pipe_error_string.assert_not_called()
    fake_lib.free_pubsub_pointer_payload.assert_not_called()

    fresh_arena = 0x5100
    fresh_frame = async_client_module._FRAME_STRUCT.pack(88, 3, 0x6100, fresh_arena)
    monkeypatch.setattr(
        async_client_module.os,
        "read",
        lambda unused_fd, unused_size: inline_payload[4:] + fresh_frame,
    )

    async_client_module._on_async_pipe_readable()

    inline_handler.assert_not_called()
    fake_lib.free_response_arena.assert_called_once_with(ffi.cast("void*", fresh_arena))
    fake_lib.free_pipe_error_string.assert_not_called()
    fake_lib.free_pubsub_pointer_payload.assert_not_called()
    assert async_client_module._pipe_remainder == b""
    assert not async_client_module._pipe_remainder_is_stale
