// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Blocking (synchronous) clients.
//!
//! Mirrors Python `glide-sync`. [`SyncGlideClient`] / [`SyncGlideClusterClient`]
//! wrap the async clients and drive them on a shared, process-wide Tokio runtime.
//!
//! Every async command is reachable from sync code via [`SyncGlideClient::run`]
//! (and the cluster equivalent), and the most common commands also have direct
//! blocking methods.

use crate::client::{GlideClient, GlideClusterClient};
use crate::commands::prelude::*;
use crate::config::{GlideClientConfiguration, GlideClusterClientConfiguration};
use crate::error::Result;
use crate::executor::CustomCommand;
use crate::pipeline_options::PipelineOptions;
use crate::routes::Route;
use redis::{ToRedisArgs, Value};
use std::future::Future;
use std::sync::OnceLock;
use tokio::runtime::{Builder, Runtime};

fn runtime() -> &'static Runtime {
    static RUNTIME: OnceLock<Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        Builder::new_multi_thread()
            .enable_all()
            .thread_name("glide-sync")
            .build()
            .expect("failed to build the shared GLIDE sync runtime")
    })
}

/// Block on an arbitrary future using the shared runtime.
pub fn block_on<F: Future>(future: F) -> F::Output {
    runtime().block_on(future)
}

/// A blocking client for a **standalone** deployment.
#[derive(Clone)]
pub struct SyncGlideClient {
    inner: GlideClient,
}

impl SyncGlideClient {
    /// Connect using the given standalone configuration (blocking).
    pub fn connect(config: GlideClientConfiguration) -> Result<Self> {
        let inner = runtime().block_on(GlideClient::connect(config))?;
        Ok(SyncGlideClient { inner })
    }

    /// The underlying async client.
    pub fn async_client(&self) -> &GlideClient {
        &self.inner
    }

    /// Run an arbitrary async operation against the client, blocking until it
    /// completes. This unlocks the *entire* async command surface from sync code:
    ///
    /// ```rust,no_run
    /// # use glide::sync::SyncGlideClient;
    /// # use glide::{AsyncCommands, GlideClientConfiguration};
    /// # fn demo(client: SyncGlideClient) -> glide::RedisResult<()> {
    /// let value: Option<String> = client.run(|c| async move { c.get("key").await })?;
    /// # let _ = value; Ok(()) }
    /// ```
    pub fn run<F, Fut, T>(&self, f: F) -> T
    where
        F: FnOnce(GlideClient) -> Fut,
        Fut: Future<Output = T>,
    {
        runtime().block_on(f(self.inner.clone()))
    }

    /// Update the connection password (blocking). See
    /// [`GlideClient::update_connection_password`].
    pub fn update_connection_password(
        &self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> Result<()> {
        runtime().block_on(
            self.inner
                .update_connection_password(password, immediate_auth),
        )
    }

    /// Run an arbitrary command (blocking escape hatch).
    pub fn custom_command<A: ToRedisArgs + Sync>(&self, args: &[A]) -> Result<Value> {
        runtime().block_on(self.inner.custom_command(args))
    }

    /// Execute a [`redis::Pipeline`] with GLIDE execution options
    /// (blocking). See [`crate::GlideClient::execute_pipeline`]; for plain
    /// typed execution prefer [`PipelineExt::query_glide`].
    pub fn execute_pipeline(
        &self,
        pipeline: &redis::Pipeline,
        raise_on_error: bool,
        options: &PipelineOptions,
    ) -> Result<Vec<Value>> {
        runtime().block_on(
            self.inner
                .execute_pipeline(pipeline, raise_on_error, options),
        )
    }

    /// Blocking `PING`.
    pub fn ping(&self) -> Result<String> {
        runtime().block_on(self.inner.ping())
    }
}

/// A blocking client for a **cluster** deployment.
#[derive(Clone)]
pub struct SyncGlideClusterClient {
    inner: GlideClusterClient,
}

impl SyncGlideClusterClient {
    /// Connect using the given cluster configuration (blocking).
    pub fn connect(config: GlideClusterClientConfiguration) -> Result<Self> {
        let inner = runtime().block_on(GlideClusterClient::connect(config))?;
        Ok(SyncGlideClusterClient { inner })
    }

    /// The underlying async client.
    pub fn async_client(&self) -> &GlideClusterClient {
        &self.inner
    }

    /// Run an arbitrary async operation against the client (blocking).
    pub fn run<F, Fut, T>(&self, f: F) -> T
    where
        F: FnOnce(GlideClusterClient) -> Fut,
        Fut: Future<Output = T>,
    {
        runtime().block_on(f(self.inner.clone()))
    }

    /// Run an arbitrary command (blocking escape hatch).
    pub fn custom_command<A: ToRedisArgs + Sync>(&self, args: &[A]) -> Result<Value> {
        runtime().block_on(self.inner.custom_command(args))
    }

    /// Run an arbitrary command with an explicit route (blocking).
    pub fn custom_command_with_route<A: ToRedisArgs + Sync>(
        &self,
        args: &[A],
        route: Route,
    ) -> Result<Value> {
        runtime().block_on(self.inner.custom_command_with_route(args, route))
    }

    /// Update the connection password (blocking). See
    /// [`GlideClusterClient::update_connection_password`].
    pub fn update_connection_password(
        &self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> Result<()> {
        runtime().block_on(
            self.inner
                .update_connection_password(password, immediate_auth),
        )
    }

    /// Execute a [`redis::Pipeline`] with GLIDE execution options,
    /// optionally routed (blocking). See
    /// [`crate::GlideClusterClient::execute_pipeline`].
    pub fn execute_pipeline(
        &self,
        pipeline: &redis::Pipeline,
        raise_on_error: bool,
        route: Option<crate::Route>,
        options: &PipelineOptions,
    ) -> Result<Vec<Value>> {
        runtime().block_on(
            self.inner
                .execute_pipeline(pipeline, raise_on_error, route, options),
        )
    }

    /// Blocking `PING`.
    pub fn ping(&self) -> Result<String> {
        runtime().block_on(self.inner.ping())
    }
}

// ---- unified command API dispatch ---------------------------------------------
// See the async impls in `client/`: commands arrive **by value**, so the
// blocking typed API costs no `Cmd` clone and no packed-byte round-trip.

macro_rules! impl_sync_owned_send {
    ($sync_ty:ty) => {
        impl crate::commands::core::Commands for $sync_ty {
            fn glide_send_owned_sync(&self, cmd: redis::Cmd) -> redis::RedisResult<Value> {
                runtime().block_on(crate::commands::core::AsyncCommands::glide_send_owned(
                    &self.inner,
                    cmd,
                ))
            }
        }
    };
}

impl_sync_owned_send!(SyncGlideClient);
impl_sync_owned_send!(SyncGlideClusterClient);

// ---- native-copy sync pipelines ----------------------------------------------
//
// `query_glide` drives the async `PipelineExt::query_glide` (which hands the
// built `&Pipeline` to glide-core by reference) on the wrapped async client,
// so a blocking pipeline copies the payload exactly as many times as the
// async pipeline path. Drop-in shape: `pipe()...query_glide(&client)`.

/// A blocking GLIDE client that can run a [`redis::Pipeline`] with
/// **native copy behavior**. Sealed — implemented only by
/// [`SyncGlideClient`] and [`SyncGlideClusterClient`].
pub trait SyncPipelineTarget: sealed::Sealed {
    /// The wrapped async client type.
    #[doc(hidden)]
    type Async: crate::client::GlidePipelineTarget;
    /// A cheap clone of the wrapped async client (Arc inside).
    #[doc(hidden)]
    fn async_conn(&self) -> Self::Async;
}

mod sealed {
    pub trait Sealed {}
    impl Sealed for super::SyncGlideClient {}
    impl Sealed for super::SyncGlideClusterClient {}
}

impl SyncPipelineTarget for SyncGlideClient {
    type Async = GlideClient;
    fn async_conn(&self) -> GlideClient {
        self.inner.clone()
    }
}

impl SyncPipelineTarget for SyncGlideClusterClient {
    type Async = GlideClusterClient;
    fn async_conn(&self) -> GlideClusterClient {
        self.inner.clone()
    }
}

/// Extension for running a [`redis::Pipeline`] on a blocking GLIDE
/// client with **native copy behavior** (no packed-byte round-trip).
///
/// Like the rest of the sync layer, this blocks on the internal runtime and
/// therefore **must not be called from within an async context** (doing so
/// panics with tokio's "cannot block the current thread from within a runtime"
/// — use the async [`crate::PipelineExt::query_glide`] there instead).
///
/// ```no_run
/// use glide::sync::{PipelineExt, SyncGlideClient};
/// # fn demo(client: &SyncGlideClient) -> glide::RedisResult<()> {
/// let (a, b): (i64, i64) = glide::pipe()
///     .atomic()
///     .incr("c", 1)
///     .incr("c", 1)
///     .query_glide(client)?;
/// # let _ = (a, b); Ok(()) }
/// ```
pub trait PipelineExt {
    /// Execute this pipeline on a blocking GLIDE client, sending the built
    /// `Pipeline` to glide-core by reference (native copy count), and decode
    /// the replies into `T` honoring `.ignore()` markers and transaction
    /// unwrapping.
    fn query_glide<C: SyncPipelineTarget, T: redis::FromRedisValue + Send>(
        &self,
        con: &C,
    ) -> redis::RedisResult<T>;
}

impl PipelineExt for redis::Pipeline {
    fn query_glide<C: SyncPipelineTarget, T: redis::FromRedisValue + Send>(
        &self,
        con: &C,
    ) -> redis::RedisResult<T> {
        let async_conn = con.async_conn();
        runtime().block_on(crate::client::PipelineExt::query_glide(self, &async_conn))
    }
}
