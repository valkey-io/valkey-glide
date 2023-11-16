# Developer Guide

This document describes how to set up your development environment to build and test the Babushka Python wrapper.

### Development Overview

The Babushka Python wrapper consists of both Python and Rust code. Rust bindings for Python are implemented using [PyO3](https://github.com/PyO3/pyo3), and the Python package is built using [maturin](https://github.com/PyO3/maturin). The Python and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.


### Build

- Follow the building instructions for the Python wrapper in the [Build from source](https://github.com/aws/babushka/blob/main/python/README.md#build-from-source) section to clone the code and build the wrapper.

- Install Python development requirements with:

    ```bash
    pip install -r python/dev_requirements.txt
    ```

- For a fast build, execute `maturin develop` without the release flag. This will perform an unoptimized build, which is suitable for developing tests. Keep in mind that performance is significantly affected in an unoptimized build, so it's required to include the "--release" flag when measuring performance.


### Test

To run tests, use the following command:

```bash
pytest --asyncio-mode=auto
```

To execute a specific test, include the `-k <test_name>` option. For example:

```bash
pytest --asyncio-mode=auto -k test_socket_set_and_get
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Generate protobuf files
During the initial build, Python protobuf files were created in `python/python/pybushka/protobuf`. If modifications are made to the protobuf definition files (.proto files located in `babushka-core/src/protofuf`), it becomes necessary to regenerate the Python protobuf files. To do so, run:

```bash
BABUSHKA_ROOT_FOLDER_PATH=. # e.g. /home/ubuntu/babushka
protoc -Iprotobuf=${BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/ --python_out=${BABUSHKA_ROOT_FOLDER_PATH}/python/python/pybushka ${BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/*.proto
``` 

#### Protobuf interface files
To generate the protobuf files with Python Interface files (pyi) for type-checking purposes, ensure you have installed `mypy-protobuf` with pip, and then execute the following command:

```bash
BABUSHKA_PATH=. # e.g. /home/ubuntu/babushka
MYPY_PROTOC_PATH=`which protoc-gen-mypy`
protoc --plugin=protoc-gen-mypy=${MYPY_PROTOC_PATH} -Iprotobuf={BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/ --python_out={BABUSHKA_ROOT_FOLDER_PATH}/python/python/pybushka --mypy_out=./python/python/pybushka {BABUSHKA_ROOT_FOLDER_PATH}/babushka-core/src/protobuf/*.proto
```

### Linters
Development on the Python wrapper may involve changes in either the Python or Rust code. Each language has distinct linter tests that must be passed before committing changes.

#### Language-specific Linters

__Python:__
- flake8
- isort
- black
- mypy

__Rust:__
- clippy
- fmt

#### Running the linters
Run from the main `/python` folder
1. Python
    > Note: make sure to [generate protobuf with interface files]("#protobuf-interface-files") before running mypy linter
    ```bash
    pip install -r dev_requirements.txt
    isort . --profile black --skip-glob python/pybushka/protobuf
    black . --exclude python/pybushka/protobuf
    flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics --exclude=python/pybushka/protobuf,.env/* --extend-ignore=E230
    flake8 . --count --exit-zero --max-complexity=12 --max-line-length=127 --statistics --exclude=python/pybushka/protobuf,.env/* --extend-ignore=E230
    # run type check
    mypy .
    ```
2. Rust
    ```bash
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all

    ```

### Recommended extensions for VS Code
-   [Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python)
-   [isort](https://marketplace.visualstudio.com/items?itemName=ms-python.isort)
-   [Black Formetter](https://marketplace.visualstudio.com/items?itemName=ms-python.black-formatter)
-   [Flake8](https://marketplace.visualstudio.com/items?itemName=ms-python.flake8)
-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)
