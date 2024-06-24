# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import asyncio
from enum import IntEnum
from typing import Any, Dict, List, Optional, Set, Tuple, Union

import pytest
from glide.async_commands.core import InfoSection
from glide.config import (
    ClusterClientConfiguration,
    ProtocolVersion,
    RedisClientConfiguration,
)
from glide.constants import OK
from glide.exceptions import WrongConfiguration
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.conftest import create_client
from tests.utils.utils import check_if_server_version_lt, get_random_string


class PubSubChannelModes(IntEnum):
    Exact = 0
    """ Use exact channel names """
    Pattern = 1
    """ Use channel name patterns """
    Sharded = 2
    """ Use sharded pubsub """


class MethodTesting(IntEnum):
    """
    Enumeration for specifying the method of Pub/Sub subscription.
    """

    Async = 0
    Sync = 1
    Callback = 2


async def setup_clients(
    request,
    cluster_mode,
    pub_sub,
    pub_sub2: Optional[Any] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
) -> Tuple[
    Union[RedisClient, RedisClusterClient], Union[RedisClient, RedisClusterClient]
]:
    "Sets up clients for testing purposes."
    c_pubsub, s_pubsub = None, None
    c2_pubsub, s2_pubsub = None, None
    if cluster_mode:
        c_pubsub = pub_sub
        c2_pubsub = pub_sub2
    else:
        s_pubsub = pub_sub
        s2_pubsub = pub_sub2

    publishing_client = await create_client(
        request,
        cluster_mode=cluster_mode,
        cluster_mode_pubsub=c2_pubsub,
        standalone_mode_pubsub=s2_pubsub,
        protocol=protocol,
    )
    listening_client = await create_client(
        request,
        cluster_mode=cluster_mode,
        cluster_mode_pubsub=c_pubsub,
        standalone_mode_pubsub=s_pubsub,
        protocol=protocol,
    )
    return publishing_client, listening_client


async def get_message_by_method(
    method: MethodTesting,
    client: TRedisClient,
    messages: Optional[
        List[Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]]
    ] = None,
    index: Optional[int] = None,
):
    if method == MethodTesting.Async:
        return await client.get_pubsub_message()
    elif method == MethodTesting.Sync:
        return client.try_get_pubsub_message()
    assert messages and (index is not None)
    return messages[index]


async def check_no_messages_left(
    method,
    client: TRedisClient,
):
    if method == MethodTesting.Async:
        # assert there are no messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(client.get_pubsub_message(), timeout=3)
    elif method == MethodTesting.Sync:
        assert client.try_get_pubsub_message() is None
        # check callback


def create_channels_and_patterns(
    cluster_mode, modes_and_channels: Dict[PubSubChannelModes, Set[str]]
):
    result: Dict[
        Union[
            RedisClientConfiguration.PubSubChannelModes,
            ClusterClientConfiguration.PubSubChannelModes,
        ],
        Set[str],
    ] = {}
    for mode, channel in modes_and_channels.items():
        if mode == PubSubChannelModes.Exact:
            if cluster_mode:
                result[ClusterClientConfiguration.PubSubChannelModes.Exact] = channel
            else:
                result[RedisClientConfiguration.PubSubChannelModes.Exact] = channel
        elif mode == PubSubChannelModes.Pattern:
            if cluster_mode:
                result[ClusterClientConfiguration.PubSubChannelModes.Pattern] = channel
            else:
                result[RedisClientConfiguration.PubSubChannelModes.Pattern] = channel
        else:
            result[ClusterClientConfiguration.PubSubChannelModes.Sharded] = channel
    return result


def create_pubsub_subscription(
    cluster_mode,
    channels_and_patterns: Dict[PubSubChannelModes, Set[str]],
    callback=None,
    context=None,
):
    channels = create_channels_and_patterns(cluster_mode, channels_and_patterns)
    if cluster_mode:
        return ClusterClientConfiguration.PubSubSubscriptions(
            channels_and_patterns=channels,
            callback=callback,
            context=context,
        )
    return RedisClientConfiguration.PubSubSubscriptions(
        channels_and_patterns=channels, callback=callback, context=context
    )


def new_message(
    msg: Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg], context: Any
):
    received_messages: List[RedisClient.PubSubMsg] = context
    received_messages.append(msg)


@pytest.mark.asyncio
class TestPubSub:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_basic_happy_path(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
    ):
        """
        Tests the basic happy path for Pub/Sub functionality.

        This test covers the basic pub/sub flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific channel is correctly received by a subscriber.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        publish_response = 1 if cluster_mode else OK

        callback, context = None, None
        callback_massages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_massages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {channel}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        assert await publishing_client.publish(message, channel) == publish_response
        # allow the message to propagate
        await asyncio.sleep(1)

        pubsub_msg = await get_message_by_method(
            method, listening_client, callback_massages, 0
        )

        assert pubsub_msg.message == message
        assert pubsub_msg.channel == channel
        assert pubsub_msg.pattern is None

        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_basic_happy_path_coexistence(
        self, request, cluster_mode: bool
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in Pub/Sub.

        This test covers the scenario where messages are published to a channel
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        message2 = get_random_string(7)
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {channel}},
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        assert await publishing_client.publish(message, channel) == publish_response
        assert await publishing_client.publish(message2, channel) == publish_response
        # allow the message to propagate
        await asyncio.sleep(1)

        async_msg = await listening_client.get_pubsub_message()
        sync_msg = listening_client.try_get_pubsub_message()
        assert sync_msg

        assert async_msg.message == message
        assert async_msg.channel == channel
        assert async_msg.pattern is None

        assert sync_msg.message == message2
        assert sync_msg.channel == channel
        assert sync_msg.pattern is None

        # assert there are no messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

        assert listening_client.try_get_pubsub_message() is None

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_basic_happy_path_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests publishing and receiving messages across many channels in Pub/Sub.

        This test covers the scenario where multiple channels each receive their own
        unique message. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"
        publish_response = 1 if cluster_mode else OK

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_massages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_massages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: set(channels_and_messages.keys())},
            callback=callback,
            context=context,
        )
        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        # Publish messages to each channel
        for channel, message in channels_and_messages.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # Allow the messages to propagate
        await asyncio.sleep(1)

        # Check if all messages are received correctly
        for index, (channel, expected_message) in enumerate(
            channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_massages, index
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(
                ["UNSUBSCRIBE", *list(channels_and_messages.keys())]
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_basic_happy_path_many_channels_co_existence(
        self, request, cluster_mode: bool
    ):
        """
        Tests publishing and receiving messages across many channels in Pub/Sub, ensuring coexistence of async and sync retrieval methods.

        This test covers scenarios where multiple channels each receive their own unique message.
        It verifies that messages are correctly published and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"
        publish_response = 1 if cluster_mode else OK

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: set(channels_and_messages.keys())},
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        # Publish messages to each channel
        for channel, message in channels_and_messages.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # Allow the messages to propagate
        await asyncio.sleep(1)

        # Check if all messages are received correctly by each method
        for index, (channel, expected_message) in enumerate(
            channels_and_messages.items()
        ):
            method = MethodTesting.Async if index % 2 else MethodTesting.Sync
            msg = await get_message_by_method(method, listening_client)

            assert msg.message == expected_message
            assert msg.channel == channel
            assert msg.pattern is None

        # assert there are no messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

        assert listening_client.try_get_pubsub_message() is None

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(
                ["UNSUBSCRIBE", *list(channels_and_messages.keys())]
            )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_sharded_pubsub(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test sharded Pub/Sub functionality with different message retrieval methods.

        This test covers the sharded pub/sub flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific sharded channel is correctly received by a subscriber.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        publish_response = 1

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Sharded: {channel}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )
        min_version = "7.0.0"
        if await check_if_server_version_lt(publishing_client, min_version):
            pytest.skip(reason=f"Redis version required >= {min_version}")

        assert type(publishing_client) == RedisClusterClient
        assert (
            await publishing_client.publish(message, channel, sharded=True)
            == publish_response
        )
        # allow the message to propagate
        await asyncio.sleep(1)

        pubsub_msg = await get_message_by_method(
            method, listening_client, callback_messages, 0
        )
        assert pubsub_msg.message == message
        assert pubsub_msg.channel == channel
        assert pubsub_msg.pattern is None

        # assert there are no messages to read
        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["SUNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_sharded_pubsub_co_existence(self, request, cluster_mode: bool):
        """
        Test sharded Pub/Sub with co-existence of multiple messages.

        This test verifies the behavior of sharded Pub/Sub when multiple messages are published
        to the same sharded channel. It ensures that both async and sync methods of message retrieval
        function correctly in this scenario.

        It covers the scenario where messages are published to a sharded channel and received using
        both async and sync methods. This ensures that the asynchronous and synchronous message
        retrieval methods can coexist without interfering with each other and operate as expected.
        """
        channel = get_random_string(10)
        message = get_random_string(5)
        message2 = get_random_string(7)
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode, {PubSubChannelModes.Sharded: {channel}}
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        min_version = "7.0.0"
        if await check_if_server_version_lt(publishing_client, min_version):
            pytest.skip(reason=f"Redis version required >= {min_version}")

        assert type(publishing_client) == RedisClusterClient
        assert (
            await publishing_client.publish(message, channel, sharded=True)
            == publish_response
        )
        assert (
            await publishing_client.publish(message2, channel, sharded=True)
            == publish_response
        )
        # allow the messages to propagate
        await asyncio.sleep(1)

        async_msg = await listening_client.get_pubsub_message()
        sync_msg = listening_client.try_get_pubsub_message()
        assert sync_msg

        assert async_msg.message == message
        assert async_msg.channel == channel
        assert async_msg.pattern is None

        assert sync_msg.message == message2
        assert sync_msg.channel == channel
        assert sync_msg.pattern is None

        # assert there are no messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

        assert listening_client.try_get_pubsub_message() is None
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["SUNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_sharded_pubsub_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test sharded Pub/Sub with multiple channels and different message retrieval methods.

        This test verifies the behavior of sharded Pub/Sub when multiple messages are published
        across multiple sharded channels. It covers three different message retrieval methods:
        Async, Sync, and Callback.
        """
        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"
        publish_response = 1

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Sharded: set(channels_and_messages.keys())},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        min_version = "7.0.0"
        if await check_if_server_version_lt(publishing_client, min_version):
            pytest.skip(reason=f"Redis version required >= {min_version}")

        assert type(publishing_client) == RedisClusterClient
        # Publish messages to each channel
        for channel, message in channels_and_messages.items():
            assert (
                await publishing_client.publish(message, channel, sharded=True)
                == publish_response
            )

        # Allow the messages to propagate
        await asyncio.sleep(1)

        # Check if all messages are received correctly
        for index, (channel, expected_message) in enumerate(
            channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, index
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        # Assert there are no more messages to read
        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(
                ["SUNSUBSCRIBE", *list(channels_and_messages.keys())]
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_pattern(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test Pub/Sub with pattern subscription using different message retrieval methods.

        This test verifies the behavior of Pub/Sub when subscribing to a pattern and receiving
        messages using three different methods: Async, Sync, and Callback.
        """
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
        }
        publish_response = 1 if cluster_mode else OK

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )
        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        for channel, message in channels.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # allow the message to propagate
        await asyncio.sleep(1)

        for index, (channel, message) in enumerate(channels.items()):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, index
            )
            assert pubsub_msg.pattern == PATTERN
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel

        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern_co_existence(self, request, cluster_mode: bool):
        """
        Tests the coexistence of async and sync message retrieval methods in pattern-based Pub/Sub.

        This test covers the scenario where messages are published to a channel that match a specified pattern
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
        }
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode, {PubSubChannelModes.Pattern: {PATTERN}}
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        for channel, message in channels.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # allow the message to propagate
        await asyncio.sleep(1)

        for index, (channel, message) in enumerate(channels.items()):
            method = MethodTesting.Async if index % 2 else MethodTesting.Sync
            msg = await get_message_by_method(method, listening_client)

            assert msg.message == message
            assert msg.channel == channel
            assert msg.pattern == PATTERN

        # assert there are no more messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

        assert listening_client.try_get_pubsub_message() is None
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_pattern_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests publishing and receiving messages across many channels in pattern-based Pub/Sub.

        This test covers the scenario where messages are published to multiple channels that match a specified pattern
        and received. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }
        publish_response = 1 if cluster_mode else OK

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )
        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        for channel, message in channels.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # allow the message to propagate
        await asyncio.sleep(1)

        for index, (channel, message) in enumerate(channels.items()):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, index
            )
            assert pubsub_msg.pattern == PATTERN
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel

        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_basic_and_pattern_one_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined basic and pattern Pub/Sub with one client.

        This test verifies that a single client can correctly handle both basic and pattern Pub/Sub
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")

        # Create dictionaries of channels and their corresponding messages
        basic_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        all_channels_and_messages = {
            **basic_channels_and_messages,
            **pattern_channels_and_messages,
        }

        publish_response = 1 if cluster_mode else OK

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        # Setup pub/sub for exact channels
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {
                PubSubChannelModes.Exact: set(basic_channels_and_messages.keys()),
                PubSubChannelModes.Pattern: {PATTERN},
            },
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await setup_clients(
            request,
            cluster_mode,
            pub_sub_basic,
        )

        # Publish messages to all channels
        for channel, message in all_channels_and_messages.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # allow the message to propagate
        await asyncio.sleep(1)

        # Verify messages for basic pub/sub
        for i, (channel, expected_message) in enumerate(
            basic_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        # Verify messages for pattern pub/sub
        for index, (channel, expected_message) in enumerate(
            pattern_channels_and_messages.items(), start=i + 1
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, index
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern == PATTERN

        await check_no_messages_left(method, listening_client)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(
                ["UNSUBSCRIBE", *list(basic_channels_and_messages.keys())]
            )
            await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_basic_and_pattern_multiple_clients(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined basic and pattern Pub/Sub with multiple clients, one for each subscription.

        This test verifies that separate clients can correctly handle both basic and pattern Pub/Sub
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")

        # Create dictionaries of channels and their corresponding messages
        basic_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        all_channels_and_messages = {
            **basic_channels_and_messages,
            **pattern_channels_and_messages,
        }

        publish_response = 1 if cluster_mode else OK

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        # Setup pub/sub for exact channels
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: set(basic_channels_and_messages.keys())},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client_basic = await setup_clients(
            request,
            cluster_mode,
            pub_sub_basic,
        )

        callback_messages_pattern: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_pattern

        # Setup pub/sub for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )

        _, listening_client_pattern = await setup_clients(
            request, cluster_mode, pub_sub_pattern
        )

        # Publish messages to all channels
        for channel, message in all_channels_and_messages.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # allow the messages to propagate
        await asyncio.sleep(1)

        # Verify messages for basic pub/sub
        for i, (channel, expected_message) in enumerate(
            basic_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client_basic, callback_messages, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        # Verify messages for pattern pub/sub
        for i, (channel, expected_message) in enumerate(
            pattern_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client_pattern, callback_messages_pattern, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern == PATTERN

        await check_no_messages_left(method, listening_client_basic)
        await check_no_messages_left(method, listening_client_pattern)
        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client_basic.custom_command(
                ["UNSUBSCRIBE", *list(basic_channels_and_messages.keys())]
            )
            await listening_client_pattern.custom_command(["PUNSUBSCRIBE", PATTERN])

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_basic_pattern_and_sharded_one_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined basic, pattern and sharded Pub/Sub with one client.

        This test verifies that a single client can correctly handle both basic, pattern and sharded Pub/Sub
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Subscribing to channels using a with sharded subscription and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")
        SHARD_PREFIX = "{same-shard}"

        # Create dictionaries of channels and their corresponding messages
        basic_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }
        sharded_channels_and_messages = {
            f"{SHARD_PREFIX}:{get_random_string(10)}": get_random_string(7)
            for _ in range(NUM_CHANNELS)
        }

        publish_response = 1

        callback, context = None, None
        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        # Setup pub/sub for exact channels
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {
                PubSubChannelModes.Exact: set(basic_channels_and_messages.keys()),
                PubSubChannelModes.Pattern: {PATTERN},
                PubSubChannelModes.Sharded: set(sharded_channels_and_messages.keys()),
            },
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await setup_clients(
            request,
            cluster_mode,
            pub_sub_basic,
        )

        # Setup pub/sub for sharded channels (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        assert type(publishing_client) == RedisClusterClient

        # Publish messages to all channels
        for channel, message in {
            **basic_channels_and_messages,
            **pattern_channels_and_messages,
        }.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # Publish sharded messages to all channels
        for channel, message in sharded_channels_and_messages.items():
            assert (
                await publishing_client.publish(message, channel, sharded=True)
                == publish_response
            )

        # allow the messages to propagate
        await asyncio.sleep(1)

        # Verify messages for basic pub/sub
        for i, (channel, expected_message) in enumerate(
            basic_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        # Verify messages for pattern pub/sub
        for j, (channel, expected_message) in enumerate(
            pattern_channels_and_messages.items(), start=i + 1
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, j
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern == PATTERN

        # Verify messages for sharded pub/sub
        for k, (channel, expected_message) in enumerate(
            sharded_channels_and_messages.items(), start=j + 1
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, k
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        await check_no_messages_left(method, listening_client)

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(
                ["UNSUBSCRIBE", *list(basic_channels_and_messages.keys())]
            )
            await listening_client.custom_command(["PUNSUBSCRIBE", PATTERN])
            await listening_client.custom_command(
                ["SUNSUBSCRIBE", *list(sharded_channels_and_messages.keys())]
            )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_basic_pattern_and_sharded_multi_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined basic, pattern and sharded Pub/Sub with multiple clients, one for each subscription.

        This test verifies that separate clients can correctly handle both basic, pattern and sharded Pub/Sub
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Subscribing to channels using a sharded subscription and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("pattern", "*")
        SHARD_PREFIX = "{same-shard}"

        # Create dictionaries of channels and their corresponding messages
        basic_channels_and_messages = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(10)
            for _ in range(NUM_CHANNELS)
        }
        pattern_channels_and_messages = {
            "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }
        sharded_channels_and_messages = {
            f"{SHARD_PREFIX}:{get_random_string(10)}": get_random_string(7)
            for _ in range(NUM_CHANNELS)
        }

        publish_response = 1

        callback, context = None, None
        callback_messages_basic: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_pattern: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_sharded: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_basic

        # Setup pub/sub for exact channels
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: set(basic_channels_and_messages.keys())},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client_basic = await setup_clients(
            request,
            cluster_mode,
            pub_sub_basic,
        )

        # Setup pub/sub for sharded channels (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        if method == MethodTesting.Callback:
            context = callback_messages_pattern

        # Setup pub/sub for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {PATTERN}},
            callback=callback,
            context=context,
        )

        if method == MethodTesting.Callback:
            context = callback_messages_sharded

        pub_sub_sharded = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Sharded: set(sharded_channels_and_messages.keys())},
            callback=callback,
            context=context,
        )

        listening_client_sharded, listening_client_pattern = await setup_clients(
            request, cluster_mode, pub_sub_pattern, pub_sub_sharded
        )

        assert type(publishing_client) == RedisClusterClient

        # Publish messages to all channels
        for channel, message in {
            **basic_channels_and_messages,
            **pattern_channels_and_messages,
        }.items():
            assert await publishing_client.publish(message, channel) == publish_response

        # Publish sharded messages to all channels
        for channel, message in sharded_channels_and_messages.items():
            assert (
                await publishing_client.publish(message, channel, sharded=True)
                == publish_response
            )

        # allow the messages to propagate
        await asyncio.sleep(1)

        # Verify messages for basic pub/sub
        for i, (channel, expected_message) in enumerate(
            basic_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client_basic, callback_messages_basic, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        # Verify messages for pattern pub/sub
        for i, (channel, expected_message) in enumerate(
            pattern_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client_pattern, callback_messages_pattern, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern == PATTERN

        # Verify messages for sharded pub/sub
        for i, (channel, expected_message) in enumerate(
            sharded_channels_and_messages.items()
        ):
            pubsub_msg = await get_message_by_method(
                method, listening_client_sharded, callback_messages_sharded, i
            )
            assert pubsub_msg.message == expected_message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

        await check_no_messages_left(method, listening_client_basic)
        await check_no_messages_left(method, listening_client_pattern)
        await check_no_messages_left(method, listening_client_sharded)

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client_basic.custom_command(
                ["UNSUBSCRIBE", *list(basic_channels_and_messages.keys())]
            )
            await listening_client_pattern.custom_command(["PUNSUBSCRIBE", PATTERN])
            await listening_client_sharded.custom_command(
                ["SUNSUBSCRIBE", *list(sharded_channels_and_messages.keys())]
            )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_different_channels_same_name(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined Pub/Sub with different channel modes using the same channel name.
        One publishing clients, 3 listening clients, one for each mode.

        This test verifies that separate clients can correctly handle subscriptions for exact, pattern, and sharded channels with the same name.
        It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Subscribing to a sharded channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        CHANNEL_NAME = "same-channel-name" + get_random_string(3)
        MESSAGE_BASIC = get_random_string(10)
        MESSAGE_PATTERN = get_random_string(7)
        MESSAGE_SHARDED = get_random_string(5)

        callback, context = None, None
        callback_messages_basic: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_pattern: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_sharded: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_basic

        # Setup pub/sub for exact channel
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client_basic = await setup_clients(
            request,
            cluster_mode,
            pub_sub_basic,
        )

        # (Redis version > 7)
        if await check_if_server_version_lt(publishing_client, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")

        # Setup pub/sub for pattern channel
        if method == MethodTesting.Callback:
            context = callback_messages_pattern

        # Setup pub/sub for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )

        if method == MethodTesting.Callback:
            context = callback_messages_sharded

        pub_sub_sharded = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Sharded: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )

        listening_client_sharded, listening_client_pattern = await setup_clients(
            request, cluster_mode, pub_sub_pattern, pub_sub_sharded
        )

        assert type(publishing_client) == RedisClusterClient

        # Publish messages to each channel
        assert await publishing_client.publish(MESSAGE_BASIC, CHANNEL_NAME) == 2
        assert await publishing_client.publish(MESSAGE_PATTERN, CHANNEL_NAME) == 2
        assert (
            await publishing_client.publish(MESSAGE_SHARDED, CHANNEL_NAME, sharded=True)
            == 1
        )

        # allow the message to propagate
        await asyncio.sleep(1)

        # Verify message for basic pub/sub
        for i in range(2):
            pubsub_msg_basic = await get_message_by_method(
                method, listening_client_basic, callback_messages_basic, i
            )
            msg = MESSAGE_BASIC if i < 1 else MESSAGE_PATTERN
            assert pubsub_msg_basic.message == msg
            assert pubsub_msg_basic.channel == CHANNEL_NAME
            assert pubsub_msg_basic.pattern is None

        for i in range(2):
            # Verify message for pattern pub/sub
            pubsub_msg_pattern = await get_message_by_method(
                method, listening_client_pattern, callback_messages_pattern, i
            )
            msg = MESSAGE_BASIC if i < 1 else MESSAGE_PATTERN
            assert pubsub_msg_pattern.message == msg
            assert pubsub_msg_pattern.channel == CHANNEL_NAME
            assert pubsub_msg_pattern.pattern == CHANNEL_NAME

        # Verify message for sharded pub/sub
        pubsub_msg_sharded = await get_message_by_method(
            method, listening_client_sharded, callback_messages_sharded, 0
        )
        assert pubsub_msg_sharded.message == MESSAGE_SHARDED
        assert pubsub_msg_sharded.channel == CHANNEL_NAME
        assert pubsub_msg_sharded.pattern is None

        await check_no_messages_left(method, listening_client_basic)
        await check_no_messages_left(method, listening_client_pattern)
        await check_no_messages_left(method, listening_client_sharded)

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client_basic.custom_command(["UNSUBSCRIBE", CHANNEL_NAME])
            await listening_client_pattern.custom_command(
                ["PUNSUBSCRIBE", CHANNEL_NAME]
            )
            await listening_client_sharded.custom_command(
                ["SUNSUBSCRIBE", CHANNEL_NAME]
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_two_publishing_clients_same_name(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests Pub/Sub with two publishing clients using the same channel name.
        One client uses pattern subscription, the other uses exact.
        The clients publishes messages to each other, and to thyself.

        This test verifies that two separate clients can correctly publish to and handle subscriptions
        for exact and pattern channels with the same name. It covers the following scenarios:
        - Subscribing to an exact channel and verifying message reception.
        - Subscribing to a pattern channel and verifying message reception.
        - Ensuring that messages are correctly published and received using different retrieval methods (async, sync, callback).
        - Verifying that no messages are left unread.
        - Properly unsubscribing from all channels to avoid interference with other tests.
        """
        CHANNEL_NAME = "same-channel-name" + get_random_string(3)
        MESSAGE_BASIC = get_random_string(10)
        MESSAGE_PATTERN = get_random_string(7)
        publish_response = 2 if cluster_mode else OK
        callback, context, context2 = None, None, None
        callback_messages_basic: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_pattern: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_basic
            context2 = callback_messages_pattern

        # Setup pub/sub for exact channel
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )
        # Setup pub/sub for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            callback=callback,
            context=context2,
        )

        client_pattern, client_basic = await setup_clients(
            request, cluster_mode, pub_sub_basic, pub_sub_pattern
        )

        # Publish messages to each channel - both clients publishing
        assert (
            await client_pattern.publish(MESSAGE_BASIC, CHANNEL_NAME)
            == publish_response
        )
        assert (
            await client_basic.publish(MESSAGE_PATTERN, CHANNEL_NAME)
            == publish_response
        )

        # allow the message to propagate
        await asyncio.sleep(1)

        # Verify message for basic pub/sub
        for i in range(2):
            pubsub_msg_basic = await get_message_by_method(
                method, client_basic, callback_messages_basic, i
            )
            msg = MESSAGE_BASIC if i < 1 else MESSAGE_PATTERN
            assert pubsub_msg_basic.message == msg
            assert pubsub_msg_basic.channel == CHANNEL_NAME
            assert pubsub_msg_basic.pattern is None

        for i in range(2):
            # Verify message for pattern pub/sub
            pubsub_msg_pattern = await get_message_by_method(
                method, client_pattern, callback_messages_pattern, i
            )
            msg = MESSAGE_BASIC if i < 1 else MESSAGE_PATTERN
            assert pubsub_msg_pattern.message == msg
            assert pubsub_msg_pattern.channel == CHANNEL_NAME
            assert pubsub_msg_pattern.pattern == CHANNEL_NAME

        await check_no_messages_left(method, client_pattern)
        await check_no_messages_left(method, client_basic)

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await client_basic.custom_command(["UNSUBSCRIBE", CHANNEL_NAME])
            await client_pattern.custom_command(["PUNSUBSCRIBE", CHANNEL_NAME])

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_two_publishing_clients_same_name_with_sharded(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        This test
        """
        CHANNEL_NAME = "same-channel-name" + get_random_string(3)
        MESSAGE_BASIC = get_random_string(10)
        MESSAGE_PATTERN = get_random_string(7)
        MESSAGE_SHARDED = get_random_string(5)
        publish_response = 2 if cluster_mode else OK
        callback, context, context2, context3 = None, None, None, None
        callback_messages_basic: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_pattern: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback_messages_sharded: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []

        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages_basic
            context2 = callback_messages_pattern
            context3 = callback_messages_sharded

        # Setup pub/sub for exact channel
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {CHANNEL_NAME}},
            callback=callback,
            context=context,
        )
        # Setup pub/sub for pattern channels
        pub_sub_pattern = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Pattern: {CHANNEL_NAME}},
            callback=callback,
            context=context2,
        )
        # Setup pub/sub for pattern channels
        pub_sub_sharded = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Sharded: {CHANNEL_NAME}},
            callback=callback,
            context=context3,
        )

        client_pattern, client_basic = await setup_clients(
            request, cluster_mode, pub_sub_basic, pub_sub_pattern
        )
        _, client_sharded = await setup_clients(request, cluster_mode, pub_sub_sharded)
        # (Redis version > 7)
        if await check_if_server_version_lt(client_pattern, "7.0.0"):
            pytest.skip("Redis version required >= 7.0.0")
        assert type(client_basic) == RedisClusterClient

        # Publish messages to each channel - both clients publishing
        assert (
            await client_pattern.publish(MESSAGE_BASIC, CHANNEL_NAME)
            == publish_response
        )
        assert (
            await client_sharded.publish(MESSAGE_PATTERN, CHANNEL_NAME)
            == publish_response
        )
        assert (
            await client_basic.publish(MESSAGE_SHARDED, CHANNEL_NAME, sharded=True) == 1
        )

        # allow the message to propagate
        await asyncio.sleep(1)

        # Verify message for basic pub/sub
        for i in range(2):
            pubsub_msg_basic = await get_message_by_method(
                method, client_basic, callback_messages_basic, i
            )
            msg = MESSAGE_BASIC if i < 1 else MESSAGE_PATTERN
            assert pubsub_msg_basic.message == msg
            assert pubsub_msg_basic.channel == CHANNEL_NAME
            assert pubsub_msg_basic.pattern is None

        for i in range(2):
            # Verify message for pattern pub/sub
            pubsub_msg_pattern = await get_message_by_method(
                method, client_pattern, callback_messages_pattern, i
            )
            msg = MESSAGE_BASIC if i < 1 else MESSAGE_PATTERN
            assert pubsub_msg_pattern.message == msg
            assert pubsub_msg_pattern.channel == CHANNEL_NAME
            assert pubsub_msg_pattern.pattern == CHANNEL_NAME

        msg = await get_message_by_method(
            method, client_sharded, callback_messages_sharded, 0
        )
        assert msg.message == MESSAGE_SHARDED
        assert msg.channel == CHANNEL_NAME
        assert msg.pattern is None

        await check_no_messages_left(method, client_pattern)
        await check_no_messages_left(method, client_basic)
        await check_no_messages_left(method, client_sharded)

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await client_basic.custom_command(["UNSUBSCRIBE", CHANNEL_NAME])
            await client_pattern.custom_command(["PUNSUBSCRIBE", CHANNEL_NAME])
            await client_sharded.custom_command(["SUNSUBSCRIBE", CHANNEL_NAME])

    @pytest.mark.skip(reason="no way of currently testing this")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_max_size_message(self, request, cluster_mode: bool):
        """
        Tests publishing and receiving maximum size messages in Pub/Sub.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly in both cluster and standalone modes. It ensures that the Pub/Sub system
        can handle maximum size messages without errors and that async and sync message
        retrieval methods can coexist and function correctly.

        The test covers the following scenarios:
        - Setting up Pub/Sub subscription for a specific channel.
        - Publishing two maximum size messages to the channel.
        - Verifying that the messages are received correctly using both async and sync methods.
        - Ensuring that no additional messages are left after the expected messages are received.
        """
        channel = get_random_string(10)
        message = get_random_string(512 * 1024 * 1024)
        message2 = get_random_string(512 * 1024 * 1024)
        publish_response = 1 if cluster_mode else OK

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {channel}},
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        assert await publishing_client.publish(message, channel) == publish_response
        assert await publishing_client.publish(message2, channel) == publish_response
        # allow the message to propagate
        await asyncio.sleep(5)

        async_msg = await listening_client.get_pubsub_message()
        sync_msg = listening_client.try_get_pubsub_message()
        assert sync_msg

        assert async_msg.message == message
        assert async_msg.channel == channel
        assert async_msg.pattern is None

        assert sync_msg.message == message2
        assert sync_msg.channel == channel
        assert sync_msg.pattern is None

        # assert there are no messages to read
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(listening_client.get_pubsub_message(), timeout=3)

        assert listening_client.try_get_pubsub_message() is None

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.skip(reason="no way of currently testing this")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_max_size_message_callback(self, request, cluster_mode: bool):
        """
        Tests publishing and receiving maximum size messages in Pub/Sub with callback method.

        This test verifies that very large messages (512MB - BulkString max size) can be published and received
        correctly in both cluster and standalone modes. It ensures that the Pub/Sub system
        can handle maximum size messages without errors and that the callback message
        retrieval method works as expected.

        The test covers the following scenarios:
        - Setting up Pub/Sub subscription for a specific channel with a callback.
        - Publishing a maximum size message to the channel.
        - Verifying that the message is received correctly using the callback method.
        """
        channel = get_random_string(10)
        message = get_random_string(512 * 1024 * 1024)
        message2 = get_random_string(512 * 1024 * 1024)
        publish_response = 1 if cluster_mode else OK

        callback_messages: List[
            Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]
        ] = []
        callback, context = new_message, callback_messages

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {channel}},
            callback=callback,
            context=context,
        )

        publishing_client, listening_client = await setup_clients(
            request, cluster_mode, pub_sub
        )

        assert await publishing_client.publish(message, channel) == publish_response
        # allow the message to propagate
        await asyncio.sleep(5)

        assert len(callback_messages) == 1

        assert callback_messages[0].message == message
        assert callback_messages[0].channel == channel
        assert callback_messages[0].pattern is None

        if cluster_mode:
            # Since all tests run on the same cluster, when closing the client, garbage collector can be called after another test will start running
            # In cluster mode, we check how many subscriptions received the message
            # So to avoid flakiness, we make sure to unsubscribe from the channels
            await listening_client.custom_command(["UNSUBSCRIBE", channel])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_resp2_raise_an_error(self, request, cluster_mode: bool):
        """Tests that when creating a resp2 client with pub/sub - an error will be raised"""
        channel = get_random_string(5)

        pub_sub_basic = create_pubsub_subscription(
            cluster_mode,
            {PubSubChannelModes.Exact: {channel}},
        )

        with pytest.raises(WrongConfiguration):
            await setup_clients(
                request, cluster_mode, pub_sub_basic, protocol=ProtocolVersion.RESP2
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_context_with_no_callback_raise_error(
        self, request, cluster_mode: bool
    ):
        """Tests that when creating a resp2 client with pub/sub - an error will be raised"""
        channel = get_random_string(5)
        context: List[Union[RedisClient.PubSubMsg, RedisClusterClient.PubSubMsg]] = []
        pub_sub_basic = create_pubsub_subscription(
            cluster_mode, {PubSubChannelModes.Exact: {channel}}, context=context
        )

        with pytest.raises(WrongConfiguration):
            await setup_clients(
                request, cluster_mode, pub_sub_basic, protocol=ProtocolVersion.RESP2
            )
