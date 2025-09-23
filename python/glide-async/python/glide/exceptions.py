# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import warnings

# Issue deprecation warning for direct module imports
warnings.warn(
    "Importing from 'glide.exceptions' is deprecated. Import from glide instead. For example: 'from glide import RequestError'",
    DeprecationWarning,
    stacklevel=2,
)

# Re-export all exceptions from the new location
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    ConnectionError,
    ExecAbortError,
    GlideError,
    RequestError,
    TimeoutError,
)

__all__ = [
    "ClosingError",
    "ConfigurationError",
    "ConnectionError",
    "ExecAbortError",
    "GlideError",
    "RequestError",
    "TimeoutError",
]
