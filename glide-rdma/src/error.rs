// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Errors from a RDMA command or the fabric carrying its payload.

/// An error from a RDMA transfer.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum RdmaError {
    /// The server's reply did not follow the RDMA protocol.
    #[error("rdma protocol error: {0}")]
    Protocol(String),

    /// The checksum the server sent with an `LO.GET` reply disagreed with the
    /// bytes that landed.
    #[error("rdma checksum mismatch: expected {expected:#010x}, got {actual:#010x}")]
    ChecksumMismatch {
        /// The checksum computed over the bytes that were meant to move.
        expected: u32,
        /// The checksum actually observed.
        actual: u32,
    },

    /// The value does not fit in the window the caller offered.
    /// Retry with a larger window, or fall back to a plain `GET`.
    #[error("value of {value_bytes} bytes exceeds the {capacity} byte registered window")]
    PayloadTooLarge {
        /// Size of the value on the server.
        value_bytes: usize,
        /// Capacity the caller advertised.
        capacity: usize,
    },

    /// The buffer was revoked, so the server can no longer reach it and it cannot be
    /// lent to another transfer. Register the memory again to keep using it.
    #[error("the buffer was revoked, so nothing can transfer through it")]
    Revoked,

    /// A libfabric call failed.
    #[error("fabric error in {operation}: {message}{}", errno.map(|e| format!(" (errno {e})")).unwrap_or_default())]
    Fabric {
        /// The libfabric operation that failed.
        operation: &'static str,
        /// `fi_strerror` rendering of the failure.
        message: String,
        /// The raw libfabric error code, where one was returned.
        errno: Option<i32>,
    },

    /// The fabric could not be configured as requested.
    #[error("fabric configuration error: {0}")]
    Configuration(String),

    /// libfabric could not be loaded or the copy that loaded is too old to
    /// match the headers this crate was built against.
    #[error("libfabric unavailable: {detail}")]
    LibfabricUnavailable {
        /// What was tried and what went wrong, for an operator to act on.
        detail: String,
    },
}

#[cfg(test)]
mod tests {
    use super::RdmaError;

    #[test]
    fn fabric_errors_render_errno_only_when_present() {
        let with_errno = RdmaError::Fabric {
            operation: "fi_mr_reg",
            message: "Cannot allocate memory".to_string(),
            errno: Some(-12),
        };
        assert!(with_errno.to_string().contains("errno -12"));

        let without = RdmaError::Fabric {
            operation: "fi_getinfo",
            message: "No data available".to_string(),
            errno: None,
        };
        assert!(!without.to_string().contains("errno"));
    }
}
