#!/usr/bin/env python3

"""
ACL Commands Example

This example demonstrates how to use Redis ACL (Access Control List) commands
with the Valkey GLIDE Python client. ACL commands allow you to manage users,
permissions, and security settings in Redis/Valkey.

Requirements:
- Redis/Valkey server running (version 6.0+)
- valkey-glide Python package installed

Usage:
    python acl_commands_example.py
"""

import asyncio

from glide import GlideClient
from glide_shared.config import GlideClientConfiguration


async def demo_basic_acl_commands(client):
    """Demo basic ACL commands."""
    # 1. ACL CAT - List all ACL categories
    print("1. ACL CAT - List all ACL categories:")
    categories = await client.acl_cat()
    print(f"   Available categories: {[cat.decode() for cat in categories[:5]]}...")
    print()

    # 2. ACL CAT with specific category - List commands in 'read' category
    print("2. ACL CAT 'read' - List commands in read category:")
    read_commands = await client.acl_cat("read")
    print(f"   Read commands: {[cmd.decode() for cmd in read_commands[:5]]}...")
    print()

    # 3. ACL USERS - List all users
    print("3. ACL USERS - List all users:")
    users = await client.acl_users()
    print(f"   Current users: {[user.decode() for user in users]}")
    print()

    # 4. ACL WHOAMI - Get current user
    print("4. ACL WHOAMI - Get current user:")
    current_user = await client.acl_whoami()
    print(f"   Current user: {current_user.decode()}")
    print()


async def demo_user_management(client):
    """Demo user management commands."""
    # 5. ACL GETUSER - Get user details
    print("5. ACL GETUSER 'default' - Get default user details:")
    user_info = await client.acl_getuser("default")
    if user_info:
        decoded_info = [
            info.decode() if isinstance(info, bytes) else info
            for info in user_info[:4]
        ]
        print(f"   Default user info: {decoded_info}...")
    print()

    # 6. ACL SETUSER and ACL DELUSER - Create and delete a test user
    test_username = "test_user_example"
    print(f"6. ACL SETUSER/DELUSER - Create and delete user '{test_username}':")

    try:
        # Create a test user with read-only permissions
        result = await client.acl_setuser(
            test_username, "on", "nopass", "~*", "+@read"
        )
        print(f"   User created: {result.decode()}")

        # Verify user was created
        users_after = await client.acl_users()
        if test_username.encode() in users_after:
            print(f"   User '{test_username}' successfully created")

        # Delete the test user
        deleted_count = await client.acl_deluser(test_username)
        print(f"   Users deleted: {deleted_count}")

    except Exception as e:
        print(f"   Error managing test user: {e}")
    print()


async def demo_advanced_acl_commands(client):
    """Demo advanced ACL commands."""
    # 7. ACL LIST - List all ACL rules
    print("7. ACL LIST - List all ACL rules:")
    acl_rules = await client.acl_list()
    print(f"   ACL rules: {[rule.decode() for rule in acl_rules[:2]]}...")
    print()

    # 8. ACL GENPASS - Generate a random password
    print("8. ACL GENPASS - Generate random passwords:")
    password1 = await client.acl_genpass()
    password2 = await client.acl_genpass(32)  # 32 bits = 8 hex characters
    print(f"   Generated password (default): {password1.decode()}")
    print(f"   Generated password (32 bits): {password2.decode()}")
    print()

    # 9. ACL DRYRUN - Simulate command execution
    print("9. ACL DRYRUN - Simulate command execution:")
    try:
        dryrun_result = await client.acl_dryrun("default", "GET", "somekey")
        print(f"   Dryrun result for 'GET somekey': {dryrun_result.decode()}")
    except Exception as e:
        print(f"   Dryrun error: {e}")
    print()

    # 10. ACL LOG - Get ACL security events (may be empty)
    print("10. ACL LOG - Get ACL security events:")
    try:
        # Reset log first
        reset_result = await client.acl_log_reset()
        print(f"   Log reset: {reset_result.decode()}")

        # Get log entries (likely empty after reset)
        log_entries = await client.acl_log(5)
        print(f"   Log entries count: {len(log_entries)}")
        if log_entries:
            first_entry = [
                item.decode() if isinstance(item, bytes) else item
                for item in log_entries[0][:4]
            ]
            print(f"   First entry: {first_entry}")
    except Exception as e:
        print(f"   Log error: {e}")
    print()

    # 11. ACL SAVE/LOAD (may fail if ACL file not configured)
    print("11. ACL SAVE/LOAD - Save/Load ACL rules (may fail if not configured):")
    try:
        save_result = await client.acl_save()
        print(f"   ACL save result: {save_result.decode()}")

        load_result = await client.acl_load()
        print(f"   ACL load result: {load_result.decode()}")
    except Exception as e:
        print(f"   Save/Load not available (ACL file not configured): {e}")
    print()


async def acl_commands_example():
    """
    Demonstrates various ACL commands using the async Valkey GLIDE client.
    """
    # Create a client - use GlideClusterClient for cluster mode
    config = GlideClientConfiguration(addresses=[("localhost", 6379)])
    client = await GlideClient.create(config)

    try:
        print("=== ACL Commands Example ===\n")

        # Basic ACL commands
        await demo_basic_acl_commands(client)

        # User management commands
        await demo_user_management(client)

        # Advanced ACL commands
        await demo_advanced_acl_commands(client)

        print("=== ACL Commands Example Complete ===")

    except Exception as e:
        print(f"An error occurred: {e}")
    finally:
        await client.close()


def sync_acl_commands_example():
    """
    Demonstrates ACL commands using the sync Valkey GLIDE client.
    """
    from glide_sync import GlideClient as SyncGlideClient
    from glide_sync import GlideClientConfiguration as SyncGlideClientConfiguration

    # Create a sync client
    config = SyncGlideClientConfiguration(addresses=[("localhost", 6379)])
    client = SyncGlideClient.create(config)

    try:
        print("=== Sync ACL Commands Example ===\n")

        # Basic ACL commands using sync client
        print("1. ACL USERS (sync):")
        users = client.acl_users()
        print(f"   Users: {[user.decode() for user in users]}")

        print("\n2. ACL WHOAMI (sync):")
        current_user = client.acl_whoami()
        print(f"   Current user: {current_user.decode()}")

        print("\n3. ACL CAT (sync):")
        categories = client.acl_cat()
        print(f"   Categories: {[cat.decode() for cat in categories[:3]]}...")

        print("\n=== Sync ACL Commands Example Complete ===")

    except Exception as e:
        print(f"An error occurred: {e}")
    finally:
        client.close()


if __name__ == "__main__":
    print("Choose example type:")
    print("1. Async ACL commands example")
    print("2. Sync ACL commands example")

    choice = input("Enter choice (1 or 2): ").strip()

    if choice == "1":
        asyncio.run(acl_commands_example())
    elif choice == "2":
        sync_acl_commands_example()
    else:
        print("Invalid choice. Running async example by default.")
        asyncio.run(acl_commands_example())
