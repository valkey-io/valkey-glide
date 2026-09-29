// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pipeline dispatch and typed pipeline execution on async clients.

use crate::ValkeyFuture;
use crate::pipeline::Pipeline;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;

/// Runs a pipeline of commands on an async client.
/// Implemented by [`GlideClient`] and [`GlideClusterClient`].
#[doc(hidden)]
#[sealed::sealed(pub(crate))]
pub trait PipelineDispatch {
    #[doc(hidden)]
    fn glide_dispatch_pipeline<'a>(&self, pipeline: &'a Pipeline) -> ValkeyFuture<'a, ValkeyValue>;
}

/// Extension for running a [`Pipeline`] on a GLIDE async client
/// with typed decoding and **zero extra payload copies**.
///
/// ```rust,no_run
/// use glide::{PipelineExt, pipe};
/// # async fn demo(client: &glide::GlideClient) -> glide::ValkeyResult<()> {
/// let (a, b): (i64, i64) = pipe()
///     .atomic()
///     .incr("c", 1)
///     .incr("c", 1)
///     .query_async(client)
///     .await?;
/// # let _ = (a, b); Ok(()) }
/// ```
pub trait PipelineExt {
    /// Execute this pipeline on a GLIDE client and decode the replies into `T`.
    ///
    /// Errors abort with the first errored command's reply. Ignored commands are
    /// dropped before decoding, and an atomic pipeline's `EXEC` reply is unwrapped.
    ///
    /// Mirrors `redis-rs`'s `query_async`.
    fn query_async<'a, C: PipelineDispatch, T: FromValkeyValue + Send + 'a>(
        &'a self,
        con: &C,
    ) -> ValkeyFuture<'a, T>;
}

impl PipelineExt for Pipeline {
    fn query_async<'a, C: PipelineDispatch, T: FromValkeyValue + Send + 'a>(
        &'a self,
        con: &C,
    ) -> ValkeyFuture<'a, T> {
        let reply = con.glide_dispatch_pipeline(self);
        Box::pin(async move {
            let value = match reply.await? {
                ValkeyValue::Array(items) => {
                    let ignored = self.as_redis().ignored_commands();
                    ValkeyValue::Array(
                        items
                            .into_iter()
                            .enumerate()
                            // Filter out the ignored responses.
                            .filter_map(|(i, v)| (!ignored.contains(&i)).then_some(v))
                            .collect(),
                    )
                }
                ValkeyValue::Nil => ValkeyValue::Nil,
                other => unreachable!("unexpected pipeline reply from Valkey: {other:?}"),
            };
            T::from_owned_valkey_value(value)
        })
    }
}
