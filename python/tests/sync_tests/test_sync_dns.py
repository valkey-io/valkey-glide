# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Tests for sync client behavior when connecting with hostnames.

To run these tests, you need to add the following mappings to your hosts file
then set the environment variable VALKEY_GLIDE_DNS_TESTS_ENABLED:

    127.0.0.1 valkey.glide.test.tls.com
    127.0.0.1 valkey.glide.test.no_tls.com
    ::1 valkey.glide.test.tls.com
    ::1 valkey.glide.test.no_tls.com
"""

import os

import pytest
from glide_shared.config import NodeAddress

from tests.sync_tests.conftest import create_sync_client
from tests.test_constants import HOSTNAME_NO_TLS, HOSTNAME_TLS
from tests.utils.utils import assert_connected_sync, get_ca_certificate


def create_client_with_hostname(cluster_mode: bool, use_tls: bool, hostname: str):
    """Helper to create sync client with hostname."""
    if use_tls and cluster_mode:
        cluster = pytest.valkey_tls_cluster  # type: ignore[attr-defined]
    elif use_tls:
        cluster = pytest.standalone_tls_cluster  # type: ignore[attr-defined]
    elif cluster_mode:
        cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
    else:
        cluster = pytest.standalone_cluster  # type: ignore[attr-defined]

    port = cluster.nodes_addr[0].port
    address = NodeAddress(hostname, port)

    return create_sync_client(
        cluster_mode=cluster_mode,
        use_tls=use_tls,
        root_pem_cacerts=get_ca_certificate() if use_tls else None,
        addresses=[address],
    )


@pytest.mark.skipif(
    not os.getenv("VALKEY_GLIDE_DNS_TESTS_ENABLED"),
    reason="DNS tests disabled. Set VALKEY_GLIDE_DNS_TESTS_ENABLED to enable.",
)
@pytest.mark.parametrize("cluster_mode", [True, False])
class TestSyncDns:
    """Sync DNS resolution tests."""

    def test_connect_with_valid_hostname_succeeds(self, _, cluster_mode: bool):
        """Test connection with valid hostname (non-TLS)."""
        client = create_client_with_hostname(
            cluster_mode=cluster_mode,
            use_tls=False,
            hostname=HOSTNAME_NO_TLS,
        )

        assert_connected_sync(client)
        client.close()

    def test_connect_with_invalid_hostname_fails(self, _, cluster_mode: bool):
        """Test connection with invalid hostname (non-TLS)."""
        with pytest.raises(Exception):
            create_client_with_hostname(
                cluster_mode=cluster_mode,
                use_tls=False,
                hostname="nonexistent.invalid",
            )

    def test_tls_with_hostname_in_certificate_succeeds(self, _, cluster_mode: bool):
        """Test TLS connection with hostname in certificate SAN."""
        client = create_client_with_hostname(
            cluster_mode=cluster_mode,
            use_tls=True,
            hostname=HOSTNAME_TLS,
        )

        assert_connected_sync(client)
        client.close()

    def test_tls_with_hostname_not_in_certificate_fails(self, _, cluster_mode: bool):
        """Test TLS connection with hostname not in certificate SAN."""

        with pytest.raises(Exception):
            create_client_with_hostname(
                cluster_mode=cluster_mode,
                use_tls=True,
                hostname=HOSTNAME_NO_TLS,
            )
