// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The client's RDMA methods: registering memory, transferring through it, the
//! held-memory counters, and closing.
//!
//! Only built with the `rdma` feature. The rest of the RDMA support in the client
//! stays in `mod.rs`: the fields that hold the fabric and the registered regions,
//! the timeout and blocking rules for the transfer commands, and the guards that
//! refuse those commands anywhere else.

use super::{Client, DispatchPermit};

/// What an RDMA transfer hands back: the buffer, and how the transfer went.
///
/// The buffer comes back whenever the server is known to be done with it: the
/// server replied, even with an error or to the handshake, or the transfer was
/// refused before the buffer was lent (a revoked or foreign buffer, a closed
/// client, a full in-flight limit, an open circuit breaker, a window out of
/// bounds). It is `None` when the transfer ended without a reply, for example
/// because it was cancelled or its connection failed, even if the command may
/// never have reached the server. Its region is then closed, and its memory is
/// freed on efa-direct, held until the fabric closes on tcp, or kept for good if
/// the close failed; see [`Client::rdma_parked_bytes`] and
/// [`Client::rdma_leaked_bytes`].
pub type RdmaOutcome<T> = (Option<glide_rdma::RdmaBuffer>, Result<T, redis::RedisError>);

/// End a loan the server replied to, with `reply` as parsed from that reply.
fn reclaim_rdma_loan(
    loan: glide_rdma::LentBuffer,
    reply: redis::RedisResult<glide_rdma::TransferReply>,
) -> RdmaOutcome<Option<glide_rdma::ReadReceipt>> {
    // A reply that could not be parsed is still a reply, so the server is done.
    let (transfer_reply, parse_error) = match reply {
        Ok(reply) => (reply, None),
        Err(error) => (glide_rdma::TransferReply::Failed, Some(error)),
    };
    // SAFETY: `reply` is the server's answer to this loan's command, so the server
    // is done with the window.
    match unsafe { loan.reclaim(transfer_reply) } {
        Ok((buffer, receipt)) => (Some(buffer), parse_error.map_or(Ok(receipt), Err)),
        Err((buffer, error)) => (
            Some(buffer),
            Err(crate::rdma::protocol::as_redis_error(error)),
        ),
    }
}

/// End a loan the server may still be using: close the region, then free its
/// memory on efa-direct or park it until the fabric closes on tcp. No buffer comes
/// back.
fn recall_rdma_loan(
    loan: glide_rdma::LentBuffer,
    error: redis::RedisError,
) -> RdmaOutcome<Option<glide_rdma::ReadReceipt>> {
    match loan.recall() {
        Ok(()) => (None, Err(error)),
        // The region failed to close, so the server may still reach the memory.
        // Dropping the loan keeps that memory for the life of the process and
        // counts it as leaked; the close is never tried again.
        Err((_loan, _)) => (None, Err(error)),
    }
}

/// Check the checksum a `BLOB.RDMA_GET` reported against the bytes that landed.
fn verify_rdma_checksum(
    buffer: &glide_rdma::RdmaBuffer,
    offset: usize,
    receipt: &glide_rdma::ReadReceipt,
) -> Result<(), redis::RedisError> {
    receipt
        .verify(&buffer.as_host()[offset..])
        .map_err(crate::rdma::protocol::as_redis_error)
}

impl Client {
    /// Register memory for the server to transfer data into or out of.
    ///
    /// Registration is expensive and pins pages against `RLIMIT_MEMLOCK`, so a
    /// caller should register a region once and advertise windows of it per
    /// transfer rather than registering per operation.
    ///
    /// The region is revoked when the client is closed with [`Self::close_rdma`].
    pub fn register_rdma_region(
        &self,
        memory: impl AsMut<[u8]> + Send + 'static,
    ) -> Result<glide_rdma::RdmaBuffer, redis::RedisError> {
        let rdma_fabric = self.require_rdma_fabric()?;
        self.rdma_regions.register(|| rdma_fabric.register(memory))
    }

    /// Bytes of registered memory this client is holding back because transfers
    /// ended without the server's reply, so the server might still write into them.
    /// Only grows while the client lives.
    ///
    /// Stays at zero on efa-direct, where closing a region stops the server's writes.
    /// On tcp, which is for development, every transfer that ends without the
    /// server's reply holds its memory: one that was cancelled, abandoned, or lost its
    /// connection.
    ///
    /// If this passes a threshold of your own, create a new client and register your
    /// memory again. The held memory is freed once this client and all its clones are
    /// dropped along with every buffer registered through them, unless
    /// [`Self::rdma_leaked_bytes`] is nonzero.
    ///
    /// # Errors
    ///
    /// When the client was created without an RDMA configuration.
    pub fn rdma_parked_bytes(&self) -> Result<usize, redis::RedisError> {
        Ok(self.require_rdma_fabric()?.parked_bytes())
    }

    /// Bytes of registered memory kept until the process exits, because closing their
    /// region failed. Only grows.
    ///
    /// A failed close needs a device or driver error, so this should stay at zero.
    /// Once it is not, the server might still write into that memory, so it is never
    /// freed, and this client's other held memory ([`Self::rdma_parked_bytes`]) can no
    /// longer be freed either. Each failure is also logged at error level.
    ///
    /// Watch this against a threshold of your own, and restart the process once it is
    /// passed. Until then, a new client can keep transferring with newly registered
    /// memory.
    ///
    /// # Errors
    ///
    /// When the client was created without an RDMA configuration.
    pub fn rdma_leaked_bytes(&self) -> Result<usize, redis::RedisError> {
        Ok(self.require_rdma_fabric()?.leaked_bytes())
    }

    /// Cancel every transfer this client has in flight and refuse new ones.
    ///
    /// Revokes every region registered through this client or any clone of it.
    /// The server can then no longer reach that memory. Bytes that landed
    /// before the revoke stay.
    ///
    /// When libfabric cannot close a region, that region stays registered
    /// and a transfer using it keeps waiting for the server's reply. Every other
    /// region is still revoked.
    pub fn close_rdma(&self) -> Result<(), redis::RedisError> {
        self.rdma_regions.close()
    }

    /// Read a value directly into the `[offset, offset + length)` window of
    /// registered memory.
    ///
    /// The buffer is lent to the server for the transfer and handed back with
    /// the result once the server is known to be done with it; see
    /// [`RdmaOutcome`].
    ///
    /// `Ok(None)` means the key does not exist. `Ok(Some(receipt))` means the
    /// bytes are already in the window by the time this returns.
    ///
    /// A transfer refused before the buffer is lent, such as for a window out of
    /// bounds or a full in-flight limit, hands the buffer back, and so does any
    /// reply the server sent, including one to the handshake. Any other error
    /// means the server may not be done with the memory, so the region is closed
    /// and no buffer comes back. Register the memory again for the next transfer.
    pub async fn rdma_get(
        &mut self,
        key: &[u8],
        buffer: glide_rdma::RdmaBuffer,
        offset: usize,
        length: usize,
    ) -> RdmaOutcome<Option<glide_rdma::ReadReceipt>> {
        if let Err(error) = self.check_rdma_buffer(&buffer) {
            return (Some(buffer), Err(error));
        }
        let permit = match self.rdma_preflight().await {
            Ok(permit) => permit,
            Err(error) => return (Some(buffer), Err(error)),
        };
        let (command, loan) = match buffer.lend_for_get(key, offset, length) {
            Ok(lent) => lent,
            Err((buffer, error)) => {
                return (
                    Some(buffer),
                    Err(crate::rdma::protocol::as_redis_error(error)),
                );
            }
        };
        let (buffer, result) = self
            .rdma_transfer(
                command,
                loan,
                permit,
                crate::rdma::protocol::parse_read_reply,
            )
            .await;
        let result = match (&buffer, result) {
            (Some(buffer), Ok(Some(receipt))) => {
                verify_rdma_checksum(buffer, offset, &receipt).map(|()| Some(receipt))
            }
            (_, result) => result,
        };
        (buffer, result)
    }

    /// Store a value the server reads directly out of the `[offset, offset + length)`
    /// window of registered memory.
    ///
    /// `checksum` decides whether the server checks the bytes it read against a
    /// CRC-32c before storing them, refusing them if they differ; see
    /// [`glide_rdma::SetChecksum`].
    ///
    /// Hands the buffer back as [`Self::rdma_get`] does.
    pub async fn rdma_set(
        &mut self,
        key: &[u8],
        buffer: glide_rdma::RdmaBuffer,
        offset: usize,
        length: usize,
        checksum: glide_rdma::SetChecksum,
    ) -> RdmaOutcome<()> {
        if let Err(error) = self.check_rdma_buffer(&buffer) {
            return (Some(buffer), Err(error));
        }
        let permit = match self.rdma_preflight().await {
            Ok(permit) => permit,
            Err(error) => return (Some(buffer), Err(error)),
        };
        let (command, loan) = match buffer.lend_for_set(key, offset, length, checksum) {
            Ok(lent) => lent,
            Err((buffer, error)) => {
                return (
                    Some(buffer),
                    Err(crate::rdma::protocol::as_redis_error(error)),
                );
            }
        };
        let (buffer, result) = self
            .rdma_transfer(
                command,
                loan,
                permit,
                crate::rdma::protocol::parse_write_reply,
            )
            .await;
        (buffer, result.map(|_| ()))
    }

    /// Send a lent buffer's command and end the loan.
    ///
    /// The loan is reclaimed when the server has provably stopped using the
    /// memory: it sent a reply, even an error reply or one `parse` rejects, or it
    /// answered the handshake with an error or a reply this client cannot read,
    /// so the transfer command was never sent. Both are server replies to
    /// `RedisError::is_server_reply`; `handshake_refused` and
    /// `malformed_handshake` build them that way on purpose. Any other
    /// error proves nothing about what the server is doing, so the loan is
    /// recalled instead, which closes the region first. So is a revoke that wins
    /// the race against the reply.
    ///
    /// The client-side checks that can fail before anything is sent already ran in
    /// [`Self::rdma_preflight`], before the buffer was lent. A connection error that
    /// says the command was not transmitted (`FatalSendError`) is still recalled:
    /// the cluster client may have retried after an earlier attempt that did reach
    /// the server, so it does not prove the server never saw the command.
    ///
    /// `permit` is what [`Self::rdma_preflight`] returned.
    ///
    /// If this future is dropped, so is the loan, which also closes the region.
    async fn rdma_transfer(
        &mut self,
        command: glide_rdma::RdmaCommand,
        loan: glide_rdma::LentBuffer,
        permit: DispatchPermit,
        parse: fn(redis::Value) -> redis::RedisResult<glide_rdma::TransferReply>,
    ) -> RdmaOutcome<Option<glide_rdma::ReadReceipt>> {
        let mut command = crate::rdma::protocol::redis_command(&command);
        let reply = tokio::select! {
            // A reply that is ready wins over a revoke that is also ready: the
            // transfer did finish, so the caller should hear that it did.
            biased;
            reply = self.dispatch(permit, &mut command, None) => Some(reply),
            () = loan.revoked() => None,
        };
        match reply {
            Some(Ok(value)) => reclaim_rdma_loan(loan, parse(value)),
            Some(Err(error)) => {
                // Decided before the error is rewritten as a cancellation below.
                let server_replied = error.is_server_reply();
                let client_closed = self.rdma_wait_if_closing().await;
                let error =
                    crate::rdma::cancelled_if_revoked(Err(error), loan.is_revoked(), client_closed)
                        .expect_err("an error stays an error");
                if server_replied {
                    reclaim_rdma_loan(loan, Err(error))
                } else {
                    recall_rdma_loan(loan, error)
                }
            }
            None => {
                let error = self.rdma_cancellation();
                recall_rdma_loan(loan, error)
            }
        }
    }

    /// The checks that can refuse a transfer before it is sent, run before the
    /// transfer lends its buffer, so that failing one hands the buffer straight back
    /// instead of revoking it: no open `MULTI`, the IAM reconcile, then
    /// [`DispatchPermit::take`]. The send then uses the returned permit and does not
    /// repeat them.
    ///
    /// A transfer sent while a `MULTI` is open would be queued, and the server would
    /// answer `+QUEUED` instead of replying to the transfer. The buffer would then come
    /// back while the queued transfer can still write into it on `EXEC`.
    ///
    /// The rest of [`Self::send_command`] is skipped on purpose: `BLOB.*` is what
    /// `refuse_direct_command` refuses, it does not open or close a transaction, and
    /// it is not a pubsub command. A check added to `send_command` that a transfer
    /// also needs must be added here too.
    async fn rdma_preflight(&mut self) -> Result<DispatchPermit, redis::RedisError> {
        if self.multi_active.load(std::sync::atomic::Ordering::Acquire) {
            return Err(crate::rdma::protocol::configuration_error(
                "an RDMA transfer cannot run inside an open MULTI; send EXEC or DISCARD first"
                    .to_string(),
            ));
        }
        self.reconcile_iam_before_dispatch().await?;
        DispatchPermit::take(self).await
    }

    /// Whether the client is closing, and if so, once the close has tried every
    /// region, so a failed transfer can tell whether it was cancelled.
    async fn rdma_wait_if_closing(&self) -> bool {
        let client_closed = self.rdma_regions.is_closed();
        if client_closed {
            self.rdma_regions.close_finished().await;
        }
        client_closed
    }

    fn rdma_cancellation(&self) -> redis::RedisError {
        crate::rdma::cancellation(self.rdma_regions.is_closed())
    }

    /// Refuse a buffer this client cannot transfer through, naming why.
    fn check_rdma_buffer(&self, buffer: &glide_rdma::RdmaBuffer) -> Result<(), redis::RedisError> {
        let rdma_fabric = self.require_rdma_fabric()?;
        if !buffer.is_registered_on(rdma_fabric) {
            return Err(crate::rdma::protocol::configuration_error(
                "buffer is registered on a different fabric".to_string(),
            ));
        }
        if self.rdma_regions.is_closed() {
            return Err(crate::rdma::protocol::cancelled(
                "the client is closed, so it cannot start a transfer",
            ));
        }
        if buffer.is_revoked() {
            return Err(crate::rdma::protocol::cancelled(
                "the region was revoked, so nothing can transfer through it",
            ));
        }
        Ok(())
    }

    /// The fabric this client opened or an error naming what is missing.
    fn require_rdma_fabric(&self) -> Result<&glide_rdma::RdmaFabric, redis::RedisError> {
        self.rdma_fabric
            .as_ref()
            .map(crate::rdma::Fabric::get)
            .ok_or_else(|| {
                crate::rdma::protocol::configuration_error(
                    "this client has no RDMA fabric: pass a RdmaConfiguration when creating it"
                        .to_string(),
                )
            })
    }
}
