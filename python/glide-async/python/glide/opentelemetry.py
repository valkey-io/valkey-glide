# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
⚠️ OpenTelemetry can only be initialized once per process. Calling `OpenTelemetry.init()` more than once will be ignored.
If you need to change configuration, restart the process with new settings.

### OpenTelemetry

- **openTelemetryConfig**: Use this object to configure OpenTelemetry exporters and options.
  - **traces**: (optional) Configure trace exporting.
    - **endpoint**: The collector endpoint for traces. Supported protocols:
      - `http://` or `https://` for HTTP/HTTPS
      - `grpc://` for gRPC
      - `file://` for local file export (see below)
    - **sample_percentage**: (optional) The percentage of requests to sample and create a span for, used to measure command duration. Must be between 0 and 100. Defaults to 1 if not specified.
      Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
      It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
  - **metrics**: (optional) Configure metrics exporting.
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

import random
from typing import Optional

from glide.glide import (
    OpenTelemetryConfig,
    OpenTelemetryTracesConfig,
    init_opentelemetry,
)
from glide_shared.exceptions import ConfigurationError

from .logger import Level, Logger


class OpenTelemetry:
    """
    Singleton class for managing OpenTelemetry configuration and operations.

    This class provides a centralized way to initialize OpenTelemetry and control
    sampling behavior at runtime.

    Example usage:
        ```python
        from glide import OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig, OpenTelemetryMetricsConfig

        OpenTelemetry.init(OpenTelemetryConfig(
            traces=OpenTelemetryTracesConfig(
                endpoint="http://localhost:4318/v1/traces",
                sample_percentage=10  # Optional, defaults to 1. Can also be changed at runtime via set_sample_percentage().
            ),
            metrics=OpenTelemetryMetricsConfig(
                endpoint="http://localhost:4318/v1/metrics"
            ),
            flush_interval_ms=1000  # Optional, defaults to 5000
        ))
        ```

    Note:
        OpenTelemetry can only be initialized once per process. Subsequent calls to
        init() will be ignored. This is by design, as OpenTelemetry is a global
        resource that should be configured once at application startup.
    """

    _instance: Optional["OpenTelemetry"] = None
    _config: Optional[OpenTelemetryConfig] = None

    @classmethod
    def init(cls, config: OpenTelemetryConfig) -> None:
        """
        Initialize the OpenTelemetry instance.

        Args:
            config: The OpenTelemetry configuration

        Note:
            OpenTelemetry can only be initialized once per process.
            Subsequent calls will be ignored and a warning will be logged.
        """
        if not cls._instance:
            cls._config = config
            # Initialize the underlying OpenTelemetry implementation
            init_opentelemetry(config)
            cls._instance = OpenTelemetry()
            Logger.log(
                Level.INFO,
                "GlideOpenTelemetry",
                "OpenTelemetry initialized successfully",
            )
            return

        Logger.log(
            Level.WARN,
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized - ignoring new configuration",
        )

    @classmethod
    def is_initialized(cls) -> bool:
        """
        Check if the OpenTelemetry instance is initialized.

        Returns:
            bool: True if the OpenTelemetry instance is initialized, False otherwise
        """
        return cls._instance is not None

    @classmethod
    def get_sample_percentage(cls) -> Optional[int]:
        """
        Get the sample percentage for traces.

        Returns:
            Optional[int]: The sample percentage for traces only if OpenTelemetry is initialized
                and the traces config is set, otherwise None.
        """
        if cls._config:
            traces_config = cls._config.get_traces()
            if traces_config:
                return traces_config.get_sample_percentage()
        return None

    @classmethod
    def should_sample(cls) -> bool:
        """
        Determines if the current request should be sampled for OpenTelemetry tracing.
        Uses the configured sample percentage to randomly decide whether to create a span for this request.

        Returns:
            bool: True if the request should be sampled, False otherwise
        """
        percentage = cls.get_sample_percentage()
        return (
            cls.is_initialized()
            and percentage is not None
            and random.random() * 100 < percentage
        )

    @classmethod
    def set_sample_percentage(cls, percentage: int) -> None:
        """
        Set the percentage of requests to be sampled and traced. Must be a value between 0 and 100.
        This setting only affects traces, not metrics.

        Args:
            percentage: The sample percentage 0-100

        Raises:
            ConfigurationError: If OpenTelemetry is not initialized or traces config is not set

        Remarks:
            This method can be called at runtime to change the sampling percentage
            without reinitializing OpenTelemetry.
        """
        if not cls._config or not cls._config.get_traces():
            raise ConfigurationError("OpenTelemetry config traces not initialized")

        if percentage < 0 or percentage > 100:
            raise ConfigurationError("Sample percentage must be between 0 and 100")

        # Create a new traces config with the updated sample_percentage
        # This is necessary because the PyO3 binding doesn't properly handle direct assignment to Option<u32>
        traces_config = cls._config.get_traces()
        if traces_config:
            endpoint = traces_config.get_endpoint()
            new_traces_config = OpenTelemetryTracesConfig(
                endpoint=endpoint, sample_percentage=percentage
            )

            # Replace the traces config
            cls._config.set_traces(new_traces_config)
