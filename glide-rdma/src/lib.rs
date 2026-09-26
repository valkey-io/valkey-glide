// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! RDMA support for Valkey GLIDE.
//!
//! An RDMA session is opened by the client sending a `LO.HELLO` command with
//! its fabric endpoint. `LO.GET` and `LO.SET` use the RESP connection to
//! coordinate with the server, but the server performs the data transfer itself
//! against the client host memory that it registered for RDMA. The RESP reply
//! carries only a byte count and an optional checksum.
//!
//! `RdmaBuffer` and `LentBuffer` loans bytes to the server for the duration of
//! a transfer. The memory can be accessed again only after the loan ends.

#[cfg(feature = "libfabric")]
mod buffer;
mod checksum;
mod command;
mod config;
#[cfg(feature = "libfabric")]
mod endpoint;
mod error;
#[cfg(feature = "libfabric")]
mod fabric;
#[cfg(feature = "libfabric")]
mod libfabric_dl;
#[cfg(feature = "libfabric")]
mod progress;
mod region_ref;
mod session;

#[cfg(feature = "libfabric")]
pub use buffer::{LentBuffer, RdmaBuffer, RdmaRevoker};
pub use checksum::checksum;
pub use command::{RdmaCommand, ReadReceipt, TransferReply, hello, needs_session};
pub use config::{FabricConfig, Provider};
pub use error::RdmaError;
#[cfg(feature = "libfabric")]
pub use fabric::{RdmaFabric, RdmaSession};
#[cfg(feature = "libfabric")]
pub use libfabric_dl::{ensure_loaded, header_api_version};
#[cfg(feature = "libfabric")]
pub use progress::ProgressGuard;
pub use region_ref::{InvalidHex, decode_hex, encode_hex};
pub use session::Handshake;
