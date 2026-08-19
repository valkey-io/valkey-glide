// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! The async GLIDE clients.
//!
//! [`GlideClient`] connects to a standalone deployment; [`GlideClusterClient`]
//! connects to a cluster. Both wrap the shared `glide_core::client::Client` and
//! implement [`CommandExecutor`], so all command family traits apply to them.

use crate::config::{GlideClientConfiguration, GlideClusterClientConfiguration};
use crate::error::{GlideError, Result};
use crate::executor::CommandExecutor;
use crate::pipeline_options::{PipelineOptions, run_pipeline};
use crate::routes::Route;
use async_trait::async_trait;
use bytes::Bytes;
use glide_core::client::Client as CoreClient;
use glide_core::cluster_scan_container::get_cluster_scan_cursor;
use redis::cluster_routing::RoutingInfo;
use redis::{ClusterScanArgs, Cmd, PushInfo, PushKind, ScanStateRC, Value};
use std::sync::Arc;
use tokio::sync::Mutex as AsyncMutex;
use tokio::sync::mpsc::{UnboundedReceiver, UnboundedSender};

/// The kind of a received Pub/Sub message.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PubSubMessageKind {
    /// A message on an exactly-subscribed channel (`SUBSCRIBE`).
    Message,
    /// A message on a pattern-subscribed channel (`PSUBSCRIBE`).
    PMessage,
    /// A message on a shard-subscribed channel (`SSUBSCRIBE`).
    SMessage,
}

/// A message received on a Pub/Sub subscription.
///
/// Mirrors Python's `CoreCommands.PubSubMsg`.
#[derive(Debug, Clone)]
pub struct PubSubMessage {
    /// The kind of subscription that produced the message.
    pub kind: PubSubMessageKind,
    /// The channel the message was published to.
    pub channel: Bytes,
    /// The message payload.
    pub payload: Bytes,
    /// The pattern that matched (only for [`PubSubMessageKind::PMessage`]).
    pub pattern: Option<Bytes>,
}

/// A shared receiver of Pub/Sub push messages from `glide-core`.
///
/// Semantics worth knowing:
/// - The channel is **unbounded**: a fast publisher with a slow/absent consumer
///   grows memory without bound, so callers should drain promptly.
/// - Access is guarded by a `tokio::Mutex`, making this a **single-consumer**
///   model — concurrent `get_pubsub_message` / `try_get_pubsub_message` callers
///   serialize on the lock (the async `Mutex` is held across `.await`, which is
///   correct for `tokio::Mutex`).
type PushRx = Arc<AsyncMutex<UnboundedReceiver<PushInfo>>>;

/// Build the optional push channel for a client that has subscriptions.
fn make_push_channel(
    has_subscriptions: bool,
) -> (Option<UnboundedSender<PushInfo>>, Option<PushRx>) {
    if has_subscriptions {
        let (tx, rx) = tokio::sync::mpsc::unbounded_channel();
        (Some(tx), Some(Arc::new(AsyncMutex::new(rx))))
    } else {
        (None, None)
    }
}

/// The client's push receiver, or the error both clients report when Pub/Sub
/// was not configured.
fn pubsub_rx(rx: &Option<PushRx>) -> Result<&PushRx> {
    rx.as_ref()
        .ok_or_else(|| GlideError::Request("client has no configured pub/sub subscriptions".into()))
}

/// Shared implementation of `get_pubsub_message`: wait for the next Pub/Sub
/// *message* push, skipping non-message pushes (subscribe/unsubscribe
/// confirmations, invalidations, disconnects).
async fn recv_pubsub_message(rx: &Option<PushRx>) -> Result<PubSubMessage> {
    let mut guard = pubsub_rx(rx)?.lock().await;
    loop {
        match guard.recv().await {
            Some(push) => {
                if let Some(msg) = push_to_message(push) {
                    return Ok(msg);
                }
            }
            None => return Err(GlideError::Request("pub/sub channel closed".into())),
        }
    }
}

/// Shared implementation of `try_get_pubsub_message`: non-blocking variant of
/// [`recv_pubsub_message`]; returns `None` when no message is available.
async fn try_recv_pubsub_message(rx: &Option<PushRx>) -> Result<Option<PubSubMessage>> {
    let mut guard = pubsub_rx(rx)?.lock().await;
    loop {
        match guard.try_recv() {
            Ok(push) => {
                if let Some(msg) = push_to_message(push) {
                    return Ok(Some(msg));
                }
            }
            Err(tokio::sync::mpsc::error::TryRecvError::Empty) => return Ok(None),
            Err(tokio::sync::mpsc::error::TryRecvError::Disconnected) => {
                return Err(GlideError::Request("pub/sub channel closed".into()));
            }
        }
    }
}

/// Convert a raw `PushInfo` into a [`PubSubMessage`], returning `None` for
/// non-message push kinds (subscribe/unsubscribe confirmations, invalidations,
/// disconnects).
fn push_to_message(push: PushInfo) -> Option<PubSubMessage> {
    let (kind, has_pattern) = match push.kind {
        PushKind::Message => (PubSubMessageKind::Message, false),
        PushKind::SMessage => (PubSubMessageKind::SMessage, false),
        PushKind::PMessage => (PubSubMessageKind::PMessage, true),
        _ => return None,
    };
    let mut data = push.data.into_iter();
    if has_pattern {
        let pattern = value_to_bytes(data.next()?);
        let channel = value_to_bytes(data.next()?);
        let payload = value_to_bytes(data.next()?);
        Some(PubSubMessage {
            kind,
            channel,
            payload,
            pattern: Some(pattern),
        })
    } else {
        let channel = value_to_bytes(data.next()?);
        let payload = value_to_bytes(data.next()?);
        Some(PubSubMessage {
            kind,
            channel,
            payload,
            pattern: None,
        })
    }
}

fn value_to_bytes(v: Value) -> Bytes {
    match v {
        Value::BulkString(b) => Bytes::from(b),
        Value::SimpleString(s) => Bytes::from(s.into_bytes()),
        other => Bytes::from(format!("{other:?}").into_bytes()),
    }
}

/// A cursor for an in-progress cluster `SCAN`.
///
/// Start a new scan with [`ClusterScanCursor::new`]. After each
/// [`GlideClusterClient::cluster_scan`] call, use the returned cursor for the
/// next iteration until [`ClusterScanCursor::is_finished`] returns `true`.
///
/// Mirrors Python's `ClusterScanCursor`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ClusterScanCursor(String);

impl ClusterScanCursor {
    /// The sentinel returned by the core when a scan has completed.
    const FINISHED: &'static str = "finished";

    /// Create a fresh cursor to begin a new cluster scan.
    pub fn new() -> Self {
        ClusterScanCursor(String::new())
    }

    /// Create a cursor from a previously-returned cursor id.
    pub fn from_id(id: impl Into<String>) -> Self {
        ClusterScanCursor(id.into())
    }

    /// The underlying cursor id.
    pub fn id(&self) -> &str {
        &self.0
    }

    /// Whether the scan has completed (no more keys to return).
    pub fn is_finished(&self) -> bool {
        self.0 == Self::FINISHED
    }
}

impl Default for ClusterScanCursor {
    fn default() -> Self {
        Self::new()
    }
}

/// An async client for a **standalone** Valkey/Redis deployment.
///
/// Mirrors Python `GlideClient`. Cheaply cloneable — clones share the same
/// underlying connection pool.
#[derive(Clone)]
pub struct GlideClient {
    inner: CoreClient,
    pubsub_rx: Option<PushRx>,
    db: i64,
}

impl GlideClient {
    /// Connect using the given standalone configuration.
    pub async fn connect(config: GlideClientConfiguration) -> Result<Self> {
        let db = config.database_id;
        let request = config.to_request();
        let has_subs = config
            .pubsub_subscriptions
            .as_ref()
            .is_some_and(|s| !s.is_empty());
        let (sender, pubsub_rx) = make_push_channel(has_subs || config.force_pubsub_channel);
        let inner = CoreClient::new(request, sender)
            .await
            .map_err(GlideError::from)?;
        Ok(GlideClient {
            inner,
            pubsub_rx,
            db,
        })
    }

    /// Access the underlying `glide-core` client (advanced use).
    pub fn core(&self) -> &CoreClient {
        &self.inner
    }

    /// The configured logical database index (crate-internal; reported to
    /// the pipeline adapter in `client/connection.rs`).
    pub(crate) fn db(&self) -> i64 {
        self.db
    }

    /// Wait for the next Pub/Sub message on this client's configured
    /// subscriptions (`get_pubsub_message`). Subscribe/unsubscribe confirmations
    /// and other non-message pushes are skipped.
    ///
    /// Returns an error if the client was not configured with subscriptions.
    pub async fn get_pubsub_message(&self) -> Result<PubSubMessage> {
        recv_pubsub_message(&self.pubsub_rx).await
    }

    /// Try to get the next Pub/Sub message without blocking
    /// (`try_get_pubsub_message`). Returns `None` if no message is currently
    /// available.
    pub async fn try_get_pubsub_message(&self) -> Result<Option<PubSubMessage>> {
        try_recv_pubsub_message(&self.pubsub_rx).await
    }

    /// Execute a [`redis::Pipeline`] with GLIDE execution options
    /// (per-call timeout, pipeline retry policy) and return the raw per-command
    /// replies. Build with [`crate::pipe()`]; `.atomic()` pipelines run as a
    /// `MULTI`/`EXEC` transaction. For plain typed execution prefer
    /// [`PipelineExt::query_glide`]. When `raise_on_error` is `true`, the
    /// first errored command aborts with an error; otherwise error replies are
    /// returned inline.
    pub async fn execute_pipeline(
        &self,
        pipeline: &redis::Pipeline,
        raise_on_error: bool,
        options: &PipelineOptions,
    ) -> Result<Vec<Value>> {
        run_pipeline(&self.inner, pipeline, None, raise_on_error, options).await
    }

    /// Update the password used by this client to authenticate with the server,
    /// without changing the server-side password (`update_connection_password`).
    ///
    /// The new `password` is stored and used for all future (re)connections. Pass
    /// `None` to clear a previously-set password (revert to no authentication).
    ///
    /// When `immediate_auth` is `true`, an `AUTH` is issued on the live
    /// connection right away so the change takes effect without waiting for a
    /// reconnect; the call errors if that `AUTH` is rejected. When `false`, the
    /// password is only applied on the next reconnection.
    ///
    /// Mirrors Python's `update_connection_password`.
    pub async fn update_connection_password(
        &self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> Result<()> {
        // `Client` is Clone (Arc inside) and the core method needs `&mut self`,
        // so we operate on a cheap clone — same seam as `execute_command`.
        let mut client = self.inner.clone();
        client
            .update_connection_password(password, immediate_auth)
            .await
            .map_err(GlideError::from)?;
        Ok(())
    }
}

#[async_trait]
impl CommandExecutor for GlideClient {
    async fn execute_command(&self, mut cmd: Cmd, routing: Option<RoutingInfo>) -> Result<Value> {
        // `Client` is Clone (Arc inside) and `send_command` needs `&mut self`,
        // so we operate on a cheap clone — exactly what every wrapper does.
        let mut client = self.inner.clone();
        client
            .send_command(&mut cmd, routing)
            .await
            .map_err(GlideError::from)
    }
}

/// An async client for a **cluster** Valkey/Redis deployment.
///
/// Mirrors Python `GlideClusterClient`. Commands are routed automatically by the
/// core based on their keys; use the `*_with_route` helpers (or
/// [`crate::CustomCommand::custom_command_with_route`]) to override routing.
#[derive(Clone)]
pub struct GlideClusterClient {
    inner: CoreClient,
    pubsub_rx: Option<PushRx>,
}

impl GlideClusterClient {
    /// Connect using the given cluster configuration.
    pub async fn connect(config: GlideClusterClientConfiguration) -> Result<Self> {
        let request = config.to_request();
        let has_subs = config
            .pubsub_subscriptions
            .as_ref()
            .is_some_and(|s| !s.is_empty());
        let (sender, pubsub_rx) = make_push_channel(has_subs || config.force_pubsub_channel);
        let inner = CoreClient::new(request, sender)
            .await
            .map_err(GlideError::from)?;
        Ok(GlideClusterClient { inner, pubsub_rx })
    }

    /// Access the underlying `glide-core` client (advanced use).
    pub fn core(&self) -> &CoreClient {
        &self.inner
    }

    /// Wait for the next Pub/Sub message (including shard messages) on this
    /// client's configured subscriptions.
    pub async fn get_pubsub_message(&self) -> Result<PubSubMessage> {
        recv_pubsub_message(&self.pubsub_rx).await
    }

    /// Try to get the next Pub/Sub message without blocking. Returns `None` if
    /// no message is currently available.
    pub async fn try_get_pubsub_message(&self) -> Result<Option<PubSubMessage>> {
        try_recv_pubsub_message(&self.pubsub_rx).await
    }

    /// Execute a raw command with an explicit route.
    pub async fn route_command(&self, mut cmd: Cmd, route: Route) -> Result<Value> {
        let routing = route.to_routing_info(Some(&cmd));
        let mut client = self.inner.clone();
        client
            .send_command(&mut cmd, Some(routing))
            .await
            .map_err(GlideError::from)
    }

    /// Execute a [`redis::Pipeline`] with GLIDE execution options,
    /// optionally routed. See [`crate::GlideClient::execute_pipeline`].
    pub async fn execute_pipeline(
        &self,
        pipeline: &redis::Pipeline,
        raise_on_error: bool,
        route: Option<Route>,
        options: &PipelineOptions,
    ) -> Result<Vec<Value>> {
        let routing = route.map(|r| r.to_routing_info(None));
        run_pipeline(&self.inner, pipeline, routing, raise_on_error, options).await
    }

    /// Update the password used by this client to authenticate with the cluster,
    /// without changing the server-side password (`update_connection_password`).
    ///
    /// The new `password` is stored and used for all future (re)connections to
    /// every node. Pass `None` to clear a previously-set password. When
    /// `immediate_auth` is `true`, an `AUTH` is issued right away and the call
    /// errors if rejected; when `false`, it applies on the next reconnection.
    ///
    /// Mirrors Python's `update_connection_password`.
    pub async fn update_connection_password(
        &self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> Result<()> {
        let mut client = self.inner.clone();
        client
            .update_connection_password(password, immediate_auth)
            .await
            .map_err(GlideError::from)?;
        Ok(())
    }

    /// Incrementally iterate the entire keyspace of a cluster (`SCAN` for
    /// cluster). Returns the next [`ClusterScanCursor`] and the batch of keys
    /// found. Iteration is complete when the returned cursor's
    /// [`ClusterScanCursor::is_finished`] is `true`.
    ///
    /// Unlike standalone `SCAN`, the cluster scan is coordinated by `glide-core`
    /// across all shards using an opaque cursor.
    pub async fn cluster_scan(
        &self,
        cursor: &ClusterScanCursor,
        match_pattern: Option<&[u8]>,
        count: Option<u32>,
        object_type: Option<crate::commands::options::ObjectType>,
    ) -> Result<(ClusterScanCursor, Vec<Bytes>)> {
        let scan_state = if cursor.0.is_empty() || cursor.0 == "0" {
            ScanStateRC::new()
        } else {
            get_cluster_scan_cursor(cursor.0.clone()).map_err(GlideError::from)?
        };

        let mut builder = ClusterScanArgs::builder();
        if let Some(p) = match_pattern {
            builder = builder.with_match_pattern(p.to_vec());
        }
        if let Some(c) = count {
            builder = builder.with_count(c);
        }
        if let Some(t) = object_type {
            builder = builder.with_object_type(t.to_redis());
        }
        let args = builder.build();

        let mut client = self.inner.clone();
        let reply = client
            .cluster_scan(&scan_state, args)
            .await
            .map_err(GlideError::from)?;

        // Reply shape: [cursor_id_or_"finished", [keys...]].
        let items = match reply {
            Value::Array(items) => items,
            other => {
                return Err(GlideError::Request(format!(
                    "unexpected cluster scan reply: {other:?}"
                )));
            }
        };
        let [cursor_val, keys_val] = <[Value; 2]>::try_from(items).map_err(|items| {
            GlideError::Request(format!("unexpected cluster scan reply arity: {items:?}"))
        })?;
        let next = ClusterScanCursor(crate::value::to_string(cursor_val)?);
        let keys = match keys_val {
            Value::Array(elems) => elems
                .into_iter()
                .map(crate::value::to_bytes)
                .collect::<Result<Vec<_>>>()?,
            Value::Nil => Vec::new(),
            other => vec![crate::value::to_bytes(other)?],
        };
        Ok((next, keys))
    }
}

#[async_trait]
impl CommandExecutor for GlideClusterClient {
    async fn execute_command(&self, mut cmd: Cmd, routing: Option<RoutingInfo>) -> Result<Value> {
        let mut client = self.inner.clone();
        client
            .send_command(&mut cmd, routing)
            .await
            .map_err(GlideError::from)
    }
}

mod connection;

pub use connection::{GlidePipelineTarget, PipelineExt};

// ---- unified command API dispatch ---------------------------------------------
//
// `glide::AsyncCommands` methods build the `Cmd` themselves and hand it here
// **by value**: one copy to build, glide-core's internal owned copy, nothing
// else — this is the client's primary command path.

impl crate::commands::core::AsyncCommands for GlideClient {
    fn glide_send_owned<'a>(&'a self, mut cmd: Cmd) -> redis::RedisFuture<'a, Value> {
        // `Client` is Clone (Arc inside); operate on a cheap clone so the
        // unified API can take `&self` — same pattern as `execute_command`.
        let mut client = self.inner.clone();
        Box::pin(async move { client.send_command(&mut cmd, None).await })
    }
}

impl crate::commands::core::AsyncCommands for GlideClusterClient {
    fn glide_send_owned<'a>(&'a self, mut cmd: Cmd) -> redis::RedisFuture<'a, Value> {
        // Routing is decided by glide-core from the command's keys.
        let mut client = self.inner.clone();
        Box::pin(async move { client.send_command(&mut cmd, None).await })
    }
}
