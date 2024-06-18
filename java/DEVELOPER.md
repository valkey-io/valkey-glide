# Developer Guide

This document describes how to set up your development environment to build and test the GLIDE for Redis Java wrapper.

### Development Overview

The GLIDE for Redis Java wrapper consists of both Java and Rust code. Rust bindings for the Java Native Interface are implemented using [jni-rs](https://github.com/jni-rs/jni-rs), and the Java JAR is built using [Gradle](https://github.com/gradle/gradle). The Java and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.

### Build from source

#### Prerequisites

Software Dependencies

-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler) >= 26.1
-   openssl
-   openssl-dev
-   rustup
-   Java 11

**Install protobuf compiler (necessary for all systems)**
```bash
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v26.1/protoc-26.1-linux-x86_64.zip
unzip protoc-26.1-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
```

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y openjdk-11-jdk git gcc pkg-config openssl libssl-dev unzip
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v26.1/protoc-26.1-linux-x86_64.zip
unzip protoc-26.1-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
```

**Dependencies installation for CentOS**

```bash
sudo yum update -y
sudo yum install -y java-11-openjdk-devel git gcc pkgconfig openssl openssl-devel unzip
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v26.1/protoc-26.1-linux-x86_64.zip
unzip protoc-26.1-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
```
**Dependencies installation for MacOS**

```bash
brew update
brew install openjdk@11 git gcc pkgconfig protobuf openssl protobuf
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
# Install protobuf compiler
brew install protobuf
# Check that the protobuf compiler version 26.1 or higher is installed
protoc --version
```

#### Building and installation steps

Before starting this step, make sure you've installed all software dependencies.

1. Clone the repository:
    ```bash
    VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
    git clone --branch ${VERSION} https://github.com/aws/glide-for-redis.git
    cd glide-for-redis/java
    ```
2. Initialize git submodule:
    ```bash
    git submodule update --init --recursive
    ```
3. Build the Java wrapper (Choose a build option from the following and run it from the `java` folder):

    1. Enter the java directory:

    ```bash
    cd java
    ```

    2. Build in debug mode:

    ```bash
    ./gradlew :client:buildAll
    ```

    3. Build in release mode:

    ```bash
    ./gradlew :client:buildAllRelease
    ```

### Troubleshooting

Some troubleshooting issues:

- Failed to find cargo after rustup: gradlew may need to be restarted to recognize the new path. If clearing the gradle cache doesn't work, you may need to restart your machine.
- If build fails because cargo build compiler fails, make sure submodules are updated using git submodule update.
- If protobuf 26.0 or earlier is detected, upgrade to the latest protobuf release.


### Test

To run all tests, use the following command:

```bash
./gradlew test
```

To run a unit test, use the following command:

```bash
./gradlew :client:test
```

To run FFI tests between Java and Rust, use the following command:

```bash
./gradlew :client:testFfi
```

To run end-to-end tests, use the following command:

```bash
./gradlew :integTest:test
```

To run a single test, use the following command:
```bash
./gradlew :integTest:test --tests '*.functionLoad_and_functionList' --rerun
```

To run one class, use the following command:
```bash
./gradlew :client:test --tests 'TransactionTests' --rerun
```

### Generate files
To (re)generate protobuf code, use the following command:

```bash
./gradlew protobuf
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Linters

Development on the Java wrapper may involve changes in either the Java or Rust code. Each language has distinct linter tests that must be passed before committing changes.

#### Language-specific Linters

**Java:**

-   Spotless

#### Running the linters

1. Spotless
    ```bash
    # Run from the `java` folder
    ./gradlew :spotlessApply
    ```
2. Rust
    ```bash
    # Run from the `java` folder
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all
    ```

### FFI and features
- Names of the FFI defined in lib.rs have to correspond to the paths of real Java classes that expose native functions.
- lib.rs method names explicitly point to the native functions defined there.

### Recommended extensions for VS Code

-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language support for VSCode.
-   [spotless-gradle](https://marketplace.visualstudio.com/items?itemName=richardwillis.vscode-spotless-gradle) - Spotless Gradle plugin for VSCode.
-   [gradle](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) - Gradle extension for Java.

### Recommended extensions for IntelliJ

-   [spotless-gradle](https://plugins.jetbrains.com/plugin/18321-spotless-gradle) - Spotless Gradle plugin for IntelliJ.
-   [lombok](https://plugins.jetbrains.com/plugin/6317-lombok) - Lombok plugin for IntelliJ.
