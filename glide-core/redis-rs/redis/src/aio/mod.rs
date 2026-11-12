//! Adds async IO support to redis.
use crate::cmd::{cmd, Cmd};
use crate::connection::{get_resp3_hello_command_error, RedisConnectionInfo};
use crate::pipeline::PipelineRetryStrategy;
use crate::types::{
    ErrorKind, FromRedisValue, InfoDict, ProtocolVersion, RedisError, RedisFuture, RedisResult,
    Value,
};
use ::tokio::io::{AsyncRead, AsyncWrite};
use async_trait::async_trait;
use futures_util::Future;
use std::net::SocketAddr;
#[cfg(unix)]
use std::path::Path;
use std::pin::Pin;
use std::time::Duration;

use crate::tls::TlsConnParams;

/// Enables the tokio compatibility
#[cfg(feature = "tokio-comp")]
#[cfg_attr(docsrs, doc(cfg(feature = "tokio-comp")))]
pub mod tokio;

/// Represents the ability of connecting via TCP or via Unix socket
#[async_trait]
pub(crate) trait RedisRuntime: AsyncStream + Send + Sync + Sized + 'static {
    /// Performs a TCP connection
    async fn connect_tcp(socket_addr: SocketAddr, tcp_nodelay: bool) -> RedisResult<Self>;

    // Performs a TCP TLS connection
    async fn connect_tcp_tls(
        hostname: &str,
        socket_addr: SocketAddr,
        insecure: bool,
        tls_params: &Option<TlsConnParams>,
        tcp_nodelay: bool,
    ) -> RedisResult<Self>;

    /// Performs a UNIX connection
    #[cfg(unix)]
    async fn connect_unix(path: &Path) -> RedisResult<Self>;

    fn spawn(f: impl Future<Output = ()> + Send + 'static);

    fn boxed(self) -> Pin<Box<dyn AsyncStream + Send + Sync>> {
        Box::pin(self)
    }
}

/// Trait for objects that implements `AsyncRead` and `AsyncWrite`
pub trait AsyncStream: AsyncRead + AsyncWrite {}
impl<S> AsyncStream for S where S: AsyncRead + AsyncWrite {}

/// An async abstraction over connections.
pub trait ConnectionLike {
    /// Sends an already encoded (packed) command into the TCP socket and
    /// reads the single response from it.
    fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value>;

    /// Sends multiple already encoded (packed) command into the TCP socket
    /// and reads `count` responses from it.  This is used to implement
    /// pipelining.
    /// Important - this function is meant for internal usage, since it's
    /// easy to pass incorrect `offset` & `count` parameters, which might
    /// cause the connection to enter an erroneous state. Users shouldn't
    /// call it, instead using the Pipeline::query_async function.
    #[doc(hidden)]
    fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a crate::Pipeline,
        offset: usize,
        count: usize,
        pipeline_retry_strategy: Option<PipelineRetryStrategy>,
    ) -> RedisFuture<'a, Vec<Value>>;

    /// Returns the database this connection is bound to.  Note that this
    /// information might be unreliable because it's initially cached and
    /// also might be incorrect if the connection like object is not
    /// actually connected.
    fn get_db(&self) -> i64;

    /// Returns the state of the connection
    fn is_closed(&self) -> bool;

    /// Get the connection availibility zone
    fn get_az(&self) -> Option<String> {
        None
    }

    /// Set the connection availibility zone
    fn set_az(&mut self, _az: Option<String>) {}

    /// Update the node address used for PubSub tracking.
    /// Default implementation does nothing - only MultiplexedConnection implements this.
    fn update_push_manager_node_address(&mut self, _address: String) {
        // Default: no-op
    }
}

/// Implements ability to notify about disconnection events
#[async_trait]
pub trait DisconnectNotifier: Send + Sync {
    /// Notify about disconnect event
    fn notify_disconnect(&mut self);

    /// Wait for disconnect event with timeout
    async fn wait_for_disconnect_with_timeout(&self, max_wait: &Duration);

    /// Intended to be used with Box
    fn clone_box(&self) -> Box<dyn DisconnectNotifier>;
}

impl Clone for Box<dyn DisconnectNotifier> {
    fn clone(&self) -> Box<dyn DisconnectNotifier> {
        self.clone_box()
    }
}

// Helper function to extract and update availability zone from INFO command
async fn update_az_from_info<C>(con: &mut C) -> RedisResult<()>
where
    C: ConnectionLike,
{
    let info_res = con.req_packed_command(&cmd("INFO")).await;

    match info_res {
        Ok(value) => {
            let info_dict: InfoDict = FromRedisValue::from_redis_value(&value)?;
            if let Some(node_az) = info_dict.get::<String>("availability_zone") {
                con.set_az(Some(node_az));
            }
            Ok(())
        }
        Err(e) => {
            // Handle the error case for the INFO command
            Err(RedisError::from((
                ErrorKind::ResponseError,
                "Failed to execute INFO command. ",
                format!("{e:?}"),
            )))
        }
    }
}

/// Rejects a connection whose server-side tracking prefix differs from the prefix its cache
/// filters keys with.
///
/// The server only invalidates keys under `connection_info.tracking_prefix`, while the cache
/// only stores keys under its own prefix. If the two differ, the cache can hold keys the
/// server never invalidates, so the connection must not be set up at all.
fn check_tracking_prefix_matches_cache(connection_info: &RedisConnectionInfo) -> RedisResult<()> {
    let Some(cache) = &connection_info.cache else {
        return Ok(());
    };
    let cache_prefix = cache.core().tracking_prefix();
    let connection_prefix = connection_info.tracking_prefix.as_deref();
    if cache_prefix == connection_prefix {
        return Ok(());
    }
    Err(RedisError::from((
        ErrorKind::InvalidClientConfig,
        "Tracking prefix of the connection differs from the prefix of its client-side cache",
        // Prefix values are left out: they can reveal key or tenant naming.
        format!(
            "connection_prefix_set={}, cache_prefix_set={}",
            connection_prefix.is_some(),
            cache_prefix.is_some()
        ),
    )))
}

// Initial setup for every connection.
async fn setup_connection<C>(
    connection_info: &RedisConnectionInfo,
    con: &mut C,
    // This parameter is set to 'true' if ReadFromReplica strategy is set to AZAffinity, AZAffinityReplicasAndPrimary, or AZAffinityAllNodes.
    // An INFO command will be triggered in the connection's setup to update the 'availability_zone' property.
    discover_az: bool,
) -> RedisResult<()>
where
    C: ConnectionLike,
{
    if connection_info.protocol != ProtocolVersion::RESP2 {
        let hello_cmd = resp3_hello(connection_info);
        let val: RedisResult<Value> = hello_cmd.query_async(con).await;
        if let Err(err) = val {
            return Err(get_resp3_hello_command_error(err));
        }
    } else if let Some(password) = &connection_info.password {
        let mut command = cmd("AUTH");
        if let Some(username) = &connection_info.username {
            command.arg(username);
        }
        match command.arg(password).query_async(con).await {
            Ok(Value::Okay) => (),
            Err(e) => {
                let err_msg = e.detail().ok_or((
                    ErrorKind::AuthenticationFailed,
                    "Password authentication failed",
                ))?;

                if !err_msg.contains("wrong number of arguments for 'auth' command") {
                    fail!((
                        ErrorKind::AuthenticationFailed,
                        "Password authentication failed",
                    ));
                }

                let mut command = cmd("AUTH");
                match command.arg(password).query_async(con).await {
                    Ok(Value::Okay) => (),
                    _ => {
                        fail!((
                            ErrorKind::AuthenticationFailed,
                            "Password authentication failed"
                        ));
                    }
                }
            }
            _ => {
                fail!((
                    ErrorKind::AuthenticationFailed,
                    "Password authentication failed"
                ));
            }
        }
    }

    if connection_info.db != 0 {
        match cmd("SELECT").arg(connection_info.db).query_async(con).await {
            Ok(Value::Okay) => (),
            _ => fail!((
                ErrorKind::ResponseError,
                "Redis server refused to switch database"
            )),
        }
    }

    if let Some(client_name) = &connection_info.client_name {
        match cmd("CLIENT")
            .arg("SETNAME")
            .arg(client_name)
            .query_async(con)
            .await
        {
            Ok(Value::Okay) => {}
            _ => fail!((
                ErrorKind::ResponseError,
                "Redis server refused to set client name"
            )),
        }
    }

    if connection_info.server_assisted_cache {
        if connection_info.protocol == ProtocolVersion::RESP2 {
            return Err(RedisError::from((
                ErrorKind::InvalidClientConfig,
                "server_assisted_cache requires RESP3 protocol",
            )));
        }
        // Broadcasting mode: the server pushes invalidations for every modified key, or only
        // for keys matching `PREFIX` when one is configured. The prefix is re-registered on
        // every (re)connection because this setup runs for each new connection.
        check_tracking_prefix_matches_cache(connection_info)?;
        let mut tracking = cmd("CLIENT");
        tracking.arg("TRACKING").arg("ON").arg("BCAST");
        if let Some(prefix) = &connection_info.tracking_prefix {
            tracking.arg("PREFIX").arg(prefix.as_slice());
        }
        match tracking.query_async(con).await {
            Ok(Value::Okay) => {}
            Err(e) => {
                // Only the server error text: the configured prefix is left out because it
                // can reveal key or tenant naming.
                return Err(RedisError::from((
                    ErrorKind::ClientError,
                    "Failed to enable server-assisted client tracking",
                    e.to_string(),
                )));
            }
            other => {
                return Err(RedisError::from((
                    ErrorKind::ClientError,
                    "Unexpected response from CLIENT TRACKING ON BCAST",
                    format!("{other:?}"),
                )));
            }
        }
    }

    if discover_az {
        update_az_from_info(con).await?;
    }

    // result is ignored, as per the command's instructions.
    // https://redis.io/commands/client-setinfo/
    let _: RedisResult<()> = crate::connection::client_set_info_pipeline(
        connection_info.lib_name.as_deref(),
        connection_info.lib_ver.as_deref(),
    )
    .query_async(con)
    .await;
    Ok(())
}

mod connection;
pub use connection::*;
mod multiplexed_connection;
pub use multiplexed_connection::*;
#[cfg(feature = "connection-manager")]
mod connection_manager;
#[cfg(feature = "connection-manager")]
#[cfg_attr(docsrs, doc(cfg(feature = "connection-manager")))]
pub use connection_manager::*;
mod runtime;
use crate::commands::resp3_hello;
pub(super) use runtime::*;

#[cfg(test)]
mod tracking_prefix_tests {
    use super::{check_tracking_prefix_matches_cache, setup_connection, ConnectionLike};
    use crate::cache::glide_cache::CacheConfig;
    use crate::cache::lru_cache::new_lru_cache;
    use crate::cmd::Arg;
    use crate::connection::RedisConnectionInfo;
    use crate::types::{RedisError, RedisFuture, Value};
    use crate::ErrorKind;
    use crate::{Cmd, ProtocolVersion};

    /// Builds a connection info with `connection_prefix` and, when `cache_prefix` is `Some`, a
    /// cache created with that prefix.
    fn connection_info(
        cache_prefix: Option<Option<&[u8]>>,
        connection_prefix: Option<&[u8]>,
    ) -> RedisConnectionInfo {
        RedisConnectionInfo {
            cache: cache_prefix.map(|prefix| {
                new_lru_cache(CacheConfig {
                    max_memory_bytes: 1024,
                    ttl: None,
                    enable_metrics: false,
                    tracking_prefix: prefix.map(<[u8]>::to_vec),
                })
            }),
            server_assisted_cache: true,
            tracking_prefix: connection_prefix.map(<[u8]>::to_vec),
            ..Default::default()
        }
    }

    /// Equal prefixes, or a connection without a cache, pass the check.
    #[test]
    fn test_matching_prefixes_are_accepted() {
        for (cache, connection) in [
            (Some(None), None),
            (Some(Some(&b"xxx:"[..])), Some(&b"xxx:"[..])),
            // Tracking without a local cache has nothing to keep consistent.
            (None, None),
            (None, Some(&b"xxx:"[..])),
        ] {
            assert!(
                check_tracking_prefix_matches_cache(&connection_info(cache, connection)).is_ok(),
                "cache={cache:?}, connection={connection:?}"
            );
        }
    }

    /// Any difference between the connection prefix and the cache prefix is rejected, without
    /// prefix values in the error.
    #[test]
    fn test_mismatching_prefixes_are_rejected() {
        for (cache, connection) in [
            (Some(&b"xxx:"[..]), Some(&b"yyy:"[..])),
            // The server would invalidate only `xxx:` while the cache stores every key.
            (None, Some(&b"xxx:"[..])),
            // The server would invalidate every key; rejected as well to keep one rule.
            (Some(&b"xxx:"[..]), None),
        ] {
            let err =
                check_tracking_prefix_matches_cache(&connection_info(Some(cache), connection))
                    .unwrap_err();
            assert_eq!(
                err.kind(),
                ErrorKind::InvalidClientConfig,
                "cache={cache:?}, connection={connection:?}"
            );
            let message = err.to_string();
            assert!(
                !message.contains("xxx:") && !message.contains("yyy:"),
                "prefix values must not appear in the error: {message}"
            );
        }
    }

    /// Connection that accepts every setup command except `CLIENT TRACKING`, which it
    /// rejects with a fixed server error.
    struct TrackingRejectingConnection;

    impl ConnectionLike for TrackingRejectingConnection {
        fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
            let is_tracking = cmd
                .args_iter()
                .any(|arg| matches!(arg, Arg::Simple(bytes) if bytes == b"TRACKING"));
            Box::pin(async move {
                if is_tracking {
                    Err(RedisError::from((
                        ErrorKind::ResponseError,
                        "ERR",
                        "tracking rejected by server".to_string(),
                    )))
                } else {
                    Ok(Value::Okay)
                }
            })
        }

        fn req_packed_commands<'a>(
            &'a mut self,
            _cmd: &'a crate::Pipeline,
            _offset: usize,
            count: usize,
            _pipeline_retry_strategy: Option<crate::PipelineRetryStrategy>,
        ) -> RedisFuture<'a, Vec<Value>> {
            Box::pin(async move { Ok(vec![Value::Okay; count]) })
        }

        fn get_db(&self) -> i64 {
            0
        }

        fn is_closed(&self) -> bool {
            false
        }
    }

    /// A rejected `CLIENT TRACKING` keeps its error kind, description and the server error
    /// text, with or without a prefix, and never includes the configured prefix.
    #[tokio::test]
    async fn test_tracking_setup_error_omits_the_configured_prefix() {
        for prefix in [None, Some(&b"tenant-42:"[..])] {
            let info = RedisConnectionInfo {
                protocol: ProtocolVersion::RESP3,
                ..connection_info(None, prefix)
            };
            let err = setup_connection(&info, &mut TrackingRejectingConnection, false)
                .await
                .unwrap_err();

            assert_eq!(err.kind(), ErrorKind::ClientError, "prefix={prefix:?}");
            let message = err.to_string();
            assert!(
                message.contains("Failed to enable server-assisted client tracking"),
                "{message}"
            );
            assert!(
                message.contains("tracking rejected by server"),
                "server error text is kept: {message}"
            );
            assert!(
                !message.contains("tenant-42"),
                "the configured prefix must not appear in the error: {message}"
            );
        }
    }
}
