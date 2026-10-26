#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""``is_address_already_in_use`` decides whether ``create_servers`` keeps a node
or replaces it. A node is kept only when its log says it is listening; a node
whose log shows neither "Ready" nor a bind error within the timeout is replaced,
because handing it to ``valkey-cli --cluster create`` fails later on CLUSTER
MEET with no indication of which node never came up.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "utils"))

from cluster_manager import (  # type: ignore[import-not-found]  # noqa: E402
    Server,
    is_address_already_in_use,
)

SERVER = Server("127.0.0.1", 6379)


class TestIsAddressAlreadyInUse:
    def test_ready_node_is_kept(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text("* Ready to accept connections tcp\n")
        assert is_address_already_in_use(SERVER, str(log), timeout=1) is False

    def test_node_that_lost_the_bind_race_is_replaced(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text(
            "# Warning: Could not create server TCP listening socket *:6379: bind: Address already in use\n"
        )
        assert is_address_already_in_use(SERVER, str(log), timeout=1) is True

    def test_node_with_no_verdict_before_the_timeout_is_replaced(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text("# Server initialized\n")
        assert is_address_already_in_use(SERVER, str(log), timeout=0.3) is True

    def test_node_whose_log_never_appears_is_replaced(self, tmp_path):
        assert (
            is_address_already_in_use(SERVER, str(tmp_path / "server.log"), timeout=0.3)
            is True
        )
