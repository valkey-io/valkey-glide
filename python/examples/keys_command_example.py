#!/usr/bin/env python3

"""
Example demonstrating the Redis KEYS command in Valkey GLIDE Python client.

This example shows how to use the KEYS command for pattern matching and key discovery.
"""

import asyncio
from glide import GlideClient, GlideClientConfiguration


async def keys_command_example():
    """Demonstrate the KEYS command functionality."""
    
    # Create a client configuration
    config = GlideClientConfiguration(
        addresses=[{"host": "localhost", "port": 6379}]
    )
    
    # Create a client
    client = GlideClient(config)
    
    try:
        print("=== Redis KEYS Command Examples ===")
        
        # Clear the database
        await client.flushall()
        print("Database cleared")
        
        # Set up some test data
        test_data = {
            "user:1001": "Alice",
            "user:1002": "Bob", 
            "user:1003": "Charlie",
            "session:abc123": "active",
            "session:def456": "expired",
            "cache:page1": "content1",
            "cache:page2": "content2",
            "temp:file1": "data1",
            "config:timeout": "30",
            "config:retries": "3"
        }
        
        print(f"\nSetting up {len(test_data)} test keys...")
        for key, value in test_data.items():
            await client.set(key, value)
        
        print("\n=== Pattern Matching Examples ===")
        
        # Get all keys
        all_keys = await client.keys("*")
        print(f"All keys ({len(all_keys)}): {[key.decode() for key in all_keys]}")
        
        # Get user keys
        user_keys = await client.keys("user:*")
        print(f"User keys ({len(user_keys)}): {[key.decode() for key in user_keys]}")
        
        # Get session keys
        session_keys = await client.keys("session:*")
        print(f"Session keys ({len(session_keys)}): {[key.decode() for key in session_keys]}")
        
        # Get cache keys
        cache_keys = await client.keys("cache:*")
        print(f"Cache keys ({len(cache_keys)}): {[key.decode() for key in cache_keys]}")
        
        # Get config keys
        config_keys = await client.keys("config:*")
        print(f"Config keys ({len(config_keys)}): {[key.decode() for key in config_keys]}")
        
        print("\n=== Advanced Pattern Matching ===")
        
        # Question mark wildcard (single character)
        await client.set("a", "value_a")
        await client.set("b", "value_b")
        await client.set("c", "value_c")
        await client.set("ab", "value_ab")
        
        single_char_keys = await client.keys("?")
        print(f"Single character keys: {[key.decode() for key in single_char_keys]}")
        
        # Character class matching
        await client.set("key1", "value1")
        await client.set("key2", "value2")
        await client.set("key3", "value3")
        await client.set("keya", "valuea")
        
        numeric_keys = await client.keys("key[123]")
        print(f"Keys ending with 1, 2, or 3: {[key.decode() for key in numeric_keys]}")
        
        # Range matching
        range_keys = await client.keys("key[1-3]")
        print(f"Keys ending with 1-3: {[key.decode() for key in range_keys]}")
        
        print("\n=== Return Type Demonstration ===")
        
        # Show that KEYS returns bytes (redis-py compatibility)
        keys = await client.keys("user:*")
        print(f"Return type: {type(keys)}")
        print(f"First key type: {type(keys[0]) if keys else 'N/A'}")
        print(f"First key value: {keys[0] if keys else 'N/A'}")
        print(f"Decoded first key: {keys[0].decode() if keys else 'N/A'}")
        
        print("\n=== Performance Warning Example ===")
        
        # Create many keys to demonstrate performance impact
        print("Creating 1000 keys for performance demonstration...")
        for i in range(1000):
            await client.set(f"perf_test_{i:04d}", f"value_{i}")
        
        # This would be slow in production with millions of keys
        all_perf_keys = await client.keys("perf_test_*")
        print(f"Found {len(all_perf_keys)} performance test keys")
        
        # Pattern matching is still fast
        subset_keys = await client.keys("perf_test_00*")
        print(f"Keys starting with 'perf_test_00': {len(subset_keys)}")
        
        print("\n=== Edge Cases ===")
        
        # Empty pattern
        empty_result = await client.keys("")
        print(f"Empty pattern result: {empty_result}")
        
        # Non-matching pattern
        no_match = await client.keys("nonexistent_*")
        print(f"Non-matching pattern result: {no_match}")
        
        # Special characters in keys
        await client.set("key*with*stars", "special1")
        await client.set("key?with?question", "special2")
        await client.set("key[with]brackets", "special3")
        
        # Escaping special characters
        escaped_keys = await client.keys("key\\*with\\*stars")
        print(f"Escaped pattern result: {[key.decode() for key in escaped_keys]}")
        
        # All special character keys
        special_keys = await client.keys("key*with*")
        print(f"Special character keys: {[key.decode() for key in special_keys]}")
        
        print("\n=== Comparison with SCAN (Recommended Alternative) ===")
        print("Note: For production use, consider using SCAN instead of KEYS")
        print("SCAN is non-blocking and more suitable for large datasets")
        
        # Example of why SCAN is preferred (conceptual)
        print("KEYS '*' - Blocks server, returns all keys at once")
        print("SCAN 0 - Non-blocking, returns keys in batches")
        
    except Exception as e:
        print(f"Error: {e}")
    
    finally:
        await client.close()


def sync_keys_example():
    """Demonstrate the sync version of KEYS command."""
    from glide_sync import GlideClient as SyncGlideClient
    from glide_sync import GlideClientConfiguration as SyncGlideClientConfiguration
    
    print("\n=== Sync KEYS Command Example ===")
    
    config = SyncGlideClientConfiguration(
        addresses=[{"host": "localhost", "port": 6379}]
    )
    
    client = SyncGlideClient(config)
    
    try:
        # Clear and set up data
        client.flushall()
        client.set("sync_key1", "value1")
        client.set("sync_key2", "value2")
        client.set("other_key", "value3")
        
        # Get keys
        all_keys = client.keys("*")
        sync_keys = client.keys("sync_*")
        
        print(f"All keys: {[key.decode() for key in all_keys]}")
        print(f"Sync keys: {[key.decode() for key in sync_keys]}")
        
    except Exception as e:
        print(f"Sync error: {e}")
    
    finally:
        client.close()


if __name__ == "__main__":
    print("Valkey GLIDE Python - KEYS Command Example")
    print("=" * 50)
    
    # Run async example
    asyncio.run(keys_command_example())
    
    # Run sync example
    sync_keys_example()
    
    print("\n" + "=" * 50)
    print("Example completed!")
    print("\nIMPORTANT: In production, use SCAN instead of KEYS")
    print("KEYS can block the server with large datasets!")