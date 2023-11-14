# Getting Started - Node Wrapper

## System Requirements

The beta release of Babushka was tested on Intel x86_64 using Ubuntu 22.04.1, Amazon Linux 2023 (AL2023), and macOS 12.7.

## NodeJS supported version
Node.js 16.20 or higher.
> Note: Currently, we only support npm major version 8. f you have a later version installed, you can downgrade it with `npm i -g npm@8`.

## Installation and Setup

### Install from package manager
At the moment, the beta release of Babushka is only available by building from source.

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
sudo yum install -y nodejs git gcc pkgconfig protobuf-compiler openssl openssl-devel
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
    git clone --branch ${VERSION} https://github.com/aws/babushka.git
    cd babushka
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
    ```bash
    npm test
    ```

## Integrating the Babushka Package into Your Project

Before adding the Babushka package into your application, ensure you follow the build steps outlined in "[Build the Node wrapper](#Building-and-installation-steps)".

Currently, Babushka is not available on npm. Therefore, you'll need to build this repository to your device and add the package using the folder path with the command `npm install <path to Babushka>/node`.

## Basic Examples

#### Cluster Redis:

```node
import { RedisClusterClient } from "babushka-rs";

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
client.dispose();
```


#### Standalone Redis:

```node
import { RedisClient } from "babushka-rs";

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
client.dispose();
```
