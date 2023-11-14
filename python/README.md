# Getting Started - Python Wrapper

## System Requirements

The beta release of Babushka was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## Python supported version
Python 3.8 or higher.

## Installation and Setup

### Install from package manager
At the moment, the beta release of Babushka is only available by building from source.

### Build from source

#### Prerequisites

Software Dependencies

-   python3 virtualenv
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler)
-   openssl
-   openssl-dev
-   rustup

**Dependencies installation for Ubuntu**
```bash
sudo apt update -y
sudo apt install -y python3 python3-venv git gcc pkg-config protobuf-compiler openssl libssl-dev
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for CentOS**
```bash 
sudo yum update -y
sudo yum install -y python3 git gcc pkgconfig protobuf-compiler openssl openssl-devel
pip3 install virtualenv
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for MacOS**
```bash
brew update
brew install python3 git gcc pkgconfig protobuf openssl 
pip3 install virtualenv
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
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
    BABUSHKA_ROOT_FOLDER_PATH=.
    protoc -Iprotobuf=${BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/ --python_out=${BABUSHKA_ROOT_FOLDER_PATH}/python/python/pybushka ${BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/*.proto
    ```
4. Create a virtual environment:
    ```bash
    cd python
    python3 -m venv .env
    ```
5. Activate the virtual environment:
    ```bash
    source .env/bin/activate
    ```
6. Install requirements:
    ```bash
    pip install -r requirements.txt
    ```
7. Build the Python wrapper in release mode:
    ```
    maturin develop --release --strip
    ```
     > **Note:** To build the wrapper binary with debug symbols remove the --strip flag.
8. Run tests:
    1. Ensure that you have installed redis-server and redis-cli on your host. You can find the Redis installation guide at the following link: [Redis Installation Guide](https://redis.io/docs/install/install-redis/install-redis-on-linux/).
    2. Validate the activation of the virtual environment from step 4 by ensuring its name (`.env`) is displayed next to your command prompt. 
    3. Execute the following command from the python folder:
        ```bash
        pytest --asyncio-mode=auto
        ```

## Basic Examples

#### Cluster Redis:

```python:
>>> from pybushka import (
...     NodeAddress,
...     ClusterClientConfiguration,
...     RedisClusterClient,
... )
>>> addresses = [NodeAddress("redis.example.com", 6379)]
>>> config = ClusterClientConfiguration(
...     addresses=addresses
... )
>>> client = await RedisClusterClient.create(config)
>>> await client.set("foo", "bar")
'OK'
>>> await client.get("foo")
'bar'
```

#### Standalone Redis:

```python:
>>> from pybushka import (
...     NodeAddress,
...     RedisClientConfiguration,
...     RedisClient,
... )
>>> addresses = [NodeAddress("redis_primary.example.com", 6379), NodeAddress("redis_replica.example.com", 6379)]
>>> config = RedisClientConfiguration(
...     addresses=addresses
... )
>>> client = await RedisClient.create(config)
>>> await client.set("foo", "bar")
'OK'
>>> await client.get("foo")
'bar'
```
