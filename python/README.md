Using [Pyo3](https://github.com/PyO3/pyo3) and [Maturin](https://github.com/PyO3/maturin).

### Source venv

`source .env/bin/activate` in order to enter virtual env.

### Build

`maturin develop` to build rust code and create python wrapper.

### Running tests

Run `pytest --asyncio-mode=auto` from this folder, or from the `tests` folder. Make sure your shell uses your virtual environment.
