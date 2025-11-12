# TLS Certificate Configuration Guide - Python

This guide explains how to configure custom TLS root certificates for Valkey-GLIDE Python client connections (both async and sync).

## Overview

The Python client supports three modes for TLS certificate validation:

1. **Platform Verifier (Default)**: Uses the operating system's trusted certificate store
2. **Custom Certificates**: Uses user-provided PEM-encoded certificates
3. **Error on Empty**: Returns an error if an empty (but non-None) certificate bytes object is provided

## Usage Examples

### 1. Default Behavior - Platform Verifier

By default, when TLS is enabled without custom certificates, the client uses the OS trust store:

```python
from glide import GlideClient, GlideClusterClient, NodeAddress

# Standalone client
config = GlideClientConfiguration(
    addresses=[NodeAddress("redis.example.com", 6379)],
    use_tls=True
)
client = await GlideClient.create(config)

# Cluster client
cluster_config = GlideClusterClientConfiguration(
    addresses=[NodeAddress("redis.example.com", 6379)],
    use_tls=True
)
cluster_client = await GlideClusterClient.create(cluster_config)
```

### 2. Custom Root Certificates from File

Load custom certificates from a PEM file:

```python
from glide import (
    GlideClient,
    GlideClusterClient,
    NodeAddress,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    TlsAdvancedConfiguration,
    load_root_certificates_from_file,
)

# Load certificates from file
certs = load_root_certificates_from_file('/path/to/ca-cert.pem')

# Configure TLS with custom certificates
tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)

# Standalone client
config = GlideClientConfiguration(
    addresses=[NodeAddress("redis.example.com", 6379)],
    use_tls=True,
    advanced_config=advanced_config
)
client = await GlideClient.create(config)

# Cluster client
cluster_tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
cluster_advanced_config = AdvancedGlideClusterClientConfiguration(
    tls_config=cluster_tls_config
)

cluster_config = GlideClusterClientConfiguration(
    addresses=[NodeAddress("redis.example.com", 6379)],
    use_tls=True,
    advanced_config=cluster_advanced_config
)
cluster_client = await GlideClusterClient.create(cluster_config)
```

### 3. Custom Root Certificates from Memory

Provide certificates directly as a bytes object:

```python
from glide import (
    GlideClientConfiguration,
    AdvancedGlideClientConfiguration,
    TlsAdvancedConfiguration,
    NodeAddress,
)

# Certificate data in PEM format
cert_data = b"""-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAKL0UG+mRKmzMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
...
-----END CERTIFICATE-----"""

tls_config = TlsAdvancedConfiguration(root_pem_cacerts=cert_data)
advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)

config = GlideClientConfiguration(
    addresses=[NodeAddress("redis.example.com", 6379)],
    use_tls=True,
    advanced_config=advanced_config
)
```

### 4. Multiple Certificates

You can provide multiple certificates by concatenating them in PEM format:

```python
from glide import TlsAdvancedConfiguration, AdvancedGlideClientConfiguration

multi_cert_data = b"""-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAKL0UG+mRKmzMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
...
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
MIIDYTCCAkmgAwIBAgIJAKL0UG+mRKm0MA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
...
-----END CERTIFICATE-----"""

tls_config = TlsAdvancedConfiguration(root_pem_cacerts=multi_cert_data)
advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)
```

### 5. Sync Client Usage

The same configuration works for sync clients:

```python
from glide_sync import (
    GlideClient,
    GlideClusterClient,
    NodeAddress,
    GlideClientConfiguration,
    AdvancedGlideClientConfiguration,
    TlsAdvancedConfiguration,
    load_root_certificates_from_file,
)

# Load certificates
certs = load_root_certificates_from_file('/path/to/ca-cert.pem')

# Configure TLS
tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)

# Create sync client
config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    use_tls=True,
    advanced_config=advanced_config
)
client = GlideClient(config)
```

## Important Notes

1. **None vs Empty Bytes**:
   - `None` (default): Uses platform verifier
   - Empty bytes `b""`: Raises `ConfigurationError`
   - Populated bytes: Uses custom certificates

2. **Certificate Format**: Certificates must be in PEM format

3. **Backward Compatibility**: Existing code without custom certificates continues to work unchanged

4. **Self-Signed Certificates**: Custom certificates are particularly useful for:
   - Self-signed certificates
   - Corporate/internal certificate authorities
   - Development/testing environments

5. **Combining with Insecure TLS**: You can use custom certificates with `use_insecure_tls=True` if needed

## Error Handling

```python
from glide import ConfigurationError, TlsAdvancedConfiguration

# This will raise ConfigurationError
try:
    empty_certs = b""
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=empty_certs)
    config = GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
        use_tls=True,
        advanced_config=AdvancedGlideClientConfiguration(tls_config=tls_config)
    )
    request = config._create_a_protobuf_conn_request()
except ConfigurationError as e:
    print(f"Error: {e}")
    # Error: root_pem_cacerts cannot be an empty bytes object; use None to use platform verifier
```

## Complete Example - Async

```python
import asyncio
from glide import (
    GlideClient,
    NodeAddress,
    GlideClientConfiguration,
    AdvancedGlideClientConfiguration,
    TlsAdvancedConfiguration,
    load_root_certificates_from_file,
)

async def main():
    # Load custom CA certificate
    try:
        certs = load_root_certificates_from_file('/path/to/ca-cert.pem')
    except FileNotFoundError as e:
        print(f"Certificate file not found: {e}")
        return
    except Exception as e:
        print(f"Failed to load certificates: {e}")
        return

    # Configure TLS
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
    advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)

    # Create client configuration
    config = GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
        use_tls=True,
        advanced_config=advanced_config
    )

    # Create client (this will work once core implementation is complete)
    try:
        client = await GlideClient.create(config)
        
        # Use the client...
        result = await client.set("key", "value")
        print(f"Set result: {result}")
        
        value = await client.get("key")
        print(f"Get result: {value}")
        
    finally:
        await client.close()

if __name__ == "__main__":
    asyncio.run(main())
```

## Complete Example - Sync

```python
from glide_sync import (
    GlideClient,
    NodeAddress,
    GlideClientConfiguration,
    AdvancedGlideClientConfiguration,
    TlsAdvancedConfiguration,
    load_root_certificates_from_file,
)

def main():
    # Load custom CA certificate
    try:
        certs = load_root_certificates_from_file('/path/to/ca-cert.pem')
    except FileNotFoundError as e:
        print(f"Certificate file not found: {e}")
        return
    except Exception as e:
        print(f"Failed to load certificates: {e}")
        return

    # Configure TLS
    tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
    advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)

    # Create client configuration
    config = GlideClientConfiguration(
        addresses=[NodeAddress("localhost", 6379)],
        use_tls=True,
        advanced_config=advanced_config
    )

    # Create client (this will work once core implementation is complete)
    try:
        client = GlideClient(config)
        
        # Use the client...
        result = client.set("key", "value")
        print(f"Set result: {result}")
        
        value = client.get("key")
        print(f"Get result: {value}")
        
    finally:
        client.close()

if __name__ == "__main__":
    main()
```

## Testing Configuration

Until the core implementation is complete, you can test the configuration generation:

```python
from glide import (
    GlideClientConfiguration,
    AdvancedGlideClientConfiguration,
    TlsAdvancedConfiguration,
    NodeAddress,
)

# Test configuration
certs = b"-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----"
tls_config = TlsAdvancedConfiguration(root_pem_cacerts=certs)
advanced_config = AdvancedGlideClientConfiguration(tls_config=tls_config)

config = GlideClientConfiguration(
    addresses=[NodeAddress("localhost", 6379)],
    use_tls=True,
    advanced_config=advanced_config
)

request = config._create_a_protobuf_conn_request()
print(f"TLS Mode: {request.tls_mode}")
print(f"Root Certs Set: {len(request.root_certs) > 0}")
if request.root_certs:
    print(f"Number of cert entries: {len(request.root_certs)}")
```

## API Reference

### `TlsAdvancedConfiguration`

```python
class TlsAdvancedConfiguration:
    def __init__(
        self,
        use_insecure_tls: Optional[bool] = None,
        root_pem_cacerts: Optional[bytes] = None,
    ):
        """
        Args:
            use_insecure_tls: Whether to bypass TLS certificate verification
            root_pem_cacerts: Custom root certificate data in PEM format
        """
```

### `load_root_certificates_from_file`

```python
def load_root_certificates_from_file(path: str) -> bytes:
    """
    Load PEM-encoded root certificates from a file.
    
    Args:
        path: The file path to the PEM-encoded certificate file
        
    Returns:
        The certificate data in PEM format
        
    Raises:
        FileNotFoundError: If the certificate file does not exist
        ConfigurationError: If the certificate file is empty
    """
```
