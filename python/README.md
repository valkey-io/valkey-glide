# Getting Started - Python Wrapper

## Installation and Setup
The beta release of Babushka is only available by building from source, and only supports Unix based systems.

### System Requirements

Unix based system.

### Software Dependencies

-   Python3.8 or higher 
-   python3 virtualenv
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler)
-   openssl
-   openssl-dev
-   rustup



### Installation and Setup Instructions

#### Dependencies installation for Ubuntu:
```
sudo apt update -y
sudo apt install -y python3 python3-venv git gcc pkg-config protobuf-compiler openssl libssl-dev
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Dependencies installation for CentOS:
``` 
sudo yum update -y
sudo yum install -y python3 git gcc pkgconfig protobuf-compiler openssl openssl-devel
pip3 install virtualenv
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Dependencies installation for MacOS:
```
brew update -y 
brew install python3 git gcc pkgconfig protobuf openssl 
pip3 install virtualenv
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Building from source:
Before starting this step, make sure you've installed all software requirments. 
1. Clone the repository:
    ```
    git clone https://github.com/aws/babushka.git
    cd babushka
    ```
2. Initialize git submodule:
    ```
    git submodule update --init --recursive
    ```
3. Generate protobuf files:
    ```
    BABUSHKA_ROOT_FOLDER_PATH=.
    protoc -Iprotobuf=${BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/ --python_out=${BABUSHKA_ROOT_FOLDER_PATH}/python/python/pybushka ${BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/*.proto
    ```
4. Create a virtual environment:
    ```
    cd python
    python3 -m venv .env
    ```
5. Activate the virtual environment:
    ```
    source .env/bin/activate
    ```
6. Install requirements:
    ```
    pip install -r requirements.txt
    ```
7. Build the Python-Rust code in release mode:
    ```
    maturin develop --release
    ```
8. Run tests:
    7.1.  First, ensure that you have installed redis-server and redis-cli on your host. You can find the Redis installation guide at the following link: [Redis Installation Guide](https://redis.io/docs/install/install-redis/install-redis-on-linux/).
    7.2. Ensure that you have activated the virtual environment created in step 4, and then execute the following command from the python folder:

    ```
    pytest --asyncio-mode=auto
    ```

### Basic Example

#### Cluster Redis:

```python:
    from pybushka import (
        AddressInfo,
        AllNodes,
        ClusterClientConfiguration,
        Logger as ClientLogger,
        LogLevel,
        RedisClusterClient,
        # ReadFrom
        # RedisCredentials
    )
    # Configure the client's logger
    ClientLogger.set_logger_config(LogLevel.INFO, "file_name")
    # Add address of any node, and the client will discover the remaining nodes in the cluster.
    host="example-configuration-endpoint.use1.cache.amazonaws.com"
    port=6379
    addresses = [AddressInfo(host, port)]
    # Check `ClusterClientConfiguration` for additional options.
    config = ClusterClientConfiguration(
        addresses=addresses,
        # use_tls=True
        # read_from=ReadFrom.PREFER_REPLICA
        # credentials=RedisCredentials("password", "username")
    )
    client = await RedisClusterClient.create(config)
    # Send SET and GET
    set_response = await client.set("foo", "bar")
    print(f"Set response is = {set_response}")
    get_response = await client.get("foo")
    print(f"Get response is = {get_response}")
    # Send PING to all primaries (according to Redis's PING request_policy)
    pong = await client.custom_command(["PING"])
    print(f"PONG response is = {pong}")
    # Send INFO REPLICATION with routing option to all nodes
    info_repl_resps = await client.custom_command(["INFO", "REPLICATION"], AllNodes())
    print(f"INFO REPLICATION responses to all nodes are = {info_repl_resps}")
```

#### Standalone Redis:

```python:
    from pybushka import (
        AddressInfo,
        AllNodes,
        RedisClientConfiguration,
        Logger as ClientLogger,
        LogLevel,
        RedisClient,
        # ReadFrom
        # RedisCredentials
    )
    # Configure the client's logger
    ClientLogger.set_logger_config(LogLevel.INFO, "file_name")
    # When in Redis is in standalone mode, add address of the primary node,
    # and any replicas you'd like to be able to read from.
    primary="primary-endpoint.use1.cache.amazonaws.com"
    replica="reader-endpoint.use1.cache.amazonaws.com"
    port=6379
    addresses = [AddressInfo(primary, port), AddressInfo(replica, port)]
    # Check `RedisClientConfiguration` for additional options.
    config = RedisClientConfiguration(
        addresses=addresses,
        # use_tls=True,
        # read_from=ReadFrom.PREFER_REPLICA
        # credentials=RedisCredentials("password", "username"),
        # database_id=1,

    )
    client = await RedisClient.create(config)

    # Send SET and GET
    set_response = await client.set("foo", "bar")
    print(f"Set response is = {set_response}")
    get_response = await client.get("foo")
    print(f"Get response is = {get_response}")
    # Send PING to the primary node
    pong = await client.custom_command(["PING"])
    print(f"PONG response is = {pong}")
```

Configuration  Standard/Common

Number of connections
Timeouts
Etc.

### Advanced Topis
Transactions
PubSub
Pipelines
Etc.
