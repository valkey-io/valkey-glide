# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Cluster slot-zero affinity regressions for sync isolated scopes."""

import binascii
import uuid

import pytest
from glide_shared.routes import SlotIdRoute, SlotType
from glide_sync import GlideClusterClient, GlideClusterClientConfiguration

from tests.utils.utils import get_cluster_addresses

CLUSTER_SLOT_COUNT = 16_384
# Bound routed topology probes while sampling enough tags to find another primary.
MAX_OTHER_PRIMARY_CANDIDATES = 512


def _slot_for_tag(tag: str) -> int:
    """Compute the cluster slot for a hash tag."""
    return binascii.crc_hqx(tag.encode(), 0) % CLUSTER_SLOT_COUNT


def _keys_on_slot_zero_and_another_primary(
    client: GlideClusterClient,
) -> tuple[str, str]:
    """Find keys for slot zero and a slot owned by another primary."""
    slot_zero_tag = "06S"
    assert _slot_for_tag(slot_zero_tag) == 0
    slot_zero_owner = client.custom_command(
        ["CLUSTER", "MYID"], route=SlotIdRoute(SlotType.PRIMARY, 0)
    )

    for index in range(MAX_OTHER_PRIMARY_CANDIDATES):
        tag = f"scope-other-{index}"
        slot = _slot_for_tag(tag)
        if slot == 0:
            continue
        owner = client.custom_command(
            ["CLUSTER", "MYID"], route=SlotIdRoute(SlotType.PRIMARY, slot)
        )
        if owner != slot_zero_owner:
            suffix = uuid.uuid4().hex[:8]
            return f"{{{slot_zero_tag}}}-scope-{suffix}", f"{{{tag}}}-scope-{suffix}"

    pytest.skip("Cluster does not expose another primary for slot-affinity testing")


@pytest.mark.parametrize("slot_zero_first", [True, False])
def test_sync_scope_slot_zero_affinity_cluster(slot_zero_first):
    """Slot zero never reuses a scope targeting another cluster primary."""
    addresses = get_cluster_addresses()

    client = GlideClusterClient.create(
        GlideClusterClientConfiguration(addresses=addresses, request_timeout=5000)
    )
    try:
        slot_zero_key, other_primary_key = _keys_on_slot_zero_and_another_primary(
            client
        )
        first_key, second_key = (
            (slot_zero_key, other_primary_key)
            if slot_zero_first
            else (other_primary_key, slot_zero_key)
        )

        with client.scoped_connection(routing_key=first_key) as scope:
            assert scope.set(first_key, "first") is not None
            assert scope.get(first_key) == "first"

        with client.scoped_connection(routing_key=second_key) as scope:
            assert scope.set(second_key, "second") is not None
            assert scope.get(second_key) == "second"

        client.delete([slot_zero_key, other_primary_key])
    finally:
        client.close()
