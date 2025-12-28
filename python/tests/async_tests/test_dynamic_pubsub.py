# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from __future__ import annotations

# Enable Rust mock for all tests
from enum import IntEnum
from typing import Any, Dict, List, Optional, Set, Tuple, Type, Union, cast, overload

import anyio
import pytest
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ProtocolVersion,
)
from glide_shared.exceptions import RequestError
from glide_shared.exceptions import TimeoutError as GlideTimeoutError
from glide_shared.routes import AllNodes

from tests.async_tests.conftest import create_client
from tests.utils.utils import (
    create_pubsub_subscription,
    decode_pubsub_msg,
    new_message,
    wait_for_subscription_state,
)


class MethodTesting(IntEnum):
    """
    Enumeration for specifying the method of PUBSUB subscription.
    """

    Async = 0
    "Uses asynchronous subscription method."
    Sync = 1
    "Uses synchronous subscription method."
    Callback = 2
    "Uses callback-based subscription method."


class SubscriptionMethod(IntEnum):
    """
    Enumeration for specifying the subscription method.
    """

    Lazy = 0
    "Uses non-blocking (lazy) subscription method."
    Blocking = 1
    "Uses blocking subscription method with timeout."


async def create_two_clients_with_pubsub(
    request,
    cluster_mode,
    pubsubs: list[Optional[Any]],
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = None,
) -> Tuple[TGlideClient, TGlideClient]:
    if len(pubsubs) == 0:
        pubsubs = [None, None]
    elif len(pubsubs) == 1:
        pubsubs = [pubsubs[0], None]
    else:
        pubsubs = pubsubs[:2]

    clients = []

    for pubsub in pubsubs:
        if cluster_mode:
            cluster_mode_pubsub = pubsub
            standalone_mode_pubsub = None
        else:
            cluster_mode_pubsub = None
            standalone_mode_pubsub = pubsub

        try:
            client = await create_client(
                request,
                cluster_mode=cluster_mode,
                cluster_mode_pubsub=cluster_mode_pubsub,
                standalone_mode_pubsub=standalone_mode_pubsub,
                protocol=protocol,
                request_timeout=timeout,
            )

            clients.append(client)
        except Exception:
            for c in clients:
                await c.close()
            raise

    return clients[0], clients[1]


def _extract_expected_subscriptions(
    pubsub_config: Any,
    cluster_mode: bool,
    client: TGlideClient,
) -> Tuple[Optional[Set[str]], Optional[Set[str]], Optional[Set[str]]]:
    """
    Extract expected channels, patterns, and sharded subscriptions from a pubsub config.

    Args:
        pubsub_config: PubSubSubscriptions configuration object
        cluster_mode: whether in cluster mode

    Returns:
        Tuple of (expected_channels, expected_patterns, expected_sharded)
    """
    PubSubChannelModes = get_pubsub_channel_modes_from_client(client)

    channels_and_patterns = pubsub_config.channels_and_patterns

    expected_channels = (
        channels_and_patterns.get(
            PubSubChannelModes.Exact, set()  # type: ignore[arg-type]
        )
        or None
    )
    expected_patterns = (
        channels_and_patterns.get(
            PubSubChannelModes.Pattern, set()  # type: ignore[arg-type]
        )
        or None
    )
    expected_sharded = None

    if cluster_mode:
        expected_sharded = (
            channels_and_patterns.get(
                PubSubChannelModes.Sharded, set()  # type: ignore[arg-type, union-attr]
            )
            or None
        )

    if expected_channels == set():
        expected_channels = None
    if expected_patterns == set():
        expected_patterns = None
    if expected_sharded == set():
        expected_sharded = None

    return expected_channels, expected_patterns, expected_sharded


async def get_message_by_method(
    method: MethodTesting,
    client: TGlideClient,
    messages: Optional[List[PubSubMsg]] = None,
    index: Optional[int] = None,
):
    if method == MethodTesting.Async:
        return decode_pubsub_msg(await client.get_pubsub_message())
    elif method == MethodTesting.Sync:
        return decode_pubsub_msg(client.try_get_pubsub_message())
    assert messages and (index is not None)
    return decode_pubsub_msg(messages[index])


async def check_no_messages_left(
    method,
    client: TGlideClient,
    callback: Optional[List[Any]] = None,
    expected_callback_messages_count: int = 0,
):
    if method == MethodTesting.Async:
        # assert there are no messages to read
        with pytest.raises(TimeoutError):
            with anyio.fail_after(3):
                await client.get_pubsub_message()
    elif method == MethodTesting.Sync:
        assert client.try_get_pubsub_message() is None
    else:
        assert callback is not None
        assert len(callback) == expected_callback_messages_count


async def subscribe_by_method(
    client: TGlideClient,
    subscription_method: SubscriptionMethod,
    channels: Set[str],
    timeout_ms: int = 5000,
) -> None:
    """Subscribe using the specified method."""
    if subscription_method == SubscriptionMethod.Lazy:
        await client.subscribe_lazy(channels)
    else:
        await client.subscribe(channels, timeout_ms=timeout_ms)


async def unsubscribe_by_method(
    client: TGlideClient,
    subscription_method: SubscriptionMethod,
    channels: Optional[Set[str]] = None,
    timeout_ms: int = 5000,
) -> None:
    """Unsubscribe using the specified method."""
    if subscription_method == SubscriptionMethod.Lazy:
        await client.unsubscribe_lazy(channels)
    else:
        await client.unsubscribe(channels, timeout_ms=timeout_ms)


async def psubscribe_by_method(
    client: TGlideClient,
    subscription_method: SubscriptionMethod,
    patterns: Set[str],
    timeout_ms: int = 5000,
) -> None:
    """Pattern subscribe using the specified method."""
    if subscription_method == SubscriptionMethod.Lazy:
        await client.psubscribe_lazy(patterns)
    else:
        await client.psubscribe(patterns, timeout_ms=timeout_ms)


async def punsubscribe_by_method(
    client: TGlideClient,
    subscription_method: SubscriptionMethod,
    patterns: Optional[Set[str]] = None,
    timeout_ms: int = 5000,
) -> None:
    """Pattern unsubscribe using the specified method."""
    if subscription_method == SubscriptionMethod.Lazy:
        await client.punsubscribe_lazy(patterns)
    else:
        await client.punsubscribe(patterns, timeout_ms=timeout_ms)


async def ssubscribe_by_method(
    client: GlideClusterClient,
    subscription_method: SubscriptionMethod,
    channels: Set[str],
    timeout_ms: int = 5000,
) -> None:
    """Sharded subscribe using the specified method."""
    if subscription_method == SubscriptionMethod.Lazy:
        await client.ssubscribe_lazy(channels)
    else:
        await client.ssubscribe(channels, timeout_ms=timeout_ms)


async def sunsubscribe_by_method(
    client: GlideClusterClient,
    subscription_method: SubscriptionMethod,
    channels: Optional[Set[str]] = None,
    timeout_ms: int = 5000,
) -> None:
    """Sharded unsubscribe using the specified method."""
    if subscription_method == SubscriptionMethod.Lazy:
        await client.sunsubscribe_lazy(channels)
    else:
        await client.sunsubscribe(channels, timeout_ms=timeout_ms)


async def wait_for_subscription_if_needed(
    client: TGlideClient,
    subscription_method: SubscriptionMethod,
    expected_channels: Optional[Set[str]] = None,
    expected_patterns: Optional[Set[str]] = None,
    expected_sharded: Optional[Set[str]] = None,
    timeout: int = 1000,
) -> None:
    """
    Wait for subscription state if using lazy method.
    For blocking method, assert subscriptions are immediately active.
    """
    if subscription_method == SubscriptionMethod.Lazy:
        await wait_for_subscription_state(
            client,
            expected_channels=expected_channels,
            expected_patterns=expected_patterns,
            expected_sharded=expected_sharded,
            timeout_ms=timeout,
        )
    else:
        # For blocking, verify immediately
        state = await client.get_subscriptions()

        PubSubChannelModes = get_pubsub_channel_modes_from_client(client)

        if expected_channels is not None:
            actual_channels = state.actual_subscriptions.get(
                PubSubChannelModes.Exact, set()  # type: ignore[arg-type]
            )
            assert (
                actual_channels == expected_channels
            ), f"Expected channels {expected_channels}, got {actual_channels}"

        if expected_patterns is not None:
            actual_patterns = state.actual_subscriptions.get(
                PubSubChannelModes.Pattern, set()  # type: ignore[arg-type]
            )
            assert (
                actual_patterns == expected_patterns
            ), f"Expected patterns {expected_patterns}, got {actual_patterns}"

        if expected_sharded is not None and isinstance(client, GlideClusterClient):
            actual_sharded = state.actual_subscriptions.get(
                PubSubChannelModes.Sharded, set()  # type: ignore[arg-type, union-attr]
            )
            assert (
                actual_sharded == expected_sharded
            ), f"Expected sharded {expected_sharded}, got {actual_sharded}"


ClusterModes = GlideClusterClientConfiguration.PubSubChannelModes
StandaloneModes = GlideClientConfiguration.PubSubChannelModes


@overload
def get_pubsub_channel_modes_from_client(
    client: GlideClusterClient,
) -> Type[ClusterModes]: ...


@overload
def get_pubsub_channel_modes_from_client(
    client: GlideClient,
) -> Type[StandaloneModes]: ...


def get_pubsub_channel_modes_from_client(
    client: TGlideClient,
) -> Union[Type[ClusterModes], Type[StandaloneModes]]:
    """Get the appropriate PubSubChannelModes enum from a client instance."""
    if isinstance(client, GlideClusterClient):
        return GlideClusterClientConfiguration.PubSubChannelModes
    return GlideClientConfiguration.PubSubChannelModes


async def client_cleanup(
    client: Optional[Union[GlideClient, GlideClusterClient]],
):
    """
    This function tries its best to clear state associated with client.
    """
    if client is None:
        return

    cleanup_error = None

    try:
        PubSubChannelModes = get_pubsub_channel_modes_from_client(client)

        # Get subscription state
        state = await client.get_subscriptions()

        # Extract actual subscriptions
        has_channels = (
            len(state.actual_subscriptions.get(PubSubChannelModes.Exact) or set())  # type: ignore[arg-type]
        ) > 0
        has_patterns = (
            len(state.actual_subscriptions.get(PubSubChannelModes.Pattern) or set())  # type: ignore[arg-type]
        ) > 0
        has_sharded = (
            isinstance(client, GlideClusterClient)
            and len(state.actual_subscriptions.get(PubSubChannelModes.Sharded) or set())  # type: ignore[arg-type, union-attr]
        ) > 0

        # Unsubscribe from all active subscriptions using lazy methods
        if has_channels:
            await client.unsubscribe_lazy()
        if has_patterns:
            await client.punsubscribe_lazy()
        if isinstance(client, GlideClusterClient) and has_sharded:
            await client.sunsubscribe_lazy()

        if has_channels or has_patterns or has_sharded:
            await wait_for_subscription_state(
                client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set(),
            )

    except Exception as e:
        cleanup_error = e
    finally:
        await client.close()
        del client
        await anyio.sleep(1)

        if cleanup_error:
            raise cleanup_error


@pytest.mark.anyio
class TestDynamicPubSub:
    """Tests for dynamic PubSub subscription/unsubscription API"""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_subscribe_basic(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic subscription using both lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "channel_1"
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            # We need to specify the pubsub param here in order to register the callback
            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if cluster_mode and callback
                    else None
                ),
                standalone_mode_pubsub=(
                    GlideClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if not cluster_mode and callback
                    else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)
            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels=set(),
                expected_patterns=set(),
            )
            await subscribe_by_method(listening_client, subscription_method, {channel})

            # Verify subscription is active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels={channel}
            )
            await publishing_client.publish(message, channel)
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None
            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_unsubscribe_basic(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic unsubscription using both lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "channel_1"
            message1 = "message_1"
            message2 = "message_2"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
                {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify subscription is active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels={channel}
            )

            await publishing_client.publish(message1, channel)
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            await unsubscribe_by_method(
                listening_client, subscription_method, {channel}
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels=set()
            )

            # Publish second message - should not be received
            await publishing_client.publish(message2, channel)
            await anyio.sleep(1)

            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_psubscribe_basic(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic pattern subscription using both lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            pattern = "news.*"
            channel1 = "news.sports"
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if cluster_mode and callback
                    else None
                ),
                standalone_mode_pubsub=(
                    GlideClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if not cluster_mode and callback
                    else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)

            # Verify no subscriptions initially
            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels=set(),
                expected_patterns=set(),
            )

            await psubscribe_by_method(listening_client, subscription_method, {pattern})

            # Verify pattern subscription is active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_patterns={pattern}
            )

            await publishing_client.publish(message, channel1)
            await anyio.sleep(1)

            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel1
            assert pubsub_msg.pattern == pattern

            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_punsubscribe_basic(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic pattern unsubscription using both lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            pattern = "news.*"
            channel = "news.sports"
            message1 = "message_1"
            message2 = "message_2"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {pattern}},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {pattern}},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify pattern subscription is active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_patterns={pattern}
            )

            await publishing_client.publish(message1, channel)
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            await punsubscribe_by_method(
                listening_client, subscription_method, {pattern}
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_patterns=set()
            )

            # Publish second message - should not be received
            await publishing_client.publish(message2, channel)
            await anyio.sleep(1)

            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_ssubscribe_basic(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic sharded subscription using both lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "channel_sharded_1"
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if callback
                    else None
                ),
            )

            publishing_client = await create_client(request, cluster_mode)
            
            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels=set(),
                expected_sharded=set(),
            )

            await ssubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                subscription_method,
                {channel},
            )

            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_sharded={channel},
            )

            await cast(GlideClusterClient, publishing_client).publish(
                message, channel, sharded=True
            )

            await anyio.sleep(1)

            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )

            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_sunsubscribe_basic(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic sharded unsubscription using both lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "channel_sharded_1"
            message1 = "message_1"
            message2 = "message_2"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
                {},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded={channel}
            )

            await cast(GlideClusterClient, publishing_client).publish(
                message1, channel, sharded=True
            )
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            await sunsubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                subscription_method,
                {channel},
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=set()
            )

            # Publish second message - should not be received
            await cast(GlideClusterClient, publishing_client).publish(
                message2, channel, sharded=True
            )
            await anyio.sleep(1)

            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_subscribe_coexistence_async_sync(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test that async and sync message retrieval can coexist for dynamically subscribed channels.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "channel_1"
            message1 = "message_1"
            message2 = "message_2"

            listening_client = await create_client(request, cluster_mode)
            publishing_client = await create_client(request, cluster_mode)

            # Subscribe dynamically
            await subscribe_by_method(listening_client, subscription_method, {channel})
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels={channel}
            )

            # Publish two messages
            await publishing_client.publish(message1, channel)
            await publishing_client.publish(message2, channel)
            await anyio.sleep(1)

            # Retrieve using both async and sync methods
            async_msg = decode_pubsub_msg(await listening_client.get_pubsub_message())
            sync_msg = decode_pubsub_msg(listening_client.try_get_pubsub_message())

            assert async_msg.message in [message1, message2]
            assert sync_msg.message in [message1, message2]
            assert async_msg.message != sync_msg.message

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)
    
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_subscribe_many_exact_channels(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test subscribing to many exact channels at once.
        Verifies that all channels are subscribed and messages are received on each.
        """
        listening_client, publishing_client = None, None
        try:
            num_channels = 256
            channels = {f"channel_{i}" for i in range(num_channels)}
            messages = {ch: f"message_for_{ch}" for ch in channels}

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if cluster_mode and callback
                    else None
                ),
                standalone_mode_pubsub=(
                    GlideClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if not cluster_mode and callback
                    else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)

            # Subscribe to all channels at once
            await subscribe_by_method(listening_client, subscription_method, channels)

            # Verify all subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels=channels
            )

            # Verify state shows all channels
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)
            actual_channels = state.actual_subscriptions.get(
                PubSubChannelModes.Exact, set()  # type: ignore[arg-type]
            )
            assert actual_channels == channels, f"Expected {channels}, got {actual_channels}"

            # Publish to all channels
            for channel, message in messages.items():
                await publishing_client.publish(message, channel)

            await anyio.sleep(1)

            # Collect all received messages
            received_messages: Dict[str, str] = {}
            for index in range(num_channels):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                received_messages[pubsub_msg.channel] = pubsub_msg.message
                assert pubsub_msg.pattern is None, "Exact subscription should have no pattern"

            # Verify all messages were received correctly
            assert received_messages == messages, f"Expected {messages}, got {received_messages}"

            await check_no_messages_left(
                method, listening_client, callback_messages, num_channels
            )

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_psubscribe_many_patterns(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test subscribing to many patterns at once.
        Verifies that all patterns are subscribed and messages are received for matching channels.
        """
        listening_client, publishing_client = None, None
        try:
            num_patterns = 256
            patterns = {f"pattern_{i}.*" for i in range(num_patterns)}
            # Create channels that match each pattern
            pattern_to_channel = {f"pattern_{i}.*": f"pattern_{i}.test" for i in range(num_patterns)}
            messages = {ch: f"message_for_{ch}" for ch in pattern_to_channel.values()}

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if cluster_mode and callback
                    else None
                ),
                standalone_mode_pubsub=(
                    GlideClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if not cluster_mode and callback
                    else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)

            # Subscribe to all patterns at once
            await psubscribe_by_method(listening_client, subscription_method, patterns)

            # Verify all pattern subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_patterns=patterns
            )

            # Verify state shows all patterns
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)
            actual_patterns = state.actual_subscriptions.get(
                PubSubChannelModes.Pattern, set()  # type: ignore[arg-type]
            )
            assert actual_patterns == patterns, f"Expected {patterns}, got {actual_patterns}"

            # Publish to channels matching each pattern
            for channel, message in messages.items():
                await publishing_client.publish(message, channel)

            await anyio.sleep(1)

            # Collect all received messages
            received_messages: Dict[str, Tuple[str, Optional[str]]] = {}
            for index in range(num_patterns):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                received_messages[pubsub_msg.channel] = (pubsub_msg.message, pubsub_msg.pattern)

            # Verify all messages were received with correct pattern
            assert len(received_messages) == num_patterns
            for channel, (message, pattern) in received_messages.items():
                assert message == messages[channel], f"Wrong message for {channel}"
                assert pattern is not None, f"Pattern should be set for {channel}"
                assert pattern in patterns, f"Pattern {pattern} not in subscribed patterns"

            await check_no_messages_left(
                method, listening_client, callback_messages, num_patterns
            )

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])  # Sharded only in cluster mode
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_ssubscribe_many_sharded_channels(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test subscribing to many sharded channels at once.
        This specifically tests that channels hashing to different slots are handled correctly.
        Verifies that all channels are subscribed and messages are received on each.
        """
        listening_client, publishing_client = None, None
        try:
            # Use channel names that will hash to different slots
            # This ensures we test the slot-based routing properly
            num_channels = 256
            channels = {f"sharded_ch_{i}" for i in range(num_channels)}
            messages = {ch: f"message_for_{ch}" for ch in channels}

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if callback
                    else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)

            # Subscribe to all sharded channels at once
            await ssubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                subscription_method,
                channels,
            )

            # Verify all sharded subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=channels
            )

            # Verify state shows all sharded channels
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)
            actual_sharded = state.actual_subscriptions.get(
                PubSubChannelModes.Sharded, set()  # type: ignore[arg-type, union-attr]
            )
            assert actual_sharded == channels, f"Expected {channels}, got {actual_sharded}"

            # Publish to all sharded channels
            for channel, message in messages.items():
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            await anyio.sleep(1)

            # Collect all received messages
            received_messages: Dict[str, str] = {}
            for index in range(num_channels):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                received_messages[pubsub_msg.channel] = pubsub_msg.message
                assert pubsub_msg.pattern is None, "Sharded subscription should have no pattern"

            # Verify all messages were received correctly
            assert received_messages == messages, f"Expected {messages}, got {received_messages}"

            await check_no_messages_left(
                method, listening_client, callback_messages, num_channels
            )

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_ssubscribe_channels_different_slots(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test subscribing to sharded channels that explicitly hash to different slots.
        This is a targeted test to verify CrossSlot handling.
        """
        listening_client, publishing_client = None, None
        try:
            channels = {
                "{slot1}channel_a", 
                "{slot2}channel_b",
                "{slot3}channel_c",
                "{slot1}channel_d",
                "{slot4}channel_e",
            }
            messages = {ch: f"msg_{ch}" for ch in channels}

            listening_client = await create_client(request, cluster_mode)
            publishing_client = await create_client(request, cluster_mode)

            # Subscribe to all channels - should handle multiple slots
            await ssubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                subscription_method,
                channels,
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=channels
            )

            # Verify state
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)
            actual_sharded = state.actual_subscriptions.get(
                PubSubChannelModes.Sharded, set()  # type: ignore[arg-type, union-attr]
            )
            assert actual_sharded == channels, f"Expected {channels}, got {actual_sharded}"

            # Publish and verify
            for channel, message in messages.items():
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            await anyio.sleep(1)

            received_messages: Dict[str, str] = {}
            for _ in range(len(channels)):
                msg = decode_pubsub_msg(await listening_client.get_pubsub_message())
                received_messages[msg.channel] = msg.message

            assert received_messages == messages

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_sunsubscribe_channels_different_slots(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test unsubscribing from sharded channels that hash to different slots.
        This verifies the CrossSlot fix for SUNSUBSCRIBE.
        """
        listening_client, publishing_client = None, None
        try:
            # Channels that hash to different slots
            channels = {
                "{slotA}channel_1",
                "{slotB}channel_2",
                "{slotC}channel_3",
                "{slotA}channel_4",
            }
            message = "test_message"

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Sharded: channels},
                {},
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify all subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=channels
            )

            # Unsubscribe from all channels at once (tests CrossSlot handling)
            await sunsubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                subscription_method,
                channels,
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=set()
            )

            # Verify no subscriptions remain
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)
            actual_sharded = state.actual_subscriptions.get(
                PubSubChannelModes.Sharded, set()  # type: ignore[arg-type, union-attr]
            )
            assert actual_sharded == set(), f"Expected empty, got {actual_sharded}"

            # Verify no messages received after unsubscribe
            for channel in channels:
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            await anyio.sleep(1)

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_unsubscribe_all_channels(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test unsubscribing from all channels using unsubscribe with no arguments.
        """
        listening_client, publishing_client = None, None
        try:
            channels = {"channel_1", "channel_2", "channel_3"}
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Exact: channels},
                {GlideClientConfiguration.PubSubChannelModes.Exact: channels},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify all subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels=channels
            )

            # Unsubscribe from all channels
            await unsubscribe_by_method(listening_client, subscription_method, None)

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels=set()
            )

            # Publish to any channel - should not be received
            await publishing_client.publish(message, list(channels)[0])
            await anyio.sleep(1)

            await check_no_messages_left(method, listening_client, callback_messages, 0)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)
            
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_punsubscribe_all_patterns(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test unsubscribing from all patterns using punsubscribe with no arguments.
        """
        listening_client, publishing_client = None, None
        try:
            patterns = {"news.*", "updates.*", "alerts.*"}
            # Channels that match each pattern
            channels = ["news.sports", "updates.weather", "alerts.security"]
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: patterns},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: patterns},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify all pattern subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_patterns=patterns
            )

            # Unsubscribe from all patterns (pass None to unsubscribe from all)
            await punsubscribe_by_method(listening_client, subscription_method, None)

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_patterns=set()
            )

            # Publish to channels matching patterns - should not be received
            for channel in channels:
                await publishing_client.publish(message, channel)
            await anyio.sleep(1)

            await check_no_messages_left(method, listening_client, callback_messages, 0)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)


    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_sunsubscribe_all_sharded(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test unsubscribing from all sharded channels using sunsubscribe with no arguments.
        """
        listening_client, publishing_client = None, None
        try:
            sharded_channels = {"sharded_channel_1", "sharded_channel_2", "sharded_channel_3"}
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Sharded: sharded_channels},
                {},  # Standalone doesn't support sharded
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify all sharded subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=sharded_channels
            )

            # Unsubscribe from all sharded channels (pass None to unsubscribe from all)
            await sunsubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                subscription_method,
                None,
            )

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_sharded=set()
            )

            # Publish to any sharded channel - should not be received
            await cast(GlideClusterClient, publishing_client).publish(
                message, list(sharded_channels)[0], sharded=True
            )
            await anyio.sleep(1)

            await check_no_messages_left(method, listening_client, callback_messages, 0)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)
            
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_mixed_lazy_and_blocking(self, request, cluster_mode: bool):
        """
        Test mixing lazy and blocking subscription operations.
        """
        listening_client, publishing_client = None, None
        try:
            channel1 = "channel_1"
            channel2 = "channel_2"
            message1 = "message_1"
            message2 = "message_2"

            listening_client = await create_client(request, cluster_mode)
            publishing_client = await create_client(request, cluster_mode)

            # Use lazy subscribe for first channel
            await subscribe_by_method(
                listening_client, SubscriptionMethod.Lazy, {channel1}
            )

            # Use blocking subscribe for second channel
            await subscribe_by_method(
                listening_client, SubscriptionMethod.Blocking, {channel2}
            )

            # Wait for lazy subscription to be applied
            await wait_for_subscription_state(
                listening_client, expected_channels={channel1, channel2}
            )

            # Publish to both
            await publishing_client.publish(message1, channel1)
            await publishing_client.publish(message2, channel2)
            await anyio.sleep(1)

            # Verify both messages received
            received = set()
            for _ in range(2):
                msg = await listening_client.get_pubsub_message()
                received.add(decode_pubsub_msg(msg).channel)

            assert received == {channel1, channel2}

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_get_subscriptions_empty(self, request, cluster_mode: bool):
        """
        Test get_subscriptions() with no subscriptions returns empty sets.
        """
        client = None
        try:
            client = await create_client(request, cluster_mode)

            state = await client.get_subscriptions()

            PubSubChannelModes = get_pubsub_channel_modes_from_client(client)

            # Check desired subscriptions are empty
            assert (
                state.desired_subscriptions.get(PubSubChannelModes.Exact)  # type: ignore[arg-type]
                == set()
            )
            assert (
                state.desired_subscriptions.get(PubSubChannelModes.Pattern)  # type: ignore[arg-type]
                == set()
            )

            # Check actual subscriptions are empty
            assert (
                state.actual_subscriptions.get(PubSubChannelModes.Exact) == set()  # type: ignore[arg-type]
            )
            assert (
                state.actual_subscriptions.get(PubSubChannelModes.Pattern)  # type: ignore[arg-type]
                == set()
            )

            if cluster_mode:
                assert (
                    state.desired_subscriptions.get(PubSubChannelModes.Sharded)  # type: ignore[arg-type, union-attr]
                    == set()
                )
                assert (
                    state.actual_subscriptions.get(PubSubChannelModes.Sharded)  # type: ignore[arg-type, union-attr]
                    == set()
                )

        finally:
            await client_cleanup(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_lazy_vs_blocking_timeout(self, request, cluster_mode: bool):
        """Test that blocking subscribe times out when reconciliation can't complete"""
        username = "mock_test_user_1"
        password = "password_1"
        channel = "channel_1"

        admin_client = await create_client(request, cluster_mode)

        # Create user without pubsub permissions
        acl_command: List[Union[str, bytes]] = [
            "ACL",
            "SETUSER",
            username,
            "ON",
            f">{password}",
            "~*",
            "resetchannels",
            "+@all",
            "-@pubsub",
        ]
        if cluster_mode:
            await cast(GlideClusterClient, admin_client).custom_command(
                acl_command, route=AllNodes()
            )
        else:
            await admin_client.custom_command(acl_command)

        client = await create_client(request, cluster_mode)

        auth_cmd: List[Union[str, bytes]] = ["AUTH", username, password]
        if cluster_mode:
            await cast(GlideClusterClient, client).custom_command(
                auth_cmd, route=AllNodes()
            )
        else:
            await client.custom_command(auth_cmd)

        # Lazy subscribe should succeed (desired state updated)
        await client.subscribe_lazy({channel})

        # Blocking subscribe should timeout
        with pytest.raises(GlideTimeoutError):
            await client.subscribe({channel}, timeout_ms=1000)

        await client_cleanup(client)
        await client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_desired_vs_actual_state_during_reconciliation(
        self, request, cluster_mode: bool
    ):
        """Test that desired state updates immediately but actual state updates after delay"""
        username = "mock_test_user_1"
        password = "[REDACTED:PASSWORD]"
        channel = "channel_1"

        admin_client = await create_client(request, cluster_mode)

        # Block subscriptions
        acl_command: List[Union[str, bytes]] = [
            "ACL",
            "SETUSER",
            username,
            "ON",
            f">{password}",
            "~*",
            "resetchannels",
            "+@all",
            "-@pubsub",
        ]
        if cluster_mode:
            await cast(GlideClusterClient, admin_client).custom_command(
                acl_command, route=AllNodes()
            )
        else:
            await admin_client.custom_command(acl_command)

        client = await create_client(request, cluster_mode)
        auth_cmd: List[Union[str, bytes]] = ["AUTH", username, password]
        if cluster_mode:
            await cast(GlideClusterClient, client).custom_command(
                auth_cmd, route=AllNodes()
            )
        else:
            await client.custom_command(auth_cmd)

        # Subscribe (will be blocked)
        await client.subscribe_lazy({channel})

        # Immediately check state - desired should be updated, actual should not
        await anyio.sleep(0.1)  # Small delay to ensure desired is set
        state = await client.get_subscriptions()
        PubSubChannelModes = get_pubsub_channel_modes_from_client(client)

        desired = state.desired_subscriptions.get(PubSubChannelModes.Exact)  # type: ignore[arg-type]
        actual = state.actual_subscriptions.get(PubSubChannelModes.Exact)  # type: ignore[arg-type]

        assert channel in cast(
            set[str], desired
        ), "Desired should be updated immediately"
        assert channel not in cast(
            set[str], actual
        ), "Actual should not be updated yet (blocked)"

        await client_cleanup(client)
        await client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_unsubscribe_from_nonexistent(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """Test that unsubscribing from non-subscribed channel is safe"""
        client = await create_client(request, cluster_mode)
        channel = "channel_1"

        # Unsubscribe without ever subscribing (should be no-op)
        await unsubscribe_by_method(client, subscription_method, {channel})

        # Verify state is empty
        state = await client.get_subscriptions()
        PubSubChannelModes = get_pubsub_channel_modes_from_client(client)
        desired = state.desired_subscriptions.get(PubSubChannelModes.Exact, set())  # type: ignore[arg-type]
        actual = state.actual_subscriptions.get(PubSubChannelModes.Exact, set())  # type: ignore[arg-type]

        assert desired == set()
        assert actual == set()

        await client_cleanup(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_pattern_and_exact_same_channel(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test subscribing to both pattern and exact for same channel name.

        This test verifies that when a client subscribes to both a pattern and an exact
        channel name that matches the pattern, messages published to that channel are
        received twice - once for the exact match and once for the pattern match.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "news.sports"
            pattern = "news.*"
            message = "message_1"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=(
                    GlideClusterClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if cluster_mode and callback
                    else None
                ),
                standalone_mode_pubsub=(
                    GlideClientConfiguration.PubSubSubscriptions(
                        channels_and_patterns={},
                        callback=callback,
                        context=context,
                    )
                    if not cluster_mode and callback
                    else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)

            # Subscribe to both exact channel and pattern
            await subscribe_by_method(listening_client, subscription_method, {channel})
            await psubscribe_by_method(listening_client, subscription_method, {pattern})

            # Verify both subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
                expected_patterns={pattern},
            )

            # Publish one message
            await publishing_client.publish(message, channel)
            await anyio.sleep(1)

            # Should receive the message twice - once for exact, once for pattern
            msg1 = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )

            msg2 = await get_message_by_method(
                method, listening_client, callback_messages, 1
            )

            # Both messages should have same content and channel
            assert msg1.message == message
            assert msg2.message == message
            assert msg1.channel == channel
            assert msg2.channel == channel

            # One should be from exact match (no pattern), other from pattern match
            patterns = {msg1.pattern, msg2.pattern}
            assert None in patterns, "One message should have no pattern (exact match)"
            assert pattern in patterns, "One message should have pattern match"

            # Verify no more messages
            await check_no_messages_left(method, listening_client, callback_messages, 2)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_dynamic_subscription_with_initial_config(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test adding dynamic subscriptions to client with initial subscriptions.

        This test verifies that clients created with initial PubSub configuration
        can successfully add more subscriptions dynamically using subscribe_lazy/subscribe
        methods, and that messages are received from both initial and dynamic subscriptions.
        """
        listening_client, publishing_client = None, None
        try:
            initial_channel = "channel_initial"
            dynamic_channel = "channel_dynamic"
            initial_message = "message_initial"
            dynamic_message = "message_dynamic"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            # Create client with initial subscription
            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        initial_channel
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Exact: {initial_channel}},
                callback=callback,
                context=context,
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, [pub_sub]
            )

            # Verify initial subscription is active
            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels={initial_channel},
            )

            # Test initial subscription works
            await publishing_client.publish(initial_message, initial_channel)
            await anyio.sleep(1)

            msg1 = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert msg1.message == initial_message
            assert msg1.channel == initial_channel

            # Now add dynamic subscription
            await subscribe_by_method(
                listening_client, subscription_method, {dynamic_channel}
            )

            # Verify both subscriptions are active
            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels={initial_channel, dynamic_channel},
            )

            # Test both subscriptions work
            await publishing_client.publish(initial_message, initial_channel)
            await publishing_client.publish(dynamic_message, dynamic_channel)
            await anyio.sleep(1)

            # Collect messages (already got 1 from earlier)
            msg2 = await get_message_by_method(
                method, listening_client, callback_messages, 1
            )
            msg3 = await get_message_by_method(
                method, listening_client, callback_messages, 2
            )

            received_messages = {msg2.channel: msg2.message, msg3.channel: msg3.message}

            assert received_messages[initial_channel] == initial_message
            assert received_messages[dynamic_channel] == dynamic_message

            # Verify no more messages
            await check_no_messages_left(method, listening_client, callback_messages, 3)

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_subscribe_empty_set(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test subscribing with empty set is no-op.

        This test verifies that calling subscribe methods with an empty set of channels
        does not cause errors and does not change the subscription state.
        """
        listening_client, publishing_client = None, None
        try:

            listening_client = await create_client(request, cluster_mode)
            publishing_client = await create_client(request, cluster_mode)

            # Get initial state (should be empty)
            initial_state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)

            initial_channels = initial_state.actual_subscriptions.get(
                PubSubChannelModes.Exact  # type: ignore[arg-type]
            )
            initial_patterns = initial_state.actual_subscriptions.get(
                PubSubChannelModes.Pattern  # type: ignore[arg-type]
            )

            assert initial_channels == set()
            assert initial_patterns == set()

            with pytest.raises(RequestError) as e:
                await subscribe_by_method(listening_client, subscription_method, set())
            assert "No channels provided for subscription" in str(e)

            with pytest.raises(RequestError) as e:
                await psubscribe_by_method(listening_client, subscription_method, set())
            assert "No channels provided for subscription" in str(e)

            if cluster_mode:
                with pytest.raises(RequestError) as e:
                    await ssubscribe_by_method(
                        cast(GlideClusterClient, listening_client),
                        subscription_method,
                        set(),
                    )
                assert "No channels provided for subscription" in str(e)

            # Verify state is still empty
            final_state = await listening_client.get_subscriptions()

            final_channels = final_state.actual_subscriptions.get(
                PubSubChannelModes.Exact  # type: ignore[arg-type]
            )
            final_patterns = final_state.actual_subscriptions.get(
                PubSubChannelModes.Pattern  # type: ignore[arg-type]
            )

            assert final_channels == set()
            assert final_patterns == set()

            if cluster_mode:
                initial_sharded = initial_state.actual_subscriptions.get(
                    PubSubChannelModes.Sharded  # type: ignore[arg-type, union-attr]
                )
                final_sharded = final_state.actual_subscriptions.get(
                    PubSubChannelModes.Sharded  # type: ignore[arg-type, union-attr]
                )
                assert initial_sharded == set()
                assert final_sharded == set()

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_subscription_metrics_on_acl_failure(
        self, request, cluster_mode: bool
    ):
        """
    Test that out-of-sync metric is recorded when subscription fails due to ACL.
        """
        listening_client, admin_client = None, None
        try:
            channel = "channel_1"
            # Must use this special prefix for mock interception
            username = "mock_test_user_1"
            password = "password_1"

            # Create admin client to manage ACL (this gets intercepted by mock)
            admin_client = await create_client(request, cluster_mode)

            # This ACL command will be intercepted and set can_subscribe=false
            acl_command: List[Union[str, bytes]] = [
                "ACL",
                "SETUSER",
                username,
                "ON",
                f">{password}",
                "~*",
                "resetchannels",
                "+@all",
                "-@pubsub",
            ]

            if cluster_mode:
                await cast(GlideClusterClient, admin_client).custom_command(
                    acl_command, route=AllNodes()
                )
            else:
                await admin_client.custom_command(acl_command)

            # Create client with mock user (AUTH will be intercepted)
            listening_client = await create_client(request, cluster_mode)

            if cluster_mode:
                await cast(GlideClusterClient, listening_client).custom_command(
                    ["AUTH", username, password], route=AllNodes()
                )
            else:
                await listening_client.custom_command(["AUTH", username, password])
                
            # Get initial metrics
            initial_stats = await listening_client.get_statistics()
            initial_out_of_sync = int(
                initial_stats.get("subscription_out_of_sync_count", "0")
            )

            # Try to subscribe - will fail due to ACL
            await listening_client.subscribe_lazy({channel})

            # Wait for reconciliation attempts
            await anyio.sleep(1.0)

            # Check that out-of-sync metric increased (reconciliation failed)
            stats = await listening_client.get_statistics()
            out_of_sync_count = int(stats.get("subscription_out_of_sync_count", "0"))

            assert (
                out_of_sync_count > initial_out_of_sync
            ), f"Expected out-of-sync count to increase from {initial_out_of_sync}, got {out_of_sync_count}"

            # Verify subscription is NOT active (desired != actual)
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_channel_modes_from_client(listening_client)

            desired_channels = state.desired_subscriptions.get(
                PubSubChannelModes.Exact, set()  # type: ignore[arg-type]
            )
            actual_channels = state.actual_subscriptions.get(
                PubSubChannelModes.Exact, set()  # type: ignore[arg-type]
            )

            assert channel in desired_channels, "Channel should be in desired"
            assert (
                channel not in actual_channels
            ), "Channel should NOT be in actual (ACL blocked)"

            # capture timestamp before subscriptions are in sync
            initial_sync_timestamp = int(
                initial_stats.get("subscription_last_sync_timestamp", "0")
            )

            # Now grant pubsub permissions
            acl_grant_command: List[Union[str, bytes]] = [
                "ACL",
                "SETUSER",
                username,
                "+@pubsub",
                "allchannels",
            ]

            if cluster_mode:
                await cast(GlideClusterClient, admin_client).custom_command(
                    acl_grant_command, route=AllNodes()
                )
            else:
                await admin_client.custom_command(acl_grant_command)
            
            # Wait for reconciliation to succeed - 6 secs to allow for spawned reconciliation to run (spawned every 5 secs)
            await wait_for_subscription_state(
                listening_client,
                expected_channels={channel},
                timeout_ms=6000,
            )

            # Verify sync timestamp was updated (should be greater than initial)
            final_stats = await listening_client.get_statistics()
            final_sync_timestamp = int(
                final_stats.get("subscription_last_sync_timestamp", "0")
            )
            assert final_sync_timestamp > initial_sync_timestamp, (
                f"Sync timestamp should have been updated from {initial_sync_timestamp} "
                f"to {final_sync_timestamp} after successful reconciliation"
            )

        finally:
            # Cleanup ACL user
            if admin_client:
                acl_delete_command: List[Union[str, bytes]] = [
                    "ACL",
                    "DELUSER",
                    username,
                ]

                if cluster_mode:
                    await cast(GlideClusterClient, admin_client).custom_command(
                        acl_delete_command, route=AllNodes()
                    )
                else:
                    await admin_client.custom_command(acl_delete_command)

            await client_cleanup(listening_client)
            await client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_subscription_metrics_repeated_reconciliation_failures(
        self, request, cluster_mode: bool
    ):
        """
        Test that out-of-sync metric increments on repeated reconciliation failures.
        """
        listening_client, admin_client = None, None
        try:
            channel1 = "channel_1"
            channel2 = "channel_2"
            # Must use this special prefix for mock interception
            username = "mock_test_user_1"
            password = "password_1"

            admin_client = await create_client(request, cluster_mode)

            # Create user WITHOUT pubsub permissions (intercepted by mock)
            acl_create_command: List[Union[str, bytes]] = [
                "ACL",
                "SETUSER",
                username,
                "ON",
                f">{password}",
                "~*",
                "resetchannels",
                "+@all",
                "-@pubsub",
            ]

            if cluster_mode:
                await cast(GlideClusterClient, admin_client).custom_command(
                    acl_create_command, route=AllNodes()
                )
            else:
                await admin_client.custom_command(acl_create_command)

            listening_client = await create_client(request, cluster_mode)
            
            if cluster_mode:
                await cast(GlideClusterClient, listening_client).custom_command(
                    ["AUTH", username, password], route=AllNodes()
                )
            else:
                await listening_client.custom_command(["AUTH", username, password])

            initial_stats = await listening_client.get_statistics()
            initial_out_of_sync = int(
                initial_stats.get("subscription_out_of_sync_count", "0")
            )

            # Try to subscribe to multiple channels - all will fail
            await listening_client.subscribe_lazy({channel1})
            await anyio.sleep(0.6)  # Wait for reconciliation attempt

            await listening_client.subscribe_lazy({channel2})
            await anyio.sleep(0.6)  # Wait for reconciliation attempt

            # Check that out-of-sync increased multiple times
            stats = await listening_client.get_statistics()
            out_of_sync_count = int(stats.get("subscription_out_of_sync_count", "0"))

            # Should have at least 2 out-of-sync events (one per failed reconciliation)
            assert (
                out_of_sync_count >= initial_out_of_sync + 2
            ), f"Expected at least 2 out-of-sync events, got {out_of_sync_count - initial_out_of_sync}"

        finally:
            if admin_client:
                acl_delete_command: List[Union[str, bytes]] = [
                    "ACL",
                    "DELUSER",
                    username,
                ]

                if cluster_mode:
                    await cast(GlideClusterClient, admin_client).custom_command(
                        acl_delete_command, route=AllNodes()
                    )
                else:
                    await admin_client.custom_command(acl_delete_command)

            await client_cleanup(listening_client)
            await client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_subscription_sync_timestamp_metric_on_success(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test that sync timestamp updates on successful subscription.

        This test verifies that when subscriptions are successfully applied,
        the last_sync_timestamp metric is updated to reflect the successful
        synchronization and that the timestamp is reasonable (matches actual time).
        """
        listening_client, publishing_client = None, None
        try:
            import time

            channel1 = "channel_1"
            channel2 = "channel_2"
            message = "message_1"

            listening_client = await create_client(request, cluster_mode)
            publishing_client = await create_client(request, cluster_mode)

            initial_stats = await listening_client.get_statistics()
            initial_timestamp = int(
                initial_stats.get("subscription_last_sync_timestamp", "0")
            )

            time_before_first_sub = int(time.time() * 1000)

            await subscribe_by_method(listening_client, subscription_method, {channel1})

            await wait_for_subscription_if_needed(
                listening_client, subscription_method, expected_channels={channel1}
            )

            # Check that timestamp was updated
            stats_after_first = await listening_client.get_statistics()
            timestamp_after_first = int(
                stats_after_first.get("subscription_last_sync_timestamp", "0")
            )

            assert (
                timestamp_after_first > initial_timestamp
            ), f"Timestamp should increase after subscription: {initial_timestamp} -> {timestamp_after_first}"

            # Verify the timestamp is greater than or equal to when we started the subscription
            assert timestamp_after_first >= time_before_first_sub, (
                f"Timestamp {timestamp_after_first} should be >= "
                f"{time_before_first_sub} (time before subscription)"
            )

            # Verify subscription works
            await publishing_client.publish(message, channel1)
            await anyio.sleep(1)
            msg = decode_pubsub_msg(await listening_client.get_pubsub_message())
            assert msg.message == message

            # Wait a bit to ensure timestamps can differ
            await anyio.sleep(1)

            # Capture time before second subscription
            time_before_second_sub = int(time.time() * 1000)

            # Subscribe to second channel
            await subscribe_by_method(listening_client, subscription_method, {channel2})

            await wait_for_subscription_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel1, channel2},
            )

            # Check that timestamp was updated again
            stats_after_second = await listening_client.get_statistics()
            timestamp_after_second = int(
                stats_after_second.get("subscription_last_sync_timestamp", "0")
            )

            assert (
                timestamp_after_second >= timestamp_after_first
            ), f"Timestamp should not decrease: {timestamp_after_first} -> {timestamp_after_second}"

            # Verify the second timestamp is greater than or equal to when we started second subscription
            assert timestamp_after_second >= time_before_second_sub, (
                f"Timestamp {timestamp_after_second} should be >= "
                f"{time_before_second_sub} (time before second subscription)"
            )

            # Verify both subscriptions work
            await publishing_client.publish(message, channel2)
            await anyio.sleep(1)
            msg2 = decode_pubsub_msg(await listening_client.get_pubsub_message())
            assert msg2.message == message

        finally:
            await client_cleanup(listening_client)
            await client_cleanup(publishing_client)
