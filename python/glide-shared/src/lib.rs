use pyo3::ffi;
use pyo3::prelude::*;
use redis::Value;
use std::os::raw::c_char;

type PyObject = Py<PyAny>;

/// CommandResponse layout — must match ffi/src/lib.rs `CommandResponse` exactly.
/// If you change this struct, you must also update the corresponding struct in ffi/src/lib.rs
/// and the CFFI declarations in python/glide-shared/glide_shared/_glide_ffi.py.
#[repr(C)]
struct CommandResponse {
    response_type: i32,
    _pad0: i32,
    int_value: i64,
    float_value: f64,
    bool_value: bool,
    _pad1: [u8; 7],
    string_value: *const c_char,
    string_value_len: i64,
    array_value: *const CommandResponse,
    array_value_len: i64,
    map_key: *const CommandResponse,
    map_value: *const CommandResponse,
    sets_value: *const CommandResponse,
    sets_value_len: i64,
    arena_ptr: *mut std::ffi::c_void,
}

/// Cached reference to glide_shared.exceptions.RequestError class.
/// Wrapped for Send+Sync safety — the pointer is valid for the process lifetime.
struct PyClassPtr(*mut ffi::PyObject);
unsafe impl Sync for PyClassPtr {}
unsafe impl Send for PyClassPtr {}

static REQUEST_ERROR_CLASS: std::sync::OnceLock<PyClassPtr> = std::sync::OnceLock::new();

/// Get (and cache) the RequestError class from glide_shared.exceptions.
unsafe fn get_request_error_class() -> *mut ffi::PyObject {
    REQUEST_ERROR_CLASS
        .get_or_init(|| unsafe {
            let mod_name =
                ffi::PyUnicode_FromStringAndSize(c"glide_shared.exceptions".as_ptr(), 23);
            if mod_name.is_null() {
                ffi::PyErr_Clear();
                return PyClassPtr(std::ptr::null_mut());
            }
            let module = ffi::PyImport_Import(mod_name);
            ffi::Py_DECREF(mod_name);
            if module.is_null() {
                ffi::PyErr_Clear();
                return PyClassPtr(std::ptr::null_mut());
            }
            let attr = ffi::PyUnicode_FromStringAndSize(c"RequestError".as_ptr(), 12);
            if attr.is_null() {
                ffi::Py_DECREF(module);
                ffi::PyErr_Clear();
                return PyClassPtr(std::ptr::null_mut());
            }
            let cls = ffi::PyObject_GetAttr(module, attr);
            ffi::Py_DECREF(attr);
            ffi::Py_DECREF(module);
            if cls.is_null() {
                ffi::PyErr_Clear();
                return PyClassPtr(std::ptr::null_mut());
            }
            PyClassPtr(cls)
        })
        .0
}

/// Response type values — must match ffi/src/lib.rs `ResponseType` enum exactly.
/// If you add or change variants, you must also update the ResponseType enum in ffi/src/lib.rs
/// and the CFFI declarations in python/glide-shared/glide_shared/_glide_ffi.py.
unsafe fn convert(resp: *const CommandResponse) -> *mut ffi::PyObject {
    if resp.is_null() {
        return unsafe { py_none() };
    }
    let r = unsafe { &*resp };
    match r.response_type {
        0 => unsafe { py_none() },
        1 => unsafe { ffi::PyLong_FromLongLong(r.int_value) },
        2 => unsafe { ffi::PyFloat_FromDouble(r.float_value) },
        3 => unsafe { ffi::PyBool_FromLong(r.bool_value as std::ffi::c_long) },
        4 => unsafe {
            py_bytes(std::slice::from_raw_parts(
                r.string_value as *const u8,
                r.string_value_len as usize,
            ))
        },
        5 => unsafe {
            let len = r.array_value_len as isize;
            let list = ffi::PyList_New(len);
            if list.is_null() {
                return std::ptr::null_mut();
            }
            for i in 0..len {
                let item = convert(r.array_value.offset(i));
                if item.is_null() {
                    ffi::Py_DECREF(list);
                    return std::ptr::null_mut();
                }
                ffi::PyList_SetItem(list, i, item);
            }
            list
        },
        6 => unsafe {
            // Map — wrapper layout: array_value[i].map_key / .map_value
            let n = r.array_value_len as isize;
            let dict = ffi::PyDict_New();
            if dict.is_null() {
                return std::ptr::null_mut();
            }
            for i in 0..n {
                let wrapper = &*r.array_value.offset(i);
                let key = convert(wrapper.map_key);
                if key.is_null() {
                    ffi::Py_DECREF(dict);
                    return std::ptr::null_mut();
                }
                let val = convert(wrapper.map_value);
                if val.is_null() {
                    ffi::Py_DECREF(key);
                    ffi::Py_DECREF(dict);
                    return std::ptr::null_mut();
                }
                ffi::PyDict_SetItem(dict, key, val);
                ffi::Py_DECREF(key);
                ffi::Py_DECREF(val);
            }
            dict
        },
        7 => unsafe {
            let len = r.sets_value_len as isize;
            let set = ffi::PySet_New(std::ptr::null_mut());
            if set.is_null() {
                return std::ptr::null_mut();
            }
            for i in 0..len {
                let item = convert(r.sets_value.offset(i));
                if item.is_null() {
                    ffi::Py_DECREF(set);
                    return std::ptr::null_mut();
                }
                ffi::PySet_Add(set, item);
                ffi::Py_DECREF(item);
            }
            set
        },
        8 => unsafe { py_ok() },
        9 => unsafe {
            py_request_error(std::slice::from_raw_parts(
                r.string_value as *const u8,
                r.string_value_len as usize,
            ))
        },
        _ => unsafe { py_none() },
    }
}

unsafe fn py_none() -> *mut ffi::PyObject {
    unsafe {
        let n = ffi::Py_None();
        ffi::Py_INCREF(n);
        n
    }
}

unsafe fn py_bytes(data: &[u8]) -> *mut ffi::PyObject {
    unsafe { ffi::PyBytes_FromStringAndSize(data.as_ptr() as *const c_char, data.len() as isize) }
}

/// `RequestError(message)`, or the raw message when the class cannot be loaded, or `None`
/// when constructing the instance fails.
unsafe fn py_request_error(message: &[u8]) -> *mut ffi::PyObject {
    unsafe {
        let cls = get_request_error_class();
        if cls.is_null() {
            return py_bytes(message);
        }
        let msg = ffi::PyUnicode_FromStringAndSize(
            message.as_ptr() as *const c_char,
            message.len() as isize,
        );
        let obj = if msg.is_null() {
            std::ptr::null_mut()
        } else {
            let obj =
                ffi::PyObject_CallFunctionObjArgs(cls, msg, std::ptr::null_mut::<ffi::PyObject>());
            ffi::Py_DECREF(msg);
            obj
        };
        if obj.is_null() {
            ffi::PyErr_Clear();
            return py_none();
        }
        obj
    }
}

/// Interned once so that `result is OK` identity checks pass.
static OK_STRING: std::sync::OnceLock<PyClassPtr> = std::sync::OnceLock::new();

unsafe fn py_ok() -> *mut ffi::PyObject {
    let ok = OK_STRING
        .get_or_init(|| unsafe {
            let s = ffi::PyUnicode_FromStringAndSize(c"OK".as_ptr(), 2);
            if s.is_null() {
                ffi::PyErr_Clear();
                return PyClassPtr(std::ptr::null_mut());
            }
            let mut interned = s;
            ffi::PyUnicode_InternInPlace(&mut interned);
            PyClassPtr(interned)
        })
        .0;
    if ok.is_null() {
        return std::ptr::null_mut();
    }
    unsafe { ffi::Py_INCREF(ok) };
    ok
}

unsafe fn py_list_of(items: Vec<Value>) -> *mut ffi::PyObject {
    unsafe {
        let list = ffi::PyList_New(items.len() as isize);
        if list.is_null() {
            return std::ptr::null_mut();
        }
        for (i, item) in items.into_iter().enumerate() {
            let obj = convert_value(item);
            if obj.is_null() {
                ffi::Py_DECREF(list);
                return std::ptr::null_mut();
            }
            ffi::PyList_SetItem(list, i as isize, obj);
        }
        list
    }
}

unsafe fn py_dict_of(entries: Vec<(Value, Value)>) -> *mut ffi::PyObject {
    unsafe {
        let dict = ffi::PyDict_New();
        if dict.is_null() {
            return std::ptr::null_mut();
        }
        for (key, value) in entries {
            let k = convert_value(key);
            if k.is_null() {
                ffi::Py_DECREF(dict);
                return std::ptr::null_mut();
            }
            let v = convert_value(value);
            if v.is_null() {
                ffi::Py_DECREF(k);
                ffi::Py_DECREF(dict);
                return std::ptr::null_mut();
            }
            ffi::PyDict_SetItem(dict, k, v);
            ffi::Py_DECREF(k);
            ffi::Py_DECREF(v);
        }
        dict
    }
}

/// A `redis::Value` straight to Python objects, in the shapes `convert` produces from the
/// arena: bytes for every string kind, an interned "OK", `RequestError` instances for server
/// errors, a `{b"kind", b"values"}` dict for pushes. The arena path never encoded attributes
/// or big numbers; here they match the async client, a `{"value", "attributes"}` dict and a
/// Python int.
unsafe fn convert_value(value: Value) -> *mut ffi::PyObject {
    unsafe {
        match value {
            Value::Nil => py_none(),
            Value::Int(n) => ffi::PyLong_FromLongLong(n),
            Value::Double(f) => ffi::PyFloat_FromDouble(f),
            Value::Boolean(b) => ffi::PyBool_FromLong(b as std::ffi::c_long),
            Value::BulkString(data) => py_bytes(&data),
            Value::SimpleString(text) | Value::VerbatimString { text, .. } => {
                py_bytes(text.as_bytes())
            }
            Value::Okay => py_ok(),
            Value::Array(items) => py_list_of(items),
            Value::Map(entries) => py_dict_of(entries),
            Value::Set(items) => {
                let set = ffi::PySet_New(std::ptr::null_mut());
                if set.is_null() {
                    return std::ptr::null_mut();
                }
                for item in items {
                    let obj = convert_value(item);
                    if obj.is_null() {
                        ffi::Py_DECREF(set);
                        return std::ptr::null_mut();
                    }
                    ffi::PySet_Add(set, obj);
                    ffi::Py_DECREF(obj);
                }
                set
            }
            Value::Attribute { data, attributes } => {
                let dict = ffi::PyDict_New();
                if dict.is_null() {
                    return std::ptr::null_mut();
                }
                let value = convert_value(*data);
                let attrs = py_dict_of(attributes);
                if value.is_null() || attrs.is_null() {
                    ffi::Py_XDECREF(value);
                    ffi::Py_XDECREF(attrs);
                    ffi::Py_DECREF(dict);
                    return std::ptr::null_mut();
                }
                ffi::PyDict_SetItemString(dict, c"value".as_ptr(), value);
                ffi::PyDict_SetItemString(dict, c"attributes".as_ptr(), attrs);
                ffi::Py_DECREF(value);
                ffi::Py_DECREF(attrs);
                dict
            }
            Value::BigNumber(n) => {
                let digits = std::ffi::CString::new(n.to_string()).expect("decimal digits");
                ffi::PyLong_FromString(digits.as_ptr(), std::ptr::null_mut(), 10)
            }
            Value::Push { kind, data } => py_dict_of(vec![
                (
                    Value::BulkString(b"kind".as_slice().into()),
                    Value::BulkString(format!("{kind:?}").into_bytes().into()),
                ),
                (
                    Value::BulkString(b"values".as_slice().into()),
                    Value::Array(data),
                ),
            ]),
            Value::ServerError(error) => {
                let message = redis::RedisError::from(error).to_string();
                py_request_error(message.as_bytes())
            }
        }
    }
}

/// Convert the boxed `redis::Value` a value-results client receives in `CommandResult.value`
/// into Python objects. Takes ownership of the box, so the caller must null the field before
/// `free_command_result`.
#[pyfunction]
fn parse_value(py: Python<'_>, ptr: usize) -> PyResult<PyObject> {
    if ptr == 0 {
        return Ok(py.None());
    }
    let value = unsafe { Box::from_raw(ptr as *mut Value) };
    let raw = unsafe { convert_value(*value) };
    if raw.is_null() {
        return Err(PyErr::take(py).unwrap_or_else(|| {
            pyo3::exceptions::PyRuntimeError::new_err("failed to convert the reply")
        }));
    }
    Ok(unsafe { Bound::from_owned_ptr(py, raw).unbind() })
}

/// Parse a CommandResponse and return (result, arena_ptr).
/// The caller must call free_response_arena(arena_ptr) when done.
#[pyfunction]
fn parse_response(py: Python<'_>, ptr: usize) -> (PyObject, usize) {
    if ptr == 0 {
        return (py.None(), 0);
    }
    let resp = ptr as *const CommandResponse;
    let arena_ptr = unsafe { (*resp).arena_ptr as usize };
    let raw = unsafe { convert(resp) };
    let obj = if raw.is_null() {
        py.None()
    } else {
        unsafe { Bound::from_owned_ptr(py, raw).unbind() }
    };
    (obj, arena_ptr)
}

/// Size and alignment of `Value` in this build. It must equal the ffi library's
/// `value_abi_token` before `parse_value` may be used on its values.
#[pyfunction]
fn value_abi_token() -> u64 {
    ((std::mem::size_of::<Value>() as u64) << 32) | std::mem::align_of::<Value>() as u64
}

#[pymodule(gil_used = false)]
fn _fast_response(m: &Bound<'_, PyModule>) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(parse_response, m)?)?;
    m.add_function(wrap_pyfunction!(parse_value, m)?)?;
    m.add_function(wrap_pyfunction!(value_abi_token, m)?)?;
    Ok(())
}
