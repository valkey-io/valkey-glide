// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
#![doc = include_str!("../README.md")]
#![forbid(unsafe_code)]
#![deny(missing_docs)]

pub mod client;
pub mod commands;
pub mod config;
pub mod error;
pub mod executor;
pub mod pipeline_options;
pub mod routes;
pub mod script;
pub mod telemetry;
pub mod value;

#[cfg(feature = "sync")]
pub mod sync;

// ---- Primary public API re-exports (mirror Python's top-level `glide` package) ----

pub use client::{
    ClusterScanCursor, GlideClient, GlideClusterClient, PubSubMessage, PubSubMessageKind,
};
pub use error::{GlideError, Result};
pub use executor::{CommandExecutor, CustomCommand};
pub use pipeline_options::PipelineOptions;
pub use routes::{Route, SlotType};

pub use config::{
    BackoffStrategy, ClientIdentity, GlideClientConfiguration, GlideClusterClientConfiguration,
    IamAuthConfig, NodeAddress, NodeDiscoveryMode, PeriodicChecks, ProtocolVersion,
    PubSubChannelMode, PubSubSubscriptions, ReadFrom, ServerCredentials, ServiceType, TlsConfig,
};

/// All command traits in one import.
pub use commands::prelude::*;

/// All shared option types.
pub use commands::options::{
    ClientPauseMode, ConditionalChange, ExpireOptions, ExpirySet, FlushMode, FunctionRestorePolicy,
    HashFieldConditionalChange, Limit, MigrateOptions, ObjectType, OrderBy, RestoreOptions,
};

/// Family-specific option/type re-exports.
pub use commands::bitmap::{
    BitEncoding, BitFieldOffset, BitFieldSubcommand, BitOverflow, BitmapIndexType,
};
pub use commands::geo::{GeoSearchShape, GeoUnit, GeospatialData};
pub use commands::sorted_set::{AggregationType, LexBound, ScoreBound};
pub use commands::stream::{
    PendingConsumer, StreamAddOptions, StreamClaimOptions, StreamEntry, StreamGroupCreateOptions,
    StreamReadGroupOptions, StreamReadOptions, StreamTrimOptions, StreamTrimStrategy,
    XPendingEntry, XPendingSummary,
};

/// Re-export the underlying `redis` value type for advanced use.
pub use redis::Value;

// ---- `redis` crate re-exports ----
//
// GLIDE's unified command traits are source-compatible with the fork's
// command surface, and their signatures reference fork types
// (`ToRedisArgs`, `FromRedisValue`, `SetOptions`, ...). Downstream crates
// depend on `glide-rust` only — the vendored `redis` fork is a transitive git
// dependency they cannot name — so re-export everything a migrating codebase
// needs:
//
// ```rust,no_run
// use glide::{AsyncCommands, GlideClient, GlideClientConfiguration};
//
// # async fn demo() -> glide::RedisResult<()> {
// # let mut client = GlideClient::connect(GlideClientConfiguration::with_address("localhost", 6379)).await.unwrap();
// client.set::<_, _, ()>("my_key", 42).await?;
// let v: i64 = client.get("my_key").await?;
// # Ok(()) }
// ```

/// Typed [`Pipeline`] execution on the async clients with zero extra payload
/// copies (`pipe()...query_glide(&client)`); the blocking counterpart lives
/// at `sync::PipelineExt`.
pub use client::{GlidePipelineTarget, PipelineExt};
/// GLIDE's async command API (source-compatible with the redis-rs fork,
/// v0.25.2 — see `commands::core`). Commands travel GLIDE's native
/// zero-extra-copy path.
pub use commands::core::AsyncCommands;
/// GLIDE's blocking command API (see [`AsyncCommands`]).
#[cfg(feature = "sync")]
pub use commands::core::Commands;
/// GLIDE's cursor-driven scan iterators, returned by the `scan*` methods.
pub use commands::scan::ScanIter;
/// Blocking counterpart of [`ScanIter`] (implements [`Iterator`]).
#[cfg(feature = "sync")]
pub use commands::scan::SyncScanIter;
/// The **whole vendored `redis` crate**, re-exported. Downstream crates cannot
/// name the git-dep fork directly, and the curated flat re-exports above are
/// deliberately incomplete where names collide with other exported types
/// (`redis::SetOptions`, `redis::Expiry`, ...). Everything is reachable as
/// `glide::redis::…` with zero collision risk:
///
/// ```rust,no_run
/// use glide::redis::{Expiry, SetOptions};
/// ```
///
/// Note: the GLIDE clients are deliberately **not** `redis` connection
/// objects (no `ConnectionLike`) — that interop layer cost a payload copy
/// per command. Use the unified traits, [`PipelineExt::query_glide`], or
/// [`CustomCommand::custom_command`] instead.
///
/// **Semver note:** this makes the fork's API part of this crate's public
/// surface — bumping the pinned fork rev is a breaking change.
pub use redis;
/// Connection-description types, accepted by
/// [`GlideClientConfiguration::from_connection_info`] and
/// [`GlideClusterClientConfiguration::from_urls`].
pub use redis::{ConnectionAddr, ConnectionInfo, IntoConnectionInfo};
/// Argument types appearing in command signatures (`lmpop`, `lpos`, …).
pub use redis::{Direction, LposOptions};
/// Error and conversion types (`RedisResult`, `FromRedisValue`, …).
pub use redis::{ErrorKind, FromRedisValue, RedisError, RedisResult, ToRedisArgs, cmd};
/// Pipeline / transaction builder (`pipe()`; run with
/// [`PipelineExt::query_glide`] or `execute_pipeline`).
pub use redis::{Pipeline, pipe};
/// Lua script helper (`Script` — SHA-caching `EVALSHA` with `EVAL` fallback).
pub use script::{Script, ScriptInvocation};

/// Re-export `bytes::Bytes` — the byte-string type returned by binary-safe commands.
pub use bytes::Bytes;
