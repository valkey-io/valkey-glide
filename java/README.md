# Getting Started - Java Wrapper

## System Requirements

The beta release of Babushka was tested on Intel x86_64 using Ubuntu 22.04.1, and amd64 using macOS 14.1.

## Java supported version

On Linux, tested on Temurin JDK version 11.0.21, and 17.0.9. 
On MacOs, tested on Amazon Corretto version 11.0.21, and 17.0.3. 

## Installation and Setup

### Install from Gradle

At the moment, the beta release of Babushka mush be build from source. 

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
Before starting this step, make sure you've installed all software requirments.
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
   $ cd java/
   $ ./gradlew :client:protobuf
   BUILD SUCCESSFUL
    ```
4. Build the client library:
    ```bash
   $ cd java/
   $ ./gradlew :client:build
   BUILD SUCCESSFUL
    ```
5. Run tests:
   ```bash
   $ cd java/
   $ ./gradlew :client:test
   BUILD SUCCESSFUL
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
import response.ResponseOuterClass.Response;

public class RedisClient {

   Client testClient = new Client();

   public RedisClient() {
      testClient.asyncConnectToRedis("localhost", 6379, false, false).get(); // expect Ok
      Response setResponse = testClient.asyncSet("name", "johnsmith").get(); // expect Ok
      Response getResponse = testClient.asyncGet("name").get(); // expect "johnsmith"
   }
  
}
```

### Benchmarks

You can run benchmarks using `./gradlew run`. You can set arguments using the args flag like:

```shell
./gradlew run --args="-help"
./gradlew run --args="-resultsFile=output -dataSize \"100 1000\" -concurrentTasks \"10 100\" -clients all -host localhost -port 6279 -clientCount \"1 5\" -tls"
```

### Troubleshooting

* Connection Timeout: 
  * If you're unable to connect to redis, check that you are connecting to the correct host, port, and TLS configuration.
* Only server-side certificates are supported by the TLS configured redis.

