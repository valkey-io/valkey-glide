# Getting Started - Node Wrapper

## System Requirements

The beta release of GLIDE for Redis was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## NodeJS supported version
Node.js 16.20 or higher.
> Note: Currently, we only support npm major version 8. f you have a later version installed, you can downgrade it with `npm i -g npm@8`.

## Installation and Setup

### Installing via Package Manager (npm)

To install GLIDE for Redis using `npm`, follow these steps:

1. Open your terminal.
2. Execute the command below:
   ```bash
   $ npm install @aws/glide-for-redis
   ```
3. After installation, confirm the client is installed by running:
    ```bash
    $ npm list
    myApp@ /home/ubuntu/myApp
    └── @aws/glide-for-redis@0.1.0
    ```

### Build from source

#### Prerequisites

Software Dependencies
-   npm v8
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
sudo apt install -y nodejs npm git gcc pkg-config protobuf-compiler openssl libssl-dev
npm i -g npm@8
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for CentOS**
``` bash
sudo yum update -y
sudo yum install -y nodejs git gcc pkgconfig protobuf-compiler openssl openssl-devel gettext
npm i -g npm@8
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for MacOS**
```bash
brew update
brew install nodejs git gcc pkgconfig protobuf openssl 
npm i -g npm@8
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Building and installation steps
Before starting this step, make sure you've installed all software requirments. 
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
3. Install all node dependencies:
    ```bash
    cd node
    npm i
    cd rust-client
    npm i
    cd ..
    ```
4. Build the Node wrapper: 
    Choose a build option from the following and run it from the `node` folder:
    1. Build in release mode, stripped from all debug symbols (optimized and minimized binary size):
        ```bash
        npm run build:release
        ```

    2. Build in release mode with debug symbols (optimized but large binary size):
        ```bash
        npm run build:benchmark
        ```

    3. For testing purposes, you can execute an unoptimized but fast build using:
        ```bash
        npm run build
        ```
    Once building completed, you'll find the compiled JavaScript code in the `./build-ts` folder.
5. Run tests:
    1. Ensure that you have installed redis-server and redis-cli on your host. You can find the Redis installation guide at the following link: [Redis Installation Guide](https://redis.io/docs/install/install-redis/install-redis-on-linux/).
    2. Execute the following command from the node folder:
        ```bash
        npm test
        ```
6. Integrating the built GLIDE package into your project:
    Add the package to your project using the folder path with the command `npm install <path to GLIDE>/node`.

## Basic Examples

#### Cluster Redis:

```node
import { RedisClusterClient } from "glide-for-redis";

const addresses = [
    {
        host: "redis.example.com",
        port: 6379,
    },
];
const client = await RedisClusterClient.createClient({
    addresses: addresses,
});
await client.set("foo", "bar");
const value = await client.get("foo");
client.close();
```


#### Standalone Redis:

```node
import { RedisClient } from "glide-for-redis";

const addresses = [
    {
        host: "redis_primary.example.com",
        port: 6379,
    },
    {
        host: "redis_replica.example.com",
        port: 6379,
    },
];
const client = await RedisClient.createClient({
    addresses: addresses,
});
await client.set("foo", "bar");
const value = await client.get("foo");
client.close();
```
## Documenation

Visit our [wiki](https://github.com/aws/glide-for-redis/wiki/NodeJS-wrapper) for examples and further details on TLS, Read strategy, Timeouts and various other configurations.
