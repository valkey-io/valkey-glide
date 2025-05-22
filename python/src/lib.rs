// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use bytes::Bytes;
use glide_core::MAX_REQUEST_ARGS_LENGTH;
use glide_core::Telemetry;
use glide_core::client::FINISHED_SCAN_CURSOR;
use glide_core::errors::error_message;
use glide_core::start_socket_listener;
use pyo3::Python;
use pyo3::exceptions::PyTypeError;
use pyo3::prelude::*;
use pyo3::types::{PyAny, PyBool, PyBytes, PyDict, PyFloat, PyList, PySet, PyString};
use redis::Value;
use std::collections::HashMap;
use std::ptr::from_mut;
use std::sync::Arc;

pub const DEFAULT_TIMEOUT_IN_MILLISECONDS: u32 =
    glide_core::client::DEFAULT_RESPONSE_TIMEOUT.as_millis() as u32;
pub const MAX_REQUEST_ARGS_LEN: u32 = MAX_REQUEST_ARGS_LENGTH as u32;

#[pyclass(eq, eq_int)]
#[derive(PartialEq, Eq, PartialOrd, Clone)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
    Off = 5,
}

#[allow(dead_code)]
#[pymethods]
impl Level {
    fn is_lower(&self, level: &Level) -> bool {
        self <= level
    }
}

/// This struct is used to keep track of the cursor of a cluster scan.
/// We want to avoid passing the cursor between layers of the application,
/// So we keep the state in the container and only pass the id of the cursor.
/// The cursor is stored in the container and can be retrieved using the id.
/// The cursor is removed from the container when the object is deleted (dropped).
#[pyclass]
#[derive(Default)]
pub struct ClusterScanCursor {
    cursor: String,
}

#[pymethods]
impl ClusterScanCursor {
    #[new]
    #[pyo3(signature = (new_cursor=None))]
    fn new(new_cursor: Option<String>) -> Self {
        match new_cursor {
            Some(cursor) => ClusterScanCursor { cursor },
            None => ClusterScanCursor::default(),
        }
    }

    fn get_cursor(&self) -> String {
        self.cursor.clone()
    }

    fn is_finished(&self) -> bool {
        self.cursor == *FINISHED_SCAN_CURSOR.to_string()
    }
}

impl Drop for ClusterScanCursor {
    fn drop(&mut self) {
        glide_core::cluster_scan_container::remove_scan_state_cursor(self.cursor.clone());
    }
}

#[pyclass]
pub struct Script {
    hash: String,
}

#[pymethods]
impl Script {
    #[new]
    fn new(code: &Bound<PyAny>) -> PyResult<Self> {
        let hash = if let Ok(code_str) = code.extract::<String>() {
            glide_core::scripts_container::add_script(code_str.as_bytes())
        } else if let Ok(code_bytes) = code.extract::<Bound<PyBytes>>() {
            glide_core::scripts_container::add_script(code_bytes.as_bytes())
        } else {
            return Err(PyTypeError::new_err(
                "code must be either a String or PyBytes",
            ));
        };

        Ok(Script { hash })
    }

    fn get_hash(&self) -> String {
        self.hash.clone()
    }

    fn __del__(&mut self) {
        glide_core::scripts_container::remove_script(&self.hash);
    }
}

/// A Python module implemented in Rust.
#[pymodule]
fn glide(_py: Python, m: &Bound<PyModule>) -> PyResult<()> {
    m.add_class::<Level>()?;
    m.add_class::<Script>()?;
    m.add_class::<ClusterScanCursor>()?;
    m.add(
        "DEFAULT_TIMEOUT_IN_MILLISECONDS",
        DEFAULT_TIMEOUT_IN_MILLISECONDS,
    )?;
    m.add("MAX_REQUEST_ARGS_LEN", MAX_REQUEST_ARGS_LEN)?;
    m.add_function(wrap_pyfunction!(py_log, m)?)?;
    m.add_function(wrap_pyfunction!(py_init, m)?)?;
    m.add_function(wrap_pyfunction!(start_socket_listener_external, m)?)?;
    m.add_function(wrap_pyfunction!(value_from_pointer, m)?)?;
    m.add_function(wrap_pyfunction!(create_leaked_value, m)?)?;
    m.add_function(wrap_pyfunction!(create_leaked_bytes_vec, m)?)?;
    m.add_function(wrap_pyfunction!(get_statistics, m)?)?;

    #[pyfunction]
    fn py_log(log_level: Level, log_identifier: String, message: String) {
        log(log_level, log_identifier, message);
    }

    #[pyfunction]
    fn get_statistics(_py: Python) -> PyResult<PyObject> {
        let mut stats_map = HashMap::<String, String>::new();
        stats_map.insert(
            "total_connections".to_string(),
            Telemetry::total_connections().to_string(),
        );
        stats_map.insert(
            "total_clients".to_string(),
            Telemetry::total_clients().to_string(),
        );

        Python::with_gil(|py| {
            let py_dict = PyDict::new(py);

            for (key, value) in stats_map {
                py_dict.set_item(PyString::new(py, &key), PyString::new(py, &value))?;
            }

            Ok(py_dict
                .into_pyobject(py)
                .expect("Expected a proper conversion into a Python dict.")
                .into_any()
                .unbind())
        })
    }

    #[pyfunction]
    #[pyo3(signature = (level=None, file_name=None))]
    fn py_init(level: Option<Level>, file_name: Option<&str>) -> Level {
        init(level, file_name)
    }
    #[pyfunction]
    fn start_socket_listener_external(init_callback: PyObject) -> PyResult<PyObject> {
        let init_callback = Arc::new(init_callback);
        start_socket_listener({
            let init_callback = Arc::clone(&init_callback);
            move |socket_path| {
                let init_callback = Arc::clone(&init_callback);
                Python::with_gil(|py| {
                    match socket_path {
                        Ok(path) => {
                            let _ = init_callback.call(py, (path, py.None()), None);
                        }
                        Err(error_message) => {
                            let _ = init_callback.call(py, (py.None(), error_message), None);
                        }
                    };
                });
            }
        });
        Ok(Python::with_gil(|py| {
            "OK".into_pyobject(py)
                .expect("Expected a proper conversion of 'OK' into a Python string.")
                .into_any()
                .unbind()
        }))
    }

    fn iter_to_value<TIterator>(
        py: Python,
        iter: impl IntoIterator<Item = Value, IntoIter = TIterator>,
    ) -> PyResult<Vec<PyObject>>
    where
        TIterator: ExactSizeIterator<Item = Value>,
    {
        let mut iterator = iter.into_iter();
        let len = iterator.len();

        iterator.try_fold(Vec::with_capacity(len), |mut acc, val| {
            acc.push(resp_value_to_py(py, val)?);
            Ok(acc)
        })
    }

    fn resp_value_to_py(py: Python, val: Value) -> PyResult<PyObject> {
        match val {
            Value::Nil => Ok(py.None()),
            Value::SimpleString(str) => {
                let data_bytes = PyBytes::new(py, str.as_bytes());
                Ok(data_bytes
                    .into_pyobject(py)
                    .expect("SimpleString: expected a proper conversion into Python bytes.")
                    .into_any()
                    .unbind())
            }
            Value::Okay => Ok("OK"
                .into_pyobject(py)
                .expect("Expected a proper conversion of 'OK' into a Python string.")
                .into_any()
                .unbind()),
            Value::Int(num) => Ok(num
                .into_pyobject(py)
                .expect("Int: expected a proper conversion into a Python int.")
                .into_any()
                .unbind()),
            Value::BulkString(data) => {
                let data_bytes = PyBytes::new(py, &data);
                Ok(data_bytes
                    .into_pyobject(py)
                    .expect("BulkString: expected a proper conversion into Python bytes.")
                    .into_any()
                    .unbind())
            }
            Value::Array(bulk) => {
                let elements: Bound<PyList> = PyList::new(py, iter_to_value(py, bulk)?)?;
                Ok(elements
                    .into_pyobject(py)
                    .expect("Array: expected a proper conversion into a Python list.")
                    .into_any()
                    .unbind())
            }
            Value::Map(map) => {
                let dict = PyDict::new(py);
                for (key, value) in map {
                    dict.set_item(resp_value_to_py(py, key)?, resp_value_to_py(py, value)?)?;
                }
                Ok(dict
                    .into_pyobject(py)
                    .expect("Map: expected a proper conversion into a Python dict.")
                    .into_any()
                    .unbind())
            }
            Value::Attribute { data, attributes } => {
                let dict = PyDict::new(py);
                let value = resp_value_to_py(py, *data)?;
                let attributes = resp_value_to_py(py, Value::Map(attributes))?;
                dict.set_item("value", value)?;
                dict.set_item("attributes", attributes)?;
                Ok(dict
                    .into_pyobject(py)
                    .expect("Attribute: expected a proper conversion into a Python dict.")
                    .into_any()
                    .unbind())
            }
            Value::Set(set) => {
                let set = iter_to_value(py, set)?;
                let set = PySet::new(py, set.iter())?;
                Ok(set
                    .into_pyobject(py)
                    .expect("Set: expected a proper conversion into a Python set.")
                    .into_any()
                    .unbind())
            }
            Value::Double(double) => Ok(PyFloat::new(py, double)
                .into_pyobject(py)
                .expect("Double: expected a proper conversion into a Python float.")
                .into_any()
                .unbind()),
            Value::Boolean(boolean) => Ok(<pyo3::Bound<'_, PyBool> as Clone>::clone(
                &PyBool::new(py, boolean)
                    .into_pyobject(py)
                    .expect("Boolean: expected a proper conversion into a Python boolean."),
            )
            .unbind()
            .into()),
            Value::VerbatimString { format: _, text } => {
                // TODO create MATCH on the format
                let data_bytes = PyBytes::new(py, text.as_bytes());
                Ok(data_bytes
                    .into_pyobject(py)
                    .expect("VerbatimString: expected a proper conversion into Python bytes.")
                    .into_any()
                    .unbind())
            }
            Value::BigNumber(bigint) => Ok(bigint
                .into_pyobject(py)
                .expect("BigNumber: expected a proper conversion into a Python int.")
                .into_any()
                .unbind()),
            Value::Push { kind, data } => {
                let dict = PyDict::new(py);
                dict.set_item("kind", format!("{kind:?}"))?;
                let values: Bound<PyList> = PyList::new(py, iter_to_value(py, data)?)?;
                dict.set_item("values", values)?;
                Ok(dict
                    .into_pyobject(py)
                    .expect("Push: expected a proper conversion into a Python dict.")
                    .into_any()
                    .unbind())
            }
            Value::ServerError(error) => {
                let err_msg = error_message(&error.into());
                // Load the module that defines the request error.
                let module = py.import("glide.exceptions")?;
                // Retrieve the request error type.
                let request_error_type = module.getattr("RequestError")?;
                // Create an instance of the request error with the error message.
                let instance = request_error_type.call1((err_msg,))?;
                // Return the error instance as a PyObject.
                Ok(instance
                    .into_pyobject(py)
                    .expect("ServerError: expected a proper conversion into a Python int.")
                    .into_any()
                    .unbind())
            }
        }
    }

    #[pyfunction]
    pub fn value_from_pointer(py: Python, pointer: u64) -> PyResult<PyObject> {
        let value = unsafe { Box::from_raw(pointer as *mut Value) };
        resp_value_to_py(py, *value)
    }

    #[pyfunction]
    /// This function is for tests that require a value allocated on the heap.
    /// Should NOT be used in production.
    pub fn create_leaked_value(message: String) -> usize {
        let value = Value::SimpleString(message);
        from_mut(Box::leak(Box::new(value))) as usize
    }

    #[pyfunction]
    pub fn create_leaked_bytes_vec(args_vec: Vec<Bound<PyBytes>>) -> usize {
        // Convert the bytes vec -> Bytes vector
        let bytes_vec: Vec<Bytes> = args_vec
            .iter()
            .map(|v| {
                let bytes = v.as_bytes();
                Bytes::from(bytes.to_vec())
            })
            .collect();
        from_mut(Box::leak(Box::new(bytes_vec))) as usize
    }
    Ok(())
}
impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level::Error,
            logger_core::Level::Warn => Level::Warn,
            logger_core::Level::Info => Level::Info,
            logger_core::Level::Debug => Level::Debug,
            logger_core::Level::Trace => Level::Trace,
            logger_core::Level::Off => Level::Off,
        }
    }
}

impl From<Level> for logger_core::Level {
    fn from(level: Level) -> logger_core::Level {
        match level {
            Level::Error => logger_core::Level::Error,
            Level::Warn => logger_core::Level::Warn,
            Level::Info => logger_core::Level::Info,
            Level::Debug => logger_core::Level::Debug,
            Level::Trace => logger_core::Level::Trace,
            Level::Off => logger_core::Level::Off,
        }
    }
}

#[pyfunction]
pub fn log(log_level: Level, log_identifier: String, message: String) {
    logger_core::log(log_level.into(), log_identifier, message);
}

#[pyfunction]
#[pyo3(signature = (level=None, file_name=None))]
pub fn init(level: Option<Level>, file_name: Option<&str>) -> Level {
    let logger_level = logger_core::init(level.map(|level| level.into()), file_name);
    logger_level.into()
}
