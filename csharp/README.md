# C# wrapper

The C# wrapper is currently not in a usable state.

# Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services. Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications. It is sponsored and supported by AWS, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions. This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

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
