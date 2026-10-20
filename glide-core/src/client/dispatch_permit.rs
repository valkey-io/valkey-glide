// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Kept in its own module so that [`DispatchPermit::take`] is the only way to get a
//! permit, and so no permit skips its checks.

use super::{Client, ClientWrapper, circuit_breaker_open};
use redis::RedisResult;
use redis::cluster_async::InflightRequestTracker;

/// The client and in-flight slot a command is sent with, held once the circuit
/// breaker and the in-flight limit have let it through.
pub(super) struct DispatchPermit {
    client: ClientWrapper,
    tracker: InflightRequestTracker,
}

impl DispatchPermit {
    /// Check the circuit breaker and reserve an in-flight slot. The tracker holds
    /// the slot until the last clone of the Cmd is dropped (i.e. all sub-commands
    /// in the cluster event loop finish). This decouples user-facing timeout from
    /// internal pipeline cleanup.
    pub(super) async fn take(client: &Client) -> RedisResult<Self> {
        let wrapper = client.get_or_initialize_client().await?;

        // Reject immediately if circuit breaker is open.
        if !client.is_circuit_breaker_healthy() {
            return Err(circuit_breaker_open());
        }

        let tracker = client
            .reserve_inflight_request()
            .ok_or_else(|| client.inflight_requests_exhausted())?;
        Ok(Self {
            client: wrapper,
            tracker,
        })
    }

    pub(super) fn into_parts(self) -> (ClientWrapper, InflightRequestTracker) {
        (self.client, self.tracker)
    }
}
