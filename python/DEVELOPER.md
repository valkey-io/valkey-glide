# Developer Guide

This document describes how to set up your development environment to build and test the Valkey GLIDE Python wrapper.

The Valkey GLIDE Python wrapper consists of both Python and Rust code. Rust bindings for Python are implemented using [PyO3](https://github.com/PyO3/pyo3), and the Python package is built using [maturin](https://github.com/PyO3/maturin). The Python and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.

# Prerequisites
---

Before building the package from source, make sure that you have installed the listed dependencies below:


-   python3 virtualenv
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler) >= v3.20.0
-   openssl
-   openssl-dev
-   rustup

For your convenience, we wrapped the steps in a "copy-paste" code blocks for common operating systems:

<details>
<summary>Ubuntu / Debian</summary>

```bash
sudo apt update -y
sudo apt install -y python3 python3-venv git gcc pkg-config openssl libssl-dev unzip
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
# For other arch type from x86 example below, the signature of the curl url should be protoc-<version>-<os>-<arch>.zip,
# e.g. protoc-3.20.3-linux-aarch_64.zip for ARM64.
curl -LO $PB_REL/download/v3.20.3/protoc-3.20.3-linux-x86_64.zip
unzip protoc-3.20.3-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler is installed
protoc --version
```

</details>

<details>
<summary>CentOS</summary>

```bash
sudo yum update -y
sudo yum install -y python3 git gcc pkgconfig openssl openssl-devel unzip
pip3 install virtualenv
# Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Install protobuf compiler
PB_REL="https://github.com/protocolbuffers/protobuf/releases"
curl -LO $PB_REL/download/v3.20.3/protoc-3.20.3-linux-x86_64.zip
unzip protoc-3.20.3-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
# Check that the protobuf compiler is installed
protoc --version
```

</details>

<details>
<summary>MacOS</summary>

```bash
brew update
brew install python3 git gcc pkgconfig protobuf@3 openssl virtualenv
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check that the Rust compiler is installed
rustc --version
# Verify the Protobuf compiler installation
protoc --version

# If protoc is not found or does not work correctly, update the PATH
echo 'export PATH="/opt/homebrew/opt/protobuf@3/bin:$PATH"' >> /Users/$USER/.bash_profile
source /Users/$USER/.bash_profile
protoc --version
```

</details>

# Building
---

Before starting this step, make sure you've installed all software requirements.

## Prepare your environment

```bash
mkdir -p $HOME/src
cd $_
git clone https://github.com/valkey-io/valkey-glide.git
cd valkey-glide
GLIDE_ROOT=$(pwd)
protoc -Iprotobuf=${GLIDE_ROOT}/glide-core/src/protobuf/    \
        --python_out=${GLIDE_ROOT}/python/python/glide      \
        ${GLIDE_ROOT}/glide-core/src/protobuf/*.proto
cd python
python3 -m venv .env
source .env/bin/activate
pip install -r dev_requirements.txt
```

## Build the package (in release mode):

```bash
maturin develop --release --strip
```

> **Note:** to build the wrapper binary with debug symbols remove the `--strip` flag.

> **Note 2:** for a faster build time, execute `maturin develop` without the release flag. This will perform an unoptimized build, which is suitable for developing tests. Keep in mind that performance is significantly affected in an unoptimized build, so it's required to include the `--release` flag when measuring performance.

# Running tests
---

Ensure you have installed `valkey-server` and `valkey-cli` on your host (or `redis-server` and `redis-cli`). 
See the [Valkey installation guide](https://valkey.io/topics/installation/) to install the Valkey server and CLI.

From a terminal, change directory to the GLIDE source folder and type:

```bash
cd $HOME/src/valkey-glide
cd python
source .env/bin/activate
pytest -v --asyncio-mode=auto
```

To run modules tests:

```bash
cd $HOME/src/valkey-glide
cd python
source .env/bin/activate
pytest -v --asyncio-mode=auto -k "test_server_modules.py"
```

**TIP:** to run a specific test, append `-k <test_name>` to the `pytest` execution line

To run tests against an already running servers, change the `pytest` line above to this:

```bash
pytest -v --asyncio-mode=auto --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379
```

# Generate protobuf files
---

During the initial build, Python protobuf files were created in `python/python/glide/protobuf`. If modifications are made
to the protobuf definition files (`.proto` files located in `glide-core/src/protofuf`), it becomes necessary to
regenerate the Python protobuf files. To do so, run:

```bash
cd $HOME/src/valkey-glide
GLIDE_ROOT_FOLDER_PATH=.
protoc -Iprotobuf=${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/    \
    --python_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide          \
    ${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/*.proto
```

## Protobuf interface files

To generate the protobuf files with Python Interface files (pyi) for type-checking purposes, ensure you have installed `mypy-protobuf` with pip, and then execute the following command:

```bash
cd $HOME/src/valkey-glide
GLIDE_ROOT_FOLDER_PATH=.
MYPY_PROTOC_PATH=`which protoc-gen-mypy`
protoc --plugin=protoc-gen-mypy=${MYPY_PROTOC_PATH}                     \
        -Iprotobuf=${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/   \
        --python_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide      \
        --mypy_out=${GLIDE_ROOT_FOLDER_PATH}/python/python/glide        \
        ${GLIDE_ROOT_FOLDER_PATH}/glide-core/src/protobuf/*.proto
```

# Linters
---

Development on the Python wrapper may involve changes in either the Python or Rust code. Each language has distinct linter tests that must be passed before committing changes.

## Language-specific Linters

**Python:**

-   flake8
-   isort
-   black
-   mypy

**Rust:**

-   clippy
-   fmt

## Running the linters

Run from the main `/python` folder

1. Python
    > Note: make sure to [generate protobuf with interface files]("#protobuf-interface-files") before running `mypy` linter
    ```bash
    cd $HOME/src/valkey-glide/python
    source .env/bin/activate
    pip install -r dev_requirements.txt
    isort . --profile black --skip-glob python/glide/protobuf --skip-glob .env
    black . --exclude python/glide/protobuf --exclude .env
    flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics      \
            --exclude=python/glide/protobuf,.env --extend-ignore=E230
    flake8 . --count --exit-zero --max-complexity=12 --max-line-length=127  \
            --statistics --exclude=python/glide/protobuf,.env             \
            --extend-ignore=E230
    # run type check
    mypy .
    ```

2. Rust

    ```bash
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all
    ```

# Documentation
---

> **NOTE:**  We are currently in process of switching our documentation tool from `sphinx` to `mkdocs`. Currently the files located in `python/docs` are required for `sphinx`'s CI validation step (`docs-test`) as they are the configuration files for how `sphinx` works in documenting Valkey GLIDE. Once we switch to `mkdocs`, `sphinx` related files and validation should be removed, and `mkdocs`'s files and validation should be used instead.

> By default, `mkdocs` should still be using Google's Python Docstring Style so the "Documentation Style" section below will still be valid.

We follow the [Google Style Python Docstrings format](https://sphinxcontrib-napoleon.readthedocs.io/en/latest/example_google.html) in our documentation. For our documentation tool, we use `sphinx`. 

**Note:** `docs/index.rst` has manual modifications to it and should NOT be deleted. Modify this file with caution.

To run this tool, execute the following:

```bash
cd $HOME/src/valkey-glide/python
source .env/bin/activate
pip install -r dev_requirements.txt
cd docs
sphinx-apidoc -o . ../python/glide
make clean
make html # or run make help to see a list of available options
```

In `docs/_build` you will find the `index.html` page. Open this file in your browser and you should see all the documented functions.

However, some stylings may not be implemented by this Google format. In such cases, we revert back to the default style that `sphinx` uses: [reStructuredText](https://sphinx-rtd-tutorial.readthedocs.io/en/latest/docstrings.html). An example of this is shown for hyperlinks below.

## Documentation Style

### Example of a Properly Formatted Docstring in Functions

```python
"""
Reads entries from the given streams.

See https://valkey.io/commands/xread for more details.

Note:
    When in cluster mode, all keys in `keys_and_ids` must map to the same hash slot.

Warning:
    If we wanted to provide a warning message, we would format it like this.

Args:
    keys_and_ids (Mapping[TEncodable, TEncodable]): A mapping of keys and entry
        IDs to read from.
    options (Optional[StreamReadOptions]): Options detailing how to read the stream.

Returns:
    Optional[Mapping[bytes, Mapping[bytes, List[List[bytes]]]]]: A mapping of stream keys, to a mapping of stream IDs,
    to a list of pairings with format `[[field, entry], [field, entry], ...]`.

    None will be returned under the following conditions:

        - All key-ID pairs in `keys_and_ids` have either a non-existing key or a non-existing ID, or there are no
            entries after the given ID.
        - The `BLOCK` option is specified and the timeout is hit.

Examples:
    >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
    >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
    >>> await client.xread({"mystream": "0-0"}, StreamReadOptions(block_ms=1000))
        {
            b"mystream": {
                b"0-1": [[b"field1", b"value1"]],
                b"0-2": [[b"field2", b"value2"], [b"field2", b"value3"]],
            }
        }
        # Indicates the stream entries for "my_stream" with IDs greater than "0-0". The operation blocks up to
        # 1000ms if there is no stream data.
"""
```

### Example of Properly Formatted Documentation for a Class 

```python
class BitOffsetMultiplier(BitFieldOffset):
    """
    Represents an offset in an array of bits for the `BITFIELD` or `BITFIELD_RO` commands. The bit offset index is
    calculated as the numerical value of the offset multiplied by the encoding value. Must be greater than or equal
    to 0.

    For example, if we have the binary 01101001 with offset multiplier of 1 for an unsigned encoding of size 4, then
    the value is 9 from `0110(1001)`.

    Attributes:
        offset (int): The offset in the array of bits, which will be multiplied by the encoding value to get the
            final bit index offset.
    """

    #: Prefix specifying that the offset uses an encoding multiplier.
    OFFSET_MULTIPLIER_PREFIX = "#"

    def __init__(self, offset: int):
        self._offset = f"{self.OFFSET_MULTIPLIER_PREFIX}{str(offset)}"

    def to_arg(self) -> str:
        return self._offset
```

### Example of Properly Formatted Documentation for Enums

```python
class ConditionalChange(Enum):
    """
    A condition to the `SET`, `ZADD` and `GEOADD` commands.
    """

    ONLY_IF_EXISTS = "XX"
    """ Only update key / elements that already exist. Equivalent to `XX` in the Valkey API. """

    ONLY_IF_DOES_NOT_EXIST = "NX"
    """ Only set key / add elements that does not already exist. Equivalent to `NX` in the Valkey API. """
```

### Indentation and Spaces

#### Args or Attributes

To provide documentation for arguments or attributes, we can have each argument or attribute next to each other with descriptions that exceed the max line length indented on the next line:

```python
Args:
    some_num (int): If this line ever gets too long, what we could do is break this line
        to the next line here, and indent it so that sphinx can see the line as part of
        the same argument.
    options (Optional[StreamReadOptions]): For other arguments, we start by having the indent
        match up with the indent of the first line of the previous argument, and then follow
        the same rule.
    some_bool (bool): And then one-line descriptions are as usual.


Attributes:
    some_num (int): If this line ever gets too long, what we could do is break this line
        to the next line here, and indent it so that sphinx can see the line as part of
        the same argument.
    options (Optional[StreamReadOptions]): For other arguments, we start by having the indent
        match up with the indent of the first line of the previous argument, and then follow
        the same rule.
    some_bool (bool): And then one-line descriptions are as usual.
```

#### Return value(s)

Return values are a little special for sphinx. If we wanted to provide more context or multiple possible return values, the convention we will go for is that we should add a space between every different return value. 

We start by adding the return type on the first line, followed by a description of the return value.

**Note**: for each return value, we **should not** indent the docs like args to show that it is part of the same return value. For example:

```python
Returns:
    List[int]: Some description here regarding the purpose of the list of ints being
    returned. Notice how this new line is not indented but it is still apart of the same 
    description.

    If we ever want to provide more context or another description of another return value 
    (ex. None, -1, True/False, etc.) we add a space between this description and the
    previous description.
```

#### Lists

We have to add a space between the previous line and a line after the list ends and also indent the list by one indent level:

```python
Args:
    key (TEncodable): The key of the stream.
    start (StreamRangeBound): The starting stream ID bound for the range.

        - Use `IdBound` to specify a stream ID.
        - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
        - Use `MinId` to start with the minimum available ID.

    end (StreamRangeBound): The ending stream ID bound for the range.

        - Use `IdBound` to specify a stream ID.
        - Use `ExclusiveIdBound` to specify an exclusive bounded stream ID.
        - Use `MaxId` to end with the maximum available ID.

    count (Optional[int]): An optional argument specifying the maximum count of stream entries to return.
```

#### Examples or Code Blocks

For examples, we can use the `Example:` or `Examples:` keyword and indent the following code block:

```python
Examples:
    >>> await client.xadd("mystream", [("field1", "value1")], StreamAddOptions(id="0-1"))
    >>> await client.xadd("mystream", [("field2", "value2"), ("field2", "value3")], StreamAddOptions(id="0-2"))
    >>> await client.xread({"mystream": "0-0"}, StreamReadOptions(block_ms=1000))
        {
            b"mystream": {
                b"0-1": [[b"field1", b"value1"]],
                b"0-2": [[b"field2", b"value2"], [b"field2", b"value3"]],
            }
        }
        # Indicates the stream entries for "my_stream" with IDs greater than "0-0". The operation blocks up to
        # 1000ms if there is no stream data.
    
    ... # More examples here
```

If we wanted to add a code block in a place other than the `Examples` block, we have to use a double colon syntax and indent the code block.

```python
Attributes:
    addresses (List[NodeAddress]): DNS Addresses and ports of known nodes in the cluster.
        If the server is in cluster mode the list can be partial, as the client will attempt to map out
        the cluster and find all nodes.
        If the server is in standalone mode, only nodes whose addresses were provided will be used by the
        client.
        For example::

            [
                {address:sample-address-0001.use1.cache.amazonaws.com, port:6379},
                {address: sample-address-0002.use2.cache.amazonaws.com, port:6379}
            ]

    use_tls (bool): True if communication with the cluster should use Transport Level Security.
        Should match the TLS configuration of the server/cluster, otherwise the connection attempt will fail
```

### Constants

We want to use `#:` to add documentation for constants:

```python
#: "GET" subcommand string for use in the `BITFIELD` or `BITFIELD_RO` commands.
GET_COMMAND_STRING = "GET"
```

### Enums

We provide a general description at the top, and follow each enum value with a description beneath. Refer to [the example](#example-of-properly-formatted-documentation-for-enums).

### Links and Hyperlinks

If we wanted to show a regular link, we can add it as is. If we wanted to show hyperlinks, follow the reStructuredText (rst) link format:

Format: `` `text <link>`_ ``

Example: `` `SORT <https://valkey.io/commands/sort/>`_ ``


# Recommended extensions for VS Code
---

-   [Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python)
-   [isort](https://marketplace.visualstudio.com/items?itemName=ms-python.isort)
-   [Black Formatter](https://marketplace.visualstudio.com/items?itemName=ms-python.black-formatter)
-   [Flake8](https://marketplace.visualstudio.com/items?itemName=ms-python.flake8)
-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)
