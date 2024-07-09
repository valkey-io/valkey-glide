# Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services. Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications. It is sponsored and supported by AWS, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions. This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

## Current Status

We've made Valkey GLIDE an open-source project, and are releasing it in Preview to the community to gather feedback, and actively collaborate on the project roadmap. We welcome questions and contributions from all Redis stakeholders.
This preview release is recommended for testing purposes only.

# Getting Started - Node Wrapper

## System Requirements

The beta release of Valkey GLIDE was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## NodeJS supported version

Node.js 16.20 or higher.

## Installation and Setup

### Installing via Package Manager (npm)

To install GLIDE for Redis using `npm`, follow these steps:

1. Open your terminal.
2. Execute the command below:
    ```bash
    $ npm install @aws/glide-for-redis
    ```
3. After installation, confirm the client is installed by running:
    ```bash
    $ npm list
    myApp@ /home/ubuntu/myApp
    └── @aws/glide-for-redis@0.1.0
    ```

## Basic Examples

#### Cluster Redis:

```node
import { GlideClusterClient } from "@aws/glide-for-redis";

const addresses = [
    {
        host: "redis.example.com",
        port: 6379,
    },
];
const client = await GlideClusterClient.createClient({
    addresses: addresses,
});
await client.set("foo", "bar");
const value = await client.get("foo");
client.close();
```

#### Standalone Redis:

```node
import { GlideClient } from "@aws/glide-for-redis";

const addresses = [
    {
        host: "redis_primary.example.com",
        port: 6379,
    },
    {
        host: "redis_replica.example.com",
        port: 6379,
    },
];
const client = await GlideClient.createClient({
    addresses: addresses,
});
await client.set("foo", "bar");
const value = await client.get("foo");
client.close();
```

## Documentation

Visit our [wiki](https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/node/DEVELOPER.md#build-from-source) file.

### Supported platforms

Currentlly the package is supported on:

| Operation systems | C lib                | Architecture      |
| ----------------- | -------------------- | ----------------- |
| `Linux`           | `glibc`, `musl libc` | `x86_64`, `arm64` |
| `macOS`           | `Darwin`             | `x86_64`, `arm64` |
