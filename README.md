# Babushka (Temporary Name)
[The followig short description is place holder, copied from PRFAQ - should not be reviewed]
Babushka is a Redis client. It’s free, open-sourced under a permissive license (Apache 2.0), sponsored by AWS and connects to any Redis datastore. Over the years, AWS has gained operational experience in managing ElastiCache for Redis and MemoryDB at a very large scale. We know that the client library is key for reliability, performance, and security. We brought this operational experience to Babushka, and we optimized it for open-source and managed Redis workloads running on AWS. Here are some examples: Babushka is configured by default with an exponential backoff retry strategy to prevent connection storms. To handle cluster topology changes better, Babushka proactively checks DNS endpoints validity, and auto-triggers DNS refreshes. Customers do not experience slowness originating from bad connections configuration, because Babushka uses a smart connection multiplexing algorithm. Babushka is built with a common core layer, written in Rust, which can be extended to support various programming languages. With this release, Babushka supports Python and TypeScript. Having a common core ensures consistency across all supported languages, and provides a unified client experience, which means similar behavior and functionality. 

## Supported Redis Versions
Redis 6 and above

## Current Status
Babushka is currently a **beta release** and is recommended for testing purposes only. We're tracking its production readiness and future features on the [roadmap](https://github.com/orgs/aws/projects/165/).


## Getting Started

-   [Node](./node/README.md)
-   [Python](./python/README.md)

## Getting Help
If you have any questions, feature requests, encounter issues, or need assistance with this project, please don't hesitate to open a GitHub issue. Our community and contributors are here to help you. Before creating an issue, we recommend checking the [existing issues](https://github.com/aws/babushka/issues) to see if your question or problem has already been addressed. If not, feel free to create a new issue, and we'll do our best to assist you. Please provide as much detail as possible in your issue description, including: 

1. A clear and concise title
2. Detailed description of the problem or question
3. A reproducible test case or series of steps
4. The Babushka version in use
5. Operating system
6. Redis version
7. Redis cluster information, cluster topology, number of shards, number of replicas, used data types
8. Any modifications you've made that are relevant to the issue
9. Anything unusual about your environment or deployment
10. Log files


## Contributing

GitHub is a platform for collaborative coding. If you're interested in writing code, we encourage you to contribute by submitting pull requests from forked copies of this repository. Additionally, please consider creating GitHub issues for reporting bugs and suggesting new features. Feel free to comment on issues that interest. For more info see [Contributing](./CONTRIBUTING.md).

## License
* [Apache License 2.0](./LICENSE)
