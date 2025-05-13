# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import gc
import json
import os
import sys
from typing import Any, Dict

import pytest
from glide import GlideClient, GlideClusterClient, ProtocolVersion
from glide.async_commands.transaction import ClusterTransaction, Transaction
from glide.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    OpenTelemetryConfig,
)
from glide.exceptions import ConfigurationError

# Constants
TIMEOUT = 50000  # milliseconds
VALID_ENDPOINT_TRACES = "file:///tmp/spans.json"
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


@pytest.fixture
def temp_span_file(tmp_path):
    """Create a temporary file path for spans"""
    span_dir = tmp_path / "spans"
    span_dir.mkdir(exist_ok=True)
    span_file = span_dir / "spans.json"
    yield str(span_file)
    # Cleanup
    if span_file.exists():
        os.unlink(str(span_file))


class TestOpenTelemetryStandalone:
    """Tests for OpenTelemetry with GlideClient (standalone mode)"""

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_span_memory_leak(self, protocol):
        """Test that spans don't cause memory leaks with regular commands"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=pytest.standalone_cluster.nodes_addr,
                protocol=protocol,
                advanced_config=AdvancedGlideClientConfiguration(
                    opentelemetry_config=OpenTelemetryConfig(
                        traces_collector_endpoint=VALID_ENDPOINT_TRACES,
                        metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                    )
                ),
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
    async def test_span_transaction_memory_leak(self, protocol):
        """Test that spans don't cause memory leaks with transactions"""
        # Skip if gc.collect is not available
        if not hasattr(gc, "collect"):
            pytest.skip("gc.collect not available")

        gc.collect()
        start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=pytest.standalone_cluster.nodes_addr,
                protocol=protocol,
                advanced_config=AdvancedGlideClientConfiguration(
                    opentelemetry_config=OpenTelemetryConfig(
                        traces_collector_endpoint=VALID_ENDPOINT_TRACES,
                        metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                        flush_interval_ms=100,
                    )
                ),
            )
        )

        # Remove the span file if it exists
        if os.path.exists("/tmp/spans.json"):
            os.unlink("/tmp/spans.json")

        # Create and execute a transaction
        transaction = Transaction()
        transaction.set("test_key", "foo")
        transaction.get("test_key")
        response = await client.exec(transaction)

        assert response is not None
        assert len(response) == 2
        assert response[0] == "OK"
        assert response[1] == b"foo"

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1

    # TODO: uncomment test after fix OTel global config issue (#3317)
    # @pytest.mark.asyncio
    # @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    # async def test_span_file_export(self, protocol):
    #     """Test that spans are properly exported to file"""
    #     # Skip if gc.collect is not available
    #     if not hasattr(gc, 'collect'):
    #         pytest.skip("gc.collect not available")

    #     gc.collect()
    #     start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

    #     path = "/tmp/spans.json"

    #     # Remove the span file if it exists
    #     if os.path.exists(path):
    #         os.unlink(path)

    #     client = await GlideClient.create(
    #         GlideClientConfiguration(
    #             addresses=pytest.standalone_cluster.nodes_addr,
    #             protocol=protocol,
    #             advanced_config=AdvancedGlideClientConfiguration(
    #                 opentelemetry_config=OpenTelemetryConfig(
    #                     traces_collector_endpoint=f"file://{path}",
    #                     metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
    #                     flush_interval_ms=100,
    #                 )
    #             )
    #         )
    #     )

    #     # Execute commands to create spans
    #     await client.set("test_key", "foo")
    #     await client.get("test_key")

    #     # Wait for spans to be flushed to file
    #     await asyncio.sleep(0.5)

    #     # Create and execute a transaction
    #     transaction = Transaction()
    #     transaction.set("test_key2", "bar")
    #     transaction.get("test_key2")
    #     response = await client.exec(transaction)

    #     # Wait for spans to be flushed to file
    #     await asyncio.sleep(0.5)

    #     await client.close()

    #     # Read and check span names from the file
    #     span_data = read_and_parse_span_file(path)
    #     span_names = span_data['span_names']

    #     # Check for expected span names
    #     assert "Set" in span_names
    #     assert "Get" in span_names
    #     assert "Batch" in span_names

    #     gc.collect()
    #     end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

    #     # Allow for small fluctuations
    #     assert end_memory < start_memory * 1.1

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_wrong_config_negative_flush_interval(self, protocol):
        """Test that negative flush interval raises ConfigurationError"""
        with pytest.raises(ConfigurationError, match=r".*flushIntervalMs.*negative.*"):
            await GlideClient.create(
                GlideClientConfiguration(
                    addresses=pytest.standalone_cluster.nodes_addr,  # pytest.standalone_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint=VALID_ENDPOINT_TRACES,
                            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                            flush_interval_ms=-400,
                        )
                    ),
                )
            )

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_wrong_file_path_config(self, protocol):
        """Test that invalid file path raises ConfigurationError"""
        with pytest.raises(ConfigurationError, match=r".*InvalidInput.*"):
            await GlideClient.create(
                GlideClientConfiguration(
                    addresses=pytest.standalone_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint="file:invalid-path/v1/traces.json",
                            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                            flush_interval_ms=400,
                        )
                    ),
                )
            )

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_not_exists_folder_path_config(self, protocol):
        """Test that non-existent folder path raises an exception"""
        with pytest.raises(Exception, match=r".*InvalidInput.*"):
            await GlideClient.create(
                GlideClientConfiguration(
                    addresses=pytest.standalone_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint="file:///no-exits-path/v1/traces.json",
                            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                            flush_interval_ms=400,
                        )
                    ),
                )
            )

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_config_wrong_parameter(self, protocol):
        """Test that invalid endpoint format raises ConfigurationError"""
        with pytest.raises(ConfigurationError, match=r".*InvalidInput.*"):
            await GlideClient.create(
                GlideClientConfiguration(
                    addresses=pytest.standalone_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint="wrong.endpoint",
                            metrics_collector_endpoint="wrong.endpoint",
                            flush_interval_ms=400,
                        )
                    ),
                )
            )


class TestOpenTelemetryCluster:
    """Tests for OpenTelemetry with GlideClusterClient (cluster mode)"""

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
                advanced_config=AdvancedGlideClusterClientConfiguration(
                    opentelemetry_config=OpenTelemetryConfig(
                        traces_collector_endpoint=VALID_ENDPOINT_TRACES,
                        metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                    )
                ),
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
                advanced_config=AdvancedGlideClusterClientConfiguration(
                    opentelemetry_config=OpenTelemetryConfig(
                        traces_collector_endpoint=VALID_ENDPOINT_TRACES,
                        metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                        flush_interval_ms=100,
                    )
                ),
            )
        )

        # Remove the span file if it exists
        if os.path.exists("/tmp/spans.json"):
            os.unlink("/tmp/spans.json")

        # Create and execute a transaction
        transaction = ClusterTransaction()
        transaction.set("test_key", "foo")
        transaction.get("test_key")
        response = await client.exec(transaction)

        assert response is not None
        assert len(response) == 2
        assert response[0] == "OK"
        assert response[1] == b"foo"

        await client.close()

        gc.collect()
        end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

        # Allow for small fluctuations
        assert end_memory < start_memory * 1.1

    # TODO: uncomment test after fix OTel global config issue (#3317)
    # @pytest.mark.asyncio
    # @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    # async def test_span_batch_file(self, protocol):
    #     """Test that spans for batch operations are properly exported to file in cluster mode"""
    #     # Skip if gc.collect is not available
    #     if not hasattr(gc, 'collect'):
    #         pytest.skip("gc.collect not available")

    #     gc.collect()
    #     start_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

    #     path = "/tmp/spans.json"

    #     # Remove the span file if it exists
    #     if os.path.exists(path):
    #         os.unlink(path)

    #     client = await GlideClusterClient.create(
    #         GlideClusterClientConfiguration(
    #             addresses=pytest.valkey_cluster.nodes_addr,
    #             protocol=protocol,
    #             advanced_config=AdvancedGlideClusterClientConfiguration(
    #                 opentelemetry_config=OpenTelemetryConfig(
    #                     traces_collector_endpoint=f"file://{path}",
    #                     metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
    #                     flush_interval_ms=100,
    #                 )
    #             )
    #         )
    #     )

    #     # Create and execute a transaction
    #     transaction = ClusterTransaction()
    #     transaction.set("test_key", "foo")
    #     transaction.get("test_key")
    #     response = await client.exec(transaction)

    #     assert response is not None
    #     assert len(response) == 2
    #     assert response[0] == "OK"
    #     assert response[1] == b"foo"

    #     # Wait for spans to be flushed to file
    #     await asyncio.sleep(0.5)

    #     # Read and check span names from the file
    #     span_data = read_and_parse_span_file(path)
    #     span_names = span_data['span_names']

    #     # Check for expected span names
    #     assert "Batch" in span_names

    #     await client.close()

    #     gc.collect()
    #     end_memory = sum(sys.getsizeof(x) for x in gc.get_objects())

    #     # Allow for small fluctuations
    #     assert end_memory < start_memory * 1.1

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_wrong_config_negative_flush_interval(self, protocol):
        """Test that negative flush interval raises ConfigurationError in cluster mode"""
        with pytest.raises(ConfigurationError, match=r".*flushIntervalMs.*negative.*"):
            await GlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=pytest.valkey_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClusterClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint=VALID_ENDPOINT_TRACES,
                            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                            flush_interval_ms=-400,
                        )
                    ),
                )
            )

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_wrong_file_path_config(self, protocol):
        """Test that invalid file path raises ConfigurationError in cluster mode"""
        with pytest.raises(ConfigurationError, match=r".*InvalidInput.*"):
            await GlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=pytest.valkey_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClusterClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint="file:invalid-path/v1/traces.json",
                            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                            flush_interval_ms=400,
                        )
                    ),
                )
            )

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_not_exists_folder_path_config(self, protocol):
        """Test that non-existent folder path raises a exception in cluster mode"""
        with pytest.raises(Exception, match=r".*InvalidInput.*"):
            await GlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=pytest.valkey_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClusterClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint="file:///no-exits-path/v1/traces.json",
                            metrics_collector_endpoint=VALID_ENDPOINT_METRICS,
                            flush_interval_ms=400,
                        )
                    ),
                )
            )

    @pytest.mark.asyncio
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_opentelemetry_config_wrong_parameter(self, protocol):
        """Test that invalid endpoint format raises ConfigurationError in cluster mode"""
        with pytest.raises(ConfigurationError, match=r".*InvalidInput.*"):
            await GlideClusterClient.create(
                GlideClusterClientConfiguration(
                    addresses=pytest.valkey_cluster.nodes_addr,
                    protocol=protocol,
                    advanced_config=AdvancedGlideClusterClientConfiguration(
                        opentelemetry_config=OpenTelemetryConfig(
                            traces_collector_endpoint="wrong.endpoint",
                            metrics_collector_endpoint="wrong.endpoint",
                            flush_interval_ms=400,
                        )
                    ),
                )
            )
