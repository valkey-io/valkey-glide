# Remote Cluster Credentials Setup

This guide explains how to securely configure SSH credentials for remote Valkey cluster access.

## Overview

Windows runners connect to Linux runners via SSH to manage Valkey clusters. This requires secure credential management using GitHub secrets and variables.

## Required Credentials

### 1. SSH Key Pair

**Generate SSH key pair:**
```bash
# Generate new key pair for Valkey runner
ssh-keygen -t ed25519 -f ~/.ssh/valkey_runner_key -C "valkey-runner@github-actions"

# Public key (add to Linux runner)
cat ~/.ssh/valkey_runner_key.pub

# Private key (add to GitHub secret)
cat ~/.ssh/valkey_runner_key
```

### 2. GitHub Repository Configuration

#### Secrets (Repository Settings → Secrets and variables → Actions)

**Required Secrets:**
```
VALKEY_RUNNER_SSH_KEY
├── Description: Private SSH key for remote cluster access
├── Value: Contents of ~/.ssh/valkey_runner_key (entire file)
└── Usage: Automatically injected into Windows workflows

AWS_ACCESS_KEY_ID (optional - for EC2 management)
├── Description: AWS access key for EC2 instance management
└── Value: Your AWS access key

AWS_SECRET_ACCESS_KEY (optional - for EC2 management)  
├── Description: AWS secret key for EC2 instance management
└── Value: Your AWS secret access key

AWS_KEY_PAIR_NAME (optional - for EC2 management)
├── Description: EC2 key pair name for instance creation
└── Value: Name of your EC2 key pair
```

#### Variables (Repository Settings → Secrets and variables → Actions)

**Required Variables:**
```
VALKEY_REMOTE_HOST
├── Description: IP address or hostname of Linux runner
├── Value: 192.168.1.100 (example)
└── Usage: Target host for SSH connections
```

## Setup Process

### Step 1: Prepare Linux Runner

**Option A: Manual EC2 Setup**
```bash
# Launch Ubuntu 22.04 instance
# Security group: SSH (22), Valkey (6379-6400), Cluster bus (16379-16400)

# SSH to instance
ssh -i your-key.pem ubuntu@<instance-ip>

# Run setup script
curl -sSL https://raw.githubusercontent.com/valkey-io/valkey-glide/main/utils/setup_linux_runner.sh | bash

# Add public key to authorized_keys
echo "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5... valkey-runner@github-actions" >> ~/.ssh/authorized_keys
```

**Option B: GitHub Workflow**
```bash
# Use setup-linux-runner.yml workflow
gh workflow run setup-linux-runner.yml -f action=start -f instance_type=t3.medium
```

### Step 2: Configure GitHub Repository

**Add SSH private key to secrets:**
```bash
# Copy private key content
cat ~/.ssh/valkey_runner_key | pbcopy

# Go to GitHub repo → Settings → Secrets and variables → Actions
# New repository secret: VALKEY_RUNNER_SSH_KEY
# Paste the private key content (including -----BEGIN/END----- lines)
```

**Add remote host to variables:**
```bash
# Get Linux runner IP
curl -s http://169.254.169.254/latest/meta-data/public-ipv4  # On EC2 instance

# Go to GitHub repo → Settings → Secrets and variables → Actions → Variables
# New repository variable: VALKEY_REMOTE_HOST
# Value: <linux-runner-ip>
```

### Step 3: Test Configuration

**Manual test:**
```bash
# Test SSH connection
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-host> "echo 'Connection successful'"

# Test remote cluster manager
python3 utils/remote_cluster_manager.py --host <remote-host> test
```

**Workflow test:**
```bash
# Trigger Java workflow on Windows
# Check logs for "✅ Remote cluster access configured successfully"
```

## Security Best Practices

### SSH Key Security
- ✅ **Use dedicated key pair** - Don't reuse existing keys
- ✅ **Ed25519 algorithm** - More secure than RSA
- ✅ **No passphrase** - GitHub Actions can't handle interactive prompts
- ✅ **Rotate regularly** - Generate new keys periodically

### GitHub Secrets
- ✅ **Repository secrets only** - Don't use organization secrets for SSH keys
- ✅ **Minimal permissions** - Only workflows that need access
- ✅ **Audit access** - Monitor secret usage in workflow logs

### Network Security
- ✅ **Security groups** - Restrict SSH access to GitHub IP ranges (if possible)
- ✅ **VPC isolation** - Use private subnets with NAT gateway
- ✅ **SSH hardening** - Disable password auth, use key-only

### Instance Security
- ✅ **Regular updates** - Keep Linux runner patched
- ✅ **Minimal services** - Only run necessary services
- ✅ **Monitoring** - Log SSH access and cluster operations

## Credential Flow

```mermaid
sequenceDiagram
    participant GH as GitHub Workflow
    participant W as Windows Runner
    participant L as Linux Runner
    
    GH->>W: Inject VALKEY_RUNNER_SSH_KEY secret
    W->>W: Write SSH key to ~/.ssh/valkey_runner_key
    W->>W: Set permissions (600)
    W->>L: Test SSH connection
    L-->>W: Connection successful
    W->>L: Execute remote_cluster_manager.py
    L->>L: Start Valkey cluster
    L-->>W: Return cluster endpoints
    W->>W: Run Java tests with remote endpoints
```

## Troubleshooting

### SSH Connection Failures

**Check SSH key format:**
```bash
# Key should start/end with these lines
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

**Test SSH manually:**
```bash
# From Windows runner (in workflow)
ssh -vvv -i ~/.ssh/valkey_runner_key ubuntu@<remote-host>
```

**Check security group:**
```bash
# Ensure port 22 is open
aws ec2 describe-security-groups --group-names valkey-runner-sg
```

### Remote Cluster Failures

**Check Valkey installation:**
```bash
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-host> "valkey-server --version"
```

**Check cluster manager:**
```bash
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-host> "cd valkey-glide/utils && python3 cluster_manager.py --help"
```

**Check firewall:**
```bash
ssh -i ~/.ssh/valkey_runner_key ubuntu@<remote-host> "sudo ufw status"
```

### GitHub Secrets Issues

**Verify secret exists:**
- Go to repo Settings → Secrets and variables → Actions
- Confirm VALKEY_RUNNER_SSH_KEY is listed

**Check secret content:**
- Secrets are masked in logs, but you can verify length
- Should be ~400-800 characters for Ed25519 key

**Test in workflow:**
```yaml
- name: Debug SSH key
  run: |
    echo "SSH key length: ${#SSH_PRIVATE_KEY_CONTENT}"
    echo "SSH key starts with: $(echo "$SSH_PRIVATE_KEY_CONTENT" | head -1)"
  env:
    SSH_PRIVATE_KEY_CONTENT: ${{ secrets.VALKEY_RUNNER_SSH_KEY }}
```

## Cost Management

### Instance Lifecycle
```bash
# Start runner for testing
gh workflow run setup-linux-runner.yml -f action=start

# Stop runner to save costs  
gh workflow run setup-linux-runner.yml -f action=stop

# Check current status
gh workflow run setup-linux-runner.yml -f action=status
```

### Shared Usage
- One Linux runner can serve multiple repositories
- Configure same VALKEY_REMOTE_HOST across repos
- Share SSH key pair (but use separate GitHub secrets)

This setup provides secure, cost-effective remote cluster access for Windows testing!
