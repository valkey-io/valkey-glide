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
//! ordinary commands -- never sends `LO.HELLO` at all.

#[cfg(not(feature = "rdma"))]
pub type GlideConnectionWithRdma = redis::aio::MultiplexedConnection;

#[cfg(feature = "rdma")]
pub use with_rdma::GlideConnectionWithRdma;

#[cfg(feature = "rdma")]
mod with_rdma {
    use std::net::{IpAddr, SocketAddr};
    use std::ops::{Deref, DerefMut};
    use std::sync::Arc;
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
                .get_or_try_init(|| handshake(connection, &self.fabric, &self.node))
                .await?;
            Ok(())
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

    /// Run `LO.HELLO` on this connection and open the session it reports.
    /// Should be run only once per RESP connection.
    async fn handshake(
        connection: &mut MultiplexedConnection,
        rdma_fabric: &glide_rdma::RdmaFabric,
        node: &str,
    ) -> RedisResult<glide_rdma::RdmaSession> {
        use crate::rdma::protocol;

        let reply = protocol::hello_command(rdma_fabric.local_address())
            .query_async(connection)
            .await
            .map_err(|error| crate::rdma::handshake_refused(node, error))?;
        let handshake = protocol::parse_hello(reply)
            .map_err(|error| crate::rdma::malformed_handshake(node, error))?;
        rdma_fabric
            .open_session(&handshake)
            .map_err(protocol::as_redis_error)
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
