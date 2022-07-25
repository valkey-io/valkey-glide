use pyo3::prelude::*;
use pyo3::types::PyString;
use redis::aio::{MultiplexedConnection};
use redis::AsyncCommands;

#[pyclass]
struct AsyncClient {
    multiplexer: MultiplexedConnection
}

#[pymethods]
impl AsyncClient {
    #[staticmethod]
    fn new<'a>(address: String, py: Python<'a>) -> PyResult<&'a PyAny> {
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let client = redis::Client::open(address).unwrap();
            let multiplexer = client.get_multiplexed_async_connection().await.unwrap();
            let client = AsyncClient {
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

    fn create_pipeline(&self) -> AsyncPipeline{
        AsyncPipeline::new(self.multiplexer.clone())
    }
}

#[pyclass]
struct AsyncPipeline {
    internal_pipeline: redis::Pipeline,
    multiplexer: MultiplexedConnection
}

impl AsyncPipeline {
    fn new(multiplexer: MultiplexedConnection) -> Self {
        AsyncPipeline { internal_pipeline: redis::Pipeline::new(), multiplexer }
    }
}

#[pymethods]
impl AsyncPipeline {
    fn get<'a>(this: &'a PyCell<Self>, key:String) -> &'a PyCell<Self> {
        let mut pipeline = this.borrow_mut();
        pipeline.internal_pipeline.get(key);
        this
    }

    fn set<'a>(this: &'a PyCell<Self>, key: String, value: String) -> &'a PyCell<Self> {
        let mut pipeline = this.borrow_mut();
        pipeline.internal_pipeline.set(key, value);
        this
    }

    fn execute<'a>(&self, py: Python<'a>) -> PyResult<&'a PyAny> {
        let mut connection = self.multiplexer.clone();
        let pipeline = self.internal_pipeline.clone();
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let result: Result<Vec<String>, redis::RedisError> = pipeline.query_async(&mut connection).await;
            match result {
                Ok(results) => Ok(Python::with_gil(|py| results.into_py(py))),
                Err(err) => Err(PyErr::new::<PyString, _>(err.to_string())),
            }
        })
    }
}

/// A Python module implemented in Rust.
#[pymodule]
fn babushka(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<AsyncClient>()?;
    Ok(())
}
