use pyo3::prelude::*;
use redis::aio::{MultiplexedConnection};
use redis::AsyncCommands;

#[pyclass]
struct Client{
    multiplexer: MultiplexedConnection
}

#[pymethods]
impl Client {
    #[staticmethod]
    fn new<'a>(address: String, py: Python<'a>) -> PyResult<&'a PyAny> {
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let client = redis::Client::open(address).unwrap();
            let multiplexer = client.get_multiplexed_async_connection().await.unwrap();
            let client = Client {
                multiplexer
            };
            Ok(Python::with_gil(|py| client.into_py(py)))
        })
    }

    fn get<'a>(&self, key:String, py: Python<'a>) -> PyResult<&'a PyAny> {
        let mut connection = self.multiplexer.clone();
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let result: String = connection.get(key).await.unwrap();
            Ok(Python::with_gil(|py| result.into_py(py)))
        })
    }

    fn set<'a>(&self, key: String, value: String, py: Python<'a>) -> PyResult<&'a PyAny> {
        let mut connection = self.multiplexer.clone();
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let _:() = connection.set(key, value).await.unwrap();
            Ok(Python::with_gil(|py| py.None()))
        })
    }
}

/// A Python module implemented in Rust.
#[pymodule]
fn babushka(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<Client>()?;
    Ok(())
}