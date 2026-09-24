# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""RDMA on the synchronous client: regions, windows, and what each refuses.

The transfer tests need a server that serves the ``LO.*`` commands, so they
are skipped unless ``GLIDE_RDMA_SERVER`` names one. Everything else runs
anywhere, including on a build with no RDMA compiled in.
"""

from __future__ import annotations

import array
import os
import threading
import time

import pytest
from glide_shared.config import (
    GlideClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    RdmaConfiguration,
    RdmaProvider,
)
from glide_shared.exceptions import (
    ClosingError,
    ConnectionError,
    RdmaError,
    TimeoutError,
)
from glide_sync import GlideClient, RdmaRegion, RdmaWindow

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

    def __init__(self):
        self.freed = []
        self.freed_results = []

    def rdma_region_capacity(self, region):
        return region["capacity"]

    def free_rdma_region(self, region):
        self.freed.append(region)

    def free_rdma_result(self, result):
        self.freed_results.append(result)


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


class _FakeResult:
    """A failed transfer as the FFI reports one, for the classification checks."""

    def __init__(self, error_message: bytes, error_type: int):
        self.error_message = error_message
        self.error_type = error_type
        self.found = False
        self.bytes_written = 0
        self.has_checksum = False
        self.checksum = 0


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
        client._handle_rdma_result(result)

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
        client._handle_rdma_result(result)

    assert client._lib.freed_results == [result]


def test_a_real_failure_is_reported_even_if_the_client_closed_meanwhile():
    # A read that overran its window and a close that followed it: the caller
    # must still learn about the overrun.
    client = _offline_client()
    client._is_closed = True
    result = _FakeResult(
        b"RDMA error - UserOperationError: value of 8192 bytes exceeds the 4096 "
        b"byte registered window",
        0,
    )

    with pytest.raises(RdmaError, match="exceeds") as raised:
        client._handle_rdma_result(result)

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
        finally:
            region.close()
    finally:
        client.close()


@pytest.mark.skipif(
    not os.getenv("GLIDE_RDMA_SERVER"),
    reason="needs GLIDE_RDMA_SERVER=<host>:<port> serving the LO.* commands",
)
class TestRdmaTransfers:
    """Real transfers, against a server serving the LO.* commands."""

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
            if receipt.checksum is not None:
                assert receipt.checksum == GlideClient.rdma_checksum(value)

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
        # Open the session first, so the transfer below is held at LO.GET itself.
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
        # The transfer has returned, so the region can be released.
        region.close()
