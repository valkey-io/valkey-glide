#!/bin/bash
# Setup script for VPC Linux instance with multi-engine Valkey/Redis support

set -e

echo "Setting up VPC Linux instance for multi-engine Valkey/Redis testing..."

# Update system
sudo apt-get update
sudo apt-get install -y build-essential git pkg-config libssl-dev python3 python3-pip curl jq

# Create engines directory
sudo mkdir -p /opt/engines
sudo chown ubuntu:ubuntu /opt/engines

# Install engines
cd /opt/engines

echo "Installing Valkey versions..."

# Valkey 7.2
if [ ! -d "valkey-7.2" ]; then
    git clone https://github.com/valkey-io/valkey.git valkey-7.2
    cd valkey-7.2
    git checkout 7.2
    make -j$(nproc) BUILD_TLS=yes
    cd ..
fi

# Valkey 8.0
if [ ! -d "valkey-8.0" ]; then
    git clone https://github.com/valkey-io/valkey.git valkey-8.0
    cd valkey-8.0
    git checkout 8.0
    make -j$(nproc) BUILD_TLS=yes
    cd ..
fi

# Valkey 8.1
if [ ! -d "valkey-8.1" ]; then
    git clone https://github.com/valkey-io/valkey.git valkey-8.1
    cd valkey-8.1
    git checkout 8.1
    make -j$(nproc) BUILD_TLS=yes
    cd ..
fi

echo "Installing Redis versions..."

# Redis 6.2
if [ ! -d "redis-6.2" ]; then
    git clone https://github.com/redis/redis.git redis-6.2
    cd redis-6.2
    git checkout 6.2
    make -j$(nproc) BUILD_TLS=yes
    cd ..
fi

# Redis 7.0
if [ ! -d "redis-7.0" ]; then
    git clone https://github.com/redis/redis.git redis-7.0
    cd redis-7.0
    git checkout 7.0
    make -j$(nproc) BUILD_TLS=yes
    cd ..
fi

# Redis 7.2
if [ ! -d "redis-7.2" ]; then
    git clone https://github.com/redis/redis.git redis-7.2
    cd redis-7.2
    git checkout 7.2
    make -j$(nproc) BUILD_TLS=yes
    cd ..
fi

# Setup valkey-glide repository
echo "Setting up valkey-glide repository..."
cd /home/ubuntu
if [ ! -d "valkey-glide" ]; then
    git clone https://github.com/valkey-io/valkey-glide.git
fi
cd valkey-glide
git pull origin main

# Install Python dependencies
cd utils
pip3 install psutil || true

# Make scripts executable
chmod +x multi_engine_manager.py
chmod +x cluster_manager.py

# Configure firewall for all engine ports
echo "Configuring firewall..."
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 6379:6879/tcp  # All engine ports (6379-6879)
sudo ufw allow 16379:16879/tcp # All cluster bus ports
sudo ufw --force enable

# Create systemd service for multi-engine manager
echo "Creating multi-engine service..."
sudo tee /etc/systemd/system/valkey-multi-engine.service > /dev/null << EOF
[Unit]
Description=Valkey Multi-Engine Manager
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/valkey-glide/utils
ExecStart=/bin/bash -c 'while true; do sleep 60; done'
Restart=always
RestartSec=10
Environment=PATH=/opt/engines/valkey-8.0/src:/opt/engines/valkey-8.1/src:/opt/engines/valkey-7.2/src:/opt/engines/redis-7.2/src:/opt/engines/redis-7.0/src:/opt/engines/redis-6.2/src:/usr/local/bin:/usr/bin:/bin

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable valkey-multi-engine
sudo systemctl start valkey-multi-engine

# Test installations
echo "Testing engine installations..."
for engine in valkey-7.2 valkey-8.0 valkey-8.1 redis-6.2 redis-7.0 redis-7.2; do
    if [ -f "/opt/engines/$engine/src/redis-server" ] || [ -f "/opt/engines/$engine/src/valkey-server" ]; then
        echo "✅ $engine: installed"
    else
        echo "❌ $engine: failed to build"
    fi
done

# Test multi-engine manager
echo "Testing multi-engine manager..."
python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py list

echo ""
echo "VPC Linux instance setup complete!"
echo ""
echo "Available engines:"
echo "  - valkey-7.2, valkey-8.0, valkey-8.1"
echo "  - redis-6.2, redis-7.0, redis-7.2"
echo ""
echo "Instance IP: $(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)"
echo ""
echo "Usage in workflows:"
echo "  export VALKEY_VPC_HOST=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)"
echo "  ./gradlew integTest -Dengine-version=valkey-8.0"
