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

    #[test]
    fn request_timeout_error_type() {
        let err: redis::RedisError = std::io::Error::from(std::io::ErrorKind::TimedOut).into();
        assert_eq!(error_type(&err), RequestErrorType::Timeout);
    }

    /// A connection-class error mapped to `ClientError` reports as `Unspecified`, which
    /// keeps it out of the `Disconnect` bucket. Java raises `Disconnect` as
    /// `ClosingException` and closes the client on it, so a binding that remaps an error
    /// to `ClientError` is choosing not to trigger that.
    #[test]
    fn client_error_is_unspecified_not_disconnect() {
        let err = redis::RedisError::from((
            redis::ErrorKind::ClientError,
            "Cluster scan execution failed",
            "connection reset".to_string(),
        ));
        assert_eq!(error_type(&err), RequestErrorType::Unspecified);
    }

    /// `is_timeout` reads the error's inner representation, not its `ErrorKind`, so a
    /// timeout that is rebuilt through `RedisError::from((kind, ..))` stops reporting as
    /// one. A binding that bridges a timeout must pass it through rather than rebuild it.
    #[test]
    fn rebuilding_a_timeout_loses_its_type() {
        let err: redis::RedisError = std::io::Error::from(std::io::ErrorKind::TimedOut).into();
        let rebuilt =
            redis::RedisError::from((err.kind(), "Cluster scan execution failed", err.to_string()));
        assert_eq!(error_type(&rebuilt), RequestErrorType::Unspecified);
    }
}
