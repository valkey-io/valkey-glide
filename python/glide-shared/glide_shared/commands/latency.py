# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Response models and parsing helpers for ``LATENCY`` commands."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, cast

from glide_shared.constants import TClusterResponse


@dataclass
class LatencyEntry:
    """Represents the time and latency for a latency spike.

    Attributes:
        time (int): The time of the latency spike, as a Unix timestamp in seconds.
        latency (int): The duration of the latency spike, in milliseconds.
    """

    time: int
    latency: int


@dataclass
class LatencyEventInfo:
    """Represents information about an event's latency spike time series.

    Attributes:
        event_name (str): The name of the event.
        time (int): The time of the latest latency spike, as a Unix
            timestamp in seconds.
        latest (int): The duration of the latest latency spike, in milliseconds.
        maximum (int): The all-time maximum duration of a latency spike, in milliseconds.
        sum (Optional[int]): The sum of all latency spike durations in the event's
            time series, in milliseconds. Only populated for Valkey 8.1+.
        count (Optional[int]): The number of latency spikes recorded in the event's
            time series. Only populated for Valkey 8.1+.
    """

    event_name: str
    time: int
    latest: int
    maximum: int
    sum: Optional[int] = None
    count: Optional[int] = None


def _parse_latency_history(response: List) -> List[LatencyEntry]:
    """Parses a ``LATENCY HISTORY`` response."""
    return [
        LatencyEntry(time=int(entry[0]), latency=int(entry[1])) for entry in response
    ]


def _parse_latency_latest(response: List) -> List[LatencyEventInfo]:
    """Parses a ``LATENCY LATEST`` response."""
    result: List[LatencyEventInfo] = []
    for entry in response:
        sum_value = int(entry[4]) if len(entry) > 4 else None
        count_value = int(entry[5]) if len(entry) > 5 else None
        result.append(
            LatencyEventInfo(
                event_name=(
                    entry[0].decode() if isinstance(entry[0], bytes) else entry[0]
                ),
                time=int(entry[1]),
                latest=int(entry[2]),
                maximum=int(entry[3]),
                sum=sum_value,
                count=count_value,
            )
        )
    return result


def _parse_latency_history_cluster(
    response: object,
) -> TClusterResponse[List[LatencyEntry]]:
    """Parses a cluster ``LATENCY HISTORY`` response."""
    if isinstance(response, dict):
        per_node = cast(Dict[bytes, object], response)
        return {
            addr: _parse_latency_history(cast(List, value))
            for addr, value in per_node.items()
        }
    return _parse_latency_history(cast(List, response))


def _parse_latency_latest_cluster(
    response: object,
) -> TClusterResponse[List[LatencyEventInfo]]:
    """Parses a cluster ``LATENCY LATEST`` response."""
    if isinstance(response, dict):
        per_node = cast(Dict[bytes, object], response)
        return {
            addr: _parse_latency_latest(cast(List, value))
            for addr, value in per_node.items()
        }
    return _parse_latency_latest(cast(List, response))
