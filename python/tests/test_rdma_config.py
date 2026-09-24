# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""RDMA configuration: what reaches the protobuf and what is refused before it."""

from __future__ import annotations

import asyncio

import pytest
from glide_shared.config import (
    ConfigurationError,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    RdmaConfiguration,
    RdmaProvider,
)

ADDRESSES = [NodeAddress("localhost", 6379)]


def _request(rdma: RdmaConfiguration | None, cluster: bool = False):
    config_class = (
        GlideClusterClientConfiguration if cluster else GlideClientConfiguration
    )
    config = config_class(addresses=ADDRESSES, rdma=rdma)
    return config._create_a_protobuf_conn_request(cluster_mode=cluster)


def test_rdma_is_absent_from_the_request_unless_configured():
    # An unset field is what tells the core RDMA was never asked for, so a
    # default client must not carry an empty RdmaConfig.
    assert _request(None).HasField("rdma_config") is False


@pytest.mark.parametrize("cluster", [False, True], ids=["standalone", "cluster"])
def test_efa_direct_sets_the_provider_oneof(cluster):
    request = _request(RdmaConfiguration(provider=RdmaProvider.EfaDirect()), cluster)

    assert request.HasField("rdma_config")
    assert request.rdma_config.WhichOneof("provider") == "efa_direct"


@pytest.mark.parametrize("cluster", [False, True], ids=["standalone", "cluster"])
def test_tcp_sets_the_provider_oneof(cluster):
    # tcp carries no required field, so the oneof has to be set explicitly or
    # the core reads it back as "no provider configured".
    request = _request(RdmaConfiguration(provider=RdmaProvider.Tcp()), cluster)

    assert request.rdma_config.WhichOneof("provider") == "tcp"
    assert request.rdma_config.tcp.HasField("bind") is False


def test_interface_and_bind_reach_the_request():
    request = _request(
        RdmaConfiguration(provider=RdmaProvider.Tcp(bind="127.0.0.1"), interface="eth0")
    )

    assert request.rdma_config.interface == "eth0"
    assert request.rdma_config.tcp.bind == "127.0.0.1"


def test_interface_is_optional():
    request = _request(RdmaConfiguration(provider=RdmaProvider.EfaDirect()))

    assert request.rdma_config.HasField("interface") is False


def test_efa_direct_has_no_bind_to_set():
    # efa-direct has no IP to bind. Carrying `bind` on Tcp rather than on the
    # configuration makes that a type error instead of a runtime check.
    with pytest.raises(TypeError):
        RdmaProvider.EfaDirect(bind="127.0.0.1")  # type: ignore[call-arg]


def test_a_provider_is_required_to_be_a_provider():
    with pytest.raises(
        ConfigurationError,
        match="must be RdmaProvider.EfaDirect\\(\\) or RdmaProvider.Tcp\\(\\)",
    ):
        RdmaConfiguration(provider="tcp")


@pytest.mark.parametrize(
    ("config", "field"),
    [
        (
            lambda: RdmaConfiguration(provider=RdmaProvider.Tcp(), interface=""),
            "interface",
        ),
        (lambda: RdmaConfiguration(provider=RdmaProvider.Tcp(bind="")), "bind"),
    ],
    ids=["interface", "bind"],
)
def test_empty_strings_are_refused(config, field):
    # An empty string is a set field carrying nothing, which reads to the core
    # as a real request for a nameless interface rather than as "unset".
    with pytest.raises(ConfigurationError, match=f"{field} must not be empty"):
        config()


def test_reassigned_fields_are_revalidated_when_the_request_is_built():
    # __post_init__ cannot see a field assigned afterwards, so the conversion
    # checks again rather than emitting a configuration the core will reject.
    config = RdmaConfiguration(provider=RdmaProvider.Tcp())
    config.provider = "tcp"  # type: ignore[assignment]

    with pytest.raises(ConfigurationError, match="must be RdmaProvider.EfaDirect"):
        config._to_protobuf()


def test_the_async_client_refuses_an_rdma_configuration():
    # The async client has no transfer API, so opening a fabric for it would
    # pin memory and hold an endpoint open for transfers that can never be
    # asked for.
    from glide import GlideClient

    config = GlideClientConfiguration(
        addresses=ADDRESSES, rdma=RdmaConfiguration(provider=RdmaProvider.Tcp())
    )

    with pytest.raises(ConfigurationError, match="only available on the synchronous"):
        asyncio.run(GlideClient.create(config))
