# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

# flake8: noqa: E402
import warnings

# Issue deprecation warning for direct module imports
warnings.warn(
    "Importing from 'glide.config' is deprecated. Import from glide instead. For example: 'from glide import GlideClientConfiguration'",
    DeprecationWarning,
    stacklevel=2,
)

# Re-export all config classes from the new location
from glide_shared.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    PeriodicChecksManualInterval,
    PeriodicChecksStatus,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
    TlsAdvancedConfiguration,
)

__all__ = [
    "AdvancedGlideClientConfiguration",
    "AdvancedGlideClusterClientConfiguration",
    "BackoffStrategy",
    "GlideClientConfiguration",
    "GlideClusterClientConfiguration",
    "NodeAddress",
    "PeriodicChecksManualInterval",
    "PeriodicChecksStatus",
    "ProtocolVersion",
    "ReadFrom",
    "ServerCredentials",
    "TlsAdvancedConfiguration",
]
