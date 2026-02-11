# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import time
from enum import IntEnum
from typing import Any, List, Optional, Set, Tuple, Union, cast

import pytest
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.constants import OK
from glide_shared.exceptions import (
    ConfigurationError,
    RequestError,
)
from glide_shared.routes import AllNodes
from glide_sync import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
)
from glide_sync.glide_client import GlideClient, GlideClusterClient, TGlideClient

from tests.sync_tests.conftest import create_sync_client
from tests.utils.pubsub_test_utils import PubSubTestConstants, new_message
from tests.utils.utils import (
    SubscriptionMethod,
    create_pubsub_subscription,
    create_sync_pubsub_client,
    decode_pubsub_msg,
    get_pubsub_modes,
    get_random_string,
    kill_connections,
    run_sync_func_with_timeout_in_thread,
    sync_check_if_server_version_lt,
)


def create_simple_pubsub_config(
    cluster_mode: bool,
    channels: Optional[Set[str]] = None,
    patterns: Optional[Set[str]] = None,
    sharded: Optional[Set[str]] = None,
):
    """Helper to create pubsub config from simple sets."""
    modes = (
        GlideClusterClientConfiguration.PubSubChannelModes
        if cluster_mode
        else GlideClientConfiguration.PubSubChannelModes
    )

    channels_dict = {}
    if channels:
        channels_dict[modes.Exact] = channels
    if patterns:
        channels_dict[modes.Pattern] = patterns
    if cluster_mode and sharded:
        channels_dict[modes.Sharded] = sharded  # type: ignore[union-attr,arg-type]

    return create_pubsub_subscription(
        cluster_mode,
        channels_dict if cluster_mode else {},  # type: ignore[union-attr,arg-type]
        channels_dict if not cluster_mode else {},  # type: ignore[union-attr,arg-type]
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


def sync_subscribe_by_method(
    client: TGlideClient,
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
    if subscription_method == SubscriptionMethod.Config:
        return  # Already subscribed at creation

    if subscription_method == SubscriptionMethod.Lazy:
        # Use lazy (non-blocking) subscribe methods
        if channels:
            client.subscribe_lazy(channels)
        if patterns:
            client.psubscribe_lazy(patterns)
        if cluster_mode and sharded:
            client.ssubscribe_lazy(sharded)  # type: ignore[union-attr]
        # Wait for lazy subscription to propagate
        time.sleep(0.5)
    else:  # Blocking
        # Use blocking subscribe with timeout
        if channels:
            client.subscribe(channels, timeout_ms=timeout_ms)
        if patterns:
            client.psubscribe(patterns, timeout_ms=timeout_ms)
        if cluster_mode and sharded:
            client.ssubscribe(sharded, timeout_ms=timeout_ms)  # type: ignore[union-attr]


def create_two_clients_with_pubsub(
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
        timeout: timeout in milliseconds for both request and connection timeouts.
    """
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
        )
    except Exception as e:
        client1.close()
        raise e

    return client1, client2


def get_message_by_method(
    method: MethodTesting,
    client: TGlideClient,
    messages: Optional[List[PubSubMsg]] = None,
    index: Optional[int] = None,
):
    if method == MethodTesting.Async:
        return decode_pubsub_msg(client.get_pubsub_message())
    elif method == MethodTesting.Sync:
        return decode_pubsub_msg(client.try_get_pubsub_message())
    assert messages and (index is not None)
    return decode_pubsub_msg(messages[index])


def check_no_messages_left(
    method,
    client: TGlideClient,
    callback: Optional[List[Any]] = None,
    expected_callback_messages_count: int = 0,
):
    if method == MethodTesting.Async:
        # assert there are no messages to read
        with pytest.raises(TimeoutError):
            run_sync_func_with_timeout_in_thread(
                lambda: client.get_pubsub_message(),  # This blocks indefinitely
                timeout=3.0,
            )
    elif method == MethodTesting.Sync:
        assert client.try_get_pubsub_message() is None
    else:
        assert callback is not None
        assert len(callback) == expected_callback_messages_count


def client_cleanup(
    client: Optional[Union[GlideClient, GlideClusterClient]],
    cluster_mode_subs: Optional[
        Union[
            GlideClusterClientConfiguration.PubSubSubscriptions,
            GlideClientConfiguration.PubSubSubscriptions,
        ]
    ] = None,
) -> None:
    """
    This function tries its best to clear state assosiated with client
    Its explicitly calls client.close() and deletes the object
    In addition, it tries to clean up cluster mode subsciptions since it was found the closing the client via close() is
    not enough.
    Note that unsubscribing is not feasible in the current implementation since its unknown on which node the subs
    are configured
    """
    # TODO: Once dynamic pubsub is implemented for the sync client
    # fix the cleanup to mirror that of the async client
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

            for channel_patern in channel_patterns:
                client.custom_command([cmd, channel_patern, "0"])

    client.close()
    del client
    # The closure is not completed in the glide-core instantly
    time.sleep(1)


def wait_for_subscription_state(
    client: Union[GlideClient, GlideClusterClient],
    expected_channels: Optional[Set[str]] = None,
    expected_patterns: Optional[Set[str]] = None,
    expected_sharded: Optional[Set[str]] = None,
    timeout_sec: float = 3.0,
    check_actual: bool = True,
) -> None:
    """
    Poll for subscription state to match expected channels/patterns.

    Args:
        client: The client to check
        expected_channels: Expected exact channels (None to skip check, empty set to check for no channels)
        expected_patterns: Expected pattern channels (None to skip check, empty set to check for no patterns)
        expected_sharded: Expected sharded channels (None to skip check, empty set to check for no sharded)
        timeout_sec: Maximum time to wait in seconds
        check_actual: If True, check actual_subscriptions; if False, check desired_subscriptions
    """
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


class TestSyncPubSub:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_pubsub_exact_happy_path(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests the basic happy path for exact PUBSUB functionality.

        This test covers the basic PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific channel is correctly received by a subscriber.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = get_random_string(5)

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            channels={channel},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            result = publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            time.sleep(1)

            pubsub_msg = get_message_by_method(
                method, listening_client, callback_messages, 0
            )

            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            check_no_messages_left(method, listening_client, callback_messages, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_pubsub_exact_happy_path_coexistence(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in exact PUBSUB.

        This test covers the scenario where messages are published to a channel
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = get_random_string(5)
        message2 = get_random_string(7)

        with sync_pubsub_test_clients(
            request, cluster_mode, subscription_method, channels={channel}
        ) as (listening_client, publishing_client):
            for msg in [message, message2]:
                result = publishing_client.publish(msg, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            time.sleep(1)

            async_msg_res = listening_client.get_pubsub_message()
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
                run_sync_func_with_timeout_in_thread(
                    lambda: listening_client.get_pubsub_message(), timeout=3.0
                )

            assert listening_client.try_get_pubsub_message() is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_pubsub_exact_happy_path_many_channels(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests publishing and receiving messages across many channels in exact PUBSUB.

        This test covers the scenario where multiple channels each receive their own
        unique message. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            channels=set(channels_and_messages.keys()),
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            time.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels_and_messages)):
                pubsub_msg = get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}
            # check no messages left
            check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
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
    def test_sync_pubsub_exact_happy_path_many_channels_co_existence(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests publishing and receiving messages across many channels in exact PUBSUB, ensuring coexistence of async and sync
        retrieval methods.

        This test covers scenarios where multiple channels each receive their own unique message.
        It verifies that messages are correctly published and received using both async and sync methods to ensure that
        both methods
        can coexist and function correctly.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            channels=set(channels_and_messages.keys()),
        ) as (listening_client, publishing_client):
            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # Allow the messages to propagate
            time.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels_and_messages)):
                method = MethodTesting.Async if index % 2 else MethodTesting.Sync
                pubsub_msg = get_message_by_method(method, listening_client)

                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}
            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                run_sync_func_with_timeout_in_thread(
                    lambda: listening_client.get_pubsub_message(), timeout=3.0
                )

            assert listening_client.try_get_pubsub_message() is None

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_sharded_pubsub(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test sharded PUBSUB functionality with different message retrieval methods.

        This test covers the sharded PUBSUB flow using three different methods:
        Async, Sync, and Callback. It verifies that a message published to a
        specific sharded channel is correctly received by a subscriber.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = get_random_string(5)
        publish_response = 1

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            sharded={channel},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == publish_response
            )

            # allow the message to propagate
            time.sleep(1)

            pubsub_msg = get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            # assert there are no messages to read
            check_no_messages_left(method, listening_client, callback_messages, 1)

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
    def test_sync_sharded_pubsub_co_existence(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
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
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = get_random_string(5)
        message2 = get_random_string(7)

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            sharded={channel},
        ) as (listening_client, publishing_client):
            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == 1
            )
            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    message2, channel, sharded=True
                )
                == 1
            )

            # allow the messages to propagate
            time.sleep(1)

            async_msg_res = listening_client.get_pubsub_message()
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
                run_sync_func_with_timeout_in_thread(
                    lambda: listening_client.get_pubsub_message(), timeout=3.0
                )

            assert listening_client.try_get_pubsub_message() is None

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
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_sharded_pubsub_many_channels(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test sharded PUBSUB with multiple channels and different message retrieval methods.

        This test verifies the behavior of sharded PUBSUB when multiple messages are published
        across multiple sharded channels. It covers three different message retrieval methods:
        Async, Sync, and Callback.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        NUM_CHANNELS = 256
        shard_prefix = "{same-shard}"
        publish_response = 1

        # Create a map of channels to random messages with shard prefix
        channels_and_messages = {
            f"{shard_prefix}{get_random_string(10)}": get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            sharded=set(channels_and_messages.keys()),
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Publish messages to each channel
            for channel, message in channels_and_messages.items():
                assert (
                    cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # Allow the messages to propagate
            time.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels_and_messages)):
                pubsub_msg = get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels_and_messages.keys()
                assert pubsub_msg.message == channels_and_messages[pubsub_msg.channel]
                assert pubsub_msg.pattern is None
                del channels_and_messages[pubsub_msg.channel]

            # check that we received all messages
            assert channels_and_messages == {}

            # Assert there are no more messages to read
            check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_pattern(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test PUBSUB with pattern subscription using different message retrieval methods.

        This test verifies the behavior of PUBSUB when subscribing to a pattern and receiving
        messages using three different methods: Async, Sync, and Callback.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
        }

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            patterns={PATTERN},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            for channel, message in channels.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            time.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels)):
                pubsub_msg = get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            check_no_messages_left(method, listening_client, callback_messages, 2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_pubsub_pattern_co_existence(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Tests the coexistence of async and sync message retrieval methods in pattern-based PUBSUB.

        This test covers the scenario where messages are published to a channel that match a specified pattern
        and received using both async and sync methods to ensure that both methods
        can coexist and function correctly.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5),
        }

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            patterns={PATTERN},
        ) as (listening_client, publishing_client):
            for channel, message in channels.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            time.sleep(1)

            # Check if all messages are received correctly by each method
            for index in range(len(channels)):
                method = MethodTesting.Async if index % 2 else MethodTesting.Sync
                pubsub_msg = get_message_by_method(method, listening_client)

                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            # assert there are no more messages to read
            with pytest.raises(TimeoutError):
                run_sync_func_with_timeout_in_thread(
                    lambda: listening_client.get_pubsub_message(), timeout=3.0
                )

            assert listening_client.try_get_pubsub_message() is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_pattern_many_channels(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests publishing and receiving messages across many channels in pattern-based PUBSUB.

        This test covers the scenario where messages are published to multiple channels that match a specified pattern
        and received. It verifies that messages are correctly published and received
        using different retrieval methods: async, sync, and callback.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        NUM_CHANNELS = 256
        PATTERN = "{{{}}}:{}".format("channel", "*")
        channels = {
            "{{{}}}:{}".format("channel", get_random_string(5)): get_random_string(5)
            for _ in range(NUM_CHANNELS)
        }

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            patterns={PATTERN},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            for channel, message in channels.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            time.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(channels)):
                pubsub_msg = get_message_by_method(
                    method, listening_client, callback_messages, index
                )
                assert pubsub_msg.channel in channels.keys()
                assert pubsub_msg.message == channels[pubsub_msg.channel]
                assert pubsub_msg.pattern == PATTERN
                del channels[pubsub_msg.channel]

            # check that we received all messages
            assert channels == {}

            check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_combined_exact_and_pattern_one_client(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
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
            callback_messages: List[PubSubMsg] = []

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

            listening_client, publishing_client = create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            # Publish messages to all channels
            for channel, message in all_channels_and_messages.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the message to propagate
            time.sleep(1)

            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = get_message_by_method(
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

            check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 2
            )
        finally:
            client_cleanup(listening_client, pub_sub_exact if cluster_mode else None)
            client_cleanup(publishing_client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_combined_exact_and_pattern_multiple_clients(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
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
            callback_messages: List[PubSubMsg] = []

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
            ) = create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            callback_messages_pattern: List[PubSubMsg] = []
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
            ) = create_two_clients_with_pubsub(request, cluster_mode, pub_sub_pattern)

            # Publish messages to all channels
            for channel, message in all_channels_and_messages.items():
                result = publishing_client.publish(message, channel)
                if cluster_mode:
                    assert result == 1

            # allow the messages to propagate
            time.sleep(1)

            # Verify messages for exact PUBSUB
            for index in range(len(exact_channels_and_messages)):
                pubsub_msg = get_message_by_method(
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
                pubsub_msg = get_message_by_method(
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

            check_no_messages_left(
                method, listening_client_exact, callback_messages, NUM_CHANNELS
            )
            check_no_messages_left(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                NUM_CHANNELS,
            )

        finally:
            client_cleanup(
                listening_client_exact, pub_sub_exact if cluster_mode else None
            )
            client_cleanup(publishing_client, None)
            client_cleanup(
                listening_client_pattern, pub_sub_pattern if cluster_mode else None
            )
            client_cleanup(client_dont_care, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_combined_exact_pattern_and_sharded_one_client(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
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
            callback_messages: List[PubSubMsg] = []

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

            listening_client, publishing_client = create_two_clients_with_pubsub(
                request,
                cluster_mode,
                pub_sub_exact,
            )

            # Publish messages to all channels
            for channel, message in {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }.items():
                assert publishing_client.publish(message, channel) == publish_response

            # Publish sharded messages to all channels
            for channel, message in sharded_channels_and_messages.items():
                assert (
                    cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # allow the messages to propagate
            time.sleep(1)

            all_channels_and_messages = {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
                **sharded_channels_and_messages,
            }
            # Check if all messages are received correctly
            for index in range(len(all_channels_and_messages)):
                pubsub_msg = get_message_by_method(
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

            check_no_messages_left(
                method, listening_client, callback_messages, NUM_CHANNELS * 3
            )

        finally:
            client_cleanup(listening_client, pub_sub_exact if cluster_mode else None)
            client_cleanup(publishing_client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_combined_exact_pattern_and_sharded_multi_client(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
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
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []
            callback_messages_sharded: List[PubSubMsg] = []

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
            ) = create_two_clients_with_pubsub(
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
            ) = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_pattern, pub_sub_sharded
            )

            # Publish messages to all channels
            for channel, message in {
                **exact_channels_and_messages,
                **pattern_channels_and_messages,
            }.items():
                assert publishing_client.publish(message, channel) == publish_response

            # Publish sharded messages to all channels
            for channel, message in sharded_channels_and_messages.items():
                assert (
                    cast(GlideClusterClient, publishing_client).publish(
                        message, channel, sharded=True
                    )
                    == publish_response
                )

            # allow the messages to propagate
            time.sleep(1)

            # Verify messages for exact PUBSUB
            for index in range(len(exact_channels_and_messages)):
                pubsub_msg = get_message_by_method(
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
                pubsub_msg = get_message_by_method(
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
                pubsub_msg = get_message_by_method(
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

            check_no_messages_left(
                method, listening_client_exact, callback_messages_exact, NUM_CHANNELS
            )
            check_no_messages_left(
                method,
                listening_client_pattern,
                callback_messages_pattern,
                NUM_CHANNELS,
            )
            check_no_messages_left(
                method,
                listening_client_sharded,
                callback_messages_sharded,
                NUM_CHANNELS,
            )

        finally:
            client_cleanup(
                listening_client_exact, pub_sub_exact if cluster_mode else None
            )
            client_cleanup(publishing_client, None)
            client_cleanup(
                listening_client_pattern, pub_sub_pattern if cluster_mode else None
            )
            client_cleanup(
                listening_client_sharded, pub_sub_sharded if cluster_mode else None
            )

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_combined_different_channels_with_same_name(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
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
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []
            callback_messages_sharded: List[PubSubMsg] = []

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
            ) = create_two_clients_with_pubsub(
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
            ) = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_pattern, pub_sub_sharded
            )

            # Publish messages to each channel
            assert publishing_client.publish(MESSAGE_EXACT, CHANNEL_NAME) == 2
            assert publishing_client.publish(MESSAGE_PATTERN, CHANNEL_NAME) == 2
            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    MESSAGE_SHARDED, CHANNEL_NAME, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            time.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (listening_client_exact, callback_messages_exact, None),
                (listening_client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = get_message_by_method(method, client, callback, 1)  # type: ignore
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            # Verify message for sharded PUBSUB
            pubsub_msg_sharded = get_message_by_method(
                method, listening_client_sharded, callback_messages_sharded, 0
            )
            assert pubsub_msg_sharded.message == MESSAGE_SHARDED
            assert pubsub_msg_sharded.channel == CHANNEL_NAME
            assert pubsub_msg_sharded.pattern is None

            check_no_messages_left(
                method, listening_client_exact, callback_messages_exact, 2
            )
            check_no_messages_left(
                method, listening_client_pattern, callback_messages_pattern, 2
            )
            check_no_messages_left(
                method, listening_client_sharded, callback_messages_sharded, 1
            )

        finally:
            client_cleanup(
                listening_client_exact, pub_sub_exact if cluster_mode else None
            )
            client_cleanup(publishing_client, None)
            client_cleanup(
                listening_client_pattern, pub_sub_pattern if cluster_mode else None
            )
            client_cleanup(
                listening_client_sharded, pub_sub_sharded if cluster_mode else None
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_two_publishing_clients_same_name(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
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
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []

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

            client_exact, client_pattern = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_exact, pub_sub_pattern
            )

            # Publish messages to each channel - both clients publishing
            for msg in [MESSAGE_EXACT, MESSAGE_PATTERN]:
                result = client_pattern.publish(msg, CHANNEL_NAME)
                if cluster_mode:
                    assert result == 2

            # allow the message to propagate
            time.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = get_message_by_method(method, client, callback, 1)  # type: ignore
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            check_no_messages_left(method, client_pattern, callback_messages_pattern, 2)
            check_no_messages_left(method, client_exact, callback_messages_exact, 2)

        finally:
            client_cleanup(client_exact, pub_sub_exact if cluster_mode else None)
            client_cleanup(client_pattern, pub_sub_pattern if cluster_mode else None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    def test_sync_pubsub_three_publishing_clients_same_name_with_sharded(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
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
            callback_messages_exact: List[PubSubMsg] = []
            callback_messages_pattern: List[PubSubMsg] = []
            callback_messages_sharded: List[PubSubMsg] = []

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

            client_exact, client_pattern = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_exact, pub_sub_pattern
            )
            client_sharded, client_dont_care = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_sharded
            )

            # Publish messages to each channel - both clients publishing
            assert (
                client_pattern.publish(MESSAGE_EXACT, CHANNEL_NAME) == publish_response
            )
            assert (
                client_sharded.publish(MESSAGE_PATTERN, CHANNEL_NAME)
                == publish_response
            )
            assert (
                cast(GlideClusterClient, client_exact).publish(
                    MESSAGE_SHARDED, CHANNEL_NAME, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            time.sleep(1)

            # Verify message for exact and pattern PUBSUB
            for client, callback, pattern in [  # type: ignore
                (client_exact, callback_messages_exact, None),
                (client_pattern, callback_messages_pattern, CHANNEL_NAME),
            ]:
                pubsub_msg = get_message_by_method(method, client, callback, 0)  # type: ignore

                pubsub_msg2 = get_message_by_method(method, client, callback, 1)  # type: ignore
                assert not pubsub_msg.message == pubsub_msg2.message
                assert pubsub_msg2.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.message in [MESSAGE_PATTERN, MESSAGE_EXACT]
                assert pubsub_msg.channel == pubsub_msg2.channel == CHANNEL_NAME
                assert pubsub_msg.pattern == pubsub_msg2.pattern == pattern

            msg = get_message_by_method(
                method, client_sharded, callback_messages_sharded, 0
            )
            assert msg.message == MESSAGE_SHARDED
            assert msg.channel == CHANNEL_NAME
            assert msg.pattern is None

            check_no_messages_left(method, client_pattern, callback_messages_pattern, 2)
            check_no_messages_left(method, client_exact, callback_messages_exact, 2)
            check_no_messages_left(method, client_sharded, callback_messages_sharded, 1)

        finally:
            client_cleanup(client_exact, pub_sub_exact if cluster_mode else None)
            client_cleanup(client_pattern, pub_sub_pattern if cluster_mode else None)
            client_cleanup(client_sharded, pub_sub_sharded if cluster_mode else None)
            client_cleanup(client_dont_care, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_pubsub_exact_max_size_message(
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
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = "1" * 512 * 1024 * 1024
        message2 = "2" * 512 * 1024 * 1024

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            channels={channel},
            timeout=30000,  # 30 seconds for large messages
        ) as (listening_client, publishing_client):
            result = publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1

            result = publishing_client.publish(message2, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            time.sleep(15)

            async_msg = listening_client.get_pubsub_message()
            assert async_msg.message == message
            assert async_msg.channel == channel
            assert async_msg.pattern is None

            sync_msg = listening_client.try_get_pubsub_message()
            assert sync_msg
            assert sync_msg.message == message2
            assert sync_msg.channel == channel
            assert sync_msg.pattern is None

            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                run_sync_func_with_timeout_in_thread(
                    lambda: listening_client.get_pubsub_message(), timeout=3.0
                )

            assert listening_client.try_get_pubsub_message() is None

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
    def test_sync_pubsub_sharded_max_size_message(
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
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = "1" * 512 * 1024 * 1024
        message2 = "2" * 512 * 1024 * 1024

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            sharded={channel},
        ) as (listening_client, publishing_client):
            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == 1
            )

            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    message2, channel, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            time.sleep(15)

            async_msg = listening_client.get_pubsub_message()
            sync_msg = listening_client.try_get_pubsub_message()
            assert sync_msg

            assert async_msg.message == message
            assert async_msg.channel == channel
            assert async_msg.pattern is None

            assert sync_msg.message == message2
            assert sync_msg.channel == channel
            assert sync_msg.pattern is None

            # assert there are no messages to read
            with pytest.raises(TimeoutError):
                run_sync_func_with_timeout_in_thread(
                    lambda: listening_client.get_pubsub_message(), timeout=3.0
                )

            assert listening_client.try_get_pubsub_message() is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_pubsub_exact_max_size_message_callback(
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
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = "0" * 12 * 1024 * 1024

        callback_messages: List[PubSubMsg] = []
        callback, context = new_message, callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            channels={channel},
            callback=callback,
            context=context,
            timeout=10000,
        ) as (listening_client, publishing_client):
            result = publishing_client.publish(message, channel)
            if cluster_mode:
                assert result == 1
            # allow the message to propagate
            time.sleep(15)

            assert len(callback_messages) == 1

            assert callback_messages[0].message == message
            assert callback_messages[0].channel == channel
            assert callback_messages[0].pattern is None

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
    def test_sync_pubsub_sharded_max_size_message_callback(
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
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = get_random_string(10)
        message = "0" * 512 * 1024 * 1024

        callback_messages: List[PubSubMsg] = []
        callback, context = new_message, callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            subscription_method,
            sharded={channel},
            callback=callback,
            context=context,
            timeout=10000,
        ) as (listening_client, publishing_client):
            assert (
                cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )
                == 1
            )

            # allow the message to propagate
            time.sleep(15)

            assert len(callback_messages) == 1

            assert callback_messages[0].message == message
            assert callback_messages[0].channel == channel
            assert callback_messages[0].pattern is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_resp2_raise_an_error(self, request, cluster_mode: bool):
        """Tests that when creating a resp2 client with PUBSUB - an error will be raised"""
        channel = get_random_string(5)

        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
        )

        with pytest.raises(ConfigurationError):
            create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub_exact, protocol=ProtocolVersion.RESP2
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_context_with_no_callback_raise_error(
        self, request, cluster_mode: bool
    ):
        """Tests that when creating a PUBSUB client in callback method with context but no callback raises an error"""
        channel = get_random_string(5)
        context: List[PubSubMsg] = []
        pub_sub_exact = create_pubsub_subscription(
            cluster_mode,
            {GlideClusterClientConfiguration.PubSubChannelModes.Exact: {channel}},
            {GlideClientConfiguration.PubSubChannelModes.Exact: {channel}},
            context=context,
        )

        with pytest.raises(ConfigurationError):
            create_two_clients_with_pubsub(request, cluster_mode, pub_sub_exact)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_channels(self, request, cluster_mode: bool):
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

            client = create_sync_client(request, cluster_mode)
            # Assert no channels exists yet
            assert client.pubsub_channels() == []

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

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            # Test pubsub_channels without pattern
            channels = client2.pubsub_channels()
            assert set(channels) == {channel1_bytes, channel2_bytes, channel3_bytes}

            # Test pubsub_channels with pattern
            channels_with_pattern = client2.pubsub_channels(pattern)
            assert set(channels_with_pattern) == {channel1_bytes, channel2_bytes}

            # Test with non-matching pattern
            non_matching_channels = client2.pubsub_channels("non_matching_*")
            assert len(non_matching_channels) == 0

        finally:
            client_cleanup(client1, pub_sub if cluster_mode else None)
            client_cleanup(client2, None)
            client_cleanup(client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_numpat(self, request, cluster_mode: bool):
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
            client = create_sync_client(request, cluster_mode)
            assert client.pubsub_numpat() == 0

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

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            # Test pubsub_numpat
            num_patterns = client2.pubsub_numpat()
            assert num_patterns == 2

        finally:
            client_cleanup(client1, pub_sub if cluster_mode else None)
            client_cleanup(client2, None)
            client_cleanup(client, None)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_numsub(self, request, cluster_mode: bool):
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
            client = create_sync_client(request, cluster_mode)
            assert client.pubsub_numsub([channel1, channel2, channel3]) == {
                channel1_bytes: 0,
                channel2_bytes: 0,
                channel3_bytes: 0,
            }

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub1, pub_sub2
            )
            client3, client4 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub3
            )

            # Test pubsub_numsub
            subscribers = client2.pubsub_numsub(
                [channel1_bytes, channel2_bytes, channel3_bytes, channel4_bytes]
            )
            assert subscribers == {
                channel1_bytes: 1,
                channel2_bytes: 2,
                channel3_bytes: 3,
                channel4_bytes: 0,
            }

            # Test pubsub_numsub with no channels
            empty_subscribers = client2.pubsub_numsub()
            assert empty_subscribers == {}

        finally:
            client_cleanup(client1, pub_sub1 if cluster_mode else None)
            client_cleanup(client2, pub_sub2 if cluster_mode else None)
            client_cleanup(client3, pub_sub3 if cluster_mode else None)
            client_cleanup(client4, None)
            client_cleanup(client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    def test_sync_pubsub_shardchannels(self, request, cluster_mode: bool):
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

            client = create_sync_client(request, cluster_mode)
            assert isinstance(client, GlideClusterClient)
            # Assert no sharded channels exist yet
            assert client.pubsub_shardchannels() == []

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

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            assert isinstance(client2, GlideClusterClient)

            # Test pubsub_shardchannels without pattern
            channels = client2.pubsub_shardchannels()
            assert set(channels) == {channel1_bytes, channel2_bytes, channel3_bytes}

            # Test pubsub_shardchannels with pattern
            channels_with_pattern = client2.pubsub_shardchannels(pattern)
            assert set(channels_with_pattern) == {channel1_bytes, channel2_bytes}

            # Test with non-matching pattern
            assert client2.pubsub_shardchannels("non_matching_*") == []

        finally:
            client_cleanup(client1, pub_sub if cluster_mode else None)
            client_cleanup(client2, None)
            client_cleanup(client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    def test_sync_pubsub_shardnumsub(self, request, cluster_mode: bool):
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
            client = create_sync_client(request, cluster_mode)

            assert isinstance(client, GlideClusterClient)
            assert client.pubsub_shardnumsub([channel1, channel2, channel3]) == {
                channel1_bytes: 0,
                channel2_bytes: 0,
                channel3_bytes: 0,
            }

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub1, pub_sub2
            )

            client3, client4 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub3
            )

            assert isinstance(client4, GlideClusterClient)

            # Test pubsub_shardnumsub
            subscribers = client4.pubsub_shardnumsub(
                [channel1, channel2, channel3, channel4]
            )
            assert subscribers == {
                channel1_bytes: 1,
                channel2_bytes: 2,
                channel3_bytes: 3,
                channel4_bytes: 0,
            }

            # Test pubsub_shardnumsub with no channels
            empty_subscribers = client4.pubsub_shardnumsub()
            assert empty_subscribers == {}

        finally:
            client_cleanup(client1, pub_sub1 if cluster_mode else None)
            client_cleanup(client2, pub_sub2 if cluster_mode else None)
            client_cleanup(client3, pub_sub3 if cluster_mode else None)
            client_cleanup(client4, None)
            client_cleanup(client, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    def test_sync_pubsub_channels_and_shardchannels_separation(
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

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub
            )

            assert isinstance(client2, GlideClusterClient)
            # Test pubsub_channels
            assert client2.pubsub_channels() == [regular_channel_bytes]

            # Test pubsub_shardchannels
            assert client2.pubsub_shardchannels() == [shard_channel_bytes]

        finally:
            client_cleanup(client1, pub_sub if cluster_mode else None)
            client_cleanup(client2, None)

    @pytest.mark.skip_if_version_below("7.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    def test_sync_pubsub_numsub_and_shardnumsub_separation(
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

            client1, client2 = create_two_clients_with_pubsub(
                request, cluster_mode, pub_sub1, pub_sub2
            )

            assert isinstance(client2, GlideClusterClient)

            # Test pubsub_numsub
            regular_subscribers = client2.pubsub_numsub(
                [regular_channel_bytes, shard_channel_bytes]
            )

            assert regular_subscribers == {
                regular_channel_bytes: 2,
                shard_channel_bytes: 0,
            }

            # Test pubsub_shardnumsub
            shard_subscribers = client2.pubsub_shardnumsub(
                [regular_channel_bytes, shard_channel_bytes]
            )

            assert shard_subscribers == {
                regular_channel_bytes: 0,
                shard_channel_bytes: 2,
            }

        finally:
            client_cleanup(client1, pub_sub1 if cluster_mode else None)
            client_cleanup(client2, pub_sub2 if cluster_mode else None)

    def test_sync_clients_support_pubsub_reconciliation_interval(self):
        """
        Test that GlideClientConfiguration accepts pubsub_reconciliation_interval
        now that dynamic pubsub is supported in sync clients.
        """
        addresses = [NodeAddress("localhost", 6379)]

        # Create advanced config with pubsub_reconciliation_interval
        advanced_config = AdvancedGlideClientConfiguration(
            pubsub_reconciliation_interval=500,
        )

        # Verify that creating the config succeeds
        config = GlideClientConfiguration(
            addresses=addresses,
            advanced_config=advanced_config,
        )
        assert config.advanced_config.pubsub_reconciliation_interval == 500

        # Create advanced cluster config with pubsub_reconciliation_interval
        advanced_cluster_config = AdvancedGlideClusterClientConfiguration(
            pubsub_reconciliation_interval=500,
        )

        # Verify that creating the config succeeds
        cluster_config = GlideClusterClientConfiguration(
            addresses=addresses,
            advanced_config=advanced_cluster_config,
        )
        assert cluster_config.advanced_config.pubsub_reconciliation_interval == 500

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_dynamic_subscribe_and_get_subscriptions(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test dynamic subscribe methods and get_subscriptions().
        """
        client = create_sync_client(request, cluster_mode)
        try:

            # Subscribe to channels
            exact_channel = "test_exact_channel"
            pattern = "test_pattern_*"

            client.subscribe({exact_channel})
            client.psubscribe({pattern})

            # Sharded pubsub requires Redis 7.0+
            sharded_channel = None
            if cluster_mode and not sync_check_if_server_version_lt(client, "7.0.0"):
                sharded_channel = "test_sharded_channel"
                cast(GlideClusterClient, client).ssubscribe({sharded_channel})

            # Wait for subscriptions to be registered
            wait_for_subscription_state(
                client,
                expected_channels={exact_channel},
                expected_patterns={pattern},
                expected_sharded={sharded_channel} if sharded_channel else None,
                timeout_sec=3.0,
            )

            # Get subscriptions
            state = client.get_subscriptions()

            # Verify subscriptions
            modes = (
                GlideClusterClientConfiguration.PubSubChannelModes
                if cluster_mode
                else GlideClientConfiguration.PubSubChannelModes
            )

            assert exact_channel in state.desired_subscriptions[modes.Exact]
            assert pattern in state.desired_subscriptions[modes.Pattern]
            assert exact_channel in state.actual_subscriptions[modes.Exact]
            assert pattern in state.actual_subscriptions[modes.Pattern]

            if sharded_channel:
                assert sharded_channel in state.desired_subscriptions[modes.Sharded]  # type: ignore[union-attr,arg-type]
                assert sharded_channel in state.actual_subscriptions[modes.Sharded]  # type: ignore[union-attr,arg-type]

            # Unsubscribe
            client.unsubscribe({exact_channel}, timeout_ms=5000)
            client.punsubscribe({pattern}, timeout_ms=5000)
            if sharded_channel:
                cast(GlideClusterClient, client).sunsubscribe(
                    {sharded_channel}, timeout_ms=5000
                )

            # Verify unsubscribed - wait for state to update
            wait_for_subscription_state(
                client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set() if sharded_channel else None,
                timeout_sec=3.0,
            )

            state = client.get_subscriptions()
            assert exact_channel not in state.actual_subscriptions[modes.Exact]
            assert pattern not in state.actual_subscriptions[modes.Pattern]
            if sharded_channel:
                assert sharded_channel not in state.actual_subscriptions[modes.Sharded]  # type: ignore[union-attr,arg-type]

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_mixed_subscription_methods_all_types(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test mixing Config and Blocking subscriptions across all subscription types
        (Exact, Pattern, and Sharded for cluster mode).

        Note: Sync client doesn't support Lazy methods, so we only test Config and Blocking.
        """
        listening_client, publishing_client = None, None
        try:
            # Create unique names for each combination
            prefix = "mixed_sub_types_sync"

            # Exact channels
            exact_config = f"exact_config_{prefix}"
            exact_blocking = f"exact_blocking_{prefix}"

            # Pattern subscriptions
            pattern_config = f"pattern_config_{prefix}_*"
            pattern_blocking = f"pattern_blocking_{prefix}_*"

            # Sharded channels (cluster only, Redis 7.0+)
            # Create a temporary client to check version
            temp_client = create_sync_client(request, cluster_mode)
            supports_sharded = cluster_mode and not sync_check_if_server_version_lt(
                temp_client, "7.0.0"
            )
            temp_client.close()

            sharded_config = f"sharded_config_{prefix}" if supports_sharded else None
            sharded_blocking = (
                f"sharded_blocking_{prefix}" if supports_sharded else None
            )

            # Create client with Config subscriptions
            sharded_set = {sharded_config} if sharded_config else None
            pubsub_config = create_simple_pubsub_config(
                cluster_mode,
                channels={exact_config},
                patterns={pattern_config},
                sharded=sharded_set,
            )

            listening_client = create_sync_client(
                request,
                cluster_mode,
                cluster_mode_pubsub=pubsub_config if cluster_mode else None,
                standalone_mode_pubsub=pubsub_config if not cluster_mode else None,
            )
            publishing_client = create_sync_client(request, cluster_mode)

            # Wait for config subscriptions
            time.sleep(0.5)

            # Add Blocking subscriptions
            listening_client.subscribe({exact_blocking}, timeout_ms=5000)
            listening_client.psubscribe({pattern_blocking}, timeout_ms=5000)
            if cluster_mode and sharded_blocking:
                cast(GlideClusterClient, listening_client).ssubscribe(
                    {sharded_blocking}, timeout_ms=5000
                )

            # Wait for all subscriptions
            all_exact = {exact_config, exact_blocking}
            all_patterns = {pattern_config, pattern_blocking}
            all_sharded = (
                {sharded_config, sharded_blocking}
                if (cluster_mode and sharded_config and sharded_blocking)
                else None
            )

            wait_for_subscription_state(
                listening_client,
                expected_channels=all_exact,
                expected_patterns=all_patterns,
                expected_sharded=all_sharded,
                timeout_sec=3.0,
            )

            # Verify all subscriptions are active
            state = listening_client.get_subscriptions()
            modes = (
                GlideClusterClientConfiguration.PubSubChannelModes
                if cluster_mode
                else GlideClientConfiguration.PubSubChannelModes
            )

            assert all_exact.issubset(state.actual_subscriptions[modes.Exact])
            assert all_patterns.issubset(state.actual_subscriptions[modes.Pattern])

            if cluster_mode and sharded_config and sharded_blocking:
                assert all_sharded.issubset(state.actual_subscriptions[modes.Sharded])  # type: ignore[union-attr,arg-type]

            # Publish messages to all channels
            message = "test_message"
            publishing_client.publish(message, exact_config)
            publishing_client.publish(message, exact_blocking)
            publishing_client.publish(message, pattern_config.replace("*", "test"))
            publishing_client.publish(message, pattern_blocking.replace("*", "test"))

            if cluster_mode and sharded_config and sharded_blocking:
                cast(GlideClusterClient, publishing_client).publish(
                    message, sharded_config, sharded=True  # type: ignore[union-attr,arg-type]
                )
                cast(GlideClusterClient, publishing_client).publish(
                    message, sharded_blocking, sharded=True  # type: ignore[union-attr,arg-type]
                )

            # Verify messages received
            time.sleep(0.5)
            received_count = 0
            while True:
                msg = listening_client.try_get_pubsub_message()
                if msg is None:
                    break
                received_count += 1

            # Expected count: 4 for exact+pattern, +2 if sharded is supported
            expected_count = 4
            if cluster_mode and sharded_config and sharded_blocking:
                expected_count = 6
            assert received_count == expected_count

        finally:
            if listening_client:
                listening_client.close()
            if publishing_client:
                publishing_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_subscribe_with_timeout(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test subscribe with timeout parameter.
        """
        client = create_sync_client(request, cluster_mode)
        try:

            channel = "test_timeout_channel"

            # Subscribe with timeout (should succeed)
            client.subscribe({channel}, timeout_ms=5000)

            # Verify subscription
            state = client.get_subscriptions()
            modes = (
                GlideClusterClientConfiguration.PubSubChannelModes
                if cluster_mode
                else GlideClientConfiguration.PubSubChannelModes
            )
            assert channel in state.actual_subscriptions[modes.Exact]

        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_subscribe_empty_set_raises_error(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that subscribing with an empty set raises an error.
        """
        client = create_sync_client(request, cluster_mode)
        try:

            # Test subscribe with empty set
            with pytest.raises(RequestError) as exc_info:
                client.subscribe(set())
            assert "No channels provided for subscription" in str(exc_info.value)

            # Test psubscribe with empty set
            with pytest.raises(RequestError) as exc_info:
                client.psubscribe(set())
            assert "No channels provided for subscription" in str(exc_info.value)

            # Test ssubscribe with empty set (cluster only)
            if cluster_mode:
                with pytest.raises(RequestError) as exc_info:
                    cast(GlideClusterClient, client).ssubscribe(set())
                assert "No channels provided for subscription" in str(exc_info.value)

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_unsubscribe_all(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test unsubscribing from all channels using unsubscribe with no arguments.
        """
        client = create_sync_client(request, cluster_mode)
        try:

            # Subscribe to multiple channels
            channels = {"channel1", "channel2", "channel3"}
            patterns = {"pattern1_*", "pattern2_*"}

            client.subscribe(channels)
            client.psubscribe(patterns)

            # Sharded pubsub requires Redis 7.0+
            supports_sharded = cluster_mode and not sync_check_if_server_version_lt(
                client, "7.0.0"
            )
            if supports_sharded:
                sharded = {"sharded1", "sharded2"}
                cast(GlideClusterClient, client).ssubscribe(sharded)

            # Verify subscriptions
            time.sleep(0.5)
            state = client.get_subscriptions()
            modes = (
                GlideClusterClientConfiguration.PubSubChannelModes
                if cluster_mode
                else GlideClientConfiguration.PubSubChannelModes
            )

            assert len(state.actual_subscriptions[modes.Exact]) == 3
            assert len(state.actual_subscriptions[modes.Pattern]) == 2
            if supports_sharded:
                assert len(state.actual_subscriptions[modes.Sharded]) == 2  # type: ignore[union-attr,arg-type]

            # Unsubscribe from all
            client.unsubscribe()
            client.punsubscribe()
            if supports_sharded:
                cast(GlideClusterClient, client).sunsubscribe()

            # Verify all unsubscribed
            time.sleep(0.5)
            state = client.get_subscriptions()
            assert len(state.actual_subscriptions[modes.Exact]) == 0
            assert len(state.actual_subscriptions[modes.Pattern]) == 0
            if supports_sharded or cluster_mode:
                assert len(state.actual_subscriptions[modes.Sharded]) == 0  # type: ignore[union-attr,arg-type]

        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_subscription_metrics(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that subscription metrics are available in get_statistics().
        """
        # Create client with pubsub enabled (no callback needed for reconciliation)
        client = create_sync_pubsub_client(request, cluster_mode)
        try:
            # Use lazy subscribe to trigger reconciliation
            client.subscribe_lazy({"test_metrics_channel"})

            # Wait for subscription to actually be applied (reconciliation completes)
            wait_for_subscription_state(
                client,
                expected_channels={"test_metrics_channel"},
                timeout_sec=5.0,
            )

            # Now wait for the NEXT reconciliation cycle to check sync state and update timestamp
            # Reconciliation runs every 3 seconds, so we may need to wait up to 3 more seconds
            # Plus some buffer for processing time. Cluster mode may take significantly longer.
            last_sync = 0
            stats = {}
            for i in range(30):
                time.sleep(1)
                stats = client.get_statistics()
                last_sync = int(stats.get("subscription_last_sync_timestamp", "0"))
                print(f"[{i+1}s] Waiting for timestamp update: last_sync={last_sync}")
                if last_sync > 0:
                    print(f"Timestamp updated after {i+1} seconds")
                    break

            # Check metrics
            assert (
                "subscription_out_of_sync_count" in stats
            ), f"subscription_out_of_sync_count not in stats. Available keys: {list(stats.keys())}"
            assert (
                "subscription_last_sync_timestamp" in stats
            ), f"subscription_last_sync_timestamp not in stats. Available keys: {list(stats.keys())}"

            # Verify timestamp is set
            assert (
                last_sync > 0
            ), f"Timestamp should be set after subscription within 30s, got {last_sync}"

        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_negative_timeout_raises_error(
        self,
        request,
        cluster_mode: bool,
    ):
        """Test that negative timeout raises ValueError."""
        client = create_sync_client(request, cluster_mode)
        try:
            # Test subscribe with negative timeout
            with pytest.raises(ValueError) as exc_info:
                client.subscribe({"channel1"}, timeout_ms=-1)
            assert "Timeout must be non-negative" in str(exc_info.value)
            assert "got: -1" in str(exc_info.value)

            # Test psubscribe with negative timeout
            with pytest.raises(ValueError) as exc_info:
                client.psubscribe({"pattern*"}, timeout_ms=-100)
            assert "Timeout must be non-negative" in str(exc_info.value)
            assert "got: -100" in str(exc_info.value)

            # Test unsubscribe with negative timeout
            with pytest.raises(ValueError) as exc_info:
                client.unsubscribe({"channel1"}, timeout_ms=-5)
            assert "Timeout must be non-negative" in str(exc_info.value)
            assert "got: -5" in str(exc_info.value)

            # Test punsubscribe with negative timeout
            with pytest.raises(ValueError) as exc_info:
                client.punsubscribe({"pattern*"}, timeout_ms=-10)
            assert "Timeout must be non-negative" in str(exc_info.value)
            assert "got: -10" in str(exc_info.value)

            # Test ssubscribe with negative timeout (cluster only)
            if cluster_mode:
                with pytest.raises(ValueError) as exc_info:
                    cast(GlideClusterClient, client).ssubscribe(
                        {"shard1"}, timeout_ms=-20
                    )
                assert "Timeout must be non-negative" in str(exc_info.value)
                assert "got: -20" in str(exc_info.value)

                # Test sunsubscribe with negative timeout (cluster only)
                with pytest.raises(ValueError) as exc_info:
                    cast(GlideClusterClient, client).sunsubscribe(
                        {"shard1"}, timeout_ms=-30
                    )
                assert "Timeout must be non-negative" in str(exc_info.value)
                assert "got: -30" in str(exc_info.value)

        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_unsubscribe_exact_channel(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic unsubscription from exact channels using lazy and blocking APIs.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = "channel"
        message1 = "exact_message_1"
        message2 = "exact_message_2"

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            SubscriptionMethod.Config,
            channels={channel},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Verify subscription is active
            wait_for_subscription_state(
                listening_client,
                expected_channels={channel},
                timeout_sec=3.0,
            )
            state = listening_client.get_subscriptions()
            assert (
                channel
                in state.actual_subscriptions[get_pubsub_modes(listening_client).Exact]
            )

            publishing_client.publish(message1, channel)
            time.sleep(1)

            # Get message
            pubsub_msg = get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            # Unsubscribe
            if subscription_method == SubscriptionMethod.Lazy:
                listening_client.unsubscribe_lazy({channel})
            else:  # Blocking
                listening_client.unsubscribe({channel}, timeout_ms=5000)

            # Verify unsubscribed
            wait_for_subscription_state(
                listening_client,
                expected_channels=set(),
                timeout_sec=3.0,
            )
            state = listening_client.get_subscriptions()
            assert (
                channel
                not in state.actual_subscriptions[
                    get_pubsub_modes(listening_client).Exact
                ]
            )

            # Publish second message - should not be received
            publishing_client.publish(message2, channel)
            time.sleep(1)

            # Check no messages left
            check_no_messages_left(method, listening_client, callback_messages, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_subscribe_empty_set_raises_error(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that subscribing with an empty set raises an error for dynamic subscription methods.
        """
        if subscription_method == SubscriptionMethod.Config:
            pytest.skip("Config method allows empty sets")

        client = None
        try:
            client = create_sync_client(request, cluster_mode)

            if subscription_method == SubscriptionMethod.Lazy:
                with pytest.raises(RequestError):
                    client.subscribe_lazy(set())
                with pytest.raises(RequestError):
                    client.psubscribe_lazy(set())
                if cluster_mode:
                    with pytest.raises(RequestError):
                        cast(GlideClusterClient, client).ssubscribe_lazy(set())
            else:  # Blocking
                with pytest.raises(RequestError):
                    client.subscribe(set(), 5000)
                with pytest.raises(RequestError):
                    client.psubscribe(set(), 5000)
                if cluster_mode:
                    with pytest.raises(RequestError):
                        cast(GlideClusterClient, client).ssubscribe(set(), 5000)

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_config_subscription_with_empty_set_is_allowed(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that Config subscription method with empty sets is a silent no-op.
        """
        client = None
        try:
            # Create client with empty subscription config - should not error
            pub_sub = create_simple_pubsub_config(
                cluster_mode, channels=set(), patterns=set()
            )
            client, _ = create_two_clients_with_pubsub(
                request, cluster_mode, client1_pubsub=pub_sub
            )

            # Should be able to get subscriptions (empty)
            state = client.get_subscriptions()
            modes = get_pubsub_modes(client)
            assert len(state.desired_subscriptions.get(modes.Exact, set())) == 0
            assert len(state.desired_subscriptions.get(modes.Pattern, set())) == 0

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def _publish_messages(
        self,
        publishing_client,
        channel: str,
        pattern_channel: str,
        sharded_channel: Optional[str],
        cluster_mode: bool,
    ):
        """Helper to publish messages to all channels."""
        publishing_client.publish("msg1", channel)
        time.sleep(0.5)

        publishing_client.publish("msg2", pattern_channel)
        time.sleep(0.5)

        if cluster_mode and sharded_channel:
            cast(GlideClusterClient, publishing_client).publish(
                "msg3", sharded_channel, sharded=True
            )
            time.sleep(0.5)

    def _verify_messages(
        self,
        listening_client,
        method: MethodTesting,
        callback_messages: List[PubSubMsg],
        cluster_mode: bool,
        sharded_channel: Optional[str],
    ):
        """Helper to verify messages were received."""
        if method == MethodTesting.Callback:
            expected_count = 3 if (cluster_mode and sharded_channel) else 2
            assert len(callback_messages) >= expected_count
        else:
            msg1 = (
                listening_client.try_get_pubsub_message()
                if method == MethodTesting.Sync
                else listening_client.get_pubsub_message()
            )
            assert msg1 is not None

            msg2 = (
                listening_client.try_get_pubsub_message()
                if method == MethodTesting.Sync
                else listening_client.get_pubsub_message()
            )
            assert msg2 is not None

            # Read third message if sharded channel was used
            if cluster_mode and sharded_channel:
                msg3 = (
                    listening_client.try_get_pubsub_message()
                    if method == MethodTesting.Sync
                    else listening_client.get_pubsub_message()
                )
                assert msg3 is not None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method", [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback]
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking],
    )
    def test_sync_lazy_client_multiple_subscription_types(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test multiple subscription types (exact, pattern, and sharded for cluster) with a lazy client.
        """
        listening_client, publishing_client = None, None
        try:
            channel = "exact_channel"
            pattern = "pattern_*"
            pattern_channel = "pattern_match"

            # Check if sharded pubsub is supported (Redis 7.0+ in cluster mode)
            sharded_channel: Optional[str] = None
            if cluster_mode:
                temp_client = create_sync_client(request, cluster_mode)
                try:
                    if not sync_check_if_server_version_lt(temp_client, "7.0.0"):
                        sharded_channel = "sharded_channel"
                finally:
                    temp_client.close()

            callback, context = None, None
            callback_messages: List[PubSubMsg] = []
            if method == MethodTesting.Callback:
                callback = new_message
                context = callback_messages

            listening_client = create_sync_pubsub_client(
                request, cluster_mode, callback=callback, context=context
            )
            publishing_client = create_sync_client(request, cluster_mode)

            sync_subscribe_by_method(
                listening_client,
                subscription_method,
                cluster_mode,
                channels={channel},
                patterns={pattern},
                sharded={sharded_channel} if sharded_channel else None,
            )

            time.sleep(1)

            self._publish_messages(
                publishing_client,
                channel,
                pattern_channel,
                sharded_channel,
                cluster_mode,
            )

            self._verify_messages(
                listening_client,
                method,
                callback_messages,
                cluster_mode,
                sharded_channel,
            )

        finally:
            if listening_client:
                listening_client.close()
            if publishing_client:
                publishing_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_reconciliation_interval_config(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that pubsub_reconciliation_interval controls reconciliation frequency.

        Configures a 1 second interval, then measures the actual time between
        two consecutive reconciliation events by polling the sync timestamp.
        Verifies the interval is within tolerance (minimum 100ms, maximum 1.5x interval).
        """
        client = None
        try:
            interval_ms = 1000
            poll_interval_s = 0.1

            client = create_sync_pubsub_client(
                request, cluster_mode, reconciliation_interval_ms=interval_ms
            )

            def poll_for_timestamp_change(
                previous_ts: int, timeout_s: float = 5.0
            ) -> int:
                """Poll until sync timestamp changes, return new timestamp."""
                import time

                start = time.time()
                while (time.time() - start) < timeout_s:
                    stats = client.get_statistics()
                    current_ts = int(stats.get("subscription_last_sync_timestamp", "0"))
                    if current_ts != previous_ts:
                        return current_ts
                    time.sleep(poll_interval_s)
                raise TimeoutError(
                    f"Sync timestamp did not change within {timeout_s}s. Previous: {previous_ts}"
                )

            initial_stats = client.get_statistics()
            initial_ts = int(initial_stats.get("subscription_last_sync_timestamp", "0"))

            # Wait for first sync event
            first_sync_ts = poll_for_timestamp_change(initial_ts)

            # Wait for second sync event
            second_sync_ts = poll_for_timestamp_change(first_sync_ts)

            actual_interval_ms = second_sync_ts - first_sync_ts

            # Assert interval is within tolerance: minimum 100ms, maximum 1.5x interval
            # Matches async Python behavior but with Go/Java minimum bound
            assert 100 <= actual_interval_ms <= interval_ms * 1.5, (
                f"Reconciliation interval ({actual_interval_ms}ms) should be between "
                f"100ms and {interval_ms * 1.5}ms"
            )

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_lazy_vs_blocking_timeout(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Test that lazy and blocking subscribe both work with dynamic subscriptions.
        """
        client = None
        try:
            # Create client with pubsub enabled but without callback
            client = create_sync_pubsub_client(request, cluster_mode)

            # Lazy should succeed (doesn't wait)
            client.subscribe_lazy({"channel1"})

            # Blocking should also succeed with dynamic subscriptions
            client.subscribe({"channel2"}, timeout_ms=5000)

            # Verify both subscriptions are in desired state
            state = client.get_subscriptions()
            PubSubChannelModes = (
                GlideClusterClientConfiguration.PubSubChannelModes
                if cluster_mode
                else GlideClientConfiguration.PubSubChannelModes
            )
            desired = state.desired_subscriptions.get(PubSubChannelModes.Exact, set())
            assert "channel1" in desired
            assert "channel2" in desired

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_sync_pubsub_callback_only_raises_error_on_get_methods(
        self,
        request,
        cluster_mode: bool,
    ):
        """
        Tests that when a client is configured with only a callback (no polling),
        calling get_pubsub_message() or try_get_pubsub_message() raises ConfigurationError.
        """
        listening_client, publishing_client = None, None
        try:
            callback_messages: List[PubSubMsg] = []

            # Create client with callback only
            listening_client = create_sync_pubsub_client(
                request, cluster_mode, callback=new_message, context=callback_messages
            )
            publishing_client = create_sync_client(request, cluster_mode)

            # Subscribe
            listening_client.subscribe_lazy({"test_channel"})
            time.sleep(1)
            # Publish message
            publishing_client.publish("test_message", "test_channel")
            time.sleep(1)

            # Verify message received via callback
            assert len(callback_messages) >= 1

            # Try to call get methods - should raise ConfigurationError
            with pytest.raises(ConfigurationError):
                listening_client.get_pubsub_message()

            with pytest.raises(ConfigurationError):
                listening_client.try_get_pubsub_message()

        finally:
            if listening_client:
                listening_client.close()
            if publishing_client:
                publishing_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_punsubscribe_pattern(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic pattern unsubscription using lazy and blocking APIs.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        pattern = "news_punsubscribe_test.*"
        channel = "news_punsubscribe_test.sports"
        message1 = "message_before_unsub"
        message2 = "message_after_unsub"

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            SubscriptionMethod.Config,
            patterns={pattern},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Verify subscription is active
            wait_for_subscription_state(
                listening_client,
                expected_patterns={pattern},
                timeout_sec=3.0,
            )
            state = listening_client.get_subscriptions()
            assert (
                pattern
                in state.actual_subscriptions[
                    get_pubsub_modes(listening_client).Pattern
                ]
            )

            # Publish first message
            publishing_client.publish(message1, channel)
            time.sleep(1)

            # Get message
            pubsub_msg = get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            # Unsubscribe from pattern
            if subscription_method == SubscriptionMethod.Lazy:
                listening_client.punsubscribe_lazy({pattern})
            else:  # Blocking
                listening_client.punsubscribe({pattern}, timeout_ms=5000)

            # Verify unsubscribed
            wait_for_subscription_state(
                listening_client,
                expected_patterns=set(),
                timeout_sec=3.0,
            )
            state = listening_client.get_subscriptions()
            assert (
                pattern
                not in state.actual_subscriptions[
                    get_pubsub_modes(listening_client).Pattern
                ]
            )

            # Publish second message - should not be received
            publishing_client.publish(message2, channel)
            time.sleep(1)

            # Check no messages left
            check_no_messages_left(method, listening_client, callback_messages, 1)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize(
        "method",
        [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_sunsubscribe_sharded_channel(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test basic sharded channel unsubscription.
        """
        if not cluster_mode:
            pytest.skip("Sharded channels only available in cluster mode")

        # Check Redis version
        temp_client = create_sync_client(request, cluster_mode)
        try:
            if sync_check_if_server_version_lt(temp_client, "7.0.0"):
                pytest.skip("Sharded pubsub requires Redis 7.0+")
        finally:
            temp_client.close()

        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = "sharded_channel_test"
        message1 = "message_before_unsub"
        message2 = "message_after_unsub"

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            SubscriptionMethod.Config,
            sharded={channel},
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Verify subscription is active
            wait_for_subscription_state(
                listening_client,
                expected_sharded={channel},
                timeout_sec=3.0,
            )
            state = listening_client.get_subscriptions()
            assert (
                channel
                in state.actual_subscriptions[
                    get_pubsub_modes(listening_client).Sharded
                ]
            )

            # Publish first message
            cast(GlideClusterClient, publishing_client).publish(
                message1, channel, sharded=True
            )
            time.sleep(1)

            # Get message
            pubsub_msg = get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message1

            # Unsubscribe
            if subscription_method == SubscriptionMethod.Lazy:
                cast(GlideClusterClient, listening_client).sunsubscribe_lazy({channel})
            else:  # Blocking
                cast(GlideClusterClient, listening_client).sunsubscribe(
                    {channel}, timeout_ms=5000
                )

            # Verify unsubscribed
            wait_for_subscription_state(
                listening_client,
                expected_sharded=set(),
                timeout_sec=3.0,
            )
            state = listening_client.get_subscriptions()
            assert (
                channel
                not in state.actual_subscriptions[
                    get_pubsub_modes(listening_client).Sharded
                ]
            )

            # Publish second message - should not be received
            cast(GlideClusterClient, publishing_client).publish(
                message2, channel, sharded=True
            )
            time.sleep(1)

            # Check no messages left
            check_no_messages_left(method, listening_client, callback_messages, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_unsubscribe_all_subscription_types(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test unsubscribing from all subscription types at once.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = "exact_channel"
        pattern = "pattern_*"
        sharded = None

        # Check if sharded pubsub is supported
        if cluster_mode:
            temp_client = create_sync_client(request, cluster_mode)
            try:
                if not sync_check_if_server_version_lt(temp_client, "7.0.0"):
                    sharded = "sharded_channel"
            finally:
                temp_client.close()

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            SubscriptionMethod.Config,
            channels={channel},
            patterns={pattern},
            sharded={sharded} if sharded else None,
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Verify subscriptions are active
            expected_sharded_set = {sharded} if cluster_mode and sharded else None
            wait_for_subscription_state(
                listening_client,
                expected_channels={channel},
                expected_patterns={pattern},
                expected_sharded=expected_sharded_set,
                timeout_sec=3.0,
            )

            # Unsubscribe from all
            if subscription_method == SubscriptionMethod.Lazy:
                listening_client.unsubscribe_lazy()
                listening_client.punsubscribe_lazy()
            else:  # Blocking
                listening_client.unsubscribe(timeout_ms=5000)
                listening_client.punsubscribe(timeout_ms=5000)

            if cluster_mode:
                if subscription_method == SubscriptionMethod.Lazy:
                    cast(GlideClusterClient, listening_client).sunsubscribe_lazy()
                else:  # Blocking
                    cast(GlideClusterClient, listening_client).sunsubscribe(
                        timeout_ms=5000
                    )

            # Wait for unsubscriptions to complete
            wait_for_subscription_state(
                listening_client,
                expected_channels=set(),
                expected_patterns=set(),
                expected_sharded=set() if cluster_mode else None,
                timeout_sec=3.0,
            )

            # Verify all unsubscribed
            state = listening_client.get_subscriptions()
            modes = get_pubsub_modes(listening_client)
            assert len(state.desired_subscriptions.get(modes.Exact, set())) == 0
            assert len(state.desired_subscriptions.get(modes.Pattern, set())) == 0
            if cluster_mode and hasattr(modes, "Sharded"):
                assert len(state.desired_subscriptions.get(modes.Sharded, set())) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Lazy,
        ],
    )
    def test_sync_subscription_sync_timestamp_metric_on_success(
        self,
        request,
        cluster_mode: bool,
        subscription_method: SubscriptionMethod,
    ):
        """
        Test that sync timestamp updates on successful subscription.
        Only tests lazy subscribe since blocking subscribe doesn't trigger async reconciliation.
        """
        listening_client, publishing_client = None, None
        try:
            channel1 = "channel1_sync_timestamp"
            channel2 = "channel2_sync_timestamp"

            # No callback needed - reconciliation works automatically
            listening_client = create_sync_pubsub_client(request, cluster_mode)
            publishing_client = create_sync_client(request, cluster_mode)

            initial_stats = listening_client.get_statistics()
            initial_timestamp = int(
                initial_stats.get("subscription_last_sync_timestamp", "0")
            )
            print(f"Initial timestamp: {initial_timestamp}")

            # Subscribe to first channel
            if subscription_method == SubscriptionMethod.Lazy:
                listening_client.subscribe_lazy({channel1})
            else:
                listening_client.subscribe({channel1}, timeout_ms=5000)

            print(f"Subscribed to {channel1}, waiting for state...")
            wait_for_subscription_state(
                listening_client,
                expected_channels={channel1},
                timeout_sec=3.0,
            )
            print(f"Subscription to {channel1} confirmed")

            # Subscribe to second channel - this ensures we will have at least 1 full reconciliation cycle
            # and 1 successful timestamp update before checking it
            if subscription_method == SubscriptionMethod.Lazy:
                listening_client.subscribe_lazy({channel2})
            else:
                listening_client.subscribe({channel2}, timeout_ms=5000)

            print(f"Subscribed to {channel2}, waiting for state...")
            # Wait for both subscriptions to be applied
            wait_for_subscription_state(
                listening_client,
                expected_channels={channel1, channel2},
                timeout_sec=3.0,
            )
            print("Both subscriptions confirmed")

            # Wait for next reconciliation cycle to confirm sync and update timestamp (up to 20 seconds)
            # The timestamp is only updated when the system is synchronized (desired == actual)
            timestamp_after = 0
            for i in range(20):
                time.sleep(1)
                stats_after = listening_client.get_statistics()
                timestamp_after = int(
                    stats_after.get("subscription_last_sync_timestamp", "0")
                )
                state = listening_client.get_subscriptions()
                desired_count = len(
                    state.desired_subscriptions.get(
                        get_pubsub_modes(listening_client).Exact, set()
                    )
                )
                actual_count = len(
                    state.actual_subscriptions.get(
                        get_pubsub_modes(listening_client).Exact, set()
                    )
                )
                print(
                    f"[{i+1}s] timestamp={timestamp_after}, desired={desired_count}, actual={actual_count}"
                )
                if timestamp_after > 0:
                    print(f"Timestamp set after {i+1} seconds")
                    break

            # Just verify that timestamp is set (non-zero), don't compare to initial
            # The initial timestamp might be from an empty subscription set, and the new timestamp
            # should be from the current subscription set
            assert (
                timestamp_after > 0
            ), f"Timestamp should be set after subscriptions, got {timestamp_after}"

        finally:
            if listening_client:
                listening_client.close()
            if publishing_client:
                publishing_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "method",
        [MethodTesting.Async, MethodTesting.Sync, MethodTesting.Callback],
    )
    @pytest.mark.parametrize(
        "subscription_method",
        [SubscriptionMethod.Lazy, SubscriptionMethod.Blocking],
    )
    def test_sync_pubsub_exact_happy_path_custom_command(
        self,
        request,
        cluster_mode: bool,
        method: MethodTesting,
        subscription_method: SubscriptionMethod,
    ):
        """
        Tests the basic happy path for exact PUBSUB functionality using custom commands.
        """
        from tests.sync_tests.conftest import sync_pubsub_test_clients

        channel = "test_exact_channel_custom"
        message = "test_exact_message_custom"

        callback, context = None, None
        callback_messages: List[PubSubMsg] = []
        if method == MethodTesting.Callback:
            callback = new_message
            context = callback_messages

        with sync_pubsub_test_clients(
            request,
            cluster_mode,
            SubscriptionMethod.Config,
            callback=callback,
            context=context,
        ) as (listening_client, publishing_client):
            # Subscribe using custom_command
            if subscription_method == SubscriptionMethod.Lazy:
                cmd = ["SUBSCRIBE", channel]
            else:
                cmd = ["SUBSCRIBE_BLOCKING", channel, "5000"]

            result = listening_client.custom_command(cmd)
            assert result is None

            # Verify subscription
            wait_for_subscription_state(
                listening_client,
                expected_channels={channel},
                timeout_sec=3.0,
            )

            publishing_client.publish(message, channel)
            time.sleep(1)

            pubsub_msg = get_message_by_method(
                method, listening_client, callback_messages, 0
            )
            assert pubsub_msg.message == message
            assert pubsub_msg.channel == channel
            assert pubsub_msg.pattern is None

            check_no_messages_left(method, listening_client, callback_messages, 1)

            # Unsubscribe using custom_command
            if subscription_method == SubscriptionMethod.Lazy:
                unsub_cmd = ["UNSUBSCRIBE", channel]
            else:
                unsub_cmd = ["UNSUBSCRIBE_BLOCKING", channel, "5000"]

            result = listening_client.custom_command(unsub_cmd)
            assert result is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_resubscribe_after_connection_kill_exact_channels(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test that exact channel subscriptions are automatically restored after connection is killed.
        """
        client = None
        try:
            channel = "test_channel_reconnect"

            client = create_sync_pubsub_client(
                request, cluster_mode, channels={channel}
            )

            # Verify initial subscription
            wait_for_subscription_state(
                client, expected_channels={channel}, timeout_sec=3.0
            )

            # Kill all client connections
            kill_connections(client, None)

            # Wait for reconnection and resubscription
            time.sleep(2)

            # Verify subscription is restored
            wait_for_subscription_state(
                client, expected_channels={channel}, timeout_sec=5.0
            )

            state = client.get_subscriptions()
            assert channel in state.actual_subscriptions[get_pubsub_modes(client).Exact]

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_resubscribe_after_connection_kill_patterns(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test that pattern subscriptions are restored after connection kill.
        """
        client = None
        try:
            pattern = "test_pattern_*"

            client = create_sync_pubsub_client(
                request, cluster_mode, patterns={pattern}
            )

            # Verify initial subscription
            wait_for_subscription_state(
                client, expected_patterns={pattern}, timeout_sec=3.0
            )

            # Kill all client connections
            kill_connections(client, None)

            # Wait for reconnection and resubscription
            time.sleep(2)

            # Verify subscription is restored
            wait_for_subscription_state(
                client, expected_patterns={pattern}, timeout_sec=5.0
            )

            state = client.get_subscriptions()
            assert (
                pattern in state.actual_subscriptions[get_pubsub_modes(client).Pattern]
            )

        finally:
            if client:
                client.close()

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
    def test_sync_ssubscribe_channels_different_slots(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test subscribing to sharded channels in different slots.
        """
        client, publishing_client = None, None
        try:
            # These channels hash to different slots
            channels = {
                "{slot1}channel_a",
                "{slot2}channel_b",
                "{slot3}channel_c",
                "{slot1}channel_d",
                "{slot4}channel_e",
            }
            messages = {ch: f"msg_{ch}" for ch in channels}

            # Create client without config-based subscriptions
            client = create_sync_pubsub_client(request, cluster_mode)
            publishing_client = create_sync_client(request, cluster_mode)

            # Subscribe dynamically
            cast(GlideClusterClient, client).ssubscribe(channels, timeout_ms=5000)

            # Verify subscriptions
            wait_for_subscription_state(
                client, expected_sharded=channels, timeout_sec=3.0
            )

            # Publish to all channels
            for channel, message in messages.items():
                cast(GlideClusterClient, publishing_client).publish(
                    message, channel, sharded=True
                )

            time.sleep(1)

            # Retrieve all messages using try_get with retry
            received_messages = {}
            for _ in range(len(channels)):
                msg = None
                for attempt in range(10):
                    msg = client.try_get_pubsub_message()
                    if msg:
                        msg = decode_pubsub_msg(msg)
                        break
                    time.sleep(0.5)

                assert msg is not None, "Failed to receive message after 10 attempts"
                received_messages[msg.channel] = msg.message

            assert received_messages == messages

        finally:
            if client:
                client.close()
            if publishing_client:
                publishing_client.close()

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
    def test_sync_sunsubscribe_channels_different_slots(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test unsubscribing from sharded channels in different slots.
        """
        client = None
        try:
            channels = {
                "{slot1}channel_a",
                "{slot2}channel_b",
            }

            # Create client and subscribe dynamically
            client = create_sync_pubsub_client(request, cluster_mode)
            cast(GlideClusterClient, client).ssubscribe(channels, timeout_ms=5000)

            # Verify subscriptions
            wait_for_subscription_state(
                client, expected_sharded=channels, timeout_sec=3.0
            )

            # Unsubscribe from one channel
            channel_to_unsub = "{slot1}channel_a"
            cast(GlideClusterClient, client).sunsubscribe(
                {channel_to_unsub}, timeout_ms=5000
            )

            # Verify only one channel remains
            remaining = channels - {channel_to_unsub}
            wait_for_subscription_state(
                client, expected_sharded=remaining, timeout_sec=3.0
            )

            state = client.get_subscriptions()
            modes = get_pubsub_modes(client)
            if hasattr(modes, "Sharded"):
                assert channel_to_unsub not in state.actual_subscriptions[modes.Sharded]  # type: ignore
                assert remaining.issubset(state.actual_subscriptions[modes.Sharded])  # type: ignore

        finally:
            if client:
                client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize(
        "subscription_method",
        [
            SubscriptionMethod.Config,
            SubscriptionMethod.Lazy,
            SubscriptionMethod.Blocking,
        ],
    )
    def test_sync_subscription_metrics_on_acl_failure(
        self, request, cluster_mode: bool, subscription_method: SubscriptionMethod
    ):
        """
        Test that out-of-sync metric is recorded when subscription fails due to ACL.
        """
        listening_client, admin_client = None, None
        try:
            channel = "channel_acl_metrics_test"
            username = f"{PubSubTestConstants.ACL_TEST_USERNAME_PREFIX}_acl_metrics"
            password = f"{PubSubTestConstants.ACL_TEST_PASSWORD_PREFIX}_acl_metrics"

            admin_client = create_sync_client(request, cluster_mode)

            # Create user without pubsub permissions
            acl_command = [
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
                cast(GlideClusterClient, admin_client).custom_command(
                    acl_command, route=AllNodes()  # type: ignore[arg-type]
                )
            else:
                admin_client.custom_command(acl_command)  # type: ignore[arg-type]

            # Create regular client (not pubsub) and authenticate with restricted user
            listening_client = create_sync_client(request, cluster_mode)

            if cluster_mode:
                cast(GlideClusterClient, listening_client).custom_command(
                    ["AUTH", username, password], route=AllNodes()
                )
            else:
                listening_client.custom_command(["AUTH", username, password])

            # Get initial metrics
            initial_stats = listening_client.get_statistics()
            initial_out_of_sync = int(
                initial_stats.get("subscription_out_of_sync_count", "0")
            )

            # Try to subscribe (will fail due to ACL)
            listening_client.subscribe_lazy({channel})

            # Wait for reconciliation attempts and poll for metric update
            out_of_sync_count = initial_out_of_sync
            for i in range(15):
                time.sleep(1)
                stats = listening_client.get_statistics()
                out_of_sync_count = int(
                    stats.get("subscription_out_of_sync_count", "0")
                )
                print(
                    f"[{i+1}s] out_of_sync_count={out_of_sync_count}, initial={initial_out_of_sync}"
                )
                if out_of_sync_count > initial_out_of_sync:
                    print(f"Metric increased after {i+1} seconds")
                    break

            assert (
                out_of_sync_count > initial_out_of_sync
            ), f"Expected out-of-sync count to increase from {initial_out_of_sync}, got {out_of_sync_count}"

            # Verify subscription is NOT active (desired != actual)
            state = listening_client.get_subscriptions()
            modes = get_pubsub_modes(listening_client)

            desired_channels = state.desired_subscriptions.get(modes.Exact, set())
            actual_channels = state.actual_subscriptions.get(modes.Exact, set())

            assert channel in desired_channels, "Channel should be in desired"
            assert (
                channel not in actual_channels
            ), "Channel should NOT be in actual (ACL blocked)"

        finally:
            # Cleanup: delete test user
            if admin_client:
                try:
                    admin_client.custom_command(["ACL", "DELUSER", username])
                except Exception:
                    pass
                admin_client.close()
            if listening_client:
                listening_client.close()
