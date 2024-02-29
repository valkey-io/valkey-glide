# C# wrapper

The C# wrapper is currently not in a usable state.

# GLIDE for Redis

General Language Independent Driver for the Enterprise (GLIDE) for Redis, is an AWS-sponsored, open-source Redis client. GLIDE for Redis works with any Redis distribution that adheres to the Redis Serialization Protocol (RESP) specification, including open-source Redis, Amazon ElastiCache for Redis, and Amazon MemoryDB for Redis.
Strategic, mission-critical Redis-based applications have requirements for security, optimized performance, minimal downtime, and observability. GLIDE for Redis is designed to provide a client experience that helps meet these objectives. It is sponsored and supported by AWS, and comes pre-configured with best practices learned from over a decade of operating Redis-compatible services used by hundreds of thousands of customers. To help ensure consistency in development and operations, GLIDE for Redis is implemented using a core driver framework, written in Rust, with extensions made available for each supported programming language. This design ensures that updates easily propagate to each language and reduces overall complexity.

## Supported Redis Versions

GLIDE for Redis is API-compatible with open source Redis version 6 and 7.

## Current Status

We've made GLIDE for Redis an open-source project, and are releasing it in Preview to the community to gather feedback, and actively collaborate on the project roadmap. We welcome questions and contributions from all Redis stakeholders.
This preview release is recommended for testing purposes only.

# Getting Started - C# Wrapper

## .net sdk supported version

.net 6.0 or higher.

## Basic Example

```csharp

using Glide;

AsyncClient glideClient = new (host, PORT, useTLS);
await glideClient.SetAsync("foo", "bar");
string? value = await glideClient.GetAsync("foo");
glideClient.Dispose();
```

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](DEVELOPER.md#build-from-source) file.
