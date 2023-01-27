use std::time::{SystemTime, UNIX_EPOCH};
use tracing::{self, event};
use tracing_appender::{
    non_blocking,
    non_blocking::WorkerGuard,
    rolling::{RollingFileAppender, Rotation},
};
use tracing_subscriber::{self, filter::LevelFilter};

// Guard is in charge of making sure that the logs been collected when program stop
pub static mut GUARD: Option<WorkerGuard> = None;

#[derive(Debug)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
}
impl Level {
    fn to_filter(&self) -> LevelFilter {
        match self {
            Level::Trace => LevelFilter::TRACE,
            Level::Debug => LevelFilter::DEBUG,
            Level::Info => LevelFilter::INFO,
            Level::Warn => LevelFilter::WARN,
            Level::Error => LevelFilter::ERROR,
        }
    }
    fn to_tracing_level(&self) -> tracing::Level {
        match self {
            Level::Trace => tracing::Level::TRACE,
            Level::Debug => tracing::Level::DEBUG,
            Level::Info => tracing::Level::INFO,
            Level::Warn => tracing::Level::WARN,
            Level::Error => tracing::Level::ERROR,
        }
    }
}

// Initialize a logger that writes the received logs to a file under the babushka-logs folder.
// The file name will be prefixed with the current timestamp, and will be replaced every hour.
// This logger doesn't block the calling thread, and will save only logs of the given level or above.
pub fn init_file(minimal_level: Level, file_name: &str) -> Level {
    let file_appender = RollingFileAppender::new(Rotation::HOURLY, "babushka-logs", file_name);
    let (non_blocking, _guard) = non_blocking(file_appender);
    unsafe { GUARD = Some(_guard) }
    let level_filter = minimal_level.to_filter();
    let _ = tracing::subscriber::set_global_default(
        tracing_subscriber::fmt()
            .with_max_level(level_filter)
            .with_writer(non_blocking)
            .finish(),
    );
    return minimal_level;
}

// Initialize the global logger so that it will write the received logs to a file under the babushka-logs folder.
// The file name will be prefixed with the current timestamp, and will be replaced every hour.
// The logger doesn't block the calling thread, and will save only logs of the given level or above.
pub fn init_console(minimal_level: Level) -> Level {
    let level_filter = minimal_level.to_filter();
    let (non_blocking, _guard) = tracing_appender::non_blocking(std::io::stdout());
    unsafe { GUARD = Some(_guard) }
    let _ = tracing::subscriber::set_global_default(
        tracing_subscriber::fmt()
            .with_writer(non_blocking)
            .with_max_level(level_filter)
            .finish(),
    );
    return minimal_level;
}

// Initialize the global logger so that it will write the received logs to the console.
// The logger will save only logs of the given level or above.
pub fn init(minimal_level: Option<Level>, file_name: Option<&str>) -> Level {
    match minimal_level {
        None => return init_console(Level::Error),
        _ => (),
    }
    match file_name {
        None => return init_console(minimal_level.unwrap()),
        _ => return init_file(minimal_level.unwrap(), file_name.unwrap()),
    }
}

// Logs the given log, with log_identifier and log level prefixed. If the given log level is below the threshold of given when the logger was initialized, the log will be ignored.
// log_identifier should be used to add context to a log, and make it easier to connect it to other relevant logs. For example, it can be used to pass a task identifier.
// If this is called before a logger was initialized the log will not be registered.
// If logger doesn't exist, create the default
pub fn log(log_level: Level, log_identifier: &str, message: &str) {
    unsafe {
        if GUARD.is_none() {
            init(None, None);
        };
    };
    let micro_time_stamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("Time went backwards")
        .as_micros();
    match log_level.to_tracing_level() {
        tracing::Level::DEBUG => event!(
            tracing::Level::DEBUG,
            " - {log_identifier} - {message} - {micro_time_stamp}"
        ),
        tracing::Level::TRACE => event!(
            tracing::Level::TRACE,
            " - {log_identifier} - {message} - {micro_time_stamp}"
        ),
        tracing::Level::INFO => event!(
            tracing::Level::INFO,
            " - {log_identifier} - {message} - {micro_time_stamp}"
        ),
        tracing::Level::WARN => event!(
            tracing::Level::WARN,
            " - {log_identifier} - {message} - {micro_time_stamp}"
        ),
        tracing::Level::ERROR => event!(
            tracing::Level::ERROR,
            " - {log_identifier} - {message} - {micro_time_stamp}"
        ),
    }
}
