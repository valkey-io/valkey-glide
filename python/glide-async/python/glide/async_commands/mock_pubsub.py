import fnmatch
import random
import threading
from collections import defaultdict
from typing import Any, Callable, DefaultDict, Dict, List, Optional, Set, Tuple, Union

from glide.logger import Level as LogLevel
from glide.logger import Logger as ClientLogger
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_shared.constants import TEncodable


class MockPubSubBroker:
    """Global mock pubsub broker that simulates Redis pub/sub behavior."""

    _instance: Optional["MockPubSubBroker"] = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._initialize()
        return cls._instance

    def _initialize(self, max_application_delay: float = 0.1) -> None:
        """Initialize broker state."""
        self._subscribers_lock = threading.Lock()
        self._max_application_delay = max_application_delay

        # Desired subscriptions (updated immediately by API calls)
        self._desired_channel_subscribers: DefaultDict[str, Set[str]] = defaultdict(set)
        self._desired_pattern_subscribers: DefaultDict[str, Set[str]] = defaultdict(set)
        self._desired_sharded_subscribers: DefaultDict[str, Set[str]] = defaultdict(set)

        # Actual subscriptions (updated after delay to simulate server application)
        self._actual_channel_subscribers: DefaultDict[str, Set[str]] = defaultdict(set)
        self._actual_pattern_subscribers: DefaultDict[str, Set[str]] = defaultdict(set)
        self._actual_sharded_subscribers: DefaultDict[str, Set[str]] = defaultdict(set)

        # Client message queues: client_id -> queue of PubSubMsg
        self._client_queues: DefaultDict[str, List[PubSubMsg]] = defaultdict(list)

        # Client callbacks: client_id -> (callback, context)
        self._client_callbacks: Dict[str, Tuple[Optional[Callable], Any]] = {}

        # Track if client is cluster mode: client_id -> bool
        self._client_is_cluster: Dict[str, bool] = {}

        # Track pending timers for cleanup
        self._pending_timers: List[threading.Timer] = []

    def set_max_application_delay(self, max_delay: float):
        """Set the maximum application delay for subscription changes."""
        with self._subscribers_lock:
            self._max_application_delay = max_delay

    def register_client(
        self,
        client_id: str,
        callback: Optional[Callable[[PubSubMsg, Any], None]] = None,
        context: Any = None,
        is_cluster: bool = False,
    ):
        """Register a client with the broker."""
        with self._subscribers_lock:
            self._client_callbacks[client_id] = (callback, context)
            self._client_is_cluster[client_id] = is_cluster

    def unregister_client(self, client_id: str):
        """Unregister a client and clean up all its subscriptions."""
        with self._subscribers_lock:
            # Remove from all desired subscriptions
            for subscribers in self._desired_channel_subscribers.values():
                subscribers.discard(client_id)
            for subscribers in self._desired_pattern_subscribers.values():
                subscribers.discard(client_id)
            for subscribers in self._desired_sharded_subscribers.values():
                subscribers.discard(client_id)

            # Remove from all actual subscriptions
            for subscribers in self._actual_channel_subscribers.values():
                subscribers.discard(client_id)
            for subscribers in self._actual_pattern_subscribers.values():
                subscribers.discard(client_id)
            for subscribers in self._actual_sharded_subscribers.values():
                subscribers.discard(client_id)

            # Clean up client data
            self._client_queues.pop(client_id, None)
            self._client_callbacks.pop(client_id, None)
            self._client_is_cluster.pop(client_id, None)

    def _schedule_actual_update(self, update_func: Callable[[], None]):
        """Schedule an actual subscription update after a random delay."""
        delay = random.uniform(0, self._max_application_delay)
        timer = threading.Timer(delay, update_func)
        self._pending_timers.append(timer)
        timer.start()

    def _apply_channel_subscribe(self, client_id: str, channels: List[str]):
        """Apply actual channel subscription."""
        with self._subscribers_lock:
            for channel in channels:
                self._actual_channel_subscribers[channel].add(client_id)

    def _apply_pattern_subscribe(self, client_id: str, patterns: List[str]):
        """Apply actual pattern subscription."""
        with self._subscribers_lock:
            for pattern in patterns:
                self._actual_pattern_subscribers[pattern].add(client_id)

    def _apply_sharded_subscribe(self, client_id: str, channels: List[str]):
        """Apply actual sharded subscription."""
        with self._subscribers_lock:
            for channel in channels:
                self._actual_sharded_subscribers[channel].add(client_id)

    def _apply_channel_unsubscribe(self, client_id: str, channels: Optional[List[str]]):
        """Apply actual channel unsubscription."""
        with self._subscribers_lock:
            if channels is None:
                for subscribers in self._actual_channel_subscribers.values():
                    subscribers.discard(client_id)
            else:
                for channel in channels:
                    self._actual_channel_subscribers[channel].discard(client_id)

    def _apply_pattern_unsubscribe(self, client_id: str, patterns: Optional[List[str]]):
        """Apply actual pattern unsubscription."""
        with self._subscribers_lock:
            if patterns is None:
                for subscribers in self._actual_pattern_subscribers.values():
                    subscribers.discard(client_id)
            else:
                for pattern in patterns:
                    self._actual_pattern_subscribers[pattern].discard(client_id)

    def _apply_sharded_unsubscribe(self, client_id: str, channels: Optional[List[str]]):
        """Apply actual sharded unsubscription."""
        with self._subscribers_lock:
            if channels is None:
                for subscribers in self._actual_sharded_subscribers.values():
                    subscribers.discard(client_id)
            else:
                for channel in channels:
                    self._actual_sharded_subscribers[channel].discard(client_id)

    def _check_channels_applied(self, client_id: str, channels: List[str]) -> bool:
        """Check if channels are in actual subscriptions."""
        with self._subscribers_lock:
            for channel in channels:
                if client_id not in self._actual_channel_subscribers[channel]:
                    return False
            return True

    def _check_channels_removed(
        self, client_id: str, channels: Optional[List[str]]
    ) -> bool:
        """Check if channels are removed from actual subscriptions."""
        with self._subscribers_lock:
            if channels is None:
                # Check all channels
                for subscribers in self._actual_channel_subscribers.values():
                    if client_id in subscribers:
                        return False
                return True
            else:
                for channel in channels:
                    if client_id in self._actual_channel_subscribers[channel]:
                        return False
                return True

    def _check_patterns_applied(self, client_id: str, patterns: List[str]) -> bool:
        """Check if patterns are in actual subscriptions."""
        with self._subscribers_lock:
            for pattern in patterns:
                if client_id not in self._actual_pattern_subscribers[pattern]:
                    return False
            return True

    def _check_patterns_removed(
        self, client_id: str, patterns: Optional[List[str]]
    ) -> bool:
        """Check if patterns are removed from actual subscriptions."""
        with self._subscribers_lock:
            if patterns is None:
                for subscribers in self._actual_pattern_subscribers.values():
                    if client_id in subscribers:
                        return False
                return True
            else:
                for pattern in patterns:
                    if client_id in self._actual_pattern_subscribers[pattern]:
                        return False
                return True

    def _check_sharded_applied(self, client_id: str, channels: List[str]) -> bool:
        """Check if sharded channels are in actual subscriptions."""
        with self._subscribers_lock:
            for channel in channels:
                if client_id not in self._actual_sharded_subscribers[channel]:
                    return False
            return True

    def _check_sharded_removed(
        self, client_id: str, channels: Optional[List[str]]
    ) -> bool:
        """Check if sharded channels are removed from actual subscriptions."""
        with self._subscribers_lock:
            if channels is None:
                for subscribers in self._actual_sharded_subscribers.values():
                    if client_id in subscribers:
                        return False
                return True
            else:
                for channel in channels:
                    if client_id in self._actual_sharded_subscribers[channel]:
                        return False
                return True

    # Lazy methods (update desired state only)
    def subscribe(self, client_id: str, channels: List[str]) -> None:
        """Subscribe a client to exact channels (lazy)."""
        with self._subscribers_lock:
            for channel in channels:
                self._desired_channel_subscribers[channel].add(client_id)

        self._schedule_actual_update(
            lambda: self._apply_channel_subscribe(client_id, channels)
        )

    def psubscribe(self, client_id: str, patterns: List[str]) -> None:
        """Subscribe a client to channel patterns (lazy)."""
        with self._subscribers_lock:
            for pattern in patterns:
                self._desired_pattern_subscribers[pattern].add(client_id)

        self._schedule_actual_update(
            lambda: self._apply_pattern_subscribe(client_id, patterns)
        )

    def ssubscribe(self, client_id: str, channels: List[str]) -> None:
        """Subscribe a client to sharded channels (lazy)."""
        with self._subscribers_lock:
            for channel in channels:
                self._desired_sharded_subscribers[channel].add(client_id)

        self._schedule_actual_update(
            lambda: self._apply_sharded_subscribe(client_id, channels)
        )

    def unsubscribe(self, client_id: str, channels: Optional[List[str]] = None) -> None:
        """Unsubscribe a client from exact channels (lazy)."""
        with self._subscribers_lock:
            if channels is None:
                for subscribers in self._desired_channel_subscribers.values():
                    subscribers.discard(client_id)
            else:
                for channel in channels:
                    self._desired_channel_subscribers[channel].discard(client_id)

        self._schedule_actual_update(
            lambda: self._apply_channel_unsubscribe(client_id, channels)
        )

    def punsubscribe(
        self, client_id: str, patterns: Optional[List[str]] = None
    ) -> None:
        """Unsubscribe a client from channel patterns (lazy)."""
        with self._subscribers_lock:
            if patterns is None:
                for subscribers in self._desired_pattern_subscribers.values():
                    subscribers.discard(client_id)
            else:
                for pattern in patterns:
                    self._desired_pattern_subscribers[pattern].discard(client_id)

        self._schedule_actual_update(
            lambda: self._apply_pattern_unsubscribe(client_id, patterns)
        )

    def sunsubscribe(
        self, client_id: str, channels: Optional[List[str]] = None
    ) -> None:
        """Unsubscribe a client from sharded channels (lazy)."""
        with self._subscribers_lock:
            if channels is None:
                for subscribers in self._desired_sharded_subscribers.values():
                    subscribers.discard(client_id)
            else:
                for channel in channels:
                    self._desired_sharded_subscribers[channel].discard(client_id)

        self._schedule_actual_update(
            lambda: self._apply_sharded_unsubscribe(client_id, channels)
        )

    # Blocking methods (wait for actual state to match)
    async def subscribe_blocking(
        self, client_id: str, channels: List[str], timeout_ms: int
    ) -> None:
        """Subscribe and wait for actual state to match (blocking)."""
        # Update desired state
        self.subscribe(client_id, channels)

        # Wait for actual state
        await self._wait_for_condition(
            lambda: self._check_channels_applied(client_id, channels),
            timeout_ms,
            f"Timeout waiting for channels {channels} to be subscribed",
        )

    async def psubscribe_blocking(
        self, client_id: str, patterns: List[str], timeout_ms: int
    ) -> None:
        """Pattern subscribe and wait (blocking)."""
        self.psubscribe(client_id, patterns)

        await self._wait_for_condition(
            lambda: self._check_patterns_applied(client_id, patterns),
            timeout_ms,
            f"Timeout waiting for patterns {patterns} to be subscribed",
        )

    async def ssubscribe_blocking(
        self, client_id: str, channels: List[str], timeout_ms: int
    ) -> None:
        """Sharded subscribe and wait (blocking)."""
        self.ssubscribe(client_id, channels)

        await self._wait_for_condition(
            lambda: self._check_sharded_applied(client_id, channels),
            timeout_ms,
            f"Timeout waiting for sharded channels {channels} to be subscribed",
        )

    async def unsubscribe_blocking(
        self, client_id: str, channels: Optional[List[str]], timeout_ms: int
    ) -> None:
        """Unsubscribe and wait (blocking)."""
        self.unsubscribe(client_id, channels)

        await self._wait_for_condition(
            lambda: self._check_channels_removed(client_id, channels),
            timeout_ms,
            f"Timeout waiting for channels {channels} to be unsubscribed",
        )

    async def punsubscribe_blocking(
        self, client_id: str, patterns: Optional[List[str]], timeout_ms: int
    ) -> None:
        """Pattern unsubscribe and wait (blocking)."""
        self.punsubscribe(client_id, patterns)

        await self._wait_for_condition(
            lambda: self._check_patterns_removed(client_id, patterns),
            timeout_ms,
            f"Timeout waiting for patterns {patterns} to be unsubscribed",
        )

    async def sunsubscribe_blocking(
        self, client_id: str, channels: Optional[List[str]], timeout_ms: int
    ) -> None:
        """Sharded unsubscribe and wait (blocking)."""
        self.sunsubscribe(client_id, channels)

        await self._wait_for_condition(
            lambda: self._check_sharded_removed(client_id, channels),
            timeout_ms,
            f"Timeout waiting for sharded channels {channels} to be unsubscribed",
        )

    async def _wait_for_condition(
        self, condition: Callable[[], bool], timeout_ms: int, error_msg: str
    ) -> None:
        """Wait for a condition to be true, or raise TimeoutError."""
        import anyio

        if timeout_ms == 0:
            # Wait indefinitely with safety limit
            iterations = 0
            max_iterations = 100000
            while not condition():
                iterations += 1
                if iterations > max_iterations:
                    raise TimeoutError(f"{error_msg} (exceeded max iterations)")
                await anyio.sleep(0.01)  # ✅ Non-blocking
        else:
            # Wait with timeout
            start_ms = anyio.current_time() * 1000
            while not condition():
                elapsed_ms = (anyio.current_time() * 1000) - start_ms
                if elapsed_ms >= timeout_ms:
                    raise TimeoutError(error_msg)
                await anyio.sleep(0.01)

    def publish(self, channel: str, message: str, sharded: bool = False) -> int:
        """Publish a message to a channel. Returns number of recipients."""
        with self._subscribers_lock:
            recipient_count = 0

            channel_bytes = channel.encode() if isinstance(channel, str) else channel
            message_bytes = message.encode() if isinstance(message, str) else message

            if sharded:
                subscribers = self._actual_sharded_subscribers.get(channel, set())
                for client_id in subscribers:
                    self._deliver_message(
                        client_id,
                        PubSubMsg(
                            message=message_bytes,
                            channel=channel_bytes,
                            pattern=None,
                        ),
                    )
                    recipient_count += 1
            else:
                subscribers = self._actual_channel_subscribers.get(channel, set())
                for client_id in subscribers:
                    self._deliver_message(
                        client_id,
                        PubSubMsg(
                            message=message_bytes,
                            channel=channel_bytes,
                            pattern=None,
                        ),
                    )
                    recipient_count += 1

                for (
                    pattern,
                    pattern_subscribers,
                ) in self._actual_pattern_subscribers.items():
                    if fnmatch.fnmatch(channel, pattern):
                        pattern_bytes = (
                            pattern.encode() if isinstance(pattern, str) else pattern
                        )
                        for client_id in pattern_subscribers:
                            self._deliver_message(
                                client_id,
                                PubSubMsg(
                                    message=message_bytes,
                                    channel=channel_bytes,
                                    pattern=pattern_bytes,
                                ),
                            )
                            recipient_count += 1

            return recipient_count

    def _deliver_message(self, client_id: str, msg: PubSubMsg):
        """Deliver a message to a specific client."""
        callback, context = self._client_callbacks.get(client_id, (None, None))

        if callback is not None:
            try:
                callback(msg, context)
            except Exception as e:
                ClientLogger.log(
                    LogLevel.WARN,
                    "pubsub callback error",
                    f"Error in pubsub callback for client {client_id}: {e}",
                )
        else:
            self._client_queues[client_id].append(msg)

    def get_client_message(self, client_id: str) -> Optional[PubSubMsg]:
        """Get the next queued message for a client, if any."""
        with self._subscribers_lock:
            queue = self._client_queues.get(client_id, [])
            if queue:
                return queue.pop(0)

            return None

    def get_client_subscriptions(self, client_id: str) -> Union[
        GlideClientConfiguration.PubSubState,
        GlideClusterClientConfiguration.PubSubState,
    ]:
        """
        Get both desired and actual subscriptions for a specific client.
        Returns a PubSubState object.
        """
        from glide_shared.config import (
            GlideClientConfiguration,
            GlideClusterClientConfiguration,
        )

        is_cluster = self._client_is_cluster.get(client_id, False)

        if is_cluster:
            PubSubChannelModes = GlideClusterClientConfiguration.PubSubChannelModes
            PubSubState = GlideClusterClientConfiguration.PubSubState
        else:
            PubSubChannelModes = GlideClientConfiguration.PubSubChannelModes  # type: ignore[assignment]
            PubSubState = GlideClientConfiguration.PubSubState  # type: ignore[assignment]

        with self._subscribers_lock:
            # Get desired subscriptions
            desired_channels = {
                channel
                for channel, subs in self._desired_channel_subscribers.items()
                if client_id in subs
            }
            desired_patterns = {
                pattern
                for pattern, subs in self._desired_pattern_subscribers.items()
                if client_id in subs
            }
            desired_sharded = {
                channel
                for channel, subs in self._desired_sharded_subscribers.items()
                if client_id in subs
            }

            # Get actual subscriptions
            actual_channels = {
                channel
                for channel, subs in self._actual_channel_subscribers.items()
                if client_id in subs
            }
            actual_patterns = {
                pattern
                for pattern, subs in self._actual_pattern_subscribers.items()
                if client_id in subs
            }
            actual_sharded = {
                channel
                for channel, subs in self._actual_sharded_subscribers.items()
                if client_id in subs
            }

            # Build desired subscriptions dict with enum keys
            desired_subscriptions = {
                PubSubChannelModes.Exact: desired_channels,
                PubSubChannelModes.Pattern: desired_patterns,
            }

            # Build actual subscriptions dict with enum keys
            actual_subscriptions = {
                PubSubChannelModes.Exact: actual_channels,
                PubSubChannelModes.Pattern: actual_patterns,
            }

            # Add Sharded only for cluster mode
            if is_cluster:
                desired_subscriptions[PubSubChannelModes.Sharded] = desired_sharded
                actual_subscriptions[PubSubChannelModes.Sharded] = actual_sharded

            return PubSubState(
                desired_subscriptions=desired_subscriptions,
                actual_subscriptions=actual_subscriptions,
            )

    @classmethod
    def reset(cls):
        """Reset the broker (useful for testing)."""
        with cls._lock:
            if cls._instance is not None:
                for timer in cls._instance._pending_timers:
                    timer.cancel()
                cls._instance._pending_timers.clear()
                cls._instance._initialize()


def normalize_args(args: List[TEncodable]) -> List[str]:
    """Convert args to strings for internal storage."""
    return [arg.decode() if isinstance(arg, bytes) else str(arg) for arg in args]
