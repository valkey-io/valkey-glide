# Welcome to Valkey GLIDE!

Valkey General Language Independent Driver for the Enterprise (GLIDE) is an official open-source Valkey client library, proudly part of the Valkey organization. Our mission is to make your experience with Valkey and Redis OSS seamless and enjoyable. Whether you're a seasoned developer or just starting out, Valkey GLIDE is here to support you every step of the way.

Visit our official documentation at [glide.valkey.io](https://glide.valkey.io).

## Why Choose Valkey GLIDE?

- **Community and Open Source**: Join our vibrant community and contribute to the project. We are always here to respond, and the client is for the community.
- **Reliability**: Built with best practices learned from over a decade of operating Redis OSS-compatible services.
- **Performance**: Optimized for high performance and low latency.
- **High Availability**: Designed to ensure your applications are always up and running.
- **Cross-Language Support**: Implemented using a core driver framework written in Rust, with language-specific extensions to ensure consistency and reduce complexity.
- **Stability and Fault Tolerance**: We brought our years of experience to create a bulletproof client.
- **Backed and Supported by AWS and GCP**: Ensuring robust support and continuous improvement of the project.

## Key Features
- **[AZ Affinity](https://valkey.io/blog/az-affinity-strategy/)** – Ensures low-latency connections and minimal cross-zone costs by routing read traffic to replicas in the clients availability zone. **(Requires Valkey server version 8.0+ or AWS ElastiCache for Valkey 7.2+)**.
- **[PubSub Auto-Reconnection](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#pubsub-support:~:text=PubSub%20Support,Receiving%2C%20and%20Unsubscribing.)** – Seamless background resubscription on topology updates or disconnection.
- **[Sharded PubSub](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#pubsub-support:~:text=Receiving%2C%20and%20Unsubscribing.-,Subscribing,routed%20to%20the%20server%20holding%20the%20slot%20for%20the%20command%27s%20channel.,-Receiving)** – Native support for sharded PubSub across cluster slots.
- **[Cluster-Aware MGET/MSET/DEL/FLUSHALL](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#multi-slot-command-handling:~:text=Multi%2DSlot%20Command%20Execution,JSON.MGET)** – Execute multi-key commands across cluster slots without manual key grouping.
- **[Cluster Scan](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan)** – Unified key iteration across shards using a consistent, high-level API for cluster environments.
- **Support for TS / CJS / MJS** – Fully compatible with modern and legacy JavaScript/TypeScript runtimes.
- **Support for asyncio / anyio / trio** – Native compatibility with modern Python async frameworks, enabling efficient and seamless integration into asynchronous workflows.
- **[Batching (Pipeline and Transaction)](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#batching-pipeline-and-transaction)** – Efficiently execute multiple commands in a single network roundtrip, significantly reducing latency and improving throughput.
- **[OpenTelemetry](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#opentelemetry)** – Integrated tracing support for enhanced observability and easier debugging in distributed environments.

## Supported Engine Versions

Valkey GLIDE is API-compatible with the following engine versions:

| Engine Type           |  6.2  |  7.0  |   7.1  |  7.2  |  8.0  |  8.1  |  9.0  |
|-----------------------|-------|-------|--------|-------|-------|-------|-------|
| Valkey                |   -   |   -   |   -   |   ✅   |   ✅   |   ✅   |   ✅   |
| Redis                 |   ✅   |   ✅   |   ✅   |   ✅   |   -   |   -   |   -   |

## Current Status and Upcoming Releases

The client currently supports Python, Java, Node.js, Go, C#, and PHP. C# and PHP have preview releases, and have been moved to separate repositories to simplify development. Active development continues for C#, PHP, C++ and Ruby clients. Python, Java, Node.js and Go clients will be moved to separate repositories in the near future.

#### v2.2 (Nov. 2025)
- Windows Support for Java Client – Migrated the Java client to JNI-based communication
- IAM authentication Support – Added automatic authentication token generation, enabling secure, password-free connections
- Seed-Based Topology Refresh – Added topology refresh capability
- Enhanced TLS Certificate Configuration – Added support for custom CA certificates in TLS connections

### Previous Releases

#### v2.1 (Sep. 2025)
- Valkey 9 Support – First-class support for Multi-DB and Hash Field Expiration (HFE)
- Python Sync Support – Full synchronous API support for Python
- Lazy Connection – Extended lazy connection support to Go and Java clients
- Jedis Compatibility – Added Jedis compatibility layer for Java client

#### v2.0 (June 2025)

- Go GA – Official stable release for production environments
- OpenTelemetry Integration – Enhanced observability and tracing
- Batching Support – Improved performance through batch operations
- Lazy Connection – Allows client creation even when the server is not active, deferring connection establishment.

#### v1.3 (Feb. 2025)
- Public preview release of Go client support

#### v1.2 (Dec. 2024)
- Vector Similarity Search and JSON module support
- Availability zone-aware routing for read-from-replica operations

## Getting Started

**Documentation**
GLIDE's [documentation site](https://valkey.io/valkey-glide/) currently offers documentation for the Python and Node wrappers.

**SDKs**
- [Java](./java/README.md)
- [Python](./python/README.md)
- [Node](./node/README.md)
- [Go](./go/README.md)

**Under Development SDKs**
- [C#](https://github.com/valkey-io/valkey-glide-csharp)
- [C++](https://github.com/valkey-io/valkey-glide-cpp)
- [Ruby](https://github.com/valkey-io/valkey-glide-ruby)

**General Concepts:**
- [Custom Command](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command)
- [Connection Management](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#connection-management)
- [Multi-Slot Command Handling](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#multi-slot-command-handling)
- [Inflight Request Limit](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#inflight-request-limit)
- [PubSub Support](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#pubsub-support)
- [Cluster Scan](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan)
- [Dynamic Password Management](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#dynamic-password-management)
- [Modules API](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#modules-api)
- [Batching (Pipeline and Transaction)](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#batching-pipeline-and-transaction)
- [OpenTelemetry](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#opentelemetry)

**Migration Guides**
- [go-redis](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-go%E2%80%90redis)
- [ioredis](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-ioredis)
- [Jedis](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-Jedis)
- [Lettuce](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-Lettuce)
- [Redisson](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-redisson)
- [redis-py](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-redis%E2%80%90py)
- [StackExchange.Redis](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-StackExchange.Redis)
- [PHPRedis](https://github.com/valkey-io/valkey-glide-php/wiki/Migration-Guide-PHPRedis)

**Community**
- [Contributors meeting](https://github.com/valkey-io/valkey-glide/wiki/Contributors-meeting)

Looking for more? Check out the [Valkey Glide Wiki](https://github.com/valkey-io/valkey-glide/wiki).

## Ecosystem

Valkey GLIDE has a growing ecosystem of integrations and extensions that enhance its functionality across different frameworks and use cases:

- **[node-flexible-rate-limiter](https://www.npmjs.com/package/rate-limiter-flexible)** - A flexible rate limiting library for Node.js with Valkey GLIDE backend support
- **[fastify-valkey-glide](https://www.npmjs.com/package/@fastify/valkey-glide)** - Fastify plugin for Valkey GLIDE integration, enabling seamless caching and session management
- **[aiocache](https://pypi.org/project/aiocache/)** - Python async caching framework with Valkey GLIDE backend support for high-performance distributed caching
- **[aws-lambda-powertools-typescript](https://github.com/aws-powertools/powertools-lambda-typescript)** - AWS Lambda Powertools for TypeScript with Valkey GLIDE integration in the idempotency feature (more integrations planned)
- **[aws-lambda-powertools-python](https://github.com/aws-powertools/powertools-lambda-python)** - AWS Lambda Powertools for Python with Valkey GLIDE support in the idempotency feature (more integrations planned)
- **[redlock-universal](https://www.npmjs.com/package/redlock-universal)** - Distributed lock library for Node.js with native GLIDE adapter, featuring auto-extension and atomic batch acquisition

## Getting Help

If you have any questions, feature requests, encounter issues, or need assistance with this project, please don't hesitate to open a GitHub issue. Our community and contributors are here to help you. Before creating an issue, we recommend checking the [existing issues](https://github.com/valkey-io/valkey-glide/issues) to see if your question or problem has already been addressed. If not, feel free to create a new issue, and we'll do our best to assist you. Please provide as much detail as possible in your issue description, including:

1. A clear and concise title
2. Detailed description of the problem or question
3. Reproducible test case or step-by-step instructions
4. Valkey GLIDE version in use
5. Operating system details
6. Server version
7. Cluster or standalone setup information, including topology, number of shards, number of replicas, and data types used
8. Relevant modifications you've made
9. Any unusual aspects of your environment or deployment
10. Log files

## Contributing

GitHub is a platform for collaborative coding. If you're interested in writing code, we encourage you to contribute by submitting pull requests from forked copies of this repository. Additionally, please consider creating GitHub issues for reporting bugs and suggesting new features. Feel free to comment on issues that interest. For more info see [Contributing](./CONTRIBUTING.md).

## Get Involved!

We invite you to join our open-source community and contribute to Valkey GLIDE. Whether it's reporting bugs, suggesting new features, or submitting pull requests, your contributions are highly valued. Check out our [Contributing Guidelines](./CONTRIBUTING.md) to get started.

If you have any questions or need assistance, don't hesitate to reach out. Open a GitHub issue, and our community and contributors will be happy to help you.

## Community Support and Feedback

We encourage you to join our community to support, share feedback, and ask questions. You can approach us for anything on our Valkey Slack: [Join Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).

## License
* [Apache License 2.0](./LICENSE)
