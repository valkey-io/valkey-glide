# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

# flake8: noqa: E402

import warnings

# Issue deprecation warning for direct module imports
warnings.warn(
    "Importing from 'glide.routes' is deprecated. Import from glide instead. For example: 'from glide import AllNodes'",
    DeprecationWarning,
    stacklevel=2,
)

# Re-export all routes from the new location
from glide_shared.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
)

__all__ = [
    "AllNodes",
    "AllPrimaries",
    "ByAddressRoute",
    "RandomNode",
    "Route",
    "SlotIdRoute",
    "SlotKeyRoute",
    "SlotType",
]
