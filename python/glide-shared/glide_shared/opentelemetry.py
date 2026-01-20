# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
OpenTelemetry configuration classes shared between async and sync clients.
"""

from typing import Optional


class OpenTelemetryTracesConfig:
    """Configuration for exporting OpenTelemetry traces."""

    def __init__(self, endpoint: str, sample_percentage: Optional[int] = None) -> None:
        self.endpoint = endpoint
        self.sample_percentage = (
            sample_percentage if sample_percentage is not None else 1
        )

    def get_endpoint(self) -> str:
        return self.endpoint

    def get_sample_percentage(self) -> int:
        return self.sample_percentage


class OpenTelemetryMetricsConfig:
    """Configuration for exporting OpenTelemetry metrics."""

    def __init__(self, endpoint: str) -> None:
        self.endpoint = endpoint

    def get_endpoint(self) -> str:
        return self.endpoint


class OpenTelemetryConfig:
    """Configuration for OpenTelemetry integration."""

    def __init__(
        self,
        traces: Optional[OpenTelemetryTracesConfig] = None,
        metrics: Optional[OpenTelemetryMetricsConfig] = None,
        flush_interval_ms: Optional[int] = None,
    ) -> None:
        self.traces = traces
        self.metrics = metrics
        self.flush_interval_ms = flush_interval_ms

    def get_traces(self) -> Optional[OpenTelemetryTracesConfig]:
        return self.traces

    def set_traces(self, traces: OpenTelemetryTracesConfig) -> None:
        self.traces = traces

    def get_metrics(self) -> Optional[OpenTelemetryMetricsConfig]:
        return self.metrics

    def get_flush_interval_ms(self) -> Optional[int]:
        return self.flush_interval_ms
