# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for multi-database support in both standalone and cluster modes.
"""

import pytest
from glide_shared.exceptions import RequestError
from glide_shared.routes import AllNodes

from tests.async_tests.conftest import create_client
from tests.sync_tests.conftest import create_sync_client
from tests.utils.utils import (
    check_if_server_version_lt,
    get_random_string,
    sync_check_if_server_version_lt,
)


class TestDatabaseIdStandalone:
    """Test database_id functionality in standalone mode."""

    @pytest.mark.asyncio
    async def test_standalone_client_creation_with_database_id(self, request):
        """Test creating standalone client with different database_id values."""
        # Test with database_id = 1
        client = await create_client(request, cluster_mode=False, database_id=1)

        try:
            # Verify we're connected to database 1
            await client.set("test_key", "test_value")

            # Connect to database 0 and verify key doesn't exist there
            client_db0 = await create_client(request, cluster_mode=False, database_id=0)

            try:
                result = await client_db0.get("test_key")
                assert result is None, "Key should not exist in database 0"

                # Verify key exists in database 1
                result = await client.get("test_key")
                assert result == b"test_value"
            finally:
                await client_db0.close()
        finally:
            await client.close()

    @pytest.mark.asyncio
    async def test_standalone_client_database_isolation(self, request):
        """Test that different databases are properly isolated."""
        key = get_random_string(10)
        value1 = "value_db1"
        value2 = "value_db2"

        # Create clients for different databases
        client_db1 = await create_client(request, cluster_mode=False, database_id=1)
        client_db2 = await create_client(request, cluster_mode=False, database_id=2)

        try:
            # Set different values in each database
            await client_db1.set(key, value1)
            await client_db2.set(key, value2)

            # Verify values are isolated
            result1 = await client_db1.get(key)
            result2 = await client_db2.get(key)

            assert result1 == value1.encode()
            assert result2 == value2.encode()
            assert result1 != result2
        finally:
            await client_db1.close()
            await client_db2.close()

    def test_sync_standalone_client_creation_with_database_id(self, request):
        """Test creating sync standalone client with database_id."""
        client = create_sync_client(request, cluster_mode=False, database_id=2)

        try:
            # Verify we're connected to database 2
            client.set("sync_test_key", "sync_test_value")

            # Connect to database 0 and verify key doesn't exist there
            client_db0 = create_sync_client(request, cluster_mode=False, database_id=0)

            try:
                result = client_db0.get("sync_test_key")
                assert result is None, "Key should not exist in database 0"

                # Verify key exists in database 2
                result = client.get("sync_test_key")
                assert result == b"sync_test_value"
            finally:
                client_db0.close()
        finally:
            client.close()


async def check_cluster_multi_db_support(request):
    """Check if the cluster supports multiple databases."""
    temp_client = await create_client(request, cluster_mode=True)
    try:
        if await check_if_server_version_lt(temp_client, "9.0.0"):
            return False, "Multi-DB cluster mode requires Valkey 9.0+"

        # Try to select database 1 to see if cluster supports multiple databases
        try:
            await temp_client.select(1, AllNodes())
            return True, None
        except RequestError as e:
            if "DB index is out of range" in str(e):
                return (
                    False,
                    "Cluster is configured with cluster-databases=1 (only database 0 supported)",
                )
            raise e
    finally:
        await temp_client.close()


def sync_check_cluster_multi_db_support(request):
    """Check if the cluster supports multiple databases (sync version)."""
    temp_client = create_sync_client(request, cluster_mode=True)
    try:
        if sync_check_if_server_version_lt(temp_client, "9.0.0"):
            return False, "Multi-DB cluster mode requires Valkey 9.0+"

        # Try to select database 1 to see if cluster supports multiple databases
        try:
            temp_client.select(1, AllNodes())
            return True, None
        except RequestError as e:
            if "DB index is out of range" in str(e):
                return (
                    False,
                    "Cluster is configured with cluster-databases=1 (only database 0 supported)",
                )
            raise e
    finally:
        temp_client.close()


class TestDatabaseIdCluster:
    """Test database_id functionality in cluster mode."""

    @pytest.mark.asyncio
    async def test_cluster_client_creation_with_database_id(self, request):
        """Test creating cluster client with database_id (requires Valkey 9+ with cluster-databases > 1)."""
        # Check if cluster supports multi-DB
        supports_multi_db, skip_reason = await check_cluster_multi_db_support(request)
        if not supports_multi_db:
            pytest.skip(skip_reason)

        # Test with database_id = 1
        client = await create_client(request, cluster_mode=True, database_id=1)

        try:
            # Verify we're connected to database 1
            await client.set("cluster_test_key", "cluster_test_value")

            # Connect to database 0 and verify key doesn't exist there
            client_db0 = await create_client(request, cluster_mode=True, database_id=0)

            try:
                result = await client_db0.get("cluster_test_key")
                assert result is None, "Key should not exist in database 0"

                # Verify key exists in database 1
                result = await client.get("cluster_test_key")
                assert result == b"cluster_test_value"
            finally:
                await client_db0.close()
        finally:
            await client.close()

    @pytest.mark.asyncio
    async def test_cluster_select_command_routing(self, request):
        """Test that SELECT command routes to all nodes in cluster mode."""
        # Check if cluster supports multi-DB
        supports_multi_db, skip_reason = await check_cluster_multi_db_support(request)
        if not supports_multi_db:
            pytest.skip(skip_reason)

        client = await create_client(request, cluster_mode=True, database_id=0)

        try:
            # Test SELECT command with explicit AllNodes routing
            result = await client.select(1, AllNodes())
            # Result should be a dict with responses from all nodes
            assert isinstance(result, dict)
            for node_result in result.values():
                assert node_result in [
                    b"OK",
                    "OK",
                ]  # Handle both bytes and string responses

            # Test SELECT command without explicit routing (should default to AllNodes)
            result = await client.select(2)
            assert isinstance(result, dict)
            for node_result in result.values():
                assert node_result in [
                    b"OK",
                    "OK",
                ]  # Handle both bytes and string responses
        finally:
            await client.close()

    def test_sync_cluster_client_creation_with_database_id(self, request):
        """Test creating sync cluster client with database_id."""
        # Check if cluster supports multi-DB
        supports_multi_db, skip_reason = sync_check_cluster_multi_db_support(request)
        if not supports_multi_db:
            pytest.skip(skip_reason)

        client = create_sync_client(request, cluster_mode=True, database_id=1)

        try:
            # Verify we're connected to database 1
            client.set("sync_cluster_test_key", "sync_cluster_test_value")

            # Connect to database 0 and verify key doesn't exist there
            client_db0 = create_sync_client(request, cluster_mode=True, database_id=0)

            try:
                result = client_db0.get("sync_cluster_test_key")
                assert result is None, "Key should not exist in database 0"

                # Verify key exists in database 1
                result = client.get("sync_cluster_test_key")
                assert result == b"sync_cluster_test_value"
            finally:
                client_db0.close()
        finally:
            client.close()


class TestDatabaseIdErrorHandling:
    """Test error handling for database_id configuration."""

    @pytest.mark.asyncio
    async def test_invalid_database_id_configuration_error(self, request):
        """Test that invalid database_id values raise configuration errors."""
        # These should raise ValueError during client creation
        with pytest.raises(ValueError, match="database_id must be non-negative"):
            await create_client(request, cluster_mode=False, database_id=-1)

    @pytest.mark.asyncio
    async def test_select_command_error_handling(self, request):
        """Test error handling for SELECT command with invalid database numbers."""
        client = await create_client(request, cluster_mode=False, database_id=0)

        try:
            # Try to select an invalid database number
            with pytest.raises(RequestError, match="DB index is out of range"):
                await client.select(999)
        finally:
            await client.close()


class TestBackwardCompatibility:
    """Test backward compatibility for existing code."""

    @pytest.mark.asyncio
    async def test_existing_cluster_code_works_without_database_id(self, request):
        """Test that existing cluster client code works without database_id."""
        client = await create_client(
            request, cluster_mode=True
        )  # No database_id specified

        try:
            # Should work with default database (0)
            await client.set("backward_compat_key", "backward_compat_value")
            result = await client.get("backward_compat_key")
            assert result == b"backward_compat_value"
        finally:
            await client.close()

    @pytest.mark.asyncio
    async def test_existing_standalone_code_works_without_database_id(self, request):
        """Test that existing standalone client code works without database_id."""
        client = await create_client(
            request, cluster_mode=False
        )  # No database_id specified

        try:
            # Should work with default database (0)
            await client.set("backward_compat_key", "backward_compat_value")
            result = await client.get("backward_compat_key")
            assert result == b"backward_compat_value"
        finally:
            await client.close()

    @pytest.mark.asyncio
    async def test_explicit_database_zero_same_as_default(self, request):
        """Test that explicit database_id=0 behaves the same as default."""
        key = get_random_string(10)
        value = "test_value"

        # Client with explicit database_id=0
        client_explicit = await create_client(
            request, cluster_mode=False, database_id=0
        )

        # Client with default database_id (None)
        client_default = await create_client(request, cluster_mode=False)

        try:
            # Set value with explicit client
            await client_explicit.set(key, value)

            # Get value with default client - should be the same
            result = await client_default.get(key)
            assert result == value.encode()

            # Set value with default client
            await client_default.set(key + "_2", value)

            # Get value with explicit client - should be the same
            result = await client_explicit.get(key + "_2")
            assert result == value.encode()
        finally:
            await client_explicit.close()
            await client_default.close()


class TestBroaderDatabaseIdRanges:
    """Test database_id functionality with broader ranges beyond typical 0-15."""

    @pytest.mark.asyncio
    async def test_standalone_client_with_higher_database_ids(self, request):
        """Test creating standalone client with database IDs beyond typical range."""
        # Test with database_id = 50
        try:
            client = await create_client(request, cluster_mode=False, database_id=50)
            try:
                # Try to set a value - this may fail if server doesn't support DB 50
                await client.set("test_key_50", "test_value_50")
                result = await client.get("test_key_50")
                assert result == b"test_value_50"
            except RequestError as e:
                # Server-side validation should handle out-of-range database IDs
                assert "DB index is out of range" in str(
                    e
                ) or "invalid DB index" in str(e)
            finally:
                await client.close()
        except RequestError as e:
            # Connection-time error for invalid database
            assert "DB index is out of range" in str(e) or "invalid DB index" in str(e)

    @pytest.mark.asyncio
    async def test_standalone_client_with_very_high_database_ids(self, request):
        """Test creating standalone client with very high database IDs."""
        # Test with database_id = 999
        try:
            client = await create_client(request, cluster_mode=False, database_id=999)
            try:
                # Try to set a value - this should fail with server-side validation
                await client.set("test_key_999", "test_value_999")
                # If we get here, the server supports DB 999
                result = await client.get("test_key_999")
                assert result == b"test_value_999"
            except RequestError as e:
                # Expected: server-side validation should handle out-of-range database IDs
                assert "DB index is out of range" in str(
                    e
                ) or "invalid DB index" in str(e)
            finally:
                await client.close()
        except RequestError as e:
            # Connection-time error for invalid database
            assert "DB index is out of range" in str(e) or "invalid DB index" in str(e)

    @pytest.mark.asyncio
    async def test_cluster_client_with_higher_database_ids(self, request):
        """Test creating cluster client with database IDs beyond typical range."""
        # Check if cluster supports multi-DB
        supports_multi_db, skip_reason = await check_cluster_multi_db_support(request)
        if not supports_multi_db:
            pytest.skip(skip_reason)

        # Test with database_id = 100
        try:
            client = await create_client(request, cluster_mode=True, database_id=100)
            try:
                # Try to set a value - this may fail if server doesn't support DB 100
                await client.set("test_key_100", "test_value_100")
                result = await client.get("test_key_100")
                assert result == b"test_value_100"
            except RequestError as e:
                # Server-side validation should handle out-of-range database IDs
                assert "DB index is out of range" in str(
                    e
                ) or "invalid DB index" in str(e)
            finally:
                await client.close()
        except RequestError as e:
            # Connection-time error for invalid database
            assert "DB index is out of range" in str(e) or "invalid DB index" in str(e)


class TestServerSideValidation:
    """Test that server-side validation is properly handled for out-of-range database IDs."""

    @pytest.mark.asyncio
    async def test_server_side_validation_for_out_of_range_database_ids(self, request):
        """Test that server-side validation errors are properly propagated."""
        # Try to connect with a database ID that's likely out of range
        very_high_db_id = 9999

        try:
            client = await create_client(
                request, cluster_mode=False, database_id=very_high_db_id
            )
            try:
                # If connection succeeds, try an operation
                await client.set("test_key", "test_value")
                # If this succeeds, the server supports this database ID
                result = await client.get("test_key")
                assert result == b"test_value"
            except RequestError as e:
                # Expected: server should reject operations on invalid database
                assert "DB index is out of range" in str(
                    e
                ) or "invalid DB index" in str(e)
            finally:
                await client.close()
        except RequestError as e:
            # Expected: server should reject connection to invalid database
            assert "DB index is out of range" in str(e) or "invalid DB index" in str(e)

    @pytest.mark.asyncio
    async def test_select_command_server_side_validation(self, request):
        """Test that SELECT command with out-of-range database IDs is handled by server."""
        client = await create_client(request, cluster_mode=False, database_id=0)

        try:
            # Try to select a database that's likely out of range
            with pytest.raises(RequestError, match="DB index is out of range"):
                await client.select(9999)
        finally:
            await client.close()

    @pytest.mark.asyncio
    async def test_cluster_select_command_server_side_validation(self, request):
        """Test that SELECT command in cluster mode with out-of-range database IDs is handled by server."""
        # Check if cluster supports multi-DB
        supports_multi_db, skip_reason = await check_cluster_multi_db_support(request)
        if not supports_multi_db:
            pytest.skip(skip_reason)

        client = await create_client(request, cluster_mode=True, database_id=0)

        try:
            # Try to select a database that's likely out of range
            with pytest.raises(RequestError, match="DB index is out of range"):
                await client.select(9999, route=AllNodes())
        finally:
            await client.close()
