use babushka::headers::{RequestType, ResponseType, HEADER_END};
use babushka::start_socket_listener;
use pyo3::prelude::*;
use pyo3::types::PyString;
use redis::aio::MultiplexedConnection;
use redis::{AsyncCommands, RedisResult};

#[pyclass]
struct AsyncClient {
    multiplexer: MultiplexedConnection,
}

#[pyclass]
#[derive(PartialEq, PartialOrd, Clone)]
pub enum Level {
    Debug = 3,
    Error = 0,
    Info = 2,
    Trace = 4,
    Warn = 1,
}

#[allow(dead_code)]
#[pymethods]
impl Level {
    fn is_lower(&self, level: &Level) -> bool {
        self <= level
    }
}

#[pyclass]
enum PyRequestType {
    /// Type of a server address request
    ServerAddress = RequestType::ServerAddress as isize,
    /// Type of a get string request.
    GetString = RequestType::GetString as isize,
    /// Type of a set string request.
    SetString = RequestType::SetString as isize,
}

#[pyclass]
enum PyResponseType {
    /// Type of a response that returns a null.
    Null = ResponseType::Null as isize,
    /// Type of a response that returns a string.
    String = ResponseType::String as isize,
    /// Type of response containing an error that impacts a single request.
    RequestError = ResponseType::RequestError as isize,
    /// Type of response containing an error causes the connection to close.
    ClosingError = ResponseType::ClosingError as isize,
}

#[pymethods]
impl AsyncClient {
    #[staticmethod]
    fn create_client(address: String, py: Python) -> PyResult<&PyAny> {
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let client = redis::Client::open(address).unwrap();
            let multiplexer = client.get_multiplexed_async_connection().await.unwrap();
            let client = AsyncClient { multiplexer };
            Ok(Python::with_gil(|py| client.into_py(py)))
        })
    }

    fn get<'a>(&self, key: String, py: Python<'a>) -> PyResult<&'a PyAny> {
        let mut connection = self.multiplexer.clone();
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let result: RedisResult<Option<String>> = connection.get(key).await;
            match result {
                Ok(result) => match result {
                    Some(result) => Ok(Python::with_gil(|py| result.into_py(py))),
                    None => Ok(Python::with_gil(|py| py.None())),
                },
                Err(err) => Err(PyErr::new::<PyString, _>(err.to_string())),
            }
        })
    }

    fn set<'a>(&self, key: String, value: String, py: Python<'a>) -> PyResult<&'a PyAny> {
        let mut connection = self.multiplexer.clone();
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let result: RedisResult<()> = connection.set(key, value).await;
            match result {
                Ok(_) => Ok(Python::with_gil(|py| py.None())),
                Err(err) => Err(PyErr::new::<PyString, _>(err.to_string())),
            }
        })
    }

    fn create_pipeline(&self) -> AsyncPipeline {
        AsyncPipeline::new(self.multiplexer.clone())
    }
}

#[pyclass]
struct AsyncPipeline {
    internal_pipeline: redis::Pipeline,
    multiplexer: MultiplexedConnection,
}

impl AsyncPipeline {
    fn new(multiplexer: MultiplexedConnection) -> Self {
        AsyncPipeline {
            internal_pipeline: redis::Pipeline::new(),
            multiplexer,
        }
    }
}

#[pymethods]
impl AsyncPipeline {
    fn get(this: &PyCell<Self>, key: String) -> &PyCell<Self> {
        let mut pipeline = this.borrow_mut();
        pipeline.internal_pipeline.get(key);
        this
    }

    #[args(ignore_result = false)]
    fn set(this: &PyCell<Self>, key: String, value: String, ignore_result: bool) -> &PyCell<Self> {
        let mut pipeline = this.borrow_mut();
        pipeline.internal_pipeline.set(key, value);
        if ignore_result {
            pipeline.internal_pipeline.ignore();
        }
        this
    }

    fn execute<'a>(&self, py: Python<'a>) -> PyResult<&'a PyAny> {
        let mut connection = self.multiplexer.clone();
        let pipeline = self.internal_pipeline.clone();
        pyo3_asyncio::tokio::future_into_py(py, async move {
            let result: RedisResult<Vec<String>> = pipeline.query_async(&mut connection).await;
            match result {
                Ok(results) => Ok(Python::with_gil(|py| results.into_py(py))),
                Err(err) => Err(PyErr::new::<PyString, _>(err.to_string())),
            }
        })
    }
}

/// A Python module implemented in Rust.
#[pymodule]
fn pybushka(_py: Python, m: &PyModule) -> PyResult<()> {
    m.add_class::<AsyncClient>()?;
    m.add_class::<PyRequestType>()?;
    m.add_class::<PyResponseType>()?;
    m.add("HEADER_LENGTH_IN_BYTES", HEADER_END).unwrap();
    m.add_class::<Level>()?;

    #[pyfn(m)]
    fn py_log(log_level: Option<Level>, log_identifier: String, message: String) {
        log(log_level, log_identifier, message);
    }

    #[pyfn(m)]
    fn py_init(level: Option<Level>, file_name: Option<&str>) -> Level {
        init(level, file_name)
    }

    #[pyfn(m)]
    fn start_socket_listener_external(init_callback: PyObject) -> PyResult<PyObject> {
        start_socket_listener(move |socket_path| {
            Python::with_gil(|py| {
                match socket_path {
                    Ok(path) => {
                        let _ = init_callback.call(py, (path, py.None()), None);
                    }
                    Err(err) => {
                        let _ = init_callback.call(py, (py.None(), err.to_string()), None);
                    }
                };
            });
        });
        Ok(Python::with_gil(|py| "OK".into_py(py)))
    }

    Ok(())
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        into_level(level)
    }
}

fn into_logger_level(level: Option<Level>) -> Option<logger_core::Level> {
    match level {
        None => None,
        Some(Level::Debug) => Some(logger_core::Level::Debug),
        Some(Level::Error) => Some(logger_core::Level::Error),
        Some(Level::Warn) => Some(logger_core::Level::Warn),
        Some(Level::Info) => Some(logger_core::Level::Info),
        Some(Level::Trace) => Some(logger_core::Level::Trace),
    }
}

fn into_level(level: logger_core::Level) -> Level {
    match level {
        logger_core::Level::Debug => Level::Debug,
        logger_core::Level::Error => Level::Error,
        logger_core::Level::Warn => Level::Warn,
        logger_core::Level::Info => Level::Info,
        logger_core::Level::Trace => Level::Trace,
    }
}

#[pyfunction]
pub fn log(log_level: Option<Level>, log_identifier: String, message: String) {
    logger_core::log(
        into_logger_level(log_level).unwrap(),
        &log_identifier,
        &message,
    );
}

#[pyfunction]
pub fn init(level: Option<Level>, file_name: Option<&str>) -> Level {
    let logger_level = logger_core::init(into_logger_level(level), file_name);
    let _ = tracing_subscriber::fmt::try_init();
    return Level::from(logger_level);
}
