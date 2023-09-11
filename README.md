# babushka

Babushka (temporary name, final name pending) is a collection of open source Redis clients in various managed languages, based around shared logic written in Rust. We call the clients "wrappers", and the shared logic "core".

## Supported languages

-   [node](./node/README.md).
-   [python](./python/README.md)

## Folder structure

-   babushka-core - [the Rust core](./babushka-core/README.md). This code is compiled into binaries which are used by the various wrappers.
-   benchmarks - [benchmarks for the wrappers](./benchmarks/README.md), and for the current leading Redis client in each language.
-   csharp - [the C# wrapper](./csharp/README.md). currently not in a usable state.
-   examples - [Sample applications](./examples/), in various languages, demonstrating usage of the client.
-   logger_core - [the logger we use](./logger_core/).
-   node - [the NodeJS wrapper](./node/README.md).
-   python - [the Python3 wrapper](./python/README.md)
-   submodules - git submodules.
-   utils - Utility scripts used in testing, building, and deploying.

## development pre-requirements

-   GCC
-   pkg-config
-   protobuf-compiler (protoc) >= V3
-   openssl
-   libssl-dev // for amazon-linux install openssl-devel
-   python3

Installation for ubuntu:
`sudo apt install -y gcc pkg-config protobuf-compiler openssl libssl-dev python3`

### git submodule

run `git submodule update --init --recursive` on first usage, and on any update to the redis-rs fork.

### rustup

https://rustup.rs/

```
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

after the instalation will show-up in the terminal steps to add rustup to the path - do it.

### redis-server

This is required for running tests and local benchmarks.

https://redis.io/docs/getting-started/

```
sudo yum -y install gcc make wget
cd /usr/local/src
sudo wget http://download.redis.io/releases/redis-{0}.tar.gz
sudo tar xzf redis-{0}.tar.gz
sudo rm redis-{0}.tar.gz
cd redis-{0}
sudo make distclean
sudo make BUILD_TLS=yes
sudo mkdir /etc/redis
sudo cp src/redis-server src/redis-cli /usr/local/bin
```

change {0} to the version you want, e.g. 7.0.12. version names are available here: http://download.redis.io/releases/
recommended version - 7.0.12 ATM

### node 16 (or newer)

This is required for the NodeJS wrapper, and for running benchmarks.

```
curl -s https://deb.nodesource.com/setup_16.x | sudo bash
apt-get install nodejs npm
npm i -g npm@8
```

## Recommended VSCode extensions

[GitLens](https://marketplace.visualstudio.com/items?itemName=eamodio.gitlens)
