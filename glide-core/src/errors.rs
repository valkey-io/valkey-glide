// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::RedisError;

#[repr(C)]
#[derive(Debug, Clone, PartialEq)]
pub enum RequestErrorType {
    Unspecified = 0,
    ExecAbort = 1,
    Timeout = 2,
    Disconnect = 3,
    CircuitBreakerOpen = 4,
}

pub fn error_type(error: &RedisError) -> RequestErrorType {
    if error.is_timeout() {
        RequestErrorType::Timeout
    } else if error.is_unrecoverable_error() {
        RequestErrorType::Disconnect
    } else if matches!(error.kind(), redis::ErrorKind::ExecAbortError) {
        RequestErrorType::ExecAbort
    } else if matches!(error.kind(), redis::ErrorKind::CircuitBreakerOpen) {
        RequestErrorType::CircuitBreakerOpen
    } else {
        RequestErrorType::Unspecified
    }
}

pub fn error_message(error: &RedisError) -> String {
    let error_message = error.to_string();
    if matches!(error_type(error), RequestErrorType::Disconnect) {
        format!("Received connection error `{error_message}`. Will attempt to reconnect")
    } else {
        error_message
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn circuit_breaker_rejection_error_type() {
        let err = redis::RedisError::from((
            redis::ErrorKind::CircuitBreakerOpen,
            "Client circuit breaker is open - core unhealthy",
        ));
        assert_eq!(error_type(&err), RequestErrorType::CircuitBreakerOpen);
    }
}
