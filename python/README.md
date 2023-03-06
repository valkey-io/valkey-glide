Using [Pyo3](https://github.com/PyO3/pyo3) and [Maturin](https://github.com/PyO3/maturin).

### Create venv
`python -m venv .env` in order to create a new virtual env.

### Source venv

`source .env/bin/activate` in order to enter virtual env.

### Build

`maturin develop` to build rust code and create python wrapper.

### [Optional] Build for release

`maturin develop --release` to build rust code optimized for release and create python wrapper.

### [Optional] Autogenerate protobuf files
Autogenerate python's protobuf files with:
`protoc -IPATH=/home/ubuntu/babushka/babushka-core/src/protobuf/ --python_out=/home/ubuntu/babushka/python /home/ubuntu/babushka/babushka-core/src/protobuf/pb_message.proto`
Run protobuf test:
`pytest --asyncio-mode=auto /home/ubuntu/babushka/python/pyton/tests/test_async_client.py::TestProtobufClient -s`

### Running tests

Run `pytest --asyncio-mode=auto` from this folder, or from the `tests` folder. Make sure your shell uses your virtual environment.
