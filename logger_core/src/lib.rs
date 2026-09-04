/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use once_cell::sync::OnceCell;
use serde_json::{Map as JsonMap, Value as JsonValue};
use std::{
    fmt,
    path::{Path, PathBuf},
    sync::RwLock,
    sync::atomic::{AtomicUsize, Ordering},
};
use tracing::{
    self, Event, Subscriber, event,
    field::{Field, Visit},
};
use tracing_appender::rolling::{RollingFileAppender, RollingWriter, Rotation};
use tracing_subscriber::{
    Registry,
    filter::Filtered,
    fmt::{
        FmtContext, FormatEvent, FormatFields, Layer,
        format::{DefaultFields, Format, Writer},
    },
    layer::Layered,
    registry::LookupSpan,
};

use tracing_subscriber::{
    self,
    filter::{self, LevelFilter},
    prelude::*,
    reload::{self, Handle},
};

use std::str::FromStr;

// Layer-Filter pair determines whether a log will be collected
type InnerFiltered =
    Filtered<Layer<Registry, DefaultFields, GlideLogFormat>, LevelFilter, Registry>;
// A Reloadable pair of layer-filter
type InnerLayered = Layered<reload::Layer<InnerFiltered, Registry>, Registry>;
// A reloadable layer of subscriber to a rolling file
type FileReload = Handle<
    Filtered<
        Layer<InnerLayered, DefaultFields, GlideLogFormat, LazyRollingFileAppender>,
        LevelFilter,
        InnerLayered,
    >,
    InnerLayered,
>;

pub struct Reloads {
    console_reload: RwLock<reload::Handle<InnerFiltered, Registry>>,
    file_reload: RwLock<FileReload>,
}

pub struct InitiateOnce {
    init_once: OnceCell<Reloads>,
}

pub static INITIATE_ONCE: InitiateOnce = InitiateOnce {
    init_once: OnceCell::new(),
};

/// Cheap, cached effective max log verbosity (0=Off,1=Error,2=Warn,3=Info,4=Debug,5=Trace).
/// Read on every lazy-log macro invocation to short-circuit the (expensive, RwLock-guarded)
/// reload-subscriber `enabled` check when the level is disabled — the common hot-path case
/// (e.g. per-command trace/debug logs while running at the default Warn level). Updated by
/// `init` whenever the effective level changes; set as an UPPER BOUND on the true effective
/// level so it can never suppress a log that should be emitted.
pub static CURRENT_MAX_VERBOSITY: AtomicUsize = AtomicUsize::new(0);

/// Maps `tracing::Level` to the verbosity ordering used by `CURRENT_MAX_VERBOSITY`.
#[inline]
pub fn level_may_be_enabled(level: tracing::Level) -> bool {
    let needed = match level {
        tracing::Level::ERROR => 1,
        tracing::Level::WARN => 2,
        tracing::Level::INFO => 3,
        tracing::Level::DEBUG => 4,
        tracing::Level::TRACE => 5,
    };
    CURRENT_MAX_VERBOSITY.load(Ordering::Relaxed) >= needed
}

const FILE_DIRECTORY: &str = "glide-logs";
const ENV_GLIDE_LOG_DIR: &str = "GLIDE_LOG_DIR";
const STRUCTURED_PAYLOAD_FIELD: &str = "glide_structured_payload";

#[derive(Default)]
/// Formats structured events as JSON and delegates all other events to the text formatter.
struct GlideLogFormat {
    text_format: Format,
}

impl<S, N> FormatEvent<S, N> for GlideLogFormat
where
    S: Subscriber + for<'span> LookupSpan<'span>,
    N: for<'writer> FormatFields<'writer> + 'static,
{
    /// Writes a structured payload as one JSON line or uses the existing text format.
    fn format_event(
        &self,
        ctx: &FmtContext<'_, S, N>,
        mut writer: Writer<'_>,
        event: &Event<'_>,
    ) -> fmt::Result {
        let mut visitor = StructuredPayloadVisitor::default();
        event.record(&mut visitor);

        if let Some(payload) = visitor.payload {
            writeln!(writer, "{payload}")?;
            return Ok(());
        }

        self.text_format.format_event(ctx, writer, event)
    }
}

#[derive(Default)]
/// Extracts the private structured payload field from a tracing event.
struct StructuredPayloadVisitor {
    payload: Option<String>,
}

impl Visit for StructuredPayloadVisitor {
    /// Records the structured payload when tracing provides it as a string field.
    fn record_str(&mut self, field: &Field, value: &str) {
        if field.name() == STRUCTURED_PAYLOAD_FIELD {
            self.payload = Some(value.to_string());
        }
    }

    /// Records the structured payload when tracing provides it through debug formatting.
    fn record_debug(&mut self, field: &Field, value: &dyn fmt::Debug) {
        if field.name() == STRUCTURED_PAYLOAD_FIELD {
            self.payload = Some(format!("{value:?}"));
        }
    }
}

/// Wraps [RollingFileAppender] to defer initialization until logging is required,
/// allowing [init] to disable file logging on read-only filesystems.
/// This is needed because [RollingFileAppender] tries to create the log directory on initialization.
struct LazyRollingFileAppender {
    file_appender: OnceCell<RollingFileAppender>,
    rotation: Rotation,
    directory: PathBuf,
    filename_prefix: PathBuf,
}

impl LazyRollingFileAppender {
    fn new(
        rotation: Rotation,
        directory: impl AsRef<Path>,
        filename_prefix: impl AsRef<Path>,
    ) -> LazyRollingFileAppender {
        LazyRollingFileAppender {
            file_appender: OnceCell::new(),
            rotation,
            directory: directory.as_ref().to_path_buf(),
            filename_prefix: filename_prefix.as_ref().to_path_buf(),
        }
    }
}

impl<'a> tracing_subscriber::fmt::writer::MakeWriter<'a> for LazyRollingFileAppender {
    type Writer = RollingWriter<'a>;
    fn make_writer(&'a self) -> Self::Writer {
        let file_appender = self.file_appender.get_or_init(|| {
            RollingFileAppender::new(
                self.rotation.clone(),
                self.directory.clone(),
                self.filename_prefix.clone(),
            )
        });
        file_appender.make_writer()
    }
}

#[derive(Debug)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
    Off = 5,
}

#[derive(Debug, Clone)]
/// A typed key-value field included in a structured log event.
pub struct StructuredField {
    key: &'static str,
    value: JsonValue,
}

/// Creates a structured field from a JSON-compatible value.
pub fn structured_field<Value: Into<JsonValue>>(
    key: &'static str,
    value: Value,
) -> StructuredField {
    StructuredField {
        key,
        value: value.into(),
    }
}

/// Creates a borrowed collection of typed fields for [`log_structured`].
#[macro_export]
macro_rules! structured_fields {
    ($($key:literal => $value:expr),* $(,)?) => {
        &[$($crate::structured_field($key, $value)),*]
    };
}

/// Builds the single-line JSON payload written for a structured event.
fn structured_payload(
    log_level: &Level,
    log_identifier: &str,
    fields: &[StructuredField],
) -> String {
    let mut payload = JsonMap::with_capacity(fields.len() + 3);
    for field in fields {
        payload.insert(field.key.to_string(), field.value.clone());
    }
    payload.insert("glide_structured".to_string(), JsonValue::Bool(true));
    payload.insert(
        "glide_event".to_string(),
        JsonValue::String(log_identifier.to_string()),
    );
    payload.insert(
        "level".to_string(),
        JsonValue::String(log_level.as_str().to_string()),
    );
    JsonValue::Object(payload).to_string()
}

impl Level {
    fn to_filter(&self) -> filter::LevelFilter {
        match self {
            Level::Trace => LevelFilter::TRACE,
            Level::Debug => LevelFilter::DEBUG,
            Level::Info => LevelFilter::INFO,
            Level::Warn => LevelFilter::WARN,
            Level::Error => LevelFilter::ERROR,
            Level::Off => LevelFilter::OFF,
        }
    }

    /// Returns the lowercase level name stored in structured events.
    fn as_str(&self) -> &'static str {
        match self {
            Level::Error => "error",
            Level::Warn => "warn",
            Level::Info => "info",
            Level::Debug => "debug",
            Level::Trace => "trace",
            Level::Off => "off",
        }
    }
}

/// Attempt to read a directory path from an environment variable. If the environment variable `envname` exists
/// and contains a valid path - this function will create and return that path. In any case of failure,
/// this method returns `None` (e.g. the environment variable exists but contains an empty path etc)
pub fn create_directory_from_env(envname: &str) -> Option<String> {
    let Ok(dirpath) = std::env::var(envname) else {
        return None;
    };

    if dirpath.trim().is_empty() || std::fs::create_dir_all(&dirpath).is_err() {
        return None;
    }

    Some(dirpath)
}

// Initialize the global logger to error level on the first call only
// In any of the calls to the function, including the first - resetting the existence loggers to the new setting
// provided by using the global reloadable handle
// The logger will save only logs of the given level or above.
pub fn init(minimal_level: Option<Level>, file_name: Option<&str>) -> Level {
    let level = minimal_level.unwrap_or(Level::Warn);
    let level_filter = level.to_filter();
    let reloads = INITIATE_ONCE.init_once.get_or_init(|| {
        let stdout_fmt = tracing_subscriber::fmt::layer()
            .event_format(GlideLogFormat::default())
            .with_ansi(true)
            .with_filter(LevelFilter::OFF);

        let (stdout_layer, stdout_reload) = reload::Layer::new(stdout_fmt);

        // Check if the environment variable GLIDE_LOG is set
        let logs_dir =
            create_directory_from_env(ENV_GLIDE_LOG_DIR).unwrap_or(FILE_DIRECTORY.to_string());
        let file_appender = LazyRollingFileAppender::new(
            Rotation::HOURLY,
            logs_dir,
            file_name.unwrap_or("output.log"),
        );

        let file_fmt = tracing_subscriber::fmt::layer()
            .event_format(GlideLogFormat::default())
            .with_writer(file_appender)
            .with_filter(LevelFilter::OFF);
        let (file_layer, file_reload) = reload::Layer::new(file_fmt);

        // If user has set the environment variable "RUST_LOG" with a valid log verbosity, use it
        let log_level = if let Ok(level) = std::env::var("RUST_LOG") {
            let trace_level = tracing::Level::from_str(&level).unwrap_or(tracing::Level::TRACE);
            LevelFilter::from(trace_level)
        } else {
            LevelFilter::TRACE
        };

        // Enable logging only from allowed crates
        let targets_filter = filter::Targets::new()
            .with_target("glide", log_level)
            .with_target("redis", log_level)
            .with_target("logger_core", log_level)
            .with_target(std::env!("CARGO_PKG_NAME"), log_level);

        // Use try_init() instead of init() to gracefully handle the case where
        // a tracing subscriber has already been set by the application.
        // This allows applications to control their own logging configuration.
        tracing_subscriber::registry()
            .with(stdout_layer)
            .with(file_layer)
            .with(targets_filter)
            .try_init()
            .ok(); // Ignore the error if subscriber already set

        let reloads: Reloads = Reloads {
            console_reload: RwLock::new(stdout_reload),
            file_reload: RwLock::new(file_reload),
        };
        reloads
    });

    match file_name {
        None => {
            let _ = reloads
                .console_reload
                .write()
                .expect("error reloading stdout")
                .modify(|layer| *layer.filter_mut() = level_filter);
            let _ = reloads
                .file_reload
                .write()
                .expect("error reloading file appender")
                .modify(|layer| {
                    *layer.filter_mut() = LevelFilter::OFF;
                });
        }
        Some(file) => {
            // Check if the environment variable GLIDE_LOG is set
            let logs_dir =
                create_directory_from_env(ENV_GLIDE_LOG_DIR).unwrap_or(FILE_DIRECTORY.to_string());
            let file_appender = LazyRollingFileAppender::new(Rotation::HOURLY, logs_dir, file);
            let _ = reloads
                .file_reload
                .write()
                .expect("error reloading file appender")
                .modify(|layer| {
                    *layer.filter_mut() = level_filter;
                    *layer.inner_mut().writer_mut() = file_appender;
                });
            let _ = reloads
                .console_reload
                .write()
                .expect("error reloading stdout")
                .modify(|layer| *layer.filter_mut() = LevelFilter::OFF);
        }
    };
    // Publish the effective max verbosity for the cheap lazy-macro gate.
    CURRENT_MAX_VERBOSITY.store(
        match level {
            Level::Off => 0,
            Level::Error => 1,
            Level::Warn => 2,
            Level::Info => 3,
            Level::Debug => 4,
            Level::Trace => 5,
        },
        Ordering::Relaxed,
    );
    level
}

macro_rules! create_log {
    ($name:ident, $uppercase_level:tt) => {
        pub fn $name<Message: AsRef<str>, Identifier: AsRef<str>>(
            log_identifier: Identifier,
            message: Message,
        ) {
            if INITIATE_ONCE.init_once.get().is_none() {
                init(Some(Level::Warn), None);
            };
            let message_ref = message.as_ref();
            let identifier_ref = log_identifier.as_ref();
            event!(
                tracing::Level::$uppercase_level,
                "{identifier_ref} - {message_ref}"
            )
        }
    };
}

create_log!(log_trace, TRACE);
create_log!(log_debug, DEBUG);
create_log!(log_info, INFO);
create_log!(log_warn, WARN);
create_log!(log_error, ERROR);

/// Emits a structured event as exactly one JSON line.
pub fn log_structured<Identifier: AsRef<str>>(
    log_level: Level,
    log_identifier: Identifier,
    fields: &[StructuredField],
) {
    if INITIATE_ONCE.init_once.get().is_none() {
        init(Some(Level::Warn), None);
    };
    let payload = structured_payload(&log_level, log_identifier.as_ref(), fields);
    match log_level {
        Level::Debug => event!(
            tracing::Level::DEBUG,
            glide_structured_payload = payload.as_str()
        ),
        Level::Trace => event!(
            tracing::Level::TRACE,
            glide_structured_payload = payload.as_str()
        ),
        Level::Info => event!(
            tracing::Level::INFO,
            glide_structured_payload = payload.as_str()
        ),
        Level::Warn => event!(
            tracing::Level::WARN,
            glide_structured_payload = payload.as_str()
        ),
        Level::Error => event!(
            tracing::Level::ERROR,
            glide_structured_payload = payload.as_str()
        ),
        Level::Off => (),
    }
}

/// Lazy logging macros that only evaluate the message expression if the log level is enabled.
/// This avoids the cost of `format!(...)` when the level is disabled.
///
/// Usage:
/// ```ignore
/// log_trace_lazy!("identifier", format!("expensive computation: {}", value));
/// log_warn_lazy!("identifier", format!("something happened: {:?}", err));
/// ```
#[macro_export]
macro_rules! log_trace_lazy {
    ($identifier:expr, $message:expr) => {
        if $crate::level_may_be_enabled(tracing::Level::TRACE)
            && tracing::event_enabled!(tracing::Level::TRACE)
        {
            $crate::log_trace($identifier, $message);
        }
    };
}

#[macro_export]
macro_rules! log_debug_lazy {
    ($identifier:expr, $message:expr) => {
        if $crate::level_may_be_enabled(tracing::Level::DEBUG)
            && tracing::event_enabled!(tracing::Level::DEBUG)
        {
            $crate::log_debug($identifier, $message);
        }
    };
}

#[macro_export]
macro_rules! log_info_lazy {
    ($identifier:expr, $message:expr) => {
        if $crate::level_may_be_enabled(tracing::Level::INFO)
            && tracing::event_enabled!(tracing::Level::INFO)
        {
            $crate::log_info($identifier, $message);
        }
    };
}

#[macro_export]
macro_rules! log_warn_lazy {
    ($identifier:expr, $message:expr) => {
        if tracing::event_enabled!(tracing::Level::WARN) {
            $crate::log_warn($identifier, $message);
        }
    };
}

#[macro_export]
macro_rules! log_error_lazy {
    ($identifier:expr, $message:expr) => {
        if tracing::event_enabled!(tracing::Level::ERROR) {
            $crate::log_error($identifier, $message);
        }
    };
}

/// Rate-limited logging macro. Logs at most once per `interval_secs` seconds.
/// Uses a static AtomicU64 per call site to track the last log time.
///
/// Usage:
/// ```ignore
/// log_warn_rate_limited!("identifier", 10, format!("something happened: {}", val));
/// ```
#[macro_export]
macro_rules! log_warn_rate_limited {
    ($identifier:expr, $interval_secs:expr, $message:expr) => {{
        static LAST_LOG: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let last = LAST_LOG.load(std::sync::atomic::Ordering::Relaxed);
        if now >= last + $interval_secs {
            LAST_LOG.store(now, std::sync::atomic::Ordering::Relaxed);
            $crate::log_warn($identifier, $message);
        }
    }};
}

#[macro_export]
macro_rules! log_info_rate_limited {
    ($identifier:expr, $interval_secs:expr, $message:expr) => {{
        static LAST_LOG: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let last = LAST_LOG.load(std::sync::atomic::Ordering::Relaxed);
        if now >= last + $interval_secs {
            LAST_LOG.store(now, std::sync::atomic::Ordering::Relaxed);
            $crate::log_info($identifier, $message);
        }
    }};
}

#[macro_export]
macro_rules! log_debug_rate_limited {
    ($identifier:expr, $interval_secs:expr, $message:expr) => {{
        static LAST_LOG: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let last = LAST_LOG.load(std::sync::atomic::Ordering::Relaxed);
        if now >= last + $interval_secs {
            LAST_LOG.store(now, std::sync::atomic::Ordering::Relaxed);
            $crate::log_debug($identifier, $message);
        }
    }};
}

// Logs the given log, with log_identifier and log level prefixed. If the given log level is below the threshold of given when the logger was initialized, the log will be ignored.
// log_identifier should be used to add context to a log, and make it easier to connect it to other relevant logs. For example, it can be used to pass a task identifier.
// If this is called before a logger was initialized the log will not be registered.
// If logger doesn't exist, create the default
pub fn log<Message: AsRef<str>, Identifier: AsRef<str>>(
    log_level: Level,
    log_identifier: Identifier,
    message: Message,
) {
    match log_level {
        Level::Debug => log_debug(log_identifier, message),
        Level::Trace => log_trace(log_identifier, message),
        Level::Info => log_info(log_identifier, message),
        Level::Warn => log_warn(log_identifier, message),
        Level::Error => log_error(log_identifier, message),
        Level::Off => (),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_directory_from_env() {
        let dir_path = format!("{}/glide-logs", std::env::temp_dir().display());
        // Case 1: try to create an already existing folder
        // make sure we are starting fresh
        let _ = std::fs::remove_dir_all(&dir_path);
        // Create the directory
        assert!(std::fs::create_dir_all(&dir_path).is_ok());

        unsafe { std::env::set_var(ENV_GLIDE_LOG_DIR, &dir_path) };
        assert!(create_directory_from_env(ENV_GLIDE_LOG_DIR).is_some());
        assert!(std::fs::metadata(&dir_path).is_ok());

        // Case 2: try to create a new folder (i.e. the folder does not already exist)
        let _ = std::fs::remove_dir_all(&dir_path);

        // Create the directory
        assert!(std::fs::create_dir_all(&dir_path).is_ok());
        assert!(std::fs::metadata(&dir_path).is_ok());

        unsafe { std::env::set_var(ENV_GLIDE_LOG_DIR, &dir_path) };
        assert!(create_directory_from_env(ENV_GLIDE_LOG_DIR).is_some());

        // make sure we are starting fresh
        let _ = std::fs::remove_dir_all(&dir_path);

        // Case 3: empty variable is not acceptable
        unsafe { std::env::set_var(ENV_GLIDE_LOG_DIR, "") };
        assert!(create_directory_from_env(ENV_GLIDE_LOG_DIR).is_none());
    }
}
