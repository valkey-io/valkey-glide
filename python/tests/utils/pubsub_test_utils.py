# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Shared utilities for pubsub tests (both async and sync).
"""

import sys
from contextlib import asynccontextmanager, contextmanager
from enum import IntEnum
from typing import Any, Callable, Dict, List, Optional, Set, Union, cast

if sys.version_info >= (3, 10):
    from typing import TypeAlias
else:
    from typing_extensions import TypeAlias

import anyio
import pytest
from glide.glide_client import GlideClusterClient, TGlideClient
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ProtocolVersion,
)
from glide_sync.glide_client import GlideClient as SyncGlideClient
from glide_sync.glide_client import GlideClusterClient as SyncGlideClusterClient

# Type alias for any glide client (async or sync)
AnyGlideClient: TypeAlias = Union[TGlideClient, SyncGlideClient, SyncGlideClusterClient]


class SubscriptionMethod(IntEnum):
    """
    Enumeration for specifying how subscriptions are established.
    """

    Config = 0
    "Subscriptions set in client configuration at creation time."
    Lazy = 1
    "Non-blocking subscription using *_lazy methods."
    Blocking = 2
    "Blocking subscription with timeout."


class MessageReadMethod(IntEnum):
    """
    Enumeration for specifying the method of reading PUBSUB messages.
    """

    Async = 0
    "Uses asynchronous get_pubsub_message() method."
    Sync = 1
    "Uses synchronous try_get_pubsub_message() method."
    Callback = 2
    "Uses callback-based subscription method."


# Alias for backward compatibility with sync tests
MethodTesting = MessageReadMethod


# Type alias for PubSubChannelModes
ClusterPubSubModes: TypeAlias = GlideClusterClientConfiguration.PubSubChannelModes
StandalonePubSubModes: TypeAlias = GlideClientConfiguration.PubSubChannelModes


# Shared callback function for pubsub tests
def new_message(msg: PubSubMsg, context: Any) -> None:
    """Standard callback function that appends messages to a context list."""
    received_messages: List[PubSubMsg] = context
    received_messages.append(msg)


def wait_for_messages(
    expected_count: int, messages: List[PubSubMsg], timeout: float = 3.0
) -> List[PubSubMsg]:
    """
    Wait for expected number of messages to arrive in callback list.

    Args:
        expected_count: Number of messages to wait for
        messages: List that callback appends to
        timeout: Maximum time to wait in seconds

    Returns:
        List of messages received

    Raises:
        TimeoutError: If expected messages don't arrive in time
    """
    import time

    start = time.time()
    while len(messages) < expected_count:
        if time.time() - start > timeout:
            raise TimeoutError(
                f"Timeout waiting for {expected_count} messages, got {len(messages)}"
            )
        time.sleep(0.1)
    return messages[:expected_count]


def get_pubsub_modes(
    client: AnyGlideClient,
) -> Any:
    """Get the appropriate PubSubChannelModes enum for the client type."""
    if isinstance(client, (GlideClusterClient, SyncGlideClusterClient)):
        return GlideClusterClientConfiguration.PubSubChannelModes
    return GlideClientConfiguration.PubSubChannelModes


def _build_channels_dict(
    cluster_mode: bool,
    channels: Optional[Set[str]],
    patterns: Optional[Set[str]],
    sharded_channels: Optional[Set[str]],
) -> Union[Dict[ClusterPubSubModes, Set[str]], Dict[StandalonePubSubModes, Set[str]]]:
    """Build channels_and_patterns dict for cluster or standalone mode."""
    if cluster_mode:
        result: Dict[ClusterPubSubModes, Set[str]] = {}
        if channels:
            result[GlideClusterClientConfiguration.PubSubChannelModes.Exact] = channels
        if patterns:
            result[GlideClusterClientConfiguration.PubSubChannelModes.Pattern] = (
                patterns
            )
        if sharded_channels:
            result[GlideClusterClientConfiguration.PubSubChannelModes.Sharded] = (
                sharded_channels
            )
        return result
    else:
        result_standalone: Dict[StandalonePubSubModes, Set[str]] = {}
        if channels:
            result_standalone[GlideClientConfiguration.PubSubChannelModes.Exact] = (
                channels
            )
        if patterns:
            result_standalone[GlideClientConfiguration.PubSubChannelModes.Pattern] = (
                patterns
            )
        return result_standalone


def create_pubsub_subscription(
    cluster_mode: bool,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded_channels: Optional[Set[str]] = None,
    callback: Optional[Callable[[PubSubMsg, Any], None]] = None,
    context: Optional[Any] = None,
) -> Union[
    GlideClusterClientConfiguration.PubSubSubscriptions,
    GlideClientConfiguration.PubSubSubscriptions,
]:
    """Create a PubSubSubscriptions object for the given mode."""
    channels_dict = _build_channels_dict(
        cluster_mode, channels, patterns, sharded_channels
    )
    if cluster_mode:
        return GlideClusterClientConfiguration.PubSubSubscriptions(
            channels_and_patterns=channels_dict,  # type: ignore[arg-type]
            callback=callback,
            context=context,
        )
    return GlideClientConfiguration.PubSubSubscriptions(
        channels_and_patterns=channels_dict,  # type: ignore[arg-type]
        callback=callback,
        context=context,
    )


async def create_pubsub_client(
    request,
    cluster_mode: bool,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded_channels: Optional[Set[str]] = None,
    callback: Optional[Callable[[PubSubMsg, Any], None]] = None,
    context: Optional[Any] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = None,
    lazy_connect: bool = False,
    reconciliation_interval_ms: Optional[int] = None,
) -> TGlideClient:
    from tests.async_tests.conftest import create_client

    has_subscriptions = channels or patterns or sharded_channels
    has_callback = callback is not None

    if has_subscriptions or has_callback:
        pubsub_subscription = create_pubsub_subscription(
            cluster_mode,
            channels=channels,
            patterns=patterns,
            sharded_channels=sharded_channels,
            callback=callback,
            context=context,
        )

        if cluster_mode:
            client = await create_client(
                request,
                cluster_mode=cluster_mode,
                cluster_mode_pubsub=pubsub_subscription,  # type: ignore[arg-type]
                standalone_mode_pubsub=None,
                protocol=protocol,
                request_timeout=timeout,
                lazy_connect=lazy_connect,
                reconciliation_interval_ms=reconciliation_interval_ms,
            )
        else:
            client = await create_client(
                request,
                cluster_mode=cluster_mode,
                cluster_mode_pubsub=None,
                standalone_mode_pubsub=pubsub_subscription,  # type: ignore[arg-type]
                protocol=protocol,
                request_timeout=timeout,
                lazy_connect=lazy_connect,
                reconciliation_interval_ms=reconciliation_interval_ms,
            )
    else:
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            request_timeout=timeout,
            lazy_connect=lazy_connect,
            reconciliation_interval_ms=reconciliation_interval_ms,
        )

    return client


async def subscribe_by_method(
    client: TGlideClient,
    channels: Set[str],
    subscription_method: SubscriptionMethod,
    timeout_ms: int = 0,
) -> None:
    """
    Subscribe to exact channels using the specified method.
    This helper is intended for Lazy and Blocking methods only.
    For Config method, subscriptions are set at client creation time.
    Does NOT wait for subscription to be established - use wait_for_subscription_state_if_needed after.
    """
    if subscription_method == SubscriptionMethod.Lazy:
        result = await client.subscribe_lazy(channels)  # type: ignore[func-returns-value]
    else:  # Blocking
        result = await client.subscribe(channels, timeout_ms=timeout_ms)  # type: ignore[func-returns-value]

    assert result is None, f"Expected subscribe to return None, got {result}"


async def psubscribe_by_method(
    client: TGlideClient,
    patterns: Set[str],
    subscription_method: SubscriptionMethod,
    timeout_ms: int = 0,
) -> None:
    """
    Subscribe to patterns using the specified method.
    This helper is intended for Lazy and Blocking methods only.
    For Config method, subscriptions are set at client creation time.
    Does NOT wait for subscription to be established - use wait_for_subscription_state_if_needed after.
    """
    if subscription_method == SubscriptionMethod.Lazy:
        result = await client.psubscribe_lazy(patterns)  # type: ignore[func-returns-value]
    else:  # Blocking
        result = await client.psubscribe(patterns, timeout_ms=timeout_ms)  # type: ignore[func-returns-value]

    assert result is None, f"Expected psubscribe to return None, got {result}"


async def ssubscribe_by_method(
    client: GlideClusterClient,
    channels: Set[str],
    subscription_method: SubscriptionMethod,
    timeout_ms: int = 0,
) -> None:
    """
    Subscribe to sharded channels using the specified method.
    This helper is intended for Lazy and Blocking methods only.
    For Config method, subscriptions are set at client creation time.
    Does NOT wait for subscription to be established - use wait_for_subscription_state_if_needed after.
    """
    if subscription_method == SubscriptionMethod.Lazy:
        result = await client.ssubscribe_lazy(channels)  # type: ignore[func-returns-value]
    else:  # Blocking
        result = await client.ssubscribe(channels, timeout_ms=timeout_ms)  # type: ignore[func-returns-value]

    assert result is None, f"Expected ssubscribe to return None, got {result}"


async def unsubscribe_by_method(
    client: TGlideClient,
    channels: Optional[Set[str]],
    subscription_method: SubscriptionMethod,
    timeout_ms: int = 5000,
) -> None:
    """
    Unsubscribe from exact channels using the specified method.
    This helper is intended for Lazy and Blocking methods only.
    For Config method, cannot dynamically unsubscribe.
    Does NOT wait for unsubscription to complete - use wait_for_subscription_state_if_needed after.
    """
    if subscription_method == SubscriptionMethod.Config:
        return

    if subscription_method == SubscriptionMethod.Lazy:
        result = await client.unsubscribe_lazy(channels)  # type: ignore[func-returns-value]
    else:  # Blocking
        result = await client.unsubscribe(channels, timeout_ms=timeout_ms)  # type: ignore[func-returns-value]

    assert result is None, f"Expected unsubscribe to return None, got {result}"


async def punsubscribe_by_method(
    client: TGlideClient,
    patterns: Optional[Set[str]],
    subscription_method: SubscriptionMethod,
    timeout_ms: int = 5000,
) -> None:
    """
    Unsubscribe from patterns using the specified method.
    This helper is intended for Lazy and Blocking methods only.
    For Config method, cannot dynamically unsubscribe.
    Does NOT wait for unsubscription to complete - use wait_for_subscription_state_if_needed after.
    """
    if subscription_method == SubscriptionMethod.Config:
        return

    if subscription_method == SubscriptionMethod.Lazy:
        result = await client.punsubscribe_lazy(patterns)  # type: ignore[func-returns-value]
    else:  # Blocking
        result = await client.punsubscribe(patterns, timeout_ms=timeout_ms)  # type: ignore[func-returns-value]

    assert result is None, f"Expected punsubscribe to return None, got {result}"


async def sunsubscribe_by_method(
    client: GlideClusterClient,
    channels: Optional[Set[str]],
    subscription_method: SubscriptionMethod,
    timeout_ms: int = 5000,
) -> None:
    """
    Unsubscribe from sharded channels using the specified method.
    This helper is intended for Lazy and Blocking methods only.
    For Config method, cannot dynamically unsubscribe.
    Does NOT wait for unsubscription to complete - use wait_for_subscription_state_if_needed after.
    """
    if subscription_method == SubscriptionMethod.Config:
        return

    if subscription_method == SubscriptionMethod.Lazy:
        result = await client.sunsubscribe_lazy(channels)  # type: ignore[func-returns-value]
    else:  # Blocking
        result = await client.sunsubscribe(channels, timeout_ms=timeout_ms)  # type: ignore[func-returns-value]

    assert result is None, f"Expected sunsubscribe to return None, got {result}"


async def wait_for_subscription_state(
    client: TGlideClient,
    expected_channels: Optional[Set[str]] = None,
    expected_patterns: Optional[Set[str]] = None,
    expected_sharded: Optional[Set[str]] = None,
    timeout_ms: int = 5000,
    poll_interval: float = 0.1,
) -> Dict[str, Set[str]]:
    """
    Wait for subscription state to match expected values by polling.

    Args:
        client: The Glide client
        expected_channels: Expected exact channel subscriptions (None = don't check)
        expected_patterns: Expected pattern subscriptions (None = don't check)
        expected_sharded: Expected sharded channel subscriptions (None = don't check)
        timeout_ms: Timeout in milliseconds
        poll_interval: How often to poll state in seconds

    Returns:
        Dictionary with current actual subscription state

    Raises:
        TimeoutError: If expected state not reached within timeout
    """
    timeout_seconds = timeout_ms / 1000.0
    start_time = anyio.current_time()
    last_actual_state: Optional[Dict[str, Set[str]]] = None

    PubSubModes = get_pubsub_modes(client)

    while True:
        elapsed = anyio.current_time() - start_time
        if elapsed > timeout_seconds:
            error_msg = (
                f"Subscription state not reached within {timeout_ms}ms.\n"
                f"Expected - channels: {expected_channels}, patterns: {expected_patterns}, "
                f"sharded: {expected_sharded}\n"
            )
            if last_actual_state:
                error_msg += (
                    f"Actual - channels: {last_actual_state.get('channels', set())}, "
                    f"patterns: {last_actual_state.get('patterns', set())}, "
                    f"sharded: {last_actual_state.get('sharded', set())}\n"
                )
            raise TimeoutError(error_msg)

        try:
            state = await client.get_subscriptions()
            actual_subs = state.actual_subscriptions

            channels_actual = actual_subs.get(PubSubModes.Exact, set())  # type: ignore[arg-type]
            patterns_actual = actual_subs.get(PubSubModes.Pattern, set())  # type: ignore[arg-type]
            sharded_actual: Set[str] = set()
            if isinstance(client, GlideClusterClient):
                sharded_actual = actual_subs.get(PubSubModes.Sharded, set())  # type: ignore[union-attr, arg-type]

            last_actual_state = {
                "channels": channels_actual,
                "patterns": patterns_actual,
                "sharded": sharded_actual,
            }

            # Check if all expected states match
            channels_match = (
                expected_channels is None or channels_actual == expected_channels
            )
            patterns_match = (
                expected_patterns is None or patterns_actual == expected_patterns
            )
            sharded_match = (
                expected_sharded is None or sharded_actual == expected_sharded
            )

            if channels_match and patterns_match and sharded_match:
                return last_actual_state

        except Exception as e:
            # Ignore connection errors during polling
            if not isinstance(e, (ConnectionError, TimeoutError)):
                raise

        await anyio.sleep(poll_interval)


async def wait_for_subscription_state_if_needed(
    client: TGlideClient,
    subscription_method: SubscriptionMethod,
    expected_channels: Optional[Set[str]] = None,
    expected_patterns: Optional[Set[str]] = None,
    expected_sharded: Optional[Set[str]] = None,
    timeout_ms: int = 5000,
) -> None:
    """
    - Lazy: wait/poll until state matches (with timeout)
    - Blocking and Config: verify immediately
    """

    # Lazy subscriptions may need time to reconcile
    if subscription_method == SubscriptionMethod.Lazy:
        await wait_for_subscription_state(
            client,
            expected_channels=expected_channels,
            expected_patterns=expected_patterns,
            expected_sharded=expected_sharded,
            timeout_ms=timeout_ms,
        )
        return

    # Blocking and Config should already be established
    state = await client.get_subscriptions()

    # Define empty set with proper type
    empty_set: Set[str] = set()

    if isinstance(client, GlideClusterClient):
        ClusterModes = GlideClusterClientConfiguration.PubSubChannelModes
        cluster_subs = cast(
            Dict[GlideClusterClientConfiguration.PubSubChannelModes, Set[str]],
            state.actual_subscriptions,
        )

        if expected_channels is not None:
            actual_channels = cluster_subs.get(ClusterModes.Exact, empty_set)
            assert (
                actual_channels == expected_channels
            ), f"Expected channels {expected_channels}, got {actual_channels}"

        if expected_patterns is not None:
            actual_patterns = cluster_subs.get(ClusterModes.Pattern, empty_set)
            assert (
                actual_patterns == expected_patterns
            ), f"Expected patterns {expected_patterns}, got {actual_patterns}"

        if expected_sharded is not None:
            actual_sharded = cluster_subs.get(ClusterModes.Sharded, empty_set)
            assert (
                actual_sharded == expected_sharded
            ), f"Expected sharded {expected_sharded}, got {actual_sharded}"
    else:
        StandaloneModes = GlideClientConfiguration.PubSubChannelModes
        standalone_subs = cast(
            Dict[GlideClientConfiguration.PubSubChannelModes, Set[str]],
            state.actual_subscriptions,
        )

        if expected_channels is not None:
            actual_channels = standalone_subs.get(StandaloneModes.Exact, empty_set)
            assert (
                actual_channels == expected_channels
            ), f"Expected channels {expected_channels}, got {actual_channels}"

        if expected_patterns is not None:
            actual_patterns = standalone_subs.get(StandaloneModes.Pattern, empty_set)
            assert (
                actual_patterns == expected_patterns
            ), f"Expected patterns {expected_patterns}, got {actual_patterns}"


def decode_pubsub_msg(msg: Optional[PubSubMsg]) -> PubSubMsg:
    """Decode a PubSubMsg with bytes to one with strings. If already strings, return as-is."""
    if not msg:
        return PubSubMsg("", "", None)
    # Handle both bytes (async) and strings (sync)
    string_msg = msg.message.decode() if isinstance(msg.message, bytes) else msg.message
    string_channel = (
        msg.channel.decode() if isinstance(msg.channel, bytes) else msg.channel
    )
    string_pattern = (
        msg.pattern.decode()
        if isinstance(msg.pattern, bytes) and msg.pattern
        else msg.pattern
    )
    return PubSubMsg(string_msg, string_channel, string_pattern)


async def get_message_by_method(
    method: MessageReadMethod,
    client: TGlideClient,
    callback_messages: Optional[List[PubSubMsg]] = None,
    index: Optional[int] = None,
) -> PubSubMsg:
    """
    Get a pubsub message using the specified read method.

    Args:
        method: How to read the message (Async, Sync, or Callback)
        client: The client to read from
        callback_messages: List of messages from callback (required for Callback method)
        index: Index in callback_messages list (required for Callback method)

    Returns:
        Decoded PubSubMsg
    """
    if method == MessageReadMethod.Async:
        return decode_pubsub_msg(await client.get_pubsub_message())
    elif method == MessageReadMethod.Sync:
        return decode_pubsub_msg(client.try_get_pubsub_message())
    else:  # Callback
        assert callback_messages is not None and index is not None
        return decode_pubsub_msg(callback_messages[index])


async def check_no_messages_left(
    method: MessageReadMethod,
    client: TGlideClient,
    callback_messages: Optional[List[PubSubMsg]] = None,
    expected_callback_count: int = 0,
    async_timeout: float = 3.0,
) -> None:
    """
    Verify there are no more messages to read.

    Args:
        method: The read method being used
        client: The client to check
        callback_messages: Callback message list (for Callback method)
        expected_callback_count: Expected number of messages in callback list
        async_timeout: Timeout for async method check

    Raises:
        AssertionError if there are unexpected messages
    """
    if method == MessageReadMethod.Async:
        with pytest.raises(TimeoutError):
            with anyio.fail_after(async_timeout):
                await client.get_pubsub_message()
    elif method == MessageReadMethod.Sync:
        assert client.try_get_pubsub_message() is None
    else:  # Callback
        assert callback_messages is not None
        assert len(callback_messages) == expected_callback_count


async def pubsub_client_cleanup(
    client: Optional[TGlideClient],
) -> None:
    """
    Clean up a pubsub client by unsubscribing and closing.

    For Config method: just close (server will clean up on disconnect)
    For Lazy/Blocking: unsubscribe from all before closing
    """
    if client is None:
        return

    cleanup_error = None

    try:
        # Get current subscriptions and unsubscribe
        state = await client.get_subscriptions()
        actual = state.actual_subscriptions

        has_channels: bool
        has_patterns: bool
        has_sharded: bool

        if isinstance(client, GlideClusterClient):
            ClusterModes = GlideClusterClientConfiguration.PubSubChannelModes
            cluster_subs = cast(
                Dict[GlideClusterClientConfiguration.PubSubChannelModes, Set[str]],
                actual,
            )
            has_channels = bool(cluster_subs.get(ClusterModes.Exact))
            has_patterns = bool(cluster_subs.get(ClusterModes.Pattern))
            has_sharded = bool(cluster_subs.get(ClusterModes.Sharded))
        else:
            StandaloneModes = GlideClientConfiguration.PubSubChannelModes
            standalone_subs = cast(
                Dict[GlideClientConfiguration.PubSubChannelModes, Set[str]],
                actual,
            )
            has_channels = bool(standalone_subs.get(StandaloneModes.Exact))
            has_patterns = bool(standalone_subs.get(StandaloneModes.Pattern))
            has_sharded = False

        # Unsubscribe from all using lazy (faster cleanup)
        if has_channels:
            await client.unsubscribe_lazy()
        if has_patterns:
            await client.punsubscribe_lazy()
        if has_sharded:
            await cast(GlideClusterClient, client).sunsubscribe_lazy()

        # Wait briefly for unsubscriptions
        if has_channels or has_patterns or has_sharded:
            await wait_for_subscription_state(
                client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=(
                    set() if isinstance(client, GlideClusterClient) else None
                ),
                timeout_ms=3000,
            )

    except Exception as e:
        cleanup_error = e
    finally:
        await client.close()
        del client
        # The closure is not completed in the glide-core instantly
        await anyio.sleep(1)

        if cleanup_error:
            raise cleanup_error


@asynccontextmanager
async def async_pubsub_test_clients(
    request,
    cluster_mode: bool,
    subscription_method,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded: Optional[Set[str]] = None,
    callback: Optional[Any] = None,
    context: Optional[Any] = None,
    timeout: Optional[int] = None,
    lazy_connect: bool = False,
):
    """
    Async context manager for pubsub test clients.
    Handles client creation, subscription, and cleanup.
    """
    from tests.async_tests.conftest import create_client

    listening_client, publishing_client = None, None

    try:
        if subscription_method.value == 0:  # Config
            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                channels=channels,
                patterns=patterns,
                sharded_channels=sharded,
                callback=callback,
                context=context,
                timeout=timeout,
                lazy_connect=lazy_connect,
            )
        else:  # Lazy or Blocking
            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                callback=callback,
                context=context,
                timeout=timeout,
                lazy_connect=lazy_connect,
            )
            # Subscribe dynamically
            if channels:
                await subscribe_by_method(
                    listening_client, channels, subscription_method
                )
            if patterns:
                await psubscribe_by_method(
                    listening_client, patterns, subscription_method
                )
            if sharded and cluster_mode:
                await ssubscribe_by_method(listening_client, sharded, subscription_method)  # type: ignore[arg-type]

        publishing_client = await create_client(request, cluster_mode)

        yield listening_client, publishing_client

    finally:
        await pubsub_client_cleanup(listening_client)
        await pubsub_client_cleanup(publishing_client)


# Shared test constants
class PubSubTestConstants:
    """Constants used across pubsub tests."""

    # ACL test constants
    ACL_TEST_USERNAME_PREFIX = "mock_test_user"
    ACL_TEST_PASSWORD_PREFIX = "password"

    # Channel name prefixes
    CHANNEL_PREFIX = "test_channel"
    PATTERN_PREFIX = "test_pattern"
    SHARDED_PREFIX = "test_sharded"

    # Reconciliation timing
    DEFAULT_RECONCILIATION_INTERVAL_SEC = 3
    DEFAULT_POLL_TIMEOUT_SEC = 15
    DEFAULT_MESSAGE_PROPAGATION_DELAY_SEC = 1

    # Subscription state wait timeout
    SUBSCRIPTION_STATE_TIMEOUT_SEC = 3


def create_sync_pubsub_client(
    request,
    cluster_mode: bool,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded_channels: Optional[Set[str]] = None,
    callback: Optional[Any] = None,
    context: Optional[Any] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = None,
    reconciliation_interval_ms: Optional[int] = None,
    lazy_connect: bool = False,
):
    """
    Create a sync client with pubsub configuration.
    Convenience wrapper similar to async create_pubsub_client.
    """
    from tests.sync_tests.conftest import create_sync_client

    has_subscriptions = channels or patterns or sharded_channels
    has_callback = callback is not None

    if has_subscriptions or has_callback:
        pubsub_subscription = create_pubsub_subscription(
            cluster_mode,
            channels=channels,
            patterns=patterns,
            sharded_channels=sharded_channels,
            callback=callback,
            context=context,
        )

        if cluster_mode:
            return create_sync_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=pubsub_subscription,  # type: ignore[arg-type]
                request_timeout=timeout,
                reconciliation_interval_ms=reconciliation_interval_ms,
                lazy_connect=lazy_connect,
            )
        else:
            return create_sync_client(
                request,
                cluster_mode,
                standalone_mode_pubsub=pubsub_subscription,  # type: ignore[arg-type]
                request_timeout=timeout,
                reconciliation_interval_ms=reconciliation_interval_ms,
                lazy_connect=lazy_connect,
            )
    else:
        return create_sync_client(
            request,
            cluster_mode,
            reconciliation_interval_ms=reconciliation_interval_ms,
            lazy_connect=lazy_connect,
        )


# ============================================================================
# Sync pubsub test utilities
# ============================================================================


def sync_subscribe_by_method(
    client,
    subscription_method: SubscriptionMethod,
    cluster_mode: bool,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded: Optional[Set[str]] = None,
    timeout_ms: int = 5000,
) -> None:
    """
    Subscribe to channels/patterns using the specified method (Config, Lazy, or Blocking).
    For Config method, does nothing (subscriptions already set at client creation).
    For Lazy method, uses non-blocking lazy subscribe methods.
    For Blocking method, uses blocking subscribe with timeout.
    """
    import time

    if subscription_method == SubscriptionMethod.Config:
        return  # Already subscribed at creation

    if subscription_method == SubscriptionMethod.Lazy:
        # Use lazy (non-blocking) subscribe methods
        if channels:
            client.subscribe_lazy(channels)
        if patterns:
            client.psubscribe_lazy(patterns)
        if cluster_mode and sharded:
            client.ssubscribe_lazy(sharded)
        # Wait for lazy subscription to propagate
        time.sleep(0.5)
    else:  # Blocking
        # Use blocking subscribe with timeout
        if channels:
            client.subscribe(channels, timeout_ms=timeout_ms)
        if patterns:
            client.psubscribe(patterns, timeout_ms=timeout_ms)
        if cluster_mode and sharded:
            client.ssubscribe(sharded, timeout_ms=timeout_ms)


def create_two_sync_clients_with_pubsub(
    request,
    cluster_mode,
    client1_pubsub: Optional[Any] = None,
    client2_pubsub: Optional[Any] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = None,
    lazy_connect: bool = False,
):
    """
    Sets up 2 sync clients for testing purposes with optional pubsub configuration.

    Args:
        request: pytest request for creating a client.
        cluster_mode: the cluster mode.
        client1_pubsub: pubsub configuration subscription for the first client.
        client2_pubsub: pubsub configuration subscription for the second client.
        protocol: what protocol to use.
        timeout: timeout in milliseconds for both request and connection timeouts.
    """
    from tests.sync_tests.conftest import create_sync_client

    cluster_mode_pubsub1, standalone_mode_pubsub1 = None, None
    cluster_mode_pubsub2, standalone_mode_pubsub2 = None, None
    if cluster_mode:
        cluster_mode_pubsub1 = client1_pubsub
        cluster_mode_pubsub2 = client2_pubsub
    else:
        standalone_mode_pubsub1 = client1_pubsub
        standalone_mode_pubsub2 = client2_pubsub

    client1 = create_sync_client(
        request,
        cluster_mode=cluster_mode,
        cluster_mode_pubsub=cluster_mode_pubsub1,
        standalone_mode_pubsub=standalone_mode_pubsub1,
        protocol=protocol,
        request_timeout=timeout,
        connection_timeout=timeout,
        lazy_connect=lazy_connect,
    )
    try:
        client2 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            cluster_mode_pubsub=cluster_mode_pubsub2,
            standalone_mode_pubsub=standalone_mode_pubsub2,
            protocol=protocol,
            request_timeout=timeout,
            connection_timeout=timeout,
            lazy_connect=lazy_connect,
        )
    except Exception as e:
        client1.close()
        raise e

    return client1, client2


def sync_client_cleanup(
    client,
    cluster_mode_subs=None,
) -> None:
    """
    This function tries its best to clear state associated with client.
    It explicitly calls client.close() and deletes the object.
    In addition, it tries to clean up cluster mode subscriptions since it was found
    closing the client via close() is not enough.
    """
    import time

    from tests.utils.utils import sync_check_if_server_version_lt

    if client is None:
        return

    if cluster_mode_subs:
        for (
            channel_type,
            channel_patterns,
        ) in cluster_mode_subs.channels_and_patterns.items():
            if channel_type == GlideClusterClientConfiguration.PubSubChannelModes.Exact:
                cmd = "UNSUBSCRIBE_BLOCKING"
            elif (
                channel_type
                == GlideClusterClientConfiguration.PubSubChannelModes.Pattern
            ):
                cmd = "PUNSUBSCRIBE_BLOCKING"
            elif not sync_check_if_server_version_lt(client, "7.0.0"):
                cmd = "SUNSUBSCRIBE_BLOCKING"
            else:
                # disregard sharded config for versions < 7.0.0
                continue

            for channel_pattern in channel_patterns:
                client.custom_command([cmd, channel_pattern, "0"])

    client.close()
    del client
    # The closure is not completed in the glide-core instantly
    time.sleep(1)


@contextmanager
def sync_pubsub_test_clients(
    request,
    cluster_mode: bool,
    subscription_method,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded: Optional[Set[str]] = None,
    callback: Optional[Any] = None,
    context: Optional[Any] = None,
    timeout: Optional[int] = None,
    lazy_connect: bool = False,
):
    """
    Context manager for sync pubsub test clients.
    Handles client creation, subscription, and cleanup.
    """
    from tests.sync_tests.conftest import create_sync_client

    listening_client, publishing_client = None, None
    pub_sub = None

    try:
        if subscription_method.value == 0:  # Config
            pub_sub = create_pubsub_subscription(
                cluster_mode,
                channels=channels,
                patterns=patterns,
                sharded_channels=sharded,
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = create_two_sync_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub,
                timeout=timeout,
                lazy_connect=lazy_connect,
            )
        else:  # Lazy or Blocking
            # For Lazy/Blocking with callback, create client with empty subscriptions
            # For Lazy/Blocking without callback, create client with no pubsub config
            if callback:
                if cluster_mode:
                    cluster_pubsub_config = (
                        GlideClusterClientConfiguration.PubSubSubscriptions(
                            channels_and_patterns={},
                            callback=callback,
                            context=context,
                        )
                    )
                    listening_client = create_sync_client(
                        request,
                        cluster_mode,
                        cluster_mode_pubsub=cluster_pubsub_config,
                        request_timeout=timeout,
                        connection_timeout=timeout,
                        lazy_connect=lazy_connect,
                    )
                else:
                    standalone_pubsub_config = (
                        GlideClientConfiguration.PubSubSubscriptions(
                            channels_and_patterns={},
                            callback=callback,
                            context=context,
                        )
                    )
                    listening_client = create_sync_client(
                        request,
                        cluster_mode,
                        standalone_mode_pubsub=standalone_pubsub_config,
                        request_timeout=timeout,
                        connection_timeout=timeout,
                        lazy_connect=lazy_connect,
                    )
            else:
                # No callback - create client with no pubsub config
                listening_client = create_sync_client(
                    request,
                    cluster_mode,
                    request_timeout=timeout,
                    connection_timeout=timeout,
                    lazy_connect=lazy_connect,
                )

            publishing_client = create_sync_client(
                request,
                cluster_mode,
                request_timeout=timeout,
                connection_timeout=timeout,
            )
            sync_subscribe_by_method(
                listening_client,
                subscription_method,
                cluster_mode,
                channels=channels,
                patterns=patterns,
                sharded=sharded,
                timeout_ms=timeout if timeout else 5000,
            )

        yield listening_client, publishing_client

    finally:
        if subscription_method.value == 0:  # Config
            sync_client_cleanup(listening_client, pub_sub if cluster_mode else None)
        else:
            sync_client_cleanup(listening_client, None)
        sync_client_cleanup(publishing_client, None)


def sync_get_message_by_method(
    method: MessageReadMethod,
    client,
    messages: Optional[List[PubSubMsg]] = None,
    index: Optional[int] = None,
) -> PubSubMsg:
    """
    Get a pubsub message using the specified read method (sync version).

    Args:
        method: How to read the message (Async, Sync, or Callback)
        client: The sync client to read from
        messages: List of messages from callback (required for Callback method)
        index: Index in messages list (required for Callback method)

    Returns:
        Decoded PubSubMsg
    """
    if method == MessageReadMethod.Async:
        return decode_pubsub_msg(client.get_pubsub_message())
    elif method == MessageReadMethod.Sync:
        return decode_pubsub_msg(client.try_get_pubsub_message())
    assert messages and (index is not None)
    return decode_pubsub_msg(messages[index])


def sync_check_no_messages_left(
    method: MessageReadMethod,
    client,
    callback: Optional[List[Any]] = None,
    expected_callback_messages_count: int = 0,
) -> None:
    """
    Verify there are no more messages to read (sync version).

    Args:
        method: The read method being used
        client: The sync client to check
        callback: Callback message list (for Callback method)
        expected_callback_messages_count: Expected number of messages in callback list

    Raises:
        AssertionError if there are unexpected messages
    """
    import pytest

    from tests.utils.utils import run_sync_func_with_timeout_in_thread

    if method == MessageReadMethod.Async:
        # assert there are no messages to read
        with pytest.raises(TimeoutError):
            run_sync_func_with_timeout_in_thread(
                lambda: client.get_pubsub_message(),  # This blocks indefinitely
                timeout=3.0,
            )
    elif method == MessageReadMethod.Sync:
        assert client.try_get_pubsub_message() is None
    else:
        assert callback is not None
        assert len(callback) == expected_callback_messages_count


def sync_wait_for_subscription_state(
    client,
    expected_channels: Optional[Set[str]] = None,
    expected_patterns: Optional[Set[str]] = None,
    expected_sharded: Optional[Set[str]] = None,
    timeout_sec: float = 3.0,
    check_actual: bool = True,
) -> None:
    """
    Poll for subscription state to match expected channels/patterns (sync version).

    Args:
        client: The sync client to check
        expected_channels: Expected exact channels (None to skip check, empty set to check for no channels)
        expected_patterns: Expected pattern channels (None to skip check, empty set to check for no patterns)
        expected_sharded: Expected sharded channels (None to skip check, empty set to check for no sharded)
        timeout_sec: Maximum time to wait in seconds
        check_actual: If True, check actual_subscriptions; if False, check desired_subscriptions
    """
    import time

    modes = get_pubsub_modes(client)
    start_time = time.time()

    while time.time() - start_time < timeout_sec:
        state = client.get_subscriptions()
        subs = (
            state.actual_subscriptions if check_actual else state.desired_subscriptions
        )

        # For each subscription type, check if it matches expected
        # If expected is None, skip the check
        # If expected is empty set, check that actual is also empty
        # If expected is non-empty set, check that expected is subset of actual
        channels_match = (
            expected_channels is None
            or (len(expected_channels) == 0 and len(subs[modes.Exact]) == 0)
            or (
                len(expected_channels) > 0
                and expected_channels.issubset(subs[modes.Exact])
            )
        )
        patterns_match = (
            expected_patterns is None
            or (len(expected_patterns) == 0 and len(subs[modes.Pattern]) == 0)
            or (
                len(expected_patterns) > 0
                and expected_patterns.issubset(subs[modes.Pattern])
            )
        )
        sharded_match = (
            expected_sharded is None
            or not hasattr(modes, "Sharded")
            or (len(expected_sharded) == 0 and len(subs.get(modes.Sharded, set())) == 0)  # type: ignore[union-attr,arg-type]
            or (len(expected_sharded) > 0 and expected_sharded.issubset(subs.get(modes.Sharded, set())))  # type: ignore[union-attr,arg-type]
        )

        if channels_match and patterns_match and sharded_match:
            return

        time.sleep(0.1)

    # Final check with detailed error
    state = client.get_subscriptions()
    subs = state.actual_subscriptions if check_actual else state.desired_subscriptions

    if expected_channels is not None:
        if len(expected_channels) == 0 and len(subs[modes.Exact]) > 0:
            raise AssertionError(f"Expected no channels but found {subs[modes.Exact]}")
        elif len(expected_channels) > 0 and not expected_channels.issubset(
            subs[modes.Exact]
        ):
            raise AssertionError(
                f"Expected channels {expected_channels} not in {subs[modes.Exact]}"
            )

    if expected_patterns is not None:
        if len(expected_patterns) == 0 and len(subs[modes.Pattern]) > 0:
            raise AssertionError(
                f"Expected no patterns but found {subs[modes.Pattern]}"
            )
        elif len(expected_patterns) > 0 and not expected_patterns.issubset(
            subs[modes.Pattern]
        ):
            raise AssertionError(
                f"Expected patterns {expected_patterns} not in {subs[modes.Pattern]}"
            )

    if expected_sharded is not None and hasattr(modes, "Sharded"):
        sharded_subs = subs.get(modes.Sharded, set())  # type: ignore[union-attr,arg-type]
        if len(expected_sharded) == 0 and len(sharded_subs) > 0:
            raise AssertionError(
                f"Expected no sharded channels but found {sharded_subs}"
            )
        elif len(expected_sharded) > 0 and not expected_sharded.issubset(sharded_subs):
            raise AssertionError(
                f"Expected sharded {expected_sharded} not in {sharded_subs}"
            )


def sync_wait_for_subscription_state_if_needed(
    client,
    subscription_method: SubscriptionMethod,
    expected_channels: Optional[Set[str]] = None,
    expected_patterns: Optional[Set[str]] = None,
    expected_sharded: Optional[Set[str]] = None,
    timeout_sec: float = 5.0,
) -> None:
    """
    Wait for subscription state based on subscription method (sync version).

    - Lazy: wait/poll until state matches (with timeout)
    - Blocking and Config: verify immediately (should already be established)

    This mirrors the async wait_for_subscription_state_if_needed function.
    For Blocking and Config methods, subscriptions should be established immediately,
    so we only assert rather than wait - this ensures we catch API contract violations.

    Args:
        client: The sync client to check
        subscription_method: The method used to subscribe
        expected_channels: Expected exact channels
        expected_patterns: Expected pattern channels
        expected_sharded: Expected sharded channels
        timeout_sec: Maximum time to wait (only used for Lazy method)
    """
    from glide_sync import GlideClusterClient as SyncGlideClusterClient

    # Lazy subscriptions may need time to reconcile
    if subscription_method == SubscriptionMethod.Lazy:
        sync_wait_for_subscription_state(
            client,
            expected_channels=expected_channels,
            expected_patterns=expected_patterns,
            expected_sharded=expected_sharded,
            timeout_sec=timeout_sec,
        )
        return

    # Blocking and Config should already be established - verify immediately
    state = client.get_subscriptions()
    actual = state.actual_subscriptions

    # Define empty set with proper type
    empty_set: Set[str] = set()

    if isinstance(client, SyncGlideClusterClient):
        ClusterModes = GlideClusterClientConfiguration.PubSubChannelModes

        if expected_channels is not None:
            actual_channels = actual.get(ClusterModes.Exact, empty_set)
            assert (
                actual_channels == expected_channels
            ), f"Expected channels {expected_channels}, got {actual_channels}"

        if expected_patterns is not None:
            actual_patterns = actual.get(ClusterModes.Pattern, empty_set)
            assert (
                actual_patterns == expected_patterns
            ), f"Expected patterns {expected_patterns}, got {actual_patterns}"

        if expected_sharded is not None:
            actual_sharded = actual.get(ClusterModes.Sharded, empty_set)
            assert (
                actual_sharded == expected_sharded
            ), f"Expected sharded {expected_sharded}, got {actual_sharded}"
    else:
        StandaloneModes = GlideClientConfiguration.PubSubChannelModes

        if expected_channels is not None:
            actual_channels = actual.get(StandaloneModes.Exact, empty_set)
            assert (
                actual_channels == expected_channels
            ), f"Expected channels {expected_channels}, got {actual_channels}"

        if expected_patterns is not None:
            actual_patterns = actual.get(StandaloneModes.Pattern, empty_set)
            assert (
                actual_patterns == expected_patterns
            ), f"Expected patterns {expected_patterns}, got {actual_patterns}"
