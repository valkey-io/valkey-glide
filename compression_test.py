#!/usr/bin/env python3
"""
Valkey-Glide Compression Performance Test Script

This script tests the compression feature in valkey-glide by:
1. Reading key-value pairs from a CSV file
2. Setting all keys with compression enabled
3. Getting all keys and measuring performance
4. Comparing memory usage with and without compression

COMPRESSION BEHAVIOR:
- SET: Values are compressed before sending to server
- GET: Values are decompressed after receiving from server
- Batch SET (Pipeline): Each SET in the batch uses compression
- Batch GET (Pipeline): Each GET in the batch uses decompression
"""

import asyncio
import csv
import time
import sys
import os
from pathlib import Path
from typing import List, Tuple, Dict, Any

# Add the python path to import glide
sys.path.insert(0, str(Path(__file__).parent / "python" / "glide-async" / "python"))
sys.path.insert(0, str(Path(__file__).parent / "python" / "glide-shared"))
sys.path.insert(0, str(Path(__file__).parent / "python"))

try:
    from glide import (
        GlideClient,
        GlideClientConfiguration,
        NodeAddress,
        CompressionConfiguration,
        CompressionBackend,
        Batch,
        BatchOptions,
    )
except ImportError as e:
    print(f"Error importing glide: {e}")
    print("Please build the Python client first using: python python/dev.py build --client async")
    sys.exit(1)


class CompressionTester:
    def __init__(self, host: str = "localhost", port: int = 6379):
        self.host = host
        self.port = port
        self.results: Dict[str, Any] = {}

    async def create_client(self, compression_enabled: bool = False) -> GlideClient:
        """Create a Glide client with optional compression."""
        config = GlideClientConfiguration(
            addresses=[NodeAddress(host=self.host, port=self.port)],
            use_tls=False,
        )
        
        if compression_enabled:
            # Enable compression with zstd backend
            config.compression = CompressionConfiguration(
                enabled=True,
                backend=CompressionBackend.ZSTD,
                compression_level=3,  # Default zstd level
                min_compression_size=64,  # Only compress values >= 64 bytes
            )
        
        return await GlideClient.create(config)

    def read_csv_file(self, file_path: str) -> List[Tuple[str, str]]:
        """Read key-value pairs from a CSV file."""
        pairs = []
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                reader = csv.reader(f)
                for row in reader:
                    if len(row) >= 2:
                        key, value = row[0], row[1]
                        pairs.append((key, value))
                    elif len(row) == 1 and ',' in row[0]:
                        # Handle case where CSV might not be properly parsed
                        parts = row[0].split(',', 1)
                        if len(parts) == 2:
                            pairs.append((parts[0], parts[1]))
        except FileNotFoundError:
            print(f"Error: File {file_path} not found")
            sys.exit(1)
        except Exception as e:
            print(f"Error reading file {file_path}: {e}")
            sys.exit(1)
        
        return pairs

    async def measure_set_performance(self, client: GlideClient, pairs: List[Tuple[str, str]]) -> Dict[str, float]:
        """Measure SET operation performance."""
        print(f"Setting {len(pairs)} key-value pairs...")
        
        start_time = time.time()
        for key, value in pairs:
            await client.set(key, value)
        end_time = time.time()
        
        total_time = end_time - start_time
        tps = len(pairs) / total_time if total_time > 0 else 0
        
        return {
            "total_time": total_time,
            "tps": tps,
            "operations": len(pairs)
        }

    async def measure_get_performance(self, client: GlideClient, keys: List[str]) -> Dict[str, float]:
        """Measure GET operation performance."""
        print(f"Getting {len(keys)} keys...")
        
        start_time = time.time()
        for key in keys:
            await client.get(key)
        end_time = time.time()
        
        total_time = end_time - start_time
        tps = len(keys) / total_time if total_time > 0 else 0
        
        return {
            "total_time": total_time,
            "tps": tps,
            "operations": len(keys)
        }

    async def measure_batch_set_performance(self, client: GlideClient, pairs: List[Tuple[str, str]], batch_size: int = 100) -> Dict[str, float]:
        """Measure batch SET operation performance using pipelines."""
        print(f"Setting {len(pairs)} key-value pairs using batches of {batch_size}...")
        
        start_time = time.time()
        
        # Process in batches
        for i in range(0, len(pairs), batch_size):
            batch_pairs = pairs[i:i + batch_size]
            batch = Batch(is_atomic=False)  # Pipeline (non-atomic)
            
            # Add SET commands to batch
            for key, value in batch_pairs:
                batch.set(key, value)
            
            # Execute batch
            await client.exec(batch, raise_on_error=True, options=BatchOptions())
        
        end_time = time.time()
        
        total_time = end_time - start_time
        tps = len(pairs) / total_time if total_time > 0 else 0
        
        return {
            "total_time": total_time,
            "tps": tps,
            "operations": len(pairs),
            "batch_size": batch_size
        }

    async def measure_batch_get_performance(self, client: GlideClient, keys: List[str], batch_size: int = 100) -> Dict[str, float]:
        """Measure batch GET operation performance using pipelines."""
        print(f"Getting {len(keys)} keys using batches of {batch_size}...")
        
        start_time = time.time()
        
        # Process in batches
        for i in range(0, len(keys), batch_size):
            batch_keys = keys[i:i + batch_size]
            batch = Batch(is_atomic=False)  # Pipeline (non-atomic)
            
            # Add GET commands to batch
            for key in batch_keys:
                batch.get(key)
            
            # Execute batch
            await client.exec(batch, raise_on_error=True, options=BatchOptions())
        
        end_time = time.time()
        
        total_time = end_time - start_time
        tps = len(keys) / total_time if total_time > 0 else 0
        
        return {
            "total_time": total_time,
            "tps": tps,
            "operations": len(keys),
            "batch_size": batch_size
        }



    async def measure_memory_usage(self, client: GlideClient, pairs: List[Tuple[str, str]]) -> List[Dict[str, Any]]:
        """Measure memory usage for each key."""
        memory_stats = []
        
        print(f"Measuring memory usage for {len(pairs)} keys...")
        
        for key, original_value in pairs:
            try:
                # Get memory usage of the key in Redis using custom command
                redis_memory = await client.custom_command(["MEMORY", "USAGE", key])
                if redis_memory is None:
                    redis_memory = 0
                else:
                    redis_memory = int(redis_memory)
                
                original_size = len(original_value.encode('utf-8'))
                
                compression_ratio = original_size / redis_memory if redis_memory > 0 else 0
                
                memory_stats.append({
                    "key": key,
                    "original_size": original_size,
                    "redis_memory": redis_memory,
                    "compression_ratio": compression_ratio,
                    "space_saved": original_size - redis_memory,
                    "space_saved_percent": ((original_size - redis_memory) / original_size * 100) if original_size > 0 else 0
                })
            except Exception as e:
                print(f"Error measuring memory for key {key}: {e}")
                memory_stats.append({
                    "key": key,
                    "original_size": len(original_value.encode('utf-8')),
                    "redis_memory": None,
                    "compression_ratio": None,
                    "space_saved": None,
                    "space_saved_percent": None,
                    "error": str(e)
                })
        
        return memory_stats

    async def run_test(self, file_path: str, compression_enabled: bool = False, test_batches: bool = True) -> Dict[str, Any]:
        """Run the complete test suite."""
        test_type = "with_compression" if compression_enabled else "without_compression"
        print(f"\n=== Running test {test_type} ===")
        
        # Read data
        pairs = self.read_csv_file(file_path)
        keys = [pair[0] for pair in pairs]
        
        print(f"Loaded {len(pairs)} key-value pairs from {file_path}")
        
        # Create client
        client = await self.create_client(compression_enabled)
        
        try:
            # Clear any existing data
            await client.flushall()
            
            # Measure individual SET performance
            set_stats = await self.measure_set_performance(client, pairs)
            
            # Measure individual GET performance
            get_stats = await self.measure_get_performance(client, keys)
            
            batch_stats = {}
            if test_batches:
                # Clear data for batch tests
                await client.flushall()
                
                # Measure batch SET performance (pipeline)
                batch_set_stats = await self.measure_batch_set_performance(client, pairs, batch_size=100)
                
                # Measure batch GET performance (pipeline)
                batch_get_stats = await self.measure_batch_get_performance(client, keys, batch_size=100)
                
                batch_stats = {
                    "batch_set_performance": batch_set_stats,
                    "batch_get_performance": batch_get_stats,
                }
            
            # Ensure data is set for memory measurement (use individual sets for consistency)
            await client.flushall()
            for key, value in pairs:
                await client.set(key, value)
            
            # Measure memory usage
            memory_stats = await self.measure_memory_usage(client, pairs)
            
            # Calculate summary statistics
            total_original_size = sum(stat["original_size"] for stat in memory_stats)
            total_redis_memory = sum(stat["redis_memory"] for stat in memory_stats if stat["redis_memory"] is not None)
            valid_ratios = [stat["compression_ratio"] for stat in memory_stats if stat["compression_ratio"] is not None and stat["compression_ratio"] > 0]
            avg_compression_ratio = sum(valid_ratios) / len(valid_ratios) if valid_ratios else 0
            
            results = {
                "test_type": test_type,
                "compression_enabled": compression_enabled,
                "data_file": file_path,
                "total_pairs": len(pairs),
                "set_performance": set_stats,
                "get_performance": get_stats,
                "memory_summary": {
                    "total_original_size": total_original_size,
                    "total_redis_memory": total_redis_memory,
                    "overall_compression_ratio": total_original_size / total_redis_memory if total_redis_memory > 0 else 0,
                    "average_compression_ratio": avg_compression_ratio,
                    "total_space_saved": total_original_size - total_redis_memory,
                    "space_saved_percent": ((total_original_size - total_redis_memory) / total_original_size * 100) if total_original_size > 0 else 0
                },
                "per_key_stats": memory_stats
            }
            
            # Add batch stats if available
            if batch_stats:
                results.update(batch_stats)
            
            return results
            
        finally:
            await client.close()

    def print_results(self, results: Dict[str, Any]):
        """Print formatted results."""
        print(f"\n=== Results for {results['test_type']} ===")
        print(f"Data file: {results['data_file']}")
        print(f"Total key-value pairs: {results['total_pairs']}")
        
        # Individual SET performance
        set_perf = results['set_performance']
        print(f"\nIndividual SET Performance:")
        print(f"  Total time: {set_perf['total_time']:.3f} seconds")
        print(f"  TPS: {set_perf['tps']:.2f} operations/second")
        
        # Individual GET performance
        get_perf = results['get_performance']
        print(f"\nIndividual GET Performance:")
        print(f"  Total time: {get_perf['total_time']:.3f} seconds")
        print(f"  TPS: {get_perf['tps']:.2f} operations/second")
        
        # Batch performance (if available)
        if 'batch_set_performance' in results:
            batch_set_perf = results['batch_set_performance']
            print(f"\nBatch SET Performance (Pipeline, batch size {batch_set_perf['batch_size']}):")
            print(f"  Total time: {batch_set_perf['total_time']:.3f} seconds")
            print(f"  TPS: {batch_set_perf['tps']:.2f} operations/second")
            print(f"  Speedup vs individual: {batch_set_perf['tps'] / set_perf['tps']:.2f}x")
        
        if 'batch_get_performance' in results:
            batch_get_perf = results['batch_get_performance']
            print(f"\nBatch GET Performance (Pipeline, batch size {batch_get_perf['batch_size']}):")
            print(f"  Total time: {batch_get_perf['total_time']:.3f} seconds")
            print(f"  TPS: {batch_get_perf['tps']:.2f} operations/second")
            print(f"  Speedup vs individual: {batch_get_perf['tps'] / get_perf['tps']:.2f}x")
        

        
        # Memory usage
        mem_summary = results['memory_summary']
        print(f"\nMemory Usage:")
        print(f"  Total original size: {mem_summary['total_original_size']:,} bytes")
        print(f"  Total Redis memory: {mem_summary['total_redis_memory']:,} bytes")
        print(f"  Overall compression ratio: {mem_summary['overall_compression_ratio']:.2f}x")
        print(f"  Space saved: {mem_summary['total_space_saved']:,} bytes ({mem_summary['space_saved_percent']:.1f}%)")
        
        # Show top 10 keys by compression ratio
        valid_stats = [s for s in results['per_key_stats'] if s['compression_ratio'] is not None]
        if valid_stats:
            top_compressed = sorted(valid_stats, key=lambda x: x['compression_ratio'], reverse=True)[:10]
            print(f"\nTop 10 keys by compression ratio:")
            for i, stat in enumerate(top_compressed, 1):
                print(f"  {i:2d}. {stat['key'][:30]:30s} - {stat['compression_ratio']:.2f}x ({stat['space_saved_percent']:5.1f}% saved)")

    async def run_comparison(self, file_path: str, test_batches: bool = True):
        """Run both compressed and uncompressed tests for comparison."""
        print("Starting compression comparison test...")
        
        # Test without compression
        results_no_compression = await self.run_test(file_path, compression_enabled=False, test_batches=test_batches)
        self.print_results(results_no_compression)
        
        # Test with compression
        results_with_compression = await self.run_test(file_path, compression_enabled=True, test_batches=test_batches)
        self.print_results(results_with_compression)
        
        # Print comparison
        print(f"\n=== Comparison Summary ===")
        
        # Individual operations comparison
        set_improvement = (results_with_compression['set_performance']['tps'] / results_no_compression['set_performance']['tps'] - 1) * 100
        get_improvement = (results_with_compression['get_performance']['tps'] / results_no_compression['get_performance']['tps'] - 1) * 100
        
        print(f"Individual SET TPS: {results_no_compression['set_performance']['tps']:.2f} → {results_with_compression['set_performance']['tps']:.2f} ({set_improvement:+.1f}%)")
        print(f"Individual GET TPS: {results_no_compression['get_performance']['tps']:.2f} → {results_with_compression['get_performance']['tps']:.2f} ({get_improvement:+.1f}%)")
        
        # Batch operations comparison (if available)
        if test_batches and 'batch_set_performance' in results_no_compression:
            batch_set_improvement = (results_with_compression['batch_set_performance']['tps'] / results_no_compression['batch_set_performance']['tps'] - 1) * 100
            batch_get_improvement = (results_with_compression['batch_get_performance']['tps'] / results_no_compression['batch_get_performance']['tps'] - 1) * 100
            
            print(f"Batch SET TPS: {results_no_compression['batch_set_performance']['tps']:.2f} → {results_with_compression['batch_set_performance']['tps']:.2f} ({batch_set_improvement:+.1f}%)")
            print(f"Batch GET TPS: {results_no_compression['batch_get_performance']['tps']:.2f} → {results_with_compression['batch_get_performance']['tps']:.2f} ({batch_get_improvement:+.1f}%)")
        
        # Memory comparison
        mem_no_comp = results_no_compression['memory_summary']['total_redis_memory']
        mem_with_comp = results_with_compression['memory_summary']['total_redis_memory']
        memory_reduction = ((mem_no_comp - mem_with_comp) / mem_no_comp * 100) if mem_no_comp > 0 else 0
        
        print(f"Memory usage: {mem_no_comp:,} → {mem_with_comp:,} bytes ({memory_reduction:.1f}% reduction)")
        
        # Performance vs memory trade-off summary
        print(f"\n=== Performance vs Memory Trade-off ===")
        if test_batches and 'batch_set_performance' in results_with_compression:
            best_write_tps = max(
                results_with_compression['set_performance']['tps'],
                results_with_compression['batch_set_performance']['tps']
            )
            best_read_tps = max(
                results_with_compression['get_performance']['tps'],
                results_with_compression['batch_get_performance']['tps']
            )
            print(f"Best write performance with compression: {best_write_tps:.2f} TPS")
            print(f"Best read performance with compression: {best_read_tps:.2f} TPS")
        print(f"Memory savings with compression: {memory_reduction:.1f}%")

def create_sample_data(file_path: str, num_pairs: int = 100):
    """Create a sample CSV file with test data."""
    print(f"Creating sample data file: {file_path}")
    
    with open(file_path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        for i in range(num_pairs):
            key = f"test_key_{i:04d}"
            # Create values of varying sizes to test compression
            if i % 10 == 0:
                # Large compressible value
                value = "This is a large compressible string that repeats many times. " * 20
            elif i % 5 == 0:
                # Medium JSON-like value
                value = f'{{"id": {i}, "name": "user_{i}", "description": "This is user number {i} with some additional data that might compress well", "timestamp": "2025-01-01T00:00:00Z", "metadata": {{"type": "test", "category": "sample"}}}}'
            else:
                # Small value
                value = f"value_{i}_small_data"
            
            writer.writerow([key, value])
    
    print(f"Created {num_pairs} key-value pairs in {file_path}")


async def main():
    if len(sys.argv) < 2:
        print("Usage: python compression_test.py <csv_file_path> [host] [port] [options]")
        print("       python compression_test.py --create-sample [num_pairs]")
        print("       python compression_test.py --batch-size-test <csv_file_path> [host] [port]")
        print("       python compression_test.py --demo-compression [host] [port]")
        print("")
        print("Examples:")
        print("  python compression_test.py data.csv")
        print("  python compression_test.py data.csv localhost 6379")
        print("  python compression_test.py data.csv localhost 6379 --no-batches")
        print("  python compression_test.py --create-sample 1000")
        print("  python compression_test.py --batch-size-test data.csv")
        print("  python compression_test.py --demo-compression")
        print("")
        print("Options:")
        print("  --no-batches       Skip batch/pipeline testing (individual operations only)")
        print("  --batch-size-test  Test different batch sizes to find optimal performance")
        print("  --demo-compression Demonstrate compression behavior for SET/GET commands")
        sys.exit(1)
    
    if sys.argv[1] == "--create-sample":
        num_pairs = int(sys.argv[2]) if len(sys.argv) > 2 else 100
        sample_file = "sample_data.csv"
        create_sample_data(sample_file, num_pairs)
        print(f"Sample data created. Run: python {sys.argv[0]} {sample_file}")
        return
    
    if sys.argv[1] == "--batch-size-test":
        if len(sys.argv) < 3:
            print("Error: --batch-size-test requires a CSV file path")
            sys.exit(1)
        
        file_path = sys.argv[2]
        host = sys.argv[3] if len(sys.argv) > 3 else "localhost"
        port = int(sys.argv[4]) if len(sys.argv) > 4 else 6379
        
        if not os.path.exists(file_path):
            print(f"Error: File {file_path} does not exist")
            sys.exit(1)
        
        tester = CompressionTester(host, port)
        
        # Test both with and without compression
        await tester.run_batch_size_comparison(file_path, compression_enabled=False)
        await tester.run_batch_size_comparison(file_path, compression_enabled=True)
        return
    
    if sys.argv[1] == "--demo-compression":
        host = sys.argv[2] if len(sys.argv) > 2 else "localhost"
        port = int(sys.argv[3]) if len(sys.argv) > 3 else 6379
        
        tester = CompressionTester(host, port)
        await tester.demonstrate_compression_behavior()
        return
    
    file_path = sys.argv[1]
    host = "localhost"
    port = 6379
    test_batches = True
    
    # Parse remaining arguments
    for i, arg in enumerate(sys.argv[2:], 2):
        if arg == "--no-batches":
            test_batches = False
        elif i == 2:  # host
            host = arg
        elif i == 3:  # port
            port = int(arg)
    
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} does not exist")
        print(f"Create sample data with: python {sys.argv[0]} --create-sample")
        sys.exit(1)
    
    tester = CompressionTester(host, port)
    await tester.run_comparison(file_path, test_batches=test_batches)


if __name__ == "__main__":
    asyncio.run(main())
