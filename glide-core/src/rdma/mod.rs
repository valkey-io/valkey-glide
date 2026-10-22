// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! RDMA configuration, handshake errors, and cancelling transfers by revoking
//! their regions.
//!
//! RDMA is opt-in at build time through the `rdma` feature.
//!
//! In every build:
//! - `setting`: [`RdmaSetting`] and [`RdmaUnavailable`], what a request asked
//!   for and why it may not be honored.
//! - `guard`: refusing `BLOB.RDMA_*` sent other than by the client's RDMA
//!   methods.
//!
//! With the `rdma` feature only:
//! - `handshake`: what a failed `BLOB.RDMA_HELLO` means.
//! - `regions`: every registered region, closing them, and cancellation errors.
//! - `protocol`: the RESP side of the RDMA commands.

mod guard;
#[cfg(feature = "rdma")]
mod handshake;
#[cfg(feature = "rdma")]
pub(crate) mod protocol;
#[cfg(feature = "rdma")]
mod regions;
mod setting;

pub(crate) use guard::{refuse_direct, refuse_direct_command, refuse_direct_in_batch};
#[cfg(feature = "rdma")]
pub(crate) use handshake::{
    handshake_refused, handshake_unsettled, malformed_handshake, session_not_opened,
};
#[cfg(feature = "rdma")]
pub(crate) use regions::{Regions, cancellation, cancelled_if_revoked};
pub use setting::{RdmaSetting, RdmaUnavailable};

#[cfg(feature = "rdma")]
pub use glide_rdma::{FabricConfig, Provider};

/// The fabric a client's connections handshake with.
///
/// A new type rather than the fabric itself so that the plumbing carrying it
/// from `Client::new` down to connection setup is ordinary parameters instead of
/// conditional ones and so that `glide-rdma` stays out of signatures that don't
/// need it.
#[cfg(feature = "rdma")]
#[derive(Clone, Debug)]
pub struct Fabric(glide_rdma::RdmaFabric);

#[cfg(feature = "rdma")]
impl Fabric {
    /// The fabric itself, for the connection layer that advertises it.
    pub(crate) fn opened(self) -> glide_rdma::RdmaFabric {
        self.0
    }

    /// The fabric itself, for the client that registers memory with it.
    pub(crate) fn get(&self) -> &glide_rdma::RdmaFabric {
        &self.0
    }
}

/// Uninhabited without the `rdma` feature, so `Option<Fabric>` can only ever be
/// `None` there. The type still exists, which is what lets every signature that
/// carries one stay the same in both builds.
#[cfg(not(feature = "rdma"))]
#[derive(Clone, Debug)]
pub enum Fabric {}

/// Open the local fabric endpoint, if one was requested.
///
/// `Ok(None)` means RDMA was not requested. Without the `rdma` feature a request
/// can only have been rejected, so this never opens anything there. The returned
/// fabric must be retained for the client's lifetime: dropping it tears down the
/// endpoint the server RMAs into.
pub fn open(setting: &RdmaSetting) -> Result<Option<Fabric>, RdmaUnavailable> {
    match setting {
        RdmaSetting::Absent => Ok(None),
        RdmaSetting::Rejected(reason) => Err(reason.clone()),
        #[cfg(feature = "rdma")]
        RdmaSetting::Configured(config) => Ok(Some(Fabric(glide_rdma::RdmaFabric::open(config)?))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_absent_setting_opens_nothing() {
        assert!(matches!(open(&RdmaSetting::Absent), Ok(None)));
    }

    #[test]
    fn not_compiled_in_names_the_remedy() {
        let setting = RdmaSetting::Rejected(RdmaUnavailable::NotCompiledIn);
        let error = open(&setting).unwrap_err();
        let message = error.to_string();
        assert!(message.contains("built from source"), "{message}");
        assert!(message.contains("`rdma` feature"), "{message}");
    }
}
