#!/usr/bin/env python3

"""
Example demonstrating the new Redis transaction commands (MULTI, EXEC, DISCARD) in Valkey GLIDE Python client.

This example shows how to use the individual transaction commands alongside the existing batch system.
"""

import asyncio
from glide import GlideClient, GlideClientConfiguration


async def transaction_commands_example():
    """Demonstrate the new MULTI, EXEC, and DISCARD commands."""
    
    # Create a client configuration
    config = GlideClientConfiguration(
        addresses=[{"host": "localhost", "port": 6379}]
    )
    
    # Create a client
    client = GlideClient(config)
    
    try:
        print("=== Basic MULTI/EXEC Transaction ===")
        
        # Start a transaction
        print("Starting transaction with MULTI...")
        result = await client.multi()
        print(f"MULTI result: {result}")  # Should be "OK"
        
        # Queue commands
        print("Queueing commands...")
        result1 = await client.set("key1", "value1")
        print(f"SET key1 result: {result1}")  # Should be "QUEUED"
        
        result2 = await client.set("key2", "value2")
        print(f"SET key2 result: {result2}")  # Should be "QUEUED"
        
        result3 = await client.get("key1")
        print(f"GET key1 result: {result3}")  # Should be "QUEUED"
        
        # Execute the transaction
        print("Executing transaction with EXEC...")
        exec_result = await client.exec()
        print(f"EXEC result: {exec_result}")  # Should be ['OK', 'OK', b'value1']
        
        # Verify the keys were set
        value1 = await client.get("key1")
        value2 = await client.get("key2")
        print(f"Verification - key1: {value1}, key2: {value2}")
        
        print("\n=== DISCARD Transaction ===")
        
        # Start another transaction
        await client.multi()
        print("Started new transaction")
        
        # Queue a command
        await client.set("key3", "this_will_be_discarded")
        print("Queued SET command")
        
        # Discard the transaction
        discard_result = await client.discard()
        print(f"DISCARD result: {discard_result}")
        
        # Verify key3 was not set
        value3 = await client.get("key3")
        print(f"key3 after DISCARD: {value3}")  # Should be None
        
        print("\n=== WATCH/MULTI/EXEC with Success ===")
        
        # Set up a watched key
        await client.set("watched_key", "initial")
        await client.watch(["watched_key"])
        print("Set up watched key")
        
        # Start transaction
        await client.multi()
        await client.set("watched_key", "modified")
        
        # Execute (should succeed since key wasn't modified externally)
        watch_result = await client.exec()
        print(f"WATCH/EXEC result: {watch_result}")  # Should be ['OK']
        
        # Verify the modification
        final_value = await client.get("watched_key")
        print(f"Final watched_key value: {final_value}")
        
        print("\n=== Transaction Error Handling ===")
        
        # Set up a string value
        await client.set("string_key", "not_a_number")
        
        # Start transaction with a command that will fail
        await client.multi()
        await client.set("string_key", "new_value")  # This will succeed
        await client.incr("string_key")  # This will fail (not a number)
        await client.get("string_key")  # This will succeed
        
        # Execute and handle mixed results
        mixed_result = await client.exec()
        print(f"Mixed result transaction: {mixed_result}")
        # Should be ['OK', <RequestError>, b'new_value']
        
        print("\n=== Comparison with Batch System ===")
        
        # Show how the existing batch system works
        from glide_shared.commands.batch import Batch
        
        batch = Batch(is_atomic=True)  # Atomic batch = transaction
        batch.set("batch_key1", "batch_value1")
        batch.set("batch_key2", "batch_value2")
        batch.get("batch_key1")
        
        batch_result = await client.exec(batch, raise_on_error=True)
        print(f"Batch transaction result: {batch_result}")
        
        print("\nBoth approaches achieve the same result!")
        print("- Individual commands: MULTI -> commands -> EXEC")
        print("- Batch system: Batch(is_atomic=True) -> client.exec(batch)")
        
    except Exception as e:
        print(f"Error: {e}")
    
    finally:
        await client.close()


def sync_transaction_example():
    """Demonstrate the sync version of transaction commands."""
    from glide_sync import GlideClient as SyncGlideClient
    from glide_sync import GlideClientConfiguration as SyncGlideClientConfiguration
    
    print("\n=== Sync Transaction Commands ===")
    
    config = SyncGlideClientConfiguration(
        addresses=[{"host": "localhost", "port": 6379}]
    )
    
    client = SyncGlideClient(config)
    
    try:
        # Basic sync transaction
        client.multi()
        client.set("sync_key", "sync_value")
        result = client.exec()
        print(f"Sync transaction result: {result}")
        
        # Verify
        value = client.get("sync_key")
        print(f"Sync key value: {value}")
        
    except Exception as e:
        print(f"Sync error: {e}")
    
    finally:
        client.close()


if __name__ == "__main__":
    print("Valkey GLIDE Python - Transaction Commands Example")
    print("=" * 50)
    
    # Run async example
    asyncio.run(transaction_commands_example())
    
    # Run sync example
    sync_transaction_example()
    
    print("\nExample completed!")