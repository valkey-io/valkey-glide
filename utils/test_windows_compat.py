#!/usr/bin/env python3
"""
Simple test script to verify Windows compatibility fixes
Run this to check if the basic patches work
"""

import platform
import shutil
import subprocess
import sys
import os

def test_platform_detection():
    """Test if we can detect Windows correctly"""
    print(f"Platform: {platform.system()}")
    print(f"Is Windows: {platform.system() == 'Windows'}")
    return True


def test_shutil_which():
    """Test if shutil.which works for finding executables"""
    # Try to find Python (should work on all platforms)
    python_path = shutil.which("python") or shutil.which("python3")
    print(f"Python found at: {python_path}")

    # Try to find Redis
    redis_commands = ["redis-server", "redis-server.exe", "valkey-server", "valkey-server.exe"]
    for cmd in redis_commands:
        path = shutil.which(cmd)
        if path:
            print(f"Found {cmd} at: {path}")
            return True

    print("Warning: No Redis/Valkey server found in PATH")
    return False


def test_subprocess_flags():
    """Test if we can use Windows process creation flags"""
    if platform.system() == "Windows":
        try:
            # Test if these flags are available
            flags = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
            print(f"Windows process flags available: 0x{flags:X}")

            # Try to run a simple command with these flags
            p = subprocess.Popen(
                ["cmd", "/c", "echo", "test"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                creationflags=flags
            )
            output, err = p.communicate(timeout=1)
            print(f"Test command succeeded with flags")
            return True
        except Exception as e:
            print(f"Error using Windows flags: {e}")
            return False
    else:
        print("Not on Windows, skipping process flags test")
        return True


def test_path_conversion():
    """Test path separator handling"""
    test_path = "utils/clusters/test"

    if platform.system() == "Windows":
        windows_path = test_path.replace('/', '\\')
        print(f"Unix path: {test_path}")
        print(f"Windows path: {windows_path}")
        return True
    else:
        print(f"Path: {test_path}")
        return True


def test_redis_version_detection():
    """Test if we can get Redis version"""
    redis_server = shutil.which("redis-server") or shutil.which("redis-server.exe")

    if not redis_server:
        print("Redis not found, skipping version test")
        return False

    try:
        result = subprocess.run(
            [redis_server, "--version"],
            capture_output=True,
            text=True,
            timeout=5
        )
        print(f"Redis version output: {result.stdout.strip()}")
        return True
    except Exception as e:
        print(f"Error getting Redis version: {e}")
        return False


def main():
    """Run all tests"""
    print("=" * 60)
    print("Windows Compatibility Tests")
    print("=" * 60)

    tests = [
        ("Platform Detection", test_platform_detection),
        ("shutil.which", test_shutil_which),
        ("Subprocess Flags", test_subprocess_flags),
        ("Path Conversion", test_path_conversion),
        ("Redis Version", test_redis_version_detection),
    ]

    results = []
    for name, test_func in tests:
        print(f"\nTesting: {name}")
        print("-" * 40)
        try:
            success = test_func()
            results.append((name, success))
            print(f"Result: {'✓ PASS' if success else '✗ FAIL'}")
        except Exception as e:
            print(f"ERROR: {e}")
            results.append((name, False))

    print("\n" + "=" * 60)
    print("Summary")
    print("=" * 60)
    for name, success in results:
        status = "✓" if success else "✗"
        print(f"  {status} {name}")

    passed = sum(1 for _, s in results if s)
    total = len(results)
    print(f"\nPassed: {passed}/{total}")

    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())