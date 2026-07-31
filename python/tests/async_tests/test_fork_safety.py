# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for fork() safety of the async pipe transport.

The async pipe transport (PR #5637) uses a process-wide pipe and flush thread.
After fork(), the flush thread is dead in the child but the pipe state is
inherited. Without proper fork detection, commands in forked children hang
indefinitely because responses are never flushed to the pipe.

These tests verify that:
1. Forked child processes can create and use glide clients
2. Commands in forked children complete (don't hang)
3. The parent process remains functional after children complete
4. Reusing a parent's client in a child raises ClosingError
"""

import asyncio
import multiprocessing
import os
import queue
from typing import Any, List, Tuple

import pytest
from glide import GlideClusterClient
from glide_shared.config import GlideClusterClientConfiguration, NodeAddress
from glide_shared.exceptions import ClosingError

from tests.async_tests.conftest import create_client
from tests.utils.cluster import ValkeyCluster


def _child_cluster_worker(
    addresses_raw: List[Tuple[str, int]], result_queue: multiprocessing.Queue
):
    """Worker function for forked child processes (cluster mode).

    Creates a fresh client in the child process and issues commands to verify
    the pipe transport was properly reinitialized after fork.
    """

    async def run():
        try:
            config = GlideClusterClientConfiguration(
                addresses=[NodeAddress(host=h, port=p) for h, p in addresses_raw],
                request_timeout=5000,
            )
            client = await GlideClusterClient.create(config)

            # Issue several commands to exercise the pipe transport
            pid = os.getpid()
            for i in range(10):
                await client.set(f"fork_test:{pid}:{i}", f"value-{i}")

            # Verify reads work too
            result = await client.get(f"fork_test:{pid}:0")
            assert result == b"value-0", f"Expected b'value-0', got {result!r}"

            await client.close()
            result_queue.put(("OK", pid))
        except Exception as e:  # noqa: BLE001
            result_queue.put(("ERROR", f"{type(e).__name__}: {e}"))

    asyncio.run(run())


def _child_stale_client_worker(client_pid: int, result_queue: multiprocessing.Queue):
    """Worker that tries to use a parent's client object — should raise."""

    async def run():
        # Simulate having a reference to a client created in the parent.
        # We can't pass the actual client across fork (pickle), but we can
        # check that _check_same_process fires by creating a client and
        # then changing its _create_pid to simulate the parent's.
        from glide_shared.config import GlideClusterClientConfiguration, NodeAddress

        # Create a client in the child (this works fine)
        config = GlideClusterClientConfiguration(
            addresses=[NodeAddress("localhost", 7379)],
            request_timeout=5000,
        )
        try:
            client = await GlideClusterClient.create(config)
            # Fake it as if it was created in the parent
            client._create_pid = client_pid
            await client.set("stale_test", "should_fail")
            result_queue.put(("UNEXPECTED_SUCCESS", None))
        except ClosingError as e:
            if "fork" in str(e).lower():
                result_queue.put(("OK_REJECTED", str(e)))
            else:
                result_queue.put(("WRONG_ERROR", str(e)))
        except Exception as e:  # noqa: BLE001
            result_queue.put(("ERROR", f"{type(e).__name__}: {e}"))

    asyncio.run(run())


@pytest.mark.anyio
class TestForkSafety:
    """Tests for fork() safety of the pipe transport (issue #6673)."""

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_forked_children_can_use_clients(
        self, request: Any, cluster_mode: bool
    ):
        """
        After a parent creates a client (initializing the pipe + flush thread),
        forked children must be able to create their own clients and issue
        commands without hanging.

        This is the core regression test for issue #6673.
        """
        # Create a client in the parent to initialize the pipe + flush thread
        parent_client = await create_client(request, cluster_mode)
        await parent_client.set("fork_parent_init", "initialized")

        # Get addresses for children to connect to
        valkey_cluster: ValkeyCluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        addresses_raw = [(addr.host, addr.port) for addr in valkey_cluster.nodes_addr]

        await parent_client.close()

        # Fork children and verify they can use clients
        num_workers = 3
        ctx = multiprocessing.get_context("fork")
        result_queue = ctx.Queue()

        workers = []
        for _ in range(num_workers):
            p = ctx.Process(
                target=_child_cluster_worker, args=(addresses_raw, result_queue)
            )
            p.start()
            workers.append(p)

        # Wait with timeout — if fork safety is broken, children will hang
        for p in workers:
            p.join(timeout=15.0)
            if p.is_alive():
                p.kill()
                p.join()

        # Collect results
        results = []
        for _ in range(num_workers):
            try:
                results.append(result_queue.get(timeout=5.0))
            except queue.Empty:
                break

        ok_count = sum(1 for r in results if r[0] == "OK")
        error_results = [r for r in results if r[0] == "ERROR"]
        no_report = num_workers - len(results)

        assert no_report == 0, (
            f"{no_report} worker(s) were killed (hung). "
            "Fork safety issue: pipe flush thread not reinitialized in child."
        )
        assert len(error_results) == 0, f"Worker errors: {error_results}"
        assert ok_count == num_workers

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_parent_functional_after_fork(self, request: Any, cluster_mode: bool):
        """
        The parent process must remain functional after forking children.
        Verifies that reinit in children doesn't corrupt the parent's pipe.
        """
        parent_client = await create_client(request, cluster_mode)
        await parent_client.set("pre_fork_key", "pre_fork_value")

        valkey_cluster: ValkeyCluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        addresses_raw = [(addr.host, addr.port) for addr in valkey_cluster.nodes_addr]

        # Fork a child
        ctx = multiprocessing.get_context("fork")
        result_queue = ctx.Queue()
        p = ctx.Process(
            target=_child_cluster_worker, args=(addresses_raw, result_queue)
        )
        p.start()
        p.join(timeout=15.0)
        if p.is_alive():
            p.kill()
            p.join()
            pytest.fail("Child worker hung — fork safety broken")

        # Parent should still work after child completes
        await parent_client.set("post_fork_key", "post_fork_value")
        result = await parent_client.get("post_fork_key")
        assert result == b"post_fork_value"

        await parent_client.close()

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_multiple_fork_cycles(self, request: Any, cluster_mode: bool):
        """
        Multiple fork/use/exit cycles should all work correctly.
        Tests that the fork detection doesn't leave stale state.
        """
        # Initialize pipe in parent
        parent_client = await create_client(request, cluster_mode)
        await parent_client.set("cycle_test", "init")
        await parent_client.close()

        valkey_cluster: ValkeyCluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        addresses_raw = [(addr.host, addr.port) for addr in valkey_cluster.nodes_addr]

        # Run 3 fork cycles
        for cycle in range(3):
            ctx = multiprocessing.get_context("fork")
            result_queue = ctx.Queue()
            p = ctx.Process(
                target=_child_cluster_worker, args=(addresses_raw, result_queue)
            )
            p.start()
            p.join(timeout=15.0)
            if p.is_alive():
                p.kill()
                p.join()
                pytest.fail(f"Worker hung in cycle {cycle}")

            result = result_queue.get(timeout=5.0)
            assert result[0] == "OK", f"Cycle {cycle} failed: {result}"

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_stale_client_raises_in_child(self, request: Any, cluster_mode: bool):
        """
        Using a parent's client object in a forked child must raise
        ClosingError instead of silently hanging.
        """
        # Create client in parent
        parent_client = await create_client(request, cluster_mode)
        await parent_client.set("stale_test_init", "ok")
        parent_pid = os.getpid()
        await parent_client.close()

        # Fork a child that simulates using a stale client
        ctx = multiprocessing.get_context("fork")
        result_queue = ctx.Queue()
        p = ctx.Process(
            target=_child_stale_client_worker, args=(parent_pid, result_queue)
        )
        p.start()
        p.join(timeout=15.0)
        if p.is_alive():
            p.kill()
            p.join()
            pytest.fail("Child hung — stale client check not working")

        result = result_queue.get(timeout=5.0)
        assert (
            result[0] == "OK_REJECTED"
        ), f"Expected ClosingError for stale client, got: {result}"
