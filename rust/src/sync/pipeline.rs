// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pipeline dispatch and typed pipeline execution on sync clients.

use crate::ValkeyResult;
use crate::pipeline::Pipeline;
use crate::value::FromValkeyValue;

/// Runs a pipeline of commands on a sync client.
/// Implemented by [`SyncGlideClient`] and [`SyncGlideClusterClient`].
#[doc(hidden)]
#[sealed::sealed(pub(crate))]
pub trait SyncPipelineDispatch {
    fn glide_dispatch_pipeline<T: FromValkeyValue + Send>(
        &self,
        pipeline: &Pipeline,
    ) -> ValkeyResult<T>;
}

/// Extension for running a [`Pipeline`] on a GLIDE sync client with
/// with typed decoding and **zero extra payload copies**.
///
/// Like the rest of the sync layer, this blocks on the internal runtime and
/// therefore **must not be called from within an async context** (doing so
/// panics with tokio's "cannot block the current thread from within a runtime"
/// — use the async [`crate::PipelineExt::query_async`] there instead).
///
/// ```rust,no_run
/// use glide::sync::{PipelineExt, SyncGlideClient};
/// # fn demo(client: &SyncGlideClient) -> glide::ValkeyResult<()> {
/// let (a, b): (i64, i64) = glide::pipe()
///     .atomic()
///     .incr("c", 1)
///     .incr("c", 1)
///     .query(client)?;
/// # let _ = (a, b); Ok(()) }
/// ```
pub trait PipelineExt {
    /// Execute this pipeline on a blocking GLIDE client.
    ///
    /// Errors abort with the first errored command's reply. Ignored commands are
    /// dropped before decoding, and an atomic pipeline's `EXEC` reply is unwrapped.
    ///
    /// Mirrors `redis-rs`'s `query`.
    fn query<C: SyncPipelineDispatch, T: FromValkeyValue + Send>(&self, con: &C)
    -> ValkeyResult<T>;
}

impl PipelineExt for Pipeline {
    fn query<C: SyncPipelineDispatch, T: FromValkeyValue + Send>(
        &self,
        con: &C,
    ) -> ValkeyResult<T> {
        con.glide_dispatch_pipeline(self)
    }
}
