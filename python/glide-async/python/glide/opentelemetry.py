# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
OpenTelemetry singleton for async client.

See glide_shared.opentelemetry module documentation for configuration details.
"""

import random
from typing import Optional

from glide.glide import OpenTelemetryConfig as PyO3OpenTelemetryConfig
from glide.glide import OpenTelemetryMetricsConfig as PyO3OpenTelemetryMetricsConfig
from glide.glide import OpenTelemetryTracesConfig as PyO3OpenTelemetryTracesConfig
from glide.glide import (
    init_opentelemetry,
)
from glide_shared.exceptions import ConfigurationError
from glide_shared.opentelemetry import (  # noqa: F401 - Re-exported for public API
    OpenTelemetryConfig,
    OpenTelemetryMetricsConfig,
    OpenTelemetryTracesConfig,
)

from .logger import Level, Logger


def _convert_to_pyo3_config(config: OpenTelemetryConfig) -> PyO3OpenTelemetryConfig:
    """
    Convert shared OpenTelemetryConfig to PyO3 OpenTelemetryConfig for Rust FFI.

    Args:
        config: Shared OpenTelemetryConfig instance

    Returns:
        PyO3OpenTelemetryConfig: PyO3-compatible config for Rust
    """
    pyo3_traces = None
    if config.traces:
        pyo3_traces = PyO3OpenTelemetryTracesConfig(
            endpoint=config.traces.endpoint,
            sample_percentage=config.traces.sample_percentage,
        )

    pyo3_metrics = None
    if config.metrics:
        pyo3_metrics = PyO3OpenTelemetryMetricsConfig(endpoint=config.metrics.endpoint)

    return PyO3OpenTelemetryConfig(
        traces=pyo3_traces,
        metrics=pyo3_metrics,
        flush_interval_ms=config.flush_interval_ms,
    )


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
            # Convert shared config to PyO3 config and initialize
            pyo3_config = _convert_to_pyo3_config(config)
            init_opentelemetry(pyo3_config)
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
        if cls._config and cls._config.traces:
            return cls._config.traces.sample_percentage
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
        if not cls._config or not cls._config.traces:
            raise ConfigurationError("OpenTelemetry traces not initialized")

        if percentage < 0 or percentage > 100:
            raise ConfigurationError("Sample percentage must be between 0 and 100")

        # Update the shared config directly
        cls._config.traces.sample_percentage = percentage
