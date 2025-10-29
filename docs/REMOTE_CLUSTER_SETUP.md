# Remote Cluster Setup for Windows Testing

This document describes how to set up external Linux infrastructure for running Valkey clusters while testing on Windows.

## Architecture

```
┌─────────────────┐    SSH/TCP    ┌─────────────────┐
│  Windows Runner │ ──────────────▶│  Linux Runner   │
│                 │               │                 │
│ • Java Tests    │               │ • Valkey Server │
│ • Gradle Build  │               │ • cluster_mgr   │
│ • Remote Calls  │               │ • Self-hosted   │
└─────────────────┘               └─────────────────┘
```

## Cost Analysis

**Self-hosted runners are more cost-effective:**
- **Containers**: Pay for compute during entire workflow (~35-40 mins)
- **Self-hosted**: Pay only for instance uptime, shared across workflows
- **Estimated savings**: 60-80% for frequent testing

## Setup Instructions

### 1. Launch Linux Runner

#### Option A: Manual EC2 Setup
```bash
# Launch t3.medium instance with Ubuntu 22.04
# Security group: SSH (22), Valkey (6379-6400), Cluster bus (16379-16400)

# SSH to instance and run setup
ssh -i your-key.pem ubuntu@<instance-ip>
curl -sSL https://raw.githubusercontent.com/valkey-io/valkey-glide/main/utils/setup_linux_runner.sh -o setup_linux_runner.sh
bash setup_linux_runner.sh
rm setup_linux_runner.sh
```

#### Option B: GitHub Workflow
```bash
# Use the setup-linux-runner.yml workflow
gh workflow run setup-linux-runner.yml -f action=start -f instance_type=t3.medium
```

### 2. Configure GitHub Secrets

Add these secrets to your repository:

```
VALKEY_RUNNER_SSH_KEY: <private-key-content>
AWS_ACCESS_KEY_ID: <aws-access-key>
AWS_SECRET_ACCESS_KEY: <aws-secret-key>
AWS_KEY_PAIR_NAME: <ec2-key-pair-name>
```

Add these variables:

```
VALKEY_REMOTE_HOST: <linux-runner-ip>
```

### 3. Test the Setup

#### Local Test
```bash
# Test remote cluster manager
python3 utils/remote_cluster_manager.py --host <linux-ip> start --cluster-mode -r 1

# Test Java with remote cluster
export VALKEY_REMOTE_HOST=<linux-ip>
cd java && ./gradlew integTest
```

#### CI Test
```bash
# Windows workflow will automatically use remote cluster when VALKEY_REMOTE_HOST is set
# No code changes needed in tests - they connect to remote endpoints transparently
```

## How It Works

### Remote Cluster Manager

The `remote_cluster_manager.py` script:

1. **SSH Connection**: Connects to Linux runner via SSH
2. **Repository Sync**: Ensures valkey-glide repo is up-to-date
3. **Cluster Management**: Executes cluster_manager.py remotely
4. **Endpoint Translation**: Converts localhost addresses to remote IPs
5. **Result Parsing**: Returns connection strings for Java tests

### Gradle Integration

The Gradle build automatically detects remote mode:

```gradle
def remoteHost = System.getenv("VALKEY_REMOTE_HOST")
if (remoteHost != null) {
    // Use remote_cluster_manager.py
    exec {
        commandLine pythonCmd, 'remote_cluster_manager.py', '--host', remoteHost, 'start', '--cluster-mode'
    }
} else {
    // Use local cluster_manager.py
    exec {
        commandLine pythonCmd, 'cluster_manager.py', 'start', '--cluster-mode'
    }
}
```

### Java Test Transparency

Java tests require no changes:
- Gradle provides remote endpoints via system properties
- Tests connect to `<remote-ip>:6379` instead of `localhost:6379`
- All existing test logic works unchanged

## Troubleshooting

### SSH Connection Issues
```bash
# Test SSH connectivity
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-ip> "echo 'SSH works'"

# Check security group allows SSH (port 22)
aws ec2 describe-security-groups --group-names valkey-runner-sg
```

### Cluster Start Failures
```bash
# Check remote Valkey installation
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-ip> "valkey-server --version"

# Check cluster manager
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-ip> "cd valkey-glide/utils && python3 cluster_manager.py --help"

# Manual cluster test
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-ip> "cd valkey-glide/utils && python3 cluster_manager.py start --cluster-mode"
```

### Network Connectivity
```bash
# Test Valkey port access from Windows
telnet <remote-ip> 6379

# Check firewall on Linux runner
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-ip> "sudo ufw status"
```

## Cost Optimization

### Instance Management
```bash
# Start runner when needed
gh workflow run setup-linux-runner.yml -f action=start

# Stop runner to save costs
gh workflow run setup-linux-runner.yml -f action=stop

# Check status
gh workflow run setup-linux-runner.yml -f action=status
```

### Shared Usage
- One Linux runner can serve multiple Windows workflows
- Runner stays alive between test runs
- Automatic cluster cleanup between tests

## Security Considerations

1. **SSH Keys**: Use dedicated key pair for runner access
2. **Security Groups**: Restrict access to necessary ports only
3. **Instance Isolation**: Use dedicated VPC if handling sensitive data
4. **Automatic Shutdown**: Configure auto-shutdown for cost control

## Performance Benefits

- **No WSL overhead**: Native Linux performance for Valkey
- **Better networking**: No WSL networking quirks
- **Faster cluster creation**: Optimized Linux environment
- **Consistent behavior**: Same environment as production Linux tests
