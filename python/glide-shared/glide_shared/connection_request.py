# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Optional

from glide_shared.config import (
    BaseClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_shared.protobuf.connection_request_pb2 import ConnectionRequest


def _resolve_lib_name(
    lib_name: Optional[str], client_info_tag: Optional[str], runtime_default: str
) -> str:
    """Resolve the library name sent in a client connection request."""
    resolved_name = lib_name or runtime_default
    return f"{resolved_name}({client_info_tag})" if client_info_tag else resolved_name


def _create_connection_request(
    config: BaseClientConfiguration, runtime_default: str
) -> ConnectionRequest:
    request = config._create_a_protobuf_conn_request(
        cluster_mode=isinstance(config, GlideClusterClientConfiguration)
    )
    request.lib_name = _resolve_lib_name(
        config.lib_name, config.client_info_tag, runtime_default
    )
    return request


def create_async_connection_request(
    config: BaseClientConfiguration,
) -> ConnectionRequest:
    """Build an asynchronous Python client connection request."""
    return _create_connection_request(config, "GlidePy")


def create_sync_connection_request(
    config: BaseClientConfiguration,
) -> ConnectionRequest:
    """Build a synchronous Python client connection request."""
    return _create_connection_request(config, "GlidePySync")
