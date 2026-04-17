use pyo3::ffi;
use pyo3::prelude::*;
use std::os::raw::c_char;

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
static mut REQUEST_ERROR_CLASS: *mut ffi::PyObject = std::ptr::null_mut();

/// Get (and cache) the RequestError class from glide_shared.exceptions.
unsafe fn get_request_error_class() -> *mut ffi::PyObject {
    unsafe {
        if REQUEST_ERROR_CLASS.is_null() {
            let mod_name = ffi::PyUnicode_FromStringAndSize(
                b"glide_shared.exceptions\0".as_ptr() as *const c_char,
                23,
            );
            if mod_name.is_null() {
                ffi::PyErr_Clear();
                return std::ptr::null_mut();
            }
            let module = ffi::PyImport_Import(mod_name);
            ffi::Py_DECREF(mod_name);
            if module.is_null() {
                ffi::PyErr_Clear();
                return std::ptr::null_mut();
            }
            let attr = ffi::PyUnicode_FromStringAndSize(
                b"RequestError\0".as_ptr() as *const c_char,
                12,
            );
            if attr.is_null() {
                ffi::Py_DECREF(module);
                ffi::PyErr_Clear();
                return std::ptr::null_mut();
            }
            let cls = ffi::PyObject_GetAttr(module, attr);
            ffi::Py_DECREF(attr);
            ffi::Py_DECREF(module);
            if cls.is_null() {
                ffi::PyErr_Clear();
                return std::ptr::null_mut();
            }
            REQUEST_ERROR_CLASS = cls; // keep one reference
        }
        REQUEST_ERROR_CLASS
    }
}

/// Response type values — must match ffi/src/lib.rs `ResponseType` enum exactly.
/// If you add or change variants, you must also update the ResponseType enum in ffi/src/lib.rs
/// and the CFFI declarations in python/glide-shared/glide_shared/_glide_ffi.py.
unsafe fn convert(resp: *const CommandResponse) -> *mut ffi::PyObject {
    if resp.is_null() {
        return unsafe { { let n = ffi::Py_None(); ffi::Py_INCREF(n); n } };
    }
    let r = unsafe { &*resp };
    match r.response_type {
        0 => unsafe { { let n = ffi::Py_None(); ffi::Py_INCREF(n); n } },
        1 => unsafe { ffi::PyLong_FromLongLong(r.int_value) },
        2 => unsafe { ffi::PyFloat_FromDouble(r.float_value) },
        3 => unsafe { ffi::PyBool_FromLong(r.bool_value as std::ffi::c_long) },
        4 => unsafe {
            ffi::PyBytes_FromStringAndSize(r.string_value, r.string_value_len as isize)
        },
        5 => unsafe {
            let len = r.array_value_len as isize;
            let list = ffi::PyList_New(len);
            if list.is_null() { return std::ptr::null_mut(); }
            for i in 0..len {
                let item = convert(r.array_value.offset(i));
                if item.is_null() { ffi::Py_DECREF(list); return std::ptr::null_mut(); }
                ffi::PyList_SetItem(list, i, item);
            }
            list
        },
        6 => unsafe {
            // Map — wrapper layout: array_value[i].map_key / .map_value
            let n = r.array_value_len as isize;
            let dict = ffi::PyDict_New();
            if dict.is_null() { return std::ptr::null_mut(); }
            for i in 0..n {
                let wrapper = &*r.array_value.offset(i);
                let key = convert(wrapper.map_key);
                if key.is_null() { ffi::Py_DECREF(dict); return std::ptr::null_mut(); }
                let val = convert(wrapper.map_value);
                if val.is_null() { ffi::Py_DECREF(key); ffi::Py_DECREF(dict); return std::ptr::null_mut(); }
                ffi::PyDict_SetItem(dict, key, val);
                ffi::Py_DECREF(key);
                ffi::Py_DECREF(val);
            }
            dict
        },
        7 => unsafe {
            let len = r.sets_value_len as isize;
            let set = ffi::PySet_New(std::ptr::null_mut());
            if set.is_null() { return std::ptr::null_mut(); }
            for i in 0..len {
                let item = convert(r.sets_value.offset(i));
                if item.is_null() { ffi::Py_DECREF(set); return std::ptr::null_mut(); }
                ffi::PySet_Add(set, item);
                ffi::Py_DECREF(item);
            }
            set
        },
        8 => unsafe {
            let s = ffi::PyUnicode_FromStringAndSize(b"OK\0".as_ptr() as *const c_char, 2);
            if !s.is_null() {
                // Intern so that `result is OK` identity checks pass
                let mut interned = s;
                ffi::PyUnicode_InternInPlace(&mut interned);
                interned
            } else {
                s
            }
        },
        9 => unsafe {
            // Error response: create a RequestError(message) instance
            let cls = get_request_error_class();
            if cls.is_null() {
                // Fallback: return raw bytes if class not found
                return ffi::PyBytes_FromStringAndSize(r.string_value, r.string_value_len as isize);
            }
            let msg = ffi::PyUnicode_FromStringAndSize(r.string_value, r.string_value_len as isize);
            if msg.is_null() { ffi::PyErr_Clear(); return ffi::Py_None(); }
            let args = ffi::PyTuple_New(1);
            if args.is_null() { ffi::Py_DECREF(msg); ffi::PyErr_Clear(); return ffi::Py_None(); }
            ffi::PyTuple_SetItem(args, 0, msg); // steals ref to msg
            let obj = ffi::PyObject_CallObject(cls, args);
            ffi::Py_DECREF(args);
            if obj.is_null() { ffi::PyErr_Clear(); return ffi::Py_None(); }
            obj
        },
        _ => unsafe { { let n = ffi::Py_None(); ffi::Py_INCREF(n); n } },
    }
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
    let obj = if raw.is_null() { py.None() } else { unsafe { PyObject::from_owned_ptr(py, raw) } };
    (obj, arena_ptr)
}

#[pymodule]
fn _fast_response(m: &Bound<'_, PyModule>) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(parse_response, m)?)?;
    Ok(())
}
