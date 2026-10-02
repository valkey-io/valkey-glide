# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Server-free checks of the duplicate bounds in the sync pubsub retry helpers.

The resubscribe tests re-publish when a delivery is slow, so a delayed copy can
arrive after the re-publish. Valkey delivers each PUBLISH at most once per
subscribed connection, so the helpers accept at most one copy per PUBLISH they
issued and must reject one copy more. These tests drive the real helpers with
in-memory clients whose deliveries are scheduled explicitly.
"""

import threading
import time
from typing import List, Optional, Tuple

import pytest
from glide_shared.commands.core_options import PubSubMsg

from tests.sync_tests.test_sync_pubsub import (
    _check_no_unexpected_messages_left,
    _publish_until_all_received,
)
from tests.utils.pubsub_test_utils import MessageReadMethod

MESSAGE = "message_after_kill"
CHANNEL = "resubscribe_channel"
READ_METHODS = [MessageReadMethod.Sync, MessageReadMethod.Callback]


def _msg(message: str = MESSAGE, channel: str = CHANNEL) -> PubSubMsg:
    return PubSubMsg(message=message, channel=channel, pattern=None)


class _FakeListener:
    """Holds scheduled deliveries and releases them once their time has come."""

    def __init__(self, method: MessageReadMethod, callback_messages: List[PubSubMsg]):
        self._method = method
        self._callback_messages = callback_messages
        self._scheduled: List[Tuple[float, PubSubMsg]] = []
        self._queue: List[PubSubMsg] = []
        self._timers: List[threading.Timer] = []

    def deliver(self, msg: PubSubMsg, delay: float = 0.0) -> None:
        if self._method == MessageReadMethod.Callback:
            # Real callbacks run on the client's own thread, so a delayed copy
            # shows up in the list without the test reading anything.
            if delay <= 0:
                self._callback_messages.append(msg)
            else:
                timer = threading.Timer(delay, self._callback_messages.append, [msg])
                self._timers.append(timer)
                timer.start()
            return
        self._scheduled.append((time.time() + delay, msg))
        self._release()

    def _release(self) -> None:
        now = time.time()
        self._queue.extend(m for at, m in self._scheduled if at <= now)
        self._scheduled = [(at, m) for at, m in self._scheduled if at > now]

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        self._release()
        return self._queue.pop(0) if self._queue else None

    def pending(self) -> int:
        return len(self._scheduled) + sum(t.is_alive() for t in self._timers)


class _FakePublisher:
    """Delivers ``copies_per_publish`` copies of every PUBLISH after ``lag``."""

    def __init__(self, listener: _FakeListener, lag: float, copies_per_publish: int):
        self._listener = listener
        self._lag = lag
        self._copies = copies_per_publish

    def publish(self, message: str, channel: str) -> int:
        for _ in range(self._copies):
            self._listener.deliver(_msg(message, channel), self._lag)
        return 1


def _consumed(method: MessageReadMethod) -> Tuple[_FakeListener, List[PubSubMsg]]:
    """A listener that has already handed the test its before-kill and after-kill messages."""
    callback_messages: List[PubSubMsg] = []
    listener = _FakeListener(method, callback_messages)
    if method == MessageReadMethod.Callback:
        callback_messages.extend([_msg("message_before_kill"), _msg()])
    return listener, callback_messages


def _check(method, listener, callback_messages, publishes):
    _check_no_unexpected_messages_left(
        method,
        listener,
        callback_messages,
        2,
        MESSAGE,
        CHANNEL,
        publishes,
        settle_sec=0.0,
    )


@pytest.mark.parametrize("method", READ_METHODS)
@pytest.mark.parametrize("publishes,extra_copies", [(1, 0), (2, 1), (3, 2)])
def test_tail_check_accepts_one_copy_per_publish(method, publishes, extra_copies):
    listener, callback_messages = _consumed(method)
    for _ in range(extra_copies):
        listener.deliver(_msg())
    _check(method, listener, callback_messages, publishes)


@pytest.mark.parametrize("method", READ_METHODS)
@pytest.mark.parametrize("publishes,extra_copies", [(1, 1), (2, 2), (3, 3)])
def test_tail_check_rejects_a_copy_beyond_the_publishes(
    method, publishes, extra_copies
):
    listener, callback_messages = _consumed(method)
    for _ in range(extra_copies):
        listener.deliver(_msg())
    with pytest.raises(AssertionError, match="PUBLISH calls were issued"):
        _check(method, listener, callback_messages, publishes)


@pytest.mark.parametrize("method", READ_METHODS)
@pytest.mark.parametrize(
    "stray",
    [_msg(message="other_message"), _msg(channel="other_channel")],
    ids=["other_message", "other_channel"],
)
def test_tail_check_rejects_any_other_message(method, stray):
    listener, callback_messages = _consumed(method)
    listener.deliver(stray)
    with pytest.raises(AssertionError, match="Unexpected message"):
        _check(method, listener, callback_messages, publishes=5)


@pytest.mark.parametrize("method", READ_METHODS)
def test_tail_check_waits_for_a_late_copy(method):
    listener, callback_messages = _consumed(method)
    listener.deliver(_msg(), delay=0.2)
    with pytest.raises(AssertionError, match="PUBLISH calls were issued"):
        _check_no_unexpected_messages_left(
            method,
            listener,
            callback_messages,
            2,
            MESSAGE,
            CHANNEL,
            publishes=1,
            settle_sec=1.0,
            poll_interval=0.02,
        )


def _fan_out(method, lag, copies_per_publish, channels):
    callback_messages: List[PubSubMsg] = []
    listener = _FakeListener(method, callback_messages)
    publisher = _FakePublisher(listener, lag, copies_per_publish)
    result = _publish_until_all_received(
        publisher,
        listener,
        method,
        callback_messages,
        MESSAGE,
        channels,
        deadline_sec=5.0,
        poll_timeout=0.3,
        poll_interval=0.02,
    )
    return result, listener


@pytest.mark.parametrize("method", READ_METHODS)
def test_fan_out_publishes_each_channel_once_when_delivery_beats_the_poll(method):
    channels = {f"ch{i}" for i in range(8)}
    (received, copies, publishes), listener = _fan_out(method, 0.1, 1, channels)
    assert received == channels
    assert publishes == {ch: 1 for ch in channels}
    assert copies == {ch: 1 for ch in channels}
    assert listener.pending() == 0


@pytest.mark.parametrize("method", READ_METHODS)
def test_fan_out_accepts_a_delayed_copy_per_republish(method):
    # Delivery slower than the poll window forces a re-publish; both copies
    # land, and each is explained by its own PUBLISH. The re-publish fires on
    # the first poll after poll_timeout (0.3s), so the lag must exceed that by
    # more than any stall a loaded host can insert between two polls, or the
    # delivery lands first and nothing is re-published.
    channels = {f"ch{i}" for i in range(4)}
    (received, copies, publishes), _ = _fan_out(method, 1.0, 1, channels)
    assert received == channels
    assert all(copies[ch] <= publishes[ch] for ch in channels)
    assert any(publishes[ch] >= 2 for ch in channels)


@pytest.mark.parametrize("method", READ_METHODS)
def test_fan_out_rejects_more_copies_than_publishes(method):
    channels = {f"ch{i}" for i in range(4)}
    with pytest.raises(AssertionError, match="PUBLISH calls were issued to it"):
        _fan_out(method, 0.05, 2, channels)
