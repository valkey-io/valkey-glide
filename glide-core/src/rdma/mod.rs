// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! RDMA configuration, handshake errors, and cancelling transfers by revoking
//! their regions.
//!
//! RDMA is opt-in at build time through the `rdma` feature.
//!
//! The RESP side of the RDMA commands lives in the `protocol` module.

#[cfg(feature = "rdma")]
pub(crate) mod protocol;

/// Why a `LO.HELLO` was refused by the server.
#[cfg(feature = "rdma")]
pub(crate) fn handshake_refused(node: &str, error: redis::RedisError) -> redis::RedisError {
    if !answered_by_server(&error) {
        return error;
    }
    if error.kind() == redis::ErrorKind::ReadOnly {
        let reason = RdmaUnavailable::HandshakeRefused {
            node: node.to_string(),
            reason: error.to_string(),
        };
        return redis::RedisError::from((
            redis::ErrorKind::ReadOnly,
            "An error was signalled by the server:",
            reason.to_string(),
        ));
    }
    let message = error.to_string().to_ascii_lowercase();
    let reason =
        if message.contains("unknown command") || message.contains("unknown or disabled command") {
            RdmaUnavailable::ServerModuleMissing {
                node: node.to_string(),
            }
        } else {
            RdmaUnavailable::HandshakeRefused {
                node: node.to_string(),
                reason: error.to_string(),
            }
        };
    protocol::configuration_error(reason.to_string())
}

/// A `LO.HELLO` reply this client could not read.
///
/// Different from a refusal because it indicates the node has the
/// module and answered, but the two disagree about the reply's shape.
#[cfg(feature = "rdma")]
pub(crate) fn malformed_handshake(node: &str, error: redis::RedisError) -> redis::RedisError {
    protocol::as_redis_error(glide_rdma::RdmaError::Protocol(
        RdmaUnavailable::MalformedHandshake {
            node: node.to_string(),
            reason: error.to_string(),
        }
        .to_string(),
    ))
}

/// Whether the server answered the handshake at all.
#[cfg(feature = "rdma")]
fn answered_by_server(error: &redis::RedisError) -> bool {
    !error.is_io_error()
        && !error.is_connection_dropped()
        && !error.is_timeout()
        && !matches!(
            error.kind(),
            redis::ErrorKind::IoError
                | redis::ErrorKind::BusyLoadingError
                | redis::ErrorKind::TryAgain
        )
}

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

/// Every region a client has registered so that closing the client can revoke
/// them all.
///
/// Revoking a region is what cancels a transfer: the server can no longer reach
/// the memory and a transfer waiting on the region returns at once. Closing also
/// refuses any registration or transfer that comes after it, so nothing can
/// start against a client that has been closed.
#[cfg(feature = "rdma")]
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
#[cfg(feature = "rdma")]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum CloseState {
    Open,
    /// A close has started and is revoking regions.
    Closing,
    /// A close has tried every region, whether or not each revoke succeeded.
    Closed,
}

#[cfg(feature = "rdma")]
impl Default for Regions {
    fn default() -> Self {
        Self {
            revokers: Default::default(),
            state: tokio::sync::watch::channel(CloseState::Open).0,
        }
    }
}

#[cfg(feature = "rdma")]
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
    /// keeps waiting for its reply; it also stays on the list, so closing again
    /// tries it again. Otherwise closing again does nothing.
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

/// The error a cancelled transfer returns, naming what cancelled it.
#[cfg(feature = "rdma")]
pub(crate) fn cancellation(client_closed: bool) -> redis::RedisError {
    protocol::cancelled(if client_closed {
        "the client was closed"
    } else {
        "its region was revoked"
    })
}

/// Convert a failed transfer reply into a cancelled error, or report a successful
/// reply if it completed before the region was revoked or the client started closing.
#[cfg(feature = "rdma")]
pub(crate) fn cancelled_if_revoked(
    reply: redis::RedisResult<redis::Value>,
    region_revoked: bool,
    client_closed: bool,
) -> redis::RedisResult<redis::Value> {
    match reply {
        Err(_) if region_revoked || client_closed => Err(cancellation(client_closed)),
        reply => reply,
    }
}

/// Refuse an `LO.*` command that did not come from the client's RDMA methods,
/// `Client::rdma_get` and `Client::rdma_set`.
pub(crate) fn refuse_direct(name: &[u8]) -> redis::RedisResult<()> {
    const COMMANDS: [&[u8]; 3] = [b"LO.HELLO", b"LO.GET", b"LO.SET"];
    if !COMMANDS
        .iter()
        .any(|command| name.eq_ignore_ascii_case(command))
    {
        return Ok(());
    }
    Err(redis::RedisError::from((
        redis::ErrorKind::ClientError,
        "RDMA command refused",
        format!(
            "{} can only be sent by the client's RDMA transfer methods",
            String::from_utf8_lossy(name)
        ),
    )))
}

/// [`refuse_direct`] for a built command.
pub(crate) fn refuse_direct_command(command: &redis::Cmd) -> redis::RedisResult<()> {
    match command.args_iter().next() {
        Some(redis::Arg::Simple(name)) => refuse_direct(name),
        _ => Ok(()),
    }
}

/// [`refuse_direct`] for every command in a batch.
pub(crate) fn refuse_direct_in_batch(batch: &redis::Pipeline) -> redis::RedisResult<()> {
    batch
        .cmd_iter()
        .try_for_each(|command| refuse_direct_command(command))
}

/// Uninhabited without the `rdma` feature, so `Option<Fabric>` can only ever be
/// `None` there. The type still exists, which is what lets every signature that
/// carries one stay the same in both builds.
#[cfg(not(feature = "rdma"))]
#[derive(Clone, Debug)]
pub enum Fabric {}

/// What a connection request asked of RDMA and whether it can be honored.
#[derive(Debug, Clone, Default)]
pub enum RdmaSetting {
    /// No RDMA was requested. The zero-cost default.
    #[default]
    Absent,
    /// RDMA was requested but cannot be honored, for this reason.
    Rejected(RdmaUnavailable),
    /// RDMA was requested and the configuration is usable.
    #[cfg(feature = "rdma")]
    Configured(FabricConfig),
}

/// The reason a requested RDMA configuration cannot be honored.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
#[non_exhaustive]
pub enum RdmaUnavailable {
    /// No RDMA support compiled in but the configuration is valid.
    #[error(
        "this build of GLIDE has no RDMA support compiled in; \
         it was built from source without the `rdma` feature."
    )]
    NotCompiledIn,

    /// No provider was named.
    #[error("RdmaConfiguration requires a provider; none was set")]
    NoProviderConfigured,

    /// A node did not recognise the RDMA commands, so whatever module serves them
    /// is not loaded there.
    #[error("node {node} has no RDMA module loaded")]
    ServerModuleMissing {
        /// The node that answered without the module.
        node: String,
    },

    /// A node answered the handshake with an error of its own. A replica does
    /// this because the module registers `LO.HELLO` as a write command.
    #[error("node {node} refused the RDMA handshake: {reason}")]
    HandshakeRefused {
        /// The node that refused.
        node: String,
        /// What it said.
        reason: String,
    },

    /// The handshake reply could not be read.
    #[error("node {node} returned a malformed LO.HELLO reply: {reason}")]
    MalformedHandshake {
        /// The node that answered.
        node: String,
        /// What was wrong with the reply.
        reason: String,
    },

    /// The fabric refused the configuration.
    #[cfg(feature = "rdma")]
    #[error("{0}")]
    Fabric(#[from] glide_rdma::RdmaError),
}

impl RdmaSetting {
    /// Whether the request asked for RDMA at all
    pub fn is_requested(&self) -> bool {
        !matches!(self, RdmaSetting::Absent)
    }

    /// A fragment for the connection log, empty when RDMA was not requested.
    pub fn log_summary(&self) -> String {
        match self {
            RdmaSetting::Absent => String::new(),
            RdmaSetting::Rejected(reason) => format!("\nRDMA: unavailable ({reason})"),
            #[cfg(feature = "rdma")]
            RdmaSetting::Configured(config) => {
                let interface = config
                    .interface()
                    .map(|interface| format!(", interface: {interface}"))
                    .unwrap_or_default();
                let bind = config
                    .bind()
                    .map(|bind| format!(", bind: {bind}"))
                    .unwrap_or_default();
                format!("\nRDMA: {:?}{interface}{bind}", config.provider())
            }
        }
    }
}

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

    #[cfg(feature = "rdma")]
    fn server_error(detail: &str) -> redis::RedisError {
        redis::RedisError::from((
            redis::ErrorKind::ResponseError,
            "An error was signalled by the server",
            detail.to_string(),
        ))
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_server_that_does_not_know_the_command_has_no_module() {
        for detail in [
            "unknown command 'LO.HELLO'",
            "unknown or disabled command 'LO.HELLO'",
        ] {
            let failure = handshake_refused("10.0.0.1:6379", server_error(detail));
            let message = failure.to_string();

            assert!(message.contains("no RDMA module loaded"), "{message}");
            assert!(message.contains("10.0.0.1:6379"), "{message}");
        }
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_replica_refusal_names_the_node_and_stays_readonly() {
        let failure = handshake_refused("10.0.0.1:6379", replica_refusal());

        assert_eq!(failure.kind(), redis::ErrorKind::ReadOnly);
        let message = failure.to_string();
        assert!(message.contains("10.0.0.1:6379"), "{message}");
        assert!(message.contains("refused the RDMA handshake"), "{message}");
        assert!(message.contains("read only replica"), "{message}");
    }

    /// A `READONLY` reply exactly as the parser builds it from the wire.
    #[cfg(feature = "rdma")]
    fn replica_refusal() -> redis::RedisError {
        redis::parse_redis_value(b"-READONLY You can't write against a read only replica.\r\n")
            .and_then(|reply| reply.extract_error())
            .expect_err("a READONLY reply is an error")
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_handshake_that_was_never_answered_is_returned_unchanged() {
        for unanswered in [
            redis::RedisError::from(std::io::Error::from(std::io::ErrorKind::BrokenPipe)),
            redis::RedisError::from((redis::ErrorKind::IoError, "connection closed")),
            redis::RedisError::from((redis::ErrorKind::BusyLoadingError, "LOADING")),
            redis::RedisError::from((redis::ErrorKind::TryAgain, "TRYAGAIN")),
        ] {
            let kind = unanswered.kind();

            let failure = handshake_refused("10.0.0.1:6379", unanswered);

            assert_eq!(
                failure.kind(),
                kind,
                "{kind:?} must reach the caller unchanged"
            );
        }
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn an_unreadable_reply_names_the_node_and_stays_a_protocol_error() {
        let unreadable = protocol::as_redis_error(glide_rdma::RdmaError::Protocol(
            "lo.hello returned no addresses".to_string(),
        ));

        let failure = malformed_handshake("10.0.0.1:6379", unreadable);

        assert_eq!(failure.kind(), redis::ErrorKind::TypeError);
        let message = failure.to_string();
        assert!(message.contains("10.0.0.1:6379"), "{message}");
        assert!(message.contains("malformed LO.HELLO reply"), "{message}");
    }

    #[test]
    fn an_absent_setting_opens_nothing() {
        assert!(matches!(open(&RdmaSetting::Absent), Ok(None)));
    }

    #[test]
    fn rdma_commands_are_refused_outside_the_rdma_methods() {
        for name in [b"LO.HELLO".as_slice(), b"LO.GET", b"LO.SET", b"lo.get"] {
            let error = refuse_direct(name).expect_err("an RDMA command is refused");
            assert_eq!(error.kind(), redis::ErrorKind::ClientError);
            assert!(
                error.to_string().contains("RDMA transfer methods"),
                "{error}"
            );
        }
        for name in [b"GET".as_slice(), b"SET", b"LO", b"LO.GETX", b""] {
            assert!(
                refuse_direct(name).is_ok(),
                "{:?}",
                String::from_utf8_lossy(name)
            );
        }
    }

    #[test]
    fn a_batch_with_an_rdma_command_is_refused() {
        let mut batch = redis::pipe();
        batch.cmd("SET").arg("key").arg("value");
        assert!(refuse_direct_in_batch(&batch).is_ok());

        batch.cmd("LO.SET").arg("key").arg("8").arg("7").arg("4096");
        assert!(refuse_direct_in_batch(&batch).is_err());
    }

    #[test]
    fn an_absent_setting_logs_nothing() {
        assert_eq!(RdmaSetting::Absent.log_summary(), "");
    }

    #[test]
    fn not_compiled_in_names_the_remedy() {
        let setting = RdmaSetting::Rejected(RdmaUnavailable::NotCompiledIn);
        let error = open(&setting).unwrap_err();
        let message = error.to_string();
        assert!(message.contains("built from source"), "{message}");
        assert!(message.contains("`rdma` feature"), "{message}");
    }

    #[cfg(feature = "rdma")]
    fn tcp_fabric() -> glide_rdma::RdmaFabric {
        glide_rdma::RdmaFabric::open(&FabricConfig::new(Provider::Tcp)).expect("tcp opens")
    }

    #[cfg(feature = "rdma")]
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

    #[cfg(feature = "rdma")]
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

    #[cfg(feature = "rdma")]
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

    #[cfg(feature = "rdma")]
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
    #[cfg(feature = "rdma")]
    fn failed_rma() -> redis::RedisResult<redis::Value> {
        Err(server_error("EFA write"))
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_failure_on_a_revoked_region_is_the_cancellation() {
        let closed = cancelled_if_revoked(failed_rma(), true, true).unwrap_err();
        assert!(
            closed.to_string().contains("the client was closed"),
            "{closed}"
        );
        assert_eq!(closed.kind(), redis::ErrorKind::ClientError);

        let revoked = cancelled_if_revoked(failed_rma(), true, false).unwrap_err();
        assert!(
            revoked.to_string().contains("region was revoked"),
            "{revoked}"
        );
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_failure_while_the_client_closes_is_the_cancellation() {
        let closing = cancelled_if_revoked(failed_rma(), false, true).unwrap_err();
        assert!(
            closing.to_string().contains("the client was closed"),
            "{closing}"
        );
    }

    #[cfg(feature = "rdma")]
    #[test]
    fn a_failure_on_a_live_region_is_the_servers_own() {
        let failure = cancelled_if_revoked(failed_rma(), false, false).unwrap_err();
        assert!(failure.to_string().contains("EFA write"), "{failure}");
    }

    /// The transfer finished before the revoke reached it, so it succeeded.
    #[cfg(feature = "rdma")]
    #[test]
    fn a_success_on_a_revoked_region_stays_a_success() {
        let reply = cancelled_if_revoked(Ok(redis::Value::Int(8)), true, true);
        assert_eq!(reply.unwrap(), redis::Value::Int(8));
    }

    #[cfg(feature = "rdma")]
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

    #[cfg(feature = "rdma")]
    #[test]
    fn a_region_that_failed_to_close_is_tried_again() {
        let fabric = tcp_fabric();
        let regions = Regions::default();
        let region = regions.register(|| fabric.register(vec![0u8; 64])).unwrap();

        let failed = regions.close_with(|_| {
            Err(glide_rdma::RdmaError::Fabric {
                operation: "fi_close",
                message: "Device or resource busy".to_string(),
                errno: Some(-16),
            })
        });

        assert!(failed.is_err());
        assert!(!region.is_revoked());
        assert_eq!(regions.tracked(), 1, "the failed region stays on the list");

        regions.close().expect("the second close revokes it");

        assert!(region.is_revoked());
        assert_eq!(regions.tracked(), 0);
    }

    #[cfg(feature = "rdma")]
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

    #[cfg(feature = "rdma")]
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
