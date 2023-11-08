## Client Initialization

Babushka provides support for both [Redis Cluster](https://github.com/aws/babushka/wiki/Python-wrapper#redis-cluster) and [Redis Standalone](https://github.com/aws/babushka/wiki/Python-wrapper#redis-standalone) and configurations. Please refer to the relevant section based on your specific setup.

### Redis Cluster

Babushka supports [Redis Cluster](https://redis.io/docs/reference/cluster-spec) deployments, where the Redis database is partitioned across multiple primary Redis shards, with each shard being represented by a primary node and zero or more replica nodes.. 


To initialize a `RedisClusterClient`, you need to provide a `ClusterClientConfiguration` that includes the addresses of initial seed nodes. Babushka automatically discovers the entire cluster topology, eliminating the necessity of explicitly listing all cluster nodes.

#### **Connecting to a Cluster**

The `NodeAddress` class represents the host and port of a Redis node. The host can be either an IP address, a hostname, or a fully qualified domain name (FQDN).

#### Example - Connecting to a Redis cluster

```
addresses=[NodeAddress(host="redis.example.com", port=6379)]
client_config=ClusterClientConfiguration(addresses)

client=await RedisClusterClient.create(client_config)
```

#### Request Routing

In the Redis cluster, data is divided into slots, and each primary node within the cluster is responsible for specific slots. Babushka adheres to [Redis OSS guidelines](https://redis.io/docs/reference/command-tips/#request_policy) when determining the node(s) to which a command should be sent in clustering mode. 

For more details on the routing of specific commands, please refer to the documentation within the code.

#### Response Aggregation

When requests are dispatched to multiple shards in a cluster (as discussed in the Request routing section), the Redis client needs to aggregate the responses for a given command. Babushka follows [Redis OSS guidelines](https://redis.io/docs/reference/command-tips/#response_policy) for determining how to aggregate the responses from multiple shards within a cluster. 

To learn more about response aggregation for specific commands, consult the documentation embedded in the code.

#### Topology Updates

The cluster's topology can change over time. New nodes can be added or removed, and the primary node responsible for a specific slot may change. Babushka is designed to automatically rediscover the topology whenever Redis indicates a change in slot ownership. This ensures that the Babushka client stays in sync with the cluster's topology.

### Redis Standalone 

Babushka also supports Redis Standalone deployments, where the Redis database is hosted on a single primary node, optionally with replica nodes. To initialize a `RedisClient`  for a standalone Redis setup, you should create a `RedisClientConfiguration` that includes the addresses of all endpoints, both primary and replica nodes.

#### **Example - Connecting to a standalone Redis** 

```
addresses=[
    NodeAddress(host="redis_primary.example.com", port=6379),
    NodeAddress(host="redis_replica1.example.com", port=6379),
    NodeAddress(host="redis_replica2.example.com", port=6379)
  ]
client_config = RedisClientConfiguration(addresses)

client = await RedisClient.create(client_config)
```

## Redis commands
For comprehensive information on the supported commands and their corresponding parameters, we recommend referring to the documentation embedded within the codebase. This documentation will provide in-depth insights into the usage and options available for each command.

## Advanced Options / Usage

### Authentication

By default, when connecting to Redis, Babushka operates in an unauthenticated mode.

Babushka also offers support for an authenticated connection mode. 

In authenticated mode, you have the following options:

* Use both a username and password, which is recommended and configured through [ACLs](https://redis.io/docs/management/security/acl) on the Redis server.
* Use a password only, which is applicable if Redis is configured with the [requirepass](https://redis.io/docs/management/security/#authentication) setting.

To provide the necessary authentication credentials to the client, you can use the `RedisCredentials` class.

#### Example - Connecting with Username and Password to a Redis Cluster

```
addresses = [NodeAddress(host="redis.example.com", port=6379)]
credentials = RedisCredentials("passwordA", "user1")
client_config = ClusterClientConfiguration(addresses, credentials=credentials)

client = await RedisClusterClient.create(client_config)
```


#### Example - Connecting with Username and Password to a Redis Standalone

```
addresses = [NodeAddress(host="redis.example.com", port=6379)]
credentials = RedisCredentials("passwordA", "user1")
client_config = RedisClientConfiguration(addresses, credentials=credentials)

client = await RedisClient.create(client_config)
```

### TLS

Babushka supports secure TLS connections to a Redis data store.

It's important to note that TLS support in Babushka relies on [rusttls](https://github.com/rustls/rustls). Currently, Babushka employs the default rustls settings with no option for customization.

#### Example - Connecting with TLS Mode Enabled to a Redis Cluster

```
addresses = [NodeAddress(host="redis.example.com", port=6379)]
client_config = ClusterClientConfiguration(addresses, use_tls=True)

client = await RedisClusterClient.create(client_config)
```
#### Example - Connecting with TLS Mode Enabled to a Redis Standalone

```
addresses = [NodeAddress(host="redis.example.com", port=6379)]
client_config = RedisClientConfiguration(addresses, use_tls=True)

client = await RedisClient.create(client_config)
```

### Read Strategy

By default, Babushka directs read commands to the primary node responsible for a specific slot. This ensures read-after-write consistency when reading from primaries. For applications that do not necessitate read-after-write consistency and seek to enhance read throughput, it is possible to route reads to replica nodes.


Babushka provides support for various read strategies, allowing you to choose the one that best fits your specific use case.

|Strategy	|Description	|
|---	|---	|
|`PRIMARY`	|Always read from primary, in order to get the freshest data	|
|`PREFER_REPLICA`	|Spread requests between all replicas in a round robin manner. If no replica is available, route the requests to the primary	|

#### Example - Use PREFER_REPLICA Read Strategy

```
addresses = [NodeAddress(host="redis.example.com", port=6379)]
client_config = ClusterClientConfiguration(addresses)

client = await RedisClusterClient.create(client_config, read_from=ReadFrom.PREFER_REPLICA)
await client.set("key1", "val1")
# get will read from one of the replicas
await client.get("key1")
```

### Timeouts and Reconnect Strategy

Babushka allows you to configure timeout settings and reconnect strategies. These configurations can be applied through the `ClusterClientConfiguration` and `RedisClientConfiguration` parameters.


|Configuration setting	|Description	|**Default value**	|
|---	|---	|---	|
|client_creation_timeout	|The specified duration, in milliseconds, represents the time the client should allow for its initialization, including tasks like connecting to the Redis node(s) and discovering the topology. If the client fails to complete its initialization within this defined time frame, an error will be generated. If no timeout value is explicitly set, a default value will be employed.	| 2500 milliseconds	|
|request_timeout	|This specified time duration, measured in milliseconds, represents the period during which the client will await the completion of a request. This time frame includes the process of sending the request, waiting for a response from the Redis node(s), and any necessary reconnection or retry attempts. If a pending request exceeds the specified timeout, it will trigger a timeout error. If no timeout value is explicitly set, a default value will be employed.	|250 milliseconds	|
|reconnect_strategy	|The reconnection strategy defines how and when reconnection attempts are made in the event of connection failures	|Exponential backoff	|


#### Example - Setting Increased Request Timeout for Long-Running Commands

```
addresses = [NodeAddress(host="redis.example.com", port=6379)]
client_config = ClusterClientConfiguration(addresses, request_timeout=500)

client = await RedisClusterClient.create(client_config)
```

Babushka employs backoff reconnection strategy that can be summarized as follows:

* The time between reconnection attempts grows exponentially, following the formula `rand(0 .. factor * (exponentBase ^ N))`, where N represents the number of consecutive failed attempts.
* Once a maximum value is reached, that value remains the time interval between subsequent retry attempts.
* The client will continue to make reconnection attempts until a successful reconnection occurs. 

This strategy provides an effective approach for handling disconnections and facilitates the re-establishment of a stable connection.

The backoff strategy can be applied through the `BackoffStrategy` parameters.
|Configuration setting	|Description	|**Default value**	|
|---	|---	|---	|
|num_of_retries	|The number of retry attempts that the client should perform when disconnected from the server, where the time between retries increases. Once the retries have reached the maximum value, the time between retries will remain constant until a reconnect attempt is successful.	|16	|
|factor	|The multiplier that will be applied to the waiting time between each retry.	|10	|
|exponent_base	|The exponent base configured for the strategy	|2	|


