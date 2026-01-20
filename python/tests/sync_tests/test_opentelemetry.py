# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import gc
import os
import time

import psutil  # type: ignore[import-untyped]
import pytest
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.config import ProtocolVersion
from glide_sync import (
    OpenTelemetryConfig,
    OpenTelemetryMetricsConfig,
    OpenTelemetryTracesConfig,
)
from glide_sync.opentelemetry import OpenTelemetry

from tests.sync_tests.conftest import create_sync_client
from tests.test_otel_utils import (
    read_and_parse_span_file,
    wait_for_spans_sync as wait_for_spans_to_be_flushed,
)

# Constants
TIMEOUT = 50  # seconds
VALID_ENDPOINT_TRACES = "/tmp/spans_sync.json"
VALID_FILE_ENDPOINT_TRACES = f"file://{VALID_ENDPOINT_TRACES}"
VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics"


def test_wrong_opentelemetry_config():
    """Test various invalid OpenTelemetry configurations"""
    from glide_shared.exceptions import ConfigurationError

    # Wrong traces endpoint
    with pytest.raises(ConfigurationError, match=r".*Parse error.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="wrong.endpoint",
                    sample_percentage=100,
                ),
            )
        )

    # Wrong metrics endpoint
    with pytest.raises(ConfigurationError, match=r".*Parse error.*"):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                metrics=OpenTelemetryMetricsConfig(
                    endpoint="wrong.endpoint",
                ),
            )
        )

    # Negative flush interval
    with pytest.raises(
        ConfigurationError,
        match=r".*flushIntervalMs must be a positive integer.*",
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
    with pytest.raises(
        OverflowError, match=r".*can't convert negative number to unsigned*"
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
    with pytest.raises(
        ConfigurationError, match=r".*File path must start with 'file://'.*"
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=OpenTelemetryTracesConfig(
                    endpoint="file:invalid-path/v1/traces.json",
                    sample_percentage=100,
                ),
            )
        )

    # Wrong metrics file path
    with pytest.raises(
        ConfigurationError, match=r".*File path must start with 'file://'.*"
    ):
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
        ConfigurationError,
        match=r".*The directory does not exist or is not a directory.*",
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
        ConfigurationError,
        match=r".*At least one of traces or metrics must be provided.*",
    ):
        OpenTelemetry.init(
            OpenTelemetryConfig(
                traces=None,
                metrics=None,
            )
        )


def test_span_not_exported_before_init_otel(request):
    """Test that spans are not exported before OpenTelemetry is initialized"""
    # Clean up any existing files
    if os.path.exists(VALID_ENDPOINT_TRACES):
        os.unlink(VALID_ENDPOINT_TRACES)

    client = create_sync_client(
        request,
        cluster_mode=False,
        protocol=ProtocolVersion.RESP3,
    )

    # Execute a command
    client.get("testSpanNotExportedBeforeInitOtel")

    # Check that no spans file was created
    assert not os.path.exists(VALID_ENDPOINT_TRACES)

    client.close()


class TestOpenTelemetryGlideSync:
    @pytest.fixture(scope="class")
    def setup_class(self, request):
        # Test wrong OpenTelemetry config before initializing
        test_wrong_opentelemetry_config()

        # Test that spans are not exported before OpenTelemetry is initialized
        test_span_not_exported_before_init_otel(request)

    @pytest.fixture(autouse=True)
    def setup_test(self, request, cluster_mode):
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

        # Clean up before each test - ensure file is completely removed
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        # Give a small delay to ensure OpenTelemetry is fully initialized
        time.sleep(0.1)

        yield

        # Clean up after each test
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        client = create_sync_client(
            request, cluster_mode=cluster_mode, request_timeout=2000
        )
        client.custom_command(["FLUSHALL"])
        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_span_memory_leak(self, request, protocol, cluster_mode):
        """Test that spans don't cause memory leaks"""
        # Force garbage collection
        gc.collect()

        # Create client and get initial memory usage
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Execute a series of commands sequentially
        for i in range(100):
            key = f"test_key_{i}"
            client.set(key, f"value_{i}")
            client.get(key)

        # Close client
        client.close()

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
    def test_percentage_requests_config(self, request, protocol, cluster_mode):
        """Test that sample percentage configuration works correctly"""
        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Set sample percentage to 0%
        OpenTelemetry.set_sample_percentage(0)
        assert OpenTelemetry.get_sample_percentage() == 0

        # Wait for any pending spans to be flushed
        time.sleep(0.5)

        # Clean up any existing files
        if os.path.exists(VALID_ENDPOINT_TRACES):
            os.unlink(VALID_ENDPOINT_TRACES)

        # Execute commands with 0% sampling
        for i in range(100):
            client.set("GlideClient_test_percentage_requests_config", "value")

        # Wait for any spans to be flushed (though none should be created)
        time.sleep(0.5)

        # Check that no spans file was created
        assert not os.path.exists(VALID_ENDPOINT_TRACES)

        # Set sample percentage to 100%
        OpenTelemetry.set_sample_percentage(100)

        # Execute commands with 100% sampling
        for i in range(10):
            key = f"GlideClient_test_percentage_requests_config_{i}"
            client.get(key)

        # Wait for spans to be flushed
        wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES,
            expected_span_names=["Get"],
            expected_span_counts={"Get": 10},
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Get" spans were created
        assert "Get" in span_names

        # Check that exactly 10 "Get" spans were created
        assert span_names.count("Get") == 10

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_otel_global_config_not_reinitialize(self, request, protocol, cluster_mode):
        """Test that OpenTelemetry cannot be reinitialized"""
        # Try to reinitialize with invalid config
        opentelemetry_config = OpenTelemetryConfig(
            OpenTelemetryTracesConfig(endpoint="wrong.endpoint", sample_percentage=1)
        )

        # This should not throw an error because OpenTelemetry is already initialized
        OpenTelemetry.init(opentelemetry_config)

        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute a command
        client.set("GlideClient_test_otel_global_config", "value")

        # Wait for spans to be flushed
        time.sleep(0.5)

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check that "Set" spans were created
        assert "Set" in span_names

        client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_span_batch(self, request, protocol, cluster_mode):
        """Test that batch operations create spans correctly"""
        # Force garbage collection
        gc.collect()

        # Get initial memory usage
        process = psutil.Process()
        initial_memory = process.memory_info().rss

        # Create client
        client = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Use appropriate batch type based on cluster mode
        if cluster_mode:
            batch = ClusterBatch(is_atomic=True)
        else:
            batch = Batch(is_atomic=True)

        batch.set("test_key", "foo")
        batch.object_refcount("test_key")

        response = client.exec(batch, raise_on_error=True)
        assert response is not None

        if response is not None:
            assert len(response) == 2
            assert response[0] == "OK"  # batch.set("test_key", "foo")
            assert response[1] >= 1  # batch.object_refcount("test_key")

        # Wait for spans to be flushed
        wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Batch", "send_batch"]
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Batch" in span_names
        assert "send_batch" in span_names

        # Force garbage collection
        gc.collect()

        client.close()

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
    def test_number_of_clients_with_same_config(self, request, protocol, cluster_mode):
        """Test that multiple clients with the same config work correctly with OpenTelemetry"""
        # Create two clients
        client1 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        client2 = create_sync_client(
            request,
            cluster_mode=cluster_mode,
            protocol=protocol,
        )

        # Execute commands on both clients
        client1.set("test_key", "value")
        client2.get("test_key")

        # Wait for spans to be flushed with retry mechanism
        wait_for_spans_to_be_flushed(
            VALID_ENDPOINT_TRACES, expected_span_names=["Set", "Get"]
        )

        # Read the span file and check span names
        _, _, span_names = read_and_parse_span_file(VALID_ENDPOINT_TRACES)

        # Check for expected span names
        assert "Get" in span_names
        assert "Set" in span_names

        # Close clients
        client1.close()
        client2.close()
