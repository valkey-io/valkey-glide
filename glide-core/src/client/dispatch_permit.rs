// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Kept in its own module so that `DispatchPermit::take` and
//! [`Admission::reserve`] are the only ways to get a permit, and so no permit
//! skips its checks.

use super::{Client, ClientWrapper, circuit_breaker_open};
use redis::RedisResult;
use redis::cluster_async::InflightSlot;

/// The client and in-flight slot a command is sent with, held once the circuit
/// breaker and the in-flight limit have let it through.
pub(super) struct DispatchPermit {
    client: ClientWrapper,
    /// `None` once [`Self::attach_slot_to`] has moved the slot onto a command.
    slot: Option<InflightSlot>,
}

/// A command the circuit breaker has let through, before it takes an in-flight
/// slot. A command answered locally, such as an intercepted pubsub command,
/// stops here and never uses a slot.
pub(super) struct Admission {
    client: ClientWrapper,
}

impl Admission {
    /// Initialize the client if needed, then check the circuit breaker.
    pub(super) async fn check(client: &Client) -> RedisResult<Self> {
        let wrapper = client.get_or_initialize_client().await?;

        // Reject immediately if circuit breaker is open.
        if !client.is_circuit_breaker_healthy() {
            return Err(circuit_breaker_open());
        }

        Ok(Self { client: wrapper })
    }

    /// Reserve an in-flight slot. `dispatch` decides who holds it: the request
    /// future itself for a standalone client, or the `Cmd` (as a shared
    /// `InflightRequestTracker`) for a cluster client, whose sub-commands outlive
    /// the future in the cluster event loop. This decouples user-facing timeout
    /// from internal pipeline cleanup.
    pub(super) fn reserve(self, client: &Client) -> RedisResult<DispatchPermit> {
        let slot = client
            .reserve_inflight_slot()
            .ok_or_else(|| client.inflight_requests_exhausted())?;
        Ok(DispatchPermit {
            client: self.client,
            slot: Some(slot),
        })
    }
}

impl DispatchPermit {
    /// Check the circuit breaker and reserve an in-flight slot.
    #[cfg(any(feature = "rdma", test))]
    pub(super) async fn take(client: &Client) -> RedisResult<Self> {
        Admission::check(client).await?.reserve(client)
    }

    /// Move the slot onto `cmd` as a shared tracker, so it is held until the
    /// caller drops `cmd` rather than until `dispatch` returns. For a sender
    /// whose work on the command outlives the dispatch, such as an RDMA transfer
    /// that recalls its buffer after the reply or a revoke.
    #[cfg(feature = "rdma")]
    pub(super) fn attach_slot_to(&mut self, cmd: &mut redis::Cmd) {
        if let Some(slot) = self.slot.take() {
            cmd.set_inflight_tracker(redis::cluster_async::InflightRequestTracker::from_slot(
                slot,
            ));
        }
    }

    /// The client, and the slot unless [`Self::attach_slot_to`] already moved it
    /// onto the command.
    pub(super) fn into_parts(self) -> (ClientWrapper, Option<InflightSlot>) {
        (self.client, self.slot)
    }
}
