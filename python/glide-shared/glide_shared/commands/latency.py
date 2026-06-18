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
        latest_time (int): The time of the latest latency spike, as a Unix
            timestamp in seconds.
        latest_duration (int): The duration of the latest latency spike, in milliseconds.
        max_duration (int): The all-time maximum duration of a latency spike, in milliseconds.
        sum (Optional[int]): The sum of all latency spike durations in the event's
            time series, in milliseconds. Only populated for Valkey 8.1+.
        count (Optional[int]): The number of latency spikes recorded in the event's
            time series. Only populated for Valkey 8.1+.
    """

    event_name: str
    latest_time: int
    latest_duration: int
    max_duration: int
    sum: Optional[int] = None
    count: Optional[int] = None


# Indices for LATENCY HISTORY response.
_LATENCY_ENTRY_TIME_INDEX = 0
_LATENCY_ENTRY_LATENCY_INDEX = 1

# Indices for LATENCY LATEST response.
_LATENCY_EVENT_INFO_NAME_INDEX = 0
_LATENCY_EVENT_INFO_TIME_INDEX = 1
_LATENCY_EVENT_INFO_LATEST_DURATION_INDEX = 2
_LATENCY_EVENT_INFO_MAX_DURATION_INDEX = 3
_LATENCY_EVENT_INFO_SUM_INDEX = 4
_LATENCY_EVENT_INFO_COUNT_INDEX = 5


def _parse_latency_history(response: List) -> List[LatencyEntry]:
    """Parses a ``LATENCY HISTORY`` response."""
    return [
        LatencyEntry(
            time=int(entry[_LATENCY_ENTRY_TIME_INDEX]),
            latency=int(entry[_LATENCY_ENTRY_LATENCY_INDEX]),
        )
        for entry in response
    ]


def _parse_latency_latest(response: List) -> List[LatencyEventInfo]:
    """Parses a ``LATENCY LATEST`` response."""
    result: List[LatencyEventInfo] = []
    for entry in response:
        sum_value = (
            int(entry[_LATENCY_EVENT_INFO_SUM_INDEX])
            if len(entry) > _LATENCY_EVENT_INFO_SUM_INDEX
            else None
        )
        count_value = (
            int(entry[_LATENCY_EVENT_INFO_COUNT_INDEX])
            if len(entry) > _LATENCY_EVENT_INFO_COUNT_INDEX
            else None
        )
        result.append(
            LatencyEventInfo(
                event_name=(
                    entry[_LATENCY_EVENT_INFO_NAME_INDEX].decode()
                    if isinstance(entry[_LATENCY_EVENT_INFO_NAME_INDEX], bytes)
                    else entry[_LATENCY_EVENT_INFO_NAME_INDEX]
                ),
                latest_time=int(entry[_LATENCY_EVENT_INFO_TIME_INDEX]),
                latest_duration=int(entry[_LATENCY_EVENT_INFO_LATEST_DURATION_INDEX]),
                max_duration=int(entry[_LATENCY_EVENT_INFO_MAX_DURATION_INDEX]),
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
