# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import random
import socket
import sys
from pathlib import Path

import pytest

# Ensure repository root is on sys.path so utils.cluster_manager can be imported
REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.append(str(REPO_ROOT))

from utils.cluster_manager import PortAllocator  # noqa: E402


class TestPortAllocator:
    def test_reserve_random_port_allows_binding(self):
        allocator = PortAllocator()
        port = allocator.reserve_random_port()

        probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            probe.bind(("127.0.0.1", port))
        finally:
            probe.close()

    def test_reserve_contiguous_ports_returns_block(self):
        allocator = PortAllocator()
        attempts = 3
        ports = None
        while attempts:
            base_port = 9000 + random.randint(0, 50) * 10
            try:
                ports = allocator.reserve_contiguous_ports(start_port=base_port, count=3)
                break
            except Exception:
                attempts -= 1
        else:
            pytest.skip("Unable to reserve contiguous ports for test run")

        assert ports == list(range(ports[0], ports[0] + 3))
