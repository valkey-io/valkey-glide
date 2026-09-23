// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Registered memory and the windows a transfer advertises.

use std::fmt;
use std::sync::Arc;

use crate::endpoint::{Registration, RevokeHandle};
use crate::error::RdmaError;
use crate::fabric::RdmaFabric;
use crate::region_ref::RegionRef;

/// A window of a registration: where it is and how big it is.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RegionWindow {
    region_ref: RegionRef,
    length: usize,
}

impl RegionWindow {
    /// Where the window starts.
    pub fn region_ref(&self) -> &RegionRef {
        &self.region_ref
    }

    /// How many bytes of the region it covers.
    pub fn length(&self) -> usize {
        self.length
    }
}

/// What backs a buffer's bytes.
enum Backing {
    /// Host memory owned here and writable, so a transfer can land in it.
    Owned(Box<dyn AsMut<[u8]> + Send>),
    /// Host memory shared with the caller and read-only, so it can back a buffer on
    /// several endpoints at once.
    Shared(#[allow(dead_code)] Arc<dyn AsRef<[u8]> + Send + Sync>),
}

impl Backing {
    fn describe(&self) -> &'static str {
        match self {
            Self::Owned(_) => "owned",
            Self::Shared(_) => "shared",
        }
    }
}

/// Memory registered with a [`RdmaFabric`] for the server to RMA against.
///
/// Owned host memory is reachable through [`Self::as_host_mut`]. Shared memory is
/// read-only and the server may read windows of it for `LO.SET`, never write to it.
pub struct RdmaBuffer {
    /// Declared first so the region deregisters before the domain that owns it closes.
    registration: Registration,
    region_ref: RegionRef,
    length: usize,
    backing: Backing,
    /// Keeps the domain alive for as long as the registration.
    fabric: RdmaFabric,
}

impl RdmaBuffer {
    pub(crate) fn host(
        registration: Registration,
        region_ref: RegionRef,
        length: usize,
        memory: Box<dyn AsMut<[u8]> + Send>,
        fabric: RdmaFabric,
    ) -> Self {
        Self {
            registration,
            region_ref,
            length,
            backing: Backing::Owned(memory),
            fabric,
        }
    }

    /// A read-only source registered on this fabric.
    pub(crate) fn shared(
        registration: Registration,
        region_ref: RegionRef,
        length: usize,
        memory: Arc<dyn AsRef<[u8]> + Send + Sync>,
        fabric: RdmaFabric,
    ) -> Self {
        Self {
            registration,
            region_ref,
            length,
            backing: Backing::Shared(memory),
            fabric,
        }
    }

    /// Bytes registered, capping what one transfer can move through this buffer.
    pub fn capacity(&self) -> usize {
        self.length
    }

    /// Where this buffer lives, for a transfer command.
    pub fn region_ref(&self) -> &RegionRef {
        &self.region_ref
    }

    /// The region reference for `[at, at + length)` of this registration, or `None` if
    /// that runs past the end.
    ///
    /// One registration serves many transfers by advertising a different window per
    /// request. The remote key covers the whole region.
    pub fn slice(&self, at: usize, length: usize) -> Option<RegionWindow> {
        if self.length < at.checked_add(length)? {
            return None;
        }
        Some(RegionWindow {
            region_ref: RegionRef {
                address: self.region_ref.address.clone(),
                remote_key: self.region_ref.remote_key,
                remote_address: self
                    .region_ref
                    .remote_address
                    .checked_add(u64::try_from(at).ok()?)?,
            },
            length,
        })
    }

    /// Deregister this buffer. A no-op if it is already revoked.
    ///
    /// Safe while a transfer is using the buffer: once this returns `Ok`, the server
    /// can no longer reach the memory, so what it has not yet moved fails instead of
    /// landing. What already landed stays, so after a cancelled read the window holds
    /// an unknown mix of old and new bytes. The memory itself stays readable and
    /// writable through this handle; only the fabric's access to it ends.
    ///
    /// A revoked buffer cannot be registered again. Register a new one.
    ///
    /// # Errors
    ///
    /// When libfabric fails to close the region. It is then still registered, and
    /// the server may still reach it.
    pub fn revoke(&self) -> Result<(), RdmaError> {
        self.registration.revoke()
    }

    /// Whether this buffer has been revoked.
    pub fn is_revoked(&self) -> bool {
        self.registration.is_revoked()
    }

    /// Resolves once this buffer is revoked.
    ///
    /// Borrows nothing, so it can be awaited alongside a transfer that holds the buffer.
    pub fn revoked(&self) -> impl Future<Output = ()> + Send + 'static {
        self.registration.revoked()
    }

    /// A handle that revokes this buffer from elsewhere, such as another thread.
    ///
    /// It does not keep the buffer or its registration alive: once the buffer is
    /// dropped, revoking through the handle does nothing.
    pub fn revoker(&self) -> RdmaRevoker {
        RdmaRevoker(self.registration.handle())
    }

    /// The fabric this buffer is registered with.
    pub fn fabric(&self) -> &RdmaFabric {
        &self.fabric
    }

    /// The host mapping, or `None` for a shared source, which is not writable through
    /// this handle.
    pub fn as_host_mut(&mut self) -> Option<&mut [u8]> {
        match &mut self.backing {
            Backing::Owned(memory) => Some((**memory).as_mut()),
            Backing::Shared(_) => None,
        }
    }

    /// The host mapping for reading what a transfer landed, or `None` when there is
    /// none to read.
    pub fn as_host(&mut self) -> Option<&[u8]> {
        self.as_host_mut().map(|memory| &*memory)
    }

    /// Copy `value` into the front of the buffer, returning the bytes staged, or `None`
    /// without staging when there is no writable host mapping or it will not fit.
    pub fn copy_from(&mut self, value: &[u8]) -> Option<usize> {
        let destination = self.as_host_mut()?.get_mut(..value.len())?;
        destination.copy_from_slice(value);
        Some(value.len())
    }
}

/// Revokes a [`RdmaBuffer`] from elsewhere. From [`RdmaBuffer::revoker`].
#[derive(Debug, Clone)]
pub struct RdmaRevoker(RevokeHandle);

impl RdmaRevoker {
    /// Revoke the buffer, as [`RdmaBuffer::revoke`] does. A no-op if it is already
    /// revoked or has been dropped.
    ///
    /// # Errors
    ///
    /// When libfabric fails to close the region.
    pub fn revoke(&self) -> Result<(), RdmaError> {
        self.0.revoke()
    }

    /// Whether the buffer is revoked or dropped, so that this handle has nothing
    /// left to do.
    pub fn is_released(&self) -> bool {
        self.0.is_released()
    }
}

impl fmt::Debug for RdmaBuffer {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("RdmaBuffer")
            .field("length", &self.length)
            .field("backing", &self.backing.describe())
            .field("region_ref", &self.region_ref)
            .field("registration", &self.registration)
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use crate::config::{FabricConfig, Provider};
    use crate::fabric::RdmaFabric;
    use std::sync::Arc;

    fn fabric() -> RdmaFabric {
        RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp should open")
    }

    #[test]
    fn a_window_advances_the_remote_address_by_its_offset() {
        let buffer = fabric().register(vec![0u8; 4096]).unwrap();
        let base = buffer.region_ref().remote_address;
        let window = buffer.slice(1024, 256).expect("window should fit");
        assert_eq!(window.length(), 256);
        assert_eq!(window.region_ref().remote_address, base + 1024);
        // The key covers the whole region, so only the address moves.
        assert_eq!(
            window.region_ref().remote_key,
            buffer.region_ref().remote_key
        );
    }

    #[test]
    fn a_window_past_the_end_is_refused() {
        let buffer = fabric().register(vec![0u8; 1024]).unwrap();
        assert!(buffer.slice(0, 1025).is_none());
        assert!(buffer.slice(1024, 1).is_none());
        assert!(buffer.slice(1024, 0).is_some(), "empty window at the end");
        assert!(buffer.slice(usize::MAX, 1).is_none(), "offset overflow");
    }

    #[test]
    fn owned_memory_is_readable_and_writable() {
        let mut buffer = fabric().register(vec![0u8; 64]).unwrap();
        assert_eq!(buffer.copy_from(b"hello"), Some(5));
        assert_eq!(&buffer.as_host().unwrap()[..5], b"hello");
    }

    #[test]
    fn staging_more_than_fits_writes_nothing() {
        let mut buffer = fabric().register(vec![0u8; 4]).unwrap();
        assert_eq!(buffer.copy_from(b"too long"), None);
        assert_eq!(buffer.as_host().unwrap(), &[0u8; 4]);
    }

    #[test]
    fn a_revoked_buffer_says_so_and_keeps_its_memory() {
        let mut buffer = fabric().register(vec![7u8; 64]).unwrap();
        assert!(!buffer.is_revoked());

        buffer.revoke().expect("the region closes");

        assert!(buffer.is_revoked());
        assert_eq!(
            buffer.as_host().unwrap(),
            &[7u8; 64],
            "only the fabric's access ends; the memory is still the caller's"
        );
    }

    #[test]
    fn revoking_twice_and_then_dropping_closes_the_region_once() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.revoke().unwrap();
        buffer.revoke().expect("revoking again is a no-op");
        buffer
            .revoker()
            .revoke()
            .expect("so is revoking through a handle");
        drop(buffer);
    }

    #[tokio::test]
    async fn a_revoke_from_another_thread_wakes_a_waiter() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let waiter = buffer.revoked();
        let revoker = buffer.revoker();

        let thread = std::thread::spawn(move || revoker.revoke());

        tokio::time::timeout(std::time::Duration::from_secs(5), waiter)
            .await
            .expect("the waiter wakes once the region is revoked");
        thread.join().unwrap().expect("the region closes");
        assert!(buffer.is_revoked());
    }

    #[tokio::test]
    async fn waiting_on_an_already_revoked_buffer_returns_at_once() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        buffer.revoke().unwrap();

        tokio::time::timeout(std::time::Duration::from_secs(5), buffer.revoked())
            .await
            .expect("already revoked, so there is nothing to wait for");
    }

    /// Dropping the buffer closes the region too, so a waiter must not hang on a
    /// revoke that can now never happen.
    #[tokio::test]
    async fn dropping_the_buffer_wakes_a_waiter() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let waiter = buffer.revoked();

        drop(buffer);

        tokio::time::timeout(std::time::Duration::from_secs(5), waiter)
            .await
            .expect("the drop closed the region");
    }

    /// A handle must never keep memory registered after its buffer is freed.
    #[test]
    fn a_revoker_does_not_outlive_its_buffer() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();
        assert!(!revoker.is_released());

        drop(buffer);

        assert!(revoker.is_released());
        revoker
            .revoke()
            .expect("nothing is left to revoke, which is not an error");
    }

    #[test]
    fn a_revoker_sees_a_revoke_made_through_the_buffer() {
        let buffer = fabric().register(vec![0u8; 64]).unwrap();
        let revoker = buffer.revoker();

        buffer.revoke().unwrap();

        assert!(revoker.is_released());
    }

    #[test]
    fn shared_memory_has_no_writable_mapping() {
        let mut buffer = fabric().register_shared(Arc::new(vec![1u8; 128])).unwrap();
        assert!(buffer.as_host_mut().is_none());
        assert!(buffer.copy_from(b"nope").is_none());
    }
}
