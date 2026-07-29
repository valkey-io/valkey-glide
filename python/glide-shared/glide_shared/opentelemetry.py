# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
OpenTelemetry configuration classes shared between async and sync clients.

OpenTelemetry can only be initialized once per process. Calling OpenTelemetry.init()
more than once will be ignored. If you need to change configuration, restart the process
with new settings.

OpenTelemetry Configuration
----------------------------

OpenTelemetryConfig: Main configuration object for OpenTelemetry exporters and options.

* traces: (optional) Configure trace exporting using OpenTelemetryTracesConfig.

  * endpoint: The collector endpoint for traces. Supported protocols:
    http://, https:// for HTTP/HTTPS, grpc:// for gRPC, file:// for local file export
  * sample_percentage: (optional) The percentage of requests to sample (0-100). Defaults to 1.
    Note: Higher sampling percentages impact performance. Recommended: 1-5% in production.

* metrics: (optional) Configure metrics exporting using OpenTelemetryMetricsConfig.

  * endpoint: The collector endpoint for metrics. Same protocol rules as above.

* flush_interval_ms: (optional) Interval in milliseconds for flushing data. Defaults to 5000ms.

File Exporter Details
---------------------

For file:// endpoints:

* Path must start with file:// (e.g., file:///tmp/otel or file:///tmp/otel/traces.json)
* If path is a directory or lacks extension, data is written to signals.json in that directory
* If path includes filename with extension, that file is used as-is
* Parent directory must exist; otherwise initialization fails with InvalidInput error
* If target file exists, new data is appended (not overwritten)

Validation Rules
----------------

* flush_interval_ms must be a positive integer
* sample_percentage must be between 0 and 100
* File exporter paths must start with file:// and have an existing parent directory
* Invalid configuration will throw an error when calling OpenTelemetry.init()
"""

import random
from typing import Optional

from glide_shared.exceptions import ConfigurationError


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


class OpenTelemetry:
    """
    Singleton class for managing OpenTelemetry configuration and operations.

    This class provides a centralized way to initialize OpenTelemetry and control
    sampling behavior at runtime.

    Example usage::

        from glide import OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig, OpenTelemetryMetricsConfig

        OpenTelemetry.init(OpenTelemetryConfig(
            traces=OpenTelemetryTracesConfig(
                endpoint="http://localhost:4318/v1/traces",
                sample_percentage=10
            ),
            metrics=OpenTelemetryMetricsConfig(
                endpoint="http://localhost:4318/v1/metrics"
            ),
            flush_interval_ms=1000
        ))

    Note:
        OpenTelemetry can only be initialized once per process. Subsequent calls to
        init() will be ignored. This is by design, as OpenTelemetry is a global
        resource that should be configured once at application startup.
    """

    _instance: Optional["OpenTelemetry"] = None
    _config: Optional[OpenTelemetryConfig] = None

    @classmethod
    def init(cls, config: OpenTelemetryConfig) -> None:
        """Initialize OpenTelemetry with the given configuration.

        This method can only be called once per process. Subsequent calls will log
        a warning and be ignored.

        Args:
            config: The OpenTelemetry configuration specifying trace/metrics endpoints
                and flush intervals.

        Raises:
            ConfigurationError: If the underlying FFI initialization fails (e.g., invalid
                endpoint or file path).
        """
        if not cls._instance:
            cls._config = config
            from glide_shared._glide_ffi import GlideFFI
            from glide_shared.logger import Level, Logger

            ffi = GlideFFI.ffi
            lib = GlideFFI.lib

            traces_ptr = ffi.NULL
            traces_endpoint_cstr = None
            if config.traces:
                traces_endpoint_cstr = ffi.new(
                    "char[]", config.traces.endpoint.encode()
                )
                traces_config = ffi.new("OpenTelemetryTracesConfig*")
                traces_config.endpoint = traces_endpoint_cstr
                traces_config.has_sample_percentage = True
                traces_config.sample_percentage = config.traces.sample_percentage
                traces_ptr = traces_config

            metrics_ptr = ffi.NULL
            metrics_endpoint_cstr = None
            if config.metrics:
                metrics_endpoint_cstr = ffi.new(
                    "char[]", config.metrics.endpoint.encode()
                )
                metrics_config = ffi.new("OpenTelemetryMetricsConfig*")
                metrics_config.endpoint = metrics_endpoint_cstr
                metrics_ptr = metrics_config

            otel_config = ffi.new("OpenTelemetryConfig*")
            otel_config.traces = traces_ptr
            otel_config.metrics = metrics_ptr
            otel_config.has_flush_interval_ms = config.flush_interval_ms is not None
            otel_config.flush_interval_ms = (
                config.flush_interval_ms if config.flush_interval_ms else 0
            )

            error = lib.init_open_telemetry(otel_config)
            if error != ffi.NULL:
                error_msg = ffi.string(error).decode()
                lib.free_c_string(error)
                raise ConfigurationError(
                    f"Failed to initialize OpenTelemetry: {error_msg}"
                )

            cls._instance = OpenTelemetry()
            Logger.log(
                Level.INFO,
                "GlideOpenTelemetry",
                "OpenTelemetry initialized successfully",
            )
            return

        from glide_shared.logger import Level, Logger

        Logger.log(
            Level.WARN,
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized - ignoring new configuration",
        )

    @classmethod
    def is_initialized(cls) -> bool:
        """Check whether OpenTelemetry has been initialized.

        Returns:
            bool: True if init() has been successfully called, False otherwise.
        """
        return cls._instance is not None

    @classmethod
    def get_sample_percentage(cls) -> Optional[int]:
        """Get the current trace sampling percentage.

        Returns:
            Optional[int]: The sampling percentage (0-100), or None if traces are not configured.
        """
        if cls._config and cls._config.traces:
            return cls._config.traces.sample_percentage
        return None

    @classmethod
    def should_sample(cls) -> bool:
        """Determine whether the current request should be sampled.

        Uses random sampling based on the configured sample percentage.

        Returns:
            bool: True if the request should be traced, False otherwise.
                Always returns False if OpenTelemetry is not initialized.
        """
        if not cls._instance:
            return False
        percentage = cls.get_sample_percentage()
        return percentage is not None and random.random() * 100 < percentage

    @classmethod
    def set_sample_percentage(cls, percentage: int) -> None:
        """Update the trace sampling percentage at runtime.

        Args:
            percentage: The new sampling percentage (0-100).

        Raises:
            ConfigurationError: If traces are not initialized or percentage is out of range.
        """
        if not cls._config or not cls._config.traces:
            raise ConfigurationError("OpenTelemetry traces not initialized")
        if percentage < 0 or percentage > 100:
            raise ConfigurationError("Sample percentage must be between 0 and 100")
        cls._config.traces.sample_percentage = percentage
