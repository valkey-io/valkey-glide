# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import random
from typing import Optional

from glide.exceptions import ConfigurationError
from glide.logger import Logger, Level
from glide.config import OpenTelemetryConfig
from .glide import create_otel_span, drop_otel_span, init_opentelemetry


class GlideSpan:
    """
    Represents an OpenTelemetry span for tracing operations in Valkey GLIDE.
    
    This class provides a Pythonic interface to the underlying Rust implementation
    of OpenTelemetry spans.
    """
    
    def __init__(self, name: str):
        """
        Creates a new OpenTelemetry span with the given name.
        
        Args:
            name: The name of the span, typically the command name.
        """
        self.ptr = create_otel_span(name)
        self.name = name
        
    def __del__(self):
        """
        Cleans up the span when the object is garbage collected.
        """
        if hasattr(self, "ptr") and self.ptr:
            drop_otel_span(self.ptr)
            self.ptr = 0


class OpenTelemetry:
    """
    Singleton class for managing OpenTelemetry configuration and operations.
    
    This class provides a centralized way to initialize OpenTelemetry and control
    sampling behavior at runtime.
    
    Note:
        OpenTelemetry can only be initialized once per process. Subsequent calls to
        init() will be ignored. This is by design, as OpenTelemetry is a global
        resource that should be configured once at application startup.
    """
    
    _instance: Optional['OpenTelemetry'] = None
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
                f"OpenTelemetry initialized with config: {config}"
            )
            return
            
        Logger.log(
            Level.WARN,
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized - ignoring new configuration"
        )
    
    @classmethod
    def is_initialized(cls) -> bool:
        """
        Check if the OpenTelemetry instance is initialized.
        
        Returns:
            True if the OpenTelemetry instance is initialized, False otherwise
        """
        return cls._instance is not None
    
    @classmethod
    def get_sample_percentage(cls) -> Optional[int]:
        """
        Get the sample percentage for traces.
        
        Returns:
            The sample percentage for traces if OpenTelemetry is initialized
            and the traces config is set, otherwise None.
        """
        if cls._config and hasattr(cls._config, "sample_percentage"):
            return cls._config.sample_percentage
        return None
    
    @classmethod
    def set_sample_percentage(cls, percentage: int) -> None:
        """
        Set the percentage of requests to be sampled and traced.
        
        Args:
            percentage: The sample percentage (0-100)
            
        Raises:
            ConfigurationError: If OpenTelemetry is not initialized or traces config is not set
            
        Note:
            This method can be called at runtime to change the sampling percentage
            without reinitializing OpenTelemetry.
        """
        if not cls._config:
            raise ConfigurationError("OpenTelemetry not initialized")
            
        if percentage < 0 or percentage > 100:
            raise ConfigurationError("Sample percentage must be between 0 and 100")
            
        cls._config.sample_percentage = percentage
    
    @classmethod
    def should_sample(cls) -> bool:
        """
        Determine if the current request should be sampled based on the configured percentage.
        
        Returns:
            True if the request should be sampled, False otherwise
        """
        percentage = cls.get_sample_percentage()
        if not cls.is_initialized() or percentage is None:
            return False
            
        return random.random() * 100 < percentage
