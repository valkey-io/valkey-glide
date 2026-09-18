// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//!
//! Typed [`Pipeline`] execution on the GLIDE clients: [`PipelineExt::query_async`].

use super::{GlideClient, GlideClusterClient};
use crate::ValkeyFuture;
use crate::pipeline::{Pipeline, dispatch_pipeline};
use crate::pipeline_options::PipelineOptions;
use crate::value::{FromValkeyValue, ValkeyValue};
use glide_core::client::Client as CoreClient;

mod sealed {
    pub trait Sealed {}
    impl Sealed for super::GlideClient {}
    impl Sealed for super::GlideClusterClient {}
}

/// An async GLIDE client that can run a [`Pipeline`].
/// Sealed — implemented only by [`GlideClient`] and [`GlideClusterClient`].
pub trait GlidePipelineTarget: sealed::Sealed {
    /// A cheap handle to the underlying core client (Arc inside).
    #[doc(hidden)]
    fn core_handle(&self) -> CoreClient;
}

impl GlidePipelineTarget for GlideClient {
    fn core_handle(&self) -> CoreClient {
        self.inner.clone()
    }
}

impl GlidePipelineTarget for GlideClusterClient {
    fn core_handle(&self) -> CoreClient {
        self.inner.clone()
    }
}

/// Extension for running a [`Pipeline`] on a GLIDE client
/// with typed decoding and **zero extra payload copies**.
///
/// Build with [`crate::pipe()`]; `.atomic()` pipelines run as a `MULTI`/`EXEC`
/// transaction; `.ignore()` markers are honored during decoding. For GLIDE
/// execution controls (per-call timeout, retry policy, cluster routing) use
/// `exec` on the client instead.
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
    /// Execute this pipeline on a GLIDE client and decode the kept replies into
    /// `T`. Errors abort with the first errored command's reply; `.ignore()`d
    /// commands are dropped before decoding, and an atomic pipeline's `EXEC`
    /// reply is unwrapped.
    ///
    /// Mirrors `redis-rs`'s `query_async`.
    fn query_async<'a, C: GlidePipelineTarget, T: FromValkeyValue + Send + 'a>(
        &'a self,
        con: &C,
    ) -> ValkeyFuture<'a, T>;
}

impl PipelineExt for Pipeline {
    fn query_async<'a, C: GlidePipelineTarget, T: FromValkeyValue + Send + 'a>(
        &'a self,
        con: &C,
    ) -> ValkeyFuture<'a, T> {
        let core = con.core_handle();
        Box::pin(async move {
            let opts = &PipelineOptions::default();
            let reply = dispatch_pipeline(&core, self, None, true, opts).await?;

            let value = match reply {
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
