# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
⚠️ OpenTelemetry can only be initialized once per process. Calling `OpenTelemetry.init()` more than once will be ignored.
If you need to change configuration, restart the process with new settings.

### OpenTelemetry

- **opentelemetry_config**: Use this object to configure OpenTelemetry exporters and options.
  - **traces**: (optional) Configure trace exporting.
    - **endpoint**: The collector endpoint for traces. Supported protocols:
      - `http://` or `https://` for HTTP/HTTPS
      - `grpc://` for gRPC
      - `file://` for local file export (see below)
    - **sample_percentage**: (optional) The percentage of requests to sample and create a span for, used to measure command duration. 
      Must be between 0 and 100. Defaults to 1 if not specified.
      Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed 
      telemetry data but will impact performance.
      It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
  - **metrics**: (optional) Configure metrics exporting.
    - **endpoint**: The collector endpoint for metrics. Same protocol rules as above.
  - **flush_interval_ms**: (optional) Interval in milliseconds for flushing data to the collector. Must be a positive integer. 
    Defaults to 5000ms if not specified.

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
- Invalid configuration will raise an error when calling `OpenTelemetry.init()`.
"""

from __future__ import annotations

import json
import random
from typing import Dict, Optional, Any, TypedDict, Union

from .exceptions import ConfigurationError
from .logger import Logger
from .glide import init_opentelemetry


class TracesConfig(TypedDict, total=False):
    endpoint: str
    sample_percentage: float


class MetricsConfig(TypedDict, total=False):
    endpoint: str


class OpenTelemetryConfig(TypedDict, total=False):
    traces: TracesConfig
    metrics: MetricsConfig
    flush_interval_ms: int


class OpenTelemetry:
    """
    OpenTelemetry integration for Valkey GLIDE.
    
    Example usage:
    ```python
    from glide import OpenTelemetry
    
    OpenTelemetry.init({
        "traces": {
            "endpoint": "http://localhost:4318/v1/traces",
            "sample_percentage": 10,  # Optional, defaults to 1. Can also be changed at runtime via set_sample_percentage().
        },
        "metrics": {
            "endpoint": "http://localhost:4318/v1/metrics",
        },
        "flush_interval_ms": 5000,  # Optional, defaults to 5000
    })
    ```
    """
    _instance: Optional[OpenTelemetry] = None
    _opentelemetry_config: Optional[OpenTelemetryConfig] = None

    @classmethod
    def init(cls, opentelemetry_config: OpenTelemetryConfig) -> None:
        """
        Initialize the OpenTelemetry instance
        
        Args:
            opentelemetry_config: The OpenTelemetry configuration
        """
        if not cls._instance:
            cls._internal_init(opentelemetry_config)
            Logger.log(
                Logger.Level.INFO,
                "GlideOpenTelemetry",
                f"OpenTelemetry initialized with config: {json.dumps(opentelemetry_config)}"
            )
            return

        Logger.log(
            Logger.Level.WARN,
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized"
        )

    @classmethod
    def _internal_init(cls, opentelemetry_config: OpenTelemetryConfig) -> None:
        """
        Internal initialization method
        
        Args:
            opentelemetry_config: The OpenTelemetry configuration
        """
        cls._opentelemetry_config = opentelemetry_config
        init_opentelemetry(opentelemetry_config)
        cls._instance = OpenTelemetry()

    @classmethod
    def is_initialized(cls) -> bool:
        """
        Check if the OpenTelemetry instance is initialized
        
        Returns:
            True if the OpenTelemetry instance is initialized, false otherwise
        """
        return cls._instance is not None

    @classmethod
    def get_sample_percentage(cls) -> Optional[float]:
        """
        Get the sample percentage for traces
        
        Returns:
            The sample percentage for traces only if OpenTelemetry is initialized and the traces config is set, otherwise None.
        """
        if cls._opentelemetry_config and "traces" in cls._opentelemetry_config:
            return cls._opentelemetry_config["traces"].get("sample_percentage")
        return None

    @classmethod
    def should_sample(cls) -> bool:
        """
        Determines if the current request should be sampled for OpenTelemetry tracing.
        Uses the configured sample percentage to randomly decide whether to create a span for this request.
        
        Returns:
            True if the request should be sampled, False otherwise
        """
        percentage = cls.get_sample_percentage()
        return (
            cls.is_initialized() and
            percentage is not None and
            random.random() * 100 < percentage
        )

    @classmethod
    def set_sample_percentage(cls, percentage: float) -> None:
        """
        Set the percentage of requests to be sampled and traced. Must be a value between 0 and 100.
        This setting only affects traces, not metrics.
        
        Args:
            percentage: The sample percentage 0-100
            
        Raises:
            ConfigurationError: If OpenTelemetry is not initialized or traces config is not set
            
        Notes:
            This method can be called at runtime to change the sampling percentage without reinitializing OpenTelemetry.
        """
        if not cls._opentelemetry_config or "traces" not in cls._opentelemetry_config:
            raise ConfigurationError("OpenTelemetry config traces not initialized")

        cls._opentelemetry_config["traces"]["sample_percentage"] = percentage
