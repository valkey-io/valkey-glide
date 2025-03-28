use crate::log_error;
use std::ffi::{c_int, c_ulonglong, c_void, CString};
use std::fmt::Debug;
use std::mem::forget;
use std::os::raw::c_char;
use std::panic::catch_unwind;
use std::ptr::null;
use std::str::FromStr;
use tracing_core::span::{Attributes, Id, Record};
use tracing_core::{field, Event, Field, Level, Metadata};
use tracing_subscriber::layer::Context;
use tracing_subscriber::{Layer, Registry};

fn to_ptr_or_null(input: &str) -> *const c_char {
    match CString::from_str(input) {
        Ok(d) => d.into_raw() as *const std::os::raw::c_char,
        Err(_) => null(),
    }
}

/// Simple Key-Value pair for passing via FFI
#[repr(C)]
pub struct KeyValuePair {
    pub key: *const c_char,
    pub key_length: c_int,
    pub value: *const c_char,
    pub value_length: c_int,
}

/// Simplified severity enum for FFI
#[repr(C)]
pub enum ESeverity {
    /// The "trace" level.
    ///
    /// Designates very low priority, often extremely verbose, information.
    Trace = 0,
    /// The "debug" level.
    ///
    /// Designates lower priority information.
    Debug = 1,
    /// The "info" level.
    ///
    /// Designates useful information.
    Info = 2,
    /// The "warn" level.
    ///
    /// Designates hazardous situations.
    Warn = 3,
    /// The "error" level.
    ///
    /// Designates very serious errors.
    Error = 4,
}

#[repr(C)]
pub enum EEventDataKind {
    Unknown,
    IsSpan,
    IsEvent,
}
#[repr(C)]
pub struct EventData {
    /// The name of the span described by this metadata.
    pub name: *const c_char,
    /// The length of `name`
    pub name_length: c_int,
    /// The part of the system that the span that this metadata describes
    /// occurred in.
    pub target: *const c_char,
    /// The length of `target`
    pub target_length: c_int,
    /// The severity of the described span.
    pub severity: ESeverity,
    /// The name of the Rust module where the span occurred, or `nullptr` if this
    /// could not be determined.
    pub module_path: *const c_char,
    /// The length of `module_path`
    pub module_path_length: c_int,
    /// The name of the source code file where the span occurred, or `nullptr` if
    /// this could not be determined.
    pub file: *const c_char,
    /// The length of `file`
    pub file_length: c_int,
    /// The line number in the source code file where the span occurred, or
    /// -1 if this could not be determined.
    pub line: c_int,
    /// The kind of the call-site.
    pub kind: EEventDataKind,
}
#[repr(C)]
pub struct Fields {
    /// The names of the key-value fields attached to the described span or
    /// event.
    /// This may be `nullptr` if empty
    pub fields: *const KeyValuePair,
    /// The length of fields
    pub fields_length: c_int,
}

#[repr(C)]
pub enum ESpanContextKind {
    /// The new span will be a root span.
    Root,
    /// The new span will be rooted in the current span.
    Current,
    /// The new span has an explicitly-specified parent.
    Explicit,
}

#[repr(C)]
pub struct SpanContext {
    pub kind: ESpanContextKind,
    /// Contains the parent_id if [ESpanContextKind::Explicit] is used
    pub parent_id: c_ulonglong,
}

pub type IsEnabledCallback =
    unsafe extern "C-unwind" fn(ref_data: *mut c_void, in_event_data: *const EventData) -> bool;

pub type NewSpawnCallback = unsafe extern "C-unwind" fn(
    ref_data: *mut c_void,
    in_message: *const c_char,
    in_message_length: c_int,
    in_fields: Fields,
    in_event_data: *const EventData,
    in_span_context: SpanContext,
    in_span_id: c_ulonglong,
);
pub type RecordCallback = unsafe extern "C-unwind" fn(
    ref_data: *mut c_void,
    in_message: *const c_char,
    in_message_length: c_int,
    in_fields: Fields,
    in_span_id: c_ulonglong,
);
pub type EventCallback = unsafe extern "C-unwind" fn(
    ref_data: *mut c_void,
    in_message: *const c_char,
    in_message_length: c_int,
    in_fields: Fields,
    in_event_data: *const EventData,
    in_span_context: SpanContext,
);
pub type EnterCallback =
    unsafe extern "C-unwind" fn(ref_data: *mut c_void, in_span_id: c_ulonglong);
pub type ExitCallback = unsafe extern "C-unwind" fn(ref_data: *mut c_void, in_span_id: c_ulonglong);

pub struct CallbackSubscriber {
    pub data: *mut c_void,
    pub is_enabled_callback: Option<IsEnabledCallback>,
    pub new_spawn_callback: Option<NewSpawnCallback>,
    pub record_callback: Option<RecordCallback>,
    pub event_callback: Option<EventCallback>,
    pub enter_callback: Option<EnterCallback>,
    pub exit_callback: Option<ExitCallback>,
}

struct CollectingVisitor {
    message: Option<String>,
    key_value_pairs: Vec<(String, String)>,
}

impl CollectingVisitor {
    fn new() -> Self {
        Self {
            message: None,
            key_value_pairs: vec![],
        }
    }
}

impl field::Visit for CollectingVisitor {
    fn record_debug(&mut self, field: &Field, value: &dyn Debug) {
        let name = field.name();
        if field.index() == 0 && name == "message" {
            self.message = Some(format!("{:?}", value));
        } else {
            let value = format!("{:?}", value);
            self.key_value_pairs.push((name.to_string(), value));
        }
    }
}

impl From<&Level> for ESeverity {
    fn from(value: &Level) -> Self {
        if *value == Level::TRACE {
            ESeverity::Trace
        } else if *value == Level::DEBUG {
            ESeverity::Debug
        } else if *value == Level::INFO {
            ESeverity::Info
        } else if *value == Level::WARN {
            ESeverity::Warn
        } else if *value == Level::ERROR {
            ESeverity::Error
        } else {
            ESeverity::Trace
        }
    }
}

impl From<&[(String, String)]> for Fields {
    fn from(values: &[(String, String)]) -> Self {
        let (pairs_ptr, pairs_length) = if values.is_empty() {
            (null(), 0)
        } else {
            let mut pairs = vec![];
            pairs.shrink_to_fit();
            for (key, value) in values.iter() {
                assert!(key.len() <= c_int::MAX as usize);
                assert!(value.len() <= c_int::MAX as usize);
                pairs.push(KeyValuePair {
                    key: to_ptr_or_null(key),
                    key_length: key.len() as c_int,
                    value: to_ptr_or_null(value.as_str()),
                    value_length: value.len() as c_int,
                })
            }
            assert!(pairs.len() <= c_int::MAX as usize);
            let pairs_ptr = pairs.as_ptr();
            let pairs_length = pairs.len() as c_int;
            forget(pairs);
            (pairs_ptr, pairs_length)
        };
        Self {
            fields: pairs_ptr,
            fields_length: pairs_length,
        }
    }
}

impl From<&Metadata<'_>> for EventData {
    fn from(value: &Metadata<'_>) -> Self {
        // Assert length do not exceed c_int
        assert!(value.name().len() <= c_int::MAX as usize);
        assert!(match value.module_path() {
            None => true,
            Some(module_path) => module_path.len() <= c_int::MAX as usize,
        });
        assert!(match value.file() {
            None => true,
            Some(file) => file.len() <= c_int::MAX as usize,
        });
        Self {
            name: value.name().as_ptr() as *const c_char,
            name_length: value.name().len() as c_int,
            target: value.target().as_ptr() as *const c_char,
            target_length: value.target().len() as c_int,
            severity: ESeverity::from(value.level()),
            module_path: value
                .module_path()
                .map_or_else(|| null(), |v| v.as_ptr() as *const c_char),
            module_path_length: value.module_path().map_or_else(|| 0, |v| v.len() as c_int),
            file: value
                .file()
                .map_or_else(|| null(), |v| v.as_ptr() as *const c_char),
            file_length: value.file().map_or_else(|| 0, |v| v.len() as c_int),
            line: value.line().map_or_else(|| -1, |v| v as c_int),
            kind: if value.is_event() {
                EEventDataKind::IsEvent
            } else if value.is_span() {
                EEventDataKind::IsSpan
            } else {
                EEventDataKind::Unknown
            },
        }
    }
}

impl Drop for Fields {
    fn drop(&mut self) {
        if !self.fields.is_null() {
            unsafe {
                let vec = Vec::from_raw_parts(
                    self.fields as *mut KeyValuePair,
                    self.fields_length as usize,
                    self.fields_length as usize,
                );
                for i in 0..self.fields_length {
                    let item = &vec[i as usize];
                    let key_ptr = item.key as *mut c_char;
                    let value_ptr = item.value as *mut c_char;
                    _ = CString::from_raw(key_ptr);
                    _ = CString::from_raw(value_ptr);
                }
                drop(vec);
            }
        }
    }
}
impl From<&Event<'_>> for SpanContext {
    fn from(value: &Event) -> Self {
        Self {
            kind: if value.is_root() {
                ESpanContextKind::Root
            } else if value.is_contextual() {
                ESpanContextKind::Current
            } else {
                ESpanContextKind::Explicit
            },
            parent_id: value
                .parent()
                .map_or_else(|| 0, |v| v.into_u64() as c_ulonglong),
        }
    }
}

unsafe impl Send for CallbackSubscriber {}

unsafe impl Sync for CallbackSubscriber {}
impl Layer<Registry> for CallbackSubscriber {
    fn enabled(&self, metadata: &Metadata<'_>, _ctx: Context<'_, Registry>) -> bool {
        if self.is_enabled_callback.is_none() {
            return true;
        }
        let event_data = EventData::from(metadata);
        unsafe { self.is_enabled_callback.unwrap()(self.data, &event_data) }
    }

    fn on_new_span(&self, span: &Attributes<'_>, id: &Id, _ctx: Context<'_, Registry>) {
        if self.new_spawn_callback.is_none() {
            return;
        }
        let event_data = EventData::from(span.metadata());
        let mut visitor = CollectingVisitor::new();
        span.record(&mut visitor);
        let fields = Fields::from(visitor.key_value_pairs.as_slice());
        let (message_len, message_ptr) = visitor.message.map_or_else(
            || (0, null()),
            |v| {
                assert!(v.len() <= c_int::MAX as usize);
                (v.len() as c_int, to_ptr_or_null(v.as_str()))
            },
        );
        match catch_unwind(|| unsafe {
            self.new_spawn_callback.unwrap()(
                self.data,
                message_ptr,
                message_len,
                fields,
                &event_data,
                SpanContext {
                    kind: ESpanContextKind::Root,
                    parent_id: 0,
                },
                id.into_u64() as c_ulonglong,
            );
        }) {
            Err(e) => log_error("csharp_ffi", format!("Panic in callback: {:?}", e)),
            _ => {}
        };
        for i in 0..message_len {
            unsafe {
                let message_ptr = message_ptr.add(i as usize);
                _ = CString::from_raw(message_ptr as *mut c_char);
            }
        }
    }

    fn on_record(&self, span: &Id, values: &Record<'_>, _ctx: Context<'_, Registry>) {
        if self.record_callback.is_none() {
            return;
        }
        let mut visitor = CollectingVisitor::new();
        values.record(&mut visitor);
        let fields = Fields::from(visitor.key_value_pairs.as_slice());
        let (message_len, message_ptr) = visitor.message.map_or_else(
            || (0, null()),
            |v| {
                assert!(v.len() <= c_int::MAX as usize);
                (v.len() as c_int, to_ptr_or_null(v.as_str()))
            },
        );

        match catch_unwind(|| unsafe {
            self.record_callback.unwrap()(
                self.data,
                message_ptr,
                message_len,
                fields,
                span.into_u64() as c_ulonglong,
            )
        }) {
            Err(e) => log_error("csharp_ffi", format!("Panic in callback: {:?}", e)),
            _ => {}
        };
        for i in 0..message_len {
            unsafe {
                let message_ptr = message_ptr.add(i as usize);
                _ = CString::from_raw(message_ptr as *mut c_char);
            }
        }
    }

    fn on_event(&self, event: &Event<'_>, _ctx: Context<'_, Registry>) {
        if self.event_callback.is_none() {
            return;
        }
        let mut visitor = CollectingVisitor::new();
        event.record(&mut visitor);
        let fields = Fields::from(visitor.key_value_pairs.as_slice());
        let (message_len, message_ptr) = visitor.message.map_or_else(
            || (0, null()),
            |v| {
                assert!(v.len() <= c_int::MAX as usize);
                (v.len() as c_int, to_ptr_or_null(v.as_str()))
            },
        );
        let event_data = EventData::from(event.metadata());
        let span_context = SpanContext::from(event);
        match catch_unwind(|| unsafe {
            self.event_callback.unwrap()(
                self.data,
                message_ptr,
                message_len,
                fields,
                &event_data,
                span_context,
            )
        }) {
            Err(e) => log_error("csharp_ffi", format!("Panic in callback: {:?}", e)),
            _ => {}
        };
        for i in 0..message_len {
            unsafe {
                let message_ptr = message_ptr.add(i as usize);
                _ = CString::from_raw(message_ptr as *mut c_char);
            }
        }
    }

    fn on_enter(&self, span: &Id, _ctx: Context<'_, Registry>) {
        if self.enter_callback.is_none() {
            return;
        }
        unsafe { self.enter_callback.unwrap()(self.data, span.into_u64() as c_ulonglong) }
    }

    fn on_exit(&self, span: &Id, _ctx: Context<'_, Registry>) {
        if self.exit_callback.is_none() {
            return;
        }
        unsafe { self.exit_callback.unwrap()(self.data, span.into_u64() as c_ulonglong) }
    }
}
