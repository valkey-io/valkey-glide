# Welcome to Valkey GLIDE!

Valkey General Language Independent Driver for the Enterprise (GLIDE) is the official open-source Valkey client library, proudly part of the Valkey organization. Our mission is to make your experience with Valkey and Redis OSS seamless and enjoyable. Whether you're a seasoned developer or just starting out, Valkey GLIDE is here to support you every step of the way.

## Why Choose Valkey GLIDE?

- **Community and Open Source**: Join our vibrant community and contribute to the project. We are always here to respond, and the client is for the community.
- **Reliability**: Built with best practices learned from over a decade of operating Redis OSS-compatible services.
- **Performance**: Optimized for high performance and low latency.
- **High Availability**: Designed to ensure your applications are always up and running.
- **Cross-Language Support**: Implemented using a core driver framework written in Rust, with language-specific extensions to ensure consistency and reduce complexity.
- **Stability and Fault Tolerance**: We brought our years of experience to create a bulletproof client.
- **Backed and Supported by AWS and GCP**: Ensuring robust support and continuous improvement of the project.

## Supported Engine Versions

Valkey GLIDE is API-compatible with the following engine versions:

| Engine Type           |  6.2  |  7.0  |   7.1  |  7.2  |  8.0  |  8.1  |
|-----------------------|-------|-------|--------|-------|-------|-------|
| Valkey                |   -   |   -   |   -    |   V   |   V   |   V   |
| Redis                 |   V   |   V   |   V    |   V   |   -   |   -   |

## Current Status and Upcoming Releases

In the current release, Valkey GLIDE is available for Python, Java, and Node.js. Support for Go is currently in **public preview** and support for C# is **under active development**, with plans to include more programming languages in the future. Additionally, Python sync and C++ are under active development, and a Ruby client is starting to take off.

#### v1.2 (Dec. 2024)
- Vector Similarity Search and JSON modules support
- Availability zone routing for Read from Replica

#### v1.3 (Feb. 2025)
- Public preview for GO support

## Getting Started

**Documentation** 
GLIDE's [documentation site](https://valkey.io/valkey-glide/) currently offers documentation for the Python and Node wrappers. 

**SDKs**
- [Java](./java/README.md)
- [Python](./python/README.md)
- [Node](./node/README.md)
- [Go](./go/README.md)

**General Concepts:** 
- [Custom Command](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command)
- [Connection Management](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#connection-management)
- [Multi-Slot Command Handling](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#multi-slot-command-handling)
- [Inflight Request Limit](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#inflight-request-limit)
- [PubSub Support](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#pubsub-support)
- [Cluster Scan](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan)
- [Dynamic Password Management](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#dynamic-password-management)
- [Modules API](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#modules-api)

**Migration Guides**
- [Jedis](https://github.com/valkey-io/valkey-glide/wiki/Migration-Guide-Jedis)

**Community**
- [Contributors meeting](https://github.com/valkey-io/valkey-glide/wiki/Contributors-meeting)

Looking for more? Check out the [Valkey Glide Wiki](https://github.com/valkey-io/valkey-glide/wiki).

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
