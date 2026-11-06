#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""``next_free_port`` returns a port only when every port the node will bind is
free, so a node never has to discover a collision by exiting; the reason is at
the probe in ``next_free_port``.
"""

import socket
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "utils"))

from cluster_manager import (  # noqa: E402
    CLUSTER_BUS_PORT_OFFSET,
    bind_failures,
    listening_ports,
    next_free_port,
)


def hold(port: int) -> socket.socket:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.bind(("127.0.0.1", port))
    sock.listen(1)
    return sock


class TestNextFreePort:
    def test_accepts_a_candidate_it_just_found_free(self):
        """Positive control for the held-bus-port test below."""
        port = next_free_port(cluster_mode=True)
        assert next_free_port(port, port, timeout=1, cluster_mode=True) == port

    def test_rejects_a_candidate_whose_bus_port_is_held(self, caplog):
        port = next_free_port(cluster_mode=True)
        with hold(port + CLUSTER_BUS_PORT_OFFSET):
            with pytest.raises(Exception, match="No free port"):
                next_free_port(port, port, timeout=0.3, cluster_mode=True)
        assert f"port {port + CLUSTER_BUS_PORT_OFFSET}:" in caplog.text

    def test_ignores_the_bus_port_outside_cluster_mode(self):
        port = next_free_port(cluster_mode=True)
        with hold(port + CLUSTER_BUS_PORT_OFFSET):
            assert next_free_port(port, port, timeout=1, cluster_mode=False) == port

    def test_listening_ports(self):
        assert listening_ports(6379, cluster_mode=False) == [6379]
        assert listening_ports(6379, cluster_mode=True) == [6379, 16379]


class TestBindFailures:
    def test_returns_the_servers_bind_lines_only(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text(
            "* Running mode=cluster, port=45968.\n"
            "# Warning: Could not create server TCP listening socket 127.0.0.1:55968: bind: Address already in use\n"
            "# Failed listening on port 55968 (cluster), aborting.\n"
        )
        assert bind_failures(str(log)) == [
            "# Warning: Could not create server TCP listening socket 127.0.0.1:55968: bind: Address already in use"
        ]
