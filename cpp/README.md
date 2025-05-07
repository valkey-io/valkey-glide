# C++ wrapper

The C++ wrapper is currently in its early stages of development.

# Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, and it supports all Valkey commands.
Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services.
Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications.
It is sponsored and supported by AWS, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers.
To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions.
This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

## Current Status

# Getting Started - C++ Wrapper

## C++ supported version

The minimum supported version is C++17.
> We are actively working to ensure compatibility with C++11.

## Basic Example

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](DEVELOPER.md) file.

## Todo

- [ ] C++11 support.
- [ ] Better CMakeLists structure (importable).
- - [ ] support adding as subdirectory.
- - [ ] https://cmake.org/cmake/help/latest/module/FetchContent.html#examples
- [ ] Add unittests.
- [ ] Implement GitHub Action for tests, memory checks, and documentation generation.
- - [ ] Should be triggered as soon as the glide-core got changed.
- - [ ] https://github.com/valkey-io/valkey/blame/unstable/cmake/Modules/ValkeySetup.cmake#L278
- [ ] Cluster_manager
- [ ] Expose rust logging
