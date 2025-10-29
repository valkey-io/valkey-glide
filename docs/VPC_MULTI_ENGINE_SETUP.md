# VPC Multi-Engine Setup

This guide explains how to set up a VPC Linux instance with multiple Valkey/Redis engine versions for testing.

## Architecture

```
┌─────────────────┐    VPC/SSH    ┌─────────────────┐
│  Windows Runner │ ──────────────▶│  VPC Linux      │
│                 │               │                 │
│ • Java Tests    │               │ • Multi-Engine  │
│ • Gradle Build  │               │ • valkey-7.2    │
│ • Engine Select │               │ • valkey-8.0    │
└─────────────────┘               │ • valkey-8.1    │
                                  │ • redis-6.2     │
                                  │ • redis-7.0     │
                                  │ • redis-7.2     │
                                  └─────────────────┘
```

## Benefits

- ✅ **Multiple Engine Versions** - Test against all supported Valkey/Redis versions
- ✅ **VPC Performance** - Low latency within same VPC
- ✅ **Port Isolation** - Each engine uses different port ranges
- ✅ **Shared Infrastructure** - One instance serves multiple workflows
- ✅ **Engine Switching** - Dynamic engine selection per test run

## Setup Instructions

### 1. Launch VPC Linux Instance

**EC2 Configuration:**
```bash
# Launch Ubuntu 22.04 instance in your VPC
# Instance type: t3.large or larger (for multiple engine builds)
# Security group: SSH (22), Valkey ports (6379-6879), Cluster bus (16379-16879)
# Subnet: Same VPC as your Windows runners
```

**SSH to instance and run setup:**
```bash
ssh -i your-key.pem ubuntu@<vpc-instance-ip>
curl -sSL https://raw.githubusercontent.com/valkey-io/valkey-glide/main/utils/setup_vpc_instance.sh -o setup_vpc_instance.sh
bash setup_vpc_instance.sh
rm setup_vpc_instance.sh
```

### 2. Configure GitHub Repository

**Required Secrets:**
```
VALKEY_VPC_SSH_KEY: <private-key-content>
```

**Required Variables:**
```
VALKEY_VPC_HOST: <vpc-instance-private-ip>
```

### 3. Engine Configuration

**Available Engines:**
| Engine | Version | Port Range | Binary Prefix |
|--------|---------|------------|---------------|
| valkey-7.2 | 7.2 | 6379-6399 | valkey |
| valkey-8.0 | 8.0 | 6479-6499 | valkey |
| valkey-8.1 | 8.1 | 6579-6599 | valkey |
| redis-6.2 | 6.2 | 6679-6699 | redis |
| redis-7.0 | 7.0 | 6779-6799 | redis |
| redis-7.2 | 7.2 | 6879-6899 | redis |

**Port Allocation:**
- Base port = 6379 + (engine_offset)
- Cluster bus = base_port + 10000
- Each engine gets 20 ports for clusters

## Usage

### Manual Testing

**Setup engines:**
```bash
# SSH to VPC instance
ssh -i ~/.ssh/valkey_vpc_key ubuntu@<vpc-ip>

# Setup all engines (one-time)
python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py setup
```

**Start specific engine cluster:**
```bash
# Start Valkey 8.0 cluster
python3 multi_engine_manager.py --host <vpc-ip> start --engine valkey-8.0 --cluster-mode -r 1

# Start Redis 7.2 cluster  
python3 multi_engine_manager.py --host <vpc-ip> start --engine redis-7.2 --cluster-mode -r 4

# List available engines
python3 multi_engine_manager.py --host <vpc-ip> list

# Get cluster info
python3 multi_engine_manager.py --host <vpc-ip> info --engine valkey-8.0
```

### Workflow Integration

**Java Gradle:**
```bash
# Test against Valkey 8.0
export VALKEY_VPC_HOST=10.0.1.100
./gradlew integTest -Dengine-version=valkey-8.0

# Test against Redis 7.2
export VALKEY_VPC_HOST=10.0.1.100  
./gradlew integTest -Dengine-version=redis-7.2
```

**GitHub Workflow:**
```yaml
# Trigger workflow with specific engine
gh workflow run java.yml -f use-windows-self-hosted=true

# Engine version is automatically detected from matrix.engine.version
# Or can be overridden with -Dengine-version system property
```

### Configuration Priority

The system checks for Valkey instances in this order:

1. **VPC Instance** (`VALKEY_VPC_HOST`) - Multi-engine support
2. **Remote Cluster** (`VALKEY_REMOTE_HOST`) - Single engine  
3. **Local Cluster** - Default behavior

## Engine Management

### Installation Paths
```
/opt/engines/
├── valkey-7.2/     # Valkey 7.2 source + binaries
├── valkey-8.0/     # Valkey 8.0 source + binaries  
├── valkey-8.1/     # Valkey 8.1 source + binaries
├── redis-6.2/      # Redis 6.2 source + binaries
├── redis-7.0/      # Redis 7.0 source + binaries
└── redis-7.2/      # Redis 7.2 source + binaries
```

### Binary Locations
```bash
# Valkey binaries
/opt/engines/valkey-8.0/src/valkey-server
/opt/engines/valkey-8.0/src/valkey-cli

# Redis binaries  
/opt/engines/redis-7.2/src/redis-server
/opt/engines/redis-7.2/src/redis-cli
```

### Engine Updates
```bash
# Update specific engine
cd /opt/engines/valkey-8.0
git pull origin 8.0
make clean && make -j$(nproc) BUILD_TLS=yes

# Update all engines
python3 multi_engine_manager.py setup  # Rebuilds all engines
```

## Troubleshooting

### SSH Connection Issues
```bash
# Test VPC connectivity
ssh -i ~/.ssh/valkey_vpc_key ubuntu@<vpc-private-ip> "echo 'VPC connection works'"

# Check security groups allow SSH from Windows runner subnet
aws ec2 describe-security-groups --group-ids sg-xxxxx
```

### Engine Build Failures
```bash
# Check engine status
python3 multi_engine_manager.py list

# Manually rebuild failed engine
cd /opt/engines/valkey-8.0
make clean && make -j$(nproc) BUILD_TLS=yes
```

### Port Conflicts
```bash
# Check what's running on ports
sudo netstat -tlnp | grep 637

# Stop all clusters
python3 multi_engine_manager.py stop

# Stop specific engine cluster
python3 multi_engine_manager.py stop --engine valkey-8.0
```

### Cluster Start Failures
```bash
# Check engine binary exists
ls -la /opt/engines/valkey-8.0/src/valkey-server

# Test engine manually
/opt/engines/valkey-8.0/src/valkey-server --version

# Check cluster manager
cd /home/ubuntu/valkey-glide/utils
python3 cluster_manager.py --help
```

## Performance Optimization

### Instance Sizing
- **t3.large**: Basic testing (2-3 engines)
- **t3.xlarge**: Full matrix testing (all engines)
- **c5.xlarge**: CPU-intensive workloads

### Build Optimization
```bash
# Parallel builds (adjust for instance size)
make -j$(nproc) BUILD_TLS=yes

# Use ccache for faster rebuilds
sudo apt-get install ccache
export PATH="/usr/lib/ccache:$PATH"
```

### Network Optimization
- Place instance in same AZ as Windows runners
- Use placement groups for consistent performance
- Enable enhanced networking on supported instances

## Cost Management

### Shared Usage
- One VPC instance can serve multiple repositories
- Configure same `VALKEY_VPC_HOST` across projects
- Share SSH key pair (but use separate GitHub secrets)

### Auto-Shutdown
```bash
# Schedule shutdown during off-hours
echo "0 22 * * * sudo shutdown -h now" | crontab -

# Or use AWS Instance Scheduler
```

This setup provides a robust, multi-engine testing environment within your VPC!
