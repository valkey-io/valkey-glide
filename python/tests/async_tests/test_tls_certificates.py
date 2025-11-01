# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Union

import pytest
from glide.glide_client import GlideClient, GlideClusterClient
from glide_shared.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ProtocolVersion,
    TlsAdvancedConfiguration,
)

from tests.utils.utils import get_ca_certificate


@pytest.mark.anyio
class TestTlsCertificates:
    """
    Integration tests for TLS with custom root certificates.
    These tests verify the root_pem_cacerts functionality.
    """

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_without_certificate_fails(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that connection fails when TLS is enabled but no certificate is provided.
        """
        # Get the TLS cluster addresses
        valkey_cluster = (
            pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster  # type: ignore
        )

        if cluster_mode:
            cluster_config = GlideClusterClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
            )
            with pytest.raises(Exception):
                await GlideClusterClient.create(cluster_config)
        else:
            standalone_config = GlideClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
            )
            with pytest.raises(Exception):
                await GlideClient.create(standalone_config)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_self_signed_certificate_succeeds(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that connection succeeds with custom root certificates.
        """
        ca_cert = get_ca_certificate()
        tls_config = TlsAdvancedConfiguration(root_pem_cacerts=ca_cert)

        # Get the TLS cluster addresses
        valkey_cluster = (
            pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster  # type: ignore
        )

        client: Union[GlideClient, GlideClusterClient]
        if cluster_mode:
            cluster_advanced_config = AdvancedGlideClusterClientConfiguration(
                tls_config=tls_config
            )
            cluster_config = GlideClusterClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=cluster_advanced_config,
            )
            client = await GlideClusterClient.create(cluster_config)
        else:
            standalone_advanced_config = AdvancedGlideClientConfiguration(
                tls_config=tls_config
            )
            standalone_config = GlideClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=standalone_advanced_config,
            )
            client = await GlideClient.create(standalone_config)

        try:
            result = await client.ping()
            assert result == b"PONG"
        finally:
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_multiple_certificates_succeeds(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that connection succeeds with multiple concatenated certificates.
        """
        ca_cert = get_ca_certificate()
        # Concatenate the same certificate twice to simulate multiple certificates
        multiple_certs = ca_cert + b"\n" + ca_cert

        tls_config = TlsAdvancedConfiguration(root_pem_cacerts=multiple_certs)

        # Get the TLS cluster addresses
        valkey_cluster = (
            pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster  # type: ignore
        )

        client: Union[GlideClient, GlideClusterClient]
        if cluster_mode:
            cluster_advanced_config = AdvancedGlideClusterClientConfiguration(
                tls_config=tls_config
            )
            cluster_config = GlideClusterClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=cluster_advanced_config,
            )
            client = await GlideClusterClient.create(cluster_config)
        else:
            standalone_advanced_config = AdvancedGlideClientConfiguration(
                tls_config=tls_config
            )
            standalone_config = GlideClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=standalone_advanced_config,
            )
            client = await GlideClient.create(standalone_config)

        try:
            result = await client.ping()
            assert result == b"PONG"
        finally:
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_empty_certificate_fails(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that empty certificate array returns an error.
        """
        empty_cert = b""
        tls_config = TlsAdvancedConfiguration(root_pem_cacerts=empty_cert)

        # Get the TLS cluster addresses
        valkey_cluster = (
            pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster  # type: ignore
        )

        if cluster_mode:
            cluster_advanced_config = AdvancedGlideClusterClientConfiguration(
                tls_config=tls_config
            )
            cluster_config = GlideClusterClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=cluster_advanced_config,
            )
            with pytest.raises(Exception):
                await GlideClusterClient.create(cluster_config)
        else:
            standalone_advanced_config = AdvancedGlideClientConfiguration(
                tls_config=tls_config
            )
            standalone_config = GlideClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=standalone_advanced_config,
            )
            with pytest.raises(Exception):
                await GlideClient.create(standalone_config)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_tls_with_invalid_certificate_fails(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Verify that invalid certificate returns an error.
        """
        invalid_cert = (
            b"-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----"
        )
        tls_config = TlsAdvancedConfiguration(root_pem_cacerts=invalid_cert)

        # Get the TLS cluster addresses
        valkey_cluster = (
            pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster  # type: ignore
        )

        if cluster_mode:
            cluster_advanced_config = AdvancedGlideClusterClientConfiguration(
                tls_config=tls_config
            )
            cluster_config = GlideClusterClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=cluster_advanced_config,
            )
            with pytest.raises(Exception):
                await GlideClusterClient.create(cluster_config)
        else:
            standalone_advanced_config = AdvancedGlideClientConfiguration(
                tls_config=tls_config
            )
            standalone_config = GlideClientConfiguration(
                addresses=valkey_cluster.nodes_addr,
                use_tls=True,
                protocol=protocol,
                advanced_config=standalone_advanced_config,
            )
            with pytest.raises(Exception):
                await GlideClient.create(standalone_config)
