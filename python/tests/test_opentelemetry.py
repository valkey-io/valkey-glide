# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import gc
import json
import os
import sys
from typing import Any, Dict, Optional

import pytest
from glide import GlideClient, GlideClusterClient, ProtocolVersion
from glide.async_commands.batch import ClusterBatch, Batch
from glide.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    OpenTelemetryConfig,
)
from glide.exceptions import ConfigurationError
from glide.opentelemetry import OpenTelemetry

# Constants
TIMEOUT = 50000  # milliseconds
VALID_ENDPOINT_TRACES = "/tmp/spans.json"
VALID_FILE_ENDPOINT_TRACES = f"file://{VALID_ENDPOINT_TRACES}"
VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics"


def read_and_parse_span_file(path: str) -> Dict[str, Any]:
    """
    Reads and parses a span file, extracting span data and names.

    Args:
        path: The path to the span file
    Returns:
        A dictionary containing the raw span data, array of spans, and array of span names
    Raises:
        Exception if the file cannot be read or parsed
    """
    try:
        with open(path, "r") as f:
            span_data = f.read()
            spans = [line for line in span_data.split("\n") if line.strip()]
    except Exception as e:
        raise Exception(f"Failed to read or validate file: {str(e)}")

    # Check that we have spans
    if not spans:
        raise Exception("No spans found in the span file")

    # Parse and extract span names
    span_names = []
    for line in spans:
        try:
            span = json.loads(line)
            if "name" in span:
                span_names.append(span["name"])
        except json.JSONDecodeError:
            continue

    return {"span_data": span_data, "spans": spans, "span_names": span_names}


async def teardown_otel_test():
    """Clean up OpenTelemetry files"""
    # Clean up OpenTelemetry files
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)

    if os.path.exists(VALID_ENDPOINT_METRICS):
        try:
            os.unlink(VALID_ENDPOINT_METRICS)
        except:
            pass


async def test_wrong_opentelemetry_config():
    """Test various invalid OpenTelemetry configurations"""
    # Wrong traces endpoint
    with pytest.raises(ConfigurationError):
        OpenTelemetryConfig(
            traces_collector_endpoint="wrong.endpoint",
            metrics_collector_endpoint=None,
        )

    # Wrong metrics endpoint
    with pytest.raises(ConfigurationError):
        OpenTelemetryConfig(
            traces_collector_endpoint=None,
            metrics_collector_endpoint="wrong.endpoint",
        )

    # Negative flush interval
    with pytest.raises(ConfigurationError, match=r".*flushIntervalMs.*positive.*"):
        OpenTelemetryConfig(
            traces_collector_endpoint=VALID_FILE_ENDPOINT_TRACES,
            metrics_collector_endpoint=None,
            flush_interval_ms=-400,
        )

    # Negative sample percentage
    with pytest.raises(ConfigurationError, match=r".*sample percentage.*between 0 and 100.*"):
        OpenTelemetryConfig(
            traces_collector_endpoint=VALID_FILE_ENDPOINT_TRACES,
            metrics_collector_endpoint=None,
            sample_percentage=-10,
        )

    # Wrong traces file path
    with pytest.raises(ConfigurationError, match=r".*File path must start with.*"):
        OpenTelemetryConfig(
            traces_collector_endpoint="file:invalid-path/v1/traces.json",
            metrics_collector_endpoint=None,
        )

    # Wrong metrics file path
    with pytest.raises(ConfigurationError, match=r".*File path must start with.*"):
        OpenTelemetryConfig(
            traces_collector_endpoint=None,
            metrics_collector_endpoint="file:invalid-path/v1/metrics.json",
        )

    # No traces or metrics provided
    with pytest.raises(ConfigurationError, match=r".*At least one of traces_collector_endpoint or metrics_collector_endpoint.*"):
        OpenTelemetryConfig(
            traces_collector_endpoint=None,
            metrics_collector_endpoint=None,
        )


async def test_span_not_exported_before_init_otel(cluster_addresses, is_cluster):
    """Test that spans are not exported before OpenTelemetry is initialized"""
    await teardown_otel_test()

    # Create client without initializing OpenTelemetry
    if is_cluster:
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=cluster_addresses,
                protocol=ProtocolVersion.RESP3,
            )
        )
    else:
        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=cluster_addresses,
                protocol=ProtocolVersion.RESP3,
            )
        )

    await client.get("testSpanNotExportedBeforeInitOtel")

    # Check that spans are not exported to the file before initializing OpenTelemetry
    assert not os.path.exists(VALID_ENDPOINT_TRACES)

    await client.close()


@pytest.fixture(scope="module")
def event_loop():
    """Create an event loop for the module scope"""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="module", autouse=True)
async def setup_opentelemetry(event_loop):
    """Initialize OpenTelemetry once for all tests"""
    # First check wrong configurations
    await test_wrong_opentelemetry_config()
    
    # Initialize OpenTelemetry with valid configuration
    config = OpenTelemetryConfig(
        traces_collector_endpoint=VALID_FILE_ENDPOINT_TRACES,
        metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
        sample_percentage=100,
        flush_interval_ms=100,
    )
    OpenTelemetry.init(config)
    
    yield
    
    # Clean up after all tests
    await teardown_otel_test()


class TestOpenTelemetryCluster:
    """Tests for OpenTelemetry with GlideClusterClient (cluster mode)"""

    @pytest.fixture(autouse=True)
    async def setup_teardown(self):
        """Setup and teardown for each test"""
        # Setup
        await teardown_otel_test()
        yield
        # Teardown
        await teardown_otel_test()

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_memory_leak(self, protocol):
        """Test that spans don't cause memory leaks with regular commands in cluster mode"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )

        # Execute a series of commands sequentially
        for i in range(100):
            key = f"test_key_{i}"
            await client.set(key, f"value_{i}")
            await client.get(key)

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_percentage_requests_config(self, protocol):
        """Test that sampling percentage controls span creation"""
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )
        
        # Set sampling to 0% - no spans should be created
        OpenTelemetry.set_sample_percentage(0)
        assert OpenTelemetry.get_sample_percentage() == 0
        
        # Wait for any spans to be flushed and remove the file
        await asyncio.sleep(0.5)
        await teardown_otel_test()
        
        # Execute commands - no spans should be created
        for i in range(100):
            await client.set(
                "GlideClusterClient_test_percentage_requests_config",
                "value",
            )
        
        # Wait for any potential spans to be flushed
        await asyncio.sleep(0.5)
        
        # Check that no spans were exported
        assert not os.path.exists(VALID_ENDPOINT_TRACES)
        
        # Set sampling to 100% - all commands should create spans
        OpenTelemetry.set_sample_percentage(100)
        
        # Execute commands - all should create spans
        for i in range(10):
            key = f"GlideClusterClient_test_percentage_requests_config_{i}"
            await client.get(key)
        
        # Wait for spans to be flushed to file
        await asyncio.sleep(0.5)
        
        # Check that spans were exported
        assert os.path.exists(VALID_ENDPOINT_TRACES)
        span_data = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        span_names = span_data["span_names"]
        
        # Check that the expected spans were created
        assert "Get" in span_names
        assert span_names.count("Get") == 10
        
        await client.close()

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_otel_global_config_not_reinitialize(self, protocol):
        """Test that OpenTelemetry global config cannot be reinitialized"""
        # Try to initialize with invalid config - should not throw error
        # and should not change the configuration
        config = OpenTelemetryConfig(
            traces_collector_endpoint=VALID_FILE_ENDPOINT_TRACES,
            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
        )
        OpenTelemetry.init(config)  # This should be a no-op since it's already initialized
        
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )
        
        # Execute command to generate span
        await client.set("GlideClusterClient_test_otel_global_config", "value")
        
        # Wait for spans to be flushed
        await asyncio.sleep(0.5)
        
        # Verify spans are created with original config
        assert os.path.exists(VALID_ENDPOINT_TRACES)
        span_data = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        span_names = span_data["span_names"]
        assert "Set" in span_names
        
        await client.close()

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_transaction_memory_leak(self, protocol):
        """Test that spans don't cause memory leaks with transactions in cluster mode"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )

        # Create and execute a transaction
        batch = ClusterBatch(is_atomic=True)
        batch.set("test_key", "foo")
        batch.objectRefcount("test_key")

        response = await client.exec(batch, True)
        assert response is not None
        assert len(response) == 2
        assert response[0] == "OK"  # batch.set("test_key", "foo")
        assert response[1] >= 1  # batch.objectRefcount("test_key")

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_multiple_clients_same_otel_config(self, protocol):
        """Test that multiple clients can be created with the same OpenTelemetry configuration"""
        client1 = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )
        
        client2 = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )
        
        # Execute commands with both clients
        await client1.set("test_key", "value")
        await client2.get("test_key")
        
        # Wait for spans to be flushed
        await asyncio.sleep(0.5)
        
        # Verify spans are created for both clients
        assert os.path.exists(VALID_ENDPOINT_TRACES)
        span_data = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        span_names = span_data["span_names"]
        
        # Check that the expected spans were created
        assert "Set" in span_names
        assert "Get" in span_names
        
        await client1.close()
        await client2.close()

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_batch_file(self, protocol):
        """Test that spans for batch operations are properly exported to file in cluster mode"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=pytest.valkey_cluster.nodes_addr,
                protocol=protocol,
            )
        )

        # Create and execute a transaction
        batch = ClusterBatch(is_atomic=True)
        batch.set("test_key", "foo")
        batch.objectRefcount("test_key")

        response = await client.exec(batch, True)
        assert response is not None
        assert len(response) == 2
        assert response[0] == "OK"  # batch.set("test_key", "foo")
        assert response[1] >= 1  # batch.objectRefcount("test_key")

        # Wait for spans to be flushed to file
        await asyncio.sleep(0.5)

        # Read and check span names from the file
        assert os.path.exists(VALID_ENDPOINT_TRACES)
        span_data = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        span_names = span_data["span_names"]

        # Check for expected span names
        assert "Batch" in span_names

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1


class TestOpenTelemetryStandalone:
    """Tests for OpenTelemetry with GlideClient (standalone mode)"""

    @pytest.fixture(autouse=True)
    async def setup_teardown(self):
        """Setup and teardown for each test"""
        # Setup
        await teardown_otel_test()
        yield
        # Teardown
        await teardown_otel_test()

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_automatic_span_lifecycle(self, protocol):
        """Test that spans are automatically created and cleaned up"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=pytest.standalone_cluster.nodes_addr,
                protocol=protocol,
            )
        )

        # Execute multiple commands - each should automatically create and clean up its span
        await client.set("test_key1", "value1")
        await client.get("test_key1")
        await client.set("test_key2", "value2")
        await client.get("test_key2")

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_otel_global_config_not_reinitialize(self, protocol):
        """Test that OpenTelemetry global config cannot be reinitialized"""
        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=pytest.standalone_cluster.nodes_addr,
                protocol=protocol,
            )
        )

        # Try to initialize with invalid config - should not throw error
        config = OpenTelemetryConfig(
            traces_collector_endpoint=VALID_FILE_ENDPOINT_TRACES,
            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
        )
        OpenTelemetry.init(config)  # This should be a no-op since it's already initialized
        
        # Execute command to generate span
        await client.set("test_key", "value")
        
        # Wait for spans to be flushed
        await asyncio.sleep(0.5)
        
        # Verify spans are created with original config
        assert os.path.exists(VALID_ENDPOINT_TRACES)
        
        await client.close()

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_concurrent_commands_span_lifecycle(self, protocol):
        """Test that spans don't cause memory leaks with concurrent commands"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=pytest.standalone_cluster.nodes_addr,
                protocol=protocol,
            )
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
        
        # Wait for spans to be flushed
        await asyncio.sleep(0.5)
        
        # Verify spans are created
        assert os.path.exists(VALID_ENDPOINT_TRACES)
        span_data = read_and_parse_span_file(VALID_ENDPOINT_TRACES)
        span_names = span_data["span_names"]
        
        # Check that the expected spans were created
        assert "Set" in span_names
        assert "Get" in span_names

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1
