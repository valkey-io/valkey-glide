# Valkey GLIDE
Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services. Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications. It is sponsored and supported by AWS and GCP, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions. This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions
Valkey GLIDE is API-compatible with the following engine versions:

| Engine Type           |  6.2  |  7.0  |  7.2  |  8.0  |
|-----------------------|-------|-------|-------|-------|
| Valkey                |   -   |   -   |   V   |   V   |
| Redis                 |   V   |   V   |   V   |   -   |

## Current Status and upcoming releases
In the current release, Valkey GLIDE is available for Python, Java and Node.js. Support for Go is currently in **public preview** and support for C# is **under active development**, with plans to include more programming languages in the future.

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

## Known issues

GLIDE has a native component as a Rust based library. Currently the native componnent is not compatible with certion older GLIBC based OS's.
The most relevant one is Debian 11, which is the base of ubuntu 20, which some other distros are based on, like Mint and Pop!_OS 20 etc. As a result, the ones mentioned above are incompatible with GLIDE.

Another OS which is known to be incompatible is Amazon Linux 2, which is the previoues versions of what [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html) is running on (node 18, py <=11, java <= 17). Note that this is not affecting MUSL based distros, which are not based on GLIBC.

For all the incompatible Linux distros listed, there's at least one newer and stable version available, and it is recomended to use them.

| Incompatible Distro Version | Compatible Successor Version |
|-----------------------------|------------------------------|
| Debian 11                   | Debian 12 / 13               |
| Ubuntu 20.04 LTS            | Ubuntu 22.04 LTS / 24.04 LTS |
| Linux Mint 20               | Linux Mint 21 / 22           |
| Pop!_OS 20.04 LTS           | Pop!_OS 22.04 LTS            |
| Amazon Linux 2              | Amazon Linux 2023            |

When running on incompatible systems, the client crashes with one of the following errors:
```console
/lib/x86_64-linux-gnu/libc.so.6: version `GLIBC_2.34' not found
/lib64/libm.so.6: version `GLIBC_2.29' not found
```
You can get the `GLIBC` version included in your OS by running `ldd --version`. `GLIBC` versions `2.26`, `2.27`, `2.30`, and `2.31` are not supported.
We are working hard to resolve this issue. You can track our progress in issue [#3291](https://github.com/valkey-io/valkey-glide/issues/3291).

## Contributing

GitHub is a platform for collaborative coding. If you're interested in writing code, we encourage you to contribute by submitting pull requests from forked copies of this repository. Additionally, please consider creating GitHub issues for reporting bugs and suggesting new features. Feel free to comment on issues that interest. For more info see [Contributing](./CONTRIBUTING.md).

## License
* [Apache License 2.0](./LICENSE)
