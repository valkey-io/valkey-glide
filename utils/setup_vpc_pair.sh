#!/bin/bash
# Setup script for VPC Windows + Linux instance pair

set -e

WINDOWS_PUBLIC_IP="3.88.53.125"
LINUX_PRIVATE_IP=""
WINDOWS_PRIVATE_IP=""

echo "Setting up VPC instance pair for Valkey GLIDE testing..."

# Detect if we're on the Linux instance
if [ -f /etc/os-release ] && grep -q "Ubuntu" /etc/os-release; then
    echo "Detected Linux instance - setting up multi-engine server..."
    
    # Get our private IP
    LINUX_PRIVATE_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)
    echo "Linux private IP: $LINUX_PRIVATE_IP"
    
    # Run the standard VPC setup
    curl -sSL https://raw.githubusercontent.com/valkey-io/valkey-glide/main/utils/setup_vpc_instance.sh | bash
    
    # Additional VPC-specific configuration
    echo "Configuring VPC-specific settings..."
    
    # Allow Windows instance access (add to security group if needed)
    echo "Linux instance setup complete!"
    echo ""
    echo "Configuration for GitHub:"
    echo "  VALKEY_VPC_HOST=$LINUX_PRIVATE_IP"
    echo ""
    echo "Test from Windows instance:"
    echo "  ssh ubuntu@$LINUX_PRIVATE_IP 'python3 /home/ubuntu/valkey-glide/utils/multi_engine_manager.py list'"
    
else
    echo "This script should be run on the Linux instance in your VPC."
    echo "For Windows instance setup, use the GitHub workflow configuration."
    echo ""
    echo "Manual setup steps:"
    echo "1. SSH to Linux instance and run this script"
    echo "2. Configure GitHub variables:"
    echo "   - VALKEY_VPC_HOST=<linux-private-ip>"
    echo "3. Configure GitHub secrets:"
    echo "   - VALKEY_VPC_SSH_KEY=<private-key-content>"
    echo "4. Test Windows → Linux connectivity"
    exit 1
fi
