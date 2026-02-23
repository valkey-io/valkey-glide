# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import base64
import json
import os
import random
from typing import List, cast

import pytest
from glide import GlideClient, GlideClusterClient, TGlideClient
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.commands.core_options import ExpiryGetEx, ExpiryTypeGetEx
from glide_shared.config import (
    CompressionBackend,
    CompressionConfiguration,
    ProtocolVersion,
)
from glide_shared.constants import OK

from tests.async_tests.conftest import create_client
from tests.utils.utils import create_client_config, get_random_string


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
async def compression_client(request, cluster_mode, protocol):
    """Create client with compression enabled using ZSTD backend."""
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        enable_compression=True,
    )
    yield client
    await client.close()


@pytest.fixture
async def no_compression_client(request, cluster_mode, protocol):
    """Create client without compression (default behavior)."""
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        enable_compression=False,
    )
    yield client
    await client.close()


@pytest.mark.anyio
class TestBasicCompression:
    """Test basic compression functionality."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize(
        "data_size", [512, 1024, 10240, 102400, 1048576]
    )  # 512B, 1KB, 10KB, 100KB, 1MB
    async def test_compression_basic_set_get(
        self, compression_client: TGlideClient, data_size: int
    ):
        """Verify basic SET/GET operations work with compression."""
        key = f"test_compression_{data_size}_{get_random_string(8)}"
        value = generate_compressible_text(data_size)

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set value with compression
        assert await compression_client.set(key, value) == OK

        # Get value and verify it matches
        retrieved = await compression_client.get(key)
        assert retrieved == value.encode()

        # Verify compression was applied (all sizes are >= 64B threshold)
        stats = await compression_client.get_statistics()
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
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_mget_decompression(
        self, compression_client: TGlideClient
    ):
        """Verify MGET returns decompressed values for compressed data."""
        # Create test data
        keys_and_values = []
        for i in range(5):
            key = f"mget_test_{i}_{get_random_string(8)}"
            value = generate_compressible_text(
                1024
            )  # 1KB - above compression threshold
            keys_and_values.append((key, value))

        # Set all values (should be compressed)
        for key, value in keys_and_values:
            await compression_client.set(key, value)

        # Use MGET to retrieve all values
        keys = [k for k, _ in keys_and_values]
        retrieved_values = await compression_client.mget(keys)

        # Verify all values are correctly decompressed
        for i, (key, expected_value) in enumerate(keys_and_values):
            assert retrieved_values[i] == expected_value.encode(), (
                f"MGET should return decompressed value for key {key}. "
                f"Expected: {expected_value[:50]}..., Got: {retrieved_values[i][:50] if retrieved_values[i] else None}..."
            )

        # Cleanup
        await compression_client.delete(keys)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_getex_decompression(
        self, compression_client: TGlideClient
    ):
        """Verify GETEX returns decompressed values for compressed data."""
        key = f"getex_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB - above compression threshold

        # Set value (should be compressed)
        await compression_client.set(key, value)

        # Use GETEX to retrieve value with expiration
        retrieved = await compression_client.getex(
            key, ExpiryGetEx(ExpiryTypeGetEx.SEC, 10)
        )
        assert retrieved == value.encode(), (
            f"GETEX should return decompressed value for key {key}. "
            f"Expected: {value[:50]}..., Got: {retrieved[:50] if retrieved else None}..."
        )

        # Verify TTL was set
        ttl = await compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10

        # Cleanup
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_getdel_decompression(
        self, compression_client: TGlideClient
    ):
        """Verify GETDEL returns decompressed values for compressed data."""
        key = f"getdel_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB - above compression threshold

        # Set value (should be compressed)
        await compression_client.set(key, value)

        # Use GETDEL to retrieve and delete value
        retrieved = await compression_client.getdel(key)
        assert retrieved == value.encode(), (
            f"GETDEL should return decompressed value for key {key}. "
            f"Expected: {value[:50]}..., Got: {retrieved[:50] if retrieved else None}..."
        )

        # Verify key was deleted
        assert await compression_client.get(key) is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_custom_command_decompression(
        self, compression_client: TGlideClient
    ):
        """Verify custom commands return decompressed values for compressed data."""
        key = f"custom_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB - above compression threshold

        # Set value using regular SET (should be compressed)
        await compression_client.set(key, value)

        # Use custom command to retrieve value (should decompress automatically)
        # This uses the raw GET command as a custom command
        retrieved = await compression_client.custom_command(["GET", key])
        assert retrieved == value.encode(), (
            f"Custom GET command should return decompressed value for key {key}. "
            f"Expected: {value[:50]}..., Got: {retrieved[:50] if retrieved else None}..."
        )

        # Cleanup
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_custom_mget_decompression(
        self, compression_client: TGlideClient
    ):
        """Verify custom MGET command returns decompressed values for compressed data."""
        # Create test data
        keys_and_values = []
        for i in range(3):
            key = f"custom_mget_test_{i}_{get_random_string(8)}"
            value = generate_compressible_text(
                1024
            )  # 1KB - above compression threshold
            keys_and_values.append((key, value))

        # Set all values using regular SET (should be compressed)
        for key, value in keys_and_values:
            await compression_client.set(key, value)

        # Use custom MGET command to retrieve all values (should decompress automatically)
        keys = [k for k, _ in keys_and_values]
        custom_command_args = ["MGET"] + keys
        retrieved_values = await compression_client.custom_command(custom_command_args)

        # Verify all values are correctly decompressed
        for i, (key, expected_value) in enumerate(keys_and_values):
            assert retrieved_values[i] == expected_value.encode(), (
                f"Custom MGET command should return decompressed value for key {key}. "
                f"Expected: {expected_value[:50]}..., Got: {retrieved_values[i][:50] if retrieved_values[i] else None}..."
            )

        # Cleanup
        await compression_client.delete(keys)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_min_size_threshold(
        self, compression_client: TGlideClient
    ):
        """Verify min_compression_size threshold is respected."""
        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_skipped = initial_stats["compression_skipped_count"]
        initial_compressed = initial_stats["total_values_compressed"]

        # Test values below threshold (should be skipped)
        for size in [32, 48, 63]:
            key = f"below_threshold_{size}_{get_random_string(8)}"
            value = generate_compressible_text(size)

            # Set value
            await compression_client.set(key, value)
            assert await compression_client.get(key) == value.encode()

            # Check statistics: compression should have been skipped
            stats = await compression_client.get_statistics()
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

            await compression_client.delete([key])

        # Test values at/above threshold (should be compressed)
        for size in [64, 128, 256]:
            key = f"above_threshold_{size}_{get_random_string(8)}"
            value = generate_compressible_text(size)

            # Set value
            await compression_client.set(key, value)
            assert await compression_client.get(key) == value.encode()

            # Check statistics: compression should have been applied
            stats = await compression_client.get_statistics()
            compressed_count = stats["total_values_compressed"]

            assert compressed_count > initial_compressed, (
                f"Size {size}: Compression should be applied at/above threshold. "
                f"Compressed: {compressed_count}, Initial: {initial_compressed}"
            )

            # Update baseline for next iteration
            initial_compressed = compressed_count

            await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_mset_compression(self, compression_client: TGlideClient):
        """Verify MSET compresses values above threshold (should fail until implemented)."""
        # Create test data
        keys_and_values = {}
        for i in range(3):
            key = f"mset_test_{i}_{get_random_string(8)}"
            value = generate_compressible_text(
                1024
            )  # 1KB - above compression threshold
            keys_and_values[key] = value

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use MSET to set all values (should be compressed)
        await compression_client.mset(keys_and_values)

        # Check statistics: compression should have been applied
        stats = await compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"]

        assert compressed_count > initial_compressed, (
            f"MSET should compress values above threshold. "
            f"Compressed: {compressed_count}, Initial: {initial_compressed}"
        )

        # Verify values can be retrieved and decompressed
        for key, expected_value in keys_and_values.items():
            retrieved = await compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        await compression_client.delete(list(keys_and_values.keys()))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_setex_compression(
        self, compression_client: TGlideClient
    ):
        """Verify SETEX compresses values above threshold (should fail until implemented)."""
        key = f"setex_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB - above compression threshold

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use SETEX to set value with expiration (should be compressed)
        await compression_client.custom_command(["SETEX", key, "10", value])

        # Check statistics: compression should have been applied
        stats = await compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"]

        assert compressed_count > initial_compressed, (
            f"SETEX should compress values above threshold. "
            f"Compressed: {compressed_count}, Initial: {initial_compressed}"
        )

        # Verify value can be retrieved and decompressed
        retrieved = await compression_client.get(key)
        assert retrieved == value.encode()

        # Verify TTL was set
        ttl = await compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10

        # Cleanup
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_psetex_compression(
        self, compression_client: TGlideClient
    ):
        """Verify PSETEX compresses values above threshold (should fail until implemented)."""
        key = f"psetex_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB - above compression threshold

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use PSETEX to set value with expiration in milliseconds (should be compressed)
        await compression_client.custom_command(["PSETEX", key, "10000", value])

        # Check statistics: compression should have been applied
        stats = await compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"]

        assert compressed_count > initial_compressed, (
            f"PSETEX should compress values above threshold. "
            f"Compressed: {compressed_count}, Initial: {initial_compressed}"
        )

        # Verify value can be retrieved and decompressed
        retrieved = await compression_client.get(key)
        assert retrieved == value.encode()

        # Verify TTL was set (should be around 10 seconds)
        ttl = await compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10

        # Cleanup
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_setnx_compression(
        self, compression_client: TGlideClient
    ):
        """Verify SETNX compresses values above threshold (should fail until implemented)."""
        key = f"setnx_test_{get_random_string(8)}"
        value = generate_compressible_text(1024)  # 1KB - above compression threshold

        # Ensure key doesn't exist
        await compression_client.delete([key])

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Use SETNX to set value only if it doesn't exist (should be compressed)
        result = await compression_client.custom_command(["SETNX", key, value])
        assert result == 1  # Should succeed since key didn't exist

        # Check statistics: compression should have been applied
        stats = await compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"]

        assert compressed_count > initial_compressed, (
            f"SETNX should compress values above threshold. "
            f"Compressed: {compressed_count}, Initial: {initial_compressed}"
        )

        # Verify value can be retrieved and decompressed
        retrieved = await compression_client.get(key)
        assert retrieved == value.encode()

        # Verify SETNX doesn't overwrite existing key
        result2 = await compression_client.custom_command(
            ["SETNX", key, "different_value"]
        )
        assert result2 == 0  # Should fail since key exists

        # Cleanup
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_disabled_by_default(
        self, no_compression_client: TGlideClient
    ):
        """Verify operations work and compression is NOT applied when disabled."""
        sizes = [64, 1024, 10240, 102400]  # 64B, 1KB, 10KB, 100KB

        # Get initial statistics
        initial_stats = await no_compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_skipped = initial_stats["compression_skipped_count"]

        for size in sizes:
            key = f"no_compression_{size}_{get_random_string(8)}"
            value = generate_compressible_text(size)

            # Set and get value
            assert await no_compression_client.set(key, value) == OK
            assert await no_compression_client.get(key) == value.encode()

            # Verify no compression was applied
            stats = await no_compression_client.get_statistics()
            assert (
                stats["total_values_compressed"] == initial_compressed
            ), f"No compression should be applied when disabled. Size: {size}B"
            assert (
                stats["compression_skipped_count"] == initial_skipped
            ), f"Compression should not even be attempted when disabled. Size: {size}B"

            # Cleanup
            await no_compression_client.delete([key])


@pytest.mark.anyio
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
    async def test_compression_string_types(
        self, compression_client: TGlideClient, data_type: str, generator, size: int
    ):
        """Test compression with different string content types."""
        key = f"test_{data_type}_{size}_{get_random_string(8)}"
        value = generator(size)

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set value
        assert await compression_client.set(key, value) == OK

        # Get and verify value
        retrieved = await compression_client.get(key)
        assert retrieved == value.encode()

        # Verify compression was applied (all sizes are >= 64B threshold)
        stats = await compression_client.get_statistics()
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
        await compression_client.delete([key])


@pytest.mark.anyio
class TestCompressionBatch:
    """Test compression in batch/pipeline operations."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_batch_set_get(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test compression in batch operations."""
        num_keys = 100
        key_prefix = f"batch_test_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
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
            results = await cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = await cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert all(r == OK for r in results)

        # Verify compression was applied to all values
        stats = await compression_client.get_statistics()
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
            retrieved = await compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        keys_to_delete: list[str | bytes] = [k for k, _ in keys_and_values]
        await compression_client.delete(keys_to_delete)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_batch_mixed_sizes(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test compression with mixed value sizes in batch."""
        key_prefix = f"mixed_batch_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
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
            results = await cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = await cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert all(r == OK for r in results)

        # Verify statistics: 10 small values skipped, 20 medium+large compressed
        stats = await compression_client.get_statistics()
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
            retrieved = await compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        keys_to_delete: list[str | bytes] = [k for k, _ in keys_and_values]
        await compression_client.delete(keys_to_delete)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_batch_large_payload(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test batch operations with large total payload."""
        num_keys = 1000
        value_size = 10240  # 10KB each, ~10MB total
        key_prefix = f"large_batch_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
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
            results = await cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = await cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert len(results) == num_keys
        assert all(r == OK for r in results)

        # Verify compression was applied to all values
        stats = await compression_client.get_statistics()
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
            retrieved = await compression_client.get(keys[i])
            assert retrieved == value.encode()

        # Cleanup
        await compression_client.delete(keys)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_batch_mset_mget(
        self, compression_client: TGlideClient, cluster_mode: bool
    ):
        """Test MSET/MGET compression in batch operations."""
        num_keys = 50
        key_prefix = f"batch_mset_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]

        # Create batch with MSET
        batch = (
            Batch(is_atomic=False)
            if isinstance(compression_client, GlideClient)
            else ClusterBatch(is_atomic=False)
        )

        keys_and_values = {}
        for i in range(num_keys):
            key = f"{key_prefix}_{i}"
            value = generate_compressible_text(
                1024
            )  # 1KB - above compression threshold
            keys_and_values[key] = value

        # Add MSET to batch
        batch.mset(keys_and_values)

        # Execute batch
        if isinstance(compression_client, GlideClient):
            results = await cast(GlideClient, compression_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
        else:
            results = await cast(GlideClusterClient, compression_client).exec(
                cast(ClusterBatch, batch), raise_on_error=True
            )
        assert results is not None
        assert results[0] == OK

        # Verify compression was applied
        stats = await compression_client.get_statistics()
        compressed_count = stats["total_values_compressed"] - initial_compressed
        assert (
            compressed_count == num_keys
        ), f"All {num_keys} values should be compressed via batch MSET, got {compressed_count}"

        # Use MGET to verify all values
        keys = list(keys_and_values.keys())
        retrieved_values = await compression_client.mget(keys)
        for i, expected_value in enumerate(keys_and_values.values()):
            assert retrieved_values[i] == expected_value.encode()

        # Cleanup
        await compression_client.delete(keys)


@pytest.mark.anyio
class TestCompressionEdgeCases:
    """Test compression edge cases and error handling."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_empty_values(self, compression_client: TGlideClient):
        """Test compression with empty values."""
        key = f"empty_test_{get_random_string(8)}"

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_skipped = initial_stats["compression_skipped_count"]

        # Test empty string
        assert await compression_client.set(key, "") == OK
        assert await compression_client.get(key) == b""

        # Verify compression was skipped for empty value
        stats = await compression_client.get_statistics()
        assert (
            stats["compression_skipped_count"] > initial_skipped
        ), "Empty value should be skipped"
        assert (
            stats["total_values_compressed"] == initial_compressed
        ), "Empty value should not be compressed"

        # Cleanup
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_very_large_values(
        self, compression_client: TGlideClient
    ):
        """Test compression with very large values."""
        key = f"very_large_{get_random_string(8)}"
        # Use 10MB instead of 512MB for faster testing
        size = 10 * 1024 * 1024  # 10MB
        value = generate_compressible_text(size)

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set large value
        assert await compression_client.set(key, value) == OK

        # Get and verify
        retrieved = await compression_client.get(key)
        assert retrieved == value.encode()

        # Verify compression was applied
        stats = await compression_client.get_statistics()
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
        await compression_client.delete([key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_backend_mismatch(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """Verify data written with one backend can be read with another."""

        key = f"backend_test_{get_random_string(8)}"
        value = generate_compressible_text(10240)  # 10KB

        # Write with ZSTD
        zstd_client = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            enable_compression=True,
        )

        try:
            await zstd_client.set(key, value)

            # Read with LZ4 - data should still be readable
            # (compression is transparent to the application)
            lz4_config = create_client_config(
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
                lz4_client = await GlideClusterClient.create(lz4_config)
            else:
                lz4_client = await GlideClient.create(lz4_config)

            try:
                retrieved = await lz4_client.get(key)
                assert retrieved == value.encode()
            finally:
                await lz4_client.close()

            # Cleanup
            await zstd_client.delete([key])
        finally:
            await zstd_client.close()


@pytest.mark.anyio
class TestCompressionCluster:
    """Test compression in cluster mode."""

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_cluster_multislot(
        self, compression_client: GlideClusterClient
    ):
        """Test compression with keys in different slots."""
        num_keys = 100
        keys_and_values = []

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Create keys that will hash to different slots
        for i in range(num_keys):
            key = f"multislot_{i}_{get_random_string(8)}"
            value = generate_compressible_text(5120)  # 5KB
            keys_and_values.append((key, value))

            # Set value
            assert await compression_client.set(key, value) == OK

        # Verify compression was applied to all values across all slots
        stats = await compression_client.get_statistics()
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
            retrieved = await compression_client.get(key)
            assert retrieved == expected_value.encode()

        # Cleanup
        keys_to_delete: list[str | bytes] = [k for k, _ in keys_and_values]
        await compression_client.delete(keys_to_delete)


@pytest.mark.anyio
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
    async def test_compression_valid_levels(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
        backend: CompressionBackend,
        level: int,
    ):
        """Test that valid compression levels work correctly for each backend."""
        # Create client with specific backend and level

        config = create_client_config(
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
            client = await GlideClusterClient.create(config)
        else:
            client = await GlideClient.create(config)

        try:
            # Test basic operation with this compression level
            key = f"level_test_{backend.name}_{level}_{get_random_string(8)}"
            value = generate_compressible_text(1024)  # 1KB

            # Get initial statistics
            initial_stats = await client.get_statistics()
            initial_compressed = initial_stats["total_values_compressed"]

            # Set and get value
            assert await client.set(key, value) == OK
            retrieved = await client.get(key)
            assert retrieved == value.encode()

            # Verify compression was applied
            stats = await client.get_statistics()
            assert (
                stats["total_values_compressed"] > initial_compressed
            ), f"Compression should be applied for {backend.name} level {level}"

            # Cleanup
            await client.delete([key])
        finally:
            await client.close()

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
    async def test_compression_invalid_levels(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
        backend: CompressionBackend,
        invalid_level: int,
    ):
        """Test that invalid compression levels are rejected."""

        config = create_client_config(
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
                await GlideClusterClient.create(config)
            else:
                await GlideClient.create(config)

        # Verify error message mentions compression level
        error_msg = str(exc_info.value).lower()
        assert (
            "compression" in error_msg or "level" in error_msg
        ), f"Error should mention compression level issue: {exc_info.value}"


@pytest.mark.anyio
class TestCompressionCompatibility:
    """Test compression compatibility with other features."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_compression_with_ttl(self, compression_client: TGlideClient):
        """Test compression with expiring keys."""
        key = f"ttl_test_{get_random_string(8)}"
        value = generate_compressible_text(10240)  # 10KB

        # Get initial statistics
        initial_stats = await compression_client.get_statistics()
        initial_compressed = initial_stats["total_values_compressed"]
        initial_original_bytes = initial_stats["total_original_bytes"]
        initial_bytes_compressed = initial_stats["total_bytes_compressed"]

        # Set with TTL
        assert await compression_client.set(key, value) == OK
        assert await compression_client.expire(key, 10) is True

        # Verify value and TTL
        assert await compression_client.get(key) == value.encode()
        ttl = await compression_client.ttl(key)
        assert ttl > 0 and ttl <= 10

        # Verify compression was applied
        stats = await compression_client.get_statistics()
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
        await compression_client.delete([key])
