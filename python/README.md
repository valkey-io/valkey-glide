# Welcome to Valkey GLIDE!

Valkey General Language Independent Driver for the Enterprise (GLIDE) is the official open-source Valkey client library, proudly part of the Valkey organization. Our mission is to make your experience with Valkey and Redis OSS seamless and enjoyable. Whether you're a seasoned developer or just starting out, Valkey GLIDE is here to support you every step of the way.

# Why Choose Valkey GLIDE?

- **Community and Open Source**: Join our vibrant community and contribute to the project. We are always here to respond, and the client is for the community.
- **Reliability**: Built with best practices learned from over a decade of operating Redis OSS-compatible services.
- **Performance**: Optimized for high performance and low latency.
- **High Availability**: Designed to ensure your applications are always up and running.
- **Cross-Language Support**: Implemented using a core driver framework written in Rust, with language-specific extensions to ensure consistency and reduce complexity.
- **Stability and Fault Tolerance**: We brought our years of experience to create a bulletproof client.
- **Backed and Supported by AWS and GCP**: Ensuring robust support and continuous improvement of the project.

## Documentation

See GLIDE's [documentation site](https://valkey.io/valkey-glide/).  
Visit our [wiki](https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - Python Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

Linux:

-   Ubuntu 20 (x86_64/amd64 and arm64/aarch64)
-   Amazon Linux 2 (AL2) and 2023 (AL2023) (x86_64)

**Note: Currently Alpine Linux / MUSL is NOT supported.**

macOS:

-   macOS 14.7 (Apple silicon/aarch_64)
-   macOS 13.7 (x86_64/amd64)

## Python Supported Versions

| Python Version |
|----------------|
| 3.9            |
| 3.10           |
| 3.11           |
| 3.12           |
| 3.13           |

Valkey GLIDE transparently supports both the `asyncio` and `trio` concurrency frameworks.

## Installation and Setup

### ✅ Async Client

To install the async version:

```bash
pip install valkey-glide
```

Verify installation:

```bash
python3
>>> import glide
```

### ✅ Sync Client

To install the sync version:

```bash
pip install valkey-glide-sync
```

Verify installation:

```bash
python3
>>> import glide_sync
```

---

## Basic Examples

### 🔁 Async Client

### ✅ Async Cluster Mode

```python
import asyncio
from glide import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient

async def test_cluster_client():
    addresses = [NodeAddress("address.example.com", 6379)]
    # It is recommended to set a timeout for your specific use case
    config = GlideClusterClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = await GlideClusterClient.create(config)
    set_result = await client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = await client.get("foo")
    print(f"Get response is {get_result}")

asyncio.run(test_cluster_client())
```

#### ✅ Async Standalone Mode

```python
import asyncio
from glide import GlideClientConfiguration, NodeAddress, GlideClient

async def test_standalone_client():
    addresses = [
        NodeAddress("server_primary.example.com", 6379),
        NodeAddress("server_replica.example.com", 6379)
    ]
    # It is recommended to set a timeout for your specific use case
    config = GlideClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = await GlideClient.create(config)
    set_result = await client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = await client.get("foo")
    print(f"Get response is {get_result}")

asyncio.run(test_standalone_client())
```

---

### 🔂 Sync Client

#### ✅ Sync Cluster Mode

```python
from glide_sync import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient

def test_cluster_client():
    addresses = [NodeAddress("address.example.com", 6379)]
    # It is recommended to set a timeout for your specific use case
    config = GlideClusterClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = GlideClusterClient.create(config)
    set_result = client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = client.get("foo")
    print(f"Get response is {get_result}")

test_cluster_client()
```

#### ✅ Sync Standalone Mode

```python
from glide_sync import GlideClientConfiguration, NodeAddress, GlideClient

def test_standalone_client():
    addresses = [
        NodeAddress("server_primary.example.com", 6379),
        NodeAddress("server_replica.example.com", 6379)
    ]
    # It is recommended to set a timeout for your specific use case
    config = GlideClientConfiguration(addresses, request_timeout=500)  # 500ms timeout
    client = GlideClient.create(config)
    set_result = client.set("foo", "bar")
    print(f"Set response is {set_result}")
    get_result = client.get("foo")
    print(f"Get response is {get_result}")

test_standalone_client()
```

---

For complete examples with error handling, please refer to:
- [Async Cluster Example](https://github.com/valkey-io/valkey-glide/blob/main/examples/python/cluster_example.py)
- [Async Standalone Example](https://github.com/valkey-io/valkey-glide/blob/main/examples/python/standalone_example.py)


### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/python/DEVELOPER.md#build-from-source) file.

## Community and Feedback

We encourage you to join our community to support, share feedback, and ask questions. You can approach us for anything on our Valkey Slack: [Join Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).
