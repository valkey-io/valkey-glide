// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The client's RDMA methods: registering memory, transferring through it, the
//! held-memory counters, and closing.
//!
//! Only built with the `rdma` feature. The rest of the RDMA support in the client
//! stays in `mod.rs`: the fields that hold the fabric and the registered regions,
//! the timeout and blocking rules for the transfer commands, and the guards that
//! refuse those commands anywhere else.

use super::{Client, DispatchPermit};

/// What an RDMA transfer hands back: the buffer when the server is known to be
/// done with it, and how the transfer went.
#[derive(Debug)]
pub enum RdmaOutcome<T> {
    /// The server is known to be done with the memory, so the buffer comes back.
    ///
    /// This happens in three ways:
    ///
    /// - The server answered the transfer with its result or with an error
    /// - The transfer was refused before the buffer was lent because of:
    ///   - a buffer was revoked or already registered on another fabric
    ///   - a closed client
    ///   - a full in-flight limit
    ///   - an open circuit breaker
    ///   - a window out of bounds
    /// - The handshake failed, so the transfer was never sent because:
    ///   - the server refused the handshake
    ///   - the server's handshake reply could not be read
    ///   - this client could not open its side of the session
    ///   - an earlier handshake on the same connection never finished
    ///
    ///   (In every handshake failure case but the first, the connection is
    ///   replaced and a cluster client sends the transfer again on the new one).
    Settled {
        /// The buffer that was passed in, ready for the next transfer.
        buffer: glide_rdma::RdmaBuffer,
        /// The transfer's result, or why it was refused or failed.
        result: Result<T, redis::RedisError>,
    },
    /// The server may still be using the memory, so no buffer comes back.
    /// Register the memory again for the next transfer.
    ///
    /// This happens in two ways:
    ///
    /// - The transfer was cancelled or its connection failed. This holds
    ///   even if the command never reached the server.
    /// - The server's reply is neither a result nor an error. For example,
    ///   inside a `MULTI` the server answers `+QUEUED` and runs the transfer
    ///   later on `EXEC`.
    ///
    /// The buffer's region is closed, and its memory is:
    ///
    /// - freed, on efa-direct;
    /// - held until the fabric closes, on tcp (see [`Client::rdma_parked_bytes`]);
    /// - kept until the process exits, if closing the region failed (see
    ///   [`Client::rdma_leaked_bytes`]).
    Recalled(redis::RedisError),
}

/// A RDMA transfer command: how it lends the buffer, how it reads the server's reply,
/// and what it makes of the bytes once the buffer is back.
trait Transfer {
    type Reply;

    /// Build the command and lend the buffer, or hand it back with why not.
    // As in glide-rdma: a failed lend returns the buffer inside the `Err`, so the
    // caller never loses memory it registered.
    #[allow(clippy::result_large_err)]
    fn lend(
        &self,
        buffer: glide_rdma::RdmaBuffer,
    ) -> Result<
        (glide_rdma::RdmaCommand, glide_rdma::LentBuffer),
        (glide_rdma::RdmaBuffer, glide_rdma::RdmaError),
    >;

    /// Read the server's answer into what ends the loan.
    fn parse(value: redis::Value) -> redis::RedisResult<glide_rdma::TransferReply>;

    /// Turn a reclaimed loan's receipt into the caller's result.
    fn finish(
        &self,
        buffer: &glide_rdma::RdmaBuffer,
        receipt: Option<glide_rdma::ReadReceipt>,
    ) -> redis::RedisResult<Self::Reply>;
}

/// `BLOB.RDMA_GET` into the `[offset, offset + length)` window.
struct Get<'a> {
    key: &'a [u8],
    offset: usize,
    length: usize,
}

impl Transfer for Get<'_> {
    type Reply = Option<glide_rdma::ReadReceipt>;

    fn lend(
        &self,
        buffer: glide_rdma::RdmaBuffer,
    ) -> Result<
        (glide_rdma::RdmaCommand, glide_rdma::LentBuffer),
        (glide_rdma::RdmaBuffer, glide_rdma::RdmaError),
    > {
        buffer.lend_for_get(self.key, self.offset, self.length)
    }

    fn parse(value: redis::Value) -> redis::RedisResult<glide_rdma::TransferReply> {
        crate::rdma::protocol::parse_read_reply(value)
    }

    /// Check the checksum the server reported against the bytes that landed.
    fn finish(
        &self,
        buffer: &glide_rdma::RdmaBuffer,
        receipt: Option<glide_rdma::ReadReceipt>,
    ) -> redis::RedisResult<Self::Reply> {
        if let Some(receipt) = &receipt {
            receipt
                .verify(&buffer.as_host()[self.offset..])
                .map_err(crate::rdma::protocol::as_redis_error)?;
        }
        Ok(receipt)
    }
}

/// `BLOB.RDMA_SET` out of the `[offset, offset + length)` window.
struct Set<'a> {
    key: &'a [u8],
    offset: usize,
    length: usize,
    checksum: glide_rdma::SetChecksum,
}

impl Transfer for Set<'_> {
    type Reply = ();

    fn lend(
        &self,
        buffer: glide_rdma::RdmaBuffer,
    ) -> Result<
        (glide_rdma::RdmaCommand, glide_rdma::LentBuffer),
        (glide_rdma::RdmaBuffer, glide_rdma::RdmaError),
    > {
        buffer.lend_for_set(self.key, self.offset, self.length, self.checksum)
    }

    fn parse(value: redis::Value) -> redis::RedisResult<glide_rdma::TransferReply> {
        crate::rdma::protocol::parse_write_reply(value)
    }

    fn finish(
        &self,
        _buffer: &glide_rdma::RdmaBuffer,
        _receipt: Option<glide_rdma::ReadReceipt>,
    ) -> redis::RedisResult<()> {
        Ok(())
    }
}

/// End a loan the server is done with. `reply` is what the server answered: the
/// transfer's result or the error it replied with. A handshake failure that kept
/// the transfer from being sent counts as an error reply.
fn reclaim_rdma_loan(
    loan: glide_rdma::LentBuffer,
    reply: Result<glide_rdma::TransferReply, redis::RedisError>,
) -> RdmaOutcome<Option<glide_rdma::ReadReceipt>> {
    let (reply, error) = match reply {
        Ok(reply) => (reply, None),
        Err(error) => (glide_rdma::TransferReply::Failed, Some(error)),
    };
    // SAFETY: `reply` is the server's answer to this loan's command or an error
    // that `crate::rdma::server_is_done_with` accepts, so the server is done with
    // the window.
    let (buffer, result) = match unsafe { loan.reclaim(reply) } {
        Ok((buffer, receipt)) => (buffer, error.map_or(Ok(receipt), Err)),
        Err((buffer, error)) => (buffer, Err(crate::rdma::protocol::as_redis_error(error))),
    };
    RdmaOutcome::Settled { buffer, result }
}

/// End a loan the server may still be using: close the region, then free its
/// memory on efa-direct or park it until the fabric closes on tcp. No buffer comes
/// back.
fn recall_rdma_loan<T>(loan: glide_rdma::LentBuffer, error: redis::RedisError) -> RdmaOutcome<T> {
    // If the region fails to close, the server may still reach the memory. The
    // loan that comes back is dropped here, which keeps that memory for the life
    // of the process and counts it as leaked; the close is never tried again.
    let _ = loan.recall();
    RdmaOutcome::Recalled(error)
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
    /// ended before the server was known to be done with them, so the server might
    /// still write into them. Only grows while the client lives.
    ///
    /// Stays at zero on efa-direct, where closing a region stops the server's writes.
    /// On tcp, which is for development, every transfer whose buffer does not come
    /// back holds its memory: one that was cancelled, abandoned, lost its
    /// connection, or got a reply that was neither a result nor an error; see
    /// [`RdmaOutcome`].
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
    /// bytes are already in the window by the time this returns and match the
    /// checksum the server reported.
    pub async fn rdma_get(
        &mut self,
        key: &[u8],
        buffer: glide_rdma::RdmaBuffer,
        offset: usize,
        length: usize,
    ) -> RdmaOutcome<Option<glide_rdma::ReadReceipt>> {
        let get = Get {
            key,
            offset,
            length,
        };
        self.transfer(&get, buffer).await
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
        let set = Set {
            key,
            offset,
            length,
            checksum,
        };
        self.transfer(&set, buffer).await
    }

    /// The steps every transfer runs, in this order:
    ///
    /// 1. [`Self::check_rdma_buffer`], then [`Self::rdma_preflight`].
    /// 2. [`Transfer::lend`], which builds the command and lends the buffer, or
    ///    refuses a window out of bounds.
    /// 3. [`Self::rdma_transfer`], which sends the command and ends the loan.
    /// 4. [`Transfer::finish`] once the buffer is back.
    ///
    /// A refusal in step 1 or 2 hands the buffer straight back since nothing has
    /// been lent yet. So a check that every transfer needs belongs in step 1.
    async fn transfer<T: Transfer>(
        &mut self,
        transfer: &T,
        buffer: glide_rdma::RdmaBuffer,
    ) -> RdmaOutcome<T::Reply> {
        if let Err(error) = self.check_rdma_buffer(&buffer) {
            return RdmaOutcome::Settled {
                buffer,
                result: Err(error),
            };
        }
        let permit = match self.rdma_preflight().await {
            Ok(permit) => permit,
            Err(error) => {
                return RdmaOutcome::Settled {
                    buffer,
                    result: Err(error),
                };
            }
        };
        let (command, loan) = match transfer.lend(buffer) {
            Ok(lent) => lent,
            Err((buffer, error)) => {
                return RdmaOutcome::Settled {
                    buffer,
                    result: Err(crate::rdma::protocol::as_redis_error(error)),
                };
            }
        };
        match self.rdma_transfer(command, loan, permit, T::parse).await {
            RdmaOutcome::Settled { buffer, result } => {
                let result = result.and_then(|receipt| transfer.finish(&buffer, receipt));
                RdmaOutcome::Settled { buffer, result }
            }
            RdmaOutcome::Recalled(error) => RdmaOutcome::Recalled(error),
        }
    }

    /// Send a lent buffer's command and end the loan, as [`RdmaOutcome`] describes.
    ///
    /// The loan ends one of two ways:
    ///
    /// - Reclaimed, handing the buffer back, when `parse` accepts the reply or
    ///   [`crate::rdma::server_is_done_with`] accepts the error.
    /// - Recalled, closing the region first, in every other case: a reply `parse`
    ///   rejects, any other error, or a revoke that wins the race against the
    ///   reply.
    ///
    /// A reply `parse` rejects is recalled because the server may have queued the
    /// transfer rather than run it, as it does inside a `MULTI` (`+QUEUED`).
    /// [`Self::rdma_preflight`] refuses an open `MULTI`, but cannot catch one that
    /// a clone opens on the same connection while this transfer waits to be sent.
    ///
    /// A reply proves the server is done only with the attempt it answers. That
    /// is enough, because the cluster client sends a transfer again only when the
    /// server refused it without running it, or when it was never sent. So no
    /// earlier attempt can still be using the memory.
    ///
    /// A connection error saying the command was never sent (`FatalSendError`) is
    /// still recalled, unless the handshake built it. That is more cautious than
    /// needed, but costs only a new registration.
    ///
    /// `permit` is what [`Self::rdma_preflight`] returned in [`Self::transfer`],
    /// before the buffer was lent.
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
            Some(Ok(value)) => match parse(value) {
                Ok(reply) => reclaim_rdma_loan(loan, Ok(reply)),
                Err(error) => recall_rdma_loan(loan, error),
            },
            Some(Err(error)) => {
                // Decided before the error is rewritten as a cancellation below.
                let server_is_done = crate::rdma::server_is_done_with(&error);
                let cause = self.rdma_cancel_cause(&loan).await;
                let error = crate::rdma::cancelled_if(Err(error), cause)
                    .expect_err("an error stays an error");
                if server_is_done {
                    reclaim_rdma_loan(loan, Err(error))
                } else {
                    recall_rdma_loan(loan, error)
                }
            }
            None => {
                // The revoke fired, so something cancelled the transfer.
                let cause = self.rdma_cancel_cause(&loan).await;
                let error = crate::rdma::cancellation(
                    cause.unwrap_or(crate::rdma::CancelCause::RegionRevoked),
                );
                recall_rdma_loan(loan, error)
            }
        }
    }

    /// The checks that can refuse a transfer before it is sent: no open `MULTI`,
    /// the IAM reconcile, then [`DispatchPermit::take`]. [`Self::transfer`] runs
    /// them before it lends the buffer, so failing one hands the buffer
    /// straight back instead of revoking it. The send then uses the returned permit
    /// and does not repeat them.
    ///
    /// A transfer sent while a `MULTI` is open would be queued, and the server would
    /// answer `+QUEUED` instead of replying to the transfer. [`Self::rdma_transfer`]
    /// would then have to recall the buffer, since the queued transfer can still use
    /// it on `EXEC`. Refusing here hands it back instead.
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

    /// What cancelled the transfer that lent `loan`, if anything.
    ///
    /// A closing client wins over a revoked region, since closing is what revoked
    /// it. While the client is closing, this waits until the close has tried every
    /// region.
    fn rdma_cancel_cause(
        &self,
        loan: &glide_rdma::LentBuffer,
    ) -> impl std::future::Future<Output = Option<crate::rdma::CancelCause>> + '_ {
        // Both are read before the wait: a `LentBuffer` is not `Sync`, so holding
        // `loan` across an `.await` would stop the transfer's future being `Send`.
        let client_closed = self.rdma_regions.is_closed();
        let region_revoked = loan.is_revoked();
        async move {
            if client_closed {
                self.rdma_regions.close_finished().await;
                Some(crate::rdma::CancelCause::ClientClosed)
            } else if region_revoked {
                Some(crate::rdma::CancelCause::RegionRevoked)
            } else {
                None
            }
        }
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
