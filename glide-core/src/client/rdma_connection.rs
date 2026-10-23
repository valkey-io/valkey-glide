// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! The multiplexed RESP connection and the RDMA session that belongs to it.
//!
//! A server records where to reach a client's memory against the connection the
//! handshake arrived on, so this pairs the connection with its RDMA session on
//! the client side as well.
//!
//! The session is opened by the first transfer that needs it rather than when
//! the connection is built. A connection that never carries a transfer -- every
//! replica connection, every connection of a client that only ever sends
//! ordinary commands -- never sends `BLOB.RDMA_HELLO` at all.

#[cfg(not(feature = "rdma"))]
pub type GlideConnectionWithRdma = redis::aio::MultiplexedConnection;

#[cfg(feature = "rdma")]
pub use with_rdma::GlideConnectionWithRdma;

#[cfg(feature = "rdma")]
mod with_rdma {
    use std::net::{IpAddr, SocketAddr};
    use std::ops::{Deref, DerefMut};
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::time::Duration;

    use redis::aio::MultiplexedConnection;
    use redis::cluster_async::Connect;
    use redis::{
        Cmd, GlideConnectionOptions, IntoConnectionInfo, Pipeline, PipelineRetryStrategy,
        RedisError, RedisFuture, RedisResult, Value, aio::ConnectionLike,
    };

    use tokio::sync::OnceCell;

    /// A connection and the RDMA session that may be opened on it.
    #[derive(Clone)]
    pub struct GlideConnectionWithRdma {
        inner: MultiplexedConnection,
        /// `None` when RDMA is not configured.
        rdma: Option<Arc<Rdma>>,
    }

    /// What a connection needs to open its session, and the session once open.
    struct Rdma {
        fabric: glide_rdma::RdmaFabric,
        /// Only for error messages: a failed handshake has to say which node.
        node: String,
        session: OnceCell<glide_rdma::RdmaSession>,
        /// Whether a `BLOB.RDMA_HELLO` sent on this connection may have opened a
        /// session on the server that this client never opened on its side.
        ///
        /// The server refuses a second handshake on a connection that has a
        /// session, so once this is set, the connection can never carry a
        /// transfer and has to be replaced.
        unsettled: AtomicBool,
    }

    impl Rdma {
        /// Open the session if needed and not already open.
        async fn open_session_if_needed(
            &self,
            connection: &mut MultiplexedConnection,
            command: &Cmd,
        ) -> RedisResult<()> {
            if self.session.initialized() || !needs_session(command) {
                return Ok(());
            }
            self.session
                .get_or_try_init(|| self.handshake_once(connection))
                .await?;
            Ok(())
        }

        /// Run the handshake, unless an earlier one on this connection may have
        /// opened a session on the server.
        ///
        /// The check is here rather than before `get_or_try_init` because a
        /// transfer waiting on the cell takes over the handshake when the one
        /// running it is dropped, and must not send a second `BLOB.RDMA_HELLO`.
        ///
        /// The handshake is unsettled from the moment it is sent. It settles in
        /// only two ways:
        ///
        /// - The session opens on both sides.
        /// - The server answers with an error, which means it opened nothing.
        ///
        /// Anything else leaves it unsettled: the future was dropped while waiting
        /// for the reply, the reply could not be read, the client failed to open
        /// its side of the session, or the connection failed.
        async fn handshake_once(
            &self,
            connection: &mut MultiplexedConnection,
        ) -> RedisResult<glide_rdma::RdmaSession> {
            if self.unsettled.load(Ordering::Acquire) {
                return Err(crate::rdma::handshake_unsettled(&self.node));
            }
            self.unsettled.store(true, Ordering::Release);
            let result = handshake(connection, &self.fabric, &self.node).await;
            if matches!(&result, Err(HandshakeFailure::Refused(_)) | Ok(_)) {
                self.unsettled.store(false, Ordering::Release);
            }
            result.map_err(HandshakeFailure::into_error)
        }
    }

    fn needs_session(command: &Cmd) -> bool {
        match command.args_iter().next() {
            Some(redis::Arg::Simple(name)) => glide_rdma::needs_session(name),
            _ => false,
        }
    }

    impl GlideConnectionWithRdma {
        /// Pair a connection with the fabric its session will be opened on.
        pub(crate) fn open(
            inner: MultiplexedConnection,
            rdma_fabric: Option<glide_rdma::RdmaFabric>,
            node: &str,
        ) -> Self {
            Self {
                inner,
                rdma: rdma_fabric.map(|fabric| {
                    Arc::new(Rdma {
                        fabric,
                        node: node.to_string(),
                        session: OnceCell::new(),
                        unsettled: AtomicBool::new(false),
                    })
                }),
            }
        }

        /// Send one command, opening this connection's RDMA session first if the
        /// command is a transfer and no session is open yet.
        pub async fn send_packed_command(&mut self, cmd: &Cmd) -> RedisResult<Value> {
            // The session is read through `rdma` while the handshake
            // it may run needs `inner` mutably.
            let Self { inner, rdma } = self;
            if let Some(rdma) = rdma.as_ref() {
                rdma.open_session_if_needed(inner, cmd).await?;
            }
            inner.send_packed_command(cmd).await
        }
    }

    /// Why a handshake failed, split by whether the server may hold a session.
    enum HandshakeFailure {
        /// The server answered with an error, so it opened no session.
        Refused(RedisError),
        /// The server may hold a session this client does not.
        Unsettled(RedisError),
    }

    impl HandshakeFailure {
        fn into_error(self) -> RedisError {
            match self {
                Self::Refused(error) | Self::Unsettled(error) => error,
            }
        }
    }

    /// Run `BLOB.RDMA_HELLO` on this connection and open the session it reports.
    /// Should be run only once per RESP connection; see [`Rdma::handshake_once`].
    ///
    /// The handshake has no timeout. It runs inside the dispatch of the transfer
    /// that needs it, after that transfer lent its buffer, so it waits as the
    /// transfer does. A timeout would not leave the connection usable anyway: the
    /// server may still record the handshake after the client gave up on it, and
    /// would then refuse a second one on the same connection.
    async fn handshake(
        connection: &mut MultiplexedConnection,
        rdma_fabric: &glide_rdma::RdmaFabric,
        node: &str,
    ) -> Result<glide_rdma::RdmaSession, HandshakeFailure> {
        use crate::rdma::protocol;

        let reply = protocol::hello_command(rdma_fabric.local_address())
            .query_async(connection)
            .await
            .map_err(|error| {
                if error.is_server_reply() {
                    HandshakeFailure::Refused(crate::rdma::handshake_refused(node, error))
                } else {
                    // The connection failed, so the server may have opened a
                    // session before it could answer.
                    HandshakeFailure::Unsettled(error)
                }
            })?;
        // The server answered without an error, so it opened its side of the
        // session. Every failure from here on leaves the two sides out of step.
        let handshake = protocol::parse_hello(reply).map_err(|error| {
            HandshakeFailure::Unsettled(crate::rdma::malformed_handshake(node, error))
        })?;
        rdma_fabric.open_session(&handshake).map_err(|error| {
            HandshakeFailure::Unsettled(crate::rdma::session_not_opened(node, error))
        })
    }

    impl Deref for GlideConnectionWithRdma {
        type Target = MultiplexedConnection;

        fn deref(&self) -> &Self::Target {
            &self.inner
        }
    }

    impl DerefMut for GlideConnectionWithRdma {
        fn deref_mut(&mut self) -> &mut Self::Target {
            &mut self.inner
        }
    }

    impl ConnectionLike for GlideConnectionWithRdma {
        fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
            Box::pin(self.send_packed_command(cmd))
        }

        fn req_packed_commands<'a>(
            &'a mut self,
            cmd: &'a Pipeline,
            offset: usize,
            count: usize,
            pipeline_retry_strategy: Option<PipelineRetryStrategy>,
        ) -> RedisFuture<'a, Vec<Value>> {
            self.inner
                .req_packed_commands(cmd, offset, count, pipeline_retry_strategy)
        }

        fn get_db(&self) -> i64 {
            self.inner.get_db()
        }

        fn is_closed(&self) -> bool {
            self.inner.is_closed()
        }

        // The three below have defaults on the trait, and a wrapper that does not
        // delegate them silently answers for the connection it wraps rather than
        // from it: the default `get_az` reports no zone, which reads as "this node
        // is in no availability zone" and quietly disables affinity routing.
        fn get_az(&self) -> Option<String> {
            self.inner.get_az()
        }

        fn set_az(&mut self, az: Option<String>) {
            self.inner.set_az(az)
        }

        fn update_push_manager_node_address(&mut self, address: String) {
            self.inner.update_push_manager_node_address(address)
        }
    }

    impl Connect for GlideConnectionWithRdma {
        fn connect<'a, T>(
            info: T,
            response_timeout: Duration,
            connection_timeout: Duration,
            socket_addr: Option<SocketAddr>,
            glide_connection_options: GlideConnectionOptions,
        ) -> RedisFuture<'a, (Self, Option<IpAddr>)>
        where
            T: IntoConnectionInfo + Send + 'a,
        {
            Box::pin(async move {
                let connection_info = info.into_connection_info()?;
                let rdma_fabric = glide_connection_options.rdma_fabric.clone();
                // Taken before connecting, because the info is moved in.
                let address = connection_info.addr.clone();
                let (inner, ip) = MultiplexedConnection::connect(
                    connection_info,
                    response_timeout,
                    connection_timeout,
                    socket_addr,
                    glide_connection_options,
                )
                .await?;
                let node = address.to_string();
                Ok::<(Self, Option<IpAddr>), RedisError>((
                    Self::open(inner, rdma_fabric, &node),
                    ip,
                ))
            })
        }
    }
}
