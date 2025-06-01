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
Visit our [wiki](https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - Node Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

### Linux GNU

Linux with **glibc 2.17** or higher.

### MacOS (Darwin)

MacOS Apple Silicon/aarch_64 and x86_64/amd64.

- Full tests are running on MacOS 15.0 arm64/aarch64
- Minimal tests are running on: MacOS 13.5 x86*64/amd64*(We do not recommend using MacOS Intel for production, It is supported for development purposes)\_

### Alpine

All alpine versions that are using _musl libc_ 1.2.3 (All Alpine non deprecated alpine versions) or higher should be supported.
Tests are running on:

- node:alpine (x86_64/amd64 and arm64/aarch64)

## NodeJS supported version

Node.js 16 or higher.
**For npm users on linux it is recommended to use npm >=11 since it support optional download base on libc, yarn users should not be concerned**

- Note: The library is dependent on the [protobufjs library](https://protobufjs.github.io/protobuf.js/#installation), which add a size to the package. The package is using the protobufjs/minimal version, hence, if size matter, bundlers should be able to strip the unused code. It should reduce the size of the dependency from 19kb gzipped to 6.5kb gzipped.

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](https://github.com/valkey-io/valkey-glide/blob/main/node/DEVELOPER.md#build-from-source) file.

# Quick Start

## Installation

```bash
npm i @valkey/valkey-glide
```

## Basic Examples

#### Standalone Mode:

```typescript
import { GlideClient, GlideClusterClient, Logger } from "@valkey/valkey-glide";
// When Valkey is in standalone mode, add address of the primary node, and any replicas you'd like to be able to read from.
const addresses = [
    {
        host: "localhost",
        port: 6379,
    },
];
// Check `GlideClientConfiguration/GlideClusterClientConfiguration` for additional options.
const client = await GlideClient.createClient({
    addresses: addresses,
    // if the server uses TLS, you'll need to enable it. Otherwise, the connection attempt will time out silently.
    // useTLS: true,
    // It is recommended to set a timeout for your specific use case
    requestTimeout: 500, // 500ms timeout
    clientName: "test_standalone_client",
});
// The empty array signifies that there are no additional arguments.
const pong = await client.customCommand(["PING"]);
console.log(pong);
const set_response = await client.set("foo", "bar");
console.log(`Set response is = ${set_response}`);
const get_response = await client.get("foo");
console.log(`Get response is = ${get_response}`);
```

#### Cluster Mode:

```typescript
import { GlideClient, GlideClusterClient, Logger } from "@valkey/valkey-glide";
// When Valkey is in cluster mode, add address of any nodes, and the client will find all nodes in the cluster.
const addresses = [
    {
        host: "localhost",
        port: 6379,
    },
];
// Check `GlideClientConfiguration/GlideClusterClientConfiguration` for additional options.
const client = await GlideClusterClient.createClient({
    addresses: addresses,
    // if the cluster nodes use TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
    // useTLS: true,
    // It is recommended to set a timeout for your specific use case
    requestTimeout: 500, // 500ms timeout
    clientName: "test_cluster_client",
});
// The empty array signifies that there are no additional arguments.
const pong = await client.customCommand(["PING"], { route: "randomNode" });
console.log(pong);
const set_response = await client.set("foo", "bar");
console.log(`Set response is = ${set_response}`);
const get_response = await client.get("foo");
console.log(`Get response is = ${get_response}`);
client.close();
```

### Supported platforms

Currently, the package is tested on:

| Operation systems | C lib                | Architecture      |
| ----------------- | -------------------- | ----------------- |
| `Linux`           | `glibc`, `musl libc` | `x86_64`, `arm64` |
| `macOS`           | `Darwin`             | `x86_64`, `arm64` |

## Community and Feedback

We encourage you to join our community to support, share feedback, and ask questions. You can approach us for anything on our Valkey Slack: [Join Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).
