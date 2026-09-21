// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's owned pipeline / transaction builder.
//!
//! A [`Pipeline`] batches commands into a single round-trip.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::error::GlideError;
use crate::pipeline_options::PipelineOptions;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use glide_core::client::Client as CoreClient;
use redis::PipelineRetryStrategy;
use redis::cluster_routing::RoutingInfo;

/// Create an empty pipeline.
///
/// Mirrors redis-rs's `pipe`.
pub fn pipe() -> Pipeline {
    Pipeline::new()
}

/// A pipeline that batches commands into a single round-trip.
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
///
/// Mirrors redis-rs's `Pipeline`.
#[derive(Clone, Debug, Default)]
pub struct Pipeline {
    inner: redis::Pipeline,
}

impl Pipeline {
    /// Create an empty pipeline.
    pub fn new() -> Self {
        Pipeline {
            inner: redis::Pipeline::new(),
        }
    }

    /// Enable atomic mode: the whole pipeline
    /// runs as a `MULTI`/`EXEC` transaction.
    pub fn atomic(&mut self) -> &mut Self {
        self.inner.atomic();
        self
    }

    /// Start a new command with the given keyword.
    pub fn cmd(&mut self, name: &str) -> &mut Self {
        self.inner.cmd(name);
        self
    }

    /// Append argument(s) to the last started command.
    pub fn arg<A: ToValkeyArgs>(&mut self, arg: A) -> &mut Self {
        self.inner.arg(arg.to_valkey_args());
        self
    }

    /// Ignore the last command's reply: it is still checked
    /// for errors, but dropped from the typed response.
    pub fn ignore(&mut self) -> &mut Self {
        self.inner.ignore();
        self
    }

    /// Push a fully-built [`Cmd`].
    pub(crate) fn add_command(&mut self, cmd: Cmd) -> &mut Self {
        self.inner.add_command(cmd.into_redis());
        self
    }

    /// Borrow the underlying `redis::Pipeline` (feeds glide-core dispatch, and
    /// exposes `ignored_commands()` for the typed-decode reply filtering).
    pub(crate) fn as_redis(&self) -> &redis::Pipeline {
        &self.inner
    }
}

/// Dispatches a [`Pipeline`] and returns the reply as a [`ValkeyValue`].
pub(crate) async fn dispatch_pipeline(
    core: &CoreClient,
    pipeline: &Pipeline,
    routing: Option<RoutingInfo>,
    raise_on_error: bool,
    options: &PipelineOptions,
) -> ValkeyResult<ValkeyValue> {
    let redis_pipeline = pipeline.as_redis();

    // Return empty array directly for an empty pipeline.
    if redis_pipeline.is_empty() {
        return Ok(ValkeyValue::Array(Vec::new()));
    }

    let timeout = options.timeout_millis();
    let mut client = core.clone();

    let reply = if redis_pipeline.is_atomic() {
        client
            .send_transaction(redis_pipeline, routing, timeout, raise_on_error)
            .await
    } else {
        let retry_strategy = PipelineRetryStrategy {
            retry_server_error: options.retry_server_error,
            retry_connection_error: options.retry_connection_error,
        };
        client
            .send_pipeline(
                redis_pipeline,
                routing,
                raise_on_error,
                timeout,
                retry_strategy,
            )
            .await
    };

    ValkeyValue::from_redis(reply.map_err(GlideError::from_redis_error)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn get_packed_pipeline_matches_redis() {
        for atomic in [false, true] {
            let mut glide_pipe = pipe();
            let mut redis_pipe = redis::pipe();

            if atomic {
                glide_pipe.atomic();
                redis_pipe.atomic();
            }

            // Typed commands.
            glide_pipe
                .set("k", "v")
                .incr("k", 1)
                .ignore()
                .get("k")
                .rpush("l", &["a", "b"][..]);
            redis_pipe
                .set("k", "v")
                .incr("k", 1)
                .ignore()
                .get("k")
                .rpush("l", &["a", "b"][..]);

            // Untyped command.
            glide_pipe.cmd("APPEND").arg("k").arg("x");
            redis_pipe.cmd("APPEND").arg("k").arg("x");

            assert_eq!(
                glide_pipe.as_redis().get_packed_pipeline(),
                redis_pipe.get_packed_pipeline(),
                "glide pipeline encoding diverged from redis (atomic = {atomic})",
            );
        }
    }
}
