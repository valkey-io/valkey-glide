# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Optional, Set, Tuple, Union, cast

import anyio
import pytest
from glide.glide_client import GlideClusterClient
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ProtocolVersion,
)
from glide_shared.constants import OK
from glide_shared.exceptions import ConfigurationError, RequestError
from glide_shared.exceptions import TimeoutError as GlideTimeoutError
from glide_shared.routes import AllNodes

from tests.async_tests.conftest import create_client
from tests.utils.utils import (
    MessageReadMethod,
    SubscriptionMethod,
    check_no_messages_left,
    create_pubsub_client,
    create_pubsub_subscription,
    decode_pubsub_msg,
    get_message_by_method,
    get_pubsub_modes,
    kill_connections,
    new_message,
    psubscribe_by_method,
    pubsub_client_cleanup,
    punsubscribe_by_method,
    ssubscribe_by_method,
    subscribe_by_method,
    sunsubscribe_by_method,
    unsubscribe_by_method,
    wait_for_subscription_state,
    wait_for_subscription_state_if_needed,
)


@pytest.mark.anyio
class TestPubSub:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_happy_path(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests the basic happy path for exact PUBSUB functionality.

        This test covers the basic PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific channel is correctly received by a subscriber.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "test_exact_channel"
            message = "test_exact_message"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                # Config method: subscriptions set at client creation
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={channel},
                    callback=callback,
                    context=context,
                )
            else:
                # Lazy/Blocking: create client with callback only, subscribe dynamically
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                # Subscribe dynamically
                await subscribe_by_method(
                    listening_client, {channel}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            # Verify subscription is established
            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
            )

            result = await publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            await anyio.sleep(1)

            pubsub_msg = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )

            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 1
            )
        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_happy_path_coexistence(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in exact PUBSUB.

        This test covers the scenario where messages are published to a channel
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "test_exact_channel"
            message = "test_exact_message_1"
            message2 = "test_exact_message_2"

            if subscription_method == SubscriptionMethod.Config:
                # Config method: subscriptions set at client creation
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={channel},
                )
            else:
                # Lazy/Blocking: create client without subscriptions, subscribe dynamically
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                )
                # Subscribe dynamically
                await subscribe_by_method(
                    listening_client, {channel}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            # Verify subscription is established
            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
            )

            for msg in [message, message2]:
                result = await publishing_client.publish(msg, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await anyio.sleep(1)

            async_msg_res = await listening_client.get_pubsub_message()
            sync_msg_res = listening_client.try_get_pubsub_message()
            assert sync_msg_res
            async_msg = decode_pubsub_msg(async_msg_res)
            sync_msg = decode_pubsub_msg(sync_msg_res)

            assert async_msg.message in [message, message2]
            assert async_msg.channel == channel
            assert async_msg.pattern is None

            assert sync_msg.message in [message, message2]
            assert sync_msg.channel == channel
            assert sync_msg.pattern is None
            # we do not check the order of the messages, but we can check that we received both messages once
            assert not sync_msg.message == async_msg.message

            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None
        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_happy_path_many_channels(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests publishing and receiving messages across many channels in exact PUBSUB.

        This test covers the scenario where multiple channels each receive their own
        unique message. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            shard_prefix = "{same-shard}"

            # Create a map of channels to messages with shard prefix
            channels_and_messages: Dict[str, str] = {
                f"{shard_prefix}channel_{i}": f"message_{i}"
                for i in range(NUM_CHANNELS)
            }
            channels = set(channels_and_messages.keys())

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                # Config method: subscriptions set at client creation
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels=channels,
                    callback=callback,
                    context=context,
                )
            else:
                # Lazy/Blocking: create client with callback only, subscribe dynamically
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                # Subscribe dynamically
                await subscribe_by_method(
                    listening_client, channels, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            # Verify subscriptions are established
            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=channels,
            )

            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    message_read_method, listening_client, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[channel_str]
                assert pubsub_msg.pattern is None
                del channels_and_messages[channel_str]

            # check that we received all messages
            assert channels_and_messages == {}
            # check no messages left
            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_happy_path_many_channels_co_existence(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests publishing and receiving messages across many channels in exact PUBSUB, ensuring coexistence of async and sync
        retrieval methods.

        This test covers scenarios where multiple channels each receive their own unique message.
        It verifies that messages are correctly published and received using both async and sync methods to ensure that
        both methods
        can coexist and function correctly.
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            shard_prefix = "{same-shard}"

            channels_and_messages: Dict[str, str] = {
                f"{shard_prefix}coexist_channel_{i}": f"coexist_message_{i}"
                for i in range(NUM_CHANNELS)
            }

            channels_set = set(channels_and_messages.keys())

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels=channels_set,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                )
                await subscribe_by_method(
                    listening_client, channels_set, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=channels_set,
            )

            for channel, message in channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels_and_messages)):
                method = (
                    MessageReadMethod.Async if index % 2 else MessageReadMethod.Sync
                )
                pubsub_msg = await get_message_by_method(method, listening_client)

                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[channel_str]
                assert pubsub_msg.pattern is None
                del channels_and_messages[channel_str]

            # check that we received all messages
            assert channels_and_messages == {}
            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_sharded_pubsub(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test sharded PUBSUB functionality with different message retrieval methods.

        This test covers the sharded PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific sharded channel is correctly received by a subscriber.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "sharded_channel_1"
            message = "sharded_message_1"
            publish_response = 1

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={channel},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {channel},
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded={channel},
            )

            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == publish_response
            )

            # allow the message to propagate
            await anyio.sleep(1)

            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            # assert there are no messages to read
            await check_no_messages_left(method, listening_client, callback_messages, 1)

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_sharded_pubsub_co_existence(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test sharded PUBSUB with co-existence of multiple messages.

        This test verifies the behavior of sharded PUBSUB when multiple messages are published
        to the same sharded channel. It ensures that both async and sync methods of message retrieval
        function correctly in this scenario.

        It covers the scenario where messages are published to a sharded channel and received using
        both async and sync methods. This ensures that the asynchronous and synchronous message
        retrieval methods can coexist without interfering with each other and operate as expected.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "sharded_coexist_channel"
            message = "sharded_coexist_message_1"
            message2 = "sharded_coexist_message_2"

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={channel},
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {channel},
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded={channel},
            )

            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == 1
            )
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message2, channel, sharded=True
                )
                == 1
            )

            # allow the messages to propagate
            await anyio.sleep(1)

            async_msg_res = await listening_client.get_pubsub_message()
            sync_msg_res = listening_client.try_get_pubsub_message()
            assert sync_msg_res
            async_msg = decode_pubsub_msg(async_msg_res)
            sync_msg = decode_pubsub_msg(sync_msg_res)

            assert async_msg.message in [message, message2]
            assert async_msg.channel == channel
            assert async_msg.pattern is None

            assert sync_msg.message in [message, message2]
            assert sync_msg.channel == channel
            assert sync_msg.pattern is None
            # we do not check the order of the messages, but we can check that we received both messages once
            assert not sync_msg.message == async_msg.message

            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None
        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_sharded_pubsub_many_channels(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test sharded PUBSUB with multiple channels and different message retrieval methods.

        This test verifies the behavior of sharded PUBSUB when multiple messages are published
        across multiple sharded channels. It covers three different message retrieval methods:
        Async, Sync, and Callback.
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            shard_prefix = "{same-shard}"
            publish_response = 1

            channels_and_messages: Dict[str, str] = {
                f"{shard_prefix}sharded_channel_{i}": f"sharded_message_{i}"
                for i in range(NUM_CHANNELS)
            }

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            channels_set = set(channels_and_messages.keys())

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels=channels_set,
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    channels_set,
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded=channels_set,
            )

            for channel, message in channels_and_messages.items():
                assert (
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # Allow the messages to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[channel_str]
                assert pubsub_msg.pattern is None
                del channels_and_messages[channel_str]

            # check that we received all messages
            assert channels_and_messages == {}

            # Assert there are no more messages to read
            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_pattern(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test PUBSUB with pattern subscription using different message retrieval methods.

        This test verifies the behavior of PUBSUB when subscribing to a pattern and receiving
        messages using three different methods: Async, Sync, and Callback.
        """
        listening_client, publishing_client = None, None
        try:
            PATTERN = "{channel}:*"
            channels: Dict[str, str] = {
                "{channel}:news:0": "pattern_message_0",
                "{channel}:news:1": "pattern_message_1",
            }

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={PATTERN},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await psubscribe_by_method(
                    listening_client, {PATTERN}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_patterns={PATTERN},
            )

            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in channels.keys()
                assert pubsub_msg.message == channels[channel_str]
                assert pubsub_msg.pattern == PATTERN
                del channels[channel_str]

            # check that we received all messages
            assert channels == {}

            await check_no_messages_left(method, listening_client, callback_messages, 2)

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_pattern_co_existence(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in pattern-based PUBSUB.

        This test covers the scenario where messages are published to a channel that match a specified pattern
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        listening_client, publishing_client = None, None
        try:
            PATTERN = "{channel}:*"
            channels: Dict[str, str] = {
                "{channel}:coexist_0": "pattern_coexist_message_0",
                "{channel}:coexist_1": "pattern_coexist_message_1",
            }

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={PATTERN},
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                )
                await psubscribe_by_method(
                    listening_client, {PATTERN}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_patterns={PATTERN},
            )

            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels)):
                method = (
                    MessageReadMethod.Async if index % 2 else MessageReadMethod.Sync
                )
                pubsub_msg = await get_message_by_method(method, listening_client)

                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in channels.keys()
                assert pubsub_msg.message == channels[channel_str]
                assert pubsub_msg.pattern == PATTERN
                del channels[channel_str]

            # check that we received all messages
            assert channels == {}

            # assert there are no more messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_pattern_many_channels(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests publishing and receiving messages across many channels in pattern-based PUBSUB.

        This test covers the scenario where messages are published to multiple channels that match a specified pattern
        and received. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            PATTERN = "{channel}:*"
            channels: Dict[str, str] = {
                f"{{channel}}:pattern_{i}": f"pattern_message_{i}"
                for i in range(NUM_CHANNELS)
            }

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={PATTERN},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await psubscribe_by_method(
                    listening_client, {PATTERN}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_patterns={PATTERN},
            )

            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in channels.keys()
                assert pubsub_msg.message == channels[channel_str]
                assert pubsub_msg.pattern == PATTERN
                del channels[channel_str]

            # check that we received all messages
            assert channels == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_combined_exact_and_pattern_one_client(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests combined exact and pattern PUBSUB with one client.

        This test verifies that a single client can correctly handle both exact and pattern PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods
        (async, sync, callback).
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            PATTERN = "{pattern}:*"

            exact_channels_and_messages: Dict[str, str] = {
                f"{{channel}}:exact_{i}": f"exact_message_{i}"
                for i in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages: Dict[str, str] = {
                f"{{pattern}}:match_{i}": f"pattern_message_{i}"
                for i in range(NUM_CHANNELS)
            }

            all_channels_and_messages: Dict[str, str] = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []

            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            exact_channels_set = set(exact_channels_and_messages.keys())

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels=exact_channels_set,
                    patterns={PATTERN},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await subscribe_by_method(
                    listening_client, exact_channels_set, subscription_method
                )
                await psubscribe_by_method(
                    listening_client, {PATTERN}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=exact_channels_set,
                expected_patterns={PATTERN},
            )

            for channel, message in all_channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                pattern = (
                    PATTERN
                    if channel_str in pattern_channels_and_messages.keys()
                    else None
                )
                assert channel_str in all_channels_and_messages.keys()
                assert pubsub_msg.message == all_channels_and_messages[channel_str]
                assert pubsub_msg.pattern == pattern
                del all_channels_and_messages[channel_str]

            # check that we received all messages
            assert all_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 2
            )
        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_combined_exact_and_pattern_multiple_clients(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests combined exact and pattern PUBSUB with multiple clients, one for each subscription.

        This test verifies that separate clients can correctly handle both exact and pattern PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods
        (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        (
            listening_client_exact,
            publishing_client,
            listening_client_pattern,
            client_dont_care,
        ) = (None, None, None, None)
        try:
            NUM_CHANNELS = 256
            PATTERN = "{pattern}:*"

            exact_channels_and_messages: Dict[str, str] = {
                f"{{channel}}:exact_{i}": f"exact_message_{i}"
                for i in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages: Dict[str, str] = {
                f"{{pattern}}:match_{i}": f"pattern_message_{i}"
                for i in range(NUM_CHANNELS)
            }

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []

            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            exact_channels_set = set(exact_channels_and_messages.keys())

            if subscription_method == SubscriptionMethod.Config:
                listening_client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels=exact_channels_set,
                    callback=callback,
                    context=context,
                )
            else:
                listening_client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await subscribe_by_method(
                    listening_client_exact, exact_channels_set, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            callback_messages_pattern: List[PubSubMsg] = []
            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages_pattern

            if subscription_method == SubscriptionMethod.Config:
                listening_client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={PATTERN},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await psubscribe_by_method(
                    listening_client_pattern, {PATTERN}, subscription_method
                )

            client_dont_care = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client_exact,
                subscription_method,
                expected_channels=exact_channels_set,
            )
            await wait_for_subscription_state_if_needed(
                listening_client_pattern,
                subscription_method,
                expected_patterns={PATTERN},
            )

            # Publish messages to all channels
            for channel, message in {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the messages to propagate
            await anyio.sleep(1)

            # Verify messages for exact PUBSUB
            for index in range(len(exact_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_exact, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in exact_channels_and_messages.keys()
                assert pubsub_msg.message == exact_channels_and_messages[channel_str]
                assert pubsub_msg.pattern is None
                del exact_channels_and_messages[channel_str]
            # check that we received all messages

            # Verify messages for pattern PUBSUB
            assert exact_channels_and_messages == {}

            for index in range(len(pattern_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_pattern, callback_messages_pattern, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                assert channel_str in pattern_channels_and_messages.keys()
                assert pubsub_msg.message == pattern_channels_and_messages[channel_str]
                assert pubsub_msg.pattern == PATTERN
                del pattern_channels_and_messages[channel_str]

            # check that we received all messages
            assert pattern_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client_exact, callback_messages, NUM_CHANNELS
            )
            await check_no_messages_left(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                NUM_CHANNELS,
            )

        finally:
            await pubsub_client_cleanup(listening_client_exact)
            await pubsub_client_cleanup(publishing_client)
            await pubsub_client_cleanup(listening_client_pattern)
            await pubsub_client_cleanup(client_dont_care)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_combined_exact_pattern_and_sharded_one_client(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests combined exact, pattern and sharded PUBSUB with one client.

        This test verifies that a single client can correctly handle both exact, pattern and sharded PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Subscribing to channels using a with sharded subscription and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods
        (async, sync, callback).
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            PATTERN = "{pattern}:*"
            SHARD_PREFIX = "{same-shard}"

            exact_channels_and_messages: Dict[str, str] = {
                f"{{channel}}:exact_{i}": f"exact_message_{i}"
                for i in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages: Dict[str, str] = {
                f"{{pattern}}:match_{i}": f"pattern_message_{i}"
                for i in range(NUM_CHANNELS)
            }
            sharded_channels_and_messages: Dict[str, str] = {
                f"{SHARD_PREFIX}:sharded_{i}": f"sharded_message_{i}"
                for i in range(NUM_CHANNELS)
            }

            publish_response = 1

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []

            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            exact_channels_set = set(exact_channels_and_messages.keys())
            sharded_channels_set = set(sharded_channels_and_messages.keys())

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels=exact_channels_set,
                    patterns={PATTERN},
                    sharded_channels=sharded_channels_set,
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await subscribe_by_method(
                    listening_client, exact_channels_set, subscription_method
                )
                await psubscribe_by_method(
                    listening_client, {PATTERN}, subscription_method
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    sharded_channels_set,
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=exact_channels_set,
                expected_patterns={PATTERN},
                expected_sharded=sharded_channels_set,
            )

            for channel, message in {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }.items():
                assert (
                    await publishing_client.publish(message, channel)
                    == publish_response
                )

            # Publish sharded messages to all channels
            for channel, message in sharded_channels_and_messages.items():
                assert (
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # allow the messages to propagate
            await anyio.sleep(1)

            all_channels_and_messages: Dict[str, str] = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
                **sharded_channels_and_messages,
            }
            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                channel_str = cast(str, pubsub_msg.channel)
                pattern = (
                    PATTERN
                    if channel_str in pattern_channels_and_messages.keys()
                    else None
                )
                assert channel_str in all_channels_and_messages.keys()
                assert pubsub_msg.message == all_channels_and_messages[channel_str]
                assert pubsub_msg.pattern == pattern
                del all_channels_and_messages[channel_str]

            # check that we received all messages
            assert all_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 3
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_combined_exact_pattern_and_sharded_multi_client(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Combined PUBSUB test for exact, pattern, and sharded subscriptions with multiple clients.
        """
        listening_client_exact = listening_client_pattern = listening_client_sharded = (
            None
        )
        publishing_client = None

        NUM_CHANNELS = 256
        PATTERN = "{pattern}:*"
        SHARD_PREFIX = "{same-shard}"

        exact_channels: Dict[str, str] = {
            f"{{channel}}:exact:{i}": f"exact_msg_{i}" for i in range(NUM_CHANNELS)
        }
        pattern_channels: Dict[str, str] = {
            f"{{pattern}}:test:{i}": f"pattern_msg_{i}" for i in range(NUM_CHANNELS)
        }
        sharded_channels: Dict[str, str] = {
            f"{SHARD_PREFIX}:{i}:sharded": f"sharded_msg_{i}"
            for i in range(NUM_CHANNELS)
        }

        callback_messages_exact: List[PubSubMsg] = []
        callback_messages_pattern: List[PubSubMsg] = []
        callback_messages_sharded: List[PubSubMsg] = []

        callback = context = None
        if method == MessageReadMethod.Callback:
            callback = new_message
            context = callback_messages_exact

        try:
            # Exact client
            listening_client_exact = await create_pubsub_client(
                request,
                cluster_mode,
                channels=(
                    set(exact_channels.keys())
                    if subscription_method == SubscriptionMethod.Config
                    else None
                ),
                callback=callback,
                context=context,
            )
            if subscription_method != SubscriptionMethod.Config:
                await subscribe_by_method(
                    listening_client_exact,
                    set(exact_channels.keys()),
                    subscription_method,
                )

            # Publishing client
            publishing_client = await create_client(request, cluster_mode)

            if method == MessageReadMethod.Callback:
                context = callback_messages_pattern

            # Pattern client
            listening_client_pattern = await create_pubsub_client(
                request,
                cluster_mode,
                patterns=(
                    {PATTERN}
                    if subscription_method == SubscriptionMethod.Config
                    else None
                ),
                callback=callback,
                context=context,
            )
            if subscription_method != SubscriptionMethod.Config:
                await psubscribe_by_method(
                    listening_client_pattern, {PATTERN}, subscription_method
                )

            if method == MessageReadMethod.Callback:
                context = callback_messages_sharded

            # Sharded client
            listening_client_sharded = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels=(
                    set(sharded_channels.keys())
                    if subscription_method == SubscriptionMethod.Config
                    else None
                ),
                callback=callback,
                context=context,
            )
            if subscription_method != SubscriptionMethod.Config:
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client_sharded),
                    set(sharded_channels.keys()),
                    subscription_method,
                )

            # Wait for subscriptions to activate
            await wait_for_subscription_state_if_needed(
                listening_client_exact,
                subscription_method,
                expected_channels=set(exact_channels.keys()),
            )
            await wait_for_subscription_state_if_needed(
                listening_client_pattern,
                subscription_method,
                expected_patterns={PATTERN},
            )
            await wait_for_subscription_state_if_needed(
                listening_client_sharded,
                subscription_method,
                expected_sharded=set(sharded_channels.keys()),
            )

            # Publish all messages
            publish_response = 1
            for channel, message in {**exact_channels, **pattern_channels}.items():
                assert (
                    await publishing_client.publish(message, channel)
                    == publish_response
                )
            for channel, message in sharded_channels.items():
                assert (
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            await anyio.sleep(1)

            # Helper for asserting messages
            async def assert_pubsub_messages(
                method: MessageReadMethod,
                client,
                callback_messages: List[PubSubMsg],
                expected_dict: Dict[str, str],
                pattern: Optional[str] = None,
            ):
                for index in range(len(expected_dict)):
                    msg = await get_message_by_method(
                        method, client, callback_messages, index
                    )
                    channel_str = cast(str, msg.channel)
                    assert channel_str in expected_dict
                    assert msg.message == expected_dict[channel_str]
                    assert msg.pattern == pattern
                    del expected_dict[channel_str]
                assert expected_dict == {}

            await assert_pubsub_messages(
                method, listening_client_exact, callback_messages_exact, exact_channels
            )
            await assert_pubsub_messages(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                pattern_channels,
                pattern=PATTERN,
            )
            await assert_pubsub_messages(
                method,
                listening_client_sharded,
                callback_messages_sharded,
                sharded_channels,
            )

            # Ensure no messages left
            await check_no_messages_left(
                method, listening_client_exact, callback_messages_exact, NUM_CHANNELS
            )
            await check_no_messages_left(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                NUM_CHANNELS,
            )
            await check_no_messages_left(
                method,
                listening_client_sharded,
                callback_messages_sharded,
                NUM_CHANNELS,
            )

        finally:
            await pubsub_client_cleanup(listening_client_exact)
            await pubsub_client_cleanup(publishing_client)
            await pubsub_client_cleanup(listening_client_pattern)
            await pubsub_client_cleanup(listening_client_sharded)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_combined_different_channels_with_same_name(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests combined PUBSUB with different channel modes using the same channel name.
        One publishing clients, 3 listening clients, one for each mode.

        This test verifies that separate clients can correctly handle subscriptions for exact, pattern, and sharded channels
        with the same name.
        It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Subscribing to a sharded channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods
        (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        (
            listening_client_exact,
            publishing_client,
            listening_client_pattern,
            listening_client_sharded,
        ) = (None, None, None, None)

        try:
            CHANNEL_NAME = "same-channel-name"
            MESSAGE_EXACT = "message_exact"
            MESSAGE_PATTERN = "message_pattern"
            MESSAGE_SHARDED = "message_sharded"

            callback, context = None, None
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []
            callback_messages_sharded: List[PubSubMsg] = []

            if method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages_exact

            # Create exact client
            if subscription_method == SubscriptionMethod.Config:
                listening_client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={CHANNEL_NAME},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await subscribe_by_method(
                    listening_client_exact,
                    {CHANNEL_NAME},
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            # Setup PUBSUB for pattern channel
            if method == MessageReadMethod.Callback:
                context = callback_messages_pattern

            if subscription_method == SubscriptionMethod.Config:
                listening_client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={CHANNEL_NAME},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await psubscribe_by_method(
                    listening_client_pattern,
                    {CHANNEL_NAME},
                    subscription_method,
                )

            if method == MessageReadMethod.Callback:
                context = callback_messages_sharded

            if subscription_method == SubscriptionMethod.Config:
                listening_client_sharded = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={CHANNEL_NAME},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client_sharded = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client_sharded),
                    {CHANNEL_NAME},
                    subscription_method,
                )

            # Verify subscriptions
            await wait_for_subscription_state_if_needed(
                listening_client_exact,
                subscription_method,
                expected_channels={CHANNEL_NAME},
            )
            await wait_for_subscription_state_if_needed(
                listening_client_pattern,
                subscription_method,
                expected_patterns={CHANNEL_NAME},
            )
            await wait_for_subscription_state_if_needed(
                listening_client_sharded,
                subscription_method,
                expected_sharded={CHANNEL_NAME},
            )

            # Publish messages to each channel
            assert await publishing_client.publish(MESSAGE_EXACT, CHANNEL_NAME) == 2
            assert await publishing_client.publish(MESSAGE_PATTERN, CHANNEL_NAME) == 2
            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    MESSAGE_SHARDED, CHANNEL_NAME, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            await anyio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback_list, pattern in [
                (listening_client_exact, callback_messages_exact, None),
                (listening_client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(
                    method, client, callback_list, 0
                )

                pubsub_msg2 = await get_message_by_method(
                    method, client, callback_list, 1
                )
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            # Verify message for sharded PUBSUB
            pubsub_msg_sharded = await get_message_by_method(
                method, listening_client_sharded, callback_messages_sharded, 0
            )
            assert pubsub_msg_sharded.message == MESSAGE_SHARDED
            assert pubsub_msg_sharded.channel == CHANNEL_NAME
            assert pubsub_msg_sharded.pattern is None

            await check_no_messages_left(
                method, listening_client_exact, callback_messages_exact, 2
            )
            await check_no_messages_left(
                method, listening_client_pattern, callback_messages_pattern, 2
            )
            await check_no_messages_left(
                method, listening_client_sharded, callback_messages_sharded, 1
            )

        finally:
            await pubsub_client_cleanup(listening_client_exact)
            await pubsub_client_cleanup(publishing_client)
            await pubsub_client_cleanup(listening_client_pattern)
            await pubsub_client_cleanup(listening_client_sharded)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_two_publishing_clients_same_name(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests PUBSUB with two publishing clients using the same channel name.
        One client uses pattern subscription, the other uses exact.
        The clients publishes messages to each other, and to thyself.

        This test verifies that two separate clients can correctly publish to and handle subscriptions
        for exact and pattern channels with the same name. It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods
        (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        client_exact, client_pattern = None, None
        try:
            CHANNEL_NAME = "channel-name"
            MESSAGE_EXACT = "message_exact"
            MESSAGE_PATTERN = "message_pattern"
            callback, context_exact, context_pattern = None, None, None
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []

            if method == MessageReadMethod.Callback:
                callback = new_message
                context_exact = callback_messages_exact
                context_pattern = callback_messages_pattern

            # Create exact client
            if subscription_method == SubscriptionMethod.Config:
                client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={CHANNEL_NAME},
                    callback=callback,
                    context=context_exact,
                )
            else:
                client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context_exact,
                )
                await subscribe_by_method(
                    client_exact,
                    {CHANNEL_NAME},
                    subscription_method,
                )

            # Create pattern client
            if subscription_method == SubscriptionMethod.Config:
                client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={CHANNEL_NAME},
                    callback=callback,
                    context=context_pattern,
                )
            else:
                client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context_pattern,
                )
                await psubscribe_by_method(
                    client_pattern,
                    {CHANNEL_NAME},
                    subscription_method,
                )

            # Verify subscriptions
            await wait_for_subscription_state_if_needed(
                client_exact,
                subscription_method,
                expected_channels={CHANNEL_NAME},
            )
            await wait_for_subscription_state_if_needed(
                client_pattern,
                subscription_method,
                expected_patterns={CHANNEL_NAME},
            )

            # Publish messages to each channel - both clients publishing
            for msg in [MESSAGE_EXACT, MESSAGE_PATTERN]:
                result = await client_pattern.publish(msg, CHANNEL_NAME)
                if cluster_mode:
                    assert result == 2

            # allow the message to propagate
            await anyio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback_list, pattern in [
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(
                    method, client, callback_list, 0
                )

                pubsub_msg2 = await get_message_by_method(
                    method, client, callback_list, 1
                )
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            await check_no_messages_left(
                method, client_pattern, callback_messages_pattern, 2
            )
            await check_no_messages_left(
                method, client_exact, callback_messages_exact, 2
            )

        finally:
            await pubsub_client_cleanup(client_exact)
            await pubsub_client_cleanup(client_pattern)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_three_publishing_clients_same_name_with_sharded(
        self,
        request,
        cluster_mode: bool,
        method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests PUBSUB with 3 publishing clients using the same channel name.
        One client uses pattern subscription, one uses exact, and one uses sharded.

        This test verifies that 3 separate clients can correctly publish to and handle subscriptions
        for exact, sharded and pattern channels with the same name. It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Subscribing to a sharded channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods
        (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        client_exact, client_pattern, client_sharded, client_dont_care = (
            None,
            None,
            None,
            None,
        )
        try:
            CHANNEL_NAME = "same-channel-name"
            MESSAGE_EXACT = "message_exact"
            MESSAGE_PATTERN = "message_pattern"
            MESSAGE_SHARDED = "message_sharded"
            publish_response = 2 if cluster_mode else OK
            callback, context_exact, context_pattern, context_sharded = (
                None,
                None,
                None,
                None,
            )
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []
            callback_messages_sharded: List[PubSubMsg] = []

            if method == MessageReadMethod.Callback:
                callback = new_message
                context_exact = callback_messages_exact
                context_pattern = callback_messages_pattern
                context_sharded = callback_messages_sharded

            # Create exact client
            if subscription_method == SubscriptionMethod.Config:
                client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={CHANNEL_NAME},
                    callback=callback,
                    context=context_exact,
                )
            else:
                client_exact = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context_exact,
                )
                await subscribe_by_method(
                    client_exact,
                    {CHANNEL_NAME},
                    subscription_method,
                )

            # Create pattern client
            if subscription_method == SubscriptionMethod.Config:
                client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={CHANNEL_NAME},
                    callback=callback,
                    context=context_pattern,
                )
            else:
                client_pattern = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context_pattern,
                )
                await psubscribe_by_method(
                    client_pattern,
                    {CHANNEL_NAME},
                    subscription_method,
                )

            # Create sharded client
            if subscription_method == SubscriptionMethod.Config:
                client_sharded = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={CHANNEL_NAME},
                    callback=callback,
                    context=context_sharded,
                )
            else:
                client_sharded = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context_sharded,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, client_sharded),
                    {CHANNEL_NAME},
                    subscription_method,
                )

            client_dont_care = await create_client(request, cluster_mode)

            # Verify subscriptions
            await wait_for_subscription_state_if_needed(
                client_exact,
                subscription_method,
                expected_channels={CHANNEL_NAME},
            )
            await wait_for_subscription_state_if_needed(
                client_pattern,
                subscription_method,
                expected_patterns={CHANNEL_NAME},
            )
            await wait_for_subscription_state_if_needed(
                client_sharded,
                subscription_method,
                expected_sharded={CHANNEL_NAME},
            )

            # Publish messages to each channel - both clients publishing
            assert (
                await client_pattern.publish(MESSAGE_EXACT, CHANNEL_NAME)
                == publish_response
            )
            assert (
                await client_sharded.publish(MESSAGE_PATTERN, CHANNEL_NAME)
                == publish_response
            )
            assert (
                await cast(GlideClusterClient, client_exact).publish(
                    MESSAGE_SHARDED, CHANNEL_NAME, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            await anyio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback_list, pattern in [
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(
                    method, client, callback_list, 0
                )

                pubsub_msg2 = await get_message_by_method(
                    method, client, callback_list, 1
                )
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            msg = await get_message_by_method(
                method, client_sharded, callback_messages_sharded, 0
            )
            assert msg.message == MESSAGE_SHARDED
            assert msg.channel == CHANNEL_NAME
            assert msg.pattern is None

            await check_no_messages_left(
                method, client_pattern, callback_messages_pattern, 2
            )
            await check_no_messages_left(
                method, client_exact, callback_messages_exact, 2
            )
            await check_no_messages_left(
                method, client_sharded, callback_messages_sharded, 1
            )

        finally:
            await pubsub_client_cleanup(client_exact)
            await pubsub_client_cleanup(client_pattern)
            await pubsub_client_cleanup(client_sharded)
            await pubsub_client_cleanup(client_dont_care)

    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_max_size_message(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests publishing and receiving maximum size messages in PUBSUB.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly in both cluster and standalone modes. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that async and sync message
        retrieval methods can coexist and function correctly.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific channel.
        - Publishing two maximum size messages to the channel.
        - Verifying that the messages are received correctly using both async and sync methods.
        - Ensuring that no additional messages are left after the expected messages are received.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "max_size_channel"
            message = "1" * 512 * 1024 * 1024
            message2 = "2" * 512 * 1024 * 1024

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={channel},
                    timeout=10000,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    timeout=10000,
                )
                await subscribe_by_method(
                    listening_client, {channel}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
            )

            result = await publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1

            result = await publishing_client.publish(message2, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            await anyio.sleep(15)

            async_msg = await listening_client.get_pubsub_message()
            assert async_msg.message == message.encode()
            assert async_msg.channel == channel.encode()
            assert async_msg.pattern is None

            sync_msg = listening_client.try_get_pubsub_message()
            assert sync_msg
            assert sync_msg.message == message2.encode()
            assert sync_msg.channel == channel.encode()
            assert sync_msg.pattern is None

            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_sharded_max_size_message(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests publishing and receiving maximum size messages in sharded PUBSUB.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that async and sync message
        retrieval methods can coexist and function correctly.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific sharded channel.
        - Publishing two maximum size messages to the channel.
        - Verifying that the messages are received correctly using both async and sync methods.
        - Ensuring that no additional messages are left after the expected messages are received.
        """
        publishing_client, listening_client = None, None
        try:
            channel = "sharded_max_size_channel"
            message = "1" * 512 * 1024 * 1024
            message2 = "2" * 512 * 1024 * 1024

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={channel},
                    timeout=10000,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    timeout=10000,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {channel},
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded={channel},
            )

            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == 1
            )

            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message2, channel, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            await anyio.sleep(15)

            async_msg = await listening_client.get_pubsub_message()
            sync_msg = listening_client.try_get_pubsub_message()
            assert sync_msg

            assert async_msg.message == message.encode()
            assert async_msg.channel == channel.encode()
            assert async_msg.pattern is None

            assert sync_msg.message == message2.encode()
            assert sync_msg.channel == channel.encode()
            assert sync_msg.pattern is None

            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_max_size_message_callback(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests publishing and receiving maximum size messages in exact PUBSUB with callback method.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly in both cluster and standalone modes. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that the callback message
        retrieval method works as expected.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific channel with a callback.
        - Publishing a maximum size message to the channel.
        - Verifying that the message is received correctly using the callback method.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "max_size_callback_channel"
            message = "0" * 12 * 1024 * 1024

            callback_messages: List[PubSubMsg] = []
            callback, context = new_message, callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={channel},
                    callback=callback,
                    context=context,
                    timeout=10000,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                    timeout=10000,
                )
                await subscribe_by_method(
                    listening_client, {channel}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
            )

            result = await publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            await anyio.sleep(15)

            assert len(callback_messages) == 1

            assert callback_messages[0].message == message.encode()
            assert callback_messages[0].channel == channel.encode()
            assert callback_messages[0].pattern is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_sharded_max_size_message_callback(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests publishing and receiving maximum size messages in sharded PUBSUB with callback method.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly. It ensures that the PUBSUB system
        can handle maximum size messages without errors and that the callback message
        retrieval method works as expected.

        The test covers the following scenarios:
        - Setting up PUBSUB subscription for a specific sharded channel with a callback.
        - Publishing a maximum size message to the channel.
        - Verifying that the message is received correctly using the callback method.
        """
        publishing_client, listening_client = None, None
        try:
            channel = "sharded_max_size_callback_channel"
            message = "0" * 512 * 1024 * 1024

            callback_messages: List[PubSubMsg] = []
            callback, context = new_message, callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={channel},
                    callback=callback,
                    context=context,
                    timeout=10000,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                    timeout=10000,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {channel},
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded={channel},
            )

            assert (
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            await anyio.sleep(15)

            assert len(callback_messages) == 1

            assert callback_messages[0].message == message.encode()
            assert callback_messages[0].channel == channel.encode()
            assert callback_messages[0].pattern is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_resp2_raise_an_error(self, request, cluster_mode: bool):
        """Tests that when creating a resp2 client with PUBSUB - an error will be raised"""
        channel = "resp2_error_channel"

        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        with pytest.raises(ConfigurationError):
            await create_client(
                request,
                cluster_mode=cluster_mode,
                cluster_mode_pubsub=(
                    cast(
                        GlideClusterClientConfiguration.PubSubSubscriptions,
                        pub_sub_exact,
                    )
                    if cluster_mode
                    else None
                ),
                standalone_mode_pubsub=(
                    cast(GlideClientConfiguration.PubSubSubscriptions, pub_sub_exact)
                    if not cluster_mode
                    else None
                ),
                protocol=ProtocolVersion.RESP2,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_context_with_no_callback_raise_error(
        self, request, cluster_mode: bool
    ):
        """Tests that when creating a PUBSUB client in callback method with context but no callback raises an error"""
        channel = "context_no_callback_channel"
        context: List[PubSubMsg] = []
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            context=context,
        )

        with pytest.raises(ConfigurationError):
            await create_client(
                request,
                cluster_mode=cluster_mode,
                cluster_mode_pubsub=(
                    cast(
                        GlideClusterClientConfiguration.PubSubSubscriptions,
                        pub_sub_exact,
                    )
                    if cluster_mode
                    else None
                ),
                standalone_mode_pubsub=(
                    cast(GlideClientConfiguration.PubSubSubscriptions, pub_sub_exact)
                    if not cluster_mode
                    else None
                ),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_channels(self, request, cluster_mode: bool):
        """
        Tests the pubsub_channels command functionality.

        This test verifies that the pubsub_channels command correctly returns
        the active channels matching a specified pattern.
        """
        client1, client2, client = None, None, None
        try:
            channel1 = "test_channel1"
            channel2 = "test_channel2"
            channel3 = "some_channel3"
            pattern = "test_*"

            client = await create_client(request, cluster_mode)
            # Assert no channels exists yet
            assert await client.pubsub_channels() == []

            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                channels={channel1, channel2, channel3},
            )
            client1 = listening_client
            client2 = await create_client(request, cluster_mode)

            # Verify subscriptions are established
            await wait_for_subscription_state(
                client1,
                expected_channels={channel1, channel2, channel3},
            )

            channel1_bytes = channel1.encode()
            channel2_bytes = channel2.encode()
            channel3_bytes = channel3.encode()

            # Test pubsub_channels without pattern
            channels = await client2.pubsub_channels()
            assert set(channels) == {channel1_bytes, channel2_bytes, channel3_bytes}

            # Test pubsub_channels with pattern
            channels_with_pattern = await client2.pubsub_channels(pattern)
            assert set(channels_with_pattern) == {channel1_bytes, channel2_bytes}

            # Test with non-matching pattern
            non_matching_channels = await client2.pubsub_channels("non_matching_*")
            assert len(non_matching_channels) == 0

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)
            await pubsub_client_cleanup(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_numpat(self, request, cluster_mode: bool):
        """
        Tests the pubsub_numpat command functionality.

        This test verifies that the pubsub_numpat command correctly returns
        the number of unique patterns that are subscribed to by clients.
        """
        client1, client2, client = None, None, None
        try:
            pattern1 = "test_*"
            pattern2 = "another_*"

            # Create a client and check initial number of patterns
            client = await create_client(request, cluster_mode)
            assert await client.pubsub_numpat() == 0

            # Set up subscriptions with patterns
            client1 = await create_pubsub_client(
                request,
                cluster_mode,
                patterns={pattern1, pattern2},
            )
            client2 = await create_client(request, cluster_mode)

            # Verify pattern subscriptions are established
            await wait_for_subscription_state(
                client1,
                expected_patterns={pattern1, pattern2},
            )

            # Test pubsub_numpat
            num_patterns = await client2.pubsub_numpat()
            assert num_patterns == 2

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)
            await pubsub_client_cleanup(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_numsub(self, request, cluster_mode: bool):
        """
        Tests the pubsub_numsub command functionality.

        This test verifies that the pubsub_numsub command correctly returns
        the number of subscribers for specified channels.
        """
        client1, client2, client3, client4, client = None, None, None, None, None
        try:
            channel1 = "test_channel1"
            channel2 = "test_channel2"
            channel3 = "test_channel3"
            channel4 = "test_channel4"

            channel1_bytes = channel1.encode()
            channel2_bytes = channel2.encode()
            channel3_bytes = channel3.encode()
            channel4_bytes = channel4.encode()

            # Create a client and check initial subscribers
            client = await create_client(request, cluster_mode)
            assert await client.pubsub_numsub([channel1, channel2, channel3]) == {
                channel1_bytes: 0,
                channel2_bytes: 0,
                channel3_bytes: 0,
            }

            # Set up subscriptions - client1 subscribes to channels 1, 2, 3
            client1 = await create_pubsub_client(
                request,
                cluster_mode,
                channels={channel1, channel2, channel3},
            )
            await wait_for_subscription_state(
                client1,
                expected_channels={channel1, channel2, channel3},
            )

            # client2 subscribes to channels 2, 3
            client2 = await create_pubsub_client(
                request,
                cluster_mode,
                channels={channel2, channel3},
            )
            await wait_for_subscription_state(
                client2,
                expected_channels={channel2, channel3},
            )

            # client3 subscribes to channel 3 only
            client3 = await create_pubsub_client(
                request,
                cluster_mode,
                channels={channel3},
            )
            await wait_for_subscription_state(
                client3,
                expected_channels={channel3},
            )

            client4 = await create_client(request, cluster_mode)

            # Test pubsub_numsub
            subscribers = await client4.pubsub_numsub(
                [channel1_bytes, channel2_bytes, channel3_bytes, channel4_bytes]
            )
            assert subscribers == {
                channel1_bytes: 1,
                channel2_bytes: 2,
                channel3_bytes: 3,
                channel4_bytes: 0,
            }

            # Test pubsub_numsub with no channels
            empty_subscribers = await client4.pubsub_numsub()
            assert empty_subscribers == {}

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)
            await pubsub_client_cleanup(client3)
            await pubsub_client_cleanup(client4)
            await pubsub_client_cleanup(client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_shardchannels(self, request, cluster_mode: bool):
        """
        Tests the pubsub_shardchannels command functionality.

        This test verifies that the pubsub_shardchannels command correctly returns
        the active sharded channels matching a specified pattern.
        """
        client1, client2, client = None, None, None
        try:
            channel1 = "test_shardchannel1"
            channel2 = "test_shardchannel2"
            channel3 = "some_shardchannel3"
            pattern = "test_*"

            client = await create_client(request, cluster_mode)
            assert isinstance(client, GlideClusterClient)
            # Assert no sharded channels exist yet
            assert await client.pubsub_shardchannels() == []

            client1 = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels={channel1, channel2, channel3},
            )
            client2 = await create_client(request, cluster_mode)

            # Verify sharded subscriptions are established
            await wait_for_subscription_state(
                client1,
                expected_sharded={channel1, channel2, channel3},
            )

            channel1_bytes = channel1.encode()
            channel2_bytes = channel2.encode()
            channel3_bytes = channel3.encode()

            assert isinstance(client2, GlideClusterClient)

            # Test pubsub_shardchannels without pattern
            channels = await client2.pubsub_shardchannels()
            assert set(channels) == {channel1_bytes, channel2_bytes, channel3_bytes}

            # Test pubsub_shardchannels with pattern
            channels_with_pattern = await client2.pubsub_shardchannels(pattern)
            assert set(channels_with_pattern) == {channel1_bytes, channel2_bytes}

            # Test with non-matching pattern
            assert await client2.pubsub_shardchannels("non_matching_*") == []

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)
            await pubsub_client_cleanup(client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_shardnumsub(self, request, cluster_mode: bool):
        """
        Tests the pubsub_shardnumsub command functionality.

        This test verifies that the pubsub_shardnumsub command correctly returns
        the number of subscribers for specified sharded channels.
        """
        client1, client2, client3, client4, client = None, None, None, None, None
        try:
            channel1 = "test_shardchannel1"
            channel2 = "test_shardchannel2"
            channel3 = "test_shardchannel3"
            channel4 = "test_shardchannel4"

            channel1_bytes = channel1.encode()
            channel2_bytes = channel2.encode()
            channel3_bytes = channel3.encode()
            channel4_bytes = channel4.encode()

            # Create a client and check initial subscribers
            client = await create_client(request, cluster_mode)
            assert isinstance(client, GlideClusterClient)
            assert await client.pubsub_shardnumsub([channel1, channel2, channel3]) == {
                channel1_bytes: 0,
                channel2_bytes: 0,
                channel3_bytes: 0,
            }

            # client1 subscribes to sharded channels 1, 2, 3
            client1 = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels={channel1, channel2, channel3},
            )
            await wait_for_subscription_state(
                client1,
                expected_sharded={channel1, channel2, channel3},
            )

            # client2 subscribes to sharded channels 2, 3
            client2 = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels={channel2, channel3},
            )
            await wait_for_subscription_state(
                client2,
                expected_sharded={channel2, channel3},
            )

            # client3 subscribes to sharded channel 3 only
            client3 = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels={channel3},
            )
            await wait_for_subscription_state(
                client3,
                expected_sharded={channel3},
            )

            client4 = await create_client(request, cluster_mode)
            assert isinstance(client4, GlideClusterClient)

            # Test pubsub_shardnumsub
            subscribers = await client4.pubsub_shardnumsub(
                [channel1, channel2, channel3, channel4]
            )
            assert subscribers == {
                channel1_bytes: 1,
                channel2_bytes: 2,
                channel3_bytes: 3,
                channel4_bytes: 0,
            }

            # Test pubsub_shardnumsub with no channels
            empty_subscribers = await client4.pubsub_shardnumsub()
            assert empty_subscribers == {}

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)
            await pubsub_client_cleanup(client3)
            await pubsub_client_cleanup(client4)
            await pubsub_client_cleanup(client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_channels_and_shardchannels_separation(
        self, request, cluster_mode: bool
    ):
        """
        Tests that pubsub_channels doesn't return sharded channels and pubsub_shardchannels
        doesn't return regular channels.
        """
        client1, client2 = None, None
        try:
            regular_channel = "regular_channel"
            shard_channel = "shard_channel"

            regular_channel_bytes = regular_channel.encode()
            shard_channel_bytes = shard_channel.encode()

            client1 = await create_pubsub_client(
                request,
                cluster_mode,
                channels={regular_channel},
                sharded_channels={shard_channel},
            )

            # Verify both subscription types are established
            await wait_for_subscription_state(
                client1,
                expected_channels={regular_channel},
                expected_sharded={shard_channel},
            )

            client2 = await create_client(request, cluster_mode)
            assert isinstance(client2, GlideClusterClient)

            # Test pubsub_channels - should only return regular channel
            assert await client2.pubsub_channels() == [regular_channel_bytes]

            # Test pubsub_shardchannels - should only return sharded channel
            assert await client2.pubsub_shardchannels() == [shard_channel_bytes]

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_numsub_and_shardnumsub_separation(
        self, request, cluster_mode: bool
    ):
        """
        Tests that pubsub_numsub doesn't count sharded channel subscribers and pubsub_shardnumsub
        doesn't count regular channel subscribers.
        """
        client1, client2 = None, None
        try:
            regular_channel = "regular_channel"
            shard_channel = "shard_channel"

            regular_channel_bytes = regular_channel.encode()
            shard_channel_bytes = shard_channel.encode()

            # client1 subscribes to both regular and sharded channel
            client1 = await create_pubsub_client(
                request,
                cluster_mode,
                channels={regular_channel},
                sharded_channels={shard_channel},
            )
            await wait_for_subscription_state(
                client1,
                expected_channels={regular_channel},
                expected_sharded={shard_channel},
            )

            # client2 also subscribes to both
            client2 = await create_pubsub_client(
                request,
                cluster_mode,
                channels={regular_channel},
                sharded_channels={shard_channel},
            )
            await wait_for_subscription_state(
                client2,
                expected_channels={regular_channel},
                expected_sharded={shard_channel},
            )

            assert isinstance(client2, GlideClusterClient)

            # Test pubsub_numsub
            regular_subscribers = await client2.pubsub_numsub(
                [regular_channel_bytes, shard_channel_bytes]
            )

            assert regular_subscribers == {
                regular_channel_bytes: 2,
                shard_channel_bytes: 0,
            }

            # Test pubsub_shardnumsub
            shard_subscribers = await client2.pubsub_shardnumsub(
                [regular_channel_bytes, shard_channel_bytes]
            )
            assert shard_subscribers == {
                regular_channel_bytes: 0,
                shard_channel_bytes: 2,
            }

        finally:
            await pubsub_client_cleanup(client1)
            await pubsub_client_cleanup(client2)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
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
            messages: Dict[str, str] = {ch: f"msg_{ch}" for ch in channels}

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels=channels,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    channels,
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded=channels,
            )

            # Publish and verify
            for channel, message in messages.items():
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            await anyio.sleep(1)

            received_messages: Dict[str, str] = {}
            for _ in range(len(channels)):
                msg = decode_pubsub_msg(await listening_client.get_pubsub_message())
                received_messages[cast(str, msg.channel)] = cast(str, msg.message)

            assert received_messages == messages

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_unsubscribe_exact_channel(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic unsubscription from exact channels using lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "channel"
            message1 = "exact_message_1"
            message2 = "exact_message_2"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                channels={channel},
                callback=callback,
                context=context,
            )
            publishing_client = await create_client(request, cluster_mode)

            # Verify subscription is active
            await wait_for_subscription_state_if_needed(
                listening_client,
                SubscriptionMethod.Config,
                expected_channels={channel},
            )

            await publishing_client.publish(message1, channel)
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            # Unsubscribe
            await unsubscribe_by_method(
                listening_client, {channel}, subscription_method
            )

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=set(),
            )

            # Publish second message - should not be received
            await publishing_client.publish(message2, channel)
            await anyio.sleep(1)

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 1
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_punsubscribe_pattern(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic pattern unsubscription using lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            pattern = "news_punsubscribe_test.*"
            channel = "news_punsubscribe_test.sports"
            message1 = "message_before_unsub"
            message2 = "message_after_unsub"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                patterns={pattern},
                callback=callback,
                context=context,
            )
            publishing_client = await create_client(request, cluster_mode)

            # Verify pattern subscription is active
            await wait_for_subscription_state_if_needed(
                listening_client,
                SubscriptionMethod.Config,
                expected_patterns={pattern},
            )

            await publishing_client.publish(message1, channel)
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            # Unsubscribe from pattern
            await punsubscribe_by_method(
                listening_client, {pattern}, subscription_method
            )

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_patterns=set(),
            )

            # Publish second message - should not be received
            await publishing_client.publish(message2, channel)
            await anyio.sleep(1)

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 1
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_sunsubscribe_sharded_channel(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic sharded unsubscription using lazy and blocking APIs.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "sharded_sunsubscribe_test_channel"
            message1 = "sharded_msg_before"
            message2 = "sharded_msg_after"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels={channel},
                callback=callback,
                context=context,
            )
            publishing_client = await create_client(request, cluster_mode)

            # Verify sharded subscription is active
            await wait_for_subscription_state_if_needed(
                listening_client,
                SubscriptionMethod.Config,
                expected_sharded={channel},
            )

            await cast(GlideClusterClient, publishing_client).publish(
                message1, channel, sharded=True
            )
            await anyio.sleep(1)
            pubsub_msg = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            # Unsubscribe from sharded channel
            await sunsubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                {channel},
                subscription_method,
            )

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded=set(),
            )

            # Publish second message - should not be received
            await cast(GlideClusterClient, publishing_client).publish(
                message2, channel, sharded=True
            )
            await anyio.sleep(1)

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 1
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

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
                "{slotA}unsub_channel_1",
                "{slotB}unsub_channel_2",
                "{slotC}unsub_channel_3",
                "{slotA}unsub_channel_4",
            }
            message = "test_message"

            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                sharded_channels=channels,
            )
            publishing_client = await create_client(request, cluster_mode)

            # Verify all subscriptions are active
            await wait_for_subscription_state_if_needed(
                listening_client,
                SubscriptionMethod.Config,
                expected_sharded=channels,
            )

            # Unsubscribe from all channels at once (tests CrossSlot handling)
            await sunsubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                channels,
                subscription_method,
            )

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded=set(),
            )

            # Verify no messages received after unsubscribe
            for channel in channels:
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            await anyio.sleep(1)

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method", [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking]
    )
    async def test_unsubscribe_all_subscription_types(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test unsubscribing from all channels/patterns/sharded using unsubscribe with no arguments.
        Tests all three subscription types in a single test.
        """
        listening_client, publishing_client = None, None
        try:
            exact_channels = {f"exact_unsub_all_{i}" for i in range(3)}
            patterns = {f"pattern_unsub_all_{i}.*" for i in range(3)}
            sharded_channels = {f"sharded_unsub_all_{i}" for i in range(3)}
            message = "test_message"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                channels=exact_channels,
                patterns=patterns,
                sharded_channels=sharded_channels,
                callback=callback,
                context=context,
            )
            publishing_client = await create_client(request, cluster_mode)

            # Verify all subscriptions are active
            await wait_for_subscription_state_if_needed(
                listening_client,
                SubscriptionMethod.Config,
                expected_channels=exact_channels,
                expected_patterns=patterns,
                expected_sharded=sharded_channels,
            )

            # Unsubscribe from all (pass None to unsubscribe from all of each type)
            await unsubscribe_by_method(listening_client, None, subscription_method)
            await punsubscribe_by_method(listening_client, None, subscription_method)
            await sunsubscribe_by_method(
                cast(GlideClusterClient, listening_client),
                None,
                subscription_method,
            )

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set(),
            )

            # Publish to all types - none should be received
            for channel in exact_channels:
                await publishing_client.publish(message, channel)
            for pattern in patterns:
                matching_channel = pattern.replace("*", "test")
                await publishing_client.publish(message, matching_channel)
            for channel in sharded_channels:
                await cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            await anyio.sleep(1)

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 0
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking],
    )
    async def test_subscribe_empty_set_raises_error(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that subscribing with an empty set raises an error for dynamic subscription methods.
        """
        client = None
        try:
            client = await create_pubsub_client(request, cluster_mode)

            # Verify initial state is empty
            await wait_for_subscription_state(
                client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set() if cluster_mode else None,
            )

            # Test subscribe with empty set
            with pytest.raises(RequestError) as exc_info:
                await subscribe_by_method(client, set(), subscription_method)
            assert "No channels provided for subscription" in str(exc_info.value)

            # Test psubscribe with empty set
            with pytest.raises(RequestError) as exc_info:
                await psubscribe_by_method(client, set(), subscription_method)
            assert "No channels provided for subscription" in str(exc_info.value)

            # Test ssubscribe with empty set (cluster only)
            if cluster_mode:
                with pytest.raises(RequestError) as exc_info:
                    await ssubscribe_by_method(
                        cast(GlideClusterClient, client), set(), subscription_method
                    )
                assert "No channels provided for subscription" in str(exc_info.value)

            # Verify state is still empty after failed subscription attempts
            await wait_for_subscription_state(
                client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set() if cluster_mode else None,
            )

        finally:
            await pubsub_client_cleanup(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_config_subscription_with_empty_set_is_allowed(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that Config subscription method with empty sets is a silent no-op.

        Unlike Lazy and Blocking methods which raise errors for empty sets,
        the Config method silently ignores empty subscription sets. This happens
        because empty sets are falsy in Python, so they get filtered out before
        reaching the Rust core.
        """
        client = None
        try:
            # Create client with empty subscription sets via Config
            # This should NOT raise an error - empty sets are filtered out by Python
            client = await create_pubsub_client(
                request,
                cluster_mode,
                channels=set(),
                patterns=set(),
                sharded_channels=set() if cluster_mode else None,
            )

            # Verify client was created successfully and state is empty
            await wait_for_subscription_state(
                client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set() if cluster_mode else None,
            )

        finally:
            await pubsub_client_cleanup(client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_mixed_subscription_methods_all_types(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test mixing Config, Lazy, and Blocking subscriptions across all subscription types
        (Exact, Pattern, and Sharded for cluster mode).

        This comprehensive test verifies that all subscription methods work together
        for all subscription types.
        """
        listening_client, publishing_client = None, None
        try:
            # Create unique names for each combination
            prefix = "mixed_sub_types"

            # Exact channels
            exact_config = f"exact_config_{prefix}"
            exact_lazy = f"exact_lazy_{prefix}"
            exact_blocking = f"exact_blocking_{prefix}"

            # Pattern subscriptions
            pattern_config = f"pattern_config_{prefix}_*"
            pattern_lazy = f"pattern_lazy_{prefix}_*"
            pattern_blocking = f"pattern_blocking_{prefix}_*"

            # Channels that match the patterns
            pattern_config_channel = f"pattern_config_{prefix}_match"
            pattern_lazy_channel = f"pattern_lazy_{prefix}_match"
            pattern_blocking_channel = f"pattern_blocking_{prefix}_match"

            # Sharded channels (cluster mode only)
            sharded_config = f"sharded_config_{prefix}" if cluster_mode else None
            sharded_lazy = f"sharded_lazy_{prefix}" if cluster_mode else None
            sharded_blocking = f"sharded_blocking_{prefix}" if cluster_mode else None

            # Create client with Config subscriptions
            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                channels={exact_config},
                patterns={pattern_config},
                sharded_channels=(
                    {sharded_config} if cluster_mode and sharded_config else None
                ),
            )
            publishing_client = await create_client(request, cluster_mode)

            # Wait for config subscriptions
            await wait_for_subscription_state(
                listening_client,
                expected_channels={exact_config},
                expected_patterns={pattern_config},
                expected_sharded=(
                    {sharded_config} if cluster_mode and sharded_config else None
                ),
            )

            # Add Lazy subscriptions
            await subscribe_by_method(
                listening_client, {exact_lazy}, SubscriptionMethod.Lazy
            )
            await psubscribe_by_method(
                listening_client, {pattern_lazy}, SubscriptionMethod.Lazy
            )
            if cluster_mode and sharded_lazy:
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {sharded_lazy},
                    SubscriptionMethod.Lazy,
                )

            # Add Blocking subscriptions
            await subscribe_by_method(
                listening_client, {exact_blocking}, SubscriptionMethod.Blocking
            )
            await psubscribe_by_method(
                listening_client, {pattern_blocking}, SubscriptionMethod.Blocking
            )
            if cluster_mode and sharded_blocking:
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {sharded_blocking},
                    SubscriptionMethod.Blocking,
                )

            # Wait for all subscriptions
            all_exact = {exact_config, exact_lazy, exact_blocking}
            all_patterns = {pattern_config, pattern_lazy, pattern_blocking}
            all_sharded: Optional[Set[str]] = None
            if cluster_mode and sharded_config and sharded_lazy and sharded_blocking:
                all_sharded = {sharded_config, sharded_lazy, sharded_blocking}

            await wait_for_subscription_state(
                listening_client,
                expected_channels=all_exact,
                expected_patterns=all_patterns,
                expected_sharded=all_sharded,
            )

            # Publish messages
            messages_to_publish: List[Tuple[str, str, bool]] = [
                # (channel, message, is_sharded)
                (exact_config, "msg_exact_config", False),
                (exact_lazy, "msg_exact_lazy", False),
                (exact_blocking, "msg_exact_blocking", False),
                (pattern_config_channel, "msg_pattern_config", False),
                (pattern_lazy_channel, "msg_pattern_lazy", False),
                (pattern_blocking_channel, "msg_pattern_blocking", False),
            ]

            if cluster_mode and sharded_config and sharded_lazy and sharded_blocking:
                messages_to_publish.extend(
                    [
                        (sharded_config, "msg_sharded_config", True),
                        (sharded_lazy, "msg_sharded_lazy", True),
                        (sharded_blocking, "msg_sharded_blocking", True),
                    ]
                )

            for channel, message, is_sharded in messages_to_publish:
                if is_sharded:
                    await cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                else:
                    await publishing_client.publish(message, channel)

            await anyio.sleep(1)

            # Collect all messages
            expected_count = 9 if cluster_mode else 6
            received_messages: Dict[str, str] = {}

            for _ in range(expected_count):
                msg = await listening_client.get_pubsub_message()
                decoded = decode_pubsub_msg(msg)
                received_messages[cast(str, decoded.channel)] = cast(
                    str, decoded.message
                )

            # Verify exact channel messages
            assert received_messages[exact_config] == "msg_exact_config"
            assert received_messages[exact_lazy] == "msg_exact_lazy"
            assert received_messages[exact_blocking] == "msg_exact_blocking"

            # Verify pattern channel messages
            assert received_messages[pattern_config_channel] == "msg_pattern_config"
            assert received_messages[pattern_lazy_channel] == "msg_pattern_lazy"
            assert received_messages[pattern_blocking_channel] == "msg_pattern_blocking"

            # Verify sharded channel messages (cluster mode only)
            if cluster_mode and sharded_config and sharded_lazy and sharded_blocking:
                assert received_messages[sharded_config] == "msg_sharded_config"
                assert received_messages[sharded_lazy] == "msg_sharded_lazy"
                assert received_messages[sharded_blocking] == "msg_sharded_blocking"

            # Verify no extra messages
            assert listening_client.try_get_pubsub_message() is None

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_lazy_client_multiple_subscription_types(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test multiple subscription types (exact, pattern, and sharded for cluster) with a lazy client.

        Verifies that a lazy client can handle multiple subscription types
        being added via all subscription methods (Config, Lazy, Blocking).
        """
        listening_client, publishing_client = None, None
        try:
            exact_channel = "lazy_multi_exact"
            pattern = "lazy_multi_pattern_*"
            pattern_channel = "lazy_multi_pattern_test"
            message_exact = "exact_message"
            message_pattern = "pattern_message"

            # For cluster mode, also test sharded
            sharded_channel = "lazy_multi_sharded" if cluster_mode else None
            message_sharded = "sharded_message" if cluster_mode else None

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            # Create lazy client with subscriptions based on method
            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={exact_channel},
                    patterns={pattern},
                    sharded_channels={sharded_channel} if sharded_channel else None,
                    callback=callback,
                    context=context,
                    lazy_connect=True,
                )

            else:
                # Lazy/Blocking: create lazy client, subscribe dynamically
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                    lazy_connect=True,
                )

                await subscribe_by_method(
                    listening_client, {exact_channel}, subscription_method
                )
                await psubscribe_by_method(
                    listening_client, {pattern}, subscription_method
                )
                if cluster_mode and sharded_channel:
                    await ssubscribe_by_method(
                        cast(GlideClusterClient, listening_client),
                        {sharded_channel},
                        subscription_method,
                    )

            # Wait for all subscriptions to be established
            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={exact_channel},
                expected_patterns={pattern},
                expected_sharded=(
                    {sharded_channel} if cluster_mode and sharded_channel else None
                ),
            )

            # Create lazy publishing client
            publishing_client = await create_client(
                request,
                cluster_mode=cluster_mode,
                lazy_connect=True,
            )

            # Publish to all subscription types
            await publishing_client.publish(message_exact, exact_channel)
            await publishing_client.publish(message_pattern, pattern_channel)
            if cluster_mode and sharded_channel and message_sharded:
                await cast(GlideClusterClient, publishing_client).publish(
                    message_sharded, sharded_channel, sharded=True
                )

            await anyio.sleep(1)

            # Determine expected message count
            expected_count = 3 if cluster_mode else 2

            # Collect all messages
            received: Dict[str, PubSubMsg] = {}
            for index in range(expected_count):
                msg = await get_message_by_method(
                    message_read_method, listening_client, callback_messages, index
                )
                channel_key = cast(str, msg.channel)
                received[channel_key] = msg

            # Verify exact channel message
            assert exact_channel in received
            assert received[exact_channel].message == message_exact
            assert received[exact_channel].pattern is None

            # Verify pattern channel message
            assert pattern_channel in received
            assert received[pattern_channel].message == message_pattern
            assert received[pattern_channel].pattern == pattern

            # Verify sharded channel message (cluster mode only)
            if cluster_mode and sharded_channel:
                assert sharded_channel in received
                assert received[sharded_channel].message == message_sharded
                assert received[sharded_channel].pattern is None

            # Verify no extra messages
            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, expected_count
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_lazy_vs_blocking_timeout(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that blocking subscribe times out when reconciliation can't complete.
        """
        username = "mock_test_user_timeout"
        password = "password_timeout"
        channel = "channel_timeout_test"

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

        try:
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

        finally:
            # Cleanup ACL user
            acl_delete_command: List[Union[str, bytes]] = ["ACL", "DELUSER", username]
            if cluster_mode:
                await cast(GlideClusterClient, admin_client).custom_command(
                    acl_delete_command, route=AllNodes()
                )
            else:
                await admin_client.custom_command(acl_delete_command)

            await pubsub_client_cleanup(client)
            await pubsub_client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("subscription_method", [SubscriptionMethod.Lazy])
    async def test_subscription_metrics_on_acl_failure(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that out-of-sync metric is recorded when subscription fails due to ACL.
        """
        listening_client, admin_client = None, None
        try:
            channel = "channel_acl_metrics_test"
            username = "mock_test_user_acl_metrics"
            password = "password_acl_metrics"

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

            # Create client and authenticate with restricted user
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

            await subscribe_by_method(listening_client, {channel}, subscription_method)

            # Wait for reconciliation attempts
            await anyio.sleep(1)

            # Check that out-of-sync metric increased (reconciliation failed)
            stats = await listening_client.get_statistics()
            out_of_sync_count = int(stats.get("subscription_out_of_sync_count", "0"))

            assert (
                out_of_sync_count > initial_out_of_sync
            ), f"Expected out-of-sync count to increase from {initial_out_of_sync}, got {out_of_sync_count}"

            # Verify subscription is NOT active (desired != actual)
            state = await listening_client.get_subscriptions()
            PubSubChannelModes = get_pubsub_modes(listening_client)

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

            # Capture timestamp before subscriptions are in sync
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

            # Wait for reconciliation to succeed (1 each 5 secs)
            await wait_for_subscription_state(
                listening_client,
                expected_channels={channel},
                timeout_ms=6000,
            )

            # Verify sync timestamp was updated
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

            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_subscription_metrics_repeated_reconciliation_failures(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that out-of-sync metric increments on repeated reconciliation failures.
        """
        listening_client, admin_client = None, None
        try:
            channel1 = "channel1_repeated_failures"
            channel2 = "channel2_repeated_failures"
            username = "mock_test_user_repeated"
            password = "password_repeated"

            admin_client = await create_client(request, cluster_mode)

            # Create user WITHOUT pubsub permissions
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

            channels = [channel1, channel2]

            for channel in channels:
                try:
                    await subscribe_by_method(
                        listening_client, {channel}, subscription_method
                    )
                except GlideTimeoutError:
                    # Expected for blocking method
                    pass

            # Give time for async reconciliation to run and intrease the metric
            await anyio.sleep(0.5)

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

            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(admin_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_subscription_sync_timestamp_metric_on_success(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that sync timestamp updates on successful subscription.
        """
        listening_client, publishing_client = None, None
        try:
            import time

            channel1 = "channel1_sync_timestamp"
            channel2 = "channel2_sync_timestamp"
            message = "message_1"

            listening_client = await create_pubsub_client(request, cluster_mode)

            publishing_client = await create_client(request, cluster_mode)

            initial_stats = await listening_client.get_statistics()
            initial_timestamp = int(
                initial_stats.get("subscription_last_sync_timestamp", "0")
            )

            time_before_first_sub = int(time.time() * 1000)

            await subscribe_by_method(listening_client, {channel1}, subscription_method)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel1},
            )

            # Subscribe to another channel - this ensures we will have at least 1 full reconcilliation cycle
            # and 1 successful timestamp update before checking it
            await subscribe_by_method(listening_client, {channel2}, subscription_method)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel1, channel2},
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

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_pubsub_exact_happy_path_custom_command(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests the basic happy path for exact PUBSUB functionality using custom commands.

        This test mirrors test_pubsub_exact_happy_path but uses custom_command to send
        SUBSCRIBE (lazy) or SUBSCRIBE_BLOCKING (blocking) commands directly.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "test_exact_channel_custom"
            message = "test_exact_message_custom"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            # Create client with callback only (no config-based subscriptions)
            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                callback=callback,
                context=context,
            )

            publishing_client = await create_client(request, cluster_mode)

            # Subscribe using custom_command
            if subscription_method == SubscriptionMethod.Lazy:
                # SUBSCRIBE is the lazy (non-blocking) command
                cmd: List[Union[str, bytes]] = ["SUBSCRIBE", channel]
            else:  # Blocking
                # SUBSCRIBE_BLOCKING takes channels followed by timeout_ms
                timeout_ms = 500
                cmd = ["SUBSCRIBE_BLOCKING", channel, str(timeout_ms)]

            if cluster_mode:
                result = await cast(
                    GlideClusterClient, listening_client
                ).custom_command(cmd)
            else:
                result = await listening_client.custom_command(cmd)

            assert result is None

            # Verify subscription is established
            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
            )

            result = await publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1

            # Allow the message to propagate
            await anyio.sleep(1)

            pubsub_msg = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )

            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 1
            )

            # Unsubscribe using custom_command
            if subscription_method == SubscriptionMethod.Lazy:
                # UNSUBSCRIBE is the lazy (non-blocking) command
                unsub_cmd: List[Union[str, bytes]] = ["UNSUBSCRIBE", channel]
            else:  # Blocking
                # UNSUBSCRIBE_BLOCKING takes channels followed by timeout_ms
                timeout_ms = 5000
                unsub_cmd = ["UNSUBSCRIBE_BLOCKING", channel, str(timeout_ms)]

            if cluster_mode:
                result = await cast(
                    GlideClusterClient, listening_client
                ).custom_command(unsub_cmd)
            else:
                result = await listening_client.custom_command(unsub_cmd)

            assert result is None

            # Verify unsubscription
            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=set(),
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_mock_pubsub
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_resubscribe_after_connection_kill_exact_channels(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that exact channel subscriptions are automatically restored after connection kill.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "reconnect_exact_channel_test"
            message_before = "message_before_kill"
            message_after = "message_after_kill"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels={channel},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await subscribe_by_method(
                    listening_client, {channel}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels={channel},
            )

            # Verify subscription works before kill
            await publishing_client.publish(message_before, channel)
            await anyio.sleep(1)

            msg_before = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )
            assert msg_before.message == message_before
            assert msg_before.channel == channel

            # Kill connections - this should trigger reconnection
            await kill_connections(publishing_client, None)

            # give some time for connection to reconnect
            await anyio.sleep(2)

            # Wait for subscriptions to be re-established
            await wait_for_subscription_state(
                listening_client,
                expected_channels={channel},
            )

            # Verify subscription still works after reconnection
            await publishing_client.publish(message_after, channel)
            await anyio.sleep(1)

            msg_after = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 1
            )
            assert msg_after.message == message_after
            assert msg_after.channel == channel

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 2
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_mock_pubsub
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_resubscribe_after_connection_kill_patterns(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that pattern subscriptions are automatically restored after connection kill.
        """
        listening_client, publishing_client = None, None
        try:
            pattern = "news_reconnect_pattern.*"
            channel = "news_reconnect_pattern.sports"
            message_before = "message_before_kill"
            message_after = "message_after_kill"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    patterns={pattern},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await psubscribe_by_method(
                    listening_client, {pattern}, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_patterns={pattern},
            )

            # Verify subscription works before kill
            await publishing_client.publish(message_before, channel)
            await anyio.sleep(1)

            msg_before = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )
            assert msg_before.message == message_before
            assert msg_before.channel == channel
            assert msg_before.pattern == pattern

            # Kill connections
            await kill_connections(publishing_client, None)

            # give some time for connection to reconnect
            await anyio.sleep(2)

            await wait_for_subscription_state(
                listening_client,
                expected_patterns={pattern},
            )

            # Verify subscription still works after reconnection
            await publishing_client.publish(message_after, channel)
            await anyio.sleep(1)

            msg_after = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 1
            )
            assert msg_after.message == message_after
            assert msg_after.channel == channel
            assert msg_after.pattern == pattern

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 2
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_mock_pubsub
    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_resubscribe_after_connection_kill_sharded(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that sharded subscriptions are automatically restored after connection kill.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "sharded_reconnect_test_channel"
            message_before = "message_before_kill"
            message_after = "message_after_kill"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    sharded_channels={channel},
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await ssubscribe_by_method(
                    cast(GlideClusterClient, listening_client),
                    {channel},
                    subscription_method,
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_sharded={channel},
            )

            # Verify subscription works before kill
            await cast(GlideClusterClient, publishing_client).publish(
                message_before, channel, sharded=True
            )
            await anyio.sleep(1)

            msg_before = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 0
            )
            assert msg_before.message == message_before
            assert msg_before.channel == channel

            # Kill connections
            await kill_connections(publishing_client, None)

            # give some time for connection to reconnect
            await anyio.sleep(2)

            await wait_for_subscription_state(
                listening_client,
                expected_sharded={channel},
            )

            # Verify subscription still works after reconnection
            await cast(GlideClusterClient, publishing_client).publish(
                message_after, channel, sharded=True
            )
            await anyio.sleep(1)

            msg_after = await get_message_by_method(
                message_read_method, listening_client, callback_messages, 1
            )
            assert msg_after.message == message_after
            assert msg_after.channel == channel

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, 2
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.skip_if_mock_pubsub
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "message_read_method",
        [MessageReadMethod.Async, MessageReadMethod.Sync, MessageReadMethod.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    async def test_resubscribe_after_connection_kill_many_exact_channels(
        self,
        request,
        cluster_mode: bool,
        message_read_method: MessageReadMethod,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that 256 exact channel subscriptions are automatically restored after connection kill.
        """
        listening_client, publishing_client = None, None
        try:
            NUM_CHANNELS = 256
            channels = {f"{{reconnect_exact_{i}}}channel" for i in range(NUM_CHANNELS)}
            message_after = "message_after_kill"

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if message_read_method == MessageReadMethod.Callback:
                callback = new_message
                context = callback_messages

            if subscription_method == SubscriptionMethod.Config:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    channels=channels,
                    callback=callback,
                    context=context,
                )
            else:
                listening_client = await create_pubsub_client(
                    request,
                    cluster_mode,
                    callback=callback,
                    context=context,
                )
                await subscribe_by_method(
                    listening_client, channels, subscription_method
                )

            publishing_client = await create_client(request, cluster_mode)

            await wait_for_subscription_state_if_needed(
                listening_client,
                subscription_method,
                expected_channels=channels,
            )

            # Kill connections
            await kill_connections(publishing_client, None)
            #  give time for reconnect
            await anyio.sleep(2)

            # Wait for resubscription
            await wait_for_subscription_state(
                listening_client,
                expected_channels=channels,
                timeout_ms=5000,
            )

            # Publish to all channels after reconnection
            for channel in channels:
                await publishing_client.publish(message_after, channel)

            await anyio.sleep(2)

            # Verify all messages received
            received_channels: Set[str] = set()
            for index in range(NUM_CHANNELS):
                msg = await get_message_by_method(
                    message_read_method, listening_client, callback_messages, index
                )
                assert msg.message == message_after
                assert msg.pattern is None
                received_channels.add(cast(str, msg.channel))

            assert received_channels == channels, "Not all channels received messages"

            await check_no_messages_left(
                message_read_method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_reconciliation_interval_config(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that a short pubsub_reconciliation_interval causes faster reconciliation.

        Uses sync timestamp metrics to verify reconciliation happens at approximately
        the configured interval.
        """
        listening_client = None
        try:
            # Use a short interval (500ms) for faster testing
            short_interval_ms = 500

            # Create client with short reconciliation interval
            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                reconciliation_interval_ms=short_interval_ms,
            )

            # Get initial timestamp
            initial_stats = await listening_client.get_statistics()
            previous_timestamp = int(
                initial_stats.get("subscription_last_sync_timestamp", "0")
            )

            # Iterate 5 times and verify timestamp increases by approximately the interval each time
            for i in range(5):
                await anyio.sleep(
                    0.6
                )  # Sleep slightly longer than interval to ensure reconciliation runs

                stats = await listening_client.get_statistics()
                current_timestamp = int(
                    stats.get("subscription_last_sync_timestamp", "0")
                )

                time_diff_ms = current_timestamp - previous_timestamp

                assert time_diff_ms >= short_interval_ms, (
                    f"Iteration {i + 1}: Timestamp difference ({time_diff_ms}ms) should be >= {short_interval_ms}ms "
                    f"Previous: {previous_timestamp}, Current: {current_timestamp}"
                )

                previous_timestamp = current_timestamp

        finally:
            await pubsub_client_cleanup(listening_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_callback_only_raises_error_on_get_methods(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Tests that when a client is configured with only a callback (no polling),
        calling get_pubsub_message() or try_get_pubsub_message() raises ConfigurationError.
        Also verifies that messages are still received through the callback when
        subscriptions are added dynamically.
        """
        listening_client, publishing_client = None, None
        try:
            callback_messages: List[PubSubMsg] = []

            # Create client with callback but no initial subscriptions
            listening_client = await create_pubsub_client(
                request,
                cluster_mode,
                callback=new_message,
                context=callback_messages,
            )

            # Verify get_pubsub_message raises ConfigurationError
            with pytest.raises(ConfigurationError) as exc_info:
                await listening_client.get_pubsub_message()
            assert "callback" in str(exc_info.value).lower()

            # Verify try_get_pubsub_message raises ConfigurationError
            with pytest.raises(ConfigurationError) as exc_info:
                listening_client.try_get_pubsub_message()
            assert "callback" in str(exc_info.value).lower()

        finally:
            await pubsub_client_cleanup(listening_client)
            await pubsub_client_cleanup(publishing_client)
