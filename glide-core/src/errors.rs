// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::{ErrorKind, RedisError};

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

/// The text of `error`. A server NOSCRIPT reply keeps the server's own text
/// (`NOSCRIPT detail`, or `NOSCRIPT` when the reply has no detail); any other error is
/// its `Display` text. Unlike [`error_message`], disconnect errors are not wrapped.
pub fn error_text(error: &RedisError) -> String {
    if error.kind() != ErrorKind::NoScriptError || !error.is_server_reply() {
        return error.to_string();
    }
    match error.detail().filter(|detail| !detail.is_empty()) {
        Some(detail) => format!("NOSCRIPT {detail}"),
        None => "NOSCRIPT".to_owned(),
    }
}

/// The message bindings report for `error`: [`error_text`], with disconnect errors
/// wrapped in a reconnect notice.
pub fn error_message(error: &RedisError) -> String {
    let error_message = error_text(error);
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

    /// An error reply exactly as the parser builds it from the wire.
    fn server_reply(wire: &[u8]) -> RedisError {
        redis::parse_redis_value(wire)
            .and_then(|reply| reply.extract_error())
            .expect_err("an error reply is an error")
    }

    #[test]
    fn noscript_reply_keeps_the_server_text() {
        for (wire, expected) in [
            (
                &b"-NOSCRIPT No matching script.\r\n"[..],
                "NOSCRIPT No matching script.",
            ),
            (
                b"-NOSCRIPT No matching script. Please use EVAL.\r\n",
                "NOSCRIPT No matching script. Please use EVAL.",
            ),
            (
                b"!28\r\nNOSCRIPT No matching script.\r\n",
                "NOSCRIPT No matching script.",
            ),
        ] {
            let error = server_reply(wire);
            assert_eq!(error_text(&error), expected);
            assert_eq!(error_message(&error), expected);
        }
    }

    #[test]
    fn noscript_reply_without_detail_is_the_code_alone() {
        for wire in [&b"-NOSCRIPT\r\n"[..], b"!8\r\nNOSCRIPT\r\n"] {
            let error = server_reply(wire);
            assert_eq!(error_message(&error), "NOSCRIPT");
        }
    }

    #[test]
    fn other_errors_keep_their_display_text() {
        for error in [
            server_reply(b"-ERR unknown command 'FOO'\r\n"),
            server_reply(b"-WRONGTYPE Operation against a key holding the wrong kind of value\r\n"),
            server_reply(b"-NOTBUSY No scripts in execution right now.\r\n"),
            redis::RedisError::from((ErrorKind::NoScriptError, "built by the client")),
            redis::RedisError::from((
                redis::ErrorKind::ClientError,
                "Script execution failed",
                "NOSCRIPT No matching script.".to_string(),
            )),
        ] {
            assert_eq!(error_message(&error), error.to_string());
        }
    }

    #[test]
    fn disconnect_errors_keep_their_wrapper() {
        let error =
            redis::RedisError::from(std::io::Error::from(std::io::ErrorKind::ConnectionReset));
        assert_eq!(error_type(&error), RequestErrorType::Disconnect);
        assert_eq!(
            error_message(&error),
            format!("Received connection error `{error}`. Will attempt to reconnect")
        );
    }
}
