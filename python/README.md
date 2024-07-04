## Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an AWS-sponsored, open-source Valkey client that includes support for open-source Redis 6.2 to 7.2. Valkey GLIDE works with any distribution that adheres to the Redis Serialization Protocol (RESP) specification, including Amazon ElastiCache, and Amazon MemoryDB.
Strategic, mission-critical applications have requirements for security, optimized performance, minimal downtime, and observability. Valkey GLIDE is designed to provide a client experience that helps meet these objectives. It is sponsored and supported by AWS, and comes pre-configured with best practices learned from over a decade of operating RESP-compatible services used by hundreds of thousands of customers. To help ensure consistency in development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with extensions made available for each supported programming language. This design ensures that updates easily propagate to each language and reduces overall complexity. In this Preview release, Valkey GLIDE is available for Python and Java, with support for Javascript (Node.js) actively under development.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/aws/glide-for-redis/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - Python Wrapper

## System Requirements

The beta release of GLIDE for Redis was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## Python Supported Versions

| Python Version |
|----------------|
| 3.8            |
| 3.9            |
| 3.10           |
| 3.11           |
| 3.12           |

## Installation and Setup

### Installing via Package Manager (pip)

To install GLIDE for Redis using `pip`, follow these steps:

1. Open your terminal.
2. Execute the command below:
    ```bash
    $ pip install glide-for-redis
    ```
3. After installation, confirm the client is accessible by running:
    ```bash
    $ python3
    >>> import glide
    ```

## Basic Examples

#### Cluster Redis:

```python:
>>> import asyncio
>>> from glide import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient
>>> async def test_cluster_client():
...     addresses = [NodeAddress("redis.example.com", 6379)]
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

#### Standalone Redis:

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

## Documentation

Visit our [wiki](https://github.com/aws/glide-for-redis/wiki/Python-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/aws/glide-for-redis/blob/main/python/DEVELOPER.md#build-from-source) file.
