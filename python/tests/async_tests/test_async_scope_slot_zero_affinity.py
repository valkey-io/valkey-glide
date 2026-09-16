# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Cluster slot-zero affinity regressions for async isolated scopes."""

import binascii
import uuid

import pytest
from glide import GlideClusterClient, GlideClusterClientConfiguration
from glide_shared.routes import SlotIdRoute, SlotType

from tests.utils.utils import get_cluster_addresses

pytestmark = pytest.mark.asyncio

CLUSTER_SLOT_COUNT = 16_384
# Bound routed topology probes while sampling enough tags to find another primary.
MAX_OTHER_PRIMARY_CANDIDATES = 512


def _skip_cluster_if_unavailable():
    """Skip test if no cluster endpoints are configured."""
    try:
        cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        if cluster is None or len(cluster.nodes_addr) == 0:
            pytest.skip("No cluster endpoints available")
    except AttributeError:
        pytest.skip("No cluster endpoints available (pytest.valkey_cluster not set)")


def _slot_for_tag(tag: str) -> int:
    """Compute the cluster slot for a hash tag."""
    return binascii.crc_hqx(tag.encode(), 0) % CLUSTER_SLOT_COUNT


async def _keys_on_slot_zero_and_another_primary(client) -> tuple[str, str]:
    """Find keys for slot zero and a slot owned by another primary."""
    slot_zero_tag = "06S"
    assert _slot_for_tag(slot_zero_tag) == 0
    slot_zero_owner = await client.custom_command(
        ["CLUSTER", "MYID"], route=SlotIdRoute(SlotType.PRIMARY, 0)
    )

    for index in range(MAX_OTHER_PRIMARY_CANDIDATES):
        tag = f"scope-other-{index}"
        slot = _slot_for_tag(tag)
        if slot == 0:
            continue
        owner = await client.custom_command(
            ["CLUSTER", "MYID"], route=SlotIdRoute(SlotType.PRIMARY, slot)
        )
        if owner != slot_zero_owner:
            suffix = uuid.uuid4().hex[:8]
            return f"{{{slot_zero_tag}}}-scope-{suffix}", f"{{{tag}}}-scope-{suffix}"

    pytest.skip("Cluster does not expose another primary for slot-affinity testing")


@pytest.mark.parametrize("slot_zero_first", [True, False])
async def test_scope_slot_zero_affinity_cluster(slot_zero_first):
    """Slot zero never reuses a scope targeting another cluster primary."""
    # get_cluster_addresses() falls back to localhost:7000, so without this guard a
    # standalone-only run fails on a refused connection instead of skipping. The
    # collection hook cannot rescue it: this test parameterizes on slot_zero_first
    # rather than cluster_mode.
    _skip_cluster_if_unavailable()
    addresses = get_cluster_addresses()

    client = await GlideClusterClient.create(
        GlideClusterClientConfiguration(addresses=addresses, request_timeout=5000)
    )
    try:
        slot_zero_key, other_primary_key = await _keys_on_slot_zero_and_another_primary(
            client
        )
        first_key, second_key = (
            (slot_zero_key, other_primary_key)
            if slot_zero_first
            else (other_primary_key, slot_zero_key)
        )

        async with await client.scoped_connection(routing_key=first_key) as scope:
            assert await scope.set(first_key, "first") is not None
            assert await scope.get(first_key) == "first"

        async with await client.scoped_connection(routing_key=second_key) as scope:
            assert await scope.set(second_key, "second") is not None
            assert await scope.get(second_key) == "second"

        await client.delete([slot_zero_key, other_primary_key])
    finally:
        await client.aclose()
