# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Tests for sync client address resolver functionality.
Ports the Java address resolver tests from ConnectionTests.java.
"""

from typing import Tuple, Union

import pytest
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
)
from glide_shared.constants import OK
from glide_sync import GlideClient as SyncGlideClient
from glide_sync import GlideClusterClient as SyncGlideClusterClient

TSyncGlideClient = Union[SyncGlideClient, SyncGlideClusterClient]


@pytest.mark.parametrize("cluster_mode", [True, False])
class TestSyncAddressResolver:
    """Sync address resolver tests."""

    def test_address_resolver_with_fake_address(self, cluster_mode: bool):
        """
        Test that the address resolver translates a fake address to the real server address.
        The client should connect successfully using the resolved address.
        """
        # Get the actual server address from test configuration
        if cluster_mode:
            cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        else:
            cluster = pytest.standalone_cluster  # type: ignore[attr-defined]

        actual_address = cluster.nodes_addr[0]
        actual_host = actual_address.host
        actual_port = actual_address.port

        # Use a fake/placeholder address in configuration
        fake_host = "fake-host-that-does-not-exist.invalid"
        fake_port = 9999

        # Create resolver that translates fake address to real server address
        def resolver(host: str, port: int) -> Tuple[str, int]:
            if host == fake_host and port == fake_port:
                return (actual_host, actual_port)
            return (host, port)

        # Configure client with fake address - connection should succeed because
        # resolver translates it to the real address
        client: TSyncGlideClient
        if cluster_mode:
            client = SyncGlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=[NodeAddress(fake_host, fake_port)],
                    address_resolver=resolver,
                )
            )
        else:
            client = SyncGlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(fake_host, fake_port)],
                    address_resolver=resolver,
                )
            )

        try:
            # Verify the client works - this proves the resolved address was used
            assert client.ping() == b"PONG"
            assert client.set("resolver_test_key", "resolver_test_value") == OK
            assert client.get("resolver_test_key") == b"resolver_test_value"
        finally:
            client.close()

    def test_address_resolver_exception_falls_back_to_original(
        self, cluster_mode: bool
    ):
        """
        Test that when the address resolver throws an exception, the client
        falls back to the original address and still connects successfully.
        """
        if cluster_mode:
            cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        else:
            cluster = pytest.standalone_cluster  # type: ignore[attr-defined]

        actual_address = cluster.nodes_addr[0]
        actual_host = actual_address.host
        actual_port = actual_address.port

        # Create resolver that always throws an exception
        def resolver(host: str, port: int) -> Tuple[str, int]:
            raise RuntimeError("test-exception")

        # Configure client with real address - connection should still succeed
        # because the fallback uses the original address when resolver throws
        client: TSyncGlideClient
        if cluster_mode:
            client = SyncGlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=[NodeAddress(actual_host, actual_port)],
                    address_resolver=resolver,
                )
            )
        else:
            client = SyncGlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(actual_host, actual_port)],
                    address_resolver=resolver,
                )
            )

        try:
            assert client.ping() == b"PONG"
            assert client.set("resolver_test_key", "resolver_test_value") == OK
            assert client.get("resolver_test_key") == b"resolver_test_value"
        finally:
            client.close()

    def test_address_resolver_returns_none_falls_back_to_original(
        self, cluster_mode: bool
    ):
        """
        Test that when the address resolver returns None (invalid result),
        the client falls back to the original address and still connects.
        """
        if cluster_mode:
            cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        else:
            cluster = pytest.standalone_cluster  # type: ignore[attr-defined]

        actual_address = cluster.nodes_addr[0]
        actual_host = actual_address.host
        actual_port = actual_address.port

        # Create resolver that returns None (invalid - should trigger fallback)
        def resolver(host: str, port: int):
            return None  # type: ignore

        # Configure client with real address - connection should still succeed
        client: TSyncGlideClient
        if cluster_mode:
            client = SyncGlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=[NodeAddress(actual_host, actual_port)],
                    address_resolver=resolver,
                )
            )
        else:
            client = SyncGlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(actual_host, actual_port)],
                    address_resolver=resolver,
                )
            )

        try:
            assert client.ping() == b"PONG"
            assert client.set("resolver_test_key", "resolver_test_value") == OK
            assert client.get("resolver_test_key") == b"resolver_test_value"
        finally:
            client.close()
