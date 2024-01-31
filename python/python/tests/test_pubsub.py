from __future__ import annotations
import pytest
from typing import List, Set, Dict, Tuple, Optional, Any, Awaitable
import asyncio
import fnmatch

from tests.conftest import create_client

from glide.async_commands.core import (
    CoreCommands,
)


MessagesByExactChannel = Dict[str, Dict[str, int]] # channel -> {msg -> cnt}
MessagesByChannelAndChannel = Dict[Tuple[str, Optional[str]], Dict[str, int]] # (channel, pattern) -> {msg -> cnt}
SubscriptionsByMode = Dict[CoreCommands.ChannelModes, Set[str]] # subscription_type -> {channel_or_pattern}


@pytest.mark.asyncio
class TestPubSub:

    class PubSubClient:
        def __init__(self,
                     subsciptions: SubscriptionsByMode,
                     sharded_messages_to_publish: MessagesByExactChannel,
                     unsharded_messages_to_publish: MessagesByExactChannel,
                     cluster_mode: bool,
                     request):
            self.messages_received: MessagesByChannelAndChannel = {}
            self.messages_to_receive: MessagesByChannelAndChannel = {}

            self.sharded_messages_to_publish: MessagesByExactChannel = sharded_messages_to_publish
            self.unsharded_messages_to_publish: MessagesByExactChannel = unsharded_messages_to_publish

            self.subsciptions = subsciptions

            self.cluster_mode = cluster_mode
            self.request = request

            self.redis_client: CoreCommands = None


        @staticmethod
        def handle_new_message(message: CoreCommands.PubSubMsg, client: TestPubSub.PubSubClient) -> None:
            key = (message.channel, message.pattern)
            if key not in client.messages_received:
                    client.messages_received[key] = {}

            if message not in client.messages_received[key]:
                    client.messages_received[key][message] = 0

            client.messages_received[key][message] += 1


        async def connect_and_subscribe(self) -> List[Any]:
            self.redis_client = await create_client(self.request, self.cluster_mode)

            for channel_mode, channels_or_patterns in self.subsciptions:
                await self.redis_client.subscribe(channels_or_patterns=channels_or_patterns,
                                                  channel_mode=channel_mode,
                                                  callback=TestPubSub.PubSubClient.handle_new_message,
                                                  context=self)

        def assert_client(self) -> None:
            assert self.messages_received == self.messages_to_receive


        def calculate_expected_messages(self, all_clients: List[TestPubSub.PubSubClient]) -> None:
            for sender in all_clients:
                for sharded_channel, sharded_channel_messages in sender.sharded_messages_to_publish.items():
                    # should receive by sctrict sub?
                    if sharded_channel in self.subsciptions[CoreCommands.ChannelModes.Sharded]:
                        channel_and_pattern = sharded_channel, None
                        if channel_and_pattern not in self.messages_to_receive:
                            self.messages_to_receive[channel_and_pattern] = {}
                        for message, cnt in sharded_channel_messages.items():
                            if message not in self.messages_to_receive[channel_and_pattern]:
                                self.messages_to_receive[channel_and_pattern][message] = 0
                            self.messages_to_receive[channel_and_pattern][message] += cnt

                for unsharded_channel, unsharded_channel_messages in sender.unsharded_messages_to_publish.items():
                    # should receive by sctrict sub?
                    if unsharded_channel in self.subsciptions[CoreCommands.ChannelModes.Exact]:
                        channel_and_pattern = unsharded_channel, None
                        if channel_and_pattern not in self.messages_to_receive:
                            self.messages_to_receive[channel_and_pattern] = {}
                        for message, cnt in unsharded_channel_messages.items():
                            if message not in self.messages_to_receive[channel_and_pattern]:
                                self.messages_to_receive[channel_and_pattern][message] = 0
                            self.messages_to_receive[channel_and_pattern][message] += cnt

                    # should receive by glob pattern?
                    for glob_pattern in self.subsciptions[CoreCommands.ChannelModes.Pattern]:
                        if fnmatch.filter(names=[unsharded_channel], pat=glob_pattern):
                            channel_and_pattern = unsharded_channel, glob_pattern
                            for message, cnt in unsharded_channel_messages.items():
                                if message not in self.messages_to_receive[channel_and_pattern]:
                                    self.messages_to_receive[channel_and_pattern][message] = 0
                                self.messages_to_receive[channel_and_pattern][message] += cnt

        # TODO: Allow async fanning out?
        def sched_publish_messages(self) -> List[Awaitable[None]]:
            # coroutines = []
            # for sharded_channel, sharded_messages_to_publish in self.sharded_messages_to_publish:
            #     for message in sharded_messages_to_publish:
            #         coroutines += self.redis_client.publish(message=message, channels={sharded_channel}, sharded=True)

            # for unsharded_channel, unsharded_messages_to_publish in self.unsharded_messages_to_publish:
            #     for message in unsharded_messages_to_publish:
            #         coroutines += self.redis_client.publish(message=message, channels={unsharded_channel}, sharded=False)

            # return coroutines
            pass
        
        async def publish_messages(self) -> List[Awaitable[None]]:
            for sharded_channel, sharded_messages_to_publish in self.sharded_messages_to_publish:
                for message, cnt in sharded_messages_to_publish:
                    for i in range(cnt):
                        await self.redis_client.publish(message=message, channels={sharded_channel}, sharded=True)

            for unsharded_channel, unsharded_messages_to_publish in self.unsharded_messages_to_publish:
                for message, cnt in unsharded_messages_to_publish:
                    for i in range(cnt):
                        await self.redis_client.publish(message=message, channels={unsharded_channel}, sharded=False)


    @staticmethod
    async def publish_all_messages(all_clients: List[TestPubSub.PubSubClient]):
        coroutines = []
        for client in all_clients:
            coroutines += client.sched_publish_messages()
        asyncio.wait(coroutines)
        

    @staticmethod
    async def publish_and_assert(all_clients: List[TestPubSub.PubSubClient]):
        for client in all_clients:
            client.publish_messages()

        #TODO - Wait for how long?
        for client in all_clients:
            client.calculate_expected_messages(all_clients=all_clients)
            client.assert_client()


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_exact(self, request, cluster_mode, sharded_pubsub):
        """ PUBSUB basic happy case using exact channel names - tests that clients receive all the messages using exact subscribe to a single channel """

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 5
        MESSAGES_BY_CLIENT = {
            id : {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            } for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(TestPubSub.PubSubClient(
                subsciptions={
                    CoreCommands.ChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                sharded_messages_to_publish=MESSAGES_BY_CLIENT[id] if sharded_pubsub else [],
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id] if not sharded_pubsub else [],
                cluster_mode=cluster_mode,
                request=request))
            all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern(self, request, cluster_mode):
        """ PUBSUB using patterns happy case - tests that clients receive all the messages using pattern subscribe to a single channel """

        CLIENTS_COUNT = 5
        CHANNEL_NAME = "test-channel"
        CHANNEL_PATTERN = "test-*"
        MESSAGES_BY_CLIENT = {
            id : {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            } for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(TestPubSub.PubSubClient(
                subsciptions={
                    CoreCommands.ChannelModes.Pattern: {
                        CHANNEL_PATTERN
                    }
                },
                sharded_messages_to_publish=[],
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id],
                cluster_mode=cluster_mode,
                request=request))
            all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_pattern_and_exact(self, request, cluster_mode):
        """ Tests that subscription to exact channel and it's pattern triggers reception of 2 messages """

        CLIENTS_COUNT = 5
        CHANNEL_NAME = "test-channel"
        CHANNEL_PATTERN = "test-*"
        MESSAGES_BY_CLIENT = {
            id : {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            } for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(TestPubSub.PubSubClient(
                subsciptions={
                    CoreCommands.ChannelModes.Pattern: {
                        CHANNEL_PATTERN
                    },
                    CoreCommands.ChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                sharded_messages_to_publish=[],
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id],
                cluster_mode=cluster_mode,
                request=request))
            all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_compound_pattern(self, request, cluster_mode):
        """ Tests that subscription to compound patterns triggers reception of 2 messages (one for each pattern)  """

        CLIENTS_COUNT = 5
        CHANNEL_NAME = "test-channel"
        CHANNEL_PATTERNS = {"test-*", "test?channel"}
        MESSAGES_BY_CLIENT = {
            id : {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            } for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(TestPubSub.PubSubClient(
                subsciptions={
                    CoreCommands.ChannelModes.Pattern: CHANNEL_PATTERNS
                },
                sharded_messages_to_publish=[],
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id],
                cluster_mode=cluster_mode,
                request=request))
            all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_channel_boundary_exact(self, request, cluster_mode, sharded_pubsub):
        """ Tests that messages do not cross channel boundaries by exact subsctiption """

        CLIENTS_COUNT_PER_CHANNEL = 5
        CHANNEL_NAMES = {"test-channel-1", "test-channel-2"}
        MESSAGES_BY_CHANNEL = {
            channel : {
                id : {
                    channel: {
                        "on_" + channel + "_" + msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                    }
                } for id in range(CLIENTS_COUNT_PER_CHANNEL)
            } for channel in CHANNEL_NAMES
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for channel in CHANNEL_NAMES:
            for id in range(CLIENTS_COUNT_PER_CHANNEL):
                all_clients.append(TestPubSub.PubSubClient(
                    subsciptions={
                        CoreCommands.ChannelModes.Exact: {channel}
                    },
                    sharded_messages_to_publish=MESSAGES_BY_CHANNEL[channel][id] if sharded_pubsub else [],
                    unsharded_messages_to_publish=MESSAGES_BY_CHANNEL[channel][id] if not sharded_pubsub else [],
                    cluster_mode=cluster_mode,
                    request=request))
                all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pubsub_channel_boundary_pattern(self, request, cluster_mode):
        """ Tests that messages do not cross channel boundaries by pattern subsctiption """

        CLIENTS_COUNT_PER_CHANNEL = 5
        CHANNEL_NAMES = {"test-channel-1", "test-channel-2"}
        CHANNEL_PATTERNS = {"test?channel-1", "test?channel-2"}
        MESSAGES_BY_CHANNEL = {
            channel : {
                id : {
                    channel: {
                        "on_" + channel + "_" + msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                    }
                } for id in range(CLIENTS_COUNT_PER_CHANNEL)
            } for channel in CHANNEL_NAMES
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for channel_id in len(CHANNEL_NAMES):
            for id in range(CLIENTS_COUNT_PER_CHANNEL):
                all_clients.append(TestPubSub.PubSubClient(
                    subsciptions={
                        CoreCommands.ChannelModes.Pattern: {CHANNEL_PATTERNS[channel_id]}
                    },
                    sharded_messages_to_publish=[],
                    unsharded_messages_to_publish=MESSAGES_BY_CHANNEL[CHANNEL_NAMES[channel_id]][id],
                    cluster_mode=cluster_mode,
                    request=request))
                all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_callbacks_per_channel(self, request, cluster_mode, sharded_pubsub):
        """ Tests that callbacks are per channel """

        CHANNEL_NAME = "test-channel-1"
        CLIENTS_COUNT = 1

        callback_a_called = False
        callback_b_called = False

        def callback_a(message: CoreCommands.PubSubMsg, channel: str) -> None:
            callback_a_called = True

        def callback_b(message: CoreCommands.PubSubMsg, channel: str) -> None:
            callback_b_called = True

        redis_client = await create_client(self.request, self.cluster_mode)
        await redis_client.subscribe(channels_or_patterns={"test-channel-1"},
                                     channel_mode=CoreCommands.ChannelModes.Exact,
                                     callback=callback_a,
                                     context=None)
        
        await redis_client.subscribe(channels_or_patterns={"test-channel-2"},
                                     channel_mode=CoreCommands.ChannelModes.Exact,
                                     callback=callback_b,
                                     context=None)
        
        await redis_client.publish(message="hi", channels={"test-channel-1"}, sharded=sharded_pubsub)
        # FIXME - wait for receive with TO
        assert callback_a_called
        assert not callback_b_called

        callback_a_called = False
        await redis_client.publish(message="hi", channels={"test-channel-2"}, sharded=sharded_pubsub)
        # FIXME - wait for receive with TO
        assert not callback_a_called
        assert callback_b_called

        callback_b_called = False
        await redis_client.subscribe(channels_or_patterns={"test-channel-2"},
                                     channel_mode=CoreCommands.ChannelModes.Exact,
                                     callback=callback_a,
                                     context=None)
        await redis_client.publish(message="hi", channels={"test-channel-2"}, sharded=sharded_pubsub)
        # FIXME - wait for receive with TO
        assert callback_a_called
        assert not callback_b_called


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_large_messages(self, request, cluster_mode, sharded_pubsub):
        """ Tests that large messages (1MB) are supported correctly """

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 5
        MESSAGES_BY_CLIENT = {
            id : {
                CHANNEL_NAME: {
                    msg + str(id) + "_" + ("0" * 1024 * 1024): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            } for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(TestPubSub.PubSubClient(
                subsciptions={
                    CoreCommands.ChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                sharded_messages_to_publish=MESSAGES_BY_CLIENT[id] if sharded_pubsub else [],
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id] if not sharded_pubsub else [],
                cluster_mode=cluster_mode,
                request=request))
            all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_many_clients(self, request, cluster_mode, sharded_pubsub):
        """ Tests that many clients (1K) are supported correctly """

        CHANNEL_NAME = "test-channel"
        CLIENTS_COUNT = 1024
        MESSAGES_BY_CLIENT = {
            id : {
                CHANNEL_NAME: {
                    msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                }
            } for id in range(CLIENTS_COUNT)
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for id in range(CLIENTS_COUNT):
            all_clients.append(TestPubSub.PubSubClient(
                subsciptions={
                    CoreCommands.ChannelModes.Exact: {
                        CHANNEL_NAME
                    }
                },
                sharded_messages_to_publish=MESSAGES_BY_CLIENT[id] if sharded_pubsub else [],
                unsharded_messages_to_publish=MESSAGES_BY_CLIENT[id] if not sharded_pubsub else [],
                cluster_mode=cluster_mode,
                request=request))
            all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_many_channels(self, request, cluster_mode, sharded_pubsub):
        """ Tests that many channels (256) are supported correctly """

        CLIENTS_COUNT_PER_CHANNEL = 4
        CHANNELS_COUNT = 256
        CHANNEL_NAMES = {"test-channel-" + str(id) for id in range(CHANNELS_COUNT)}
        MESSAGES_BY_CHANNEL = {
            channel : {
                id : {
                    channel: {
                        "on_" + channel + "_" + msg + str(id): 1 for msg in ["foo_from_", "bar_from_", "baz_from_"]
                    }
                } for id in range(CLIENTS_COUNT_PER_CHANNEL)
            } for channel in CHANNEL_NAMES
        }

        all_clients: List[TestPubSub.PubSubClient] = []
        for channel in CHANNEL_NAMES:
            for id in range(CLIENTS_COUNT_PER_CHANNEL):
                all_clients.append(TestPubSub.PubSubClient(
                    subsciptions={
                        CoreCommands.ChannelModes.Exact: {channel}
                    },
                    sharded_messages_to_publish=MESSAGES_BY_CHANNEL[channel][id] if sharded_pubsub else [],
                    unsharded_messages_to_publish=MESSAGES_BY_CHANNEL[channel][id] if not sharded_pubsub else [],
                    cluster_mode=cluster_mode,
                    request=request))
                all_clients[-1].connect_and_subscribe()
        
        TestPubSub.publish_and_assert(all_clients)


    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("sharded_pubsub", [True, False])
    async def test_pubsub_unsubscribe(self, request, cluster_mode, sharded_pubsub):
        pass
