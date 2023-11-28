# Getting Started - Java Wrapper

## Wrapper Status

The Java client is currently a work in progress and offers no guarantees.  The client must be built from source, and 
we assume no responsibilities or performance guarantees.  

## Installation and Setup

### Install from Gradle

At the moment, the Java client must be build from source. 

### Build from source

Software Dependencies:

- JDK 11+
- git
- protoc (protobuf compiler)
- Rust

#### Prerequisites

**Dependencies installation for Ubuntu**
```bash
sudo apt update -y
sudo apt install -y protobuf-compiler openjdk-11-jdk openssl gcc
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies for MacOS**

Ensure that you have a minimum Java version of JDK 11 installed on your system:
```bash
 $ echo $JAVA_HOME
/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home

$ java -version
 openjdk version "11.0.1" 2018-10-16
 OpenJDK Runtime Environment 18.9 (build 11.0.1+13)
 OpenJDK 64-Bit Server VM 18.9 (build 11.0.1+13, mixed mode)
```

#### Building and installation steps
The Java client is currently a work in progress and offers no guarantees.  Users should build at their own risk.      

Before starting this step, make sure you've installed all software requirements.
1. Clone the repository:
```bash
VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
git clone --branch ${VERSION} https://github.com/aws/babushka.git
cd babushka
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
* `./gradlew :client:spotlessCheck` to check for codestyle issues
* `./gradlew :client:spotlessApply` to apply codestyle recommendations
* `./gradlew :benchmarks:run` to run performance benchmarks

## Basic Examples

### Standalone Redis:

```java
import javababushka.Client;
import javababushka.Client.SingleResponse;

Client client = new Client();

SingleResponse connect = client.asyncConnectToRedis("localhost", 6379);
connect.await().isSuccess();

SingleResponse set = client.asyncSet("key", "foobar");
set.await().isSuccess();

SingleResponse get = client.asyncGet("key");
get.await().getValue() == "foobar";
```

### Benchmarks

You can run benchmarks using `./gradlew run`. You can set arguments using the args flag like:

```shell
./gradlew run --args="-help"
./gradlew run --args="-resultsFile=output -dataSize \"100 1000\" -concurrentTasks \"10 100\" -clients all -host localhost -port 6279 -clientCount \"1 5\" -tls"
```
