// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
#![doc = include_str!("../README.md")]
#![forbid(unsafe_code)]
#![deny(missing_docs)]

// Modules
// -------

pub mod client;
pub mod cmd;
pub mod commands;
pub mod config;
pub mod error;
pub mod executor;
pub mod pipeline;
pub mod pipeline_options;
pub mod routes;
pub mod script;
pub mod telemetry;
pub mod value;

#[cfg(feature = "sync")]
pub mod sync;

#[cfg(test)]
mod mock_tests;

// Aliases
// -------

/// The result type for GLIDE sync operations.
pub type ValkeyResult<T> = std::result::Result<T, GlideError>;

/// The future returned by GLIDE async commands.
pub type ValkeyFuture<'a, T> = futures::future::BoxFuture<'a, ValkeyResult<T>>;

// Classes and methods
// -------------------

// Values
pub use value::FromValkeyValue;
pub use value::ToValkeyArgs;
pub use value::ValkeyNumericBehavior;
pub use value::ValkeyValue;
pub use value::ValkeyVerbatimFormat;

// Core types
pub use error::GlideError;
pub use routes::Route;
pub use routes::SlotType;

// Client
pub use client::ClusterScanCursor;
pub use client::GlideClient;
pub use client::GlideClusterClient;
pub use client::GlidePipelineTarget;
pub use client::PipelineExt;
pub use client::PubSubMessage;
pub use client::PubSubMessageKind;

// Configuration types
pub use config::BackoffStrategy;
pub use config::ClientIdentity;
pub use config::GlideClientConfiguration;
pub use config::GlideClusterClientConfiguration;
pub use config::IamAuthConfig;
pub use config::NodeAddress;
pub use config::NodeDiscoveryMode;
pub use config::PeriodicChecks;
pub use config::ProtocolVersion;
pub use config::PubSubChannelMode;
pub use config::PubSubSubscriptions;
pub use config::ReadFrom;
pub use config::ServerCredentials;
pub use config::ServiceType;
pub use config::TlsConfig;

// Commands
pub use cmd::Cmd;
pub use cmd::cmd;
pub use commands::core::AsyncCommands;
pub use commands::prelude::*;
pub use executor::CustomCommand;

#[cfg(feature = "sync")]
pub use commands::core::Commands;

/// Shared command options.
pub use commands::options::ClientPauseMode;
pub use commands::options::ConditionalChange;
pub use commands::options::Direction;
pub use commands::options::ExistenceCheck;
pub use commands::options::ExpireOptions;
pub use commands::options::Expiry;
pub use commands::options::FlushMode;
pub use commands::options::FunctionRestorePolicy;
pub use commands::options::HashFieldConditionalChange;
pub use commands::options::Limit;
pub use commands::options::LposOptions;
pub use commands::options::MigrateOptions;
pub use commands::options::ObjectType;
pub use commands::options::OrderBy;
pub use commands::options::RestoreOptions;
pub use commands::options::SetExpiry;
pub use commands::options::SetOptions;

/// Command group-specific options.
pub use commands::bitmap::BitEncoding;
pub use commands::bitmap::BitFieldOffset;
pub use commands::bitmap::BitFieldSubcommand;
pub use commands::bitmap::BitOverflow;
pub use commands::bitmap::BitmapIndexType;
pub use commands::geo::GeoSearchShape;
pub use commands::geo::GeoUnit;
pub use commands::geo::GeospatialData;
pub use commands::sorted_set::AggregationType;
pub use commands::sorted_set::LexBound;
pub use commands::sorted_set::ScoreBound;
pub use commands::stream::PendingConsumer;
pub use commands::stream::StreamAddOptions;
pub use commands::stream::StreamClaimOptions;
pub use commands::stream::StreamEntry;
pub use commands::stream::StreamGroupCreateOptions;
pub use commands::stream::StreamReadGroupOptions;
pub use commands::stream::StreamReadOptions;
pub use commands::stream::StreamTrimOptions;
pub use commands::stream::StreamTrimStrategy;
pub use commands::stream::XPendingEntry;
pub use commands::stream::XPendingSummary;

// Scan iterators
pub use commands::scan::ScanIter;

#[cfg(feature = "sync")]
pub use commands::scan::SyncScanIter;

/// Script types.
pub use script::Script;
pub use script::ScriptInvocation;

// Pipeline
pub use pipeline::Pipeline;
pub use pipeline::pipe;
pub use pipeline_options::PipelineOptions;

// External types.
pub use bytes::Bytes;
pub use num_bigint::BigInt;
