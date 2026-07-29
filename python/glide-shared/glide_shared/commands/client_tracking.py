# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Response models and parsing helpers for ``CLIENT TRACKINGINFO`` command."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Mapping, Set, Union, cast


@dataclass
class ClientTrackingInfo:
    """Represents a `CLIENT TRACKINGINFO <https://valkey.io/commands/client-trackinginfo/>`_ response."""

    flags: Set[str]
    """Set of tracking flags."""

    redirect: int
    """Client ID receiving invalidation messages, or ``-1`` if not redirecting."""

    prefixes: Set[str]
    """Set of key prefixes monitored for invalidation."""


def _parse_client_tracking_info(response: Mapping[bytes, Any]) -> ClientTrackingInfo:
    """Parses a ``CLIENT TRACKINGINFO`` response."""
    return ClientTrackingInfo(
        flags={f.decode() for f in response[b"flags"]},
        redirect=int(response[b"redirect"]),
        prefixes={p.decode() for p in response[b"prefixes"]},
    )


def _parse_client_tracking_info_cluster(
    response: Mapping[bytes, Any],
) -> Union[ClientTrackingInfo, Dict[bytes, ClientTrackingInfo]]:
    """Parses a cluster ``CLIENT TRACKINGINFO`` response."""
    if b"flags" in response:
        return _parse_client_tracking_info(response)
    return {
        addr: _parse_client_tracking_info(cast(Mapping, value))
        for addr, value in response.items()
    }
