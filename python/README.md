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

-   Ubuntu 22.04.5 (x86_64/amd64 and arm64/aarch64)
-   Amazon Linux 2023 (AL2023) (x86_64)

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

### Installing via Package Manager (pip)

To install Valkey GLIDE using `pip`, follow these steps:

1. Open your terminal.
2. Execute the command below:
    ```bash
    $ pip install valkey-glide
    ```
3. After installation, confirm the client is accessible by running:
    ```bash
    $ python3
    >>> import glide
    ```

## Basic Examples

#### Cluster Mode:

```python:
>>> import asyncio
>>> from glide import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient
>>> async def test_cluster_client():
...     addresses = [NodeAddress("address.example.com", 6379)]
...     config = GlideClusterClientConfiguration(addresses)
...     client = await GlideClusterClient.create(config)
...     set_result = await client.set("foo", "bar")
...     print(f"Set response is {set_result}")
...     get_result = await client.get("foo")
...     print(f"Get response is {get_result}")
... 
>>> asyncio.run(test_cluster_client())
Set response is OK
Get response is bar
```

#### Standalone Mode:

```python:
>>> import asyncio
>>> from glide import GlideClientConfiguration, NodeAddress, GlideClient
>>> async def test_standalone_client():
...     addresses = [
...             NodeAddress("server_primary.example.com", 6379),
...             NodeAddress("server_replica.example.com", 6379)
...     ]
...     config = GlideClientConfiguration(addresses)
...     client = await GlideClient.create(config)
...     set_result = await client.set("foo", "bar")
...     print(f"Set response is {set_result}")
...     get_result = await client.get("foo")
...     print(f"Get response is {get_result}")
... 
>>> asyncio.run(test_standalone_client())
Set response is OK
Get response is bar
```

For complete examples with error handling, please refer to the [cluster example](https://github.com/valkey-io/valkey-glide/blob/main/examples/python/cluster_example.py) and the [standalone example](https://github.com/valkey-io/valkey-glide/blob/main/examples/python/standalone_example.py).


### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/python/DEVELOPER.md#build-from-source) file.

## Community and Feedback

We encourage you to join our community to support, share feedback, and ask questions. You can approach us for anything on our Valkey Slack: [Join Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).
