#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
    Off = 5,
}

pub fn init(_minimal_level: Option<Level>, _file_name: Option<&str>) -> Level {
    Level::Warn
}

pub fn log<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_level: Level,
    _log_identifier: Identifier,
    _message: Message,
) {
    // No-op for Miri
}

pub fn log_trace<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_identifier: Identifier,
    _message: Message,
) {
}

pub fn log_debug<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_identifier: Identifier,
    _message: Message,
) {
}

pub fn log_info<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_identifier: Identifier,
    _message: Message,
) {
}

pub fn log_warn<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_identifier: Identifier,
    _message: Message,
) {
}

pub fn log_error<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_identifier: Identifier,
    _message: Message,
) {
}
