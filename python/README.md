# Python Wrapper

Using [Pyo3](https://github.com/PyO3/pyo3) and [Maturin](https://github.com/PyO3/maturin).

### Create venv

Install python-venv: run `sudo apt install python3.10-venv` (with the relevant python version), in order to be able to create a virtual environment.

`cd python` cd into babushka/python folder
`python -m venv .env` in order to create a new virtual env.

### Source venv

`source .env/bin/activate` in order to enter virtual env.

### Install requirements

`pip install -r requirements.txt` install the library requriements (run in babushka/python folder)

### Build

`maturin develop` to build rust code and create python wrapper.

### [Optional] Build for release

`maturin develop --release` to build rust code optimized for release and create python wrapper.

### [Optional] Autogenerate protobuf files

Autogenerate python's protobuf files with:

`BABUSHKA_PATH=.` // e.g. /home/ubuntu/babushka
`protoc -Iprotobuf=${BABUSHKA_PATH}/babushka-core/src/protobuf/ --python_out=${BABUSHKA_PATH}/python/python/pybushka ${BABUSHKA_PATH}/babushka-core/src/protobuf/*.proto`

### [Optional] Install development requirements

`pip install -r dev_requirements.txt` install the library's development requriements (run in babushka/python folder)

### Running tests

Run `pytest --asyncio-mode=auto` from this folder, or from the `tests` folder. Make sure your shell uses your virtual environment.

#### Running linters

Using VS code, install the following extensions:

-   [Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python)
-   [isort](https://marketplace.visualstudio.com/items?itemName=ms-python.isort)
-   [Black Formetter](https://marketplace.visualstudio.com/items?itemName=ms-python.black-formatter)
-   [Flake8](https://marketplace.visualstudio.com/items?itemName=ms-python.flake8)

Or, run in a terminal:

```
cd babushka/python
isort . --profile black --skip-glob python/pybushka/protobuf
black --target-version py36 . --exclude python/pybushka/protobuf
flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics --exclude=python/pybushka/protobuf,.env/* --extend-ignore=E230
flake8 . --count --exit-zero --max-complexity=12 --max-line-length=127 --statistics --exclude=python/pybushka/protobuf,.env/* --extend-ignore=E230
# generate protobuf type files
MYPY_PROTOC_PATH=`which protoc-gen-mypy`
protoc --plugin=protoc-gen-mypy=${MYPY_PROTOC_PATH} -Iprotobuf=../babushka-core/src/protobuf/ --python_out=$./python/pybushka --mypy_out=./python/pybushka ../babushka-core/src/protobuf/*.proto
# run type check 
mypy .
```
