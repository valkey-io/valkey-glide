// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Every region a client has registered, closing them all, and telling a
//! cancelled transfer apart from a failed one.

use super::protocol;

/// Every region a client has registered so that closing the client can revoke
/// them all.
///
/// Revoking a region is what cancels a transfer: the server can no longer reach
/// the memory and a transfer waiting on the region returns at once. Closing also
/// refuses any registration or transfer that comes after it, so nothing can
/// start against a client that has been closed.
#[derive(Debug)]
pub(crate) struct Regions {
    /// A revoker for every region registered and not yet revoked. A close holds
    /// this for its whole run, so a second close waits for the first to finish.
    revokers: std::sync::Mutex<Vec<glide_rdma::RdmaRevoker>>,
    /// Kept apart from the list so that reading it never waits on a
    /// registration, which holds the list while it registers memory.
    state: tokio::sync::watch::Sender<CloseState>,
}

/// How far a client has got with closing.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum CloseState {
    Open,
    /// A close has started and is revoking regions.
    Closing,
    /// A close has tried every region, whether or not each revoke succeeded.
    Closed,
}

impl Default for Regions {
    fn default() -> Self {
        Self {
            revokers: Default::default(),
            state: tokio::sync::watch::channel(CloseState::Open).0,
        }
    }
}

impl Regions {
    /// Run `register` and record the region it returns.
    pub fn register(
        &self,
        register: impl FnOnce() -> Result<glide_rdma::RdmaBuffer, glide_rdma::RdmaError>,
    ) -> Result<glide_rdma::RdmaBuffer, redis::RedisError> {
        let mut revokers = self.lock();
        if self.is_closed() {
            return Err(protocol::cancelled(
                "the client is closed, so it cannot register memory",
            ));
        }
        let buffer = register().map_err(protocol::as_redis_error)?;
        revokers.retain(|revoker| !revoker.is_released());
        revokers.push(buffer.revoker());
        Ok(buffer)
    }

    /// Revoke every region and refuse whatever comes after.
    ///
    /// A region that failed to close stays registered, and a transfer using it
    /// keeps waiting for its reply. It also stays on the list, so closing again
    /// reports the failure again; libfabric is never asked to close it twice.
    /// Otherwise closing again does nothing.
    pub fn close(&self) -> Result<(), redis::RedisError> {
        self.close_with(glide_rdma::RdmaRevoker::revoke)
    }

    /// [`Self::close`], revoking each region with `revoke`, so tests can make a
    /// revoke fail.
    fn close_with(
        &self,
        revoke: impl Fn(&glide_rdma::RdmaRevoker) -> Result<(), glide_rdma::RdmaError>,
    ) -> Result<(), redis::RedisError> {
        let mut revokers = self.lock();
        // A second close finds the first already `Closed` and leaves it.
        self.state.send_if_modified(|state| {
            let opening_close = *state == CloseState::Open;
            if opening_close {
                *state = CloseState::Closing;
            }
            opening_close
        });
        let mut first_failure = None;
        revokers.retain(|revoker| match revoke(revoker) {
            Ok(()) => false,
            Err(error) => {
                first_failure.get_or_insert(protocol::as_redis_error(error));
                true
            }
        });
        self.state.send_replace(CloseState::Closed);
        first_failure.map_or(Ok(()), Err)
    }

    /// Whether [`Self::close`] has been called. True from the start of a close,
    /// before its regions are revoked.
    pub fn is_closed(&self) -> bool {
        *self.state.borrow() != CloseState::Open
    }

    /// Resolves once a close has tried to revoke every region.
    pub async fn close_finished(&self) {
        let mut state = self.state.subscribe();
        // The sender lives as long as `self`, so this only returns once closed.
        let _ = state.wait_for(|state| *state == CloseState::Closed).await;
    }

    #[cfg(test)]
    fn tracked(&self) -> usize {
        self.lock().len()
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, Vec<glide_rdma::RdmaRevoker>> {
        self.revokers
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
    }
}

/// What cancelled a transfer.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum CancelCause {
    /// The client was closed, which revokes every region.
    ClientClosed,
    /// The transfer's own region was revoked.
    RegionRevoked,
}

impl std::fmt::Display for CancelCause {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            Self::ClientClosed => "the client was closed",
            Self::RegionRevoked => "its region was revoked",
        })
    }
}

/// The error a cancelled transfer returns, naming what cancelled it.
pub(crate) fn cancellation(cause: CancelCause) -> redis::RedisError {
    protocol::cancelled(cause.to_string())
}

/// The reply a transfer reports given what cancelled it, if anything.
///
/// - A failed reply with a CancelCause becomes a cancellation error since the
///   cancel is most likely what made it fail. The original error is kept in its
///   message, in case the transfer failed for an unrelated reason at the same time.
/// - A successful reply is kept since the transfer finished before the cancel.
/// - With no cause, the reply is kept as it is.
pub(crate) fn cancelled_if(
    reply: redis::RedisResult<redis::Value>,
    cause: Option<CancelCause>,
) -> redis::RedisResult<redis::Value> {
    match (reply, cause) {
        (Err(error), Some(cause)) => Err(protocol::cancelled(format!(
            "{cause} (the transfer failed with: {error})"
        ))),
        (reply, _) => reply,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use glide_rdma::{FabricConfig, Provider};

    fn server_error(detail: &str) -> redis::RedisError {
        redis::RedisError::from((
            redis::ErrorKind::ResponseError,
            redis::SERVER_ERROR_DESCRIPTION,
            detail.to_string(),
        ))
    }

    fn tcp_fabric() -> glide_rdma::RdmaFabric {
        glide_rdma::RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp opens")
    }

    #[test]
    fn closing_revokes_every_registered_region() {
        let fabric = tcp_fabric();
        let regions = Regions::default();
        let first = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();
        let second = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();

        regions.close().expect("both regions close");

        assert!(first.is_revoked());
        assert!(second.is_revoked());
        assert!(regions.is_closed());
        regions.close().expect("closing again is a no-op");
    }

    #[test]
    fn a_closed_client_registers_nothing() {
        let fabric = tcp_fabric();
        let regions = Regions::default();
        regions.close().unwrap();

        let mut called = false;
        let refused = regions
            .register(|| {
                called = true;
                fabric.register(vec![0u8; 64])
            })
            .expect_err("registration after close is refused");

        assert!(!called, "no memory is pinned for a refused registration");
        assert!(refused.to_string().contains("closed"), "{refused}");
    }

    #[test]
    fn freed_regions_drop_out_of_the_list() {
        let fabric = tcp_fabric();
        let regions = Regions::default();
        for _ in 0..10 {
            drop(regions.register(|| fabric.register(vec![0u8; 64])).unwrap());
        }
        let _kept = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();

        assert_eq!(regions.tracked(), 1);
    }

    #[test]
    fn a_failed_registration_is_not_recorded() {
        let fabric = tcp_fabric();
        let regions = Regions::default();

        regions
            .register(|| fabric.register(Vec::<u8>::new()))
            .expect_err("0 bytes cannot be registered");

        assert_eq!(regions.tracked(), 0);
    }

    /// What the server sends when a revoke beat its RMA.
    fn failed_rma() -> redis::RedisResult<redis::Value> {
        Err(server_error("EFA write"))
    }

    #[test]
    fn a_failure_on_a_revoked_region_is_the_cancellation() {
        let revoked = cancelled_if(failed_rma(), Some(CancelCause::RegionRevoked)).unwrap_err();
        assert!(
            revoked.to_string().contains("region was revoked"),
            "{revoked}"
        );
        assert_eq!(revoked.kind(), redis::ErrorKind::ClientError);
    }

    #[test]
    fn a_failure_while_the_client_closes_is_the_cancellation() {
        let closing = cancelled_if(failed_rma(), Some(CancelCause::ClientClosed)).unwrap_err();
        assert!(
            closing.to_string().contains("the client was closed"),
            "{closing}"
        );
        assert_eq!(closing.kind(), redis::ErrorKind::ClientError);
    }

    /// A server error that is unrelated to the cancel, such as a missing key, must
    /// still be visible to the caller.
    #[test]
    fn a_cancellation_keeps_the_servers_error() {
        let failure = Err(server_error("no such key"));
        let revoked = cancelled_if(failure, Some(CancelCause::RegionRevoked)).unwrap_err();
        let message = revoked.to_string();
        assert!(message.contains("region was revoked"), "{message}");
        assert!(message.contains("no such key"), "{message}");
    }

    #[test]
    fn a_failure_with_nothing_cancelled_is_the_servers_own() {
        let failure = cancelled_if(failed_rma(), None).unwrap_err();
        assert!(failure.to_string().contains("EFA write"), "{failure}");
    }

    /// The transfer finished before the cancel reached it, so it succeeded.
    #[test]
    fn a_success_on_a_cancelled_transfer_stays_a_success() {
        let reply = cancelled_if(Ok(redis::Value::Int(8)), Some(CancelCause::ClientClosed));
        assert_eq!(reply.unwrap(), redis::Value::Int(8));
    }

    #[tokio::test]
    async fn a_close_is_finished_once_every_region_was_tried() {
        let fabric = tcp_fabric();
        let regions = Regions::default();
        let _region = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();

        let waiting = tokio::time::timeout(
            std::time::Duration::from_millis(100),
            regions.close_finished(),
        )
        .await;
        assert!(waiting.is_err(), "nothing has closed yet");

        regions.close().unwrap();

        tokio::time::timeout(std::time::Duration::from_secs(5), regions.close_finished())
            .await
            .expect("the close has finished");
    }

    /// A failed close is final in glide-rdma: the region is never closed, and
    /// every later revoke reports the same failure. So the region stays on the
    /// list, and each later close asks again and reports the failure again.
    #[test]
    fn a_region_that_failed_to_close_stays_on_the_list_and_keeps_failing() {
        let fabric = tcp_fabric();
        let regions = Regions::default();
        let region = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();
        let attempts = std::sync::atomic::AtomicUsize::new(0);
        let failing_close = |_: &glide_rdma::RdmaRevoker| {
            attempts.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
            Err(glide_rdma::RdmaError::Fabric {
                operation: "fi_close",
                message: "Device or resource busy".to_string(),
                errno: Some(-16),
            })
        };

        for close in ["first", "second"] {
            let failed = regions.close_with(failing_close);
            assert!(failed.is_err(), "the {close} close reports the failure");
            assert!(
                !region.is_revoked(),
                "the server may still reach the memory"
            );
            assert_eq!(regions.tracked(), 1, "the failed region stays on the list");
        }
        assert_eq!(
            attempts.load(std::sync::atomic::Ordering::SeqCst),
            2,
            "each close asks the region again"
        );
    }

    #[test]
    fn a_second_close_waits_for_the_first_to_finish() {
        let fabric = tcp_fabric();
        let regions = std::sync::Arc::new(Regions::default());
        let region = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();

        let (started, first_is_revoking) = std::sync::mpsc::channel();
        let (release, released) = std::sync::mpsc::channel::<()>();
        let first = std::thread::spawn({
            let regions = regions.clone();
            move || {
                regions.close_with(|revoker| {
                    started.send(()).unwrap();
                    released.recv().unwrap();
                    revoker.revoke()
                })
            }
        });
        first_is_revoking.recv().unwrap();

        let (finished, second_finished) = std::sync::mpsc::channel();
        let second = std::thread::spawn({
            let regions = regions.clone();
            move || finished.send(regions.close())
        });
        assert!(
            second_finished
                .recv_timeout(std::time::Duration::from_millis(100))
                .is_err(),
            "the second close returned while the first was still revoking"
        );

        release.send(()).unwrap();
        first
            .join()
            .unwrap()
            .expect("the first close revokes the region");
        second_finished
            .recv_timeout(std::time::Duration::from_secs(5))
            .expect("the second close returns once the first has finished")
            .expect("nothing was left for it to revoke");
        second.join().unwrap().unwrap();
        assert!(region.is_revoked());
    }

    #[test]
    fn asking_whether_closed_does_not_wait_on_a_registration() {
        let regions = std::sync::Arc::new(Regions::default());
        let registering = regions.lock();

        let (answered, answer) = std::sync::mpsc::channel();
        let asking = regions.clone();
        std::thread::spawn(move || answered.send(asking.is_closed()));

        let closed = answer.recv_timeout(std::time::Duration::from_secs(5));
        drop(registering);
        assert_eq!(closed, Ok(false), "answered while the list was held");
    }
}
