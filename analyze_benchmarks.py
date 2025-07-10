#!/usr/bin/env python3
import json
import sys
import os
from tabulate import tabulate

def load_benchmark_data(file_path):
    with open(file_path, 'r') as f:
        return json.load(f)

def analyze_data(data):
    # Group data by client type
    results_by_client = {}
    for entry in data:
        client_name = entry['client']
        if client_name not in results_by_client:
            results_by_client[client_name] = []
        results_by_client[client_name].append(entry)
    
    return results_by_client

def calculate_improvement_ratio(glide_jni, glide):
    """Calculate improvement ratio between glide-jni and regular glide."""
    if not glide or not glide_jni:
        return "N/A"
    
    # Improvement ratios
    ratios = {}
    for metric in [
        'tps',
        'set_average_latency', 'set_p50_latency', 'set_p90_latency', 'set_p99_latency',
        'get_existing_average_latency', 'get_existing_p50_latency', 'get_existing_p90_latency', 'get_existing_p99_latency'
    ]:
        if metric in glide and metric in glide_jni and glide[metric] != 0:
            if 'latency' in metric:
                # For latency, lower is better
                ratios[metric] = glide[metric] / glide_jni[metric]
            else:
                # For throughput, higher is better
                ratios[metric] = glide_jni[metric] / glide[metric]
    
    return ratios

def generate_performance_comparison(data):
    """Generate a comparison table between glide-jni and regular glide."""
    glide_jni_results = [r for r in data if r['client'] == 'glide-jni']
    glide_results = [r for r in data if r['client'] == 'glide']
    
    # Match entries by task count and data size
    comparisons = []
    for jni_result in glide_jni_results:
        for glide_result in glide_results:
            if (jni_result['num_of_tasks'] == glide_result['num_of_tasks'] and 
                jni_result['data_size'] == glide_result['data_size']):
                
                comparison = {
                    'tasks': jni_result['num_of_tasks'],
                    'data_size': jni_result['data_size'],
                    'glide-jni': {
                        'tps': jni_result['tps'],
                        'runtime': jni_result['runtime'],
                        'set_average_latency': jni_result['set_average_latency'],
                        'set_p99_latency': jni_result['set_p99_latency'],
                        'get_existing_average_latency': jni_result['get_existing_average_latency'],
                        'get_existing_p99_latency': jni_result['get_existing_p99_latency']
                    },
                    'glide': {
                        'tps': glide_result['tps'],
                        'runtime': glide_result['runtime'],
                        'set_average_latency': glide_result['set_average_latency'],
                        'set_p99_latency': glide_result['set_p99_latency'],
                        'get_existing_average_latency': glide_result['get_existing_average_latency'],
                        'get_existing_p99_latency': glide_result['get_existing_p99_latency']
                    }
                }
                
                # Calculate improvement ratios
                improvement = calculate_improvement_ratio(
                    comparison['glide-jni'], comparison['glide']
                )
                comparison['improvement'] = improvement
                
                comparisons.append(comparison)
    
    return comparisons

def print_comparison_table(comparisons):
    """Print a nicely formatted comparison table."""
    headers = ["Tasks", "Data Size", "Metric", "Glide-JNI", "Glide", "Improvement"]
    rows = []
    
    for comp in comparisons:
        # Row for throughput
        rows.append([
            comp['tasks'],
            comp['data_size'],
            "Throughput (TPS)",
            f"{comp['glide-jni']['tps']:.1f}",
            f"{comp['glide']['tps']:.1f}",
            f"{comp['improvement'].get('tps', 'N/A'):.1f}x"
        ])
        
        # Row for runtime
        rows.append([
            "",
            "",
            "Runtime (s)",
            f"{comp['glide-jni']['runtime']:.3f}",
            f"{comp['glide']['runtime']:.3f}",
            f"{comp['improvement'].get('runtime', 'N/A'):.1f}x"
        ])
        
        # Row for SET average latency
        rows.append([
            "",
            "",
            "SET avg latency (ms)",
            f"{comp['glide-jni']['set_average_latency']:.3f}",
            f"{comp['glide']['set_average_latency']:.3f}",
            f"{comp['improvement'].get('set_average_latency', 'N/A'):.1f}x"
        ])
        
        # Row for SET p99 latency
        rows.append([
            "",
            "",
            "SET p99 latency (ms)",
            f"{comp['glide-jni']['set_p99_latency']:.3f}",
            f"{comp['glide']['set_p99_latency']:.3f}",
            f"{comp['improvement'].get('set_p99_latency', 'N/A'):.1f}x"
        ])
        
        # Row for GET average latency
        rows.append([
            "",
            "",
            "GET avg latency (ms)",
            f"{comp['glide-jni']['get_existing_average_latency']:.3f}",
            f"{comp['glide']['get_existing_average_latency']:.3f}",
            f"{comp['improvement'].get('get_existing_average_latency', 'N/A'):.1f}x"
        ])
        
        # Row for GET p99 latency
        rows.append([
            "",
            "",
            "GET p99 latency (ms)",
            f"{comp['glide-jni']['get_existing_p99_latency']:.3f}",
            f"{comp['glide']['get_existing_p99_latency']:.3f}",
            f"{comp['improvement'].get('get_existing_p99_latency', 'N/A'):.1f}x"
        ])
        
        # Add an empty row for readability
        rows.append(["", "", "", "", "", ""])
    
    print(tabulate(rows, headers=headers, tablefmt="grid"))

def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <benchmark_json_file>")
        sys.exit(1)
    
    file_path = sys.argv[1]
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} not found")
        sys.exit(1)
    
    try:
        data = load_benchmark_data(file_path)
        comparisons = generate_performance_comparison(data)
        
        print("\n=== Valkey GLIDE JNI vs UDS Performance Comparison ===\n")
        print_comparison_table(comparisons)
        print("\n")
        
    except Exception as e:
        print(f"Error processing benchmark data: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()