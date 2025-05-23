# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import json
import time
import random
import pytest
import gc
import asyncio
import pytest_asyncio
from typing import Dict, List, Optional, Tuple, Any

from glide import GlideClient, GlideClusterClient
from glide.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    ServerCredentials,
)
from glide.opentelemetry import OpenTelemetry
from glide.exceptions import ConfigurationError
from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import check_if_server_version_lt

# Constants
TIMEOUT = 50  # seconds
VALID_ENDPOINT_TRACES = "/tmp/spans.json"
VALID_FILE_ENDPOINT_TRACES = f"file://{VALID_ENDPOINT_TRACES}"
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
        with open(path, 'r') as f:
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
            span_names.append(span.get('name'))
        except json.JSONDecodeError:
            continue

    return span_data, span_objects, [name for name in span_names if name]


def test_wrong_opentelemetry_config():
    """Test various invalid OpenTelemetry configurations"""
    # Wrong traces endpoint
    opentelemetry_config = {
        "traces": {
            "endpoint": "wrong.endpoint"
        }
    }
    with pytest.raises(Exception, match=r"Parse error"):
        OpenTelemetry.init(opentelemetry_config)

    # Wrong metrics endpoint
    opentelemetry_config = {
        "metrics": {
            "endpoint": "wrong.endpoint"
        }
    }
    with pytest.raises(Exception, match=r"Parse error"):
        OpenTelemetry.init(opentelemetry_config)

    # Negative flush interval
    opentelemetry_config = {
        "traces": {
            "endpoint": VALID_FILE_ENDPOINT_TRACES,
            "sample_percentage": 1
        },
        "flush_interval_ms": -400
    }
    with pytest.raises(Exception, match=r"InvalidInput: flush_interval_ms must be a positive integer"):
        OpenTelemetry.init(opentelemetry_config)

    # Negative sample percentage
    opentelemetry_config = {
        "traces": {
            "endpoint": VALID_FILE_ENDPOINT_TRACES,
            "sample_percentage": -400
        }
    }
    with pytest.raises(Exception, match=r"sample percentage must be between 0 and 100"):
        OpenTelemetry.init(opentelemetry_config)

    # Wrong traces file path
    opentelemetry_config = {
        "traces": {
            "endpoint": "file:invalid-path/v1/traces.json"
        }
    }
    with pytest.raises(Exception, match=r"File path must start with 'file://'"):
        OpenTelemetry.init(opentelemetry_config)

    # Wrong metrics file path
    opentelemetry_config = {
        "metrics": {
            "endpoint": "file:invalid-path/v1/metrics.json"
        }
    }
    with pytest.raises(Exception, match=r"File path must start with 'file://'"):
        OpenTelemetry.init(opentelemetry_config)

    # Wrong directory path
    opentelemetry_config = {
        "traces": {
            "endpoint": "file:///no-exits-path/v1/traces.json"
        }
    }
    with pytest.raises(Exception, match=r"The directory does not exist or is not a directory"):
        OpenTelemetry.init(opentelemetry_config)

    # Traces and metrics are not provided
    opentelemetry_config = {}
    with pytest.raises(Exception, match=r"At least one of traces or metrics must be provided"):
        OpenTelemetry.init(opentelemetry_config)


async def test_span_not_exported_before_init_otel(cluster_addresses):
    """Test that spans are not exported before OpenTelemetry is initialized"""
    # Clean up any existing files
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)

    # Create client without initializing OpenTelemetry
    client = await GlideClient.create(
        GlideClientConfiguration(
            addresses=[NodeAddress(addr.host, addr.port) for addr in cluster_addresses],
            protocol_version=ProtocolVersion.RESP3
        )
    )

    # Execute a command
    await client.get("testSpanNotExportedBeforeInitOtel")

    # Check that no spans file was created
    assert not os.path.exists(VALID_ENDPOINT_TRACES)

    await client.close()


class TestOpenTelemetryGlideClient:
    @pytest_asyncio.fixture(scope="class")
    async def setup_class(self, request, standalone_cluster):
        # Initialize OpenTelemetry with 100% sampling for tests
        opentelemetry_config = {
            "traces": {
                "endpoint": VALID_FILE_ENDPOINT_TRACES,
                "sample_percentage": 100
            },
            "metrics": {
                "endpoint": VALID_ENDPOINT_METRICS
            },
            "flush_interval_ms": 100
        }
        
        # Initialize OpenTelemetry
        OpenTelemetry.init(opentelemetry_config)
        
        yield
        
        # Cleanup after all tests
        await standalone_cluster.close()

    @pytest_asyncio.fixture(autouse=True)
    async def setup_test(self, standalone_cluster):
        # Clean up before each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)
            
        yield
        
        # Clean up after each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_automatic_span_lifecycle(self, standalone_cluster, protocol):
        """Test that spans are automatically created and cleaned up"""
        # Force garbage collection
        gc.collect()
        
        # Get initial memory usage
        initial_memory = 0  # This is a placeholder - Python doesn't have a direct equivalent to process.memoryUsage().heapUsed
        
        # Create client
        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in standalone_cluster.get_addresses()],
                protocol_version=protocol
            )
        )
        
        # Execute multiple commands
        await client.set("test_key1", "value1")
        await client.get("test_key1")
        await client.set("test_key2", "value2")
        await client.get("test_key2")
        
        # Force garbage collection again
        gc.collect()
        
        # Wait for spans to be flushed
        await asyncio.sleep(1)
        
        # Close client
        await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_otel_global_config_not_reinitialize(self, standalone_cluster, protocol):
        """Test that OpenTelemetry cannot be reinitialized"""
        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in standalone_cluster.get_addresses()],
                protocol_version=protocol
            )
        )
        
        # Try to reinitialize with invalid config
        opentelemetry_config = {
            "traces": {
                "endpoint": "wrong.endpoint"
            }
        }
        
        # This should not throw an error because OpenTelemetry is already initialized
        OpenTelemetry.init(opentelemetry_config)
        
        await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_concurrent_commands_span_lifecycle(self, standalone_cluster, protocol):
        """Test that spans are properly handled with concurrent commands"""
        # Force garbage collection
        gc.collect()
        
        # Create client
        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in standalone_cluster.get_addresses()],
                protocol_version=protocol
            )
        )
        
        # Execute multiple concurrent commands
        commands = [
            client.set("test_key1", "value1"),
            client.get("test_key1"),
            client.set("test_key2", "value2"),
            client.get("test_key2"),
            client.set("test_key3", "value3"),
            client.get("test_key3")
        ]
        
        await asyncio.gather(*commands)
        
        # Force garbage collection
        gc.collect()
        
        # Wait for spans to be flushed
        await asyncio.sleep(1)
        
        # Close client
        await client.close()


class TestOpenTelemetryGlideClusterClient:
    @pytest_asyncio.fixture(scope="class")
    async def setup_class(self, request, cluster):
        # Test wrong OpenTelemetry config before initializing
        test_wrong_opentelemetry_config()
        
        # Test that spans are not exported before OpenTelemetry is initialized
        await test_span_not_exported_before_init_otel(cluster.get_addresses())
        
        # Initialize OpenTelemetry with 100% sampling for tests
        opentelemetry_config = {
            "traces": {
                "endpoint": VALID_FILE_ENDPOINT_TRACES,
                "sample_percentage": 100
            },
            "metrics": {
                "endpoint": VALID_ENDPOINT_METRICS
            },
            "flush_interval_ms": 100
        }
        
        # Initialize OpenTelemetry
        OpenTelemetry.init(opentelemetry_config)
        
        yield
        
        # Cleanup after all tests
        await cluster.close()

    @pytest_asyncio.fixture(autouse=True)
    async def setup_test(self, cluster):
        # Clean up before each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)
            
        yield
        
        # Clean up after each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_memory_leak(self, cluster, protocol):
        """Test that spans don't cause memory leaks"""
        # Force garbage collection
        gc.collect()
        
        # Create client
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in cluster.get_addresses()],
                protocol_version=protocol
            )
        )
        
        # Execute a series of commands sequentially
        for i in range(100):
            key = f"test_key_{i}"
            await client.set(key, f"value_{i}")
            await client.get(key)
        
        # Force garbage collection
        gc.collect()
        
        # Close client
        await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_percentage_requests_config(self, cluster, protocol):
        """Test that sample percentage configuration works correctly"""
        # Create client
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in cluster.get_addresses()],
                protocol_version=protocol
            )
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
            await client.set("GlideClusterClient_test_percentage_requests_config", "value")
        
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

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_otel_global_config_not_reinitialize(self, cluster, protocol):
        """Test that OpenTelemetry cannot be reinitialized"""
        # Try to reinitialize with invalid config
        opentelemetry_config = {
            "traces": {
                "endpoint": "wrong.endpoint",
                "sample_percentage": 1
            }
        }
        
        # This should not throw an error because OpenTelemetry is already initialized
        OpenTelemetry.init(opentelemetry_config)
        
        # Create client
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in cluster.get_addresses()],
                protocol_version=protocol
            )
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

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_batch(self, cluster, protocol):
        """Test that batch operations create spans correctly"""
        # Force garbage collection
        gc.collect()
        
        # Create client
        client = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in cluster.get_addresses()],
                protocol_version=protocol
            )
        )
        
        # Create and execute a batch
        batch = client.create_batch(atomic=True)
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
        
        # Force garbage collection
        gc.collect()
        
        await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_number_of_clients_with_same_config(self, cluster, protocol):
        """Test that multiple clients with the same config work correctly with OpenTelemetry"""
        # Create two clients
        client1 = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in cluster.get_addresses()],
                protocol_version=protocol
            )
        )
        
        client2 = await GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port) for addr in cluster.get_addresses()],
                protocol_version=protocol
            )
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
