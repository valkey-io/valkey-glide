# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Any, Union, cast

import anyio
import pytest

from glide.config import ProtocolVersion
from glide.glide_client import GlideClient, GlideClusterClient
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


async def get_standalone_client_count(client: GlideClient) -> int:
    """Gets client count for a standalone server."""
    result: Any = await client.custom_command(["CLIENT", "LIST"])
    return await get_client_list_output_count(cast(Union[bytes, str, None], result))


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

            # 2. Get initial client count (standalone mode only)
            if not cluster_mode:
                clients_before_lazy_init = await get_standalone_client_count(
                    cast(GlideClient, monitoring_client)
                )

            # 3. Create the "lazy" client
            lazy_glide_client = await create_client(
                request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                lazy_connect=True,  # Lazy
                request_timeout=3000,
                connection_timeout=3000,
            )

            # 4. Check count (should not change) (standalone mode only)
            if not cluster_mode:
                clients_after_lazy_init = await get_standalone_client_count(
                    cast(GlideClient, monitoring_client)
                )
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
            # In cluster mode, the client count is not reliable due to the nature of cluster connections.
            if not cluster_mode:
                await anyio.sleep(0.5)
                clients_after_first_command = await get_standalone_client_count(
                    cast(GlideClient, monitoring_client)
                )
                print(
                    f"{mode_str} - Protocol: {protocol}, Clients after first command: {clients_after_first_command}"
                )
                assert clients_after_first_command == clients_before_lazy_init + 1, (
                    f"Lazy client (standalone, {protocol}) should establish one new connection after the first command. "
                    f"Before: {clients_before_lazy_init}, After first command: {clients_after_first_command}"
                )

        finally:
            if monitoring_client:
                await monitoring_client.close()
            if lazy_glide_client:
                await lazy_glide_client.close()
