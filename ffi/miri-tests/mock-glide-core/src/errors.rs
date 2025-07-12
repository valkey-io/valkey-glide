use redis::RedisError;

#[repr(C)]
pub enum RequestErrorType {
    Unspecified = 0,
    ExecAbort = 1,
    Timeout = 2,
    Disconnect = 3,
}

pub fn error_type(error: &RedisError) -> RequestErrorType {
    RequestErrorType::Unspecified
}

pub fn error_message(error: &RedisError) -> String {
    "".to_string()
}
