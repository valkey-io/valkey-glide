# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Cached RequestType enum for fast attribute access.

The protobuf-generated RequestType uses enum_type_wrapper which performs a
descriptor lookup on every attribute access. This module
caches all enum values as plain int attributes on a singleton object.
"""

from typing import Any

from glide_shared.protobuf.command_request_pb2 import RequestType as _RequestType


class _CachedRequestType:
    ValueType = int
    Name: Any

    def __getattr__(self, name: str) -> Any: ...

    def __init__(self) -> None:
        src = _RequestType
        for v in src.DESCRIPTOR.values:
            setattr(self, v.name, v.number)
        self.Name = src.Name


RequestType = _CachedRequestType()
