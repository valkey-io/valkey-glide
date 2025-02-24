# Valkey GLIDE
Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0, and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS-compatible services.

It is built for modern cloud-native architectures, Valkey GLIDE provides seamless integration with containerized and serverless environments. It supports auto-scaling, load balancing, and optimized connection pooling, making it suitable for high-performance distributed applications. Valkey GLIDE is designed for reliability, optimized performance, and high availability, for Valkey and Redis OSS-based applications. It includes robust failover mechanisms, ensuring continuous availability even in the event of server failures. Its built-in connection resilience minimizes downtime and improves reliability for latency-sensitive workloads.

It is sponsored and supported by AWS and GCP, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. This means that Valkey GLIDE inherits security, scalability, and performance optimizations based on real-world deployment experiences from some of the largest cloud providers.

To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language-specific extensions. The Rust-based core ensures memory safety, concurrent execution, and minimal runtime overhead, making it one of the most efficient and secure Valkey client libraries available.

This design ensures consistency in features across languages and reduces overall complexity. With a unified API and language-agnostic architecture, developers can transition between supported languages without major modifications, reducing learning curves and improving productivity. This ensures that enterprise applications remain scalable and maintainable across multiple development teams and technology stacks.

The multi-language support extends the reach of Valkey GLIDE, allowing software teams to standardize their data interactions across various programming ecosystems. Developers using Java, Python, Node.js, and Go (Public Preview) can take full advantage of Valkey's high-speed in-memory capabilities without reinventing the wheel.

Valkey GLIDE is continuously evolving with community-driven contributions and enterprise-backed enhancements, ensuring long-term reliability and feature parity with emerging Valkey capabilities. This makes it an ideal choice for businesses seeking a future-proof, scalable, and high-performance Redis-compatible client solution.









## Supported Engine Versions
Valkey GLIDE is API-compatible with the following engine versions:

| Engine Type           |  6.2  |  7.0  |  7.2  |  8.0  |
|-----------------------|-------|-------|-------|-------|
| Valkey                |   -   |   -   |   V   |   V   |
| Redis                 |   V   |   V   |   V   |   -   |

## Current Status and upcoming releases

In the current release, Valkey GLIDE is officially available for Python, Java, and Node.js, providing full compatibility with Valkey 7.2+ and Redis OSS 6.2, 7.0, and 7.2. These languages have been rigorously tested and optimized to ensure high performance, stability, and feature completeness.
Support for Go is currently in public preview, allowing developers to experiment with and provide feedback on its implementation. The Go client has been designed with efficiency and concurrency in mind, leveraging Go’s native goroutines and asynchronous capabilities to ensure non-blocking performance for highly scalable applications. While it is stable for general use, further optimizations and refinements are expected before reaching full production readiness.
#### v1.2 (Dec. 2024)
- Vector Similarity Search and JSON modules support
- Availability zone routing for Read from Replica

#### v1.3 (Feb. 2025)
- Public preview for GO support

## Getting Started
-   [Java](./java/README.md)
-   [Python](./python/README.md)
-   [Node](./node/README.md)
-   [Go](./go/README.md)
-   [Documentation](https://github.com/valkey-io/valkey-glide/wiki)

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

Providing comprehensive details will allow the maintainers to diagnose and resolve issues more efficiently. We also encourage users to include suggested fixes or workarounds if they have explored potential solutions.

## Contributing

GitHub is a platform for collaborative coding. If you're interested in writing code, we encourage you to contribute by submitting pull requests from forked copies of this repository. Additionally, please consider creating GitHub issues for reporting bugs and suggesting new features. Feel free to comment on issues that interest. For more info see [Contributing](./CONTRIBUTING.md).

## License
* [Apache License 2.0](./LICENSE)
