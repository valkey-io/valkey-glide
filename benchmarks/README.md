# Benchmarks

[`install_and_test.sh`](./install_and_test.sh) is the benchmark script we use to check performance. Run `install_and_test.sh -h` to get the full list of available flags.

## Common Usage Examples

Here are some common ways to use the benchmark script:

### Basic Usage

```bash
# Run all benchmarks with default settings
./install_and_test.sh

# Run only Python benchmarks
./install_and_test.sh -python

# Run both Python and Node.js benchmarks
./install_and_test.sh -python -node
```

### Customizing Benchmarks

```bash
# Run with smaller data sizes (100 bytes and 1000 bytes)
./install_and_test.sh -data 100 1000

# Run with specific concurrency levels
./install_and_test.sh -tasks 10 50 100

# Run with multiple clients
./install_and_test.sh -clients 1 5 10

# Add a prefix to result files
./install_and_test.sh -prefix my-test
```

### Cluster Mode

```bash
# Run against a cluster server
./install_and_test.sh -is-cluster

# Connect to a remote server
./install_and_test.sh -host valkey.example.com -port 6380
```

### TLS Options

```bash
# Connect without TLS
./install_and_test.sh -no-tls
```

### Client Options

```bash
# Run only with the GLIDE client implementation (all languages)
./install_and_test.sh -only-glide

# Run node benchmarks with only the GLIDE client
./install_and_test.sh -node -only-glide
```

### Comprehensive Examples

```bash
# Run node benchmarks with only GLIDE client against a cluster with no TLS
# and automatically set up the server with Docker
./install_and_test.sh -node -only-glide -is-cluster -no-tls -sv

# Run all benchmarks with 50, 200, 1000 byte data sizes and 5, 20 concurrent tasks
./install_and_test.sh -data 50 200 1000 -tasks 5 20 -prefix custom-perf-test
```

## Automatic Server Setup

You can use the `-sv` or `--setup-valkey` flag to automatically start a Valkey server using Docker before running benchmarks:

- For standalone mode: `./install_and_test.sh -sv`
- For cluster mode: `./install_and_test.sh -sv -is-cluster`

This requires Docker to be installed and running on your system.

The results of the benchmark runs will be written into .csv files in the `./results` folder.

## Cleaning Up Benchmark Resources

When you need to clean up all benchmark-related resources (results, temporary files, Docker containers, etc.), you can use the `clean.sh` script located in the utilities directory:

```bash
# Clean up all benchmark resources
./utilities/clean.sh
```

This script will:

- Remove node_modules and compiled files from the utilities directory
- Clean the data directory (may require sudo for permission issues)
- Remove all benchmark results from the results directory
- Clean Node, C#, Python, and Rust benchmark environments
- Stop and remove any Docker containers used for benchmarking

Run this script before starting fresh benchmark runs or when troubleshooting benchmark issues.

If while running benchmarks your valkey-server is killed every time the program runs the 4000 data-size benchmark, it might be because you don't have enough available storage on your machine.
To solve this issue, you have two options -

1. Allocate more storage to your'e machine. for me the case was allocating from 500 gb to 1000 gb.
2. Go to benchmarks/install_and_test.sh and change the "dataSize="100 4000"" to a data-size that your machine can handle. try for example dataSize="100 1000".
