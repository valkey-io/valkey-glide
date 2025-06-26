# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Any, Union, cast

import pytest

from glide.config import NodeAddress, ProtocolVersion
from glide.glide_client import GlideClient, GlideClusterClient
from glide.routes import AllNodes
from tests.conftest import create_client


async def get_client_list_output_count(output: Union[bytes, str, None]) -> int:
    """Parses CLIENT LIST output and returns the number of clients."""
    if output is None:
        return 0
    text = output.decode(errors="ignore") if isinstance(output, bytes) else str(output)
    stripped_text = text.strip()
    if not stripped_text:
        return 0
    return len([line for line in stripped_text.split("\n") if line.strip()])


async def get_client_count(client: Union[GlideClient, GlideClusterClient]) -> int:
    """Get client connection count for either standalone or cluster client."""

    if isinstance(client, GlideClusterClient):
        # For cluster client, execute CLIENT LIST on all nodes
        result: Any = await client.custom_command(["CLIENT", "LIST"], route=AllNodes())

        # Result will be a dict with node addresses as keys and CLIENT LIST output as values
        total_count = 0
        for node_output in result.values():
            total_count += await get_client_list_output_count(
                cast(Union[bytes, str, None], node_output)
            )

        return total_count
    else:
        # For standalone client, execute CLIENT LIST directly
        result_standalone: Any = await client.custom_command(["CLIENT", "LIST"])
        return await get_client_list_output_count(
            cast(Union[bytes, str, None], result_standalone)
        )


async def get_expected_new_connections(
    client: Union[GlideClient, GlideClusterClient],
) -> int:
    """Get the expected number of new connections when a lazy client is initialized."""

    if isinstance(client, GlideClusterClient):
        # For cluster, get node count and multiply by 2 (2 connections per node)
        result = await client.custom_command(["CLUSTER", "NODES"])

        # Handle the type explicitly before calling decode()
        if isinstance(result, bytes):
            nodes_info = result.decode().strip().split("\n")
        else:
            # Fall back to string conversion if it's not bytes
            nodes_info = str(result).strip().split("\n")

        return len(nodes_info) * 2
    else:
        # For standalone, always expect 1 new connection
        return 1


@pytest.mark.anyio
@pytest.mark.parametrize("cluster_mode", [False, True])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
class TestLazyConnection:
    async def test_lazy_connection_establishes_on_first_command(
        self,
        request: Any,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        monitoring_client: Union[GlideClient, GlideClusterClient, None] = None
        lazy_glide_client: Union[GlideClient, GlideClusterClient, None] = None
        mode_str = "Cluster" if cluster_mode else "Standalone"

        clients_before_lazy_init = 0
        clients_after_lazy_init = 0

        try:
            # 1. Create a monitoring client (eagerly connected)
            monitoring_client = await create_client(
                request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                lazy_connect=False,
                request_timeout=3000,
                connection_timeout=3000,
            )

            if cluster_mode:
                assert isinstance(monitoring_client, GlideClusterClient)
            else:
                assert isinstance(monitoring_client, GlideClient)
            await monitoring_client.ping()

            # 2. Get initial client count
            clients_before_lazy_init = await get_client_count(monitoring_client)

            # 3. Create the "lazy" client
            lazy_glide_client = await create_client(
                request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                lazy_connect=True,  # Lazy
                request_timeout=3000,
                connection_timeout=3000,
            )

            # 4. Check count (should not change)
            clients_after_lazy_init = await get_client_count(monitoring_client)
            assert clients_after_lazy_init == clients_before_lazy_init, (
                f"Lazy client ({mode_str.lower()}, {protocol}) should not connect before the first command. "
                f"Before: {clients_before_lazy_init}, After: {clients_after_lazy_init}"
            )

            # 5. Send the first command using the lazy client
            ping_response: Any = await lazy_glide_client.ping()

            decoded_ping_response: Union[str, dict[str, str]]
            if isinstance(ping_response, bytes):
                decoded_ping_response = ping_response.decode()
            elif isinstance(ping_response, str):
                decoded_ping_response = ping_response
            elif isinstance(ping_response, dict):
                decoded_ping_response = {}
                for k, v_bytes_or_str in ping_response.items():
                    key_str = k.decode() if isinstance(k, bytes) else str(k)
                    val_str = (
                        v_bytes_or_str.decode()
                        if isinstance(v_bytes_or_str, bytes)
                        else str(v_bytes_or_str)
                    )
                    decoded_ping_response[key_str] = val_str
            else:
                decoded_ping_response = str(ping_response)

            # Assert PING success for both modes
            if cluster_mode:
                is_pong_str = decoded_ping_response == "PONG"
                is_pong_dict = isinstance(decoded_ping_response, dict) and all(
                    v == "PONG" for v in decoded_ping_response.values()
                )
                assert (
                    is_pong_str or is_pong_dict
                ), f"PING response was not 'PONG' or a dict of 'PONG's: {decoded_ping_response}"
            else:
                assert (
                    decoded_ping_response == "PONG"
                ), f"PING response was not 'PONG': {decoded_ping_response}"

            # 6. Check client count after the first command
            clients_after_first_command = await get_client_count(monitoring_client)
            expected_new_connections = await get_expected_new_connections(
                monitoring_client
            )

            assert (
                clients_after_first_command
                == clients_before_lazy_init + expected_new_connections
            ), (
                f"Lazy client ({mode_str.lower()}, {protocol}) should establish {expected_new_connections} "
                f"new connection(s) after the first command. "
                f"Before: {clients_before_lazy_init}, After first command: {clients_after_first_command}"
            )

        finally:
            if monitoring_client:
                await monitoring_client.close()
            if lazy_glide_client:
                await lazy_glide_client.close()

    async def test_lazy_connection_with_non_existent_host(
        self,
        request: Any,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """
        Test behavior with non-existent host for both eager and lazy connections.

        Expected behavior:
        1. Eager connection to non-existent host - should fail immediately during client creation
        2. Lazy connection to non-existent host - should succeed in client creation but fail on command execution
        """
        non_existent_host = "non-existent-host-that-does-not-resolve"

        # First create a monitoring client to ensure the test environment is working
        monitoring_client = None
        try:

            # Test 1: Eager connection to non-existent host should fail
            with pytest.raises(Exception) as excinfo:
                # Attempt to create client with eager connection - should fail immediately
                await create_client(
                    request,
                    cluster_mode=cluster_mode,
                    protocol=protocol,
                    lazy_connect=False,  # Eager
                    request_timeout=500,
                    addresses=[NodeAddress(non_existent_host)],
                )

            # Verify the error message contains expected text
            error_msg = str(excinfo.value).lower()
            # Check for different possible error messages depending on implementation
            assert any(
                x in error_msg
                for x in ["ioerror", "connection", "network", "host", "resolve"]
            )

            # Test 2: Lazy connection to non-existent host should succeed in client creation
            lazy_client = None
            try:
                # This should succeed since we're using lazy connection
                lazy_client = await create_client(
                    request,
                    cluster_mode=cluster_mode,
                    protocol=protocol,
                    lazy_connect=True,  # Lazy
                    request_timeout=500,
                    addresses=[NodeAddress(non_existent_host)],
                )

                # But command execution should fail with appropriate error
                with pytest.raises(Exception) as cmd_excinfo:
                    await lazy_client.ping()

                # Verify the error message contains expected text
                cmd_error_msg = str(cmd_excinfo.value).lower()
                assert any(
                    x in cmd_error_msg
                    for x in ["ioerror", "connection", "network", "host", "resolve"]
                )

            finally:
                if lazy_client:
                    await lazy_client.close()

        finally:
            if monitoring_client:
                await monitoring_client.close()
