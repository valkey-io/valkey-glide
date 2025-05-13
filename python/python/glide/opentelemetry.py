# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from .glide import create_otel_span, drop_otel_span


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
