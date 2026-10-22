# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""RDMA on the synchronous client: regions, windows, and what each refuses.

The transfer tests need a server that serves the ``BLOB.RDMA_*`` commands, so they
are skipped unless ``GLIDE_RDMA_SERVER`` names one. Everything else runs
anywhere, including on a build with no RDMA compiled in.
"""

from __future__ import annotations

import array
import os
import socket
import threading
import time

import pytest
from glide_shared.config import (
    GlideClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    RdmaConfiguration,
    RdmaProvider,
    RdmaSetChecksum,
)
from glide_shared.exceptions import (
    ClosingError,
    ConnectionError,
    GlideError,
    RdmaError,
    TimeoutError,
)
from glide_sync import GlideClient, RdmaRegion, RdmaWindow
from glide_sync.glide_client import _RETAINED_RDMA_MEMORY

REGION_BYTES = 1024


class _FakeFfi:
    """Enough of CFFI for a region that is never transferred with."""

    NULL = object()

    def from_buffer(self, memory):
        return memory

    def string(self, message):
        return message


class _FakeLib:
    """Records the frees a region performs, so lifetime can be asserted."""

    RDMA_SET_CHECKSUM_SKIP = 0
    RDMA_SET_CHECKSUM_COMPUTE = 1
    RDMA_SET_CHECKSUM_KNOWN = 2

    def __init__(self):
        # The arguments of every `rdma_set` call, in order.
        self.sets = []
        self.freed = []
        self.freed_results = []
        # What `free_rdma_region` hands back for each region: NULL, or a hold
        # standing in for memory the fabric still keeps.
        self.holds = {}
        self.freed_holds = []
        # The size of every buffer `register_rdma_region` was asked to register.
        self.registrations = []
        # What `rdma_set` reports, if not a success.
        self.set_result = None

    def rdma_region_capacity(self, region):
        return region["capacity"]

    def free_rdma_region(self, region):
        self.freed.append(region)
        return self.holds.get(id(region), _FakeFfi.NULL)

    def rdma_memory_released(self, hold):
        return hold["released"]

    def free_rdma_memory_hold(self, hold):
        self.freed_holds.append(hold)

    def register_rdma_region(self, client, buffer, length):
        self.registrations.append(length)
        return _FakeRegistration({"capacity": length})

    def free_rdma_registration(self, registration):
        pass

    def free_rdma_result(self, result):
        self.freed_results.append(result)

    def rdma_set(self, *arguments):
        self.sets.append(arguments)
        if self.set_result is not None:
            return self.set_result
        return _FakeResult(_FakeFfi.NULL, 0, found=True)


def _offline_client() -> GlideClient:
    """A client with the FFI stubbed out, for the pure-Python checks.

    Those checks run before anything crosses the boundary, so they can be
    exercised without a server, a fabric, or an RDMA-capable build.
    """
    # Bypassing __init__ is what keeps this offline: every check below runs
    # before anything reaches the FFI, so the client needs no connection. The
    # ignore covers Protocol members the client inherits, which type checkers
    # count as abstract; at runtime it has none.
    client = GlideClient.__new__(GlideClient)  # type: ignore[type-abstract]
    client._ffi = _FakeFfi()
    client._lib = _FakeLib()
    client._is_closed = False
    return client


def _offline_region(client: GlideClient, capacity: int = REGION_BYTES) -> RdmaRegion:
    return RdmaRegion(client, bytearray(capacity), {"capacity": capacity})


class _FakeRegistration:
    """A registration as the FFI reports one that succeeded."""

    def __init__(self, region):
        self.error_message = _FakeFfi.NULL
        self.region = region


class _FakeResult:
    """A transfer result as the FFI reports one: failed unless `found` is set."""

    def __init__(
        self,
        error_message,
        error_type: int,
        found: bool = False,
        region_closed: bool = False,
    ):
        self.error_message = error_message
        self.error_type = error_type
        self.found = found
        self.bytes_written = 0
        self.has_checksum = False
        self.checksum = 0
        self.region_closed = region_closed


def test_rdma_availability_is_reported_as_a_bool():
    # A binding gates its RDMA surface on this rather than probing for symbols,
    # so it must answer on every build.
    assert isinstance(GlideClient.rdma_available(), bool)


def test_rdma_usability_is_reported_as_a_bool():
    # The second question: this build has RDMA, but can this machine carry it?
    assert isinstance(GlideClient.rdma_usable(), bool)


def test_a_machine_cannot_be_usable_without_the_support():
    # Usable is the narrower claim of the two. The reverse is ordinary: the
    # published packages have RDMA compiled in, and most machines running them
    # have no libfabric.
    assert not GlideClient.rdma_usable() or GlideClient.rdma_available()


def test_a_checksum_matches_the_protocols_own():
    # The value the server computes over the same bytes, so a caller can verify
    # a transfer independently of one.
    if not GlideClient.rdma_available():
        with pytest.raises(RdmaError, match="no RDMA support"):
            GlideClient.rdma_checksum(b"123456789")
        return

    assert GlideClient.rdma_checksum(b"") == 0x0000_0000
    assert GlideClient.rdma_checksum(b"123456789") == 0xE306_9283
    assert GlideClient.rdma_checksum(bytearray(b"123456789")) == 0xE306_9283
    assert GlideClient.rdma_checksum(b"ab") != GlideClient.rdma_checksum(b"ba")


def test_a_region_reports_its_capacity():
    region = _offline_region(_offline_client())

    assert region.capacity == REGION_BYTES
    assert region.closed is False


def test_closing_a_region_is_idempotent():
    # Closing twice has to be safe, or a region cannot be released in a finally
    # block that also runs on the success path.
    client = _offline_client()
    region = _offline_region(client)

    region.close()
    region.close()

    assert region.closed is True
    assert len(client._lib.freed) == 1


def test_a_region_closes_when_its_context_exits():
    client = _offline_client()
    with _offline_region(client) as region:
        assert region.closed is False

    assert region.closed is True
    assert len(client._lib.freed) == 1


def test_closing_a_region_lets_go_of_the_memory_when_the_fabric_has():
    client = _offline_client()
    memory = bytearray(REGION_BYTES)
    region = RdmaRegion(client, memory, {"capacity": REGION_BYTES})

    region.close()

    assert not _RETAINED_RDMA_MEMORY.holds(memory)
    assert client._lib.freed_holds == []


def test_a_closed_region_keeps_memory_the_fabric_still_holds():
    # On tcp a transfer that closed its region leaves the memory parked, and a
    # write could still land in it. Python must not free it until
    # the fabric lets go, even though the region is closed.
    client = _offline_client()
    memory = bytearray(REGION_BYTES)
    raw = {"capacity": REGION_BYTES}
    hold = {"released": False}
    client._lib.holds[id(raw)] = hold
    region = RdmaRegion(client, memory, raw)

    region.close()

    assert _RETAINED_RDMA_MEMORY.holds(memory), "kept while the fabric holds it"
    assert client._lib.freed_holds == []

    # The fabric lets go, for example when the client's fabric closes. The next
    # region close or client close notices.
    hold["released"] = True
    _offline_region(client).close()

    assert not _RETAINED_RDMA_MEMORY.holds(memory)
    assert client._lib.freed_holds == [hold]


def test_memory_kept_for_a_failed_close_stays_kept():
    # A region that failed to close may still be written by the device, so its
    # hold is never released and the memory stays referenced.
    client = _offline_client()
    memory = bytearray(REGION_BYTES)
    raw = {"capacity": REGION_BYTES}
    hold = {"released": False}
    client._lib.holds[id(raw)] = hold
    RdmaRegion(client, memory, raw).close()

    for _ in range(3):
        _offline_region(client).close()

    assert _RETAINED_RDMA_MEMORY.holds(memory)
    # Let the shared keeper go of it, so other tests start clean.
    hold["released"] = True
    _offline_region(client).close()
    assert not _RETAINED_RDMA_MEMORY.holds(memory)


def test_a_closed_region_cannot_be_transferred_with():
    client = _offline_client()
    region = _offline_region(client)
    window = region.window()
    region.close()

    with pytest.raises(RdmaError, match="closed"):
        region.window()

    with pytest.raises(RdmaError, match="closed"):
        region.memoryview()

    # A window outlives the close that invalidates it, so the region is checked
    # again at the transfer rather than trusted because the window exists.
    with pytest.raises(RdmaError, match="closed"):
        client._rdma_window(window)


def test_a_region_from_another_client_is_refused():
    # Each client opens its own fabric, so a region registered on one is
    # unreachable from another. Naming that here beats the core's generic
    # "registered on a different fabric".
    owner = _offline_client()
    other = _offline_client()
    window = _offline_region(owner).window()

    with pytest.raises(RdmaError, match="different client"):
        other._rdma_window(window)


def test_a_missing_length_covers_the_rest_of_the_region():
    window = _offline_region(_offline_client()).window(256)

    assert (window.offset, window.length) == (256, REGION_BYTES - 256)


def test_the_whole_region_is_the_default_window():
    region = _offline_region(_offline_client())

    window = region.window()

    assert isinstance(window, RdmaWindow)
    assert (window.region, window.offset, window.length) == (region, 0, REGION_BYTES)


def test_a_window_views_only_its_own_bytes():
    # The view is what a caller stages into and reads out of, so it has to be
    # the window's bytes and not the region's.
    region = _offline_region(_offline_client())
    region.memoryview()[64:96] = b"\x07" * 32

    view = region.window(64, 32).memoryview()

    assert view.nbytes == 32
    assert bytes(view) == b"\x07" * 32


def test_a_window_of_a_wide_buffer_is_still_measured_in_bytes():
    # A region is registered, addressed and transferred in bytes whatever the
    # buffer underneath is made of, so a view that indexed by element would put
    # a caller's staging area somewhere other than where the transfer runs.
    numbers = array.array("i", [0] * (REGION_BYTES // 4))
    assert numbers.itemsize == 4
    region = RdmaRegion(_offline_client(), numbers, {"capacity": REGION_BYTES})

    region.memoryview()[64:96] = b"\x07" * 32
    view = region.window(64, 32).memoryview()

    assert region.memoryview().nbytes == REGION_BYTES
    assert view.nbytes == 32
    assert bytes(view) == b"\x07" * 32
    # The bytes landed in the second element onwards, not the sixty-fifth.
    assert numbers[16:24] == array.array("i", [0x07070707] * 8)


@pytest.mark.parametrize(
    ("error_type", "expected"),
    [
        (0, RdmaError),
        (2, TimeoutError),
        (3, ConnectionError),
    ],
)
def test_a_failed_transfer_raises_the_class_its_type_names(error_type, expected):
    # A transfer fails the same ways any other command does, so it has to be
    # catchable the same ways. An unclassified failure stays RdmaError, which
    # the others are also a kind of.
    client = _offline_client()
    result = _FakeResult(b"transfer failed", error_type)

    with pytest.raises(expected, match="transfer failed"):
        client._handle_rdma_result(result, _offline_region(client))

    assert client._lib.freed_results == [result]


@pytest.mark.parametrize("error_type", [0, 2, 3])
def test_a_transfer_cancelled_by_closing_the_client_raises_closing_error(error_type):
    # Closing the client is how a transfer is cancelled, so the failure it
    # causes has to read as the close, not as a fault in the transfer. The
    # message is exactly how the core words it.
    client = _offline_client()
    client._is_closed = True
    result = _FakeResult(
        b"RDMA transfer cancelled - ClientError: the client was closed", error_type
    )

    with pytest.raises(ClosingError, match="the client was closed"):
        client._handle_rdma_result(result, _offline_region(client))

    assert client._lib.freed_results == [result]


def test_an_open_region_has_not_released_its_memory():
    region = _offline_region(_offline_client())

    assert region.memory_released is False


def test_a_closed_region_has_released_memory_the_fabric_let_go_of():
    region = _offline_region(_offline_client())

    region.close()

    assert region.memory_released is True


def test_memory_released_follows_the_fabric_after_close():
    # On tcp the fabric holds the memory past the close, then lets go once the
    # client's fabric closes. The property has to change when that happens.
    client = _offline_client()
    raw = {"capacity": REGION_BYTES}
    hold = {"released": False}
    client._lib.holds[id(raw)] = hold
    region = RdmaRegion(client, bytearray(REGION_BYTES), raw)

    region.close()
    assert region.memory_released is False

    hold["released"] = True
    assert region.memory_released is True
    assert client._lib.freed_holds == [hold]


@pytest.mark.parametrize(
    ("error_type", "expected"),
    [
        (3, ConnectionError),
        (0, RdmaError),
    ],
)
def test_a_transfer_that_closed_its_region_closes_the_python_region(
    error_type, expected
):
    # The FFI says the region can no longer transfer, whatever the error class.
    # Closing it here makes `region.closed` the one thing a caller checks.
    client = _offline_client()
    raw = {"capacity": REGION_BYTES}
    region = RdmaRegion(client, bytearray(REGION_BYTES), raw)
    result = _FakeResult(b"connection lost", error_type, region_closed=True)

    with pytest.raises(expected):
        client._handle_rdma_result(result, region)

    assert region.closed is True
    assert client._lib.freed == [raw]
    assert client._lib.freed_results == [result]
    assert region.memory_released is True


def test_a_cancelled_transfer_closes_the_python_region():
    client = _offline_client()
    client._is_closed = True
    region = _offline_region(client)
    result = _FakeResult(
        b"RDMA transfer cancelled - ClientError: the client was closed",
        0,
        region_closed=True,
    )

    with pytest.raises(ClosingError):
        client._handle_rdma_result(result, region)

    assert region.closed is True


def test_a_region_closed_on_tcp_keeps_its_memory_until_the_fabric_lets_go():
    client = _offline_client()
    raw = {"capacity": REGION_BYTES}
    hold = {"released": False}
    client._lib.holds[id(raw)] = hold
    memory = bytearray(REGION_BYTES)
    region = RdmaRegion(client, memory, raw)
    result = _FakeResult(b"connection lost", 3, region_closed=True)

    with pytest.raises(ConnectionError):
        client._handle_rdma_result(result, region)

    assert region.closed is True
    assert region.memory_released is False
    assert _RETAINED_RDMA_MEMORY.holds(memory)
    # Let the shared keeper go of it, so other tests start clean.
    hold["released"] = True
    assert region.memory_released is True


def test_a_failure_that_leaves_the_region_usable_keeps_it_open():
    # A server reply, such as a refused checksum, proves the server is done
    # with the memory, so the same region serves the next transfer.
    client = _offline_client()
    region = _offline_region(client)
    result = _FakeResult(b"CRC mismatch", 0)

    with pytest.raises(RdmaError):
        client._handle_rdma_result(result, region)

    assert region.closed is False
    assert client._lib.freed == []


def test_a_success_that_closed_the_region_still_returns_its_receipt():
    # A reply can win the race with a close that also revoked the region: the
    # bytes landed, but the region cannot be used again.
    client = _offline_client()
    region = _offline_region(client)
    result = _FakeResult(_FakeFfi.NULL, 0, found=True, region_closed=True)

    receipt = client._handle_rdma_result(result, region)

    assert receipt is not None
    assert region.closed is True


def _close_holding(client: GlideClient, memory) -> dict:
    """Close a region over `memory` whose memory the fabric keeps holding.

    Returns the hold. Set its ``"released"`` to let the memory go.
    """
    raw = {"capacity": memoryview(memory).nbytes}
    hold = {"released": False}
    client._lib.holds[id(raw)] = hold
    RdmaRegion(client, memory, raw).close()
    return hold


@pytest.mark.parametrize(
    "same_bytes",
    [
        pytest.param(lambda memory: memory, id="same-object"),
        pytest.param(memoryview, id="other-view"),
        pytest.param(lambda memory: memoryview(memory)[16:32], id="slice"),
        pytest.param(lambda memory: memoryview(memory).cast("I"), id="cast"),
    ],
)
def test_memory_still_held_is_refused_for_a_new_region(same_bytes):
    # A late write from the closed region's transfer could land in a new
    # transfer's data, so the memory must not be registered again, by any view.
    client = _offline_client()
    client._core_client = object()
    memory = bytearray(REGION_BYTES)
    hold = _close_holding(client, memory)
    try:
        with pytest.raises(RdmaError, match="could still write into it"):
            client.register_rdma_region(same_bytes(memory))
        assert client._lib.registrations == [], "refused before the fabric"
    finally:
        # Let the shared keeper go of it, so other tests start clean.
        hold["released"] = True
        _RETAINED_RDMA_MEMORY.release_what_is_free()


def test_other_memory_registers_while_some_is_held():
    client = _offline_client()
    client._core_client = object()
    memory = bytearray(2 * REGION_BYTES)
    # Hold only the first half, so the second half sits right next to it.
    hold = _close_holding(client, memoryview(memory)[:REGION_BYTES])
    try:
        client.register_rdma_region(memoryview(memory)[REGION_BYTES:]).close()
        client.register_rdma_region(bytearray(REGION_BYTES)).close()
        assert client._lib.registrations == [REGION_BYTES, REGION_BYTES]
    finally:
        hold["released"] = True
        _RETAINED_RDMA_MEMORY.release_what_is_free()


def test_memory_registers_again_once_the_fabric_lets_go():
    client = _offline_client()
    client._core_client = object()
    memory = bytearray(REGION_BYTES)
    hold = _close_holding(client, memory)

    hold["released"] = True
    region = client.register_rdma_region(memory)

    assert region.capacity == REGION_BYTES
    region.close()


def _set_with_recovery(client: GlideClient, state: dict) -> None:
    """The recovery the documentation recommends, exactly as a caller writes it.

    `state` holds the caller's ``"slab"`` and its current ``"region"``, which this
    replaces when it registers the slab again.
    """
    try:
        client.rdma_set(b"key", state["region"].window(0, 8))
    except GlideError:
        if state["region"].closed and not client.closed:
            state["region"] = client.register_rdma_region(state["slab"])
        raise


def _recovery_state(client: GlideClient) -> dict:
    client._core_client = object()
    slab = bytearray(REGION_BYTES)
    return {"slab": slab, "region": client.register_rdma_region(slab)}


def test_recovery_registers_the_memory_again_after_a_lost_connection():
    client = _offline_client()
    state = _recovery_state(client)
    lost = state["region"]
    client._lib.set_result = _FakeResult(b"connection lost", 3, region_closed=True)

    with pytest.raises(ConnectionError, match="connection lost"):
        _set_with_recovery(client, state)

    assert lost.closed
    assert state["region"] is not lost and not state["region"].closed
    assert client._lib.registrations == [REGION_BYTES, REGION_BYTES]
    state["region"].close()


def test_recovery_keeps_a_region_the_failure_left_usable():
    client = _offline_client()
    state = _recovery_state(client)
    usable = state["region"]
    client._lib.set_result = _FakeResult(b"CRC mismatch", 0)

    with pytest.raises(RdmaError, match="CRC mismatch"):
        _set_with_recovery(client, state)

    assert state["region"] is usable and not usable.closed
    assert client._lib.registrations == [REGION_BYTES]
    usable.close()


def test_recovery_leaves_a_closed_client_alone():
    # A transfer cancelled by closing the client: registering again could only
    # raise ClosingError over the original error, so the recovery skips it.
    client = _offline_client()
    state = _recovery_state(client)
    client._lib.set_result = _FakeResult(
        b"RDMA transfer cancelled: the client was closed", 0, region_closed=True
    )
    transfer = client._lib.rdma_set

    def closed_mid_transfer(*arguments):
        # Another thread closes the client while this transfer is blocked.
        client._is_closed = True
        return transfer(*arguments)

    client._lib.rdma_set = closed_mid_transfer

    with pytest.raises(ClosingError, match="the client was closed"):
        _set_with_recovery(client, state)

    assert state["region"].closed
    assert client._lib.registrations == [REGION_BYTES]


def test_recovery_refuses_memory_the_fabric_still_holds():
    # On tcp the lost transfer's memory stays held, so registering it again
    # raises RdmaError, with the original failure as its context.
    client = _offline_client()
    state = _recovery_state(client)
    hold = {"released": False}
    client._lib.holds[id(state["region"]._region)] = hold
    client._lib.set_result = _FakeResult(b"connection lost", 3, region_closed=True)
    try:
        with pytest.raises(RdmaError, match="could still write into it") as raised:
            _set_with_recovery(client, state)
        assert isinstance(raised.value.__context__, ConnectionError)
        assert client._lib.registrations == [REGION_BYTES]
    finally:
        hold["released"] = True
        _RETAINED_RDMA_MEMORY.release_what_is_free()


def _offline_set(checksum=None):
    """Call `rdma_set` on an offline client and return the FFI's record of it."""
    client = _offline_client()
    client._core_client = object()
    region = _offline_region(client)
    if checksum is None:
        client.rdma_set(b"key", region.window(0, 8))
    else:
        client.rdma_set(b"key", region.window(0, 8), checksum=checksum)
    return client._lib


@pytest.mark.parametrize(
    ("checksum", "mode", "value"),
    [
        (RdmaSetChecksum.Skip(), _FakeLib.RDMA_SET_CHECKSUM_SKIP, 0),
        (RdmaSetChecksum.Compute(), _FakeLib.RDMA_SET_CHECKSUM_COMPUTE, 0),
        (
            RdmaSetChecksum.Known(0xE3069283),
            _FakeLib.RDMA_SET_CHECKSUM_KNOWN,
            0xE3069283,
        ),
        (RdmaSetChecksum.Known(0), _FakeLib.RDMA_SET_CHECKSUM_KNOWN, 0),
        (
            RdmaSetChecksum.Known(0xFFFFFFFF),
            _FakeLib.RDMA_SET_CHECKSUM_KNOWN,
            0xFFFFFFFF,
        ),
    ],
)
def test_a_checksum_choice_reaches_the_ffi_as_its_mode_and_value(checksum, mode, value):
    lib = _offline_set(checksum)
    assert len(lib.sets) == 1
    assert lib.sets[0][-2:] == (mode, value)


def test_a_write_sends_no_checksum_by_default():
    lib = _offline_set()
    assert lib.sets[0][-2:] == (_FakeLib.RDMA_SET_CHECKSUM_SKIP, 0)


@pytest.mark.parametrize("value", [-1, 0x1_0000_0000, True, "123", 1.0])
def test_a_known_checksum_outside_32_bits_is_refused_before_the_ffi(value):
    client = _offline_client()
    client._core_client = object()
    region = _offline_region(client)

    with pytest.raises(ValueError, match="unsigned 32-bit integer"):
        client.rdma_set(b"key", region.window(0, 8), RdmaSetChecksum.Known(value))

    assert client._lib.sets == []


@pytest.mark.parametrize("checksum", [None, 0, "compute", RdmaSetChecksum])
def test_a_checksum_that_is_not_a_choice_is_refused_before_the_ffi(checksum):
    client = _offline_client()
    client._core_client = object()
    region = _offline_region(client)

    with pytest.raises(TypeError, match="RdmaSetChecksum"):
        client.rdma_set(b"key", region.window(0, 8), checksum)

    assert client._lib.sets == []


def test_a_real_failure_is_reported_even_if_the_client_closed_meanwhile():
    # A read whose reply claimed more bytes than the window, then a close: the
    # caller must still learn about the oversized reply, not just the close.
    client = _offline_client()
    client._is_closed = True
    result = _FakeResult(
        b"RDMA error - UserOperationError: value of 8192 bytes exceeds the 4096 "
        b"byte registered window",
        0,
    )

    with pytest.raises(RdmaError, match="exceeds") as raised:
        client._handle_rdma_result(result, _offline_region(client))

    assert not isinstance(raised.value, ClosingError)


@pytest.mark.parametrize(
    ("offset", "length", "message"),
    [
        (-1, None, "offset must not be negative"),
        (REGION_BYTES + 1, None, "past the end"),
        (0, -1, "length must not be negative"),
        (0, REGION_BYTES + 1, "runs past the end"),
        (REGION_BYTES - 1, 2, "runs past the end"),
    ],
)
def test_a_window_outside_the_region_is_refused(offset, length, message):
    # Caught when the window is made rather than on the wire: a bad window would
    # otherwise cost a round trip to learn what the region's own size says.
    region = _offline_region(_offline_client())

    with pytest.raises(RdmaError, match=message):
        region.window(offset, length)


def test_a_non_window_is_refused():
    client = _offline_client()
    region = _offline_region(client)

    with pytest.raises(TypeError, match="must be an RdmaWindow"):
        client._rdma_window(object())

    # A region is not a window: the transfer needs bounds, not a whole region.
    with pytest.raises(TypeError, match="must be an RdmaWindow"):
        client._rdma_window(region)


@pytest.mark.parametrize("cluster_mode", [True, False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
@pytest.mark.parametrize(
    ("memory", "message"),
    [
        pytest.param(bytes(REGION_BYTES), "must be writable", id="read-only"),
        pytest.param(memoryview(bytearray(8))[::2], "C-contiguous", id="strided"),
    ],
)
def test_unusable_memory_is_refused_before_registration(
    glide_sync_client: GlideClient, memory, message
):
    # The server writes into this memory directly, so it has to be writable and
    # laid out the way the fabric will address it.
    with pytest.raises(TypeError, match=message):
        glide_sync_client.register_rdma_region(memory)


@pytest.mark.parametrize("cluster_mode", [True, False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
def test_registering_without_a_fabric_names_the_cause(glide_sync_client: GlideClient):
    # The common mistake: RDMA calls on a client that was never configured for
    # it. The error has to say which configuration is missing, or, in a build
    # without RDMA, that the support itself is missing.
    cause = (
        "RdmaConfiguration"
        if GlideClient.rdma_available()
        else "no RDMA support compiled in"
    )
    with pytest.raises(RdmaError, match=cause):
        glide_sync_client.register_rdma_region(bytearray(REGION_BYTES))


@pytest.mark.parametrize("cluster_mode", [True, False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
def test_an_rdma_client_against_a_plain_server_still_works(
    glide_sync_client: GlideClient,
):
    # Asking for RDMA asks for a faster path to the keys the caller chooses. A
    # server that cannot serve it is still a server, so the client is built and
    # ordinary commands are untouched. The handshake is what such a server
    # refuses, and it is sent by the first transfer, so that is where the refusal
    # is reported. A build with no RDMA support fails earlier, before any
    # connection is made, because there is no fabric to open.
    config = type(glide_sync_client._config)(
        addresses=glide_sync_client._config.addresses,
        rdma=RdmaConfiguration(provider=RdmaProvider.Tcp()),
    )

    if not GlideClient.rdma_usable():
        with pytest.raises(Exception) as caught:
            type(glide_sync_client).create(config)
        message = str(caught.value)
        assert (
            "no RDMA support compiled in" in message
            or "libfabric unavailable" in message
        ), f"unhelpful message for an unusable machine: {message}"
        return

    client = type(glide_sync_client).create(config)
    try:
        client.set(b"plain-key", b"plain-value")
        assert client.get(b"plain-key") == b"plain-value"

        region = client.register_rdma_region(bytearray(REGION_BYTES))
        try:
            with pytest.raises(RdmaError) as caught:
                client.rdma_get(b"plain-key", region.window(0, REGION_BYTES))
            message = str(caught.value)
            assert "no RDMA module loaded" in message, message
            assert region.closed is False, "a refusal is a reply, so it stays open"
        finally:
            region.close()
    finally:
        client.close()


class _DroppingProxy:
    """
    Forwards one server's connections, but drops a connection instead of
    forwarding a command that contains `trigger`, so the command is never
    answered and the client sees the connection lost.
    """

    def __init__(self, server: NodeAddress, trigger: bytes):
        self._server = (server.host, server.port)
        self._trigger = trigger
        self._listener = socket.create_server(("127.0.0.1", 0))
        self.address = NodeAddress("127.0.0.1", self._listener.getsockname()[1])
        self.dropped = threading.Event()
        threading.Thread(target=self._accept, daemon=True).start()

    def _accept(self):
        while True:
            try:
                client, _ = self._listener.accept()
            except OSError:
                return
            server = socket.create_connection(self._server)
            threading.Thread(
                target=self._pipe, args=(client, server, True), daemon=True
            ).start()
            threading.Thread(
                target=self._pipe, args=(server, client, False), daemon=True
            ).start()

    def _pipe(self, source, sink, from_client):
        try:
            while data := source.recv(65536):
                if from_client and self._trigger in data:
                    self.dropped.set()
                    break
                sink.sendall(data)
        except OSError:
            pass
        for end in (source, sink):
            try:
                end.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            end.close()

    def close(self):
        self._listener.close()


@pytest.mark.parametrize("cluster_mode", [False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
def test_a_transfer_whose_connection_is_lost_closes_its_region(
    glide_sync_client: GlideClient,
):
    # Losing the connection mid-transfer says nothing about whether the server
    # got the command, so the core closes the region. The caller sees that on
    # the region itself, and the client keeps working: a new region is all the
    # next transfer needs. The transfer's first command is the handshake, which
    # the proxy drops, and the plain server behind it would refuse anyway.
    if not GlideClient.rdma_usable():
        pytest.skip("needs libfabric to open a tcp fabric")
    proxy = _DroppingProxy(glide_sync_client._config.addresses[0], b"BLOB.RDMA")
    client = GlideClient.create(
        GlideClientConfiguration(
            addresses=[proxy.address],
            rdma=RdmaConfiguration(provider=RdmaProvider.Tcp()),
        )
    )
    try:
        region = client.register_rdma_region(bytearray(REGION_BYTES))

        with pytest.raises(ConnectionError):
            client.rdma_get(b"glide-py-rdma-lost", region.window())

        assert proxy.dropped.is_set()
        assert region.closed is True
        # On tcp a write could still land, so the memory stays held until the
        # client's fabric closes.
        assert region.memory_released is False
        with pytest.raises(RdmaError, match="closed"):
            region.window()

        client.set(b"glide-py-rdma-lost", b"still works")
        assert client.get(b"glide-py-rdma-lost") == b"still works"
        fresh = client.register_rdma_region(bytearray(REGION_BYTES))
        fresh.close()
        client.delete([b"glide-py-rdma-lost"])
    finally:
        client.close()
        proxy.close()
    assert region.memory_released is True, "closing the client lets it go"


@pytest.mark.parametrize("cluster_mode", [False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
def test_closing_an_idle_region_lets_go_of_the_memory_at_once(
    glide_sync_client: GlideClient,
):
    # The fabric closes regions on a thread of its own. Freeing an idle region
    # has to wait for that, so the memory is the caller's again as soon as the
    # free returns: no hold, rather than one that is released a moment later.
    # This calls the FFI directly, because `close` sweeps released holds right
    # away and so would hide a hold that is only briefly outstanding.
    if not GlideClient.rdma_usable():
        pytest.skip("needs libfabric to open a tcp fabric")
    config = type(glide_sync_client._config)(
        addresses=glide_sync_client._config.addresses,
        rdma=RdmaConfiguration(provider=RdmaProvider.Tcp()),
    )
    client = type(glide_sync_client).create(config)
    try:
        memory = bytearray(REGION_BYTES)
        region = client.register_rdma_region(memory)
        raw, region._region = region._region, None
        hold = client._lib.free_rdma_region(raw)
        if hold != client._ffi.NULL:
            client._lib.free_rdma_memory_hold(hold)
        assert hold == client._ffi.NULL, "an idle region's memory is let go at once"
    finally:
        client.close()


@pytest.mark.skipif(
    not os.getenv("GLIDE_RDMA_SERVER"),
    reason="needs GLIDE_RDMA_SERVER=<host>:<port> serving the BLOB.RDMA_* commands",
)
class TestRdmaTransfers:
    """Real transfers, against a server serving the BLOB.RDMA_* commands."""

    SLOT_BYTES = 4096
    SLOTS = 4

    @staticmethod
    def _server() -> NodeAddress:
        host, _, port = os.environ["GLIDE_RDMA_SERVER"].rpartition(":")
        return NodeAddress(host, int(port))

    @pytest.fixture(scope="function")
    def rdma_client(self):
        host, _, port = os.environ["GLIDE_RDMA_SERVER"].rpartition(":")
        named = os.getenv("GLIDE_RDMA_PROVIDER", "tcp")
        provider = (
            RdmaProvider.EfaDirect() if named == "efa-direct" else RdmaProvider.Tcp()
        )
        client = GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(host, int(port))],
                rdma=RdmaConfiguration(provider=provider),
            )
        )
        yield client
        client.close()

    def test_a_value_round_trips_through_the_fabric(self, rdma_client):
        value = bytes(index % 251 for index in range(self.SLOT_BYTES))

        with rdma_client.register_rdma_region(bytearray(self.SLOT_BYTES)) as source:
            window = source.window(0, len(value))
            window.memoryview()[:] = value
            assert rdma_client.rdma_set(b"glide-py-rdma-roundtrip", window) is None

        # A separate, scrubbed region, so untouched memory cannot pass as a
        # successful read.
        with rdma_client.register_rdma_region(
            bytearray(b"\xaa" * self.SLOT_BYTES)
        ) as destination:
            receipt = rdma_client.rdma_get(
                b"glide-py-rdma-roundtrip", destination.window()
            )
            assert receipt is not None
            assert receipt.bytes_written == len(value)
            assert bytes(destination.memoryview()[: receipt.bytes_written]) == value
            assert receipt.checksum == GlideClient.rdma_checksum(value)

    def _read_back(self, client, key: bytes, length: int):
        """`key` read over RDMA, or None if it is missing."""
        with client.register_rdma_region(bytearray(length)) as destination:
            receipt = client.rdma_get(key, destination.window())
            if receipt is None:
                return None
            return bytes(destination.memoryview()[: receipt.bytes_written])

    @pytest.mark.parametrize(
        "checksum",
        [
            RdmaSetChecksum.Skip(),
            RdmaSetChecksum.Compute(),
            "known",
        ],
    )
    def test_every_checksum_choice_stores_the_value(self, rdma_client, checksum):
        value = bytes((index * 7) % 251 for index in range(self.SLOT_BYTES))
        if checksum == "known":
            checksum = RdmaSetChecksum.Known(GlideClient.rdma_checksum(value))
        key = f"glide-py-rdma-crc-{type(checksum).__name__}".encode()

        with rdma_client.register_rdma_region(bytearray(value)) as source:
            rdma_client.rdma_set(key, source.window(), checksum=checksum)

        assert self._read_back(rdma_client, key, self.SLOT_BYTES) == value
        rdma_client.delete([key])

    def test_a_wrong_checksum_is_refused_and_stores_nothing(self, rdma_client):
        # The server compares the checksum with what it read and refuses the
        # write. A refusal is a reply, so the region stays usable.
        value = bytes(index % 251 for index in range(self.SLOT_BYTES))
        wrong = GlideClient.rdma_checksum(value) ^ 1
        key = b"glide-py-rdma-crc-mismatch"
        rdma_client.delete([key])

        with rdma_client.register_rdma_region(bytearray(value)) as source:
            with pytest.raises(RdmaError, match="CRC mismatch"):
                rdma_client.rdma_set(
                    key, source.window(), checksum=RdmaSetChecksum.Known(wrong)
                )
            assert self._read_back(rdma_client, key, self.SLOT_BYTES) is None

            rdma_client.rdma_set(
                key, source.window(), checksum=RdmaSetChecksum.Compute()
            )

        assert self._read_back(rdma_client, key, self.SLOT_BYTES) == value
        rdma_client.delete([key])

    def test_a_missing_key_transfers_nothing(self, rdma_client):
        with rdma_client.register_rdma_region(bytearray(self.SLOT_BYTES)) as region:
            assert (
                rdma_client.rdma_get(b"glide-py-rdma-absent", region.window()) is None
            )

    def test_windows_of_one_region_round_trip_independently(self, rdma_client):
        # The case one large registration exists for: many values in one region,
        # each transfer naming its own window. Reading each back into a
        # different slot means ignoring either offset fails the comparison.
        total = self.SLOT_BYTES * self.SLOTS
        with rdma_client.register_rdma_region(bytearray(total)) as source:
            for slot in range(self.SLOTS):
                window = source.window(slot * self.SLOT_BYTES, self.SLOT_BYTES)
                window.memoryview()[:] = bytes([slot + 1]) * self.SLOT_BYTES
                rdma_client.rdma_set(f"glide-py-rdma-window-{slot}".encode(), window)

        with rdma_client.register_rdma_region(
            bytearray(b"\xaa" * total)
        ) as destination:
            for slot in range(self.SLOTS):
                landing = (self.SLOTS - 1 - slot) * self.SLOT_BYTES
                window = destination.window(landing, self.SLOT_BYTES)
                receipt = rdma_client.rdma_get(
                    f"glide-py-rdma-window-{slot}".encode(), window
                )
                assert receipt is not None
                assert receipt.bytes_written == self.SLOT_BYTES
                assert (
                    bytes(window.memoryview()) == bytes([slot + 1]) * self.SLOT_BYTES
                ), f"slot {slot} must land at offset {landing} and nowhere else"

    def test_closing_the_client_cancels_a_transfer_the_server_never_answers(
        self, rdma_client
    ):
        # CLIENT PAUSE holds every command without answering it, which is a
        # server that never replies. Only the close can end the transfer with
        # ClosingError: had the pause ended it, the server's reply would have
        # come back instead. The pause is left to lapse rather than lifted,
        # because the server holds CLIENT UNPAUSE too. The pausing client is
        # made before the pause, since connecting sends commands too.
        pauser = GlideClient.create(
            GlideClientConfiguration(addresses=[self._server()])
        )
        region = rdma_client.register_rdma_region(bytearray(self.SLOT_BYTES))
        # Open the session first, so the transfer below is held at BLOB.RDMA_GET itself.
        rdma_client.rdma_get(b"glide-py-rdma-cancel", region.window())
        outcome: dict = {}

        def transfer():
            try:
                outcome["returned"] = rdma_client.rdma_get(
                    b"glide-py-rdma-cancel", region.window()
                )
            except Exception as error:  # noqa: BLE001 - recorded for the assert
                outcome["raised"] = error

        pause_seconds = 5.0
        paused_at = time.monotonic()
        pauser.custom_command(
            ["CLIENT", "PAUSE", str(int(pause_seconds * 1000)), "ALL"]
        )
        try:
            thread = threading.Thread(target=transfer)
            thread.start()
            # Give the transfer time to reach the server, so it is the held
            # command that is cancelled. A close that came first would still
            # cancel it.
            thread.join(0.5)

            rdma_client.close()

            thread.join(30)
            assert not thread.is_alive(), "closing the client ends the wait"
        finally:
            # Let the pause lapse, so the tests after this one can connect.
            time.sleep(max(0.0, pause_seconds + 0.2 - (time.monotonic() - paused_at)))
            pauser.close()
        assert isinstance(outcome.get("raised"), ClosingError), outcome
        # The transfer ended without the server's reply, so it closed the region.
        assert region.closed is True
        region.close()
