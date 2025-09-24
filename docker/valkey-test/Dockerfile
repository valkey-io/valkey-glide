FROM ubuntu:22.04

# Install essential packages
RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    pkg-config \
    libssl-dev \
    python3 \
    python3-pip \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Clone and build Valkey
ARG VALKEY_VERSION=8.0
RUN git clone https://github.com/valkey-io/valkey.git /tmp/valkey \
    && cd /tmp/valkey \
    && git checkout ${VALKEY_VERSION} \
    && make BUILD_TLS=yes \
    && make install \
    && rm -rf /tmp/valkey

# Create valkey user and directories
RUN useradd -r -s /bin/false valkey \
    && mkdir -p /var/lib/valkey /var/log/valkey /etc/valkey \
    && chown valkey:valkey /var/lib/valkey /var/log/valkey

# Copy configuration templates
COPY valkey.conf /etc/valkey/valkey.conf
COPY valkey-cluster.conf /etc/valkey/valkey-cluster.conf

# Expose ports for standalone and cluster
EXPOSE 6379 7000 7001 7002 7003 7004 7005

# Create entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Switch to valkey user
USER valkey

ENTRYPOINT ["/entrypoint.sh"]
CMD ["valkey-server", "/etc/valkey/valkey.conf"]