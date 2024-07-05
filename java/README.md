# GLIDE for Valkey

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an AWS-sponsored, open-source Valkey client that includes support for open-source Redis 6.2 to 7.2.
Valkey GLIDE works with any distribution that adheres to the Redis Serialization Protocol (RESP) specification, including Amazon ElastiCache, and Amazon MemoryDB.
Strategic mission-critical applications have requirements for security, optimized performance, minimal downtime, and observability.
Valkey GLIDE is designed to provide a client experience that helps meet these objectives. It is sponsored and supported by AWS, and comes pre-configured with best practices learned from over a decade of operating RESP-compatible services used by hundreds of thousands of customers.
To help ensure consistency in development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with extensions made available for each supported programming language.
This design ensures that updates easily propagate to each language and reduces overall complexity. In this Preview release, Valkey GLIDE is available for Python and Java, with support for Javascript (Node.js) actively under development.

# Getting Started - Java Wrapper

## System Requirements

The release of GLIDE for Valkey was tested on the following platforms:
Linux:
- Ubuntu 22.04.1 (x86_64)
- Amazon Linux 2023 (AL2023) (x86_64)

macOS:
- macOS 12.7 (Apple silicon)


## Layout of Java code
The Java client contains the following parts:

1. `src`: Rust dynamic library FFI to integrate with [GLIDE core library](../glide-core/README.md).
2. `client`: A Java-wrapper around the GLIDE core rust library and unit tests for it.
3. `examples`: An example app to test the client against a Valkey localhost.
4. `benchmark`: A dedicated benchmarking tool designed to evaluate and compare the performance of GLIDE for Valkey and other Java clients.
5. `integTest`: An integration test sub-project for API and E2E testing.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/aws/glide-for-redis/blob/main/README.md#supported-engine-versions) for details.

## Installation and Setup

#### Prerequisites

For developers, please refer to Java's [DEVELOPER.md](./DEVELOPER.md) for further instruction on how to set up your development environment.

**Java Requirements**

Minimum requirements: JDK 11 or later. Ensure that you have a minimum Java version of JDK 11 installed on your system:

```bash
echo $JAVA_HOME
java -version
```

### Adding the client to your project

Refer to https://central.sonatype.com/artifact/software.amazon.glide/glide-for-redis.
Once set up, you can run the basic examples.

Additionally, consider installing the Gradle plugin, [OS Detector](https://github.com/google/osdetector-gradle-plugin) to help you determine what classifier to use.

## Classifiers
There are 4 types of classifiers for GLIDE for Valkey which are
``
osx-aarch_64
osx-x86_64
linux-aarch_64
linux-x86_64
``

Gradle:
- Copy the snippet and paste it in the `build.gradle` dependencies section.
- **IMPORTANT** must include a `classifier` to specify your platform.
```groovy

// osx-aarch_64
dependencies {
    implementation group: 'software.amazon.glide', name: 'glide-for-redis', version: '0.4.3', classifier: 'osx-aarch_64'
}

// osx-x86_64
dependencies {
    implementation group: 'software.amazon.glide', name: 'glide-for-redis', version: '0.4.3', classifier: 'osx-x86_64'
}

// linux-aarch_64
dependencies {
    implementation group: 'software.amazon.glide', name: 'glide-for-redis', version: '0.4.3', classifier: 'linux-aarch_64'
}

// linux-x86_64
dependencies {
    implementation group: 'software.amazon.glide', name: 'glide-for-redis', version: '0.4.3', classifier: 'linux-x86_64'
}
```

Maven:
- **IMPORTANT** must include a `classifier`. Please use this dependency block and add it to the pom.xml file.
```xml

<!--osx-aarch_64-->
<dependency>
   <groupId>software.amazon.glide</groupId>
   <artifactId>glide-for-redis</artifactId>
   <classifier>osx-aarch_64</classifier>
   <version><LATEST></version>
</dependency>

<!--osx-x86_64-->
<dependency>
   <groupId>software.amazon.glide</groupId>
   <artifactId>glide-for-redis</artifactId>
   <classifier>osx-x86_64</classifier>
   <version><LATEST></version>
</dependency>

<!--linux-aarch_64-->
<dependency>
   <groupId>software.amazon.glide</groupId>
   <artifactId>glide-for-redis</artifactId>
   <classifier>linux-aarch_64</classifier>
   <version><LATEST></version>
</dependency>

<!--linux-x86_64-->
<dependency>
   <groupId>software.amazon.glide</groupId>
   <artifactId>glide-for-redis</artifactId>
   <classifier>linux-x86_64</classifier>
   <version><LATEST></version>
</dependency>
```

## Setting up the Java module

To use Glide for Valkey in a Java project with modules, include a module-info.java in your project.

For example, if your program is called `App`, you can follow this path
```java
app/src/main/java/module-info.java
```

and inside the module it will specifically require the line
`requires glide.api;`

For example, if your project has a module called playground, it would look like this
```java
module playground {
    requires glide.api;
}
```

## Basic Examples

### Standalone Valkey:

```java
// You can run this example code in Main.java.
import glide.api.GlideClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.GlideClientConfiguration;
import java.util.concurrent.ExecutionException;

import static glide.api.models.GlideString.gs;

public class Main {

    public static void main(String[] args) {
        runGlideExamples();
    }

    private static void runGlideExamples() {
        String host = "localhost";
        Integer port = 6379;
        boolean useSsl = false;

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .useTLS(useSsl)
                        .build();

        try {
            Glide client = GlideClient.CreateClient(config).get();

            System.out.println("PING: " + client.ping(gs("PING")).get());
            System.out.println("PING(found you): " + client.ping( gs("found you")).get());

            System.out.println("SET(apples, oranges): " + client.set(gs("apples"), gs("oranges")).get());
            System.out.println("GET(apples): " + client.get(gs("apples")).get());

        } catch (ExecutionException | InterruptedException e) {
            System.out.println("Glide example failed with an exception: ");
            e.printStackTrace();
        }
    }
}
```

### Cluster Valkey:
```java
// You can run this example code in Main.java.
import glide.api.GlideClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration;

import java.util.concurrent.ExecutionException;

import static glide.api.models.GlideString.gs;

public class Main {

    public static void main(String[] args) {
        runGlideExamples();
    }

    private static void runGlideExamples() {
        String host = "localhost";
        Integer port1 = 7001;
        Integer port2 = 7002;
        Integer port3 = 7003;
        Integer port4 = 7004;
        Integer port5 = 7005;
        Integer port6 = 7006;
        boolean useSsl = false;

        RedisClusterClientConfiguration config =
                RedisClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port1).port(port2).port(port3).port(port4).port(port5).port(port6).build())
                        .useTLS(useSsl)
                        .build();

        try {
            GlideClusterClient client = GlideClusterClient.CreateClient(config).get();

            System.out.println("PING: " + client.ping(gs("PING")).get());
            System.out.println("PING(found you): " + client.ping( gs("found you")).get());

            System.out.println("SET(apples, oranges): " + client.set(gs("apples"), gs("oranges")).get());
            System.out.println("GET(apples): " + client.get(gs("apples")).get());

        } catch (ExecutionException | InterruptedException e) {
            System.out.println("Glide example failed with an exception: ");
            e.printStackTrace();
        }
    }
}
```

### Accessing tests
You can access [unit tests](./java/client/src/test/java/glide/api) and [integration tests](./java/integTest/src/test/java/glide) developed for the Valkey commands here.

### Benchmarks

You can run benchmarks using `./gradlew run`. You can set arguments using the args flag like:

```shell
./gradlew run --args="--help"
./gradlew run --args="--resultsFile=output --dataSize \"100 1000\" --concurrentTasks \"10 100\" --clients all --host localhost --port 6279 --clientCount \"1 5\" --tls"
```

The following arguments are accepted:
* `resultsFile`: the results output file
* `concurrentTasks`: number of concurrent tasks
* `clients`: one of: all|jedis|lettuce|glide
* `clientCount`: client count
* `host`: Valkey server host url
* `port`: Valkey server port number
* `tls`: Valkey TLS configured
