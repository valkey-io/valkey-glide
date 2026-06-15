# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from glide_shared.opentelemetry import OpenTelemetry as _BaseOpenTelemetry
from glide_shared.opentelemetry import (  # noqa: F401
    OpenTelemetryConfig,
    OpenTelemetryMetricsConfig,
    OpenTelemetryTracesConfig,
)


class OpenTelemetry(_BaseOpenTelemetry):
    """Sync client OpenTelemetry singleton (separate from async)."""

    _instance = None
    _config = None
