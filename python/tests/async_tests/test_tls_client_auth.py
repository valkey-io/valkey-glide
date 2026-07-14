# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
mTLS client-certificate enforcement tests.

Unlike the tests in ``test_tls.py``, which run against a cluster started with
``--tls-auth-clients no`` (so the server never verifies the client cert), these
tests spin up a self-managed cluster with ``--tls-auth-clients yes``. That
server genuinely requires and verifies the certificate the client presents,
which lets us prove both directions:

  * connecting WITH the dedicated client cert + key succeeds, and
  * connecting WITHOUT a client cert fails.

The cluster is created once per module and torn down at the end. Tests are kept
serial and self-contained rather than wiring an enforcing cluster through the
session-scoped conftest fixtures.
"""

import pytest
from glide_shared.config import ProtocolVersion

from tests.async_tests.conftest import create_client
from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import (
    assert_connected,
    get_ca_certificate,
    get_client_auth_certificate,
    get_client_auth_key,
)


@pytest.fixture(scope="module")
def tls_auth_cluster():
    """
    Start a standalone TLS cluster that enforces client certificates
    (``--tls-auth-clients yes``) for the duration of this module.
    """
    cluster = ValkeyCluster(
        tls=True,
        cluster_mode=False,
        shard_count=1,
        replica_count=0,
        tls_auth_clients="yes",
    )
    yield cluster
    del cluster


@pytest.mark.anyio
class TestTlsClientAuth:
    """
    Integration tests proving the server verifies the client certificate.
    """

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_auth_with_client_cert_succeeds(
        self, request, tls_auth_cluster: ValkeyCluster, protocol: ProtocolVersion
    ):
        """
        Connecting to a client-cert-enforcing cluster WITH the dedicated client
        certificate and key succeeds.
        """
        client = await create_client(
            request=request,
            cluster_mode=False,
            protocol=protocol,
            use_tls=True,
            valkey_cluster=tls_auth_cluster,
            root_pem_cacerts=get_ca_certificate(),
            client_cert_pem=get_client_auth_certificate(),
            client_key_pem=get_client_auth_key(),
            request_timeout=5000,
        )

        await assert_connected(client)
        await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_auth_without_client_cert_fails(
        self, request, tls_auth_cluster: ValkeyCluster, protocol: ProtocolVersion
    ):
        """
        Connecting to a client-cert-enforcing cluster WITHOUT a client
        certificate fails, since the server requires one.
        """
        with pytest.raises(Exception):
            await create_client(
                request=request,
                cluster_mode=False,
                protocol=protocol,
                use_tls=True,
                valkey_cluster=tls_auth_cluster,
                root_pem_cacerts=get_ca_certificate(),
                request_timeout=5000,
            )
