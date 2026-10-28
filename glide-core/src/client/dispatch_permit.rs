// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Kept in its own module so that `DispatchPermit::take` and
//! [`Admission::reserve`] are the only ways to get a permit, and so no permit
//! skips its checks.

use super::{Client, ClientWrapper, circuit_breaker_open};
use redis::RedisResult;
use redis::cluster_async::InflightRequestTracker;

/// The client and in-flight slot a command is sent with, held once the circuit
/// breaker and the in-flight limit have let it through.
pub(super) struct DispatchPermit {
    client: ClientWrapper,
    tracker: InflightRequestTracker,
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

    /// Reserve an in-flight slot. The tracker holds the slot until the last clone
    /// of the Cmd is dropped (i.e. all sub-commands in the cluster event loop
    /// finish). This decouples user-facing timeout from internal pipeline cleanup.
    pub(super) fn reserve(self, client: &Client) -> RedisResult<DispatchPermit> {
        let tracker = client
            .reserve_inflight_request()
            .ok_or_else(|| client.inflight_requests_exhausted())?;
        Ok(DispatchPermit {
            client: self.client,
            tracker,
        })
    }
}

impl DispatchPermit {
    /// Check the circuit breaker and reserve an in-flight slot.
    #[cfg(any(feature = "rdma", test))]
    pub(super) async fn take(client: &Client) -> RedisResult<Self> {
        Admission::check(client).await?.reserve(client)
    }

    pub(super) fn into_parts(self) -> (ClientWrapper, InflightRequestTracker) {
        (self.client, self.tracker)
    }
}
