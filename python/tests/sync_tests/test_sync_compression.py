# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import base64
import json
import os
import random
from typing import List, Union, cast

import pytest
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.config import (
    CompressionBackend,
    CompressionConfiguration,
    ProtocolVersion,
)
from glide_shared.constants import OK
from glide_sync import GlideClient, GlideClusterClient, TGlideClient

from tests.sync_tests.conftest import create_sync_client
from tests.utils.utils import create_sync_client_config, get_random_string


# Data Generation Functions
def generate_compressible_text(size_bytes: int) -> str:
    """Generate highly compressible text (repeated patterns)."""
    pattern = "A" * 10 + "B" * 10 + "C" * 10
    return (pattern * (size_bytes // len(pattern) + 1))[:size_bytes]


def generate_json_data(size_bytes: int) -> str:
    """Generate JSON-like structured data."""
    base_obj = {
        "id": 12345,
        "name": "Test User",
        "email": "test@example.com",
        "description": "A" * 100,
        "metadata": {"key": "value"},
        "tags": ["tag1", "tag2", "tag3"],
    }
    json_str = json.dumps(base_obj)
    return (json_str * (size_bytes // len(json_str) + 1))[:size_bytes]


def generate_xml_data(size_bytes: int) -> str:
    """Generate XML-like structured data."""
    pattern = "<record><id>123</id><name>Test</name><value>Data</value></record>"
    return (pattern * (size_bytes // len(pattern) + 1))[:size_bytes]


def generate_base64_data(size_bytes: int) -> str:
    """Generate base64 encoded data (low compressibility)."""
    binary_data = os.urandom(size_bytes // 2)
    return base64.b64encode(binary_data).decode("ascii")[:size_bytes]


def generate_unicode_text(size_bytes: int) -> str:
    """Generate text with unicode characters."""
    chars = "Hello世界🌍Привет مرحبا"
    result = chars * (size_bytes // len(chars.encode("utf-8")) + 1)
    return result.encode("utf-8")[:size_bytes].decode("utf-8", errors="ignore")


def generate_mixed_size_list(count: int, min_size: int, max_size: int) -> List[str]:
    """Generate list of strings with varying sizes."""
    return [
        generate_compressible_text(random.randint(min_size, max_size))
        for _ in range(count)
    ]


# Test Fixtures
@pytest.fixture
def compression_client(request, cluster_mode, protocol):
    """Create client with compression enabled using ZSTD backend."""
    client = create_sync_client(
        request,
        cluster_mode,
        protocol=protocol,
        enable_compression=True,
    )
    yield client
    client.close()


@pytest.fixture
def no_compression_client(request, cluster_mode, protocol):
    """Create client without compression (default behavior)."""
    client = create_sync_client(
        request,
        cluster_mode,
        protocol=protocol,
        enable_compression=False,
    )
    yield client
    client.close()


class TestBasicCompression:
    """Test basic compression functionality."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "data_size", [512, 1024, 10240, 102400, 1048576]
    )  # 512B, 1KB, 10KB, 100KB, 1MB
    def test_compression_basic_set_get(
        self, compression_client: TGlideClient, data_size: int
    ):
        """Verify basic SET/GET operations work with compression."""
        key = f"test_compression_{data_size}_{get_random_string(8)}"
        value = generate_compressible_text(data_size)

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set value with compression
        assert compression_client.set(key, value) == OK

        # Get value and verify it matches
        retrieved = compression_client.get(key)
        assert retrieved == value.encode()

        # Verify compression was applied (all sizes are >= 64B threshold)
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), f"Compression should be applied for {data_size}B value"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Cleanup
        compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_min_size_threshold(self, compression_client: TGlideClient):
        """Verify min_compression_size threshold is respected."""
        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_skipped = initial_stats["compression_skipped_count"]
        initial_compressed = initial_stats["total_values_compressed"]

        # Test values below threshold (should be skipped)
        for size in [32, 48, 63]:
            key = f"below_threshold_{size}_{get_random_string(8)}"
            value = generate_compressible_text(size)

            # Set value
            compression_client.set(key, value)
            assert compression_client.get(key) == value.encode()

            # Check statistics: compression should have been skipped
            stats = compression_client.get_statistics()
            skipped_count = stats["compression_skipped_count"]
            compressed_count = stats["total_values_compressed"]

            assert skipped_count > initial_skipped, (
                f"Size {size}: Compression should be skipped below threshold. "
                f"Skipped: {skipped_count}, Initial: {initial_skipped}"
            )

            assert compressed_count == initial_compressed, (
                f"Size {size}: No values should be compressed below threshold. "
                f"Compressed: {compressed_count}, Initial: {initial_compressed}"
            )

            # Update baseline for next iteration
            initial_skipped = skipped_count

            compression_client.delete([key])

        # Test values at/above threshold (should be compressed)
        for size in [64, 128, 256]:
            key = f"above_threshold_{size}_{get_random_string(8)}"
            value = generate_compressible_text(size)

            # Set value
            compression_client.set(key, value)
            assert compression_client.get(key) == value.encode()

            # Check statistics: compression should have been applied
            stats = compression_client.get_statistics()
            compressed_count = stats["total_values_compressed"]

            assert compressed_count > initial_compressed, (
                f"Size {size}: Compression should be applied at/above threshold. "
                f"Compressed: {compressed_count}, Initial: {initial_compressed}"
            )

            # Update baseline for next iteration
            initial_compressed = compressed_count

            compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_disabled_by_default(self, no_compression_client: TGlideClient):
        """Verify operations work and compression is NOT applied when disabled."""
        sizes = [64, 1024, 10240, 102400]  # 64B, 1KB, 10KB, 100KB

        # Get initial statistics
        initial_stats = no_compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_skipped = initial_stats["compression_skipped_count"]

        for size in sizes:
            key = f"no_compression_{size}_{get_random_string(8)}"
            value = generate_compressible_text(size)

            # Set and get value
            assert no_compression_client.set(key, value) == OK
            assert no_compression_client.get(key) == value.encode()

            # Verify no compression was applied
            stats = no_compression_client.get_statistics()
            assert (
                stats["total_values_compressed"] == initial_compressed
            ), f"No compression should be applied when disabled. Size: {size}B"
            assert (
                stats["compression_skipped_count"] == initial_skipped
            ), f"Compression should not even be attempted when disabled. Size: {size}B"

            # Cleanup
            no_compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_mget_decompression(self, compression_client: TGlideClient):
        """Test MGET decompression of compressed values."""
        num_keys = 5
        keys_and_values = []

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Set multiple compressed values
        for i in range(num_keys):
            key = f"mget_test_{i}_{get_random_string(8)}"
            value = generate_compressible_text(1024)  # 1KB
            keys_and_values.append((key, value))
            compression_client.set(key, value)

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Values should be compressed"

        # Use MGET to retrieve all values
        keys = [k for k, _ in keys_and_values]
        retrieved_values = compression_client.mget(cast(List[Union[str, bytes]], keys))

        # Verify all values are properly decompressed
        for i, (key, expected_value) in enumerate(keys_and_values):
            retrieved = retrieved_values[i]
            assert isinstance(
                retrieved, bytes
            ), f"MGET should return bytes for key {key}"
            assert retrieved.decode() == expected_value, (
                f"MGET should return decompressed value for key {key}. "
                f"Expected: {expected_value[:50]}..., Got: {retrieved.decode()[:50]}..."
            )

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], keys))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_getex_decompression(self, compression_client: TGlideClient):
        """Test GETEX decompression of compressed values."""
        key = f"getex_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Set compressed value
        compression_client.set(key, value)

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Value should be compressed"

        # Use GETEX to retrieve and set expiration
        retrieved = compression_client.getex(key, None)  # type: ignore[arg-type]

        # Verify value is properly decompressed
        assert isinstance(retrieved, bytes), "GETEX should return bytes"
        assert retrieved.decode() == value, (
            f"GETEX should return decompressed value. "
            f"Expected: {value[:50]}..., Got: {retrieved.decode()[:50]}..."
        )

        # Verify expiration was set
        ttl = compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10, "GETEX should set expiration"

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], [key]))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_getdel_decompression(self, compression_client: TGlideClient):
        """Test GETDEL decompression of compressed values."""
        key = f"getdel_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Set compressed value
        compression_client.set(key, value)

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Value should be compressed"

        # Use GETDEL to retrieve and delete
        retrieved = compression_client.getdel(key)

        # Verify value is properly decompressed
        assert isinstance(retrieved, bytes), "GETDEL should return bytes"
        assert retrieved.decode() == value, (
            f"GETDEL should return decompressed value. "
            f"Expected: {value[:50]}..., Got: {retrieved.decode()[:50]}..."
        )

        # Verify key was deleted
        assert compression_client.get(key) is None, "GETDEL should delete the key"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_mset_compression(self, compression_client: TGlideClient):
        """Test MSET compression of values."""
        num_keys = 5
        key_value_map = {}

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Create key-value pairs
        for i in range(num_keys):
            key = f"mset_test_{i}_{get_random_string(8)}"
            value = generate_compressible_text(1024)  # 1KB
            key_value_map[key] = value

        # Use MSET to set all values
        compression_client.mset(
            cast(dict[Union[str, bytes], Union[str, bytes]], key_value_map)
        )

        # Verify compression was applied to all values
        stats = compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"] - initial_compressed
        assert (
            compressed_count >= num_keys
        ), f"MSET should compress values above threshold. Compressed: {compressed_count}, Expected: >= {num_keys}"

        # Verify all values can be retrieved and decompressed
        for key, expected_value in key_value_map.items():
            retrieved = compression_client.get(key)
            assert retrieved == expected_value.encode(), f"Value mismatch for key {key}"

        # Cleanup
        keys_to_delete = cast(List[Union[str, bytes]], list(key_value_map.keys()))
        compression_client.delete(keys_to_delete)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_setex_compression(self, compression_client: TGlideClient):
        """Test SETEX compression of values."""
        key = f"setex_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use SETEX via custom command
        result = compression_client.custom_command(
            cast(List[Union[str, bytes]], ["SETEX", key, "10", value])
        )
        assert result == OK

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "SETEX should compress values above threshold"

        # Verify value can be retrieved and decompressed
        retrieved = compression_client.get(key)
        assert (
            retrieved == value.encode()
        ), "SETEX value should be decompressed correctly"

        # Verify expiration was set
        ttl = compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10, "SETEX should set expiration"

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], [key]))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_psetex_compression(self, compression_client: TGlideClient):
        """Test PSETEX compression of values."""
        key = f"psetex_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use PSETEX via custom command (10000ms = 10s)
        result = compression_client.custom_command(
            cast(List[Union[str, bytes]], ["PSETEX", key, "10000", value])
        )
        assert result == OK

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "PSETEX should compress values above threshold"

        # Verify value can be retrieved and decompressed
        retrieved = compression_client.get(key)
        assert (
            retrieved == value.encode()
        ), "PSETEX value should be decompressed correctly"

        # Verify expiration was set
        ttl = compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10, "PSETEX should set expiration"

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], [key]))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_setnx_compression(self, compression_client: TGlideClient):
        """Test SETNX compression of values."""
        key = f"setnx_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use SETNX via custom command
        result = compression_client.custom_command(
            cast(List[Union[str, bytes]], ["SETNX", key, value])
        )
        assert result == 1  # SETNX returns 1 when key is set

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "SETNX should compress values above threshold"

        # Verify value can be retrieved and decompressed
        retrieved = compression_client.get(key)
        assert (
            retrieved == value.encode()
        ), "SETNX value should be decompressed correctly"

        # Verify SETNX doesn't overwrite existing key
        result2 = compression_client.custom_command(
            cast(List[Union[str, bytes]], ["SETNX", key, "different_value"])
        )
        assert result2 == 0  # SETNX returns 0 when key already exists

        # Value should remain unchanged
        retrieved2 = compression_client.get(key)
        assert retrieved2 == value.encode(), "SETNX should not overwrite existing key"

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], [key]))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_custom_command_decompression(
        self, compression_client: TGlideClient
    ):
        """Test custom command decompression of compressed values."""
        key = f"custom_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Set compressed value using regular SET
        compression_client.set(key, value)

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Value should be compressed"

        # Use custom GET command to retrieve value
        retrieved = compression_client.custom_command(
            cast(List[Union[str, bytes]], ["GET", key])
        )

        # Verify value is properly decompressed
        assert isinstance(retrieved, bytes), "Custom GET should return bytes"
        assert retrieved.decode() == value, (
            f"Custom GET should return decompressed value. "
            f"Expected: {value[:50]}..., Got: {retrieved.decode()[:50]}..."
        )

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], [key]))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_custom_mget_decompression(
        self, compression_client: TGlideClient
    ):
        """Test custom MGET command decompression of compressed values."""
        num_keys = 3
        keys_and_values = []

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Set multiple compressed values
        for i in range(num_keys):
            key = f"custom_mget_test_{i}_{get_random_string(8)}"
            value = generate_compressible_text(1024)  # 1KB
            keys_and_values.append((key, value))
            compression_client.set(key, value)

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Values should be compressed"

        # Use custom MGET command to retrieve all values
        keys = [k for k, _ in keys_and_values]
        retrieved_values = compression_client.custom_command(
            cast(List[Union[str, bytes]], ["MGET"] + keys)
        )

        # Verify all values are properly decompressed
        assert isinstance(retrieved_values, list), "Custom MGET should return a list"
        assert len(retrieved_values) == num_keys, f"Should return {num_keys} values"

        for i, (key, expected_value) in enumerate(keys_and_values):
            retrieved = retrieved_values[i]
            assert isinstance(
                retrieved, bytes
            ), f"Custom MGET should return bytes for key {key}"
            assert retrieved.decode() == expected_value, (
                f"Custom MGET should return decompressed value for key {key}. "
                f"Expected: {expected_value[:50]}..., Got: {retrieved.decode()[:50]}..."
            )

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], keys))


class TestCompressionDataTypes:
    """Test compression with different data types."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "data_type,generator",
        [
            ("compressible_text", generate_compressible_text),
            ("json", generate_json_data),
            ("xml", generate_xml_data),
            ("base64", generate_base64_data),
            ("unicode", generate_unicode_text),
        ],
    )
    @pytest.mark.parametrize("size", [1024, 10240, 102400])  # 1KB, 10KB, 100KB
    def test_compression_string_types(
        self, compression_client: TGlideClient, data_type: str, generator, size: int
    ):
        """Test compression with different string content types."""
        key = f"test_{data_type}_{size}_{get_random_string(8)}"
        value = generator(size)

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set value
        assert compression_client.set(key, value) == OK

        # Get and verify value
        retrieved = compression_client.get(key)
        assert retrieved == value.encode()

        # Verify compression was applied (all sizes are >= 64B threshold)
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), f"Compression should be applied for {data_type} {size}B value"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"{data_type}: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Cleanup
        compression_client.delete([key])


class TestCompressionBatch:
    """Test compression in batch/pipeline operations."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_batch_set_get(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test compression in batch operations."""
        num_keys = 100
        key_prefix = f"batch_test_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Create batch
        batch = (
            Batch(is_atomic=False)
            if isinstance(compression_client, GlideClient)
            else ClusterBatch(is_atomic=False)
        )

        # Add SET commands to batch
        keys_and_values = []
        for i in range(num_keys):
            key = f"{key_prefix}_{i}"
            size = random.randint(1024, 10240)  # 1KB to 10KB
            value = generate_compressible_text(size)
            keys_and_values.append((key, value))
            batch.set(key, value)

        # Execute batch
        if isinstance(compression_client, GlideClient):
            results = cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert all(r == OK for r in results)

        # Verify compression was applied to all values
        stats = compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"] - initial_compressed
        assert (
            compressed_count == num_keys
        ), f"All {num_keys} values should be compressed, got {compressed_count}"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Batch: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Verify values
        for key, expected_value in keys_and_values:
            retrieved = compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        keys_to_delete: list[str | bytes] = [k for k, _ in keys_and_values]
        compression_client.delete(keys_to_delete)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_batch_mixed_sizes(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test compression with mixed value sizes in batch."""
        key_prefix = f"mixed_batch_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_skipped = initial_stats["compression_skipped_count"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Create batch with mixed sizes
        batch = (
            Batch(is_atomic=False)
            if isinstance(compression_client, GlideClient)
            else ClusterBatch(is_atomic=False)
        )

        keys_and_values = []
        # Small values (below threshold)
        for i in range(10):
            key = f"{key_prefix}_small_{i}"
            value = generate_compressible_text(32)
            keys_and_values.append((key, value))
            batch.set(key, value)

        # Medium values
        for i in range(10):
            key = f"{key_prefix}_medium_{i}"
            value = generate_compressible_text(5120)  # 5KB
            keys_and_values.append((key, value))
            batch.set(key, value)

        # Large values
        for i in range(10):
            key = f"{key_prefix}_large_{i}"
            value = generate_compressible_text(102400)  # 100KB
            keys_and_values.append((key, value))
            batch.set(key, value)

        # Execute batch
        if isinstance(compression_client, GlideClient):
            results = cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert all(r == OK for r in results)

        # Verify statistics: 10 small values skipped, 20 medium+large compressed
        stats = compression_client.get_statistics()
        skipped_count = stats["compression_skipped_count"] - initial_skipped
        compressed_count = stats["total_values_compressed"] - initial_compressed

        assert (
            skipped_count == 10
        ), f"10 small values should be skipped, got {skipped_count}"
        assert (
            compressed_count == 20
        ), f"20 medium+large values should be compressed, got {compressed_count}"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Mixed batch: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Verify all values
        for key, expected_value in keys_and_values:
            retrieved = compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        keys_to_delete: list[str | bytes] = [k for k, _ in keys_and_values]
        compression_client.delete(keys_to_delete)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_batch_large_payload(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test batch operations with large total payload."""
        num_keys = 1000
        value_size = 10240  # 10KB each, ~10MB total
        key_prefix = f"large_batch_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Create batch
        batch = (
            Batch(is_atomic=False)
            if isinstance(compression_client, GlideClient)
            else ClusterBatch(is_atomic=False)
        )

        keys: list[str | bytes] = []
        value = generate_compressible_text(value_size)
        for i in range(num_keys):
            key = f"{key_prefix}_{i}"
            keys.append(key)
            batch.set(key, value)

        # Execute batch
        if isinstance(compression_client, GlideClient):
            results = cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert len(results) == num_keys
        assert all(r == OK for r in results)

        # Verify compression was applied to all values
        stats = compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"] - initial_compressed
        assert (
            compressed_count == num_keys
        ), f"All {num_keys} values should be compressed, got {compressed_count}"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Large batch: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Verify a sample of values
        for i in range(0, num_keys, 100):
            retrieved = compression_client.get(keys[i])
            assert retrieved == value.encode()

        # Cleanup
        compression_client.delete(keys)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_batch_mset_mget(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test MSET/MGET compression in batch operations."""
        num_keys = 50
        key_prefix = f"batch_mset_mget_{get_random_string(8)}"
        key_value_map = {}

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Create key-value pairs
        for i in range(num_keys):
            key = f"{key_prefix}_{i}"
            value = generate_compressible_text(2048)  # 2KB each
            key_value_map[key] = value

        # Create batch with MSET
        batch = (
            Batch(is_atomic=False)
            if isinstance(compression_client, GlideClient)
            else ClusterBatch(is_atomic=False)
        )

        # Add MSET to batch
        batch.mset(cast(dict[Union[str, bytes], Union[str, bytes]], key_value_map))

        # Execute batch
        if isinstance(compression_client, GlideClient):
            results = cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert results[0] == OK  # MSET should return OK

        # Verify compression was applied to all values
        stats = compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"] - initial_compressed
        assert (
            compressed_count >= num_keys
        ), f"MSET should compress all {num_keys} values, got {compressed_count}"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Batch MSET: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Use MGET to retrieve all values and verify decompression
        keys = list(key_value_map.keys())
        retrieved_values = compression_client.mget(cast(List[Union[str, bytes]], keys))

        # Verify all values are properly decompressed
        for i, (key, expected_value) in enumerate(key_value_map.items()):
            retrieved = retrieved_values[i]
            assert isinstance(
                retrieved, bytes
            ), f"MGET should return bytes for key {key}"
            assert (
                retrieved.decode() == expected_value
            ), f"MGET should return decompressed value for key {key}"

        # Cleanup
        compression_client.delete(cast(List[Union[str, bytes]], keys))


class TestCompressionEdgeCases:
    """Test compression edge cases and error handling."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_empty_values(self, compression_client: TGlideClient):
        """Test compression with empty values."""
        key = f"empty_test_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_skipped = initial_stats["compression_skipped_count"]

        # Test empty string
        assert compression_client.set(key, "") == OK
        assert compression_client.get(key) == b""

        # Verify compression was skipped for empty value
        stats = compression_client.get_statistics()
        assert (
            stats["compression_skipped_count"] > initial_skipped
        ), "Empty value should be skipped"
        assert (
            stats["total_values_compressed"] == initial_compressed
        ), "Empty value should not be compressed"

        # Cleanup
        compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_very_large_values(self, compression_client: TGlideClient):
        """Test compression with very large values."""
        key = f"very_large_{get_random_string(8)}"
        # Use 10MB instead of 512MB for faster testing
        size = 10 * 1024 * 1024  # 10MB
        value = generate_compressible_text(size)

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set large value
        assert compression_client.set(key, value) == OK

        # Get and verify
        retrieved = compression_client.get(key)
        assert retrieved == value.encode()

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Compression should be applied for 10MB value"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Large value: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Cleanup
        compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_backend_mismatch(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """Verify data written with one backend can be read with another."""

        key = f"backend_test_{get_random_string(8)}"
        value = generate_compressible_text(10240)  # 10KB

        # Write with ZSTD
        zstd_client = create_sync_client(
            request,
            cluster_mode,
            protocol=protocol,
            enable_compression=True,
        )

        try:
            zstd_client.set(key, value)

            # Read with LZ4 - data should still be readable
            # (compression is transparent to the application)
            lz4_config = create_sync_client_config(
                request,
                cluster_mode,
                protocol=protocol,
                enable_compression=False,  # We'll set it manually
            )

            # Set custom compression configuration with LZ4 backend
            lz4_config.compression = CompressionConfiguration(
                enabled=True,
                backend=CompressionBackend.LZ4,
                compression_level=0,
                min_compression_size=64,
            )

            lz4_client: GlideClient | GlideClusterClient
            if cluster_mode:
                lz4_client = GlideClusterClient.create(lz4_config)
            else:
                lz4_client = GlideClient.create(lz4_config)

            try:
                retrieved = lz4_client.get(key)
                assert retrieved == value.encode()
            finally:
                lz4_client.close()

            # Cleanup
            zstd_client.delete([key])
        finally:
            zstd_client.close()


class TestCompressionCluster:
    """Test compression in cluster mode."""

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_cluster_multislot(
        self, compression_client: GlideClusterClient
    ):
        """Test compression with keys in different slots."""
        num_keys = 100
        keys_and_values = []

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Create keys that will hash to different slots
        for i in range(num_keys):
            key = f"multislot_{i}_{get_random_string(8)}"
            value = generate_compressible_text(5120)  # 5KB
            keys_and_values.append((key, value))

            # Set value
            assert compression_client.set(key, value) == OK

        # Verify compression was applied to all values across all slots
        stats = compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"] - initial_compressed
        assert (
            compressed_count == num_keys
        ), f"All {num_keys} values should be compressed across slots, got {compressed_count}"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"Cluster multislot: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Verify all values
        for key, expected_value in keys_and_values:
            retrieved = compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        keys_to_delete: list[str | bytes] = [k for k, _ in keys_and_values]
        compression_client.delete(keys_to_delete)


class TestCompressionBackendLevels:
    """Test compression backend level validation."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "backend,level",
        [
            (CompressionBackend.ZSTD, 1),
            (CompressionBackend.ZSTD, 3),
            (CompressionBackend.ZSTD, 10),
            (CompressionBackend.ZSTD, 22),
            (CompressionBackend.ZSTD, -5),
            (CompressionBackend.LZ4, -128),
            (CompressionBackend.LZ4, -10),
            (CompressionBackend.LZ4, 0),
            (CompressionBackend.LZ4, 1),
            (CompressionBackend.LZ4, 6),
            (CompressionBackend.LZ4, 12),
        ],
    )
    def test_compression_valid_levels(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
        backend: CompressionBackend,
        level: int,
    ):
        """Test that valid compression levels work correctly for each backend."""
        # Create client with specific backend and level
        config = create_sync_client_config(
            request,
            cluster_mode,
            protocol=protocol,
            enable_compression=False,  # We'll set it manually
        )

        # Set custom compression configuration
        config.compression = CompressionConfiguration(
            enabled=True,
            backend=backend,
            compression_level=level,
            min_compression_size=64,
        )

        # Create client - should not raise an error
        client: GlideClient | GlideClusterClient
        if cluster_mode:
            client = GlideClusterClient.create(config)
        else:
            client = GlideClient.create(config)

        try:
            # Test basic operation with this compression level
            key = f"level_test_{backend.name}_{level}_{get_random_string(8)}"
            value = generate_compressible_text(1024)  # 1KB

            # Get initial statistics
            initial_stats = client.get_statistics()
            initial_compressed = initial_stats["total_values_compressed"]

            # Set and get value
            assert client.set(key, value) == OK
            retrieved = client.get(key)
            assert retrieved == value.encode()

            stats = client.get_statistics()
            assert (
                stats["total_values_compressed"] > initial_compressed
            ), f"Compression should be applied for {backend.name} level {level}"

            # Cleanup
            client.delete([key])
        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "backend,invalid_level",
        [
            (CompressionBackend.ZSTD, 23),  # Above max
            (CompressionBackend.ZSTD, 100),
            (CompressionBackend.ZSTD, -200000),  # Below min
            (CompressionBackend.LZ4, 13),  # Above max
            (CompressionBackend.LZ4, 100),
            (CompressionBackend.LZ4, -129),  # Below min
            (CompressionBackend.LZ4, -1000),
        ],
    )
    def test_compression_invalid_levels(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
        backend: CompressionBackend,
        invalid_level: int,
    ):
        """Test that invalid compression levels are rejected."""

        config = create_sync_client_config(
            request,
            cluster_mode,
            protocol=protocol,
            enable_compression=False,
        )

        # Set custom compression configuration with invalid level
        config.compression = CompressionConfiguration(
            enabled=True,
            backend=backend,
            compression_level=invalid_level,
            min_compression_size=64,
        )

        # Creating client should raise an error due to invalid level
        with pytest.raises(Exception) as exc_info:
            if cluster_mode:
                GlideClusterClient.create(config)
            else:
                GlideClient.create(config)

        # Verify error message mentions compression level
        error_msg = str(exc_info.value).lower()
        assert (
            "compression" in error_msg or "level" in error_msg
        ), f"Error should mention compression level issue: {exc_info.value}"


class TestCompressionCompatibility:
    """Test compression compatibility with other features."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_compression_with_ttl(self, compression_client: TGlideClient):
        """Test compression with expiring keys."""
        key = f"ttl_test_{get_random_string(8)}"
        value = generate_compressible_text(10240)  # 10KB

        # Get initial statistics
        initial_stats = compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set with TTL
        assert compression_client.set(key, value) == OK
        assert compression_client.expire(key, 10) is True

        # Verify value and TTL
        assert compression_client.get(key) == value.encode()
        ttl = compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10

        # Verify compression was applied
        stats = compression_client.get_statistics()
        assert (
            stats["total_values_compressed"] > initial_compressed
        ), "Compression should be applied with TTL"

        # Verify invariant: compressed bytes <= original bytes
        bytes_added_original = stats["total_original_bytes"] - initial_original_bytes
        bytes_added_compressed = (
            stats["total_bytes_compressed"] - initial_bytes_compressed
        )
        assert (
            bytes_added_compressed <= bytes_added_original
        ), f"TTL: Compressed size ({bytes_added_compressed}) should be <= original size ({bytes_added_original})"

        # Cleanup
        compression_client.delete([key])
