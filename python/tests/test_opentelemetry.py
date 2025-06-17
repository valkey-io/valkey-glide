# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import json
import os
from typing import Dict, List, Tuple

import psutil  # type: ignore[import-untyped]
import pytest
import pytest_asyncio

from glide import (
    OpenTelemetryConfig,
    OpenTelemetryMetricsConfig,
    OpenTelemetryTracesConfig,
)
from glide.async_commands.batch import Batch, ClusterBatch
from glide.config import ProtocolVersion
from glide.opentelemetry import OpenTelemetry
from tests.conftest import create_client

# Constants
TIMEOUT = 50  # seconds
VALID_ENDPOINT_TRACES = "/tmp/spans.json"
VALID_FILE_ENDPOINT_TRACES = f"file://{VALID_ENDPOINT_TRACES}"  # noqa: E231
VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics"


def read_and_parse_span_file(path: str) -> Tuple[str, List[Dict], List[str]]:
    """
    Reads and parses a span file, extracting span data and names.
    Args:
        path: The path to the span file

    Returns:
        A tuple containing the raw span data, array of spans, and array of span names

    Raises:
        Exception: If the file cannot be read or parsed
    """
    try:
        with open(path, "r") as f:
            span_data = f.read()
    except Exception as e:
        raise Exception(f"Failed to read or validate file: {str(e)}")

    spans = [line for line in span_data.split("\n") if line.strip()]

    # Check that we have spans
    if not spans:
        raise Exception("No spans found in the span file")

    # Parse and extract span names
    span_objects = []
    span_names = []
    for line in spans:
        try:
            span = json.loads(line)
            span_objects.append(span)
            span_names.append(span.get("name"))
        except json.JSONDecodeError:
            continue

    return span_data, span_objects, [name for name in span_names if name]


def test_wrong_opentelemetry_config():
    """Test various invalid OpenTelemetry configurations"""
    # Wrong traces endpoint
    with pytest.raises(TypeError, match=r".*Parse error.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="wrong.endpoint",
                    sample_percentage=100,
                ),
            )
        )

    # Wrong metrics endpoint
    with pytest.raises(TypeError, match=r".*Parse error.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                metrics=OpenTelemetryMetricsConfig(
                    endpoint="wrong.endpoint",
                ),
            )
        )

    # Negative flush interval
    with pytest.raises(
        TypeError,
        match=r".*InvalidInput: flush_interval_ms must be a positive integer.*",
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint=VALID_FILE_ENDPOINT_TRACES,
                    sample_percentage=100,
                ),
                flush_interval_ms=-400,
            )
        )

    # Negative sample percentage
    # TODO: This should be a ValueError: Trace sample percentage must be between 0 and 100
    with pytest.raises(
        OverflowError, match=r".*out of range integral type conversion attempted*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint=VALID_FILE_ENDPOINT_TRACES,
                    sample_percentage=-10,
                ),
            )
        )

    # Wrong traces file path
    with pytest.raises(TypeError, match=r".*File path must start with 'file://'.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="file:invalid-path/v1/traces.json",
                    sample_percentage=100,
                ),
            )
        )

    # Wrong metrics file path
    with pytest.raises(TypeError, match=r".*File path must start with 'file://'.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=None,
                metrics=OpenTelemetryMetricsConfig(
                    endpoint="file:invalid-path/v1/metrics.json",
                ),
            )
        )

    # Wrong directory path
    with pytest.raises(
        TypeError, match=r".*The directory does not exist or is not a directory.*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="file:///no-exits-path/v1/traces.json",
                    sample_percentage=100,
                ),
            )
        )

    # No traces or metrics provided
    with pytest.raises(
        TypeError, match=r".*At least one of traces or metrics must be provided.*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=None,
                metrics=None,
            )
        )


async def test_span_not_exported_before_init_otel(request):
    """Test that spans are not exported before OpenTelemetry is initialized"""
    # Clean up any existing files
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)

    client = await create_client(
        request,
        cluster_mode=False,
        protocol=ProtocolVersion.RESP3,
    )

    # Execute a command
    await client.get("testSpanNotExportedBeforeInitOtel")

    # Check that no spans file was created
    assert not os.path.exists(VALID_ENDPOINT_TRACES)

    await client.close()


class TestOpenTelemetryGlide:
    @pytest_asyncio.fixture(scope="class")
    async def setup_class(self, request):
        # Test wrong OpenTelemetry config before initializing
        test_wrong_opentelemetry_config()

        # Test that spans are not exported before OpenTelemetry is initialized
        await test_span_not_exported_before_init_otel(request)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest_asyncio.fixture(autouse=True)
    async def setup_test(self, request, cluster_mode):
        # Initialize OpenTelemetry with 100% sampling for tests
        opentelemetry_config = OpenTelemetryConfig(
            OpenTelemetryTracesConfig(
                endpoint=VALID_FILE_ENDPOINT_TRACES, sample_percentage=100
            ),
            metrics=OpenTelemetryMetricsConfig(endpoint=VALID_ENDPOINT_METRICS),
            flush_interval_ms=100,
        )

        # Initialize OpenTelemetry
        OpenTelemetry.init(opentelemetry_config)
        # Clean up before each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        yield

        # Clean up after each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        client = await create_client(
            request, cluster_mode=cluster_mode, request_timeout=2000
        )
        await client.custom_command(["FLUSHALL"])
        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_concurrent_commands_span_lifecycle(
        self, request, protocol, cluster_mode
    ):
        """Test that spans are properly handled with concurrent commands"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss  # Get resident set size in bytes

        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute multiple concurrent commands
        commands = [
            client.set("test_key1", "value1"),
            client.get("test_key1"),
            client.set("test_key2", "value2"),
            client.get("test_key2"),
            client.set("test_key3", "value3"),
            client.get("test_key3"),
        ]

        await asyncio.gather(*commands)

        # Force garbage collection again
        gc.collect()

        # Wait for spans to be flushed
        await asyncio.sleep(1)

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Check that memory usage hasn't grown significantly (indicating span leaks)
        # Allow for some reasonable growth, but not excessive
        memory_growth = final_memory - initial_memory
        max_allowed_growth = 10 * 1024 * 1024  # 10MB threshold

        assert (
            memory_growth < max_allowed_growth
        ), f"Memory grew by {memory_growth} bytes, which exceeds the {max_allowed_growth} byte threshold"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_cluster_span_lifecycle(self, request, protocol, cluster_mode):
        """Test that spans are properly handled with batch cluster operations"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss  # Get resident set size in bytes

        # Create cluster client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute multiple concurrent batch operations using ClusterBatch
        batch_operations = []

        # Create first batch
        batch1 = ClusterBatch(is_atomic=True)
        batch1.set("{batch}key1", "value1")
        batch1.get("{batch}key1")
        batch1.strlen("{batch}key1")
        batch_operations.append(client.exec(batch1, raise_on_error=True))

        # Create second batch
        batch2 = ClusterBatch(is_atomic=True)
        batch2.set("{batch}key2", "value2")
        batch2.object_refcount("{batch}key2")
        batch_operations.append(client.exec(batch2, raise_on_error=True))

        # Create third batch
        batch3 = ClusterBatch(is_atomic=True)
        batch3.set("{batch}key3", "value3")
        batch3.get("{batch}key3")
        batch3.delete(["{batch}key1", "{batch}key2", "{batch}key3"])
        batch_operations.append(client.exec(batch3, raise_on_error=True))

        # Execute all batches concurrently
        await asyncio.gather(*batch_operations)

        # Force garbage collection again
        gc.collect()

        # Wait for spans to be flushed
        await asyncio.sleep(1)

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Check that memory usage hasn't grown significantly (indicating span leaks)
        # Allow for some reasonable growth, but not excessive
        memory_growth = final_memory - initial_memory
        max_allowed_growth = 10 * 1024 * 1024  # 10MB threshold

        assert (
            memory_growth < max_allowed_growth
        ), f"Memory grew by {memory_growth} bytes, which exceeds the {max_allowed_growth} byte threshold"

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_memory_leak(self, request, protocol, cluster_mode):
        """Test that spans don't cause memory leaks"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Create client and get initial memory usage
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Execute a series of commands sequentially
        for i in range(100):
            key = f"test_key_{i}"
            await client.set(key, f"value_{i}")
            await client.get(key)

        # Close client
        await client.close()

        # Force garbage collection
        gc.collect()

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_percentage_requests_config(self, request, protocol, cluster_mode):
        """Test that sample percentage configuration works correctly"""
        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Set sample percentage to 0%
        OpenTelemetry.set_sample_percentage(0)
        assert OpenTelemetry.get_sample_percentage() == 0

        # Wait for any pending spans to be flushed
        await asyncio.sleep(0.5)

        # Clean up any existing files
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        # Execute commands with 0% sampling
        for i in range(100):
            await client.set(
                "GlideClusterClient_test_percentage_requests_config", "value"
            )

        # Wait for any spans to be flushed (though none should be created)
        await asyncio.sleep(0.5)

        # Check that no spans file was created
        assert not os.path.exists(VALID_ENDPOINT_TRACES)

        # Set sample percentage to 100%
        OpenTelemetry.set_sample_percentage(100)

        # Execute commands with 100% sampling
        for i in range(10):
            key = f"GlideClusterClient_test_percentage_requests_config_{i}"
            await client.get(key)

        # Wait for spans to be flushed
        await asyncio.sleep(5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Get" spans were created
        assert "Get" in span_names

        # Check that exactly 10 "Get" spans were created
        assert span_names.count("Get") == 10

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_otel_global_config_not_reinitialize(
        self, request, protocol, cluster_mode
    ):
        """Test that OpenTelemetry cannot be reinitialized"""
        # Try to reinitialize with invalid config
        opentelemetry_config = OpenTelemetryConfig(
            OpenTelemetryTracesConfig(endpoint="wrong.endpoint", sample_percentage=1)
        )

        # This should not throw an error because OpenTelemetry is already initialized
        OpenTelemetry.init(opentelemetry_config)

        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute a command
        await client.set("GlideClusterClient_test_otel_global_config", "value")

        # Wait for spans to be flushed
        await asyncio.sleep(0.5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Set" spans were created
        assert "Set" in span_names

        await client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_batch(self, request, protocol, cluster_mode):
        """Test that batch operations create spans correctly"""
        # This test should not run in parallel with other tests due to the memory check
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Create client
        client = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Create and execute a batch using the correct Python API

        # Use appropriate batch type based on cluster mode
        if cluster_mode:
            batch = ClusterBatch(is_atomic=True)
        else:
            batch = Batch(is_atomic=True)

        batch.set("test_key", "foo")
        batch.object_refcount("test_key")

        response = await client.exec(batch, raise_on_error=True)
        assert response is not None

        if response is not None:
            assert len(response) == 2
            assert response[0] == "OK"  # batch.set("test_key", "foo")
            assert response[1] >= 1  # batch.object_refcount("test_key")

        # Wait for spans to be flushed
        await asyncio.sleep(5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Batch" in span_names
        assert "send_batch" in span_names

        # Force garbage collection
        gc.collect()

        await client.close()

        # Get final memory usage
        final_memory = process.memory_info().rss

        # Calculate memory increase percentage
        memory_increase = ((final_memory - initial_memory) / initial_memory) * 100

        # Assert memory increase is not more than 10%
        assert (
            memory_increase < 10
        ), f"Memory usage increased by {memory_increase: .2f}%, which is more than the allowed 10%"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_number_of_clients_with_same_config(
        self, request, protocol, cluster_mode
    ):
        """Test that multiple clients with the same config work correctly with OpenTelemetry"""
        # Create two clients
        client1 = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        client2 = await create_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute commands on both clients
        await client1.set("test_key", "value")
        await client2.get("test_key")

        # Wait for spans to be flushed
        await asyncio.sleep(5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Get" in span_names
        assert "Set" in span_names

        # Close clients
        await client1.close()
        await client2.close()
