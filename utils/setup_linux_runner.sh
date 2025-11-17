#!/bin/bash
# Setup script for Linux runner with Valkey support

set -e

echo "Setting up Linux runner for Valkey GLIDE tests..."

# Update system
sudo apt-get update
sudo apt-get install -y python3 python3-pip git build-essential pkg-config libssl-dev curl

# Install Valkey
echo "Installing Valkey..."
cd /tmp
if [ ! -d "valkey" ]; then
    git clone https://github.com/valkey-io/valkey.git
fi
cd valkey
git checkout 8.0.1  # Use stable version
make -j$(nproc) BUILD_TLS=yes
sudo make install

# Verify Valkey installation
echo "Verifying Valkey installation..."
valkey-server --version
valkey-cli --version

# Install Python dependencies
echo "Installing Python dependencies..."
pip3 install psutil

# Clone valkey-glide repository
echo "Setting up valkey-glide repository..."
cd /home/ubuntu
if [ ! -d "valkey-glide" ]; then
    git clone https://github.com/valkey-io/valkey-glide.git
fi
cd valkey-glide
git pull origin main

# Install Python requirements for cluster manager
cd utils
pip3 install -r requirements.txt || echo "No requirements.txt found, continuing..."

# Test cluster manager
echo "Testing cluster manager..."
python3 cluster_manager.py --help

# Configure firewall for Valkey ports
echo "Configuring firewall..."
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 6379:6400/tcp  # Valkey ports
sudo ufw allow 16379:16400/tcp # Valkey cluster bus ports
sudo ufw --force enable

# Create systemd service for keeping runner alive
echo "Creating runner service..."
sudo tee /etc/systemd/system/valkey-runner.service > /dev/null << EOF
[Unit]
Description=Valkey Test Runner
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/valkey-glide
ExecStart=/bin/bash -c 'while true; do sleep 60; done'
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable valkey-runner
sudo systemctl start valkey-runner

echo "Linux runner setup complete!"
echo ""
echo "Runner is ready to accept remote cluster management requests."
echo "Use the following environment variables in Windows tests:"
echo "  VALKEY_REMOTE_HOST=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)"
echo ""
echo "Test the setup with:"
echo "  python3 /home/ubuntu/valkey-glide/utils/remote_cluster_manager.py --host localhost start --cluster-mode"
