// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! What kind of memory a buffer is registered over, as a type.
//!
//! [`Host`] memory is ordinary memory Rust may read and write, so a buffer over it
//! offers [`crate::RdmaBuffer::as_host`] and can checksum its bytes. [`Device`]
//! memory, such as CUDA memory, is reachable by the NIC but not by the CPU through
//! a plain pointer, so a buffer over it offers neither: nothing in this crate forms a
//! reference to it, and calling either on it does not compile.
//!
//! ```
//! fn host_bytes(buffer: &glide_rdma::RdmaBuffer<glide_rdma::Host>) -> &[u8] {
//!     buffer.as_host()
//! }
//! ```
//!
//! ```compile_fail,E0599
//! fn device_bytes(buffer: &glide_rdma::RdmaBuffer<glide_rdma::Device>) -> &[u8] {
//!     buffer.as_host()
//! }
//! ```
//!
//! Nor can a set of device memory ask for a checksum this crate would have to read
//! the bytes to compute:
//!
//! ```compile_fail,E0308
//! fn set(buffer: glide_rdma::RdmaBuffer<glide_rdma::Device>) {
//!     let _ = buffer.lend_for_set(b"key", 0, 1, glide_rdma::SetChecksum::Compute);
//! }
//! ```

use std::fmt;

use crate::command::SetChecksum;

/// The kind of memory a [`crate::RdmaBuffer`] is registered over: [`Host`] or
/// [`Device`]. Sealed, so only those two exist.
pub trait MemoryKind: sealed::Sealed + fmt::Debug + Send + Sync + 'static {
    /// What a `BLOB.RDMA_SET` of this memory can ask the server to check the bytes
    /// against: [`SetChecksum`] for host memory, [`DeviceSetChecksum`] for device
    /// memory, which this crate cannot read to compute one.
    type SetChecksum: Copy + fmt::Debug + Send + Sync + 'static;
}

/// Host memory, which Rust may read and write between transfers.
#[derive(Debug)]
pub enum Host {}

/// Device memory, such as CUDA memory: registered for the NIC to reach, and never
/// read or written by this crate.
#[derive(Debug)]
pub enum Device {}

impl MemoryKind for Host {
    type SetChecksum = SetChecksum;
}

impl MemoryKind for Device {
    type SetChecksum = DeviceSetChecksum;
}

/// Whether a set of device memory sends the server a CRC-32c to check.
///
/// There is no way to compute one here, since this crate cannot read device memory.
/// A caller that wants the server to check the bytes computes the CRC-32c itself,
/// on the device or over a host copy, and passes it as `Known`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeviceSetChecksum {
    /// Send no checksum. The server stores whatever it read.
    Skip,
    /// Send this CRC-32c of the window's bytes.
    Known(u32),
}

/// The runtime a region of device memory belongs to, as libfabric's `fi_mr_attr`
/// names it.
///
/// No device ordinal: CUDA addresses every GPU in one virtual address space, so
/// the pointer alone names the device, and efa finds the memory from it.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[non_exhaustive]
pub enum DeviceRuntime {
    /// CUDA memory, such as from `cudaMalloc`.
    Cuda,
    /// Host memory registered as if it were device memory (`FI_HMEM_SYSTEM`), so
    /// tests exercise the device path on providers without a GPU.
    #[cfg(test)]
    SystemForTests,
}

pub(crate) mod sealed {
    use super::MemoryKind;

    /// What each kind of memory does where the two differ. Not nameable outside the
    /// crate, which seals [`MemoryKind`].
    pub trait Sealed {
        /// The CRC-32c a set sends, if any. `window` gives the window's bytes, and
        /// is called only for host memory, so device memory is never referenced.
        fn set_checksum<'a>(
            checksum: <Self as MemoryKind>::SetChecksum,
            window: impl FnOnce() -> &'a [u8],
        ) -> Option<u32>
        where
            Self: MemoryKind;
    }

    impl Sealed for super::Host {
        fn set_checksum<'a>(
            checksum: super::SetChecksum,
            window: impl FnOnce() -> &'a [u8],
        ) -> Option<u32> {
            match checksum {
                super::SetChecksum::Skip => None,
                super::SetChecksum::Compute => Some(crate::checksum(window())),
                super::SetChecksum::Known(checksum) => Some(checksum),
            }
        }
    }

    impl Sealed for super::Device {
        fn set_checksum<'a>(
            checksum: super::DeviceSetChecksum,
            _window: impl FnOnce() -> &'a [u8],
        ) -> Option<u32> {
            match checksum {
                super::DeviceSetChecksum::Skip => None,
                super::DeviceSetChecksum::Known(checksum) => Some(checksum),
            }
        }
    }
}
