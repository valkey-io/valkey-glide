# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
OpenTelemetry configuration classes shared between async and sync clients.

⚠️ OpenTelemetry can only be initialized once per process. Calling `OpenTelemetry.init()` more than once will be ignored.
If you need to change configuration, restart the process with new settings.

### OpenTelemetry Configuration

- **OpenTelemetryConfig**: Main configuration object for OpenTelemetry exporters and options.
  - **traces**: (optional) Configure trace exporting using OpenTelemetryTracesConfig.
    - **endpoint**: The collector endpoint for traces. Supported protocols:
      - `http://` or `https://` for HTTP/HTTPS
      - `grpc://` for gRPC
      - `file://` for local file export (see below)
    - **sample_percentage**: (optional) The percentage of requests to sample and create a span for, used to measure command duration. Must be between 0 and 100. Defaults to 1 if not specified.
      Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
      It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
  - **metrics**: (optional) Configure metrics exporting using OpenTelemetryMetricsConfig.
    - **endpoint**: The collector endpoint for metrics. Same protocol rules as above.
  - **flush_interval_ms**: (optional) Interval in milliseconds for flushing data to the collector. Must be a positive integer. Defaults to 5000ms if not specified.

#### File Exporter Details
- For `file://` endpoints:
  - The path must start with `file://` (e.g., `file:///tmp/otel` or `file:///tmp/otel/traces.json`).
  - If the path is a directory or lacks a file extension, data is written to `signals.json` in that directory.
  - If the path includes a filename with an extension, that file is used as-is.
  - The parent directory must already exist; otherwise, initialization will fail with an InvalidInput error.
  - If the target file exists, new data is appended (not overwritten).

#### Validation Rules
- `flush_interval_ms` must be a positive integer.
- `sample_percentage` must be between 0 and 100.
- File exporter paths must start with `file://` and have an existing parent directory.
- Invalid configuration will throw an error synchronously when calling `OpenTelemetry.init()`.
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
