# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide.glide_client import TGlideClient
from glide_shared.config import (
    ConfigurationError,
    NodeAddress,
    ProtocolVersion,
)

from tests.async_tests.conftest import create_client
from tests.test_constants import HOST_ADDRESS_IPV4, HOST_ADDRESS_IPV6
from tests.utils.utils import (
    assert_connected,
    create_client_config,
    create_client_with_retry,
    get_ca_certificate,
    get_client_certificate,
    get_client_key,
)


@pytest.mark.anyio
class TestTls:
    """
    Integration tests for TLS with custom root certificates.
    """

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_without_certificate_fails(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that connection fails when TLS is enabled but no certificate is provided.
        """
        certificate = None

        with pytest.raises(Exception):
            await create_client(
                request=request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                use_tls=True,
                root_pem_cacerts=certificate,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_self_signed_certificate_succeeds(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that connection succeeds with custom root certificates.
        """
        certificate = get_ca_certificate()

        client = await create_client(
            request=request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            use_tls=True,
            root_pem_cacerts=certificate,
        )

        await assert_connected(client)
        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_multiple_certificates_succeeds(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that connection succeeds with multiple concatenated certificates.
        """
        certificate = get_ca_certificate()
        multiple_certificates = certificate + b"\n" + certificate

        client = await create_client(
            request=request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            use_tls=True,
            root_pem_cacerts=multiple_certificates,
        )

        await assert_connected(client)
        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_empty_certificate_fails(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that empty certificate array returns an error.
        """
        empty_certificate = b""

        with pytest.raises(Exception):
            await create_client(
                request=request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                use_tls=True,
                root_pem_cacerts=empty_certificate,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_invalid_certificate_fails(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that invalid certificate returns an error.
        """
        invalid_certificate = b"""
        -----BEGIN CERTIFICATE-----
        INVALID
        -----END CERTIFICATE-----
        """

        with pytest.raises(Exception):
            await create_client(
                request=request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                use_tls=True,
                root_pem_cacerts=invalid_certificate,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("tls_insecure", [True], indirect=True)
    async def test_tls_insecure_succeeds(self, glide_tls_client: TGlideClient):
        """
        This test verifies that the Glide client can connect to a TLS-enabled Valkey instance while skipping
        certificate validation. By parametrizing tls_insecure=True, we confirm that the client can successfully
        ping the cluster without strict cert checks.
        """
        await assert_connected(glide_tls_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("ip_address", [HOST_ADDRESS_IPV4, HOST_ADDRESS_IPV6])
    async def test_tls_with_ip_address_connection_succeeds(
        self, request, cluster_mode: bool, protocol: ProtocolVersion, ip_address: str
    ):
        """Test TLS connection with IPv4 and IPv6 addresses."""
        cluster = pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster  # type: ignore
        port = cluster.nodes_addr[0].port
        address = NodeAddress(ip_address, port)

        client = await create_client(
            request=request,
            cluster_mode=cluster_mode,
            addresses=[address],
            protocol=protocol,
            use_tls=True,
            root_pem_cacerts=get_ca_certificate(),
        )

        await assert_connected(client)
        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_fails_if_client_cert_provided_but_private_key_not_provided(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        server_certificate = get_ca_certificate()
        client_certificate = get_client_certificate()

        with pytest.raises(ConfigurationError):
            await create_client(
                request=request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                use_tls=True,
                root_pem_cacerts=server_certificate,
                client_cert_pem=client_certificate,
                client_key_pem=None,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_fails_if_client_cert_not_provided_but_private_key_is_provided(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        server_certificate = get_ca_certificate()
        client_key = get_client_key()

        with pytest.raises(ConfigurationError):
            await create_client(
                request=request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                use_tls=True,
                root_pem_cacerts=server_certificate,
                client_cert_pem=None,
                client_key_pem=client_key,
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_inits_if_client_cert_and_client_key_provided(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        server_certificate = get_ca_certificate()
        client_certificate = get_client_certificate()
        client_key = get_client_key()

        config = create_client_config(
            request=request,
            cluster_mode=cluster_mode,
            protocol=protocol,
            use_tls=True,
            root_pem_cacerts=server_certificate,
            client_cert_pem=client_certificate,
            client_key_pem=client_key,
        )
        client = await create_client_with_retry(config)

        await assert_connected(client)
        await client.close()
