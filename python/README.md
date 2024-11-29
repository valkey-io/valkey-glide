# Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services. Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications. It is sponsored and supported by AWS, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions. This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - Python Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

Linux:

-   Ubuntu 22.04.1 (x86_64 and aarch64)
-   Amazon Linux 2023 (AL2023) (x86_64)

macOS:

-   macOS 14.7 (Apple silicon/aarch_64)

## Python Supported Versions

| Python Version |
|----------------|
| 3.9            |
| 3.10           |
| 3.11           |
| 3.12           |
| 3.13           |

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

## Documentation

Visit our [wiki](https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/python/DEVELOPER.md#build-from-source) file.
