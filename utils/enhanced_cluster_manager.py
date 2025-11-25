#!/usr/bin/env python3

import os
import sys
import subprocess
import argparse
import time
import signal
import json
from pathlib import Path

# Import the original cluster manager
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cluster_manager

class EnhancedClusterManager:
    """Enhanced cluster manager with strace signal monitoring and config file output"""
    
    def __init__(self, config_file="/tmp/valkey_cluster_config.json", strace_output_dir="/tmp/valkey_strace_logs"):
        self.config_file = Path(config_file)
        self.strace_output_dir = Path(strace_output_dir)
        self.strace_output_dir.mkdir(exist_ok=True)
        self.strace_processes = []
        
    def start_cluster_with_monitoring(self, **kwargs):
        """Start cluster with optional strace monitoring and write config file"""
        print(f"Starting cluster with enhanced monitoring...")
        
        # Start the cluster using original cluster_manager
        cluster_info = self._start_cluster_original(**kwargs)
        
        # Write configuration file for Java to read
        self._write_config_file(cluster_info, **kwargs)
        
        # Start strace monitoring if requested
        if kwargs.get('enable_strace', False):
            print(f"Strace logs will be saved to: {self.strace_output_dir}")
            self._attach_strace_to_servers()
        
        return cluster_info
    
    def _start_cluster_original(self, **kwargs):
        """Start cluster using original cluster_manager"""
        # Create args namespace for original cluster manager
        args = argparse.Namespace()
        
        # Set required arguments
        args.tls = kwargs.get('tls', False)
        args.cluster_mode = kwargs.get('cluster_mode', False)
        args.shard_count = kwargs.get('shard_count', 3)
        args.replica_count = kwargs.get('replica_count', 1)
        args.prefix = kwargs.get('prefix', 'enhanced-cluster')
        args.load_module = kwargs.get('load_module', [])
        args.host = kwargs.get('host', '127.0.0.1')
        args.port = kwargs.get('port', None)
        args.keep_folder = False
        
        # Call original start_cluster function
        return cluster_manager.start_cluster(args)
    
    def _write_config_file(self, cluster_info, **kwargs):
        """Write cluster configuration to JSON file for Java to read"""
        config = {
            "cluster_type": "cluster" if kwargs.get('cluster_mode', False) else "standalone",
            "tls_enabled": kwargs.get('tls', False),
            "nodes": [],
            "cluster_folder": getattr(cluster_info, 'cluster_folder', ''),
            "strace_enabled": kwargs.get('enable_strace', False),
            "strace_output_dir": str(self.strace_output_dir) if kwargs.get('enable_strace', False) else None,
            "timestamp": int(time.time())
        }
        
        # Extract node addresses from cluster_info
        if hasattr(cluster_info, 'addresses') and cluster_info.addresses:
            for addr in cluster_info.addresses:
                if ':' in addr:
                    host, port = addr.split(':', 1)
                    config["nodes"].append({"host": host, "port": int(port)})
        else:
            # Fallback: try to parse from cluster folder or use defaults
            base_port = 6379
            shard_count = kwargs.get('shard_count', 3)
            replica_count = kwargs.get('replica_count', 1)
            
            for shard in range(shard_count):
                # Master
                config["nodes"].append({
                    "host": "127.0.0.1", 
                    "port": base_port + shard * (replica_count + 1)
                })
                # Replicas
                for replica in range(replica_count):
                    config["nodes"].append({
                        "host": "127.0.0.1", 
                        "port": base_port + shard * (replica_count + 1) + replica + 1
                    })
        
        # Write config file
        with open(self.config_file, 'w') as f:
            json.dump(config, f, indent=2)
        
        print(f"Cluster configuration written to: {self.config_file}")
        print(f"Config: {json.dumps(config, indent=2)}")
    
    def _attach_strace_to_servers(self):
        """Attach strace to all server processes"""
        try:
            # Get all valkey/redis processes
            result = subprocess.run(
                ["pgrep", "-f", "(valkey-server|redis-server)"],
                capture_output=True,
                text=True
            )
            
            if result.returncode == 0:
                pids = result.stdout.strip().split('\n')
                for pid in pids:
                    if pid:
                        self._start_strace_for_pid(pid)
            else:
                print("No valkey/redis server processes found for strace monitoring")
                
        except Exception as e:
            print(f"Error finding server processes: {e}")
    
    def _start_strace_for_pid(self, pid):
        """Start strace for a specific process ID"""
        try:
            timestamp = int(time.time())
            strace_log = self.strace_output_dir / f"strace_signals_pid_{pid}_{timestamp}.log"
            
            # Start strace with signal tracing
            strace_cmd = [
                "strace",
                "-e", "trace=signal",
                "-f",  # Follow forks
                "-o", str(strace_log),
                "-p", pid
            ]
            
            print(f"Starting strace for PID {pid}, output: {strace_log}")
            
            process = subprocess.Popen(
                strace_cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE
            )
            
            self.strace_processes.append({
                'process': process,
                'pid': pid,
                'log_file': strace_log
            })
            
        except Exception as e:
            print(f"Error starting strace for PID {pid}: {e}")
    
    def stop_monitoring(self):
        """Stop all strace processes"""
        if self.strace_processes:
            print("Stopping strace monitoring...")
            for strace_info in self.strace_processes:
                try:
                    process = strace_info['process']
                    if process.poll() is None:  # Process is still running
                        process.terminate()
                        time.sleep(1)
                        if process.poll() is None:
                            process.kill()
                    print(f"Stopped strace for PID {strace_info['pid']}")
                except Exception as e:
                    print(f"Error stopping strace for PID {strace_info['pid']}: {e}")
            
            self.strace_processes.clear()
    
    def analyze_signals(self):
        """Analyze strace logs for SIGTERM and other signals"""
        print("\nAnalyzing signal traces...")
        
        signal_found = False
        for strace_info in self.strace_processes:
            log_file = strace_info['log_file']
            pid = strace_info['pid']
            
            if log_file.exists():
                print(f"\nAnalyzing signals for PID {pid} (log: {log_file}):")
                try:
                    with open(log_file, 'r') as f:
                        content = f.read()
                        
                    # Look for SIGTERM and other signals
                    lines = content.split('\n')
                    for line_num, line in enumerate(lines, 1):
                        if any(sig in line for sig in ['SIGTERM', 'SIGKILL', 'SIGINT', 'SIGUSR1', 'SIGUSR2']):
                            print(f"  Line {line_num}: SIGNAL FOUND: {line.strip()}")
                            signal_found = True
                        elif any(call in line for call in ['kill(', 'tgkill(', 'tkill(']):
                            print(f"  Line {line_num}: KILL SYSCALL: {line.strip()}")
                            signal_found = True
                            
                except Exception as e:
                    print(f"Error reading log file {log_file}: {e}")
            else:
                print(f"Log file not found: {log_file}")
        
        if not signal_found:
            print("No signals found in strace logs.")
        
        # Also check if config file exists and show its contents
        if self.config_file.exists():
            print(f"\nCluster configuration file: {self.config_file}")
            try:
                with open(self.config_file, 'r') as f:
                    config = json.load(f)
                print(json.dumps(config, indent=2))
            except Exception as e:
                print(f"Error reading config file: {e}")

def main():
    parser = argparse.ArgumentParser(description="Enhanced cluster manager with strace monitoring and config file output")
    parser.add_argument("--config-file", default="/tmp/valkey_cluster_config.json", 
                       help="Configuration file for Java to read")
    parser.add_argument("--strace-output", default="/tmp/valkey_strace_logs", 
                       help="Directory for strace output files")
    parser.add_argument("--enable-strace", action="store_true", 
                       help="Enable strace signal monitoring")
    parser.add_argument("--tls", action="store_true", help="Enable TLS")
    parser.add_argument("--cluster-mode", action="store_true", help="Enable cluster mode")
    parser.add_argument("--shard-count", type=int, default=3, help="Number of shards")
    parser.add_argument("--replica-count", type=int, default=1, help="Number of replicas")
    parser.add_argument("--prefix", default="enhanced-cluster", help="Cluster prefix")
    parser.add_argument("--load-module", action="append", default=[], help="Modules to load")
    parser.add_argument("--host", default="127.0.0.1", help="Host address")
    parser.add_argument("action", choices=["start", "stop", "analyze"], help="Action to perform")
    
    args = parser.parse_args()
    
    manager = EnhancedClusterManager(args.config_file, args.strace_output)
    
    if args.action == "start":
        try:
            cluster_info = manager.start_cluster_with_monitoring(
                tls=args.tls,
                cluster_mode=args.cluster_mode,
                shard_count=args.shard_count,
                replica_count=args.replica_count,
                prefix=args.prefix,
                load_module=args.load_module,
                host=args.host,
                enable_strace=args.enable_strace
            )
            
            print(f"\nCluster started successfully!")
            print(f"Configuration file: {args.config_file}")
            if args.enable_strace:
                print(f"Strace monitoring enabled, logs in: {args.strace_output}")
                print(f"To analyze signals: python3 {__file__} --config-file {args.config_file} --strace-output {args.strace_output} analyze")
            
        except KeyboardInterrupt:
            print("\nReceived interrupt, stopping monitoring...")
            manager.stop_monitoring()
        except Exception as e:
            print(f"Error starting cluster: {e}")
            
    elif args.action == "stop":
        manager.stop_monitoring()
        
    elif args.action == "analyze":
        manager.analyze_signals()

if __name__ == "__main__":
    main()
