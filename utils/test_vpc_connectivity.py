#!/usr/bin/env python3
"""
VPC Connectivity Test - Test Windows → Linux VPC connectivity
"""

import argparse
import socket
import subprocess
import sys
import time
from typing import List, Tuple


def test_ssh_connection(host: str, user: str = "ubuntu", key_path: str = None) -> bool:
    """Test SSH connectivity"""
    print(f"Testing SSH connection to {host}...")

    ssh_cmd = ["ssh", "-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=no"]
    if key_path:
        ssh_cmd.extend(["-i", key_path])
    ssh_cmd.extend([f"{user}@{host}", "echo 'SSH connection successful'"])

    try:
        result = subprocess.run(ssh_cmd, capture_output=True, text=True, timeout=15)
        if result.returncode == 0 and "SSH connection successful" in result.stdout:
            print("✅ SSH connection successful")
            return True
        else:
            print(f"❌ SSH connection failed: {result.stderr}")
            return False
    except subprocess.TimeoutExpired:
        print("❌ SSH connection timed out")
        return False
    except Exception as e:
        print(f"❌ SSH connection error: {e}")
        return False


def test_port_connectivity(host: str, ports: List[int]) -> List[Tuple[int, bool]]:
    """Test TCP port connectivity"""
    print(f"Testing port connectivity to {host}...")

    results = []
    for port in ports:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(5)
            result = sock.connect_ex((host, port))
            sock.close()

            success = result == 0
            status = "✅ Open" if success else "❌ Closed"
            print(f"  Port {port}: {status}")
            results.append((port, success))

        except Exception as e:
            print(f"  Port {port}: ❌ Error - {e}")
            results.append((port, False))

    return results


def test_multi_engine_manager(host: str, key_path: str = None) -> bool:
    """Test multi-engine manager functionality"""
    print(f"Testing multi-engine manager on {host}...")

    ssh_cmd = ["ssh", "-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=no"]
    if key_path:
        ssh_cmd.extend(["-i", key_path])
    ssh_cmd.extend(
        [
            f"ubuntu@{host}",
            "cd /home/ubuntu/valkey-glide/utils && python3 multi_engine_manager.py list",
        ]
    )

    try:
        result = subprocess.run(ssh_cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0:
            print("✅ Multi-engine manager working")
            print("Available engines:")
            for line in result.stdout.strip().split("\n"):
                if line.strip():
                    print(f"  {line}")
            return True
        else:
            print(f"❌ Multi-engine manager failed: {result.stderr}")
            return False
    except Exception as e:
        print(f"❌ Multi-engine manager error: {e}")
        return False


def get_instance_info(host: str, key_path: str = None) -> dict:
    """Get instance information"""
    print(f"Getting instance information from {host}...")

    info_cmd = """
    echo "Private IP: $(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)"
    echo "Public IP: $(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)"
    echo "Instance ID: $(curl -s http://169.254.169.254/latest/meta-data/instance-id)"
    echo "AZ: $(curl -s http://169.254.169.254/latest/meta-data/placement/availability-zone)"
    """

    ssh_cmd = ["ssh", "-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=no"]
    if key_path:
        ssh_cmd.extend(["-i", key_path])
    ssh_cmd.extend([f"ubuntu@{host}", info_cmd])

    try:
        result = subprocess.run(ssh_cmd, capture_output=True, text=True, timeout=15)
        if result.returncode == 0:
            info = {}
            for line in result.stdout.strip().split("\n"):
                if ":" in line:
                    key, value = line.split(":", 1)
                    info[key.strip()] = value.strip()
            return info
        else:
            print(f"❌ Failed to get instance info: {result.stderr}")
            return {}
    except Exception as e:
        print(f"❌ Instance info error: {e}")
        return {}


def main():
    parser = argparse.ArgumentParser(
        description="Test VPC connectivity for Valkey GLIDE"
    )
    parser.add_argument(
        "--linux-host", required=True, help="Linux instance IP (private IP recommended)"
    )
    parser.add_argument("--key-path", help="SSH private key path")
    parser.add_argument(
        "--test-ports", action="store_true", help="Test Valkey port ranges"
    )

    args = parser.parse_args()

    print("🔍 VPC Connectivity Test for Valkey GLIDE")
    print("=" * 50)

    # Test SSH connectivity
    ssh_ok = test_ssh_connection(args.linux_host, key_path=args.key_path)
    if not ssh_ok:
        print("\n❌ SSH connectivity failed. Check:")
        print("1. Security group allows SSH (port 22) from Windows instance")
        print("2. SSH key is correct")
        print("3. Linux instance is running")
        return 1

    # Get instance information
    print("\n📋 Instance Information:")
    info = get_instance_info(args.linux_host, key_path=args.key_path)
    for key, value in info.items():
        print(f"  {key}: {value}")

    # Test multi-engine manager
    print("\n🔧 Multi-Engine Manager Test:")
    manager_ok = test_multi_engine_manager(args.linux_host, key_path=args.key_path)

    # Test port connectivity if requested
    if args.test_ports:
        print("\n🔌 Port Connectivity Test:")
        test_ports = [
            6379,  # valkey-7.2
            6479,  # valkey-8.0
            6579,  # valkey-8.1
            6679,  # redis-6.2
            6779,  # redis-7.0
            6879,  # redis-7.2
        ]
        port_results = test_port_connectivity(args.linux_host, test_ports)

        open_ports = [port for port, success in port_results if success]
        if open_ports:
            print(f"✅ {len(open_ports)} ports accessible")
        else:
            print("⚠️ No Valkey ports currently open (clusters not running)")

    # Summary
    print("\n📊 Test Summary:")
    print(f"  SSH Connection: {'✅ Pass' if ssh_ok else '❌ Fail'}")
    print(f"  Multi-Engine Manager: {'✅ Pass' if manager_ok else '❌ Fail'}")

    if ssh_ok and manager_ok:
        print("\n🎉 VPC connectivity test passed!")
        print("\nNext steps:")
        print("1. Configure GitHub variables:")
        print(f"   VALKEY_VPC_HOST={args.linux_host}")
        print("2. Configure GitHub secrets:")
        print("   VALKEY_VPC_SSH_KEY=<private-key-content>")
        print("3. Run Java tests with VPC instance")
        return 0
    else:
        print("\n❌ VPC connectivity test failed!")
        return 1


if __name__ == "__main__":
    sys.exit(main())
