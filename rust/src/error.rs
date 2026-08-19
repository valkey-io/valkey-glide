// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Error types for the GLIDE Rust client.
//!
//! Mirrors the Python exception hierarchy (`glide_shared.exceptions`):
//! `GlideError` is the base; specific kinds map 1:1 to the Python subclasses.

use redis::{ErrorKind, RedisError};
use thiserror::Error;

/// The result type returned by all GLIDE client operations.
pub type Result<T> = std::result::Result<T, GlideError>;

/// Base error type for the GLIDE client.
///
/// Variants correspond to the Python exception classes:
/// - [`GlideError::Closing`] → `ClosingError`
/// - [`GlideError::Configuration`] → `ConfigurationError`
/// - [`GlideError::Connection`] → `ConnectionError`
/// - [`GlideError::ExecAbort`] → `ExecAbortError`
/// - [`GlideError::Request`] → `RequestError`
/// - [`GlideError::Timeout`] → `TimeoutError`
/// - [`GlideError::CircuitBreaker`] → `CircuitBreakerError`
#[derive(Debug, Error, Clone, PartialEq, Eq)]
pub enum GlideError {
    /// The client is closed and can no longer be used. Unrecoverable.
    #[error("ClosingError: {0}")]
    Closing(String),

    /// The client was misconfigured.
    #[error("ConfigurationError: {0}")]
    Configuration(String),

    /// A connection could not be established or was lost.
    #[error("ConnectionError: {0}")]
    Connection(String),

    /// A transaction (MULTI/EXEC) was aborted.
    #[error("ExecAbortError: {0}")]
    ExecAbort(String),

    /// The server returned an error in response to a command.
    #[error("RequestError: {0}")]
    Request(String),

    /// A request timed out.
    #[error("TimeoutError: {0}")]
    Timeout(String),

    /// The client-side circuit breaker is open.
    #[error("CircuitBreakerError: {0}")]
    CircuitBreaker(String),
}

impl GlideError {
    /// Short human-readable name of the error class (matches the Python class name).
    pub fn class_name(&self) -> &'static str {
        match self {
            GlideError::Closing(_) => "ClosingError",
            GlideError::Configuration(_) => "ConfigurationError",
            GlideError::Connection(_) => "ConnectionError",
            GlideError::ExecAbort(_) => "ExecAbortError",
            GlideError::Request(_) => "RequestError",
            GlideError::Timeout(_) => "TimeoutError",
            GlideError::CircuitBreaker(_) => "CircuitBreakerError",
        }
    }

    /// The message payload of the error.
    pub fn message(&self) -> &str {
        match self {
            GlideError::Closing(m)
            | GlideError::Configuration(m)
            | GlideError::Connection(m)
            | GlideError::ExecAbort(m)
            | GlideError::Request(m)
            | GlideError::Timeout(m)
            | GlideError::CircuitBreaker(m) => m,
        }
    }
}

impl From<RedisError> for GlideError {
    fn from(err: RedisError) -> Self {
        let msg = err.to_string();
        match err.kind() {
            ErrorKind::IoError | ErrorKind::ClientError => {
                if err.is_timeout() {
                    GlideError::Timeout(msg)
                } else {
                    GlideError::Connection(msg)
                }
            }
            ErrorKind::ExecAbortError => GlideError::ExecAbort(msg),
            ErrorKind::CircuitBreakerOpen => GlideError::CircuitBreaker(msg),
            ErrorKind::InvalidClientConfig => GlideError::Configuration(msg),
            _ => {
                // The fork reports pure timeouts via io error kind, but guard anyway.
                if err.is_timeout() {
                    GlideError::Timeout(msg)
                } else {
                    GlideError::Request(msg)
                }
            }
        }
    }
}

impl From<glide_core::client::ConnectionError> for GlideError {
    fn from(err: glide_core::client::ConnectionError) -> Self {
        use glide_core::client::ConnectionError as CE;
        match err {
            CE::Timeout => GlideError::Timeout("connection attempt timed out".to_string()),
            CE::Configuration(msg) => GlideError::Configuration(msg),
            other => GlideError::Connection(other.to_string()),
        }
    }
}

#[cfg(test)]
mod tests {
    //! Pure-logic error tests: `From<RedisError>` and `From<ConnectionError>`
    //! mapping into [`GlideError`], plus `class_name()`/`message()` for every
    //! variant.
    use super::*;
    use glide_core::client::ConnectionError as CE;

    // ---- From<RedisError> ------------------------------------------------

    #[test]
    fn redis_exec_abort_maps_to_exec_abort() {
        let err = RedisError::from((ErrorKind::ExecAbortError, "aborted"));
        let g = GlideError::from(err);
        assert!(matches!(g, GlideError::ExecAbort(_)));
        assert_eq!(g.class_name(), "ExecAbortError");
    }

    #[test]
    fn redis_circuit_breaker_maps_to_circuit_breaker() {
        let err = RedisError::from((ErrorKind::CircuitBreakerOpen, "open"));
        let g = GlideError::from(err);
        assert!(matches!(g, GlideError::CircuitBreaker(_)));
        assert_eq!(g.class_name(), "CircuitBreakerError");
    }

    #[test]
    fn redis_invalid_client_config_maps_to_configuration() {
        let err = RedisError::from((ErrorKind::InvalidClientConfig, "bad config"));
        let g = GlideError::from(err);
        assert!(matches!(g, GlideError::Configuration(_)));
        assert_eq!(g.class_name(), "ConfigurationError");
    }

    #[test]
    fn redis_response_error_maps_to_request() {
        let err = RedisError::from((ErrorKind::ResponseError, "wrong type"));
        let g = GlideError::from(err);
        assert!(matches!(g, GlideError::Request(_)));
        assert_eq!(g.class_name(), "RequestError");
    }

    #[test]
    fn redis_unknown_kind_maps_to_request() {
        // A kind we do not special-case falls through to Request.
        let err = RedisError::from((ErrorKind::TypeError, "type mismatch"));
        let g = GlideError::from(err);
        assert!(matches!(g, GlideError::Request(_)));
    }

    #[test]
    fn redis_io_error_maps_to_connection() {
        // Tuple-constructed IoError is not a real timeout, so it maps to Connection.
        let err = RedisError::from((ErrorKind::IoError, "socket error"));
        let g = GlideError::from(err);
        assert!(matches!(g, GlideError::Connection(_)));
        assert_eq!(g.class_name(), "ConnectionError");
    }

    #[test]
    fn redis_real_io_timeout_maps_to_timeout() {
        let io_err = std::io::Error::new(std::io::ErrorKind::TimedOut, "slow");
        let err: RedisError = io_err.into();
        assert!(err.is_timeout());
        assert!(matches!(GlideError::from(err), GlideError::Timeout(_)));
    }

    #[test]
    fn redis_error_preserves_message() {
        let err = RedisError::from((ErrorKind::ResponseError, "custom detail"));
        let g = GlideError::from(err);
        assert!(g.message().contains("custom detail"));
    }

    // ---- From<ConnectionError> ------------------------------------------

    #[test]
    fn connection_timeout_maps_to_timeout() {
        let g = GlideError::from(CE::Timeout);
        assert!(matches!(g, GlideError::Timeout(_)));
        assert_eq!(g.class_name(), "TimeoutError");
    }

    #[test]
    fn connection_configuration_maps_to_configuration() {
        let g = GlideError::from(CE::Configuration("nope".to_string()));
        assert!(matches!(g, GlideError::Configuration(_)));
        assert_eq!(g.message(), "nope");
    }

    #[test]
    fn connection_io_error_maps_to_connection() {
        let io_err = std::io::Error::new(std::io::ErrorKind::ConnectionRefused, "refused");
        let g = GlideError::from(CE::IoError(io_err));
        assert!(matches!(g, GlideError::Connection(_)));
        assert_eq!(g.class_name(), "ConnectionError");
    }

    #[test]
    fn connection_cluster_error_maps_to_connection() {
        let redis_err = RedisError::from((ErrorKind::ResponseError, "cluster boom"));
        let g = GlideError::from(CE::Cluster(redis_err));
        assert!(matches!(g, GlideError::Connection(_)));
    }

    // ---- class_name() / message() for every variant --------------------

    #[test]
    fn class_names_for_all_variants() {
        assert_eq!(GlideError::Closing("x".into()).class_name(), "ClosingError");
        assert_eq!(
            GlideError::Configuration("x".into()).class_name(),
            "ConfigurationError"
        );
        assert_eq!(
            GlideError::Connection("x".into()).class_name(),
            "ConnectionError"
        );
        assert_eq!(
            GlideError::ExecAbort("x".into()).class_name(),
            "ExecAbortError"
        );
        assert_eq!(GlideError::Request("x".into()).class_name(), "RequestError");
        assert_eq!(GlideError::Timeout("x".into()).class_name(), "TimeoutError");
        assert_eq!(
            GlideError::CircuitBreaker("x".into()).class_name(),
            "CircuitBreakerError"
        );
    }

    #[test]
    fn message_for_all_variants() {
        assert_eq!(GlideError::Closing("a".into()).message(), "a");
        assert_eq!(GlideError::Configuration("b".into()).message(), "b");
        assert_eq!(GlideError::Connection("c".into()).message(), "c");
        assert_eq!(GlideError::ExecAbort("d".into()).message(), "d");
        assert_eq!(GlideError::Request("e".into()).message(), "e");
        assert_eq!(GlideError::Timeout("f".into()).message(), "f");
        assert_eq!(GlideError::CircuitBreaker("g".into()).message(), "g");
    }

    #[test]
    fn display_includes_class_and_message() {
        let e = GlideError::Timeout("slow".into());
        assert_eq!(e.to_string(), "TimeoutError: slow");
    }

    #[test]
    fn variants_are_clonable_and_comparable() {
        let a = GlideError::Request("same".into());
        let b = a.clone();
        assert_eq!(a, b);
        assert_ne!(a, GlideError::Request("different".into()));
    }
}
