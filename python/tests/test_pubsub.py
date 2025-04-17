# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from enum import IntEnum
from typing import Any, Dict, List, Optional, Set, Tuple, Union, cast

import anyio
import pytest

from glide.async_commands.core import CoreCommands
from glide.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ProtocolVersion,
)
from glide.constants import OK
from glide.exceptions import ConfigurationError
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from tests.conftest import create_client
from tests.utils.utils import check_if_server_version_lt, get_random_string


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


async def create_two_clients_with_pubsub(
    request,
    cluster_mode,
    client1_pubsub: Optional[Any] = None,
    client2_pubsub: Optional[Any] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = None,
) -> Tuple[TGlideClient, TGlideClient]:
    """
    Sets 2 up clients for testing purposes with optional pubsub configuration.

    Args:
        request: pytest request for creating a client.
        cluster_mode: the cluster mode.
        client1_pubsub: pubsub configuration subscription for the first client.
        client2_pubsub: pubsub configuration subscription for the second client.
        protocol: what protocol to use, used for the test: `test_pubsub_resp2_raise_an_error`.
    """
    cluster_mode_pubsub1, standalone_mode_pubsub1 = None, None
    cluster_mode_pubsub2, standalone_mode_pubsub2 = None, None
    if cluster_mode:
        cluster_mode_pubsub1 = client1_pubsub
        cluster_mode_pubsub2 = client2_pubsub
    else:
        standalone_mode_pubsub1 = client1_pubsub
        standalone_mode_pubsub2 = client2_pubsub

    client1 = await create_client(
        request,
        cluster_mode=cluster_mode,
        cluster_mode_pubsub=cluster_mode_pubsub1,
        standalone_mode_pubsub=standalone_mode_pubsub1,
        protocol=protocol,
        request_timeout=timeout,
    )
    try:
        client2 = await create_client(
            request,
            cluster_mode=cluster_mode,
            cluster_mode_pubsub=cluster_mode_pubsub2,
            standalone_mode_pubsub=standalone_mode_pubsub2,
            protocol=protocol,
            request_timeout=timeout,
        )
    except Exception as e:
        await client1.close()
        raise e

    return client1, client2


def decode_pubsub_msg(msg: Optional[CoreCommands.PubSubMsg]) -> CoreCommands.PubSubMsg:
    if not msg:
        return CoreCommands.PubSubMsg("", "", None)
    string_msg = cast(bytes, msg.message).decode()
    string_channel = cast(bytes, msg.channel).decode()
    string_pattern = cast(bytes, msg.pattern).decode() if msg.pattern else None
    decoded_msg = CoreCommands.PubSubMsg(string_msg, string_channel, string_pattern)
    return decoded_msg


async def get_message_by_method(
    method: MethodTesting,
    client: TGlideClient,
    messages: Optional[List[CoreCommands.PubSubMsg]] = None,
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


def create_pubsub_subscription(
    cluster_mode,
    cluster_channels_and_patterns: Dict[
        GlideClusterClientConfiguration.PubSubChannelModes, Set[str]
    ],
    standalone_channels_and_patterns: Dict[
        GlideClientConfiguration.PubSubChannelModes, Set[str]
    ],
    callback=None,
    context=None,
):
    if cluster_mode:
        return GlideClusterClientConfiguration.PubSubSubscriptions(
            channels_and_patterns=cluster_channels_and_patterns,
            callback=callback,
            context=context,
        )
    return GlideClientConfiguration.PubSubSubscriptions(
        channels_and_patterns=standalone_channels_and_patterns,
        callback=callback,
        context=context,
    )


def new_message(msg: CoreCommands.PubSubMsg, context: Any):
    received_messages: List[CoreCommands.PubSubMsg] = context
    received_messages.append(msg)


async def client_cleanup(
    client: Optional[Union[GlideClient, GlideClusterClient]],
    cluster_mode_subs: Optional[
        GlideClusterClientConfiguration.PubSubSubscriptions
    ] = None,
):
    """
    This function tries its best to clear state assosiated with client
    Its explicitly calls client.close() and deletes the object
    In addition, it tries to clean up cluster mode subsciptions since it was found the closing the client via close() is
    not enough.
    Note that unsubscribing is not feasible in the current implementation since its unknown on which node the subs
    are configured
    """

    if client is None:
        return

    if cluster_mode_subs:
        for (
            channel_type,
            channel_patterns,
        ) in cluster_mode_subs.channels_and_patterns.items():
            if channel_type == GlideClusterClientConfiguration.PubSubChannelModes.Exact:
                cmd = "UNSUBSCRIBE"
            elif (
                channel_type
                == GlideClusterClientConfiguration.PubSubChannelModes.Pattern
            ):
                cmd = "PUNSUBSCRIBE"
            elif not await check_if_server_version_lt(client, "7.0.0"):
                cmd = "SUNSUBSCRIBE"
            else:
                # disregard sharded config for versions < 7.0.0
                continue

            for channel_patern in channel_patterns:
                await client.custom_command([cmd, channel_patern])

    await client.close()
    del client
    # The closure is not completed in the glide-core instantly
    await anyio.sleep(1)


@pytest.mark.anyio
class TestPubSub:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_exact_happy_path(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
    ):
        """
        Tests the basic happy path for exact PUBSUB functionality.

        This test covers the basic PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific channel is correctly received by a subscriber.
        """
        listening_client, publishing_client = None, None
        try:
            channel = get_random_string(10)
            message = get_random_string(5)

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []
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
                request, cluster_mode, pub_sub
            )

            result = await publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            await anyio.sleep(1)

            pubsub_msg = await get_message_by_method(
                method, listening_client, callback_messages, 0
            )

            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            await check_no_messages_left(method, listening_client, callback_messages, 1)
        finally:
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_happy_path_coexistence(
        self, request, cluster_mode: bool
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in exact PUBSUB.

        This test covers the scenario where messages are published to a channel
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        listening_client, publishing_client = None, None
        try:
            channel = get_random_string(10)
            message = get_random_string(5)
            message2 = get_random_string(7)

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
                {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_exact_happy_path_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
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

            # Create a map of channels to random messages with shard prefix
            channels_and_messages = {
                f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
                for _ in range(NUM_CHANNELS)
            }

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: set(
                        channels_and_messages.keys()
                    )
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: set(
                        channels_and_messages.keys()
                    )
                },
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
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
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}
            # check no messages left
            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_happy_path_many_channels_co_existence(
        self, request, cluster_mode: bool
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

            # Create a map of channels to random messages with shard prefix
            channels_and_messages = {
                f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
                for _ in range(NUM_CHANNELS)
            }

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: set(
                        channels_and_messages.keys()
                    )
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: set(
                        channels_and_messages.keys()
                    )
                },
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels_and_messages)):
                method = MethodTesting.Async if index % 2 else MethodTesting.Sync
                pubsub_msg = await get_message_by_method(method, listening_client)

                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}
            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_sharded_pubsub(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test sharded PUBSUB functionality with different message retrieval methods.

        This test covers the sharded PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific sharded channel is correctly received by a subscriber.
        """
        listening_client, publishing_client = None, None
        try:
            channel = get_random_string(10)
            message = get_random_string(5)
            publish_response = 1

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []
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
                request, cluster_mode, pub_sub
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_sharded_pubsub_co_existence(self, request, cluster_mode: bool):
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
            channel = get_random_string(10)
            message = get_random_string(5)
            message2 = get_random_string(7)

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
                {},
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_sharded_pubsub_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
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

            # Create a map of channels to random messages with shard prefix
            channels_and_messages = {
                f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
                for _ in range(NUM_CHANNELS)
            }

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: set(
                        channels_and_messages.keys()
                    )
                },
                {},
                callback=callback,
                context=context,
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            # Publish messages to each channel
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
                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}

            # Assert there are no more messages to read
            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            if listening_client:
                await client_cleanup(
                    listening_client, pub_sub if cluster_mode else None
                )
            if publishing_client:
                await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_pattern(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Test PUBSUB with pattern subscription using different message retrieval methods.

        This test verifies the behavior of PUBSUB when subscribing to a pattern and receiving
        messages using three different methods: Async, Sync, and Callback.
        """
        listening_client, publishing_client = None, None
        try:
            PATTERN = "{{{}}}:{}".format("channel", "*")
            channels = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    5
                ),
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    5
                ),
            }

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
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
                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            await check_no_messages_left(method, listening_client, callback_messages, 2)

        finally:
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern_co_existence(self, request, cluster_mode: bool):
        """
        Tests the coexistence of async and sync message retrieval methods in pattern-based PUBSUB.

        This test covers the scenario where messages are published to a channel that match a specified pattern
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        listening_client, publishing_client = None, None
        try:
            PATTERN = "{{{}}}:{}".format("channel", "*")
            channels = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    5
                ),
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    5
                ),
            }

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            for channel, message in channels.items():
                result = await publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            await anyio.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels)):
                method = MethodTesting.Async if index % 2 else MethodTesting.Sync
                pubsub_msg = await get_message_by_method(method, listening_client)

                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            # assert there are no more messages to read
            with pytest.raises(TimeoutError):
                with anyio.fail_after(3):
                    await listening_client.get_pubsub_message()

            assert listening_client.try_get_pubsub_message() is None

        finally:
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_pattern_many_channels(
        self, request, cluster_mode: bool, method: MethodTesting
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
            PATTERN = "{{{}}}:{}".format("channel", "*")
            channels = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    5
                )
                for _ in range(NUM_CHANNELS)
            }

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                callback=callback,
                context=context,
            )
            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
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
                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

        finally:
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_and_pattern_one_client(
        self, request, cluster_mode: bool, method: MethodTesting
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
            PATTERN = "{{{}}}:{}".format("pattern", "*")

            # Create dictionaries of channels and their corresponding messages
            exact_channels_and_messages = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    10
                )
                for _ in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages = {
                "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(
                    5
                )
                for _ in range(NUM_CHANNELS)
            }

            all_channels_and_messages = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            # Setup PUBSUB for exact channels
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    ),
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {
                        PATTERN
                    },
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    ),
                    GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN},
                },
                callback=callback,
                context=context,
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            # Publish messages to all channels
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
                pattern = (
                    PATTERN
                    if pubsub_msg.channel in pattern_channels_and_messages.keys()
                    else None
                )
                assert pubsub_msg.channel in all_channels_and_messages.keys()
                assert (
                    pubsub_msg.message == all_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == pattern
                del all_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert all_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 2
            )
        finally:
            await client_cleanup(
                listening_client, pub_sub_exact if cluster_mode else None
            )
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_and_pattern_multiple_clients(
        self, request, cluster_mode: bool, method: MethodTesting
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
            PATTERN = "{{{}}}:{}".format("pattern", "*")

            # Create dictionaries of channels and their corresponding messages
            exact_channels_and_messages = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    10
                )
                for _ in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages = {
                "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(
                    5
                )
                for _ in range(NUM_CHANNELS)
            }

            all_channels_and_messages = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            # Setup PUBSUB for exact channels
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    )
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    )
                },
                callback=callback,
                context=context,
            )

            (
                listening_client_exact,
                publishing_client,
            ) = await create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages_pattern

            # Setup PUBSUB for pattern channels
            pub_sub_pattern = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                callback=callback,
                context=context,
            )

            (
                listening_client_pattern,
                client_dont_care,
            ) = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_pattern
            )

            # Publish messages to all channels
            for channel, message in all_channels_and_messages.items():
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
                assert pubsub_msg.channel in exact_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == exact_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern is None
                del exact_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert exact_channels_and_messages == {}

            # Verify messages for pattern PUBSUB
            for index in range(len(pattern_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_pattern, callback_messages_pattern, index
                )
                assert pubsub_msg.channel in pattern_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == pattern_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == PATTERN
                del pattern_channels_and_messages[pubsub_msg.channel]

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
            await client_cleanup(
                listening_client_exact, pub_sub_exact if cluster_mode else None
            )
            await client_cleanup(publishing_client, None)
            await client_cleanup(
                listening_client_pattern, pub_sub_pattern if cluster_mode else None
            )
            await client_cleanup(client_dont_care, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_pattern_and_sharded_one_client(
        self, request, cluster_mode: bool, method: MethodTesting
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
            PATTERN = "{{{}}}:{}".format("pattern", "*")
            SHARD_PREFIX = "{same-shard}"

            # Create dictionaries of channels and their corresponding messages
            exact_channels_and_messages = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    10
                )
                for _ in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages = {
                "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(
                    5
                )
                for _ in range(NUM_CHANNELS)
            }
            sharded_channels_and_messages = {
                f"{SHARD_PREFIX}: {get_random_string(10)}": get_random_string(7)
                for _ in range(NUM_CHANNELS)
            }

            publish_response = 1

            callback, context = None, None
            callback_messages: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            # Setup PUBSUB for exact channels
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    ),
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {
                        PATTERN
                    },
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: set(
                        sharded_channels_and_messages.keys()
                    ),
                },
                {},
                callback=callback,
                context=context,
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            # Publish messages to all channels
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

            all_channels_and_messages = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
                **sharded_channels_and_messages,
            }
            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                pattern = (
                    PATTERN
                    if pubsub_msg.channel in pattern_channels_and_messages.keys()
                    else None
                )
                assert pubsub_msg.channel in all_channels_and_messages.keys()
                assert (
                    pubsub_msg.message == all_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == pattern
                del all_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert all_channels_and_messages == {}

            await check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 3
            )

        finally:
            await client_cleanup(
                listening_client, pub_sub_exact if cluster_mode else None
            )
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_exact_pattern_and_sharded_multi_client(
        self, request, cluster_mode: bool, method: MethodTesting
    ):
        """
        Tests combined exact, pattern and sharded PUBSUB with multiple clients, one for each subscription.

        This test verifies that separate clients can correctly handle exact, pattern and sharded PUBSUB
        subscriptions. It covers the following scenarios:
        - Subscribing to multiple channels with exact names and verifying message reception.
        - Subscribing to channels using a pattern and verifying message reception.
        - Subscribing to channels using a sharded subscription and verifying message reception.
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

        (
            pub_sub_exact,
            pub_sub_sharded,
            pub_sub_pattern,
        ) = (None, None, None)

        try:
            NUM_CHANNELS = 256
            PATTERN = "{{{}}}:{}".format("pattern", "*")
            SHARD_PREFIX = "{same-shard}"

            # Create dictionaries of channels and their corresponding messages
            exact_channels_and_messages = {
                "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(
                    10
                )
                for _ in range(NUM_CHANNELS)
            }
            pattern_channels_and_messages = {
                "{{{}}}:{}".format("pattern", get_random_string(5)): get_random_string(
                    5
                )
                for _ in range(NUM_CHANNELS)
            }
            sharded_channels_and_messages = {
                f"{SHARD_PREFIX}: {get_random_string(10)}": get_random_string(7)
                for _ in range(NUM_CHANNELS)
            }

            publish_response = 1

            callback, context = None, None
            callback_messages_exact: List[CoreCommands.PubSubMsg] = []
            callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
            callback_messages_sharded: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages_exact

            # Setup PUBSUB for exact channels
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    )
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: set(
                        exact_channels_and_messages.keys()
                    )
                },
                callback=callback,
                context=context,
            )

            (
                listening_client_exact,
                publishing_client,
            ) = await create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            if method == MethodTesting.Callback:
                context = callback_messages_pattern

            # Setup PUBSUB for pattern channels
            pub_sub_pattern = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {PATTERN}},
                callback=callback,
                context=context,
            )

            if method == MethodTesting.Callback:
                context = callback_messages_sharded

            pub_sub_sharded = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: set(
                        sharded_channels_and_messages.keys()
                    )
                },
                {},
                callback=callback,
                context=context,
            )

            (
                listening_client_pattern,
                listening_client_sharded,
            ) = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_pattern, pub_sub_sharded
            )

            # Publish messages to all channels
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

            # Verify messages for exact PUBSUB
            for index in range(len(exact_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_exact, callback_messages_exact, index
                )
                assert pubsub_msg.channel in exact_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == exact_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern is None
                del exact_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert exact_channels_and_messages == {}

            # Verify messages for pattern PUBSUB
            for index in range(len(pattern_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_pattern, callback_messages_pattern, index
                )
                assert pubsub_msg.channel in pattern_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == pattern_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern == PATTERN
                del pattern_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert pattern_channels_and_messages == {}

            # Verify messages for shaded PUBSUB
            for index in range(len(sharded_channels_and_messages)):
                pubsub_msg = await get_message_by_method(
                    method, listening_client_sharded, callback_messages_sharded, index
                )
                assert pubsub_msg.channel in sharded_channels_and_messages.keys()
                assert (
                    pubsub_msg.message
                    == sharded_channels_and_messages[pubsub_msg.channel]
                )
                assert pubsub_msg.pattern is None
                del sharded_channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert sharded_channels_and_messages == {}

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
            await client_cleanup(
                listening_client_exact, pub_sub_exact if cluster_mode else None
            )
            await client_cleanup(publishing_client, None)
            await client_cleanup(
                listening_client_pattern, pub_sub_pattern if cluster_mode else None
            )
            await client_cleanup(
                listening_client_sharded, pub_sub_sharded if cluster_mode else None
            )

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_combined_different_channels_with_same_name(
        self, request, cluster_mode: bool, method: MethodTesting
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

        (
            pub_sub_exact,
            pub_sub_sharded,
            pub_sub_pattern,
        ) = (None, None, None)

        try:
            CHANNEL_NAME = "same-channel-name"
            MESSAGE_EXACT = get_random_string(10)
            MESSAGE_PATTERN = get_random_string(7)
            MESSAGE_SHARDED = get_random_string(5)

            callback, context = None, None
            callback_messages_exact: List[CoreCommands.PubSubMsg] = []
            callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
            callback_messages_sharded: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages_exact

            # Setup PUBSUB for exact channel
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
                callback=callback,
                context=context,
            )

            (
                listening_client_exact,
                publishing_client,
            ) = await create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            # Setup PUBSUB for pattern channel
            if method == MethodTesting.Callback:
                context = callback_messages_pattern

            # Setup PUBSUB for pattern channels
            pub_sub_pattern = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {
                        CHANNEL_NAME
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
                callback=callback,
                context=context,
            )

            if method == MethodTesting.Callback:
                context = callback_messages_sharded

            pub_sub_sharded = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        CHANNEL_NAME
                    }
                },
                {},
                callback=callback,
                context=context,
            )

            (
                listening_client_pattern,
                listening_client_sharded,
            ) = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_pattern, pub_sub_sharded
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
            for client, callback, pattern in [  # type: ignore
                (listening_client_exact, callback_messages_exact, None),
                (listening_client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = await get_message_by_method(method, client, callback, 1)  # type: ignore
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
            await client_cleanup(
                listening_client_exact, pub_sub_exact if cluster_mode else None
            )
            await client_cleanup(publishing_client, None)
            await client_cleanup(
                listening_client_pattern, pub_sub_pattern if cluster_mode else None
            )
            await client_cleanup(
                listening_client_sharded, pub_sub_sharded if cluster_mode else None
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_two_publishing_clients_same_name(
        self, request, cluster_mode: bool, method: MethodTesting
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
            MESSAGE_EXACT = get_random_string(10)
            MESSAGE_PATTERN = get_random_string(7)
            callback, context_exact, context_pattern = None, None, None
            callback_messages_exact: List[CoreCommands.PubSubMsg] = []
            callback_messages_pattern: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context_exact = callback_messages_exact
                context_pattern = callback_messages_pattern

            # Setup PUBSUB for exact channel
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
                callback=callback,
                context=context_exact,
            )
            # Setup PUBSUB for pattern channels
            pub_sub_pattern = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {
                        CHANNEL_NAME
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
                callback=callback,
                context=context_pattern,
            )

            client_exact, client_pattern = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_exact, pub_sub_pattern
            )

            # Publish messages to each channel - both clients publishing
            for msg in [MESSAGE_EXACT, MESSAGE_PATTERN]:
                result = await client_pattern.publish(msg, CHANNEL_NAME)
                if cluster_mode:
                    assert result == 2

            # allow the message to propagate
            await anyio.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = await get_message_by_method(method, client, callback, 1)  # type: ignore
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
            await client_cleanup(client_exact, pub_sub_exact if cluster_mode else None)
            await client_cleanup(
                client_pattern, pub_sub_pattern if cluster_mode else None
            )

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    async def test_pubsub_three_publishing_clients_same_name_with_sharded(
        self, request, cluster_mode: bool, method: MethodTesting
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
            MESSAGE_EXACT = get_random_string(10)
            MESSAGE_PATTERN = get_random_string(7)
            MESSAGE_SHARDED = get_random_string(5)
            publish_response = 2 if cluster_mode else OK
            callback, context_exact, context_pattern, context_sharded = (
                None,
                None,
                None,
                None,
            )
            callback_messages_exact: List[CoreCommands.PubSubMsg] = []
            callback_messages_pattern: List[CoreCommands.PubSubMsg] = []
            callback_messages_sharded: List[CoreCommands.PubSubMsg] = []

            if method == MethodTesting.Callback:
                callback = new_message
                context_exact = callback_messages_exact
                context_pattern = callback_messages_pattern
                context_sharded = callback_messages_sharded

            # Setup PUBSUB for exact channel
            pub_sub_exact = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}},
                callback=callback,
                context=context_exact,
            )
            # Setup PUBSUB for pattern channels
            pub_sub_pattern = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {
                        CHANNEL_NAME
                    }
                },
                {GlideClientConfiguration.PubSubChannelModes.Pattern: {CHANNEL_NAME}},
                callback=callback,
                context=context_pattern,
            )
            # Setup PUBSUB for pattern channels
            pub_sub_sharded = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        CHANNEL_NAME
                    }
                },
                {},
                callback=callback,
                context=context_sharded,
            )

            client_exact, client_pattern = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_exact, pub_sub_pattern
            )
            client_sharded, client_dont_care = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_sharded
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
            for client, callback, pattern in [  # type: ignore
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = await get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = await get_message_by_method(method, client, callback, 1)  # type: ignore
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
            await client_cleanup(client_exact, pub_sub_exact if cluster_mode else None)
            await client_cleanup(
                client_pattern, pub_sub_pattern if cluster_mode else None
            )
            await client_cleanup(
                client_sharded, pub_sub_sharded if cluster_mode else None
            )
            await client_cleanup(client_dont_care, None)

    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_max_size_message(self, request, cluster_mode: bool):
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
        channel = get_random_string(10)
        message = "1" * 512 * 1024 * 1024
        message2 = "2" * 512 * 1024 * 1024

        pub_sub = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        listening_client, publishing_client = await create_two_clients_with_pubsub(
            request,
            cluster_mode,
            pub_sub,
            timeout=10000,
        )

        try:
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_sharded_max_size_message(self, request, cluster_mode: bool):
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
            channel = get_random_string(10)
            message = "1" * 512 * 1024 * 1024
            message2 = "2" * 512 * 1024 * 1024

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
                {},
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub,
                timeout=10000,
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_exact_max_size_message_callback(
        self, request, cluster_mode: bool
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
            channel = get_random_string(10)
            message = "0" * 12 * 1024 * 1024

            callback_messages: List[CoreCommands.PubSubMsg] = []
            callback, context = new_message, callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
                {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
                callback=callback,
                context=context,
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub, timeout=10000
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.skip(
        reason="This test requires special configuration for client-output-buffer-limit for valkey-server and timeouts seems "
        + "to vary across platforms and server versions"
    )
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_sharded_max_size_message_callback(
        self, request, cluster_mode: bool
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
            channel = get_random_string(10)
            message = "0" * 512 * 1024 * 1024

            callback_messages: List[CoreCommands.PubSubMsg] = []
            callback, context = new_message, callback_messages

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {channel}},
                {},
                callback=callback,
                context=context,
            )

            listening_client, publishing_client = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub, timeout=10000
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
            await client_cleanup(listening_client, pub_sub if cluster_mode else None)
            await client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_resp2_raise_an_error(self, request, cluster_mode: bool):
        """Tests that when creating a resp2 client with PUBSUB - an error will be raised"""
        channel = get_random_string(5)

        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        with pytest.raises(ConfigurationError):
            await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_exact, protocol=ProtocolVersion.RESP2
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_context_with_no_callback_raise_error(
        self, request, cluster_mode: bool
    ):
        """Tests that when creating a PUBSUB client in callback method with context but no callback raises an error"""
        channel = get_random_string(5)
        context: List[CoreCommands.PubSubMsg] = []
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            context=context,
        )

        with pytest.raises(ConfigurationError):
            await create_two_clients_with_pubsub(request, cluster_mode, pub_sub_exact)

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

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        channel1,
                        channel2,
                        channel3,
                    }
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: {
                        channel1,
                        channel2,
                        channel3,
                    }
                },
            )

            channel1_bytes = channel1.encode()
            channel2_bytes = channel2.encode()
            channel3_bytes = channel3.encode()

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

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
            await client_cleanup(client1, pub_sub if cluster_mode else None)
            await client_cleanup(client2, None)
            await client_cleanup(client, None)

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
            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Pattern: {
                        pattern1,
                        pattern2,
                    }
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Pattern: {
                        pattern1,
                        pattern2,
                    }
                },
            )

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            # Test pubsub_numpat
            num_patterns = await client2.pubsub_numpat()
            assert num_patterns == 2

        finally:
            await client_cleanup(client1, pub_sub if cluster_mode else None)
            await client_cleanup(client2, None)
            await client_cleanup(client, None)

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

            # Set up subscriptions
            pub_sub1 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        channel1,
                        channel2,
                        channel3,
                    }
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: {
                        channel1,
                        channel2,
                        channel3,
                    }
                },
            )
            pub_sub2 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        channel2,
                        channel3,
                    }
                },
                {
                    GlideClientConfiguration.PubSubChannelModes.Exact: {
                        channel2,
                        channel3,
                    }
                },
            )
            pub_sub3 = create_pubsub_subscription(
                cluster_mode,
                {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel3}},
                {GlideClientConfiguration.PubSubChannelModes.Exact: {channel3}},
            )

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

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub1, pub_sub2
            )
            client3, client4 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub3
            )

            # Test pubsub_numsub
            subscribers = await client2.pubsub_numsub(
                [channel1_bytes, channel2_bytes, channel3_bytes, channel4_bytes]
            )
            assert subscribers == {
                channel1_bytes: 1,
                channel2_bytes: 2,
                channel3_bytes: 3,
                channel4_bytes: 0,
            }

            # Test pubsub_numsub with no channels
            empty_subscribers = await client2.pubsub_numsub()
            assert empty_subscribers == {}

        finally:
            await client_cleanup(client1, pub_sub1 if cluster_mode else None)
            await client_cleanup(client2, pub_sub2 if cluster_mode else None)
            await client_cleanup(client3, pub_sub3 if cluster_mode else None)
            await client_cleanup(client4, None)
            await client_cleanup(client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_pubsub_shardchannels(self, request, cluster_mode: bool):
        """
        Tests the pubsub_shardchannels command functionality.

        This test verifies that the pubsub_shardchannels command correctly returns
        the active sharded channels matching a specified pattern.
        """
        pub_sub, client1, client2, client = None, None, None, None
        try:
            channel1 = "test_shardchannel1"
            channel2 = "test_shardchannel2"
            channel3 = "some_shardchannel3"
            pattern = "test_*"

            client = await create_client(request, cluster_mode)
            assert isinstance(client, GlideClusterClient)
            # Assert no sharded channels exist yet
            assert await client.pubsub_shardchannels() == []

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        channel1,
                        channel2,
                        channel3,
                    }
                },
                {},  # Empty dict for non-cluster mode as sharded channels are not supported
            )

            channel1_bytes = channel1.encode()
            channel2_bytes = channel2.encode()
            channel3_bytes = channel3.encode()

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

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
            await client_cleanup(client1, pub_sub if cluster_mode else None)
            await client_cleanup(client2, None)
            await client_cleanup(client, None)

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

            # Set up subscriptions
            pub_sub1 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        channel1,
                        channel2,
                        channel3,
                    }
                },
                {},
            )
            pub_sub2 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        channel2,
                        channel3,
                    }
                },
                {},
            )
            pub_sub3 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        channel3
                    }
                },
                {},
            )

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

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub1, pub_sub2
            )

            client3, client4 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub3
            )

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
            await client_cleanup(client1, pub_sub1 if cluster_mode else None)
            await client_cleanup(client2, pub_sub2 if cluster_mode else None)
            await client_cleanup(client3, pub_sub3 if cluster_mode else None)
            await client_cleanup(client4, None)
            await client_cleanup(client, None)

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

            pub_sub = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        regular_channel
                    },
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        shard_channel
                    },
                },
                {GlideClientConfiguration.PubSubChannelModes.Exact: {regular_channel}},
            )

            regular_channel_bytes, shard_channel_bytes = (
                regular_channel.encode(),
                shard_channel.encode(),
            )

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            assert isinstance(client2, GlideClusterClient)
            # Test pubsub_channels
            assert await client2.pubsub_channels() == [regular_channel_bytes]

            # Test pubsub_shardchannels
            assert await client2.pubsub_shardchannels() == [shard_channel_bytes]

        finally:
            await client_cleanup(client1, pub_sub if cluster_mode else None)
            await client_cleanup(client2, None)

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

            pub_sub1 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        regular_channel
                    },
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        shard_channel
                    },
                },
                {},
            )
            pub_sub2 = create_pubsub_subscription(
                cluster_mode,
                {
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        regular_channel
                    },
                    GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {
                        shard_channel
                    },
                },
                {},
            )

            regular_channel_bytes: bytes = regular_channel.encode()
            shard_channel_bytes: bytes = shard_channel.encode()

            client1, client2 = await create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub1, pub_sub2
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
            await client_cleanup(client1, pub_sub1 if cluster_mode else None)
            await client_cleanup(client2, pub_sub2 if cluster_mode else None)
