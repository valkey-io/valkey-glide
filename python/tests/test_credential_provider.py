# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import os
import threading
import warnings
from types import SimpleNamespace
from unittest.mock import MagicMock

import anyio
import pytest
import sniffio
from glide.monitor_client import MonitorClient as AsyncMonitorClient
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
from glide_shared.exceptions import ConfigurationError
from glide_shared.ffi_helpers import create_credential_provider_callback
from glide_sync.monitor_client import MonitorClient as SyncMonitorClient


def _invoke_callback(callback, client_id, capacities=(64, 64, 64), fill=0xA5):
    ffi = GlideFFI.ffi
    buffers = [ffi.new("uint8_t[]", max(capacity, 1)) for capacity in capacities]
    for buffer, capacity in zip(buffers, capacities):
        if capacity:
            ffi.buffer(buffer, capacity)[:] = bytes([fill]) * capacity
    lengths = [ffi.new("size_t*", 777 + index) for index in range(3)]
    expiry = ffi.new("int64_t*", 888)
    status = callback(
        client_id,
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


class TestAwsCredentials:
    def test_required_fields_and_optional_values(self):
        credentials = AwsCredentials("access", "secret", "token", 2**63 - 1)
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

    @pytest.mark.parametrize("value", [-(2**63) - 1, 2**63, 1.5, True, "1"])
    def test_expiry_must_fit_signed_int64(self, value):
        with pytest.raises(ValueError, match="signed 64-bit"):
            AwsCredentials("access", "secret", expires_at_epoch_millis=value)  # type: ignore[arg-type]


class TestCredentialProviderConfig:
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
        config = IamAuthConfig(
            "cluster",
            ServiceType.ELASTICACHE,
            "us-east-1",
            credential_provider=sync_provider,
        )
        assert not config._credential_provider_is_async

    def test_non_callable_provider_is_rejected(self):
        with pytest.raises(ValueError, match="callable"):
            IamAuthConfig(
                "cluster",
                ServiceType.ELASTICACHE,
                "us-east-1",
                credential_provider="bad",  # type: ignore[arg-type]
            )


class TestCredentialProviderCallback:
    def test_typed_null_for_missing_provider(self):
        ffi = GlideFFI.ffi
        callback, owner = create_credential_provider_callback(ffi, None)
        assert owner is None
        assert callback == ffi.NULL
        assert ffi.typeof(callback) == ffi.typeof("CredentialProviderCallback")

    def test_success_writes_exact_utf8_sizes(self):
        callback, owner = create_credential_provider_callback(
            GlideFFI.ffi, lambda: AwsCredentials("accéss", "secret")
        )
        assert owner is not None
        status, lengths, expiry, buffers = _invoke_callback(callback, owner.callback_id)
        assert status == 1
        assert lengths == (len("accéss".encode()), 6, 0)
        assert expiry == 0
        assert buffers[0][: lengths[0]] == "accéss".encode()

    def test_large_negotiation_is_stateless(self):
        calls = 0

        def provider():
            nonlocal calls
            calls += 1
            return AwsCredentials("access", "secret", "t" * 8192)

        callback, owner = create_credential_provider_callback(GlideFFI.ffi, provider)
        assert owner is not None
        first = _invoke_callback(callback, owner.callback_id, (2048, 2048, 2048))
        assert first[0] == 2
        assert first[1] == (6, 6, 8192)
        assert first[2] == 888
        second = _invoke_callback(callback, owner.callback_id, (1024 * 1024,) * 3)
        assert second[0] == 1
        assert calls == 2
        assert second[3][2][:8192] == b"t" * 8192

    def test_invalid_mutated_result_fails_without_writes(self):
        credentials = AwsCredentials("access", "secret")
        credentials.access_key_id = " "
        callback, owner = create_credential_provider_callback(
            GlideFFI.ffi, lambda: credentials
        )
        assert owner is not None
        status, lengths, expiry, buffers = _invoke_callback(callback, owner.callback_id)
        assert status == 0
        assert lengths == (777, 778, 779)
        assert expiry == 888
        assert buffers == (b"\xa5" * 64,) * 3

    def test_error_is_redacted_and_late_callback_fails(self, caplog):
        secret = "SECRET_PROVIDER_FAILURE"

        def provider():
            raise RuntimeError(secret)

        callback, owner = create_credential_provider_callback(GlideFFI.ffi, provider)
        assert owner is not None
        with caplog.at_level("WARNING"):
            assert _invoke_callback(callback, owner.callback_id)[0] == 0
        assert secret not in caplog.text
        owner.close()
        assert _invoke_callback(callback, owner.callback_id)[0] == 0

    def test_sync_callback_disposes_awaitable_result(self):
        async def result():
            return AwsCredentials("access", "secret")

        callback, owner = create_credential_provider_callback(
            GlideFFI.ffi, lambda: result()
        )
        assert owner is not None
        with warnings.catch_warnings(record=True) as caught:
            warnings.simplefilter("always")
            assert _invoke_callback(callback, owner.callback_id)[0] == 0
            gc.collect()
        assert not any("was never awaited" in str(item.message) for item in caught)


@pytest.mark.anyio
async def test_async_callback_bridges_asyncio_and_trio_provider_forms():
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

    for provider in (async_provider, lambda: async_provider()):
        callback, owner = create_credential_provider_callback(
            GlideFFI.ffi,
            provider,
            event_loop=event_loop,
            trio_token=trio_token,
            allow_async=True,
        )
        assert owner is not None
        status, lengths, _, buffers = await anyio.to_thread.run_sync(
            lambda: _invoke_callback(callback, owner.callback_id)
        )
        assert status == 1
        assert buffers[0][: lengths[0]] == b"async-access"
        assert buffers[1][: lengths[1]] == b"async-secret"
        owner.close()


def test_credential_trampoline_is_stable_and_registry_is_weak():
    import glide_shared.ffi_helpers as helpers

    callback1, owner1 = create_credential_provider_callback(
        GlideFFI.ffi, lambda: AwsCredentials("access", "secret")
    )
    callback2, owner2 = create_credential_provider_callback(
        GlideFFI.ffi, lambda: AwsCredentials("access", "secret")
    )
    assert owner1 is not None and owner2 is not None
    assert int(GlideFFI.ffi.cast("uintptr_t", callback1)) == int(
        GlideFFI.ffi.cast("uintptr_t", callback2)
    )
    owner1_id = owner1.callback_id
    owner1.close()
    assert helpers._credential_provider_owners.get(owner1_id) is None
    owner2.close()


def _monitor_iam_config(provider=None):
    return GlideClientConfiguration(
        addresses=[NodeAddress(host="invalid.example", port=1)],
        credentials=ServerCredentials(
            username="iam-user",
            iam_config=IamAuthConfig(
                cluster_name="cluster",
                service=ServiceType.ELASTICACHE,
                region="us-east-1",
                credential_provider=provider,
            ),
        ),
    )


@pytest.mark.anyio
async def test_async_monitor_rejects_iam_before_native_creation():
    for provider in (None, lambda: AwsCredentials("access", "secret")):
        with pytest.raises(ConfigurationError, match="does not support IAM"):
            await AsyncMonitorClient.create(_monitor_iam_config(provider))


def test_sync_monitor_rejects_iam_before_native_creation():
    for provider in (None, lambda: AwsCredentials("access", "secret")):
        with pytest.raises(ConfigurationError, match="does not support IAM"):
            SyncMonitorClient.create(_monitor_iam_config(provider))


def test_provider_ids_share_a_unique_high_half_namespace_without_leaks():
    import glide_shared.ffi_helpers as helpers

    ffi = GlideFFI.ffi
    baseline = set(helpers._credential_provider_owners._owners)
    owners = []
    ids = []
    for _ in range(512):
        _, owner = create_credential_provider_callback(
            ffi, lambda: AwsCredentials("access", "secret")
        )
        assert owner is not None
        owners.append(owner)
        ids.append(owner.callback_id)

    namespace_bit = 1 << (ffi.sizeof("uintptr_t") * 8 - 1)
    assert len(ids) == len(set(ids))
    assert all(callback_id & namespace_bit for callback_id in ids)
    for owner in owners:
        owner.close()
    assert set(helpers._credential_provider_owners._owners) == baseline


def test_sync_provider_reentrant_close_fails_fast_for_initial_and_refresh():
    import glide_sync.glide_client as sync_client_module

    client = sync_client_module.GlideClient.__new__(sync_client_module.GlideClient)
    client._is_closed = False
    errors = []

    def provider():
        try:
            client.close()
        except RuntimeError as error:
            errors.append(str(error))
            raise
        return AwsCredentials("access", "secret")

    callback, owner = create_credential_provider_callback(
        GlideFFI.ffi, provider, allow_async=False, provider_owner=client
    )
    assert owner is not None
    for _ in range(2):
        assert _invoke_callback(callback, owner.callback_id)[0] == 0
    assert (
        errors
        == ["Cannot close a client from its own credential provider callback"] * 2
    )
    assert not client._is_closed
    owner.close()


@pytest.mark.parametrize("anyio_backend", ["asyncio", "trio"])
@pytest.mark.anyio
async def test_async_provider_reentrant_close_fails_fast_for_initial_and_refresh(
    anyio_backend,
):
    import glide.glide_client as async_client_module

    client = async_client_module.GlideClient.__new__(async_client_module.GlideClient)
    client._is_closed = False
    errors = []
    if sniffio.current_async_library() == "asyncio":
        event_loop = asyncio.get_running_loop()
        trio_token = None
    else:
        import trio

        event_loop = None
        trio_token = trio.lowlevel.current_trio_token()

    async def provider():
        await anyio.sleep(0)
        try:
            await client.close()
        except RuntimeError as error:
            errors.append(str(error))
            raise
        return AwsCredentials("access", "secret")

    callback, owner = create_credential_provider_callback(
        GlideFFI.ffi,
        provider,
        event_loop=event_loop,
        trio_token=trio_token,
        allow_async=True,
        provider_owner=client,
    )
    assert owner is not None
    for _ in range(2):
        status = await anyio.to_thread.run_sync(
            lambda: _invoke_callback(callback, owner.callback_id)[0]
        )
        assert status == 0
    assert (
        errors
        == ["Cannot close a client from its own credential provider callback"] * 2
    )
    assert not client._is_closed
    owner.close()


@pytest.mark.anyio
async def test_async_pool_shell_close_needs_no_provider_fields(monkeypatch):
    import glide.glide_client as async_client_module

    client = async_client_module.GlideClient.__new__(async_client_module.GlideClient)
    client._ffi = GlideFFI.ffi
    client._lib = SimpleNamespace(close_client=MagicMock())
    client._is_closed = False
    client._pending_futures = {}
    client._pubsub_futures = []
    client._lock = threading.Lock()
    client._pubsub_lock = threading.Lock()
    client._core_client = GlideFFI.ffi.cast("void*", 1)
    client._pipe_client_id = 123
    client._create_pid = os.getpid()
    monkeypatch.setattr(async_client_module, "_client_registry", {123: client})

    await client.close()
    client._lib.close_client.assert_called_once()


def test_sync_pool_shell_close_needs_no_provider_fields():
    import glide_sync.glide_client as sync_client_module

    client = sync_client_module.GlideClient.__new__(sync_client_module.GlideClient)
    client._ffi = GlideFFI.ffi
    client._lib = SimpleNamespace(close_client=MagicMock())
    client._is_closed = False
    client._client_lock = threading.Lock()
    client._pubsub_lock = threading.Lock()
    client._pubsub_condition = threading.Condition(client._pubsub_lock)
    client._pubsub_callback_ref = None
    client._core_client = GlideFFI.ffi.cast("void*", 1)

    client.close()
    client._lib.close_client.assert_called_once()


@pytest.mark.parametrize("failure_point", ["pipe", "set_blocking"])
def test_async_direct_provider_pipe_oserror_rolls_back_only_provider(
    monkeypatch, failure_point
):
    import glide.glide_client as async_client_module

    original_pipe = os.pipe
    ffi = GlideFFI.ffi
    core_client = ffi.cast("void*", 0x7056)
    fake_lib = SimpleNamespace(
        close_client=MagicMock(),
        init_async_pipe=MagicMock(),
        reinit_async_pipe=MagicMock(),
    )
    created_fds = []

    def fail_pipe():
        raise OSError("injected pipe setup failure")

    def tracked_pipe():
        fds = original_pipe()
        created_fds.extend(fds)
        return fds

    def fail_set_blocking(_fd, _blocking):
        raise OSError("injected pipe setup failure")

    if failure_point == "pipe":
        monkeypatch.setattr(async_client_module.os, "pipe", fail_pipe)
    else:
        monkeypatch.setattr(async_client_module.os, "pipe", tracked_pipe)
        monkeypatch.setattr(async_client_module.os, "set_blocking", fail_set_blocking)

    monkeypatch.setattr(async_client_module, "_client_registry", {})
    monkeypatch.setattr(async_client_module, "_async_pipe_read_fd", -1)
    monkeypatch.setattr(async_client_module, "_async_pipe_write_fd", -1)
    monkeypatch.setattr(async_client_module, "_async_pipe_registered", False)
    monkeypatch.setattr(async_client_module, "_async_pipe_loop", None)
    monkeypatch.setattr(async_client_module, "_async_pipe_init_pid", 0)

    provider_owner = MagicMock()
    provider_client = async_client_module.GlideClient.__new__(
        async_client_module.GlideClient
    )
    provider_client._lib = fake_lib
    provider_client._lock = threading.Lock()
    provider_client._is_asyncio = True
    provider_client._loop = None
    provider_client._is_closed = False
    provider_client._core_client = core_client
    provider_client._pipe_client_id = 901
    provider_client._create_pid = os.getpid()
    provider_client._credential_provider_callback_owner = provider_owner
    provider_client._credential_provider_callback_ref = object()

    ordinary_client = async_client_module.GlideClient.__new__(
        async_client_module.GlideClient
    )
    ordinary_client._lib = fake_lib
    ordinary_client._lock = threading.Lock()
    ordinary_client._is_asyncio = True
    ordinary_client._loop = None
    ordinary_client._is_closed = False
    ordinary_client._core_client = core_client
    ordinary_client._pipe_client_id = 902
    ordinary_client._create_pid = os.getpid()
    ordinary_client._credential_provider_callback_owner = None
    ordinary_client._credential_provider_callback_ref = None

    with pytest.raises(OSError, match="injected pipe setup failure"):
        provider_client._setup_pipe_after_creation(True)

    assert provider_client._is_closed
    assert provider_client._core_client is None
    fake_lib.close_client.assert_called_once_with(core_client)
    provider_owner.close.assert_called_once_with()
    assert async_client_module._async_pipe_read_fd == -1
    assert async_client_module._async_pipe_write_fd == -1
    assert async_client_module._async_pipe_init_pid == 0
    assert len(created_fds) == (2 if failure_point == "set_blocking" else 0)
    for fd in created_fds:
        with pytest.raises(OSError):
            os.fstat(fd)

    ordinary_client._setup_pipe_after_creation(False)
    assert not ordinary_client._is_closed
    assert ordinary_client._core_client == core_client
    fake_lib.close_client.assert_called_once_with(core_client)
    assert async_client_module._async_pipe_read_fd == -1
    assert async_client_module._async_pipe_write_fd == -1
    assert async_client_module._async_pipe_init_pid == 0
    assert len(created_fds) == (4 if failure_point == "set_blocking" else 0)
    for fd in created_fds:
        with pytest.raises(OSError):
            os.fstat(fd)


@pytest.mark.parametrize("anyio_backend", ["asyncio"])
@pytest.mark.anyio
async def test_async_direct_provider_partial_pipe_setup_failure_releases_ownership(
    monkeypatch, anyio_backend
):
    import glide.glide_client as async_client_module
    import glide_shared.ffi_helpers as helpers

    original_ffi = async_client_module._ASYNC_FFI
    ffi = original_ffi.ffi

    class FakeLib:
        def __init__(self):
            self.noop_success_callback = original_ffi.lib.noop_success_callback
            self.noop_failure_callback = original_ffi.lib.noop_failure_callback
            self.core_client = ffi.cast("void*", 0x7055)
            self.response = ffi.new(
                "ConnectionResponse*",
                {
                    "conn_ptr": self.core_client,
                    "connection_error_message": ffi.NULL,
                },
            )
            self.closed = []
            self.pipe_write_fds = []

        def create_client_with_credential_provider(self, *_args):
            return self.response

        def free_connection_response(self, _response):
            return None

        def close_client(self, core_client):
            self.closed.append(int(ffi.cast("uintptr_t", core_client)))

        def init_async_pipe(self, pipe_write_fd):
            self.pipe_write_fds.append(pipe_write_fd)

        def reinit_async_pipe(self, pipe_write_fd):
            self.pipe_write_fds.append(pipe_write_fd)

    fake_lib = FakeLib()
    registry = {}
    pipe_read_fds = []
    registered_pipe_ids = []
    loop = asyncio.get_running_loop()

    def fail_add_reader(pipe_read_fd, _callback):
        pipe_read_fds.append(pipe_read_fd)
        registered_pipe_ids.extend(registry)
        raise RuntimeError("injected add_reader failure")

    with helpers._credential_provider_owners._lock:
        baseline_owner_ids = set(helpers._credential_provider_owners._owners)
    monkeypatch.setattr(
        async_client_module,
        "_ASYNC_FFI",
        SimpleNamespace(ffi=ffi, lib=fake_lib),
    )
    monkeypatch.setattr(async_client_module, "_client_registry", registry)
    monkeypatch.setattr(async_client_module, "_async_pipe_read_fd", -1)
    monkeypatch.setattr(async_client_module, "_async_pipe_write_fd", -1)
    monkeypatch.setattr(async_client_module, "_async_pipe_registered", False)
    monkeypatch.setattr(async_client_module, "_async_pipe_loop", None)
    monkeypatch.setattr(async_client_module, "_async_pipe_init_pid", 0)
    monkeypatch.setattr(loop, "add_reader", fail_add_reader)

    config = GlideClientConfiguration(
        addresses=[NodeAddress("127.0.0.1", 1)],
        lazy_connect=True,
        credentials=ServerCredentials(
            username="iam-user",
            iam_config=IamAuthConfig(
                "cluster",
                ServiceType.ELASTICACHE,
                "us-east-1",
                credential_provider=lambda: AwsCredentials("access", "secret"),
            ),
        ),
    )

    try:
        with pytest.raises(RuntimeError, match="injected add_reader failure"):
            await async_client_module.GlideClient.create(config)

        assert len(pipe_read_fds) == 1
        assert len(fake_lib.pipe_write_fds) == 1
        assert len(registered_pipe_ids) == 1
        assert registered_pipe_ids[0] not in registry
        assert fake_lib.closed == [0x7055]
        with helpers._credential_provider_owners._lock:
            assert (
                set(helpers._credential_provider_owners._owners) == baseline_owner_ids
            )
    finally:
        for fd in {*pipe_read_fds, *fake_lib.pipe_write_fds}:
            os.close(fd)
