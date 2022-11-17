Using [Pyo3](https://github.com/PyO3/pyo3) and [Maturin](https://github.com/PyO3/maturin).

### Create venv
`python -m venv .env` in order to create a new virtual env.

### Source venv

`source .env/bin/activate` in order to enter virtual env.

### Build

`maturin develop` to build rust code and create python wrapper.

### [Optional] Build for release

`maturin develop --release` to build rust code optimized for release and create python wrapper.

### Running tests

Run `pytest --asyncio-mode=auto` from this folder, or from the `tests` folder. Make sure your shell uses your virtual environment.
