// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Typed [`redis::Pipeline`] execution on the GLIDE clients:
//! [`PipelineExt::query_glide`].
//!
//! The clients are deliberately **not** `redis` connection objects: the
//! connection-object traits (`ConnectionLike`) hand commands over by
//! reference, forcing a full payload copy per command to bridge into
//! glide-core's owned dispatch. GLIDE trades that piece of redis-rs API
//! parity for performance — single commands go through the unified command
//! API's owned-send path, and pipelines go through this extension, which
//! passes the built [`redis::Pipeline`] to glide-core **by reference**
//! (zero payload copies) while reusing the `redis` crate's own typed
//! decoding (`.ignore()` markers, transaction unwrapping).

use super::{GlideClient, GlideClusterClient};
use glide_core::client::Client as CoreClient;
use redis::{Cmd, FromRedisValue, RedisFuture, Value};

/// Dispatch a [`redis::Pipeline`] through glide-core, matching the reply shape
/// the `redis` crate's typed pipeline decoding expects: one reply per command
/// for pipelines, and the single `EXEC` reply for atomic transactions.
async fn dispatch_pipeline(
    core: &mut CoreClient,
    pipeline: &redis::Pipeline,
    retry: Option<redis::PipelineRetryStrategy>,
) -> redis::RedisResult<Vec<Value>> {
    if pipeline.is_atomic() {
        let value = core.send_transaction(pipeline, None, None, true).await?;
        Ok(vec![value])
    } else {
        let value = core
            .send_pipeline(pipeline, None, true, None, retry.unwrap_or_default())
            .await?;
        match value {
            Value::Array(items) => Ok(items),
            // glide-core contracts an array of per-command replies for
            // pipelines; anything else means the contract was violated —
            // fail loudly rather than let the caller decode garbage.
            other => Err(redis::RedisError::from((
                redis::ErrorKind::ResponseError,
                "unexpected non-array pipeline reply from glide-core",
                format!("{other:?}"),
            ))),
        }
    }
}

/// Crate-internal adapter that lets the `redis` crate's typed pipeline
/// decoding (`Pipeline::query_async`: `.ignore()` handling, transaction
/// unwrapping) run against glide-core. Only the **by-reference pipeline**
/// entry point is functional — the single-command entry point would require
/// a payload copy and is unreachable from `query_glide`, so it fails loudly.
struct PipelineConn {
    core: CoreClient,
    db: i64,
}

impl redis::aio::ConnectionLike for PipelineConn {
    fn req_packed_command<'a>(&'a mut self, _cmd: &'a Cmd) -> RedisFuture<'a, Value> {
        Box::pin(async {
            Err(redis::RedisError::from((
                redis::ErrorKind::ClientError,
                "single-command dispatch is not supported on the pipeline adapter; \
                 use the unified command API",
            )))
        })
    }

    fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a redis::Pipeline,
        // `offset`/`count` describe a packed-bytes reply layout; glide-core
        // already returns exactly the contracted shape (one reply per
        // command, or the single EXEC reply), so they are not needed here.
        _offset: usize,
        _count: usize,
        pipeline_retry_strategy: Option<redis::PipelineRetryStrategy>,
    ) -> RedisFuture<'a, Vec<Value>> {
        Box::pin(dispatch_pipeline(
            &mut self.core,
            cmd,
            pipeline_retry_strategy,
        ))
    }

    fn get_db(&self) -> i64 {
        self.db
    }

    fn is_closed(&self) -> bool {
        // glide-core owns reconnection; the client is never observably
        // "closed", matching a managed (auto-reconnecting) connection.
        false
    }
}

mod sealed {
    pub trait Sealed {}
    impl Sealed for super::GlideClient {}
    impl Sealed for super::GlideClusterClient {}
}

/// An async GLIDE client that can run a [`redis::Pipeline`] with zero extra
/// payload copies. Sealed — implemented only by [`GlideClient`] and
/// [`GlideClusterClient`].
pub trait GlidePipelineTarget: sealed::Sealed + Sync {
    /// A cheap handle to the underlying core client (Arc inside).
    #[doc(hidden)]
    fn core_handle(&self) -> CoreClient;
    /// The configured logical database index (reported to the decoder).
    #[doc(hidden)]
    fn db_index(&self) -> i64;
}

impl GlidePipelineTarget for GlideClient {
    fn core_handle(&self) -> CoreClient {
        self.inner.clone()
    }
    fn db_index(&self) -> i64 {
        self.db()
    }
}

impl GlidePipelineTarget for GlideClusterClient {
    fn core_handle(&self) -> CoreClient {
        self.inner.clone()
    }
    fn db_index(&self) -> i64 {
        0 // Cluster deployments always use database 0.
    }
}

/// Extension for running a [`redis::Pipeline`] on a GLIDE client with typed
/// decoding and **zero extra payload copies** (the pipeline is handed to
/// glide-core by reference).
///
/// Build with [`crate::pipe()`]; `.atomic()` pipelines run as a
/// `MULTI`/`EXEC` transaction; `.ignore()` markers are honored during
/// decoding. For GLIDE execution controls (per-call timeout, retry policy,
/// cluster routing) use `execute_pipeline` on the client instead.
///
/// ```rust,no_run
/// use glide::{PipelineExt, pipe};
/// # async fn demo(client: &glide::GlideClient) -> glide::RedisResult<()> {
/// let (a, b): (i64, i64) = pipe()
///     .atomic()
///     .incr("c", 1)
///     .incr("c", 1)
///     .query_glide(client)
///     .await?;
/// # let _ = (a, b); Ok(()) }
/// ```
pub trait PipelineExt {
    /// Execute this pipeline on a GLIDE client and decode the replies into
    /// `T`, with the same `.ignore()`/transaction semantics as the `redis`
    /// crate's typed pipeline execution.
    fn query_glide<'a, C: GlidePipelineTarget, T: FromRedisValue + Send + 'a>(
        &'a self,
        con: &'a C,
    ) -> RedisFuture<'a, T>;
}

impl PipelineExt for redis::Pipeline {
    fn query_glide<'a, C: GlidePipelineTarget, T: FromRedisValue + Send + 'a>(
        &'a self,
        con: &'a C,
    ) -> RedisFuture<'a, T> {
        let mut conn = PipelineConn {
            core: con.core_handle(),
            db: con.db_index(),
        };
        Box::pin(async move { self.query_async(&mut conn).await })
    }
}
