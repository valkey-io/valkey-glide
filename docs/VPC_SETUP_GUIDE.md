# VPC Setup Guide - Windows + Linux Instance Pair

This guide is for your specific VPC setup with Windows instance at `3.88.53.125`.

## Current Configuration

```
VPC: Your AWS VPC
├── Windows Instance (3.88.53.125)
│   ├── Public IP: 3.88.53.125
│   ├── Private IP: <to be determined>
│   └── Role: Java test runner, self-hosted GitHub runner
└── Linux Instance
    ├── Public IP: <optional>
    ├── Private IP: <to be determined>
    └── Role: Multi-engine Valkey/Redis server
```

## Setup Steps

### 1. Setup Linux Instance

**SSH to your Linux instance and run:**
```bash
# Download and run VPC setup script
curl -sSL https://raw.githubusercontent.com/valkey-io/valkey-glide/main/utils/setup_vpc_pair.sh -o setup_vpc_pair.sh
bash setup_vpc_pair.sh
rm setup_vpc_pair.sh
```

This will:
- Install all 6 engine versions (valkey-7.2, valkey-8.0, valkey-8.1, redis-6.2, redis-7.0, redis-7.2)
- Configure port ranges for each engine
- Setup multi-engine manager
- Display the private IP for GitHub configuration

### 2. Test Connectivity

**From your Windows instance (3.88.53.125):**
```bash
# Test SSH connectivity to Linux instance
python3 utils/test_vpc_connectivity.py --linux-host <linux-private-ip> --key-path ~/.ssh/your-key.pem

# Test with port checking
python3 utils/test_vpc_connectivity.py --linux-host <linux-private-ip> --key-path ~/.ssh/your-key.pem --test-ports
```

### 3. Configure GitHub Repository

**Add these to your repository settings:**

**Variables (Settings → Secrets and variables → Actions → Variables):**
```
VALKEY_VPC_HOST=<linux-private-ip>
```

**Secrets (Settings → Secrets and variables → Actions → Secrets):**
```
VALKEY_VPC_SSH_KEY=<private-key-content>
```

### 4. Security Group Configuration

**Ensure your security groups allow:**

**Linux Instance Security Group:**
- SSH (22) from Windows instance private IP
- Valkey ports (6379-6879) from Windows instance private IP
- Cluster bus ports (16379-16879) from Windows instance private IP

**Windows Instance Security Group:**
- Outbound to Linux instance on ports 22, 6379-6879, 16379-16879

## Usage Examples

### Manual Testing

**Start specific engine cluster:**
```bash
# From Windows instance, test Valkey 8.0
export VALKEY_VPC_HOST=<linux-private-ip>
cd /path/to/valkey-glide/java
./gradlew integTest -Dengine-version=valkey-8.0

# Test Redis 7.2
./gradlew integTest -Dengine-version=redis-7.2
```

### GitHub Workflow

**Trigger workflow with VPC instance:**
```bash
# Use self-hosted Windows runner with VPC Linux instance
gh workflow run java.yml -f use-windows-self-hosted=true
```

The workflow will automatically:
1. Detect `VALKEY_VPC_HOST` is configured
2. Use VPC instance instead of remote cluster
3. Select engine version from test matrix
4. Connect via private IP for optimal performance

### Engine Management

**List available engines:**
```bash
ssh ubuntu@<linux-private-ip> "python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py list"
```

**Start specific engine:**
```bash
ssh ubuntu@<linux-private-ip> "python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py start --engine valkey-8.0 --cluster-mode -r 1"
```

**Stop all clusters:**
```bash
ssh ubuntu@<linux-private-ip> "python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py stop"
```

## Port Allocation

| Engine | Base Port | Port Range | Cluster Bus Range |
|--------|-----------|------------|-------------------|
| valkey-7.2 | 6379 | 6379-6399 | 16379-16399 |
| valkey-8.0 | 6479 | 6479-6499 | 16479-16499 |
| valkey-8.1 | 6579 | 6579-6599 | 16579-16599 |
| redis-6.2 | 6679 | 6679-6699 | 16679-16699 |
| redis-7.0 | 6779 | 6779-6799 | 16779-16799 |
| redis-7.2 | 6879 | 6879-6899 | 16879-16899 |

## Troubleshooting

### Connection Issues

**Test basic connectivity:**
```bash
# From Windows instance
ping <linux-private-ip>
telnet <linux-private-ip> 22
```

**Check security groups:**
```bash
# List security groups
aws ec2 describe-security-groups --group-ids sg-xxxxx

# Check if ports are open
nmap -p 6379-6879 <linux-private-ip>
```

### Engine Issues

**Check engine status:**
```bash
ssh ubuntu@<linux-private-ip> "python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py list"
```

**Rebuild specific engine:**
```bash
ssh ubuntu@<linux-private-ip> "cd /opt/engines/valkey-8.0 && git pull && make clean && make -j\$(nproc) BUILD_TLS=yes"
```

### Performance Optimization

**For your VPC setup:**
- Use private IPs for all communication (lower latency)
- Place instances in same AZ if possible
- Use enhanced networking on supported instance types
- Consider placement groups for consistent performance

## Cost Optimization

**Shared Usage:**
- One Linux instance can serve multiple Windows runners
- Configure same `VALKEY_VPC_HOST` across multiple repositories
- Use spot instances for cost savings (if workload allows)

**Auto-Shutdown:**
```bash
# Schedule shutdown during off-hours (on Linux instance)
echo "0 22 * * * sudo shutdown -h now" | crontab -
```

This VPC setup provides optimal performance and cost efficiency for your Valkey GLIDE testing!
