# Summary - Java Wrapper

This module contains a Java-client wrapper that connects to the `Babushka`-rust-client.  The rust client connects to 
redis, while this wrapper provides Java-language binding.  The objective of this wrapper is to provide a thin-wrapper
language api to enhance performance and limit cpu cycles at scale. 

## Organization

The Java client (javabushka) contains the following parts:

1. A Java client (lib folder): wrapper to rust-client
2. An examples script: to sanity test javabushka and similar java-clients against a redis host
3. A benchmark app: to performance benchmark test javabushka and similar java-clients against a redis host

## Building

You can assemble the Java clients benchmarks by compiling using `./gradlew build`. 

## Benchmarks

You can run benchmarks using `./gradlew run`.  You can set arguments using the args flag like: 

```shell
./gradle run --args="--clients lettuce"
```

The following arguments are accepted: 
* `configuration`: Release or Debug configuration
* `resultsFile`: the results output file
* `concurrentTasks`: Number of concurrent tasks 
* `clients`: one of: all|jedis|lettuce|babushka
* `clientCount`: Client count
* `host`: redis server host url 
* `port`: redis server port number
* `tls`: redis TLS configured

### Troubleshooting

* If you're unable to connect to redis (such as timeout), check your port or the TLS flag
* Only server-side certificates are supported by the TLS configured redis
