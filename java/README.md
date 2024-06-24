# GLIDE for Redis

General Language Independent Driver for the Enterprise (GLIDE) for Redis, is an AWS-sponsored, open-source Redis client. GLIDE for Redis works with any Redis distribution that adheres to the Redis Serialization
Protocol (RESP) specification, including open-source Redis, Amazon ElastiCache for Redis, and Amazon MemoryDB for Redis.
Strategic, mission-critical Redis-based applications have requirements for security, optimized performance, minimal downtime, and observability. GLIDE for Redis is designed to provide a client experience that helps meet these objectives.
It is sponsored and supported by AWS, and comes pre-configured with best practices learned from over a decade of operating Redis-compatible services used by hundreds of thousands of customers.
To help ensure consistency in development and operations, GLIDE for Redis is implemented using a core driver framework, written in Rust, with extensions made available for each supported programming language. This design ensures that updates easily propagate to each language and reduces overall complexity.
In this release, GLIDE for Redis is available for Python, Javascript (Node.js), and Java.

## Supported Redis Versions

GLIDE for Redis is API-compatible with open source Redis version 6 and 7.

## Current Status

We've made GLIDE for Redis an open-source project, and are releasing it in Preview to the community to gather feedback, and actively collaborate on the project roadmap. We welcome questions and contributions from all Redis stakeholders.
This preview release is recommended for testing purposes only.

# Getting Started - Java Wrapper

## System Requirements

The beta release of GLIDE for Redis was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## Java supported version
JDK 11+.

The Java client contains the following parts:

1. `src`: Rust dynamic library FFI to integrate with [GLIDE core library](https://github.com/aws/glide-for-redis/blob/main/glide-core/README.md).
2. `client`: A Java-wrapper around the [GLIDE core rust library](../glide-core/README.md) and unit tests for it.
3. `examples`: An examples app to test the client against a Redis localhost.
4. `benchmark`: A dedicated benchmarking tool designed to evaluate and compare the performance of GLIDE for Redis and other Java clients.
5. `integTest`: An integration test sub-project for API and E2E testing.

## Installation and Setup

### Install from Gradle

At the moment, the Java client must be built from source.

### Build from source

Software Dependencies:

- JDK 11+
- git
- protoc (protobuf compiler)
- Rust

Please also consider installing the following packages to build [GLIDE core rust library](../glide-core/README.md):

- GCC
- pkg-config
- openssl
- openssl-dev

#### Prerequisites

**Protoc installation**

Download a binary matching your system from the [official release page](https://github.com/protocolbuffers/protobuf/releases) and make it accessible in your $PATH by moving it or creating a symlink.
For example, on Linux you can copy it to `/usr/bin`:

```bash
sudo cp protoc /usr/bin/
```

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y openjdk-11-jdk openssl gcc
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for MacOS**

```bash
brew update
brew install git gcc pkgconfig openssl openjdk@11
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Java version check**

Ensure that you have a minimum Java version of JDK 11 installed on your system:

```bash
echo $JAVA_HOME
java -version
```

#### Building and installation steps
The Java client is currently a work in progress and offers no guarantees. Users should build at their own risk.

Before starting this step, make sure you've installed all software requirements.
1. Clone the repository:
```bash
VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
git clone --branch ${VERSION} https://github.com/aws/glide-for-redis.git
cd glide-for-redis
```
2. Initialize git submodule:
```bash
git submodule update --init --recursive
```
3. Generate protobuf files:
```bash
cd java/
./gradlew :client:protobuf
```
4. Build the client library:
```bash
cd java/
./gradlew :client:build
```
5. Run tests:
```bash
cd java/
$ ./gradlew :client:test
```

Other useful gradle developer commands:
* `./gradlew :client:test` to run client unit tests
* `./gradlew :integTest:test` to run client examples
* `./gradlew spotlessCheck` to check for codestyle issues
* `./gradlew spotlessApply` to apply codestyle recommendations
* `./gradlew :examples:run` to run client examples (make sure you have a running redis on port `6379`)
* `./gradlew :benchmarks:run` to run performance benchmarks

## Basic Examples

### Standalone Redis:

```java
import glide.api.RedisClient;

RedisClient client = RedisClient.CreateClient().get();

CompletableFuture<String> setResponse = client.set("key", "foobar");
assert setResponse.get() == "OK" : "Failed on client.set("key", "foobar") request";

CompletableFuture<String> getResponse = client.get("key");
assert getResponse.get() == "foobar" : "Failed on client.get("key") request";
```

### Cluster Redis:
```java
import glide.api.RedisClusterClient;

String host = "localhost";
Integer port = 6379;
boolean useSsl = false;

RedisClientConfiguration config =
        RedisClientConfiguration.builder()
                .address(NodeAddress.builder().host(host).port(port).build())
                .useTLS(useSsl)
                .build();

RedisClusterClient client = RedisClusterClient.CreateClient(config).get();

CompletableFuture<String> setResponse = client.set("key", "foobar");
assert setResponse.get() == "OK" : "Failed on client.set("key", "foobar") request";

CompletableFuture<String> getResponse = client.get("key");
assert getResponse.get() == "foobar" : "Failed on client.get("key") request";
```

### Benchmarks

You can run benchmarks using `./gradlew run`. You can set arguments using the args flag like:

```shell
./gradlew run --args="--help"
./gradlew run --args="--resultsFile=output --dataSize \"100 1000\" --concurrentTasks \"10 100\" --clients all --host localhost --port 6279 --clientCount \"1 5\" --tls"
```

The following arguments are accepted:
* `resultsFile`: the results output file
* `concurrentTasks`: Number of concurrent tasks
* `clients`: one of: all|jedis|lettuce|glide
* `clientCount`: Client count
* `host`: redis server host url
* `port`: redis server port number
* `tls`: redis TLS configured
