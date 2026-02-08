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
