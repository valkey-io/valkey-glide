#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""``check_node_startup`` reads a new node's log so ``create_servers`` can keep
it (READY) or replace it. A node whose log shows neither "Ready" nor a bind
error within the timeout is NO_VERDICT and is replaced like PORT_TAKEN, because
handing it to ``valkey-cli --cluster create`` fails later on CLUSTER MEET with
no indication of which node never came up.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "utils"))

from cluster_manager import NodeCheck, Server, check_node_startup  # noqa: E402

SERVER = Server("127.0.0.1", 6379)


class TestCheckNodeStartup:
    def test_ready_node_reports_ready(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text("* Ready to accept connections tcp\n")
        assert check_node_startup(SERVER, str(log), timeout=1) is NodeCheck.READY

    def test_bind_error_reports_port_taken(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text(
            "# Warning: Could not create server TCP listening socket *:6379: bind: Address already in use\n"
        )
        assert check_node_startup(SERVER, str(log), timeout=1) is NodeCheck.PORT_TAKEN

    def test_silent_node_reports_no_verdict_at_timeout(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text("# Server initialized\n")
        assert check_node_startup(SERVER, str(log), timeout=0.3) is NodeCheck.NO_VERDICT

    def test_missing_log_reports_no_verdict_at_timeout(self, tmp_path):
        assert (
            check_node_startup(SERVER, str(tmp_path / "server.log"), timeout=0.3)
            is NodeCheck.NO_VERDICT
        )
