#!/usr/bin/env python3
"""
Start remote servers matching ConnectionTests.java configuration with strace monitoring
Requires VALKEY_REMOTE_HOST environment variable to be set
"""

import sys
import subprocess
import argparse
import os

def get_remote_host():
    """Get remote host from environment variable"""
    remote_host = os.getenv('VALKEY_REMOTE_HOST')
    if not remote_host:
        print("Error: VALKEY_REMOTE_HOST environment variable must be set")
        print("Example: export VALKEY_REMOTE_HOST=your-remote-server.com")
        sys.exit(1)
    return remote_host

def start_standalone_with_strace(remote_host):
    """Start standalone server (1 shard, 0 replicas) with strace monitoring"""
    config_file = "/tmp/connection_test_standalone_config.json"
    
    cmd = [
        "python3", "remote_cluster_manager.py",
        "--host", remote_host,
        "--config-file", config_file,
        "--enable-strace",
        "--shards", "1",
        "--replicas", "0",
        "start"
    ]
    
    print(f"Starting remote standalone server (1 shard, 0 replicas)...")
    result = subprocess.run(cmd)
    return result.returncode == 0, config_file

def start_cluster_with_strace(remote_host):
    """Start cluster (3 shards, 1 replica) with strace monitoring"""
    config_file = "/tmp/connection_test_cluster_config.json"
    
    cmd = [
        "python3", "remote_cluster_manager.py",
        "--host", remote_host,
        "--config-file", config_file,
        "--enable-strace",
        "--cluster-mode",
        "--shards", "3",
        "--replicas", "1",
        "start"
    ]
    
    print(f"Starting remote cluster (3 shards, 1 replica)...")
    result = subprocess.run(cmd)
    return result.returncode == 0, config_file

def start_az_cluster_with_strace(remote_host):
    """Start AZ cluster (3 shards, 4 replicas) with strace monitoring"""
    config_file = "/tmp/connection_test_az_cluster_config.json"
    
    cmd = [
        "python3", "remote_cluster_manager.py",
        "--host", remote_host,
        "--config-file", config_file,
        "--enable-strace",
        "--cluster-mode",
        "--shards", "3",
        "--replicas", "4",
        "start"
    ]
    
    print(f"Starting remote AZ cluster (3 shards, 4 replicas)...")
    result = subprocess.run(cmd)
    return result.returncode == 0, config_file

def run_action(remote_host, config_file, action):
    """Run analyze or stop action"""
    cmd = [
        "python3", "remote_cluster_manager.py",
        "--host", remote_host,
        "--config-file", config_file,
        "--enable-strace",
        action
    ]
    subprocess.run(cmd)

def main():
    parser = argparse.ArgumentParser(description="Start remote servers like ConnectionTests.java with strace monitoring")
    parser.add_argument("mode", choices=["standalone", "cluster", "az-cluster", "all"], 
                       help="Server mode to start")
    parser.add_argument("action", choices=["start", "analyze", "stop"], 
                       help="Action to perform")
    
    args = parser.parse_args()
    
    remote_host = get_remote_host()
    print(f"Using remote host: {remote_host}")
    
    configs = {
        "standalone": "/tmp/connection_test_standalone_config.json",
        "cluster": "/tmp/connection_test_cluster_config.json",
        "az-cluster": "/tmp/connection_test_az_cluster_config.json"
    }
    
    if args.mode == "all":
        selected_configs = configs
    else:
        selected_configs = {args.mode: configs[args.mode]}
    
    if args.action == "start":
        print("=== Starting ConnectionTests.java compatible remote servers with strace monitoring ===")
        
        for mode, config_file in selected_configs.items():
            if mode == "standalone":
                success, config = start_standalone_with_strace(remote_host)
            elif mode == "cluster":
                success, config = start_cluster_with_strace(remote_host)
            elif mode == "az-cluster":
                success, config = start_az_cluster_with_strace(remote_host)
            
            if success:
                print(f"✓ {mode} started - Config: {config}")
            else:
                print(f"✗ Failed to start {mode}")
        
        print(f"\n=== Java Usage ===")
        for mode, config_file in selected_configs.items():
            print(f"// {mode}: TestUtilities.createClusterFromConfig(\"{config_file}\")")
    
    else:
        for mode, config_file in selected_configs.items():
            print(f"=== {args.action.capitalize()} {mode} ===")
            run_action(remote_host, config_file, args.action)

if __name__ == "__main__":
    main()
