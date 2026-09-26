// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! How to open the local fabric endpoint.

/// A libfabric provider the client can drive.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Provider {
    /// Software transport, for tests and development on hosts without RDMA hardware.
    Tcp,
    /// EFA's `efa-direct` fabric.
    #[default]
    EfaDirect,
}

/// Properties only the fabric layer consults.
#[cfg(feature = "libfabric")]
impl Provider {
    /// The libfabric provider name, as `fabric_attr.prov_name`.
    pub(crate) fn as_str(self) -> &'static str {
        match self {
            Provider::Tcp => "tcp",
            Provider::EfaDirect => "efa",
        }
    }

    /// The `fabric_attr.name` selecting a specific fabric within the provider.
    pub(crate) fn fabric_name(self) -> Option<&'static str> {
        match self {
            Provider::EfaDirect => Some("efa-direct"),
            Provider::Tcp => None,
        }
    }

    /// `FI_CONTEXT2`: each operation's `op_context`
    /// must be a provider-owned `fi_context2`.
    pub(crate) fn requires_context2(self) -> bool {
        matches!(self, Provider::EfaDirect)
    }

    /// Whether a passive target must poll its completion queue for inbound RMA to make
    /// progress. False on `efa-direct`, where the NIC services it.
    pub(crate) fn needs_manual_progress(self) -> bool {
        !matches!(self, Provider::EfaDirect)
    }
}

/// How to open the local fabric endpoint.
#[derive(Debug, Clone, Default)]
pub struct FabricConfig {
    provider: Provider,
    interface: Option<String>,
    bind: Option<String>,
}

impl FabricConfig {
    /// Create a FabricConfig
    pub fn new(provider: Provider) -> Self {
        Self {
            provider,
            interface: None,
            bind: None,
        }
    }

    /// Pin the endpoint to the fabric domain when a host has more
    /// than one card. Unset takes the first the provider returns.
    pub fn with_interface(mut self, interface: impl Into<String>) -> Self {
        self.interface = Some(interface.into());
        self
    }

    /// Bind the endpoint's source address to `node`. `None` lets the provider choose.
    /// Meaningful on `tcp`, which binds an IP. Leave unset on `efa-direct`.
    pub fn with_bind(mut self, node: impl Into<String>) -> Self {
        self.bind = Some(node.into());
        self
    }

    /// The provider to open.
    pub fn provider(&self) -> Provider {
        self.provider
    }

    /// The fabric domain to pin to.
    pub fn interface(&self) -> Option<&str> {
        self.interface.as_deref()
    }

    /// The source address to bind to.
    pub fn bind(&self) -> Option<&str> {
        self.bind.as_deref()
    }
}
