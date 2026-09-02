// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE execution options for [`Pipeline`]s.
//!
//! Pipelines are used directly: build with
//! [`crate::pipe()`] (add `.atomic()` for a `MULTI`/`EXEC` transaction) and
//! run with [`crate::PipelineExt::query_glide`] (async) / the sync
//! [`crate::sync::PipelineExt::query_glide`]. When GLIDE-specific execution
//! controls are needed (per-call timeout, pipeline retry policy, cluster
//! routing), use [`crate::GlideClient::execute_pipeline`] /
//! [`crate::GlideClusterClient::execute_pipeline`] with [`PipelineOptions`].

use crate::error::{GlideError, Result};
use glide_core::client::Client as CoreClient;
use redis::cluster_routing::RoutingInfo;
use redis::{Pipeline, PipelineRetryStrategy, Value};
use std::time::Duration;

/// Execution options for [`crate::GlideClient::execute_pipeline`].
///
/// The `retry_*` flags apply only to **non-atomic pipelines**; they are
/// ignored for atomic transactions (a `MULTI`/`EXEC` is never partially
/// retried).
///
/// Retrying is only safe for idempotent commands — enabling it for a pipeline
/// that contains non-idempotent commands (e.g. `INCR`, `LPUSH`) may apply them
/// more than once on a reconnect. Both flags default to `false`.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct PipelineOptions {
    /// Per-call timeout. `None` uses the client's configured request timeout.
    pub timeout: Option<Duration>,
    /// Retry the pipeline if the server returns a retryable error (pipeline only).
    pub retry_server_error: bool,
    /// Retry the pipeline on a connection error (pipeline only).
    pub retry_connection_error: bool,
}

impl PipelineOptions {
    /// Options with no timeout override and retries disabled (the defaults).
    pub fn new() -> Self {
        PipelineOptions::default()
    }

    /// Set a per-call timeout. Builder form.
    #[must_use]
    pub fn with_timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    /// Enable/disable retrying the pipeline on a retryable server error
    /// (pipeline only). Builder form.
    #[must_use]
    pub fn with_retry_server_error(mut self, retry: bool) -> Self {
        self.retry_server_error = retry;
        self
    }

    /// Enable/disable retrying the pipeline on a connection error (pipeline
    /// only). Builder form.
    #[must_use]
    pub fn with_retry_connection_error(mut self, retry: bool) -> Self {
        self.retry_connection_error = retry;
        self
    }

    /// Timeout as whole milliseconds, saturating at `u32::MAX` (~49.7 days)
    /// instead of narrowing/overflowing.
    fn timeout_millis(&self) -> Option<u32> {
        self.timeout
            .map(|d| u32::try_from(d.as_millis()).unwrap_or(u32::MAX))
    }
}

/// Execute a [`Pipeline`] against a core client with GLIDE options
/// and normalize the reply to a `Vec` of per-command values (atomic
/// transactions: the unwrapped `EXEC` reply).
pub(crate) async fn run_pipeline(
    core: &CoreClient,
    pipeline: &Pipeline,
    routing: Option<RoutingInfo>,
    raise_on_error: bool,
    options: &PipelineOptions,
) -> Result<Vec<Value>> {
    if pipeline.is_empty() {
        return Ok(Vec::new());
    }
    let timeout = options.timeout_millis();
    let mut client = core.clone();
    let value = if pipeline.is_atomic() {
        client
            .send_transaction(pipeline, routing, timeout, raise_on_error)
            .await
            .map_err(GlideError::from)?
    } else {
        client
            .send_pipeline(
                pipeline,
                routing,
                raise_on_error,
                timeout,
                PipelineRetryStrategy {
                    retry_server_error: options.retry_server_error,
                    retry_connection_error: options.retry_connection_error,
                },
            )
            .await
            .map_err(GlideError::from)?
    };
    match value {
        Value::Array(items) => Ok(items),
        Value::Nil => Ok(Vec::new()),
        other => Ok(vec![other]),
    }
}
