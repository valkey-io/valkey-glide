// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Blocking (synchronous) clients.
//!
//! Mirrors Python `glide-sync`. [`SyncGlideClient`] / [`SyncGlideClusterClient`]
//! wrap the async clients and drive them on a shared, process-wide Tokio runtime.
//!
//! Every async command is reachable from sync code via [`SyncGlideClient::run`]
//! (and the cluster equivalent), and the most common commands also have direct
//! blocking methods.
//!
//! The blocking methods here must not be called from within an async context
//! (a running Tokio runtime) — doing so panics with tokio's "cannot block the
//! current thread from within a runtime".

use crate::client::{GlideClient, GlideClusterClient};
use crate::commands::prelude::*;
use crate::config::{GlideClientConfiguration, GlideClusterClientConfiguration};
use crate::executor::CustomCommand;
use crate::pipeline_options::PipelineOptions;
use crate::routes::Route;
use crate::write::ToValkeyArgs;
use crate::{ValkeyResult, ValkeyValue};
use std::future::Future;
use std::sync::OnceLock;
use tokio::runtime::{Builder, Runtime};

mod pipeline;
pub use pipeline::PipelineExt;

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
///
/// Must not be called from within an async context (a running Tokio runtime) –
/// doing so panics with "cannot block the current thread from within a runtime".
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
    pub fn connect(config: GlideClientConfiguration) -> ValkeyResult<Self> {
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
    /// # fn demo(client: SyncGlideClient) -> glide::ValkeyResult<()> {
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
    ) -> ValkeyResult<()> {
        runtime().block_on(
            self.inner
                .update_connection_password(password, immediate_auth),
        )
    }

    /// Run an arbitrary command (blocking escape hatch).
    pub fn custom_command<A: ToValkeyArgs + Sync>(&self, args: &[A]) -> ValkeyResult<ValkeyValue> {
        runtime().block_on(self.inner.custom_command(args))
    }

    /// Execute a [`crate::Pipeline`] with GLIDE execution options
    /// (blocking). See [`crate::GlideClient::exec`]; for plain
    /// typed execution prefer [`PipelineExt::query`].
    pub fn exec(
        &self,
        pipeline: &crate::pipeline::Pipeline,
        raise_on_error: bool,
        options: &PipelineOptions,
    ) -> ValkeyResult<Vec<ValkeyValue>> {
        runtime().block_on(self.inner.exec(pipeline, raise_on_error, options))
    }

    /// Blocking `PING`.
    pub fn ping(&self) -> ValkeyResult<String> {
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
    pub fn connect(config: GlideClusterClientConfiguration) -> ValkeyResult<Self> {
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
    pub fn custom_command<A: ToValkeyArgs + Sync>(&self, args: &[A]) -> ValkeyResult<ValkeyValue> {
        runtime().block_on(self.inner.custom_command(args))
    }

    /// Run an arbitrary command with an explicit route (blocking).
    pub fn custom_command_with_route<A: ToValkeyArgs + Sync>(
        &self,
        args: &[A],
        route: Route,
    ) -> ValkeyResult<ValkeyValue> {
        runtime().block_on(self.inner.custom_command_with_route(args, route))
    }

    /// Update the connection password (blocking). See
    /// [`GlideClusterClient::update_connection_password`].
    pub fn update_connection_password(
        &self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> ValkeyResult<()> {
        runtime().block_on(
            self.inner
                .update_connection_password(password, immediate_auth),
        )
    }

    /// Execute a [`crate::pipeline::Pipeline`] with GLIDE execution options,
    /// optionally routed (blocking). See
    /// [`crate::GlideClusterClient::exec`].
    pub fn exec(
        &self,
        pipeline: &crate::pipeline::Pipeline,
        raise_on_error: bool,
        route: Option<crate::Route>,
        options: &PipelineOptions,
    ) -> ValkeyResult<Vec<ValkeyValue>> {
        runtime().block_on(self.inner.exec(pipeline, raise_on_error, route, options))
    }

    /// Blocking `PING`.
    pub fn ping(&self) -> ValkeyResult<String> {
        runtime().block_on(self.inner.ping())
    }
}

// ---- Command dispatch -------------------------------------------------------

macro_rules! impl_sync_command_dispatch {
    ($ty:ty) => {
        impl crate::commands::core::Commands for $ty {
            fn glide_send_command(&self, cmd: crate::cmd::Cmd) -> ValkeyResult<ValkeyValue> {
                runtime().block_on(crate::commands::core::AsyncCommands::glide_send_command(
                    &self.inner,
                    cmd,
                ))
            }
        }
    };
}

impl_sync_command_dispatch!(SyncGlideClient);
impl_sync_command_dispatch!(SyncGlideClusterClient);

// ---- Script dispatch --------------------------------------------------------

macro_rules! impl_sync_script_invoke {
    ($ty:ty) => {
        #[::sealed::sealed]
        impl crate::script::ScriptInvokeSync for $ty {
            fn glide_invoke_script(
                &self,
                hash: &str,
                keys: &[Vec<u8>],
                args: &[Vec<u8>],
            ) -> ValkeyResult<ValkeyValue> {
                runtime().block_on(crate::script::ScriptInvoke::glide_invoke_script(
                    &self.inner,
                    hash,
                    keys,
                    args,
                ))
            }
        }
    };
}

impl_sync_script_invoke!(SyncGlideClient);
impl_sync_script_invoke!(SyncGlideClusterClient);

// ---- Pipeline dispatch ------------------------------------------------------

macro_rules! impl_sync_pipeline_dispatch {
    ($ty:ty) => {
        #[::sealed::sealed]
        impl pipeline::SyncPipelineDispatch for $ty {
            fn glide_dispatch_pipeline<T: crate::value::FromValkeyValue + Send>(
                &self,
                pipeline: &crate::pipeline::Pipeline,
            ) -> ValkeyResult<T> {
                let async_conn = self.inner.clone();
                runtime().block_on(crate::client::PipelineExt::query_async(
                    pipeline,
                    &async_conn,
                ))
            }
        }
    };
}

impl_sync_pipeline_dispatch!(SyncGlideClient);
impl_sync_pipeline_dispatch!(SyncGlideClusterClient);
