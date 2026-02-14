# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Shared utilities for pubsub tests (both async and sync).
"""

from typing import Any, List

from glide_shared.commands.core_options import PubSubMsg


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
