// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! What a connection request asked of RDMA, and why it may not be honored.
//!
//! Present in every build, so a request for RDMA can be refused with a reason
//! when the `rdma` feature is off.

#[cfg(feature = "rdma")]
use glide_rdma::FabricConfig;

/// What a connection request asked of RDMA and whether it can be honored.
#[derive(Debug, Clone, Default)]
pub enum RdmaSetting {
    /// No RDMA was requested. The zero-cost default.
    #[default]
    Absent,
    /// RDMA was requested but cannot be honored, for this reason.
    Rejected(RdmaUnavailable),
    /// RDMA was requested and the configuration is usable.
    #[cfg(feature = "rdma")]
    Configured(FabricConfig),
}

/// The reason a requested RDMA configuration cannot be honored.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
#[non_exhaustive]
pub enum RdmaUnavailable {
    /// No RDMA support compiled in but the configuration is valid.
    #[error(
        "this build of GLIDE has no RDMA support compiled in; \
         it was built from source without the `rdma` feature."
    )]
    NotCompiledIn,

    /// No provider was named.
    #[error("RdmaConfiguration requires a provider; none was set")]
    NoProviderConfigured,

    /// A node did not recognise the RDMA commands, so whatever module serves them
    /// is not loaded there.
    #[error("node {node} has no RDMA module loaded")]
    ServerModuleMissing {
        /// The node that answered without the module.
        node: String,
    },

    /// A node answered the handshake with an error of its own, for example a
    /// `READONLY` from a node that will not take writes. The current module
    /// accepts the handshake on replicas, but transfers are only routed to
    /// primaries because the module does not replicate its objects yet.
    #[error("node {node} refused the RDMA handshake: {reason}")]
    HandshakeRefused {
        /// The node that refused.
        node: String,
        /// What it said.
        reason: String,
    },

    /// The handshake reply could not be read.
    #[error("node {node} returned a malformed BLOB.RDMA_HELLO reply: {reason}")]
    MalformedHandshake {
        /// The node that answered.
        node: String,
        /// What was wrong with the reply.
        reason: String,
    },

    /// The fabric refused the configuration.
    #[cfg(feature = "rdma")]
    #[error("{0}")]
    Fabric(#[from] glide_rdma::RdmaError),
}

impl RdmaSetting {
    /// Whether the request asked for RDMA at all
    pub fn is_requested(&self) -> bool {
        !matches!(self, RdmaSetting::Absent)
    }

    /// A fragment for the connection log, empty when RDMA was not requested.
    pub fn log_summary(&self) -> String {
        match self {
            RdmaSetting::Absent => String::new(),
            RdmaSetting::Rejected(reason) => format!("\nRDMA: unavailable ({reason})"),
            #[cfg(feature = "rdma")]
            RdmaSetting::Configured(config) => {
                let interface = config
                    .interface()
                    .map(|interface| format!(", interface: {interface}"))
                    .unwrap_or_default();
                let bind = config
                    .bind()
                    .map(|bind| format!(", bind: {bind}"))
                    .unwrap_or_default();
                format!("\nRDMA: {:?}{interface}{bind}", config.provider())
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_absent_setting_logs_nothing() {
        assert_eq!(RdmaSetting::Absent.log_summary(), "");
    }
}
